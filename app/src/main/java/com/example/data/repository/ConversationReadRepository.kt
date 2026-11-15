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
import java.time.Instant

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
}

/**
 * Persists the signed-in participant's read cursor.
 * The existing conversation_reads RLS policy scopes updates to the signed-in participant; migration 0007 restricts column-level UPDATE to last_read_at.
 */
suspend fun SkillectRepository.markConversationRead(conversationId: String): Result<String> {
    if (!AppConfig.isSupabaseConfigured) {
        return Result.failure(BackendNotConfiguredException())
    }
    if (conversationId.isBlank()) {
        return Result.failure(IllegalArgumentException("Conversation is required."))
    }
    return try {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) {
            return Result.failure(AuthenticationRequiredException("update conversation read state"))
        }
        val readAt = Instant.now().toString()
        val api = SupabaseClientFactory.createRetrofit(sessionStore)
            .create(ConversationReadApi::class.java)
        val marker = mapOf(
            "conversation_id" to conversationId,
            "user_id" to uid,
            "last_read_at" to readAt,
        )
        val insertResponse = api.createReadMarker(marker)
        val savedResponse = if (insertResponse.isSuccessful) {
            insertResponse
        } else if (insertResponse.code() == 409) {
            api.markConversationRead(
                conversationIdEq = "eq.$conversationId",
                userIdEq = "eq.$uid",
                updates = mapOf("last_read_at" to readAt),
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
