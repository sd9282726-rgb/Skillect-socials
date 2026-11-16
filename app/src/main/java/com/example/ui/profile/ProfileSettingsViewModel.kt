package com.example.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.config.ThemeMode
import com.example.core.network.AchievementDto
import com.example.core.network.BlockRowDto
import com.example.core.network.CreateExperienceRequest
import com.example.core.network.CreateProjectRequest
import com.example.core.network.ExperienceDto
import com.example.core.network.ModerationAppealDto
import com.example.core.network.ModerationMediaDto
import com.example.core.network.ModerationAuditLogDto
import com.example.core.network.PostDto
import com.example.core.network.ProfileDto
import com.example.core.network.ProjectDto
import com.example.core.network.ReportDto
import com.example.core.network.SavedCollectionDto
import com.example.core.network.SavedPostDto
import com.example.core.network.UserPreferences
import com.example.data.repository.SkillectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileSettingsUiState(
    val viewedProfile: ProfileDto? = null,
    val myProfile: ProfileDto? = null,
    val posts: List<PostDto> = emptyList(),
    val projects: List<ProjectDto> = emptyList(),
    val experiences: List<ExperienceDto> = emptyList(),
    val achievements: List<AchievementDto> = emptyList(),
    val savedCollections: List<SavedCollectionDto> = emptyList(),
    val savedPosts: List<SavedPostDto> = emptyList(),
    val blockedAccounts: List<BlockRowDto> = emptyList(),
    val networkProfiles: List<ProfileDto> = emptyList(),
    val networkTitle: String = "",
    val isLoadingNetwork: Boolean = false,
    val moderationReports: List<ReportDto> = emptyList(),
    val auditLogs: List<ModerationAuditLogDto> = emptyList(),
    val moderationAppeals: List<ModerationAppealDto> = emptyList(),
    val moderationMedia: List<ModerationMediaDto> = emptyList(),
    val isModerator: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val isFollowing: Boolean = false,
    val isFollowActionPending: Boolean = false,
    val postsCount: Int? = null,
    val followersCount: Int? = null,
    val followingCount: Int? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

private data class ProfileSectionsResult(
    val posts: Result<List<PostDto>>,
    val projects: Result<List<ProjectDto>>,
    val experiences: Result<List<ExperienceDto>>,
    val achievements: Result<List<AchievementDto>>,
    val isFollowing: Result<Boolean>,
    val postCount: Result<Int>,
    val followCounts: Result<Pair<Int, Int>>,
)

private data class CachedProfileSections(
    val posts: List<PostDto>,
    val projects: List<ProjectDto>,
    val experiences: List<ExperienceDto>,
    val achievements: List<AchievementDto>,
    val isFollowing: Boolean,
    val postsCount: Int?,
    val followersCount: Int?,
    val followingCount: Int?,
)

class ProfileSettingsViewModel(
    private val repository: SkillectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSettingsUiState())
    private var profileLoadJob: Job? = null
    private val profileCache = mutableMapOf<String, ProfileDto>()
    private val profileSectionsCache = mutableMapOf<String, CachedProfileSections>()
    val uiState: StateFlow<ProfileSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessionStore.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
        loadMyProfileAndSaved()
    }

    fun loadMyProfileAndSaved() {
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            if (!repository.sessionStore.currentSession().isAuthenticated) {
                clearPrivateState()
                return@launch
            }
            viewModelScope.launch { repository.fetchConversations() }
            val my = repository.fetchMyProfile().getOrNull()
            if (my != null) repository.sessionStore.saveCachedProfile(my)
            val collections = repository.fetchSavedCollections().getOrDefault(emptyList())
            val saved = repository.fetchSavedPosts().getOrDefault(emptyList())
            val blocks = repository.fetchBlockedAccounts().getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    myProfile = my,
                    savedCollections = collections,
                    savedPosts = saved,
                    blockedAccounts = blocks,
                )
            }
        }
    }

    fun clearPrivateState() {
        profileLoadJob?.cancel()
        profileLoadJob = null
        profileCache.clear()
        profileSectionsCache.clear()
        _uiState.update {
            it.copy(
                myProfile = null,
                savedCollections = emptyList(),
                savedPosts = emptyList(),
                blockedAccounts = emptyList(),
                moderationReports = emptyList(),
                auditLogs = emptyList(),
                moderationAppeals = emptyList(),
                isModerator = false,
                isFollowing = false,
                isFollowActionPending = false,
                postsCount = null,
                followersCount = null,
                followingCount = null,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun loadProfile(targetUserId: String?) {
        if (!AppConfig.isSupabaseConfigured) return
        profileLoadJob?.cancel()
        profileLoadJob = viewModelScope.launch {
            val myId = repository.sessionStore.currentSession().userId
            val effectiveId = targetUserId?.takeIf { it.isNotBlank() } ?: myId
            if (effectiveId.isBlank()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val isOwnProfile = effectiveId == myId
            val current = _uiState.value
            val persistedProfile = if (isOwnProfile) {
                repository.sessionStore.cachedProfile(effectiveId)
            } else {
                null
            }
            val cachedProfile = profileCache[effectiveId]
                ?: persistedProfile
                ?: repository.cachedProfileById(effectiveId)
                ?: if (isOwnProfile) current.myProfile else current.viewedProfile?.takeIf { it.id == effectiveId }
            _uiState.update { state ->
                state.copy(
                    isLoading = cachedProfile == null,
                    isFollowActionPending = false,
                    errorMessage = null,
                    viewedProfile = cachedProfile,
                    posts = if (state.viewedProfile?.id == effectiveId) state.posts else emptyList(),
                    postsCount = if (state.viewedProfile?.id == effectiveId) state.postsCount else cachedProfile?.postsCount,
                    followersCount = if (state.viewedProfile?.id == effectiveId) state.followersCount else cachedProfile?.followersCount,
                    followingCount = if (state.viewedProfile?.id == effectiveId) state.followingCount else cachedProfile?.followingCount,
                )
            }

            // Only reuse private profile sections for the signed-in owner's own profile.
            // Another member's privacy/follow permissions may have changed since the cache was filled.
            val cachedSections = if (isOwnProfile) profileSectionsCache[effectiveId] else null
            _uiState.update { state ->
                // Compare against the profile that was visible before this load.
                // The earlier cache update may already have replaced viewedProfile.
                val sameProfile = current.viewedProfile?.id == effectiveId
                state.copy(
                    isLoading = cachedProfile == null && cachedSections == null,
                    isFollowActionPending = false,
                    errorMessage = null,
                    viewedProfile = cachedProfile,
                    posts = cachedSections?.posts ?: if (sameProfile) state.posts else emptyList(),
                    projects = cachedSections?.projects ?: if (sameProfile) state.projects else emptyList(),
                    experiences = cachedSections?.experiences ?: if (sameProfile) state.experiences else emptyList(),
                    achievements = cachedSections?.achievements ?: if (sameProfile) state.achievements else emptyList(),
                    isFollowing = cachedSections?.isFollowing ?: if (sameProfile) state.isFollowing else false,
                    postsCount = cachedSections?.postsCount ?: if (sameProfile) state.postsCount else cachedProfile?.postsCount,
                    followersCount = cachedSections?.followersCount ?: if (sameProfile) state.followersCount else cachedProfile?.followersCount,
                    followingCount = cachedSections?.followingCount ?: if (sameProfile) state.followingCount else cachedProfile?.followingCount,
                )
            }

            // Load the profile header and its sections at the same time. Cached sections
            // remain visible while the fresh values arrive in the background.
            var profileResult: Result<ProfileDto?>? = null
            val profileDeferred = async { repository.fetchProfileById(effectiveId) }
            val profileHeaderJob = launch {
                val result = profileDeferred.await()
                profileResult = result
                val loadedProfile = result.getOrNull() ?: cachedProfile
                if (loadedProfile != null) {
                    if (isOwnProfile && result.isSuccess) repository.sessionStore.saveCachedProfile(loadedProfile)
                    profileCache[effectiveId] = loadedProfile
                    _uiState.update { state ->
                        state.copy(
                            viewedProfile = loadedProfile,
                            myProfile = if (isOwnProfile) loadedProfile else state.myProfile,
                            postsCount = state.postsCount ?: loadedProfile.postsCount,
                            followersCount = state.followersCount ?: loadedProfile.followersCount,
                            followingCount = state.followingCount ?: loadedProfile.followingCount,
                            isLoading = false,
                        )
                    }
                }
            }

            val sections = coroutineScope {
                val posts = async { repository.fetchPostsByAuthor(effectiveId) }
                val projects = async { repository.fetchProjects(effectiveId) }
                val experiences = async { repository.fetchExperiences(effectiveId) }
                val achievements = async { repository.fetchAchievements(effectiveId) }
                val following = async {
                    if (myId.isNotBlank() && !isOwnProfile) repository.isFollowingUser(effectiveId)
                    else Result.success(false)
                }
                val postCount = async { repository.fetchPostCount(effectiveId) }
                val followCounts = async { repository.fetchFollowCounts(effectiveId) }
                ProfileSectionsResult(
                    posts = posts.await(),
                    projects = projects.await(),
                    experiences = experiences.await(),
                    achievements = achievements.await(),
                    isFollowing = following.await(),
                    postCount = postCount.await(),
                    followCounts = followCounts.await(),
                )
            }
            profileHeaderJob.join()
            val resolvedProfileResult = profileResult ?: Result.failure<ProfileDto?>(IllegalStateException("Profile could not be loaded."))
            val profile = resolvedProfileResult.getOrNull() ?: cachedProfile
            val postsResult = sections.posts
            val projectsResult = sections.projects
            val experiencesResult = sections.experiences
            val achievementsResult = sections.achievements
            val followingResult = sections.isFollowing
            val postCountResult = sections.postCount
            val followCountsResult = sections.followCounts

            val loadedPosts = postsResult.getOrDefault(cachedSections?.posts ?: emptyList())
            val loadedProjects = projectsResult.getOrDefault(cachedSections?.projects ?: emptyList())
            val loadedExperiences = experiencesResult.getOrDefault(cachedSections?.experiences ?: emptyList())
            val loadedAchievements = achievementsResult.getOrDefault(cachedSections?.achievements ?: emptyList())
            val loadedFollowing = followingResult.getOrDefault(cachedSections?.isFollowing ?: false)
            val loadedPostCount = postCountResult.getOrNull() ?: cachedSections?.postsCount ?: profile?.postsCount
            val loadedFollowersCount = followCountsResult.getOrNull()?.first ?: cachedSections?.followersCount ?: profile?.followersCount
            val loadedFollowingCount = followCountsResult.getOrNull()?.second ?: cachedSections?.followingCount ?: profile?.followingCount
            if (isOwnProfile) {
                profileSectionsCache[effectiveId] = CachedProfileSections(
                    posts = loadedPosts,
                    projects = loadedProjects,
                    experiences = loadedExperiences,
                    achievements = loadedAchievements,
                    isFollowing = loadedFollowing,
                    postsCount = loadedPostCount,
                    followersCount = loadedFollowersCount,
                    followingCount = loadedFollowingCount,
                )
            }

            val loadError = listOfNotNull(
                resolvedProfileResult.exceptionOrNull()?.message,
                postsResult.exceptionOrNull()?.message,
                projectsResult.exceptionOrNull()?.message,
                experiencesResult.exceptionOrNull()?.message,
                achievementsResult.exceptionOrNull()?.message,
                followingResult.exceptionOrNull()?.message,
                postCountResult.exceptionOrNull()?.message,
                followCountsResult.exceptionOrNull()?.message,
            ).firstOrNull()

            _uiState.update { state ->
                state.copy(
                    viewedProfile = profile ?: state.viewedProfile,
                    myProfile = if (isOwnProfile) profile ?: state.myProfile else state.myProfile,
                    posts = loadedPosts,
                    projects = loadedProjects,
                    experiences = loadedExperiences,
                    achievements = loadedAchievements,
                    isFollowing = loadedFollowing,
                    postsCount = loadedPostCount,
                    followersCount = loadedFollowersCount,
                    followingCount = loadedFollowingCount,
                    isLoading = false,
                    errorMessage = loadError,
                )
            }
        }
    }

    fun updateProfile(
        displayName: String,
        website: String,
        bio: String,
        regionLabel: String,
        interestsCsv: String,
        username: String,
        skillsCsv: String,
        avatarUrl: String? = null,
        coverUrl: String? = null,
        onComplete: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Sign in to update your profile.") }
                onComplete(false)
                return@launch
            }
            val cleanUsername = username.trim().removePrefix("@").lowercase()
            if (!Regex("^[a-z0-9_]{3,24}$").matches(cleanUsername)) {
                _uiState.update { it.copy(statusMessage = "Username must be 3–24 characters and use only letters, numbers, or underscores.") }
                onComplete(false)
                return@launch
            }
            val interests = interestsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val skills = skillsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val updates = mutableMapOf<String, Any?>(
                "username" to cleanUsername,
                "skills" to skills,
                "display_name" to displayName.trim(),
                "website" to website.trim(),
                "bio" to bio.trim(),
                "location" to regionLabel.trim(),
                "interests" to interests,
            )
            if (avatarUrl != null) updates["avatar_url"] = avatarUrl
            if (coverUrl != null) updates["cover_url"] = coverUrl

            repository.updateProfileFields(uid, updates).fold(
                onSuccess = { updated ->
                    profileCache[uid] = updated
                    _uiState.update {
                        it.copy(
                            myProfile = updated,
                            viewedProfile = updated,
                            statusMessage = "Profile updated.",
                        )
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.message) }
                    onComplete(false)
                },
            )
        }
    }

    fun addProject(title: String, description: String, category: String, skillsCsv: String, collaborators: String) {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) return@launch
            val skills = skillsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            repository.createProject(
                CreateProjectRequest(
                    ownerId = uid,
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    skillsUsed = skills,
                    collaboratorsNote = collaborators.trim(),
                ),
            ).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            projects = listOf(created) + state.projects,
                            statusMessage = "Project added to showcase.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun addExperience(title: String, organization: String, startDate: String, description: String) {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) return@launch
            repository.createExperience(
                CreateExperienceRequest(
                    userId = uid,
                    title = title.trim(),
                    organization = organization.trim(),
                    startDate = startDate.trim().ifBlank { "2024-01-01" },
                    isCurrent = true,
                    description = description.trim(),
                ),
            ).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            experiences = listOf(created) + state.experiences,
                            statusMessage = "Experience added.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun addAchievement(title: String, issuer: String, awardedAt: String?, description: String) {
        viewModelScope.launch {
            repository.createAchievement(title, issuer, awardedAt, description).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            achievements = listOf(created) + state.achievements,
                            statusMessage = "Achievement added.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId).fold(
                onSuccess = {
                    _uiState.update { it.copy(posts = it.posts.filterNot { post -> post.id == postId }, statusMessage = "Post deleted.") }
                },
                onFailure = { error -> _uiState.update { it.copy(statusMessage = error.message) } },
            )
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId).fold(
                onSuccess = {
                    _uiState.update { it.copy(projects = it.projects.filterNot { project -> project.id == projectId }, statusMessage = "Project deleted.") }
                },
                onFailure = { error -> _uiState.update { it.copy(statusMessage = error.message) } },
            )
        }
    }

    fun deleteExperience(experienceId: String) {
        viewModelScope.launch {
            repository.deleteExperience(experienceId).fold(
                onSuccess = {
                    _uiState.update { it.copy(experiences = it.experiences.filterNot { experience -> experience.id == experienceId }, statusMessage = "Experience deleted.") }
                },
                onFailure = { error -> _uiState.update { it.copy(statusMessage = error.message) } },
            )
        }
    }

    fun deleteAchievement(achievementId: String) {
        viewModelScope.launch {
            repository.deleteAchievement(achievementId).fold(
                onSuccess = {
                    _uiState.update { it.copy(achievements = it.achievements.filterNot { achievement -> achievement.id == achievementId }, statusMessage = "Achievement deleted.") }
                },
                onFailure = { error -> _uiState.update { it.copy(statusMessage = error.message) } },
            )
        }
    }

    fun sendMessageRequest(recipientId: String, message: String) {
        viewModelScope.launch {
            repository.createMessageRequest(recipientId, message).fold(
                onSuccess = { _uiState.update { it.copy(statusMessage = "Message request sent.") } },
                onFailure = { error -> _uiState.update { it.copy(statusMessage = error.message ?: "Could not send the message request.") } },
            )
        }
    }

    fun startDirectConversation(peerUserId: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            repository.startDirectConversation(peerUserId).fold(
                onSuccess = { conversationId -> onSuccess(conversationId) },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun createSavedCollection(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createSavedCollection(name).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            savedCollections = state.savedCollections + created,
                            statusMessage = "Private collection '${created.name}' created.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun toggleFollow(targetUserId: String) {
        val before = _uiState.value
        if (before.isFollowActionPending) return
        val wasFollowing = before.isFollowing
        val baseCount = before.followersCount ?: before.viewedProfile?.followersCount ?: 0
        val optimisticCount = (baseCount + if (wasFollowing) -1 else 1).coerceAtLeast(0)
        _uiState.update {
            it.copy(
                isFollowing = !wasFollowing,
                isFollowActionPending = true,
                followersCount = optimisticCount,
                statusMessage = null,
            )
        }
        viewModelScope.launch {
            repository.toggleFollow(targetUserId, wasFollowing).fold(
                onSuccess = { result ->
                    _uiState.update { state ->
                        val corrected = optimisticCount + when {
                            result.isFollowing == !wasFollowing -> 0
                            result.isFollowing -> 1
                            else -> -1
                        }
                        state.copy(
                            isFollowing = result.isFollowing,
                            isFollowActionPending = false,
                            followersCount = corrected.coerceAtLeast(0),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isFollowing = wasFollowing,
                            isFollowActionPending = false,
                            followersCount = baseCount,
                            statusMessage = error.message,
                        )
                    }
                },
            )
        }
    }

    fun loadNetworkList(userId: String, followers: Boolean) {
        _uiState.update {
            it.copy(
                networkProfiles = emptyList(),
                networkTitle = if (followers) "Networks" else "Connected",
                isLoadingNetwork = true,
            )
        }
        viewModelScope.launch {
            repository.fetchNetworkProfiles(userId, followers).fold(
                onSuccess = { profiles ->
                    _uiState.update {
                        it.copy(networkProfiles = profiles, isLoadingNetwork = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoadingNetwork = false, statusMessage = error.message ?: "Could not load this list.")
                    }
                },
            )
        }
    }

    fun blockUser(targetUserId: String) {
        viewModelScope.launch {
            repository.blockUser(targetUserId).fold(
                onSuccess = {
                    loadMyProfileAndSaved()
                    _uiState.update { it.copy(statusMessage = "Account blocked.") }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun unblockUser(targetUserId: String) {
        viewModelScope.launch {
            repository.unblockUser(targetUserId).fold(
                onSuccess = {
                    loadMyProfileAndSaved()
                    _uiState.update { it.copy(statusMessage = "Account unblocked.") }
                },
                onFailure = { error -> _uiState.update { it.copy(statusMessage = error.message ?: "Could not unblock this account.") } },
            )
        }
    }

    fun muteUser(targetUserId: String) {
        viewModelScope.launch {
            repository.muteUser(targetUserId).onSuccess {
                _uiState.update { it.copy(statusMessage = "Account muted.") }
            }
        }
    }

    fun reportProfile(targetUserId: String, reason: String, details: String) {
        viewModelScope.launch {
            repository.submitReport("profile", targetUserId, reason, details, targetUserId = targetUserId).fold(
                onSuccess = { _uiState.update { it.copy(statusMessage = "Profile reported for safety review.") } },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun updatePrivacySettings(isPrivate: Boolean, allowDmsFrom: String) {
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.sessionStore.setThemeMode(mode) }
    }

    fun setLanguageCode(code: String) {
        viewModelScope.launch { repository.sessionStore.setLanguageCode(code) }
    }

    fun updateNotificationPrefs(likes: Boolean, comments: Boolean, messages: Boolean, communities: Boolean) {
        viewModelScope.launch {
            repository.sessionStore.updateNotificationPrefs(likes, comments, messages, communities)
        }
    }

    fun loadModerationDashboard() {
        if (!AppConfig.isSupabaseConfigured) return
        _uiState.update {
            it.copy(
                isModerator = false,
                moderationReports = emptyList(),
                auditLogs = emptyList(),
                moderationAppeals = emptyList(),
                moderationMedia = emptyList(),
            )
        }
        viewModelScope.launch {
            val canModerate = repository.currentUserCanModerate()
            if (!canModerate) {
                _uiState.update {
                    it.copy(
                        isModerator = false,
                        moderationReports = emptyList(),
                        auditLogs = emptyList(),
                        moderationAppeals = emptyList(),
                        moderationMedia = emptyList(),
                    )
                }
                return@launch
            }
            val reports = repository.fetchModerationReports().getOrDefault(emptyList())
            val logs = repository.fetchAuditLogs().getOrDefault(emptyList())
            val appeals = repository.fetchModerationAppeals().getOrDefault(emptyList())
            val media = repository.fetchModerationMedia().getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isModerator = true,
                    moderationReports = reports,
                    auditLogs = logs,
                    moderationAppeals = appeals,
                    moderationMedia = media,
                )
            }
        }
    }

    fun resolveMedia(mediaAssetId: String, approved: Boolean) {
        viewModelScope.launch {
            repository.resolveModerationMedia(mediaAssetId, approved).fold(
                onSuccess = {
                    loadModerationDashboard()
                    _uiState.update {
                        it.copy(statusMessage = if (approved) "Media approved and published." else "Media rejected.")
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(statusMessage = error.message ?: "Could not review this media.") }
                },
            )
        }
    }

    fun submitModerationAppeal(reason: String) {
        viewModelScope.launch {
            repository.submitModerationAppeal(reason).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            moderationAppeals = listOf(created) + state.moderationAppeals,
                            statusMessage = "Appeal submitted for moderation review.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun resolveAppeal(appeal: ModerationAppealDto, approved: Boolean, note: String = "") {
        viewModelScope.launch {
            repository.resolveModerationAppeal(
                appealId = appeal.id,
                enforcementUserId = appeal.enforcementUserId,
                approved = approved,
                moderatorNote = note,
            ).fold(
                onSuccess = {
                    loadModerationDashboard()
                    _uiState.update {
                        it.copy(statusMessage = if (approved) "Appeal approved and account restored." else "Appeal declined.")
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(statusMessage = error.message ?: "Could not update this appeal.") }
                },
            )
        }
    }

    fun resolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            repository.updateReportStatus(reportId, status).fold(
                onSuccess = {
                    loadModerationDashboard()
                    _uiState.update { it.copy(statusMessage = "Report updated to $status.") }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            clearPrivateState()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            repository.requestAccountDeletion().fold(
                onSuccess = { _uiState.update { it.copy(statusMessage = "Account deletion requested.") } },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
