package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.config.AppConfig
import com.example.core.network.SupabaseStorageApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed class UploadProgressState {
    data object Idle : UploadProgressState()
    data class Validating(val fileName: String) : UploadProgressState()
    data class Uploading(val progress: Float, val fileName: String) : UploadProgressState()
    data class Success(val publicUrl: String, val mediaType: String) : UploadProgressState()
    data class Failed(val message: String) : UploadProgressState()
}

class MediaUploadManager(
    private val context: Context,
    private val storageApi: SupabaseStorageApi,
) {
    companion object {
        const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L // 15 MB
        const val MAX_VIDEO_BYTES = 100L * 1024L * 1024L // 100 MB
        const val MAX_IMAGE_DIMENSION = 1920
    }

    fun uploadMedia(
        uri: Uri,
        bucket: String,
        ownerId: String,
    ): Flow<UploadProgressState> = channelFlow {
        if (!AppConfig.isSupabaseConfigured) {
            send(UploadProgressState.Failed("Supabase is not configured in AI Studio Secrets."))
            return@channelFlow
        }

        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val isVideo = mimeType.startsWith("video/")
        val isImage = mimeType.startsWith("image/")
        if (!isImage && !isVideo) {
            send(UploadProgressState.Failed("Unsupported media format: $mimeType"))
            return@channelFlow
        }

        val fileName = resolveFileName(uri) ?: "upload_${UUID.randomUUID()}"
        send(UploadProgressState.Validating(fileName))

        val payload = withContext(Dispatchers.IO) {
            if (isImage) {
                compressImage(uri)
            } else {
                readVideoBytesValidated(uri)
            }
        }

        if (payload == null) {
            send(
                UploadProgressState.Failed(
                    if (isVideo) "Video exceeds the 100 MB limit or could not be read."
                    else "Image exceeds 15 MB or could not be decoded.",
                ),
            )
            return@channelFlow
        }

        val ext = if (isVideo) "mp4" else "jpg"
        val finalMime = if (isVideo) mimeType else "image/jpeg"
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
            val publicUrl = "${AppConfig.supabaseUrl}/storage/v1/object/public/$bucket/$objectPath"
            send(UploadProgressState.Success(publicUrl, if (isVideo) "video" else "image"))
        } else {
            val detail = response.errorBody()?.string()?.take(220).orEmpty()
            val suffix = detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            send(UploadProgressState.Failed("Upload failed (HTTP ${response.code()})$suffix"))
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
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (rawBytes.size > MAX_IMAGE_BYTES) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return rawBytes
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
