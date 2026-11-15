package com.example.data.repository

import com.example.core.config.AppConfig
import com.example.core.network.ConversationParticipantDto
import com.example.core.network.SupabaseClientFactory
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.Query
import java.time.Instant

private interface ConversationReadApi {
    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/conversation_participants")
    suspend fun markParticipantRead(
        @Query("conversation_id") conversationIdEq: String,
        @Query("user_id") userIdEq: String,
        @Body updates: Map<String, String>,
    ): Response<List<ConversationParticipantDto>>
}

/**
 * Persists the signed-in participant's read cursor.
 * The matching RLS policy and column-level UPDATE grant are staged in migration 0007.
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
        val response = api.markParticipantRead(
            conversationIdEq = "eq.$conversationId",
            userIdEq = "eq.$uid",
            updates = mapOf("last_read_at" to readAt),
        )
        if (!response.isSuccessful) {
            val detail = response.errorBody()?.string()?.take(400)
            Result.failure(IllegalStateException(detail ?: "Conversation read status could not be saved (HTTP ${response.code()})."))
        } else {
            val saved = response.body()?.firstOrNull()
            if (saved?.conversationId != conversationId || saved.userId != uid) {
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
