package com.example.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.network.CommentDto
import com.example.core.network.CreatePostRequest
import com.example.core.network.PostDto
import com.example.core.network.RealtimeEvent
import com.example.core.network.StoryDto
import com.example.data.repository.BackendNotConfiguredException
import com.example.data.repository.FeedTab
import com.example.data.repository.SkillectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val selectedTab: FeedTab = FeedTab.RECOMMENDED,
    val posts: List<PostDto> = emptyList(),
    val stories: List<StoryDto> = emptyList(),
    val shortVideos: List<PostDto> = emptyList(),
    val likedPostIds: Set<String> = emptySet(),
    val pendingLikePostIds: Set<String> = emptySet(),
    val savedPostIds: Set<String> = emptySet(),
    val followingIds: Set<String> = emptySet(),
    val pendingFollowUserIds: Set<String> = emptySet(),
    val votedPollOptions: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val isBackendConfigured: Boolean = AppConfig.isSupabaseConfigured,
    val errorMessage: String? = null,
    val activeCommentsPost: PostDto? = null,
    val comments: List<CommentDto> = emptyList(),
    val loadingComments: Boolean = false,
    val isSubmittingComment: Boolean = false,
    val snackbarMessage: String? = null,
)

class FeedViewModel(
    private val repository: SkillectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val pageSize = 20
    private var commentsLoadJob: Job? = null
    private var feedLoadJob: Job? = null
    private var feedRequestGeneration: Long = 0L
    private var shortVideosLoadJob: Job? = null
    private val feedCache = mutableMapOf<FeedTab, List<PostDto>>()
    private val commentsCache = mutableMapOf<String, List<CommentDto>>()

    init {
        refreshAll()
        // Fetch Shorts in the background while the user is browsing the feed.
        loadShortVideos()
        viewModelScope.launch {
            repository.realtimeClient.events.collect { event ->
                if (event is RealtimeEvent.FeedUpdated) {
                    refreshFeed(reset = true)
                }
            }
        }
    }

    fun selectTab(tab: FeedTab) {
        val cached = feedCache[tab].orEmpty()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                posts = cached,
                hasMore = true,
                isLoading = cached.isEmpty(),
                errorMessage = null,
            )
        }
        refreshFeed(reset = true)
    }

    fun refreshAll() {
        _uiState.update { it.copy(isBackendConfigured = AppConfig.isSupabaseConfigured) }
        refreshFeed(reset = true)
        loadStories()
        loadFollowing()
        loadViewerInteractions()
    }

    fun clearUserSpecificState() {
        commentsLoadJob?.cancel()
        commentsLoadJob = null
        _uiState.update {
            it.copy(
                selectedTab = if (it.selectedTab == FeedTab.FOLLOWING) FeedTab.RECOMMENDED else it.selectedTab,
                likedPostIds = emptySet(),
                pendingLikePostIds = emptySet(),
                savedPostIds = emptySet(),
                followingIds = emptySet(),
                pendingFollowUserIds = emptySet(),
                votedPollOptions = emptyMap(),
                activeCommentsPost = null,
                comments = emptyList(),
                isSubmittingComment = false,
                snackbarMessage = null,
            )
        }
        refreshAll()
    }

    private fun loadFollowing() {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isNotBlank()) {
                repository.getFollowingIds(uid).fold(
                    onSuccess = { ids -> _uiState.update { it.copy(followingIds = ids.toSet()) } },
                    onFailure = { error -> _uiState.update { it.copy(snackbarMessage = error.message) } },
                )
            }
        }
    }

    private fun loadViewerInteractions() {
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) return@launch
            val (likesResult, savedResult, pollVotesResult) = coroutineScope {
                val likes = async { repository.fetchMyLikedPostIds() }
                val saved = async { repository.fetchSavedPosts() }
                val votes = async { repository.fetchMyPollVotes() }
                Triple(likes.await(), saved.await(), votes.await())
            }
            val likes = likesResult.getOrNull()
            val saved = savedResult.getOrNull()?.map { it.postId }?.toSet()
            val pollVotes = pollVotesResult.getOrNull()
            val interactionError = listOfNotNull(
                likesResult.exceptionOrNull()?.message,
                savedResult.exceptionOrNull()?.message,
                pollVotesResult.exceptionOrNull()?.message,
            ).firstOrNull()
            _uiState.update { state ->
                state.copy(
                    likedPostIds = likes ?: state.likedPostIds,
                    savedPostIds = saved ?: state.savedPostIds,
                    votedPollOptions = pollVotes ?: state.votedPollOptions,
                    snackbarMessage = interactionError ?: state.snackbarMessage,
                )
            }
        }
    }

    fun refreshFeed(reset: Boolean = false) {
        val current = _uiState.value
        if (!AppConfig.isSupabaseConfigured) {
            _uiState.update { it.copy(isLoading = false, isLoadingMore = false, isBackendConfigured = false) }
            return
        }
        if (reset) {
            feedRequestGeneration += 1
            feedLoadJob?.cancel()
        } else if (feedLoadJob?.isActive == true || current.isLoading || current.isLoadingMore || !current.hasMore) {
            return
        }
        val generation = feedRequestGeneration
        val tab = current.selectedTab
        val offset = if (reset) 0 else current.posts.size
        feedLoadJob = viewModelScope.launch {
            _uiState.update {
                if (reset) it.copy(isLoading = it.posts.isEmpty(), errorMessage = null)
                else it.copy(isLoadingMore = true, errorMessage = null)
            }
            try {
                repository.fetchFeed(tab, limit = pageSize, offset = offset).fold(
                    onSuccess = { loaded ->
                        if (generation != feedRequestGeneration || _uiState.value.selectedTab != tab) return@fold
                        _uiState.update { state ->
                            val merged = if (reset) loaded else (state.posts + loaded).distinctBy { it.id }
                            feedCache[tab] = merged
                            state.copy(
                                posts = merged,
                                hasMore = loaded.size >= pageSize,
                            )
                        }
                    },
                    onFailure = { err ->
                        if (generation != feedRequestGeneration) return@fold
                        val message = if (err is BackendNotConfiguredException) null else err.message
                        _uiState.update { it.copy(errorMessage = message) }
                    },
                )
            } finally {
                if (generation == feedRequestGeneration) {
                    _uiState.update { it.copy(isLoading = false, isLoadingMore = false) }
                }
            }
        }
    }

    fun loadStories() {
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            repository.fetchActiveStories().onSuccess { list ->
                _uiState.update { it.copy(stories = list) }
            }
        }
    }

    fun loadShortVideos() {
        if (!AppConfig.isSupabaseConfigured || _uiState.value.shortVideos.isNotEmpty()) return
        if (shortVideosLoadJob?.isActive == true) return
        shortVideosLoadJob = viewModelScope.launch {
            repository.fetchShortVideos().onSuccess { vids ->
                _uiState.update { it.copy(shortVideos = vids) }
            }
        }
    }

    fun createStory(mediaUrl: String, mediaType: String, caption: String) {
        viewModelScope.launch {
            repository.createStory(mediaUrl, mediaType, caption).fold(
                onSuccess = { created ->
                    _uiState.update { state ->
                        state.copy(
                            stories = listOf(created) + state.stories.filterNot { it.id == created.id },
                            snackbarMessage = "Memory shared. It disappears after 12 hours.",
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(snackbarMessage = err.message ?: "Could not publish Memory.") }
                },
            )
        }
    }

    private val deletingStoryIds = mutableSetOf<String>()

    fun deleteStory(storyId: String) {
        if (!deletingStoryIds.add(storyId)) return
        val previousStory = _uiState.value.stories.firstOrNull { it.id == storyId }
        // Remove it from the shelf immediately; perform network and storage cleanup in the background.
        _uiState.update { state ->
            state.copy(
                stories = state.stories.filterNot { it.id == storyId },
                snackbarMessage = "Deleting Memory…",
            )
        }
        viewModelScope.launch {
            repository.deleteStory(storyId).fold(
                onSuccess = {
                    _uiState.update { it.copy(snackbarMessage = "Memory deleted.") }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        val restored = if (previousStory != null && state.stories.none { it.id == storyId }) {
                            listOf(previousStory) + state.stories
                        } else state.stories
                        state.copy(
                            stories = restored,
                            snackbarMessage = error.message ?: "Could not delete Memory.",
                        )
                    }
                },
            )
            deletingStoryIds.remove(storyId)
        }
    }

    fun toggleLike(post: PostDto) {
        val snapshot = _uiState.value
        if (post.id in snapshot.pendingLikePostIds) return
        val wasLiked = post.id in snapshot.likedPostIds
        val shouldLike = !wasLiked
        val delta = if (shouldLike) 1 else -1
        _uiState.update { state ->
            val liked = state.likedPostIds.toMutableSet().apply {
                if (shouldLike) add(post.id) else remove(post.id)
            }
            state.copy(
                likedPostIds = liked,
                pendingLikePostIds = state.pendingLikePostIds + post.id,
                posts = state.posts.map { if (it.id == post.id) it.copy(likesCount = maxOf(0, it.likesCount + delta)) else it },
                shortVideos = state.shortVideos.map { if (it.id == post.id) it.copy(likesCount = maxOf(0, it.likesCount + delta)) else it },
            )
        }
        viewModelScope.launch {
            repository.setPostLike(post.id, shouldLike).fold(
                onSuccess = {
                    _uiState.update { it.copy(pendingLikePostIds = it.pendingLikePostIds - post.id) }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        val liked = state.likedPostIds.toMutableSet().apply {
                            if (wasLiked) add(post.id) else remove(post.id)
                        }
                        state.copy(
                            likedPostIds = liked,
                            pendingLikePostIds = state.pendingLikePostIds - post.id,
                            posts = state.posts.map { if (it.id == post.id) it.copy(likesCount = maxOf(0, it.likesCount - delta)) else it },
                            shortVideos = state.shortVideos.map { if (it.id == post.id) it.copy(likesCount = maxOf(0, it.likesCount - delta)) else it },
                            snackbarMessage = err.message,
                        )
                    }
                },
            )
        }
    }

    fun toggleSave(post: PostDto) {
        val wasSaved = post.id in _uiState.value.savedPostIds
        val shouldSave = !wasSaved
        _uiState.update { state ->
            val saved = state.savedPostIds.toMutableSet()
            if (shouldSave) saved.add(post.id) else saved.remove(post.id)
            state.copy(savedPostIds = saved)
        }
        viewModelScope.launch {
            repository.toggleSavePost(post.id, wasSaved).fold(
                onSuccess = { nowSaved ->
                    _uiState.update { state ->
                        val saved = state.savedPostIds.toMutableSet()
                        if (nowSaved) saved.add(post.id) else saved.remove(post.id)
                        state.copy(savedPostIds = saved)
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        val saved = state.savedPostIds.toMutableSet()
                        if (wasSaved) saved.add(post.id) else saved.remove(post.id)
                        state.copy(savedPostIds = saved, snackbarMessage = err.message)
                    }
                },
            )
        }
    }

    fun toggleFollow(authorId: String) {
        val before = _uiState.value
        if (authorId in before.pendingFollowUserIds) return
        val wasFollowing = authorId in before.followingIds
        _uiState.update { state ->
            val optimistic = state.followingIds.toMutableSet()
            if (wasFollowing) optimistic.remove(authorId) else optimistic.add(authorId)
            state.copy(
                followingIds = optimistic,
                pendingFollowUserIds = state.pendingFollowUserIds + authorId,
            )
        }
        viewModelScope.launch {
            repository.toggleFollow(authorId, wasFollowing).fold(
                onSuccess = { result ->
                    _uiState.update { state ->
                        val updated = state.followingIds.toMutableSet()
                        if (result.isFollowing) updated.add(authorId) else updated.remove(authorId)
                        state.copy(
                            followingIds = updated,
                            pendingFollowUserIds = state.pendingFollowUserIds - authorId,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        val rollback = state.followingIds.toMutableSet()
                        if (wasFollowing) rollback.add(authorId) else rollback.remove(authorId)
                        state.copy(
                            followingIds = rollback,
                            pendingFollowUserIds = state.pendingFollowUserIds - authorId,
                            snackbarMessage = err.message,
                        )
                    }
                },
            )
        }
    }

    fun repost(post: PostDto, quoteText: String? = null) {
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            if (uid.isBlank()) {
                _uiState.update { it.copy(snackbarMessage = "Sign in to repost.") }
                return@launch
            }
            val isQuote = !quoteText.isNullOrBlank()
            val authorName = post.author?.displayName?.takeIf { it.isNotBlank() } ?: "a creator"
            val resolvedContent = quoteText?.trim()
                ?: post.content.ifBlank { "Shared a post from $authorName" }
            val resolvedImage = if (!isQuote) post.imageUrl ?: post.mediaUrls.firstOrNull() else null
            val req = CreatePostRequest(
                authorId = uid,
                postType = if (isQuote) "quote" else "repost",
                content = resolvedContent,
                mediaUrls = listOfNotNull(resolvedImage),
                repostedPostId = if (!isQuote) post.id else null,
                quotedPostId = if (isQuote) post.id else null,
                imageUrl = resolvedImage,
            )
            repository.createPost(req).fold(
                onSuccess = {
                    _uiState.update { it.copy(snackbarMessage = if (isQuote) "Quote reposted." else "Reposted to feed.") }
                    refreshFeed(reset = true)
                },
                onFailure = { err -> _uiState.update { it.copy(snackbarMessage = err.message) } },
            )
        }
    }

    fun votePoll(postId: String, optionId: String) {
        viewModelScope.launch {
            repository.votePoll(postId, optionId).fold(
                onSuccess = {
                    _uiState.update { state ->
                        val updatedVotes = state.votedPollOptions + (postId to optionId)
                        state.copy(
                            votedPollOptions = updatedVotes,
                            posts = state.posts.map { post ->
                                if (post.id == postId) {
                                    post.copy(
                                        pollOptions = post.pollOptions.map { opt ->
                                            if (opt.id == optionId) opt.copy(votesCount = opt.votesCount + 1) else opt
                                        },
                                    )
                                } else {
                                    post
                                }
                            },
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(snackbarMessage = err.message) } },
            )
        }
    }

    fun openComments(post: PostDto) {
        commentsLoadJob?.cancel()
        val cached = commentsCache[post.id].orEmpty()
        _uiState.update {
            it.copy(
                activeCommentsPost = post,
                comments = cached,
                loadingComments = cached.isEmpty(),
            )
        }
        commentsLoadJob = viewModelScope.launch {
            repository.fetchComments(post.id).fold(
                onSuccess = { list ->
                    commentsCache[post.id] = list
                    _uiState.update { state ->
                        if (state.activeCommentsPost?.id == post.id) state.copy(comments = list, loadingComments = false) else state
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        if (state.activeCommentsPost?.id == post.id) state.copy(loadingComments = false, snackbarMessage = err.message) else state
                    }
                },
            )
        }
    }

    fun closeComments() {
        commentsLoadJob?.cancel()
        commentsLoadJob = null
        _uiState.update { it.copy(activeCommentsPost = null, comments = emptyList(), loadingComments = false) }
    }

    fun submitComment(
        content: String,
        parentCommentId: String? = null,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val post = _uiState.value.activeCommentsPost ?: return
        if (content.isBlank() || _uiState.value.isSubmittingComment) return
        val cleanContent = content.trim()
        _uiState.update { it.copy(isSubmittingComment = true, snackbarMessage = null) }
        viewModelScope.launch {
            val userId = repository.sessionStore.currentSession().userId
            if (userId.isBlank()) {
                _uiState.update { it.copy(isSubmittingComment = false, snackbarMessage = "Sign in again to comment.") }
                onComplete(false)
                return@launch
            }
            val pendingId = "pending-${System.nanoTime()}"
            val pendingComment = CommentDto(
                id = pendingId,
                postId = post.id,
                authorId = userId,
                parentCommentId = parentCommentId,
                content = cleanContent,
                moderationStatus = "pending",
            )
            _uiState.update { state ->
                state.copy(
                    comments = if (state.activeCommentsPost?.id == post.id) state.comments + pendingComment else state.comments,
                    posts = state.posts.map { if (it.id == post.id) it.copy(commentsCount = it.commentsCount + 1) else it },
                    shortVideos = state.shortVideos.map { if (it.id == post.id) it.copy(commentsCount = it.commentsCount + 1) else it },
                )
            }
            repository.addComment(post.id, cleanContent, parentCommentId).fold(
                onSuccess = { added ->
                    commentsCache[post.id] = (commentsCache[post.id].orEmpty() + added).distinctBy { it.id }
                    _uiState.update { state ->
                        state.copy(
                            comments = if (state.activeCommentsPost?.id == post.id) {
                                state.comments.map { if (it.id == pendingId) added else it }
                            } else state.comments,
                            isSubmittingComment = false,
                        )
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            comments = state.comments.filterNot { it.id == pendingId },
                            posts = state.posts.map { if (it.id == post.id) it.copy(commentsCount = maxOf(0, it.commentsCount - 1)) else it },
                            shortVideos = state.shortVideos.map { if (it.id == post.id) it.copy(commentsCount = maxOf(0, it.commentsCount - 1)) else it },
                            isSubmittingComment = false,
                            snackbarMessage = err.message,
                        )
                    }
                    onComplete(false)
                },
            )
        }
    }

    fun editPost(postId: String, newContent: String) {
        viewModelScope.launch {
            repository.editPost(postId, newContent).fold(
                onSuccess = { updated ->
                    _uiState.update { state ->
                        state.copy(
                            posts = state.posts.map { if (it.id == postId) updated.copy(author = it.author) else it },
                            snackbarMessage = "Post updated.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(snackbarMessage = err.message) } },
            )
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            posts = state.posts.filterNot { it.id == postId },
                            snackbarMessage = "Post deleted.",
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(snackbarMessage = err.message) } },
            )
        }
    }

    fun reportPost(postId: String, reason: String, details: String, targetUserId: String? = null) {
        viewModelScope.launch {
            repository.submitReport("post", postId, reason, details, targetUserId = targetUserId).fold(
                onSuccess = {
                    _uiState.update { it.copy(snackbarMessage = "Report submitted for safety review.") }
                },
                onFailure = { err -> _uiState.update { it.copy(snackbarMessage = err.message) } },
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
