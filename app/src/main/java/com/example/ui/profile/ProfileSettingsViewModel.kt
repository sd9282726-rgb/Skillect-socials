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
    val moderationReports: List<ReportDto> = emptyList(),
    val auditLogs: List<ModerationAuditLogDto> = emptyList(),
    val moderationAppeals: List<ModerationAppealDto> = emptyList(),
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
    val followingIds: Result<List<String>>,
    val postCount: Result<Int>,
    val followCounts: Result<Pair<Int, Int>>,
)

class ProfileSettingsViewModel(
    private val repository: SkillectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSettingsUiState())
    private var profileLoadJob: Job? = null
    private val profileCache = mutableMapOf<String, ProfileDto>()
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
            val my = repository.fetchMyProfile().getOrNull()
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
        _uiState.update {
            it.copy(
                myProfile = null,
                savedCollections = emptyList(),
                savedPosts = emptyList(),
                blockedAccounts = emptyList(),
                moderationReports = emptyList(),
                auditLogs = emptyList(),
                moderationAppeals = emptyList(),
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
            val cachedProfile = profileCache[effectiveId]
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

            // Fetch the identity first so the profile header can render immediately.
            val profileResult = repository.fetchProfileById(effectiveId)
            val profile = profileResult.getOrNull() ?: cachedProfile
            if (profile != null) {
                profileCache[effectiveId] = profile
                _uiState.update { state ->
                    state.copy(
                        viewedProfile = profile,
                        myProfile = if (isOwnProfile) profile else state.myProfile,
                        postsCount = state.postsCount ?: profile.postsCount,
                        followersCount = state.followersCount ?: profile.followersCount,
                        followingCount = state.followingCount ?: profile.followingCount,
                        isLoading = false,
                    )
                }
            }

            // Secondary profile sections load concurrently and never block the header.
            val sections = coroutineScope {
                val posts = async { repository.fetchPostsByAuthor(effectiveId) }
                val projects = async { repository.fetchProjects(effectiveId) }
                val experiences = async { repository.fetchExperiences(effectiveId) }
                val achievements = async { repository.fetchAchievements(effectiveId) }
                val following = async {
                    if (myId.isNotBlank()) repository.getFollowingIds(myId)
                    else Result.success(emptyList<String>())
                }
                val postCount = async { repository.fetchPostCount(effectiveId) }
                val followCounts = async { repository.fetchFollowCounts(effectiveId) }
                ProfileSectionsResult(
                    posts = posts.await(),
                    projects = projects.await(),
                    experiences = experiences.await(),
                    achievements = achievements.await(),
                    followingIds = following.await(),
                    postCount = postCount.await(),
                    followCounts = followCounts.await(),
                )
            }
            val postsResult = sections.posts
            val projectsResult = sections.projects
            val experiencesResult = sections.experiences
            val achievementsResult = sections.achievements
            val followingResult = sections.followingIds
            val postCountResult = sections.postCount
            val followCountsResult = sections.followCounts

            val loadError = listOfNotNull(
                profileResult.exceptionOrNull()?.message,
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
                    posts = postsResult.getOrDefault(state.posts),
                    projects = projectsResult.getOrDefault(state.projects),
                    experiences = experiencesResult.getOrDefault(state.experiences),
                    achievements = achievementsResult.getOrDefault(state.achievements),
                    isFollowing = effectiveId in followingResult.getOrDefault(emptyList()),
                    postsCount = postCountResult.getOrNull() ?: state.postsCount,
                    followersCount = followCountsResult.getOrNull()?.first ?: state.followersCount,
                    followingCount = followCountsResult.getOrNull()?.second ?: state.followingCount,
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
            val interests = interestsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val updates = mutableMapOf<String, Any?>(
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
        if (_uiState.value.isFollowActionPending) return
        _uiState.update { it.copy(isFollowActionPending = true, statusMessage = null) }
        viewModelScope.launch {
            repository.toggleFollow(targetUserId).fold(
                onSuccess = { result ->
                    _uiState.update { state ->
                        val base = state.followersCount ?: state.viewedProfile?.followersCount ?: 0
                        val delta = if (result.isFollowing == result.wasFollowing) 0 else if (result.isFollowing) 1 else -1
                        state.copy(
                            isFollowing = result.isFollowing,
                            isFollowActionPending = false,
                            followersCount = (base + delta).coerceAtLeast(0),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isFollowActionPending = false, statusMessage = error.message) }
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
            repository.unblockUser(targetUserId).onSuccess {
                loadMyProfileAndSaved()
                _uiState.update { it.copy(statusMessage = "Account unblocked.") }
            }
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
            repository.submitReport("profile", targetUserId, reason, details).fold(
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
        viewModelScope.launch {
            val reports = repository.fetchModerationReports().getOrDefault(emptyList())
            val logs = repository.fetchAuditLogs().getOrDefault(emptyList())
            val appeals = repository.fetchModerationAppeals().getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    moderationReports = reports,
                    auditLogs = logs,
                    moderationAppeals = appeals,
                )
            }
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
