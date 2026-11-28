package com.example.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.network.CommunityDto
import com.example.core.network.CommunityMemberDto
import com.example.core.network.CreateProjectRequest
import com.example.core.network.PostDto
import com.example.core.network.ProfileDto
import com.example.core.network.ProjectDto
import com.example.core.network.SkillDto
import com.example.data.repository.SkillectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExploreFilter { ALL, PEOPLE, POSTS, HASHTAGS, COMMUNITIES, PROJECTS, SKILLS }
enum class ExploreSort { TRENDING, LATEST }

data class ExploreUiState(
    val query: String = "",
    val filter: ExploreFilter = ExploreFilter.ALL,
    val sort: ExploreSort = ExploreSort.TRENDING,
    val selectedCategory: String = "All",
    val people: List<ProfileDto> = emptyList(),
    val posts: List<PostDto> = emptyList(),
    val communities: List<CommunityDto> = emptyList(),
    val projects: List<ProjectDto> = emptyList(),
    val skills: List<SkillDto> = emptyList(),
    val joinedCommunityIds: Set<String> = emptySet(),
    val communityActionsInProgress: Set<String> = emptySet(),
    val isCreatingCommunity: Boolean = false,
    val selectedCommunity: CommunityDto? = null,
    val selectedCommunityId: String? = null,
    val communityMembers: List<CommunityMemberDto> = emptyList(),
    val communityPosts: List<PostDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
)

class ExploreCommunitiesViewModel(
    private val repository: SkillectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var exploreJob: Job? = null
    private var communityDetailJob: Job? = null

    init {
        refreshExplore()
    }

    fun updateQuery(q: String) {
        _uiState.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Debounce typing so Explore searches once the user pauses, not on every keystroke.
            delay(180)
            refreshExplore()
        }
    }

    fun setFilter(filter: ExploreFilter) {
        searchJob?.cancel()
        _uiState.update { it.copy(filter = filter) }
        refreshExplore()
    }

    fun setSort(sort: ExploreSort) {
        searchJob?.cancel()
        _uiState.update { it.copy(sort = sort) }
        refreshExplore()
    }

    fun searchNow() {
        searchJob?.cancel()
        refreshExplore()
    }

    fun selectCategory(category: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(selectedCategory = category) }
        // Use the same cancellable refresh pipeline as Explore search so an older
        // category request cannot overwrite a newer selection.
        refreshExplore()
    }

    fun refreshExplore() {
        if (!AppConfig.isSupabaseConfigured) return
        val state = _uiState.value
        val sortParam = if (state.sort == ExploreSort.TRENDING) "likes_count.desc,created_at.desc" else "created_at.desc"

        exploreJob?.cancel()
        exploreJob = viewModelScope.launch {
            val currentUserId = repository.sessionStore.currentSession().userId
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val peopleTask = async { repository.searchProfiles(state.query) }
            val postsTask = async { repository.searchPosts(state.query, sortParam) }
            val communitiesTask = async { repository.fetchCommunities(state.selectedCategory, state.query) }
            val projectsTask = async { repository.fetchProjects() }
            val skillsTask = async { repository.fetchSkills() }
            val joinedTask = async { repository.fetchMyJoinedCommunityIds() }
            val peopleRes = peopleTask.await()
            val postsRes = postsTask.await()
            val commRes = communitiesTask.await()
            val projRes = projectsTask.await()
            val skillRes = skillsTask.await()
            val joinedIds = joinedTask.await().getOrNull()

            _uiState.update {
                it.copy(
                    people = peopleRes.getOrDefault(emptyList()).filterNot { it.id == currentUserId },
                    posts = postsRes.getOrDefault(emptyList()),
                    communities = commRes.getOrDefault(emptyList()),
                    projects = projRes.getOrDefault(emptyList()),
                    skills = skillRes.getOrDefault(emptyList()),
                    joinedCommunityIds = joinedIds ?: it.joinedCommunityIds,
                    isLoading = false,
                    errorMessage = peopleRes.exceptionOrNull()?.message
                        ?: postsRes.exceptionOrNull()?.message
                        ?: commRes.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun clearPrivateState() {
        searchJob?.cancel()
        exploreJob?.cancel()
        communityDetailJob?.cancel()
        searchJob = null
        exploreJob = null
        communityDetailJob = null
        _uiState.update {
            it.copy(
                joinedCommunityIds = emptySet(),
                communityActionsInProgress = emptySet(),
                isCreatingCommunity = false,
                isLoading = false,
                selectedCommunity = null,
                selectedCommunityId = null,
                communityMembers = emptyList(),
                communityPosts = emptyList(),
                statusMessage = null,
            )
        }
    }

    fun refreshCommunitiesOnly() {
        if (!AppConfig.isSupabaseConfigured) return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.fetchCommunities(state.selectedCategory, state.query).fold(
                onSuccess = { list -> _uiState.update { it.copy(communities = list, isLoading = false) } },
                onFailure = { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } },
            )
        }
    }

    fun createCommunity(
        name: String,
        description: String,
        category: String,
        isPrivate: Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (_uiState.value.isCreatingCommunity) return
        _uiState.update { it.copy(isCreatingCommunity = true, statusMessage = null) }
        viewModelScope.launch {
            repository.createCommunity(name, description, category, isPrivate).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            communities = listOf(created) + state.communities,
                            joinedCommunityIds = state.joinedCommunityIds + created.id,
                            isCreatingCommunity = false,
                            statusMessage = "Community '${created.name}' created.",
                        )
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isCreatingCommunity = false, statusMessage = err.message)
                    }
                    onComplete(false)
                    refreshExplore()
                },
            )
        }
    }

    fun toggleJoinCommunity(communityId: String) {
        val initial = _uiState.value
        if (communityId in initial.communityActionsInProgress) return
        val joined = communityId in initial.joinedCommunityIds
        _uiState.update {
            it.copy(communityActionsInProgress = it.communityActionsInProgress + communityId)
        }
        viewModelScope.launch {
            val res = if (joined) repository.leaveCommunity(communityId) else repository.joinCommunity(communityId)
            res.fold(
                onSuccess = {
                    _uiState.update { state ->
                        val updated = state.joinedCommunityIds.toMutableSet()
                        if (joined) updated.remove(communityId) else updated.add(communityId)
                        val memberDelta = if (joined) -1 else 1
                        val updatedCommunities = state.communities.map { community ->
                            if (community.id == communityId) {
                                community.copy(membersCount = (community.membersCount + memberDelta).coerceAtLeast(0))
                            } else community
                        }
                        val updatedSelected = state.selectedCommunity?.let { community ->
                            if (community.id == communityId) {
                                community.copy(membersCount = (community.membersCount + memberDelta).coerceAtLeast(0))
                            } else community
                        }
                        state.copy(
                            joinedCommunityIds = updated,
                            communities = updatedCommunities,
                            selectedCommunity = updatedSelected,
                            communityActionsInProgress = state.communityActionsInProgress - communityId,
                            statusMessage = if (joined) "Left community." else "Joined community.",
                        )
                    }
                    // Refresh the member list and authoritative community details after the action.
                    openCommunityDetail(communityId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            communityActionsInProgress = it.communityActionsInProgress - communityId,
                            statusMessage = err.message,
                        )
                    }
                },
            )
        }
    }

    fun openCommunityDetail(communityId: String) {
        communityDetailJob?.cancel()
        val found = _uiState.value.communities.firstOrNull { it.id == communityId }
            ?: _uiState.value.selectedCommunity?.takeIf { it.id == communityId }
        _uiState.update {
            it.copy(
                selectedCommunity = found,
                selectedCommunityId = communityId,
                communityMembers = emptyList(),
                communityPosts = emptyList(),
                isLoading = AppConfig.isSupabaseConfigured,
                errorMessage = null,
            )
        }
        if (!AppConfig.isSupabaseConfigured) return
        communityDetailJob = viewModelScope.launch {
            val communitiesResult = repository.fetchCommunities()
            val membersResult = repository.fetchCommunityMembers(communityId)
            val postsResult = repository.fetchCommunityPosts(communityId)
            val latestCommunity = communitiesResult.getOrNull()?.firstOrNull { it.id == communityId } ?: found
            // Ignore late responses if the user has already opened a different community.
            _uiState.update { state ->
                if (state.selectedCommunityId != communityId) state
                else state.copy(
                    selectedCommunity = latestCommunity,
                    communities = communitiesResult.getOrNull()?.let { latest ->
                        latest + state.communities.filterNot { old -> latest.any { it.id == old.id } }
                    } ?: state.communities,
                    communityMembers = membersResult.getOrDefault(emptyList()),
                    communityPosts = postsResult.getOrDefault(emptyList()),
                    isLoading = false,
                    errorMessage = membersResult.exceptionOrNull()?.message
                        ?: postsResult.exceptionOrNull()?.message
                        ?: state.errorMessage,
                )
            }
        }
    }

    fun createProject(title: String, description: String, category: String, skillsCsv: String) {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Sign in to showcase a project.") }
                return@launch
            }
            val skills = skillsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            repository.createProject(
                CreateProjectRequest(
                    ownerId = uid,
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    skillsUsed = skills,
                ),
            ).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            projects = listOf(created) + state.projects,
                            statusMessage = "Project '${created.title}' published.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
