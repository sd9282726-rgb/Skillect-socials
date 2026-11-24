package com.example.data.repository

import com.example.core.config.AppConfig
import com.example.core.network.ConversationReadDto
import com.example.core.network.SupabaseClientFactory
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private interface ConversationReadApi {
    @Headers("Prefer: return=representation")
    @POST("rest/v1/conversation_reads")
    suspend fun createReadMarker(
        @Body marker: Map<String, String>,
    ): Response<List<ConversationReadDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/conversation_reads")
    suspend fun markConversationRead(
        @Query("conversation_id") conversationIdEq: String,
        @Query("user_id") userIdEq: String,
        @Body updates: Map<String, String>,
    ): Response<List<ConversationReadDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/conversation_reads")
    suspend fun markConversationDelivered(
        @Query("conversation_id") conversationIdEq: String,
        @Query("user_id") userIdEq: String,
        @Body updates: Map<String, String>,
    ): Response<List<ConversationReadDto>>
}

private fun currentUtcTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

/**
 * Persists the signed-in participant's read cursor and delivery acknowledgement.
 * RLS scopes both writes to the signed-in participant; column grants are limited
 * to last_read_at and last_delivered_at.
 */
suspend fun SkillectRepository.markConversationRead(conversationId: String): Result<String> {
    if (!AppConfig.isSupabaseConfigured) return Result.failure(BackendNotConfiguredException())
    if (conversationId.isBlank()) return Result.failure(IllegalArgumentException("Conversation is required."))
    return try {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return Result.failure(AuthenticationRequiredException("update conversation read state"))
        val readAt = currentUtcTimestamp()
        val api = SupabaseClientFactory.createRetrofit(sessionStore).create(ConversationReadApi::class.java)
        val marker = mapOf(
            "conversation_id" to conversationId,
            "user_id" to uid,
            "last_read_at" to readAt,
            "last_delivered_at" to readAt,
        )
        val insertResponse = api.createReadMarker(marker)
        val savedResponse = if (insertResponse.isSuccessful) {
            insertResponse
        } else if (insertResponse.code() == 409) {
            api.markConversationRead(
                conversationIdEq = "eq.$conversationId",
                userIdEq = "eq.$uid",
                updates = mapOf("last_read_at" to readAt, "last_delivered_at" to readAt),
            )
        } else {
            val detail = insertResponse.errorBody()?.string()?.take(400)
            return Result.failure(IllegalStateException(detail ?: "Conversation read status could not be saved (HTTP ${insertResponse.code()})."))
        }
        if (!savedResponse.isSuccessful) {
            val detail = savedResponse.errorBody()?.string()?.take(400)
            Result.failure(IllegalStateException(detail ?: "Conversation read status could not be saved (HTTP ${savedResponse.code()})."))
        } else {
            val saved = savedResponse.body()?.firstOrNull()
            if (saved == null || saved.conversationId != conversationId || saved.userId != uid) {
                Result.failure(IllegalStateException("The server did not confirm the conversation read update."))
            } else {
                Result.success(saved.lastReadAt ?: readAt)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

suspend fun SkillectRepository.markConversationDelivered(conversationId: String): Result<String> {
    if (!AppConfig.isSupabaseConfigured) return Result.failure(BackendNotConfiguredException())
    if (conversationId.isBlank()) return Result.failure(IllegalArgumentException("Conversation is required."))
    return try {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return Result.failure(AuthenticationRequiredException("update conversation delivery state"))
        val deliveredAt = currentUtcTimestamp()
        val api = SupabaseClientFactory.createRetrofit(sessionStore).create(ConversationReadApi::class.java)
        val response = api.markConversationDelivered(
            conversationIdEq = "eq.$conversationId",
            userIdEq = "eq.$uid",
            updates = mapOf("last_delivered_at" to deliveredAt),
        )
        if (!response.isSuccessful) {
            val detail = response.errorBody()?.string()?.take(400)
            Result.failure(IllegalStateException(detail ?: "Message delivery could not be confirmed (HTTP ${response.code()})."))
        } else {
            val saved = response.body()?.firstOrNull()
            if (saved == null || saved.conversationId != conversationId || saved.userId != uid) {
                Result.failure(IllegalStateException("The server did not confirm message delivery."))
            } else {
                Result.success(saved.lastDeliveredAt ?: deliveredAt)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
