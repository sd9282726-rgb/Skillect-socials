#!/usr/bin/env bash
set -euo pipefail
python3 <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/example/core/network/SupabaseApiService.kt")
t = p.read_text()
if "getUserPreferences" not in t:
    anchor = '    @GET("rest/v1/posts")'
    insert = '''    @GET("rest/v1/user_preferences")
    suspend fun getUserPreferences(
        @Query("user_id") userIdEq: String,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 1,
    ): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/user_preferences")
    suspend fun upsertUserPreferences(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
    ): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

'''
    if anchor in t:
        p.write_text(t.replace(anchor, insert + anchor, 1))
        print("API methods added")
    else:
        print("API anchor missing")
else:
    print("API methods present")

rp = Path("app/src/main/java/com/example/data/repository/SkillectRepository.kt")
rt = rp.read_text()
if "fun updateMessagePrivacy" not in rt:
    marker = "    suspend fun currentUserCanModerate(): Boolean {"
    method = '''
    suspend fun fetchMessagePrivacy(userId: String): Result<String> = guardConfigured {
        val resp = postgrestApi.getUserPreferences(userIdEq = "eq.$userId")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException("Could not load messaging privacy."))
        val row = resp.body()?.firstOrNull().orEmpty()
        val privacy = (row["message_privacy"] as? String)?.lowercase()
        val mapped = when (privacy) {
            "everyone", "following", "nobody" -> privacy
            else -> if (row["allow_message_requests"] == false) "nobody" else "everyone"
        }
        Result.success(mapped)
    }

    suspend fun updateMessagePrivacy(messagePrivacy: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("update message privacy")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("update message privacy"))
        val normalized = when (messagePrivacy.lowercase()) {
            "everyone" -> "everyone"
            "following", "following_only" -> "following"
            "nobody" -> "nobody"
            else -> "everyone"
        }
        val body = mapOf(
            "user_id" to uid,
            "message_privacy" to normalized,
            "allow_message_requests" to (normalized != "nobody"),
        )
        val resp = postgrestApi.upsertUserPreferences(body)
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException("Could not save messaging privacy."))
    }

'''
    if marker in rt:
        rp.write_text(rt.replace(marker, method + marker, 1))
        print("Repo methods added")
    else:
        print("Repo marker missing")
else:
    print("Repo methods present")

vp = Path("app/src/main/java/com/example/ui/profile/ProfileSettingsViewModel.kt")
vt = vp.read_text()
if "updateMessagePrivacy" not in vt:
    old = '''    fun updatePrivacySettings(isPrivate: Boolean, allowDmsFrom: String) {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) return@launch
            repository.updateProfileFields(
                uid,
                mapOf("is_private" to isPrivate),
            ).fold(
                onSuccess = { updated ->
                    _uiState.update { it.copy(myProfile = updated, statusMessage = "Privacy preferences saved.") }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }
'''
    new = '''    fun updatePrivacySettings(isPrivate: Boolean, allowDmsFrom: String) {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) return@launch
            val privateResult = repository.updateProfileFields(uid, mapOf("is_private" to isPrivate))
            val dmResult = repository.updateMessagePrivacy(allowDmsFrom)
            privateResult.fold(
                onSuccess = { updated ->
                    dmResult.fold(
                        onSuccess = {
                            _uiState.update { it.copy(myProfile = updated, statusMessage = "Privacy preferences saved.") }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    myProfile = updated,
                                    statusMessage = err.message ?: "Could not save messaging privacy.",
                                )
                            }
                        },
                    )
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }
'''
    if old in vt:
        vp.write_text(vt.replace(old, new))
        print("ViewModel updated")
    else:
        print("ViewModel pattern missing")
else:
    print("ViewModel already wired")

print("Privacy client apply complete")
PY
