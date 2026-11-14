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
    val selectedCommunity: CommunityDto? = null,
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

    init {
        refreshExplore()
    }

    fun updateQuery(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    fun setFilter(filter: ExploreFilter) {
        _uiState.update { it.copy(filter = filter) }
        refreshExplore()
    }

    fun setSort(sort: ExploreSort) {
        _uiState.update { it.copy(sort = sort) }
        refreshExplore()
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        refreshCommunitiesOnly()
    }

    fun refreshExplore() {
        if (!AppConfig.isSupabaseConfigured) return
        val state = _uiState.value
        val sortParam = if (state.sort == ExploreSort.TRENDING) "likes_count.desc,created_at.desc" else "created_at.desc"

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val peopleRes = repository.searchProfiles(state.query)
            val postsRes = repository.searchPosts(state.query, sortParam)
            val commRes = repository.fetchCommunities(state.selectedCategory, state.query)
            val projRes = repository.fetchProjects()
            val skillRes = repository.fetchSkills()
            val joinedIds = repository.fetchMyJoinedCommunityIds().getOrNull()

            _uiState.update {
                it.copy(
                    people = peopleRes.getOrDefault(emptyList()),
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
        _uiState.update {
            it.copy(
                joinedCommunityIds = emptySet(),
                communityActionsInProgress = emptySet(),
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

    fun createCommunity(name: String, description: String, category: String, isPrivate: Boolean) {
        viewModelScope.launch {
            repository.createCommunity(name, description, category, isPrivate).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            communities = listOf(created) + state.communities,
                            joinedCommunityIds = state.joinedCommunityIds + created.id,
                            statusMessage = "Community '${created.name}' created.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
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
        val found = _uiState.value.communities.firstOrNull { it.id == communityId }
        _uiState.update { it.copy(selectedCommunity = found, communityMembers = emptyList(), communityPosts = emptyList()) }
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            val members = repository.fetchCommunityMembers(communityId).getOrDefault(emptyList())
            val posts = repository.fetchCommunityPosts(communityId).getOrDefault(emptyList())
            _uiState.update { it.copy(communityMembers = members, communityPosts = posts) }
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
