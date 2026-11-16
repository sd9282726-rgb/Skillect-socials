package com.example.data.repository

import android.util.Base64
import com.example.core.config.AppConfig
import com.example.core.network.AchievementDto
import com.example.core.network.BlockRowDto
import com.example.core.network.CommentDto
import com.example.core.network.CommunityDto
import com.example.core.network.CommunityMemberDto
import com.example.core.network.ConversationDto
import com.example.core.network.CreateAchievementRequest
import com.example.core.network.CreateAppealRequest
import com.example.core.network.AccountEnforcementDto
import com.example.core.network.CreateAuditLogRequest
import com.example.core.network.CreateCommentRequest
import com.example.core.network.CreateCommunityRequest
import com.example.core.network.CreateDirectConversationParams
import com.example.core.network.CreateExperienceRequest
import com.example.core.network.CreatePostRequest
import com.example.core.network.CreateProjectRequest
import com.example.core.network.CreateReportRequest
import com.example.core.network.CreateStoryRequest
import com.example.core.network.DeviceTokenUpsertDto
import com.example.core.network.ExperienceDto
import com.example.core.network.FollowRowDto
import com.example.core.network.MessageDto
import com.example.core.network.MessageRequestDto
import com.example.core.network.ModerationAppealDto
import com.example.core.network.ModerationMediaDto
import com.example.core.network.ModerationAuditLogDto
import com.example.core.network.MuteRowDto
import com.example.core.network.NotificationDto
import com.example.core.network.PasswordSignInRequest
import com.example.core.network.PersistedSession
import com.example.core.network.PostDto
import com.example.core.network.ProfileDto
import com.example.core.network.ProfileUpsertDto
import com.example.core.network.ProjectDto
import com.example.core.network.RecoverPasswordRequest
import com.example.core.network.RefreshTokenRequest
import com.example.core.network.ReportDto
import com.example.core.network.ResolveModerationAppealRequest
import com.example.core.network.SavedCollectionDto
import com.example.core.network.SavedPostDto
import com.example.core.network.SendMessageRequest
import com.example.core.network.SessionStore
import com.example.core.network.SignUpRequest
import com.example.core.network.StartConversationRequest
import com.example.core.network.StartConversationResponse
import com.example.core.network.SkillDto
import com.example.core.network.StoryDto
import com.example.core.network.SupabaseAuthApi
import com.example.core.network.SupabasePostgrestApi
import com.example.core.network.SupabaseRealtimeClient
import com.example.core.network.SupabaseStorageApi
import com.example.core.network.UpdatePasswordRequest
import com.example.core.notifications.AnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException

class BackendNotConfiguredException :
    IllegalStateException("Supabase project credentials are not configured in AI Studio Secrets.")

class AuthenticationRequiredException(action: String = "perform this action") :
    IllegalStateException("Sign in or create an account to $action.")

enum class FeedTab { FOLLOWING, RECOMMENDED, DISCOVER }

data class FollowToggleResult(val wasFollowing: Boolean, val isFollowing: Boolean)

class SkillectRepository(
    val sessionStore: SessionStore,
    private val authApi: SupabaseAuthApi,
    private val postgrestApi: SupabasePostgrestApi,
    val realtimeClient: SupabaseRealtimeClient,
    private val analyticsTracker: AnalyticsTracker,
    private val isConfiguredCheck: () -> Boolean = { AppConfig.isSupabaseConfigured },
    private val storageApi: SupabaseStorageApi? = null,
) {
    val sessionFlow: Flow<PersistedSession> = sessionStore.sessionFlow
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private inline fun <T> guardConfigured(block: () -> Result<T>): Result<T> {
        if (!isConfiguredCheck()) {
            return Result.failure(BackendNotConfiguredException())
        }
        return try {
            block()
        } catch (cancelled: CancellationException) {
            // Preserve structured cancellation so stale Explore/community requests cannot publish old results.
            throw cancelled
        } catch (_: IOException) {
            Result.failure(IOException("Network connection error. Check your internet and try again."))
        } catch (e: Exception) {
            analyticsTracker.recordNonFatal(e)
            Result.failure(e)
        }
    }

    private suspend fun requireUserId(action: String): String? {
        val uid = sessionStore.currentSession().userId
        return uid.takeIf { it.isNotBlank() }
    }

    private fun <T> Response<T>.parseError(): String {
        val raw = errorBody()?.string().orEmpty()
        val extracted = try {
            val json = JSONObject(raw)
            json.optString("msg").ifBlank {
                json.optString("message").ifBlank {
                    json.optString("error_description").ifBlank {
                        json.optString("error_code").ifBlank { "HTTP ${code()}" }
                    }
                }
            }
        } catch (_: Exception) {
            raw.ifBlank { "HTTP ${code()}" }
        }
        return mapFriendlyAuthMessage(extracted)
    }

    private fun mapFriendlyAuthMessage(message: String): String {
        val lower = message.lowercase()
        return when {
            "already registered" in lower || "user_already_exists" in lower ->
                "An account with this email already exists. Try signing in or resetting your password."
            "invalid login credentials" in lower || "invalid_credentials" in lower ->
                "Incorrect email or password. Please check your credentials and try again."
            "weak_password" in lower || "password should be at least" in lower ->
                "Password is too weak. Use at least 8 characters with letters and numbers."
            "email not confirmed" in lower ->
                "Email confirmation is currently enabled on your Supabase Auth project. Disable 'Confirm email' in Supabase Auth settings for instant registration."
            "expired" in lower || "otp_expired" in lower ->
                "This recovery link has expired or already been used. Please request a new reset email."
            else -> message
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        birthDateIso: String,
        countryCode: String,
    ): Result<PersistedSession> = guardConfigured {
        val trimmedEmail = email.trim()
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            return@guardConfigured Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 8) {
            return@guardConfigured Result.failure(IllegalArgumentException("Password must be at least 8 characters."))
        }
        if (!AppConfig.isEligibleAge16Plus(birthDateIso)) {
            return@guardConfigured Result.failure(
                IllegalArgumentException("You must be at least 16 years old to join Skillect."),
            )
        }
        val response = authApi.signUp(
            SignUpRequest(
                email = trimmedEmail,
                password = password,
                data = mapOf(
                    "birth_date" to birthDateIso.trim(),
                    "country_code" to countryCode,
                ),
            ),
        )
        if (!response.isSuccessful) {
            return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        }
        val body = response.body()
        val userId = body?.user?.id.orEmpty()
        val accessToken = body?.accessToken.orEmpty()
        val session = PersistedSession(
            accessToken = accessToken,
            refreshToken = body?.refreshToken.orEmpty(),
            userId = userId,
            email = trimmedEmail,
        )
        if (accessToken.isNotBlank() && userId.isNotBlank()) {
            sessionStore.saveSession(session)
        }
        analyticsTracker.logEvent("sign_up", mapOf("country" to countryCode))
        Result.success(session)
    }

    suspend fun signIn(email: String, password: String): Result<PersistedSession> = guardConfigured {
        val response = authApi.signInWithPassword(
            PasswordSignInRequest(email = email.trim(), password = password),
        )
        if (!response.isSuccessful) {
            return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        }
        val body = response.body() ?: return@guardConfigured Result.failure(IllegalStateException("Empty auth response"))
        val session = PersistedSession(
            accessToken = body.accessToken.orEmpty(),
            refreshToken = body.refreshToken.orEmpty(),
            userId = body.user?.id.orEmpty(),
            email = body.user?.email ?: email.trim(),
        )
        sessionStore.saveSession(session)
        if (session.userId.isNotBlank()) {
        }
        analyticsTracker.logEvent("login")
        Result.success(session)
    }

    fun syncTelemetryAndPushToken(userId: String) {
        if (userId.isBlank()) return
        analyticsTracker.setUserId(userId)
        analyticsTracker.fetchFcmToken { token -> syncFcmTokenAsync(token) }
    }

    suspend fun fetchCurrentAuthUserMetadata(): Map<String, Any?> {
        if (!isConfiguredCheck()) return emptyMap()
        return try {
            val resp = authApi.getCurrentUser()
            if (resp.isSuccessful) resp.body()?.userMetadata.orEmpty() else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun recoverPassword(email: String): Result<Unit> = guardConfigured {
        val clean = email.trim()
        if (clean.isBlank() || !clean.contains("@")) {
            return@guardConfigured Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        val resp = authApi.recoverPassword(
            RecoverPasswordRequest(
                email = clean,
                redirectTo = "skillect://auth-callback",
            ),
        )
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun completePasswordRecovery(recoveryAccessToken: String, newPassword: String): Result<Unit> =
        guardConfigured {
            if (recoveryAccessToken.isBlank()) {
                return@guardConfigured Result.failure(
                    IllegalArgumentException("Invalid or expired password recovery token."),
                )
            }
            if (newPassword.length < 8) {
                return@guardConfigured Result.failure(
                    IllegalArgumentException("New password must be at least 8 characters."),
                )
            }
            val resp = authApi.updateUserPassword(
                bearerToken = "Bearer $recoveryAccessToken",
                request = UpdatePasswordRequest(password = newPassword),
            )
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
        }

    suspend fun refreshSessionIfNeeded(): Result<PersistedSession?> = guardConfigured {
        val current = sessionStore.currentSession()
        if (!current.isAuthenticated || current.refreshToken.isBlank()) {
            return@guardConfigured Result.success(null)
        }
        val resp = authApi.refreshToken(RefreshTokenRequest(current.refreshToken))
        if (!resp.isSuccessful) {
            if (resp.code() == 400 || resp.code() == 401) {
                sessionStore.clearSession()
                return@guardConfigured Result.success(null)
            }
            return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        }
        val body = resp.body() ?: return@guardConfigured Result.success(current)
        val refreshed = current.copy(
            accessToken = body.accessToken.orEmpty().ifBlank { current.accessToken },
            refreshToken = body.refreshToken.orEmpty().ifBlank { current.refreshToken },
        )
        sessionStore.saveSession(refreshed)
        Result.success(refreshed)
    }

    suspend fun signOut(): Result<Unit> {
        val currentSession = sessionStore.currentSession()
        if (isConfiguredCheck() && currentSession.userId.isNotBlank()) {
            try {
                val fcmToken = analyticsTracker.currentFcmTokenOrNull()
                if (!fcmToken.isNullOrBlank()) {
                    postgrestApi.deleteDeviceToken(
                        userIdEq = "eq.${currentSession.userId}",
                        platformEq = "eq.android",
                        tokenEq = "eq.$fcmToken",
                    )
                }
            } catch (_: Exception) {
                // Token cleanup is best-effort; never block signing out.
            }
            try {
                authApi.signOut()
            } catch (_: Exception) {
            }
        }
        realtimeClient.disconnect()
        analyticsTracker.setUserId(null)
        sessionStore.clearSession()
        return Result.success(Unit)
    }

    suspend fun requestAccountDeletion(): Result<Unit> = guardConfigured {
        requireUserId("delete your account") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("delete your account"))
        val resp = postgrestApi.requestAccountDeletion()
        if (resp.isSuccessful) {
            signOut()
            Result.success(Unit)
        } else {
            val rpcResp = postgrestApi.requestAccountDeletionRpc()
            if (rpcResp.isSuccessful) {
                signOut()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(resp.parseError()))
            }
        }
    }

    suspend fun checkUsernameAvailable(username: String): Result<Boolean> = guardConfigured {
        val clean = username.trim().lowercase()
        val resp = postgrestApi.getProfiles(select = "id", usernameFilter = "eq.$clean", limit = 1)
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().isEmpty())
    }

    suspend fun fetchMyProfile(): Result<ProfileDto?> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(null)
        val resp = postgrestApi.getProfiles(idFilter = "eq.$uid", limit = 1)
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()?.firstOrNull())
    }

    suspend fun fetchProfileById(userId: String): Result<ProfileDto?> = guardConfigured {
        val resp = postgrestApi.getProfiles(idFilter = "eq.$userId", limit = 1)
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()?.firstOrNull())
    }

    suspend fun upsertProfile(profile: ProfileUpsertDto): Result<ProfileDto> = guardConfigured {
        val uid = requireUserId("create or edit a profile")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("create or edit a profile"))
        val cleanUsername = profile.username.trim().lowercase()
        if (!cleanUsername.matches(Regex("^[a-z0-9_.]{3,30}$"))) {
            return@guardConfigured Result.failure(IllegalArgumentException("Username must be 3–30 characters (letters, numbers, _ or .)."))
        }
        if (profile.displayName.isBlank()) {
            return@guardConfigured Result.failure(IllegalArgumentException("Display name is required."))
        }
        val birth = profile.birthDate?.trim().orEmpty()
        if (birth.isBlank() || !AppConfig.isEligibleAge16Plus(birth)) {
            return@guardConfigured Result.failure(IllegalArgumentException("A valid date of birth proving you are at least 16 is required."))
        }
        val safeLinks = profile.portfolioLinks.filter { AppConfig.isValidHttpUrl(it.url) }
        val resolvedWebsite = profile.website.trim().ifBlank { safeLinks.firstOrNull()?.url.orEmpty() }
        val countryCode = profile.countryCode.trim().ifBlank { "US" }
        val countryName = profile.regionLabel.trim().ifBlank { countryCode }

        // The live database requires moderated profile writes to pass through this
        // server-side gateway; direct authenticated profile upserts are rejected.
        val moderationBody = mutableMapOf<String, Any?>(
            "type" to "profile",
            "display_name" to profile.displayName.trim().take(80),
            "username" to cleanUsername,
            "bio" to profile.bio.trim().take(AppConfig.MAX_BIO_LENGTH),
            "skills" to profile.skills,
            "interests" to profile.interests,
            "country" to countryCode,
            "location" to countryName,
            "website" to resolvedWebsite,
            "date_of_birth" to birth,
            "is_private" to profile.isPrivate,
            "onboarded" to true,
        )
        profile.avatarUrl?.takeIf { it.isNotBlank() }?.let { moderationBody["avatar_url"] = it }

        val moderated = postgrestApi.moderateText(moderationBody)
        if (!moderated.isSuccessful) {
            return@guardConfigured Result.failure(IllegalStateException(moderated.parseError()))
        }
        if (moderated.body()?.profileSaved != true) {
            return@guardConfigured Result.failure(IllegalStateException("Profile save could not be confirmed by Skillect safety screening."))
        }

        val followUpFields = mutableMapOf<String, Any?>(
            "type" to "profile",
            "display_name" to profile.displayName.trim().take(80),
            "username" to cleanUsername,
            "bio" to profile.bio.trim().take(AppConfig.MAX_BIO_LENGTH),
            "location" to countryName,
            "website" to resolvedWebsite,
            "skills" to profile.skills,
            "interests" to profile.interests,
            "country" to countryCode,
            "is_private" to profile.isPrivate,
            "content" to listOf(profile.displayName, cleanUsername, profile.bio, countryName, resolvedWebsite, profile.skills.joinToString(", "), profile.interests.joinToString(", ")).filter { it.isNotBlank() }.joinToString("\n"),
        )
        profile.avatarUrl?.takeIf { it.isNotBlank() }?.let { followUpFields["avatar_url"] = it }
        val followUp = postgrestApi.moderateText(followUpFields)
        if (!followUp.isSuccessful || followUp.body()?.profileSaved != true) {
            return@guardConfigured Result.failure(IllegalStateException(followUp.parseError().ifBlank { "Profile details could not be saved." }))
        }

        val savedResponse = postgrestApi.getProfiles(idFilter = "eq.$uid", limit = 1)
        if (!savedResponse.isSuccessful) {
            return@guardConfigured Result.failure(IllegalStateException(savedResponse.parseError()))
        }
        val saved = savedResponse.body()?.firstOrNull()
            ?: return@guardConfigured Result.failure(IllegalStateException("Profile save returned no profile."))
        Result.success(saved)
    }

    suspend fun updateProfileFields(userId: String, updates: Map<String, Any?>): Result<ProfileDto> = guardConfigured {
        val uid = requireUserId("edit your profile") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("edit your profile"))
        if (userId != uid) return@guardConfigured Result.failure(IllegalArgumentException("You can only edit your own profile."))
        val existingResponse = postgrestApi.getProfiles(idFilter = "eq.$uid", limit = 1)
        if (!existingResponse.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(existingResponse.parseError()))
        val existing = existingResponse.body()?.firstOrNull()
            ?: return@guardConfigured Result.failure(IllegalStateException("Your profile could not be found."))
        val sanitized = updates.toMutableMap()
        if (sanitized.containsKey("region_label") && !sanitized.containsKey("location")) {
            sanitized["location"] = sanitized.remove("region_label")
        }
        sanitized.remove("region_label")
        sanitized.remove("headline")
        sanitized.remove("role")
        sanitized.remove("is_verified")
        sanitized.remove("account_status")
        sanitized.remove("followers_count")
        sanitized.remove("following_count")
        sanitized.remove("posts_count")
        sanitized.remove("allow_dms_from")
        val body = mutableMapOf<String, Any?>(
            "type" to "profile",
            "username" to existing.username,
            "display_name" to existing.displayName,
            "bio" to existing.bio,
            "location" to existing.regionLabel,
            "website" to existing.website,
            "skills" to existing.skills,
            "interests" to existing.interests,
            "country" to existing.countryCode,
            "date_of_birth" to existing.birthDate,
            "is_private" to existing.isPrivate,
            "onboarded" to existing.onboarded,
            // Null avatar URLs must be omitted: the moderation gateway only accepts
            // a preset avatar or a secure HTTPS photo when the field is supplied.
            "cover_url" to existing.coverUrl,
        )
        if (sanitized.containsKey("avatar_url") && sanitized["avatar_url"]?.toString().isNullOrBlank()) {
            sanitized.remove("avatar_url")
        }
        body.putAll(sanitized)
        if (!body.containsKey("avatar_url")) {
            existing.avatarUrl?.takeIf { it.isNotBlank() }?.let { body["avatar_url"] = it }
        }
        val content = listOf(
            body["display_name"]?.toString(),
            body["username"]?.toString(),
            body["bio"]?.toString(),
            body["location"]?.toString(),
            body["website"]?.toString(),
            (body["skills"] as? List<*>)?.joinToString(", "),
            (body["interests"] as? List<*>)?.joinToString(", "),
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("\n")
        body["content"] = content
        val moderated = postgrestApi.moderateText(body)
        if (!moderated.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(moderated.parseError()))
        if (moderated.body()?.profileSaved != true) {
            return@guardConfigured Result.failure(IllegalStateException("Profile update could not be confirmed by Skillect safety screening."))
        }
        val savedResponse = postgrestApi.getProfiles(idFilter = "eq.$uid", limit = 1)
        if (!savedResponse.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(savedResponse.parseError()))
        val saved = savedResponse.body()?.firstOrNull()
            ?: return@guardConfigured Result.failure(IllegalStateException("Profile update returned no profile."))

        val unpersistedFields = sanitized.keys.filter { key ->
            val expected = sanitized[key]
            val actual: Any? = when (key) {
                "display_name" -> saved.displayName
                "website" -> saved.website
                "bio" -> saved.bio
                "location" -> saved.regionLabel
                "interests" -> saved.interests
                "skills" -> saved.skills
                "avatar_url" -> saved.avatarUrl
                "cover_url" -> saved.coverUrl
                "is_private" -> saved.isPrivate
                else -> expected
            }
            when (expected) {
                is String -> actual?.toString().orEmpty().trim() != expected.trim()
                is List<*> -> (actual as? List<*>)?.map { it?.toString().orEmpty().trim() } !=
                    expected.map { it?.toString().orEmpty().trim() }
                else -> actual != expected
            }
        }
        if (unpersistedFields.isNotEmpty()) {
            return@guardConfigured Result.failure(
                IllegalStateException(
                    "These profile fields were not saved: ${unpersistedFields.joinToString(", ")}. Please retry.",
                ),
            )
        }
        Result.success(saved)
    }

    suspend fun syncUserSkills(skillNames: List<String>): Result<Unit> = guardConfigured {
        val uid = requireUserId("save skills") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("save skills"))
        val cleanSkills = skillNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        updateProfileFields(uid, mapOf("skills" to cleanSkills)).map { Unit }
    }

    private fun PostDto.normalizedForUi(): PostDto {
        val resolvedMediaUrls = mediaUrls.ifEmpty {
            listOfNotNull(imageUrl?.takeIf { it.isNotBlank() })
        }
        val resolvedMediaTypes = mediaTypes.ifEmpty {
            resolvedMediaUrls.map { url ->
                val lower = url.lowercase()
                if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm")) "video" else "image"
            }
        }
        val resolvedType = when {
            postType != "text" -> postType
            !category.isNullOrBlank() -> category
            resolvedMediaTypes.contains("video") -> "video"
            resolvedMediaUrls.size > 1 -> "carousel"
            resolvedMediaUrls.size == 1 -> "image"
            else -> "text"
        }
        val resolvedSkills = skillTags.ifEmpty { tags }
        return copy(
            postType = resolvedType,
            mediaUrls = resolvedMediaUrls,
            mediaTypes = resolvedMediaTypes,
            skillTags = resolvedSkills,
        )
    }

    suspend fun fetchFeed(tab: FeedTab, limit: Int = 20, offset: Int = 0): Result<List<PostDto>> = guardConfigured {
        val trace = analyticsTracker.startTrace("fetch_feed_${tab.name.lowercase()}")
        try {
            val session = sessionStore.currentSession()
            if (tab == FeedTab.FOLLOWING && session.userId.isBlank()) {
                return@guardConfigured Result.success(emptyList())
            }
            val order = when (tab) {
                FeedTab.FOLLOWING -> "created_at.desc"
                FeedTab.RECOMMENDED -> "likes_count.desc,created_at.desc"
                FeedTab.DISCOVER -> "created_at.desc"
            }
            val authorFilter = if (tab == FeedTab.FOLLOWING && session.userId.isNotBlank()) {
                val followingIds = getFollowingIds(session.userId).getOrDefault(emptyList()) + session.userId
                "in.(${followingIds.joinToString(",")})"
            } else {
                null
            }
            val resp = postgrestApi.getPosts(
                authorIdFilter = authorFilter,
                order = order,
                limit = limit,
                offset = offset,
            )
            if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
            Result.success(resp.body().orEmpty().map { it.normalizedForUi() })
        } finally {
            analyticsTracker.stopTrace(trace)
        }
    }

    suspend fun fetchMyLikedPostIds(): Result<Set<String>> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(emptySet())
        val likedIds = linkedSetOf<String>()
        var offset = 0
        while (true) {
            val resp = postgrestApi.getPostLikes(userIdEq = "eq.$uid", limit = 200, offset = offset)
            if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
            val page = resp.body().orEmpty()
            likedIds.addAll(page.map { it.postId })
            if (page.size < 200) break
            offset += page.size
        }
        Result.success(likedIds)
    }

    suspend fun fetchMyPollVotes(): Result<Map<String, String>> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(emptyMap())
        val resp = postgrestApi.getPollVotes(userIdEq = "eq.$uid")
        if (resp.code() == 404) return@guardConfigured Result.success(emptyMap())
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().associate { it.postId to it.optionId })
    }

    suspend fun fetchShortVideos(): Result<List<PostDto>> = guardConfigured {
        val resp = postgrestApi.getPosts(order = "created_at.desc", limit = 30)
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        val videos = resp.body().orEmpty()
            .map { it.normalizedForUi() }
            .filter { it.postType == "video" || it.mediaTypes.contains("video") }
        Result.success(videos)
    }

    suspend fun fetchPostsByAuthor(authorId: String): Result<List<PostDto>> = guardConfigured {
        val resp = postgrestApi.getPosts(authorIdFilter = "eq.$authorId", limit = 30)
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().map { it.normalizedForUi() })
    }

    suspend fun createPost(
        request: CreatePostRequest,
        pollOptionTexts: List<String> = emptyList(),
    ): Result<PostDto> = guardConfigured {
        val uid = requireUserId("create a post") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("create a post"))
        val trimmedContent = request.content.trim()
        if (trimmedContent.isBlank() && request.repostedPostId == null) {
            return@guardConfigured Result.failure(IllegalArgumentException("Post content is required."))
        }
        if (trimmedContent.length > AppConfig.MAX_POST_LENGTH) {
            return@guardConfigured Result.failure(IllegalArgumentException("Post exceeds ${AppConfig.MAX_POST_LENGTH} character limit."))
        }
        val isRepost = request.repostedPostId != null || request.quotedPostId != null
        if (!isRepost && (!request.imageUrl.isNullOrBlank() || request.mediaUrls.isNotEmpty())) {
            return@guardConfigured Result.failure(IllegalArgumentException("New photo and video posts must use Skillect's secure media upload flow."))
        }
        val cleanLink = request.linkUrl?.trim()?.takeIf { it.isNotBlank() }
        if (cleanLink != null && !AppConfig.isValidHttpUrl(cleanLink)) {
            return@guardConfigured Result.failure(IllegalArgumentException("Attached link must be a valid http:// or https:// URL."))
        }
        val cleanPollOptions = pollOptionTexts.map { it.trim() }.filter { it.isNotBlank() }
        if (request.postType == "poll" && cleanPollOptions.size < 2) {
            return@guardConfigured Result.failure(IllegalArgumentException("Polls must include at least 2 options."))
        }
        val finalContent = if (cleanLink != null && !trimmedContent.contains(cleanLink)) {
            listOf(trimmedContent, cleanLink).filter { it.isNotBlank() }.joinToString("\n").take(AppConfig.MAX_POST_LENGTH)
        } else trimmedContent
        val mergedTags = (request.tags + request.skillTags + request.hashtags)
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
        val body = mutableMapOf<String, Any?>(
            "type" to "post",
            "content" to finalContent,
            "post_type" to request.postType,
            "tags" to mergedTags,
            "hashtags" to request.hashtags,
            "mentions" to request.mentions,
            "skill_tags" to request.skillTags,
            "media_urls" to (request.mediaUrls.ifEmpty { listOfNotNull(request.imageUrl) }),
            "media_types" to request.mediaTypes,
            "category" to (request.category ?: request.postType),
        )
        cleanLink?.let { body["link_url"] = it }
        request.linkTitle?.takeIf { it.isNotBlank() }?.let { body["link_title"] = it.trim().take(160) }
        request.projectId?.let { body["project_id"] = it }
        request.repostedPostId?.let { body["reposted_post_id"] = it }
        request.quotedPostId?.let { body["quoted_post_id"] = it }
        request.communityId?.let { body["community_id"] = it }
        val moderated = postgrestApi.moderateText(body)
        if (!moderated.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(moderated.parseError()))
        val createdId = moderated.body()?.id ?: moderated.body()?.postId
            ?: return@guardConfigured Result.failure(IllegalStateException("Post could not be confirmed by Skillect safety screening."))
        val postResponse = postgrestApi.getPosts(idFilter = "eq.$createdId", limit = 1)
        if (!postResponse.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(postResponse.parseError()))
        val created = postResponse.body()?.firstOrNull()?.normalizedForUi()
            ?: return@guardConfigured Result.failure(IllegalStateException("Published post could not be loaded."))
        if (cleanPollOptions.isNotEmpty()) {
            val rows = cleanPollOptions.mapIndexed { idx, option ->
                mapOf("post_id" to created.id, "option_text" to option.take(120), "sort_order" to idx)
            }
            try { postgrestApi.createPollOptions(rows) } catch (_: Exception) { }
        }
        analyticsTracker.logEvent("create_post", mapOf("post_type" to request.postType))
        Result.success(created)
    }

    suspend fun createVideoPost(
        storagePath: String,
        content: String,
        tags: List<String>,
        category: String,
        communityId: String?,
    ): Result<String> = guardConfigured {
        val uid = requireUserId("publish a video") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("publish a video"))
        if (!storagePath.startsWith("$uid/") || storagePath.contains("..")) {
            return@guardConfigured Result.failure(IllegalArgumentException("Invalid private video upload path."))
        }
        val body = mutableMapOf<String, Any?>(
            "bucket" to "post-media",
            "storage_path" to storagePath,
            "post_type" to "video",
            "content" to content.trim().take(AppConfig.MAX_POST_LENGTH),
            "tags" to tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().take(10),
            "category" to category,
        )
        communityId?.let { body["community_id"] = it }
        val response = postgrestApi.submitVideoPost(body)
        if (!response.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        val postId = response.body()?.postId ?: response.body()?.id
            ?: return@guardConfigured Result.failure(IllegalStateException(response.body()?.message ?: "Video post could not be confirmed."))
        Result.success(postId)
    }

    suspend fun editPost(postId: String, content: String): Result<PostDto> = guardConfigured {
        requireUserId("edit posts") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("edit posts"))
        val clean = content.trim()
        if (clean.isBlank()) {
            return@guardConfigured Result.failure(IllegalArgumentException("Post content cannot be empty."))
        }
        val resp = postgrestApi.updatePost(
            idEq = "eq.$postId",
            updates = mapOf("content" to clean.take(AppConfig.MAX_POST_LENGTH)),
        )
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        val updated = resp.body()?.firstOrNull()?.normalizedForUi()
            ?: return@guardConfigured Result.failure(IllegalStateException("Update returned empty body"))
        Result.success(updated)
    }

    suspend fun deletePost(postId: String): Result<Unit> = guardConfigured {
        requireUserId("delete posts") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("delete posts"))
        val resp = postgrestApi.deletePost(idEq = "eq.$postId")
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun toggleLike(postId: String): Result<Boolean> = guardConfigured {
        val uid = requireUserId("like posts") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("like posts"))
        val current = postgrestApi.getPostLikes(
            userIdEq = "eq.$uid",
            postIdEq = "eq.$postId",
            limit = 1,
        )
        if (!current.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(current.parseError()))
        val currentlyLiked = !current.body().isNullOrEmpty()
        val resp = if (currentlyLiked) {
            postgrestApi.unlikePost("eq.$postId", "eq.$uid")
        } else {
            postgrestApi.likePost(mapOf("post_id" to postId, "user_id" to uid))
        }
        if (resp.isSuccessful) Result.success(!currentlyLiked)
        else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun votePoll(postId: String, optionId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("vote in polls") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("vote in polls"))
        val resp = postgrestApi.castPollVote(mapOf("post_id" to postId, "option_id" to optionId, "user_id" to uid))
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun fetchComments(postId: String): Result<List<CommentDto>> = guardConfigured {
        val resp = postgrestApi.getComments(postIdEq = "eq.$postId")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun addComment(postId: String, content: String, parentCommentId: String? = null): Result<CommentDto> =
        guardConfigured {
            requireUserId("comment or reply") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("comment or reply"))
            val clean = content.trim()
            if (clean.isBlank()) return@guardConfigured Result.failure(IllegalArgumentException("Comment cannot be blank."))
            val moderated = postgrestApi.moderateText(
                mapOf(
                    "type" to "comment",
                    "content" to clean.take(AppConfig.MAX_COMMENT_LENGTH),
                    "post_id" to postId,
                    "parent_id" to parentCommentId,
                    "parent_comment_id" to parentCommentId,
                ),
            )
            if (!moderated.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(moderated.parseError()))
            val commentId = moderated.body()?.commentId ?: moderated.body()?.id
                ?: return@guardConfigured Result.failure(IllegalStateException("Comment could not be confirmed by Skillect safety screening."))
            val saved = postgrestApi.getComments(postIdEq = "eq.$postId", idEq = "eq.$commentId")
            if (!saved.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(saved.parseError()))
            Result.success(saved.body()?.firstOrNull() ?: return@guardConfigured Result.failure(IllegalStateException("Comment was saved but could not be loaded.")))
        }

    suspend fun fetchActiveStories(): Result<List<StoryDto>> = guardConfigured {
        val resp = postgrestApi.getActiveStories()
        if (resp.code() == 404) return@guardConfigured Result.success(emptyList())
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        val stories = resp.body().orEmpty()
        val resolved = coroutineScope {
            stories.map { story -> async(Dispatchers.IO) { createSignedStory(story) } }
                .awaitAll()
                .filterNotNull()
        }
        Result.success(resolved)
    }

    private suspend fun createSignedStory(story: StoryDto): StoryDto? {
        val api = storageApi ?: return null
        val path = story.mediaUrl.trim()
        if (path.isBlank()) return null
        if (path.startsWith("http", ignoreCase = true)) return story
        return try {
            val body = JSONObject().put("expiresIn", 24 * 60 * 60)
                .toString().toRequestBody("application/json".toMediaType())
            val response = api.createSignedUrl("stories-media", path, body)
            if (!response.isSuccessful) return null
            val signedPath = JSONObject(response.body()?.string().orEmpty())
                .optString("signedURL")
                .takeIf { it.isNotBlank() }
                ?: return null
            val signedUrl = if (signedPath.startsWith("http", ignoreCase = true)) {
                signedPath
            } else {
                val base = AppConfig.supabaseUrl.trimEnd('/')
                when {
                    signedPath.startsWith("/storage/v1/") -> base + signedPath
                    signedPath.startsWith("/object/") -> base + "/storage/v1" + signedPath
                    signedPath.startsWith("object/") -> base + "/storage/v1/" + signedPath
                    else -> base + "/storage/v1/" + signedPath.trimStart('/')
                }
            }
            story.copy(mediaUrl = signedUrl)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createStory(mediaUrl: String, mediaType: String, caption: String): Result<StoryDto> = guardConfigured {
        val uid = requireUserId("share a story") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("share a story"))
        val resp = postgrestApi.createStory(CreateStoryRequest(uid, mediaUrl, mediaType, caption.trim().take(280)))
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()!!.first())
    }

    suspend fun deleteStory(storyId: String): Result<Unit> = guardConfigured {
        requireUserId("delete stories") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("delete stories"))
        val resp = postgrestApi.deleteStory("eq.$storyId")
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun fetchFollowCounts(userId: String): Result<Pair<Int, Int>> = guardConfigured {
        val followers = postgrestApi.countFollowers(followingIdEq = "eq.$userId")
        val following = postgrestApi.countFollowing(followerIdEq = "eq.$userId")
        fun exactCount(response: retrofit2.Response<Void>): Int? =
            response.headers()["Content-Range"]?.substringAfterLast("/")?.toIntOrNull()
        val followerCount = followers.takeIf { it.isSuccessful }?.let(::exactCount)
        val followingCount = following.takeIf { it.isSuccessful }?.let(::exactCount)
        if (followerCount == null || followingCount == null) {
            val followerRows = postgrestApi.getFollows(followingIdEq = "eq.$userId")
            val followingRows = postgrestApi.getFollows(followerIdEq = "eq.$userId")
            if (!followerRows.isSuccessful || !followingRows.isSuccessful) {
                return@guardConfigured Result.failure(IllegalStateException("Could not load network counts."))
            }
            return@guardConfigured Result.success(followerRows.body().orEmpty().size to followingRows.body().orEmpty().size)
        }
        Result.success(followerCount to followingCount)
    }

    suspend fun fetchPostCount(userId: String): Result<Int> = guardConfigured {
        val response = postgrestApi.countPostsByAuthor(authorIdEq = "eq.$userId")
        if (!response.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        val count = response.headers()["Content-Range"]?.substringAfterLast("/")?.toIntOrNull()
            ?: return@guardConfigured Result.failure(IllegalStateException("Post count was not returned."))
        Result.success(count)
    }

    suspend fun getFollowingIds(userId: String): Result<List<String>> = guardConfigured {
        val resp = postgrestApi.getFollows(followerIdEq = "eq.$userId")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().map { it.followingId })
    }

    suspend fun toggleFollow(targetUserId: String, currentlyFollowing: Boolean? = null): Result<FollowToggleResult> = guardConfigured {
        val uid = requireUserId("follow creators") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("follow creators"))
        if (uid == targetUserId) return@guardConfigured Result.failure(IllegalArgumentException("Cannot follow yourself"))
        val wasFollowing = currentlyFollowing ?: run {
            val current = postgrestApi.getFollows(
                followerIdEq = "eq.$uid",
                followingIdEq = "eq.$targetUserId",
                limit = 1,
            )
            if (!current.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(current.parseError()))
            !current.body().isNullOrEmpty()
        }
        val response = if (wasFollowing) {
            postgrestApi.unfollowUser("eq.$uid", "eq.$targetUserId")
        } else {
            postgrestApi.followUser(FollowRowDto(uid, targetUserId))
        }
        if (response.isSuccessful) Result.success(FollowToggleResult(wasFollowing, !wasFollowing))
        else Result.failure(IllegalStateException(response.parseError()))
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("block accounts") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("block accounts"))
        if (uid == targetUserId) return@guardConfigured Result.failure(IllegalArgumentException("Cannot block yourself"))
        val resp = postgrestApi.blockUser(mapOf("blocker_id" to uid, "blocked_id" to targetUserId))
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("unblock accounts") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("unblock accounts"))
        val resp = postgrestApi.unblockUser("eq.$uid", "eq.$targetUserId")
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun fetchBlockedAccounts(): Result<List<BlockRowDto>> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(emptyList())
        val resp = postgrestApi.getBlocks("eq.$uid")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        val rows = resp.body().orEmpty()
        if (rows.isEmpty()) return@guardConfigured Result.success(emptyList())
        val ids = rows.map { it.blockedId }.distinct()
        val profiles = postgrestApi.getProfiles(idFilter = "in.(" + ids.joinToString(",") + ")", limit = ids.size.coerceAtMost(1000))
        val byId = if (profiles.isSuccessful) profiles.body().orEmpty().associateBy { it.id } else emptyMap()
        Result.success(rows.map { row -> row.copy(blocked = byId[row.blockedId]) })
    }

    suspend fun fetchNetworkProfiles(userId: String, followers: Boolean): Result<List<ProfileDto>> = guardConfigured {
        if (userId.isBlank()) return@guardConfigured Result.success(emptyList())
        val follows = if (followers) {
            postgrestApi.getFollows(followingIdEq = "eq." + userId, limit = 1000)
        } else {
            postgrestApi.getFollows(followerIdEq = "eq." + userId, limit = 1000)
        }
        if (!follows.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(follows.parseError()))
        val ids = follows.body().orEmpty().map { if (followers) it.followerId else it.followingId }.distinct()
        if (ids.isEmpty()) return@guardConfigured Result.success(emptyList())
        val profiles = postgrestApi.getProfiles(idFilter = "in.(" + ids.joinToString(",") + ")", limit = ids.size.coerceAtMost(1000))
        if (!profiles.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(profiles.parseError()))
        val byId = profiles.body().orEmpty().associateBy { it.id }
        Result.success(ids.mapNotNull(byId::get))
    }

    suspend fun muteUser(targetUserId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("mute accounts") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("mute accounts"))
        if (uid == targetUserId) return@guardConfigured Result.failure(IllegalArgumentException("Cannot mute yourself"))
        val resp = postgrestApi.muteUser(MuteRowDto(uid, targetUserId))
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun searchProfiles(query: String): Result<List<ProfileDto>> = guardConfigured {
        val sanitized = query.trim().replace(",", "").replace("(", "").replace(")", "")
        val filter = if (sanitized.isBlank()) null else "(username.ilike.*$sanitized*,display_name.ilike.*$sanitized*,bio.ilike.*$sanitized*)"
        val resp = postgrestApi.getProfiles(orFilter = filter, limit = 25)
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun searchPosts(query: String, sort: String = "created_at.desc"): Result<List<PostDto>> = guardConfigured {
        val sanitized = query.trim().replace(",", "").replace("(", "").replace(")", "")
        val filter = if (sanitized.isBlank()) null else "(content.ilike.*$sanitized*)"
        val resp = postgrestApi.getPosts(orFilter = filter, order = sort, limit = 25)
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().map { it.normalizedForUi() })
    }

    suspend fun fetchCommunities(category: String? = null, query: String = ""): Result<List<CommunityDto>> =
        guardConfigured {
            val sanitized = query.trim().replace(",", "").replace("(", "").replace(")", "")
            val catFilter = category?.takeIf { it != "All" }?.let { "eq.$it" }
            val orFilter = if (sanitized.isBlank()) null else "(name.ilike.*$sanitized*,description.ilike.*$sanitized*)"
            val resp = postgrestApi.getCommunities(categoryEq = catFilter, orFilter = orFilter)
            if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
            Result.success(resp.body().orEmpty())
        }

    suspend fun fetchMyJoinedCommunityIds(): Result<Set<String>> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(emptySet())
        val resp = postgrestApi.getCommunityMembers(userIdEq = "eq.$uid", select = "community_id,user_id,role,created_at")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().map { it.communityId }.toSet())
    }

    suspend fun createCommunity(
        name: String,
        description: String,
        category: String,
        isPrivate: Boolean,
    ): Result<CommunityDto> = guardConfigured {
        val uid = requireUserId("create a community") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("create a community"))
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            return@guardConfigured Result.failure(IllegalArgumentException("Community name must be at least 2 characters."))
        }
        val slugBase = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "group" }
        val slug = "$slugBase-${(100..999).random()}"
        val resp = postgrestApi.createCommunity(
            CreateCommunityRequest(
                slug = slug,
                name = cleanName.take(80),
                description = description.trim().take(1000),
                category = category,
                isPrivate = isPrivate,
                creatorId = uid,
            ),
        )
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        val created = resp.body()?.firstOrNull()
            ?: return@guardConfigured Result.failure(IllegalStateException("The community was not returned after creation."))
        val membership = postgrestApi.joinCommunity(
            mapOf("community_id" to created.id, "user_id" to uid, "role" to "owner"),
        )
        if (!membership.isSuccessful) {
            return@guardConfigured Result.failure(
                IllegalStateException(
                    "Community created, but owner membership could not be confirmed. Refresh Communities and check your membership.",
                ),
            )
        }
        Result.success(created)
    }

    suspend fun joinCommunity(communityId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("join communities") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("join communities"))
        val resp = postgrestApi.joinCommunity(mapOf("community_id" to communityId, "user_id" to uid, "role" to "member"))
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun leaveCommunity(communityId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("leave communities") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("leave communities"))
        val resp = postgrestApi.leaveCommunity("eq.$communityId", "eq.$uid")
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun fetchCommunityMembers(communityId: String): Result<List<CommunityMemberDto>> = guardConfigured {
        val resp = postgrestApi.getCommunityMembers(communityIdEq = "eq.$communityId")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun fetchCommunityPosts(communityId: String): Result<List<PostDto>> = guardConfigured {
        val resp = postgrestApi.getPosts(communityIdFilter = "eq.$communityId")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().map { it.normalizedForUi() })
    }

    suspend fun fetchProjects(ownerId: String? = null): Result<List<ProjectDto>> = guardConfigured {
        val resp = postgrestApi.getProjects(ownerIdEq = ownerId?.let { "eq.$it" })
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun createProject(request: CreateProjectRequest): Result<ProjectDto> = guardConfigured {
        val uid = requireUserId("showcase a project") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("showcase a project"))
        val title = request.title.trim()
        val desc = request.description.trim()
        if (title.isBlank() || desc.isBlank()) {
            return@guardConfigured Result.failure(IllegalArgumentException("Project title and description are required."))
        }
        val resp = postgrestApi.createProject(
            request.copy(
                ownerId = uid,
                title = title.take(120),
                description = desc.take(2000),
            ),
        )
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()!!.first())
    }

    suspend fun deleteProject(projectId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("delete projects") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("delete projects"))
        val response = postgrestApi.deleteProject("eq.$projectId", "eq.$uid")
        if (response.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(response.parseError()))
    }

    suspend fun fetchSkills(): Result<List<SkillDto>> = guardConfigured {
        val resp = postgrestApi.getSkills()
        if (resp.code() == 404) {
            return@guardConfigured Result.success(
                listOf(
                    SkillDto("sk_kotlin", "Kotlin", "Technology"),
                    SkillDto("sk_design", "UI Design", "Design"),
                    SkillDto("sk_photo", "Photography", "Creative Arts"),
                    SkillDto("sk_ai", "AI & ML", "Technology"),
                    SkillDto("sk_writing", "Writing", "Words"),
                    SkillDto("sk_founder", "Entrepreneurship", "Business"),
                    SkillDto("sk_video", "Video Editing", "Media"),
                    SkillDto("sk_music", "Music Production", "Performing"),
                ),
            )
        }
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun fetchExperiences(userId: String): Result<List<ExperienceDto>> = guardConfigured {
        val resp = postgrestApi.getExperiences("eq.$userId")
        if (resp.code() == 404) return@guardConfigured Result.success(emptyList())
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun createExperience(request: CreateExperienceRequest): Result<ExperienceDto> = guardConfigured {
        val uid = requireUserId("add experience") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("add experience"))
        if (request.title.isBlank() || request.organization.isBlank()) {
            return@guardConfigured Result.failure(IllegalArgumentException("Role title and organization are required."))
        }
        val resp = postgrestApi.createExperience(request.copy(userId = uid))
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()!!.first())
    }

    suspend fun deleteExperience(experienceId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("delete experience") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("delete experience"))
        val response = postgrestApi.deleteExperience("eq.$experienceId", "eq.$uid")
        if (response.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(response.parseError()))
    }

    suspend fun fetchAchievements(userId: String): Result<List<AchievementDto>> = guardConfigured {
        val resp = postgrestApi.getAchievements("eq.$userId")
        if (resp.code() == 404) return@guardConfigured Result.success(emptyList())
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun createAchievement(
        title: String,
        issuer: String,
        awardedAt: String?,
        description: String,
    ): Result<AchievementDto> = guardConfigured {
        val uid = requireUserId("add achievements") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("add achievements"))
        if (title.isBlank()) {
            return@guardConfigured Result.failure(IllegalArgumentException("Achievement title is required."))
        }
        val resp = postgrestApi.createAchievement(
            CreateAchievementRequest(
                userId = uid,
                title = title.trim().take(120),
                issuer = issuer.trim().take(120),
                awardedAt = awardedAt?.trim()?.takeIf { it.isNotBlank() },
                description = description.trim().take(1000),
            ),
        )
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()!!.first())
    }

    suspend fun deleteAchievement(achievementId: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("delete achievement") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("delete achievement"))
        val response = postgrestApi.deleteAchievement("eq.$achievementId", "eq.$uid")
        if (response.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(response.parseError()))
    }

    suspend fun startDirectConversation(peerUserId: String): Result<String> = guardConfigured {
        val uid = requireUserId("start a private conversation")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("start a private conversation"))
        if (peerUserId.isBlank()) {
            return@guardConfigured Result.failure(IllegalArgumentException("Choose a valid profile before starting a conversation."))
        }
        if (uid == peerUserId) {
            return@guardConfigured Result.failure(IllegalArgumentException("This is your own profile. Open another member's profile to message them."))
        }
        val response = postgrestApi.startConversation(StartConversationRequest(recipientId = peerUserId))
        if (!response.isSuccessful) {
            return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        }
        val result = response.body()
            ?: return@guardConfigured Result.failure(IllegalStateException("Messaging service returned an empty response."))
        if (!result.recipientId.isNullOrBlank() && result.recipientId != peerUserId) {
            return@guardConfigured Result.failure(IllegalStateException("The messaging service returned a different recipient. Please reopen the member's profile and try again."))
        }
        when (result.status) {
            "created", "existing" -> {
                val conversationId = result.conversationId.orEmpty()
                if (conversationId.isBlank()) {
                    Result.failure(IllegalStateException("Conversation could not be created."))
                } else {
                    Result.success(conversationId)
                }
            }
            "request_pending" -> Result.failure(IllegalStateException("Your message request is already pending."))
            "request_required" -> Result.failure(IllegalStateException("A message request is required before you can start this conversation."))
            else -> Result.failure(IllegalStateException(result.error ?: "Messaging could not be started."))
        }
    }

    suspend fun fetchIncomingMessageRequests(): Result<List<MessageRequestDto>> = guardConfigured {
        val uid = requireUserId("view message requests")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("view message requests"))
        val response = postgrestApi.getIncomingMessageRequests("eq.$uid")
        if (!response.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        Result.success(response.body().orEmpty())
    }

    suspend fun createMessageRequest(recipientId: String, content: String): Result<Unit> = guardConfigured {
        requireUserId("send a message request")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("send a message request"))
        val clean = content.trim()
        if (clean.isBlank()) return@guardConfigured Result.failure(IllegalArgumentException("Write a short message first."))
        val response = postgrestApi.createMessageRequest(mapOf("recipient_id" to recipientId, "content" to clean.take(2000)))
        if (!response.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        Result.success(Unit)
    }

    suspend fun respondToMessageRequest(requestId: String, accept: Boolean): Result<String?> = guardConfigured {
        requireUserId("respond to message requests")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("respond to message requests"))
        val response = postgrestApi.respondMessageRequest(
            mapOf("request_id" to requestId, "action" to if (accept) "accept" else "decline"),
        )
        if (!response.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        Result.success(response.body()?.get("conversation_id"))
    }

    suspend fun fetchConversations(): Result<List<ConversationDto>> = guardConfigured {
        requireUserId("access private messages") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("access private messages"))
        val resp = postgrestApi.getConversations()
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun fetchMessages(conversationId: String): Result<List<MessageDto>> = guardConfigured {
        requireUserId("access private messages") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("access private messages"))
        val resp = postgrestApi.getMessages("eq.$conversationId")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String? = null): Result<MessageDto> =
        guardConfigured {
            requireUserId("send private messages") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("send private messages"))
            val clean = content.trim()
            if (clean.isBlank()) return@guardConfigured Result.failure(IllegalArgumentException("Message content is required."))
            if (!mediaUrl.isNullOrBlank()) {
                return@guardConfigured Result.failure(IllegalArgumentException("Media attachments are not enabled for private messages yet."))
            }
            val moderated = postgrestApi.moderateText(
                mapOf(
                    "type" to "message",
                    "content" to clean.take(AppConfig.MAX_MESSAGE_LENGTH),
                    "conversation_id" to conversationId,
                ),
            )
            if (!moderated.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(moderated.parseError()))
            val messageId = moderated.body()?.messageId ?: moderated.body()?.id
                ?: return@guardConfigured Result.failure(IllegalStateException("Message could not be confirmed by Skillect safety screening."))
            val saved = postgrestApi.getMessages(conversationIdEq = "eq.$conversationId", idEq = "eq.$messageId")
            if (!saved.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(saved.parseError()))
            Result.success(saved.body()?.firstOrNull() ?: return@guardConfigured Result.failure(IllegalStateException("Message was sent but could not be loaded.")))
        }

    suspend fun fetchNotifications(): Result<List<NotificationDto>> = guardConfigured {
        val uid = requireUserId("view notifications") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("view notifications"))
        val resp = postgrestApi.getNotifications("eq.$uid")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun markNotificationRead(id: String): Result<Unit> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(Unit)
        val resp = postgrestApi.markNotificationsRead("eq.$uid", "eq.$id", mapOf("read" to true))
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun markAllNotificationsRead(): Result<Unit> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(Unit)
        val resp = postgrestApi.markNotificationsRead("eq.$uid", null, mapOf("read" to true))
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(IllegalStateException(resp.parseError()))
    }

    suspend fun fetchSavedCollections(): Result<List<SavedCollectionDto>> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(emptyList())
        val resp = postgrestApi.getSavedCollections("eq.$uid")
        if (resp.code() == 404) return@guardConfigured Result.success(emptyList())
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun createSavedCollection(name: String): Result<SavedCollectionDto> = guardConfigured {
        val uid = requireUserId("create saved collections") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("create saved collections"))
        val clean = name.trim()
        if (clean.isBlank()) return@guardConfigured Result.failure(IllegalArgumentException("Collection name is required."))
        val resp = postgrestApi.createSavedCollection(mapOf("owner_id" to uid, "name" to clean.take(60), "is_private" to true))
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()!!.first())
    }

    suspend fun fetchSavedPosts(): Result<List<SavedPostDto>> = guardConfigured {
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank()) return@guardConfigured Result.success(emptyList())
        val resp = postgrestApi.getSavedPosts("eq.$uid")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().map { it.copy(post = it.post?.normalizedForUi()) })
    }

    suspend fun toggleSavePost(postId: String, currentlySaved: Boolean, collectionId: String? = null): Result<Boolean> =
        guardConfigured {
            val uid = requireUserId("save content") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("save content"))
            val resp = if (currentlySaved) {
                postgrestApi.unsavePost("eq.$uid", "eq.$postId")
            } else {
                postgrestApi.savePost(mapOf("user_id" to uid, "post_id" to postId))
            }
            if (resp.isSuccessful) Result.success(!currentlySaved)
            else Result.failure(IllegalStateException(resp.parseError()))
        }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String,
        targetUserId: String? = null,
    ): Result<ReportDto> = guardConfigured {
            val uid = requireUserId("submit safety reports") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("submit safety reports"))
            val cleanReason = reason.trim()
            if (cleanReason.isBlank()) return@guardConfigured Result.failure(IllegalArgumentException("Choose a report reason."))
            val resp = postgrestApi.submitReport(
                CreateReportRequest(uid, targetType, targetId, cleanReason.take(120), details.trim().take(1000), targetUserId),
            )
            if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
            Result.success(resp.body()!!.first())
        }

    suspend fun currentUserCanModerate(): Boolean {
        val token = sessionStore.currentSession().accessToken
        val payload = token.split(".").getOrNull(1).orEmpty()
        if (payload.isBlank()) return false
        return try {
            val decoded = Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val appRole = JSONObject(String(decoded, Charsets.UTF_8))
                .optJSONObject("app_metadata")
                ?.optString("role")
            appRole == "admin" || appRole == "moderator"
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchModerationMedia(): Result<List<ModerationMediaDto>> = guardConfigured {
        requireUserId("access media moderation queue")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("access media moderation queue"))
        val response = postgrestApi.getModerationMediaQueue()
        if (!response.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        Result.success(response.body().orEmpty())
    }

    suspend fun resolveModerationMedia(mediaAssetId: String, approved: Boolean): Result<Unit> = guardConfigured {
        requireUserId("review uploaded media")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("review uploaded media"))
        val response = postgrestApi.reviewModerationMedia(
            mapOf("media_asset_id" to mediaAssetId, "decision" to if (approved) "approve" else "reject"),
        )
        if (!response.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        Result.success(Unit)
    }

    suspend fun fetchModerationReports(): Result<List<ReportDto>> = guardConfigured {
        requireUserId("access moderation reports") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("access moderation reports"))
        val resp = postgrestApi.getReports()
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> = guardConfigured {
        val uid = requireUserId("update moderation reports") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("update moderation reports"))
        val resp = postgrestApi.updateReportStatus("eq.$reportId", mapOf("status" to status))
        if (resp.isSuccessful) {
            try {
                postgrestApi.createAuditLog(
                    CreateAuditLogRequest(
                        actorAdminId = uid,
                        action = "report_$status",
                        targetType = "report",
                        targetId = reportId,
                        notes = "Report status updated to $status",
                    ),
                )
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(resp.parseError()))
        }
    }

    suspend fun fetchAuditLogs(): Result<List<ModerationAuditLogDto>> = guardConfigured {
        requireUserId("access moderation audit logs") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("access moderation audit logs"))
        val resp = postgrestApi.getAuditLogs()
        if (resp.code() == 404) return@guardConfigured Result.success(emptyList())
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun fetchModerationAppeals(): Result<List<ModerationAppealDto>> = guardConfigured {
        requireUserId("view moderation appeals") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("view moderation appeals"))
        val resp = postgrestApi.getModerationAppeals()
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }

    suspend fun fetchMyEnforcement(): Result<AccountEnforcementDto?> = guardConfigured {
        val uid = requireUserId("check account status") ?: return@guardConfigured Result.success(null)
        val resp = postgrestApi.getMyEnforcement("eq.$uid")
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty().firstOrNull())
    }

    suspend fun resolveModerationAppeal(
        appealId: String,
        enforcementUserId: String,
        approved: Boolean,
        moderatorNote: String,
    ): Result<Unit> = guardConfigured {
        requireUserId("review account appeals")
            ?: return@guardConfigured Result.failure(AuthenticationRequiredException("review account appeals"))
        val response = postgrestApi.resolveModerationAppeal(
            ResolveModerationAppealRequest(
                appealId = appealId,
                accept = approved,
                moderatorNote = moderatorNote.trim().take(1000),
            ),
        )
        if (!response.isSuccessful) {
            return@guardConfigured Result.failure(IllegalStateException(response.parseError()))
        }
        Result.success(Unit)
    }

    suspend fun submitModerationAppeal(reason: String): Result<ModerationAppealDto> = guardConfigured {
        val uid = requireUserId("submit moderation appeals") ?: return@guardConfigured Result.failure(AuthenticationRequiredException("submit moderation appeals"))
        val clean = reason.trim()
        if (clean.length < 20) {
            return@guardConfigured Result.failure(IllegalArgumentException("Please explain your appeal in at least 20 characters."))
        }
        val resp = postgrestApi.submitModerationAppeal(CreateAppealRequest(uid, uid, clean.take(5000)))
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body()!!.first())
    }

    suspend fun syncFcmToken(token: String) {
        if (!isConfiguredCheck()) return
        val uid = sessionStore.currentSession().userId
        if (uid.isBlank() || token.isBlank()) return
        try {
            // Live Supabase schema: public.device_tokens(user_id, platform, token),
            // with a unique constraint on (platform, token) and owner-only RLS.
            val response = postgrestApi.upsertDeviceToken(
                token = DeviceTokenUpsertDto(userId = uid, fcmToken = token),
            )
            if (!response.isSuccessful) {
                analyticsTracker.recordNonFatal(
                    IllegalStateException("FCM token registration failed: HTTP ${response.code()}"),
                )
            }
        } catch (_: Exception) {
            // Push registration is best-effort; never block sign-in if FCM is unavailable.
        }
    }

    private fun syncFcmTokenAsync(token: String) {
        repoScope.launch {
            syncFcmToken(token)
        }
    }
}
