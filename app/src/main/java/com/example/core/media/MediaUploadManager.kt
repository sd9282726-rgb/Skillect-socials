package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.config.AppConfig
import com.example.core.network.SupabaseStorageApi
import com.example.core.network.SupabasePostgrestApi
import com.example.core.network.ModerationGatewayResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed class UploadProgressState {
    data object Idle : UploadProgressState()
    data class Validating(val fileName: String) : UploadProgressState()
    data class Uploading(val progress: Float, val fileName: String) : UploadProgressState()
    data class Success(val publicUrl: String, val mediaType: String, val storagePath: String? = null) : UploadProgressState()
    data class Failed(val message: String) : UploadProgressState()
}

class MediaUploadManager(
    private val context: Context,
    private val storageApi: SupabaseStorageApi,
    private val postgrestApi: SupabasePostgrestApi,
) {
    companion object {
        const val MAX_IMAGE_BYTES = 12L * 1024L * 1024L // 12 MB moderation limit
        const val MAX_VIDEO_BYTES = 50L * 1024L * 1024L // 50 MB quarantine bucket limit
        const val MAX_IMAGE_DIMENSION = 1920
    }

    fun uploadMedia(
        uri: Uri,
        bucket: String,
        ownerId: String,
    ): Flow<UploadProgressState> = channelFlow {
        if (!AppConfig.isSupabaseConfigured) {
            send(UploadProgressState.Failed("Supabase is not configured."))
            return@channelFlow
        }
        if (ownerId.isBlank() || ownerId.contains("/") || ownerId.contains("..")) {
            send(UploadProgressState.Failed("Sign in before uploading media."))
            return@channelFlow
        }

        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val isVideo = mimeType.startsWith("video/")
        val isImage = mimeType.startsWith("image/")
        if (!isVideo && !isImage) {
            send(UploadProgressState.Failed("Choose a supported image or video file."))
            return@channelFlow
        }
        val isStoryUpload = bucket == "stories-media"
        val isQuarantineUpload = bucket == "media-quarantine"
        if (!isStoryUpload && (!isQuarantineUpload || !isVideo)) {
            send(UploadProgressState.Failed("Choose a supported image or video for your Memory."))
            return@channelFlow
        }
        val supportedVideoTypes = setOf("video/mp4", "video/webm", "video/quicktime")
        if (isVideo && mimeType.lowercase() !in supportedVideoTypes) {
            send(UploadProgressState.Failed("Use an MP4, WebM, or MOV video."))
            return@channelFlow
        }

        val fileName = resolveFileName(uri) ?: "upload_${UUID.randomUUID()}"
        send(UploadProgressState.Validating(fileName))
        val payload = withContext(Dispatchers.IO) {
            if (isImage) compressImage(uri) else readVideoBytesValidated(uri)
        }
        if (payload == null) {
            val message = if (isImage) {
                "Image exceeds the 12 MB limit or could not be prepared."
            } else {
                "Video exceeds the 50 MB limit or could not be read."
            }
            send(UploadProgressState.Failed(message))
            return@channelFlow
        }

        val ext = when {
            isImage -> "jpg"
            mimeType.equals("video/webm", ignoreCase = true) -> "webm"
            mimeType.equals("video/quicktime", ignoreCase = true) -> "mov"
            else -> "mp4"
        }
        val finalMime = if (isImage) "image/jpeg" else mimeType
        val objectPath = "$ownerId/${UUID.randomUUID()}.$ext"
        send(UploadProgressState.Uploading(0.05f, fileName))

        val requestBody = ProgressByteRequestBody(
            bytes = payload,
            mediaType = finalMime.toMediaTypeOrNull(),
            onProgress = { ratio ->
                trySend(UploadProgressState.Uploading(ratio.coerceIn(0.05f, 0.98f), fileName))
            },
        )

        val response = storageApi.uploadObject(
            bucket = bucket,
            path = objectPath,
            contentType = finalMime,
            body = requestBody,
        )

        if (response.isSuccessful) {
            if (isStoryUpload) {
                // Save the private object path; the repository creates a viewer-authorized signed URL.
                send(UploadProgressState.Success(objectPath, if (isVideo) "video" else "image", objectPath))
            } else {
                // media-quarantine is private; submit-video-post validates it and moves approved media into post-media.
                send(UploadProgressState.Success("", "video", objectPath))
            }
        } else {
            val detail = response.errorBody()?.string()?.take(220).orEmpty()
            val suffix = detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            send(UploadProgressState.Failed("Upload failed (HTTP ${response.code()})$suffix"))
        }
    }

    suspend fun uploadProfileImage(uri: Uri, ownerId: String, kind: String): Result<String> = withContext(Dispatchers.IO) {
        if (!AppConfig.isSupabaseConfigured) return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        if (kind != "avatar" && kind != "cover") return@withContext Result.failure(IllegalArgumentException("Unsupported profile image type."))
        if (ownerId.isBlank() || ownerId.contains("/") || ownerId.contains("..")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid profile owner."))
        }
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        if (!mimeType.startsWith("image/")) return@withContext Result.failure(IllegalArgumentException("Choose an image."))
        val bytes = compressImage(uri) ?: return@withContext Result.failure(IllegalArgumentException("This image could not be prepared. Choose a JPG or PNG under 12 MB."))
        val fileName = "$ownerId/$kind-${UUID.randomUUID()}.jpg"
        try {
            val response = storageApi.uploadObject(
                bucket = "avatars",
                path = fileName,
                contentType = "image/jpeg",
                body = bytes.toRequestBody("image/jpeg".toMediaType()),
            )
            if (!response.isSuccessful) {
                val detail = response.errorBody()?.string().orEmpty()
                Result.failure(IllegalStateException(detail.ifBlank { "Profile image upload failed (HTTP ${response.code()})." }))
            } else {
                Result.success("${AppConfig.supabaseUrl.trimEnd('/')}/storage/v1/object/public/avatars/$fileName")
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun uploadImagePost(
        uri: Uri,
        content: String,
        tags: List<String>,
        category: String,
        communityId: String?,
    ): Result<ModerationGatewayResponse> = withContext(Dispatchers.IO) {
        if (!AppConfig.isSupabaseConfigured) return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        if (!mimeType.startsWith("image/")) return@withContext Result.failure(IllegalArgumentException("Choose an image file."))
        val fileName = resolveFileName(uri) ?: "skillect_image.jpg"
        val bytes = compressImage(uri) ?: return@withContext Result.failure(IllegalArgumentException("This image could not be prepared. Try a JPG or PNG under 12 MB."))
        if (bytes.size > MAX_IMAGE_BYTES) return@withContext Result.failure(IllegalArgumentException("Images must be 12 MB or smaller."))
        val part = MultipartBody.Part.createFormData(
            "file",
            fileName.substringBeforeLast(".", fileName) + ".jpg",
            bytes.toRequestBody("image/jpeg".toMediaType()),
        )
        val contentPart = content.toRequestBody("text/plain".toMediaType())
        val tagsPart = JSONArray(tags).toString().toRequestBody("application/json".toMediaType())
        val categoryPart = category.toRequestBody("text/plain".toMediaType())
        val communityPart = communityId?.toRequestBody("text/plain".toMediaType())
        try {
            val response = postgrestApi.moderateMedia(part, contentPart, tagsPart, categoryPart, communityPart)
            if (!response.isSuccessful) {
                val detail = response.errorBody()?.string().orEmpty()
                Result.failure(IllegalStateException(detail.ifBlank { "Image upload failed (HTTP ${response.code()})." }))
            } else {
                val result = response.body()
                if ((result?.postId ?: result?.id).isNullOrBlank()) Result.failure(IllegalStateException(result?.message ?: "Image post could not be confirmed."))
                else Result.success(result!!)
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        return try {
            val declaredSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (declaredSize > MAX_IMAGE_BYTES) return null
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_IMAGE_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun readVideoBytesValidated(uri: Uri): ByteArray? {
        return try {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size > MAX_VIDEO_BYTES) return null
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.size > MAX_VIDEO_BYTES) null else bytes
        } catch (_: Exception) {
            null
        }
    }
}

private class ProgressByteRequestBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = mediaType
    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size
        val chunkSize = 8192
        var offset = 0
        while (offset < total) {
            val count = minOf(chunkSize, total - offset)
            sink.write(bytes, offset, count)
            offset += count
            onProgress(offset.toFloat() / total.coerceAtLeast(1))
        }
    }
}
