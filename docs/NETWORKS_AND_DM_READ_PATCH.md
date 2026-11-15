# Remaining local patches (large files)

## 1. SupabaseApiService.kt
Add after `markNotificationsRead`:

```kotlin
    @PATCH("rest/v1/conversation_participants")
    suspend fun markConversationParticipantRead(
        @Query("conversation_id") conversationIdEq: String,
        @Query("user_id") userIdEq: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>,
    ): Response<ResponseBody>
```

## 2. SkillectRepository.kt
Add before `fetchNotifications`:

```kotlin
    suspend fun markConversationRead(conversationId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("update conversation read state")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("update conversation read state"))
        if (conversationId.isBlank()) {
            return@guardConfigured Result.failure(IllegalArgumentException("Conversation is required."))
        }
        val now = java.time.Instant.now().toString()
        val resp = postgrestApi.markConversationParticipantRead(
            conversationIdEq = "eq.$conversationId",
            userIdEq = "eq.$uid",
            updates = mapOf("last_read_at" to now),
        )
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.parseError()))
    }
```

## 3. Networks terminology (one-line replacements)

| File | Find | Replace |
|------|------|---------|
| PostCard.kt | `"Following" else "Follow"` | `"Connected" else "Connect"` |
| ProfileScreen.kt | `"Following" else "Follow"` (button) | `"Connected" else "Connect"` |
| ProfileScreen.kt | `StatCounter(..., "Following")` | `StatCounter(..., "Connected")` |
| FeedScreen.kt | `FOLLOWING to "Following"` | `FOLLOWING to "Network"` |
| AuthScreen.kt | `Text("Follow")` onboarding | `Text("Connect")` |

## 4. MessagesScreen.kt unread badge
See local working tree on agent host under /tmp/skillect-audit.

## Already on GitHub this session
- MessagesNotificationsViewModel.kt (loadMessages → markConversationRead)
- migrations/0007_conversation_participants_read_update.sql (NOT applied to production)
