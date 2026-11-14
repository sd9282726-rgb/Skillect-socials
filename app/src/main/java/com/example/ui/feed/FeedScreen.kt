package com.example.ui.feed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.core.config.AppConfig
import com.example.core.designsystem.BackendConfigBanner
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.ErrorRetryCard
import com.example.core.designsystem.SkeletonFeedCard
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectHorizontalBrandGradient
import com.example.core.designsystem.SkillectCard
import com.example.core.designsystem.SkillectCrimson
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.core.media.MediaUploadManager
import com.example.core.media.UploadProgressState
import com.example.core.media.VideoPlayerBox
import com.example.core.network.PostDto
import com.example.core.network.StoryDto
import com.example.data.repository.FeedTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    uiState: FeedUiState,
    currentUserId: String,
    unreadNotificationCount: Int,
    mediaUploadManager: MediaUploadManager,
    viewModel: FeedViewModel,
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onOpenShorts: () -> Unit,
    onOpenCommunities: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenComposer: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val isGuest = currentUserId.isBlank()
    val scope = rememberCoroutineScope()
    var activeStory by remember { mutableStateOf<StoryDto?>(null) }
    var quoteTarget by remember { mutableStateOf<PostDto?>(null) }
    var editTarget by remember { mutableStateOf<PostDto?>(null) }
    var reportTarget by remember { mutableStateOf<PostDto?>(null) }

    val storyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && currentUserId.isNotBlank()) {
            scope.launch {
                mediaUploadManager.uploadMedia(uri, "stories-media", currentUserId).collect { state ->
                    if (state is UploadProgressState.Success) {
                        viewModel.createStory(state.publicUrl, state.mediaType, "")
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()) {
            // Sleek Editorial Header matching brand mark + theme toggle + notifications + settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkillectBrandBadge(size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "skillect",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.8).sp,
                                brush = SkillectHorizontalBrandGradient,
                            ),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            contentDescription = "Toggle theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("nav_notifications_button"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge(
                                        containerColor = SkillectCrimson,
                                        contentColor = Color.White,
                                    ) {
                                        Text("$unreadNotificationCount")
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("nav_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings and Privacy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Photo-led editorial header: image + typography, without a generic slogan.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(154.dp)
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_skillect_hero),
                    contentDescription = "Skillect community banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.12f)),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                ) {
                    Text(
                        "A SPACE FOR WHAT YOU CREATE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                        ),
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        "Show what you're learning.",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                    )
                    Text(
                        "Share ideas, projects and progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            // Clean Underline Tabs: Following / Recommended / Discover
            val tabIdx = when (uiState.selectedTab) {
                FeedTab.FOLLOWING -> 0
                FeedTab.RECOMMENDED -> 1
                FeedTab.DISCOVER -> 2
            }
            SecondaryTabRow(
                selectedTabIndex = tabIdx,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabIdx, matchContentSize = false),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                },
            ) {
                listOf(
                    FeedTab.FOLLOWING to "Following",
                    FeedTab.RECOMMENDED to "Recommended",
                    FeedTab.DISCOVER to "Discover",
                ).forEach { (tab, label) ->
                    val selected = uiState.selectedTab == tab
                    Tab(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            // Quick-Access Destinations Strip (Shorts & Communities)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenShorts)
                        .testTag("nav_shorts_button"),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = "Short Videos",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shorts",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenCommunities)
                        .testTag("nav_communities_button"),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Communities",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Communities",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (!uiState.isBackendConfigured) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            BackendConfigBanner()
                        }
                    }
                }

                if (isGuest) {
                    item {
                        SkillectCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("guest_mode_banner"),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Browsing as Guest",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Explore public posts, creators, and communities.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = { onRequireAuth(false, "Sign in to your Skillect account.") },
                                    modifier = Modifier.testTag("guest_banner_sign_in"),
                                ) {
                                    Text("Sign In")
                                }
                                SkillectPrimaryButton(
                                    text = "Create Account",
                                    onClick = { onRequireAuth(true, "Create a Skillect account to post, like, comment, and connect.") },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("guest_banner_create_account"),
                                )
                            }
                        }
                    }
                }

                // Stories Strip (24h expiration)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(68.dp)
                                        .testTag("add_story_button")
                                        .clickable {
                                            if (isGuest) {
                                                onRequireAuth(false, "Sign in to share 24h stories.")
                                            } else {
                                                storyPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                                                )
                                            }
                                        },
                                ) {
                                    SkillectAvatar(
                                        avatarUrl = null,
                                        contentDescription = "Add 24h story",
                                        size = 58.dp,
                                        showAddBadge = true,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Your story",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            items(uiState.stories, key = { it.id }) { story ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(68.dp)
                                        .clickable { activeStory = story },
                                ) {
                                    SkillectAvatar(
                                        avatarUrl = story.author?.avatarUrl,
                                        contentDescription = "View ${story.author?.displayName ?: "user"} story",
                                        size = 56.dp,
                                        hasActiveStory = true,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = story.author?.username?.takeIf { it.isNotBlank() }
                                            ?: story.author?.displayName ?: "Creator",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                if (uiState.errorMessage != null) {
                    item {
                        Box(modifier = Modifier.padding(16.dp)) {
                            ErrorRetryCard(
                                message = uiState.errorMessage,
                                onRetry = { viewModel.refreshAll() },
                            )
                        }
                    }
                }

                if (uiState.isLoading && uiState.posts.isEmpty()) {
                    items(3) {
                        SkeletonFeedCard()
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                } else if (uiState.posts.isEmpty()) {
                    item {
                        if (isGuest && uiState.selectedTab == FeedTab.FOLLOWING) {
                            EmptyStateCard(
                                icon = Icons.Rounded.DynamicFeed,
                                title = "Sign in for your Following feed",
                                subtitle = "Follow creators, professionals, and communities to build a personalized stream.",
                                actionLabel = "Sign In",
                                onAction = { onRequireAuth(false, "Sign in to follow creators and view your Following feed.") },
                            )
                        } else {
                            EmptyStateCard(
                                icon = Icons.Rounded.DynamicFeed,
                                title = if (uiState.isBackendConfigured) "Your feed is ready" else "Ready for live global posts",
                                subtitle = if (uiState.isBackendConfigured) {
                                    "Follow creators, join skill communities, or publish your first post or project update."
                                } else {
                                    "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to stream live posts, stories, and discussions."
                                },
                                actionLabel = "Create Post",
                                onAction = {
                                    if (isGuest) {
                                        onRequireAuth(true, "Create an account or sign in to publish posts.")
                                    } else {
                                        onOpenComposer()
                                    }
                                },
                            )
                        }
                    }
                } else {
                    items(uiState.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            currentUserId = currentUserId,
                            isLiked = post.id in uiState.likedPostIds,
                            isSaved = post.id in uiState.savedPostIds,
                            isFollowingAuthor = post.authorId in uiState.followingIds,
                            votedOptionId = uiState.votedPollOptions[post.id],
                            onAuthorClick = onOpenProfile,
                            onLikeClick = {
                                if (isGuest) onRequireAuth(false, "Sign in to like posts.")
                                else viewModel.toggleLike(post)
                            },
                            onCommentClick = { viewModel.openComments(post) },
                            onRepostClick = {
                                if (isGuest) onRequireAuth(false, "Sign in to repost.")
                                else viewModel.repost(post)
                            },
                            onQuoteClick = {
                                if (isGuest) onRequireAuth(false, "Sign in to quote repost.")
                                else quoteTarget = post
                            },
                            onSaveClick = {
                                if (isGuest) onRequireAuth(false, "Sign in to save posts to private collections.")
                                else viewModel.toggleSave(post)
                            },
                            onFollowClick = {
                                if (isGuest) onRequireAuth(false, "Sign in to follow creators.")
                                else viewModel.toggleFollow(post.authorId)
                            },
                            onVotePoll = { optionId ->
                                if (isGuest) onRequireAuth(false, "Sign in to vote in polls.")
                                else viewModel.votePoll(post.id, optionId)
                            },
                            onHashtagClick = onSearchHashtag,
                            onEditClick = { editTarget = post },
                            onDeleteClick = { viewModel.deletePost(post.id) },
                            onReportClick = {
                                if (isGuest) onRequireAuth(false, "Sign in to report content.")
                                else reportTarget = post
                            },
                        )
                    }

                    if (uiState.hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SkillectOutlinedButton(
                                    text = if (uiState.isLoadingMore) "Loading..." else "Load more posts",
                                    enabled = !uiState.isLoadingMore,
                                    shape = RoundedCornerShape(50),
                                    onClick = { viewModel.refreshFeed(reset = false) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Story Viewer Dialog (24h expiring media)
    activeStory?.let { story ->
        Dialog(
            onDismissRequest = { activeStory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                if (story.mediaType == "video") {
                    VideoPlayerBox(
                        videoUrl = story.mediaUrl,
                        isActive = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = story.mediaUrl,
                        contentDescription = "Story media",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkillectAvatar(
                        avatarUrl = story.author?.avatarUrl,
                        contentDescription = "Story author",
                        size = 38.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = story.author?.displayName ?: "Member",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = AppConfig.formatRelativeTimestamp(story.createdAt),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (story.authorId == currentUserId && currentUserId.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.deleteStory(story.id)
                            activeStory = null
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete story", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { activeStory = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close story", tint = Color.White)
                    }
                }
            }
        }
    }

    // Threaded Comments Bottom Sheet
    CommentsBottomSheet(
        uiState = uiState,
        isGuest = isGuest,
        viewModel = viewModel,
        onRequireAuth = onRequireAuth,
    )

    // Quote Repost Dialog
    quoteTarget?.let { target ->
        var quoteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { quoteTarget = null },
            title = { Text("Quote Repost") },
            text = {
                OutlinedTextField(
                    value = quoteText,
                    onValueChange = { quoteText = it },
                    label = { Text("Add your commentary") },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Post",
                    onClick = {
                        viewModel.repost(target, quoteText)
                        quoteTarget = null
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { quoteTarget = null }) { Text("Cancel") }
            },
        )
    }

    // Edit Post Dialog
    editTarget?.let { target ->
        var editedContent by remember { mutableStateOf(target.content) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Edit Post") },
            text = {
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Save",
                    onClick = {
                        viewModel.editPost(target.id, editedContent)
                        editTarget = null
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Cancel") }
            },
        )
    }

    // Safety Report Dialog
    reportTarget?.let { target ->
        ReportContentDialog(
            title = "Report Post",
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                viewModel.reportPost(target.id, reason, details)
                reportTarget = null
            },
        )
    }
}

@Composable
fun ReportContentDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit,
) {
    val reasons = listOf(
        "harassment_bullying" to "Harassment or bullying",
        "hate_content" to "Hate content",
        "scam_spam" to "Scam, spam, or malicious link",
        "impersonation" to "Impersonation",
        "explicit_sexual" to "Explicit sexual content",
        "grooming" to "Grooming or unsafe minor contact",
        "csam_exploitation" to "Child exploitation (Zero Tolerance)",
    )
    var selectedReason by remember { mutableStateOf(reasons.first().first) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reasons.forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedReason == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedReason = key },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Additional context") },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            SkillectPrimaryButton(
                text = "Submit Report",
                onClick = { onSubmit(selectedReason, details) },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    uiState: FeedUiState,
    isGuest: Boolean,
    viewModel: FeedViewModel,
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit,
) {
    uiState.activeCommentsPost ?: return
    ModalBottomSheet(onDismissRequest = viewModel::closeComments) {
        var draftComment by remember { mutableStateOf("") }
        var replyToId by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Discussion & Replies", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.loadingComments) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.comments.isEmpty() && !uiState.loadingComments) {
                Text(
                    text = "No replies yet. Start a constructive discussion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.comments, key = { it.id }) { comment ->
                        val indent = if (comment.parentCommentId != null) 28.dp else 0.dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indent),
                            verticalAlignment = Alignment.Top,
                        ) {
                            SkillectAvatar(
                                avatarUrl = comment.author?.avatarUrl,
                                contentDescription = "Comment author",
                                size = 32.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${comment.author?.displayName ?: "Member"} · ${AppConfig.formatRelativeTimestamp(comment.createdAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(text = comment.content, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    if (isGuest) {
                                        onRequireAuth(false, "Sign in to reply to comments.")
                                    } else {
                                        replyToId = comment.id
                                    }
                                }) {
                                    Text("Reply", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (isGuest) {
                SkillectPrimaryButton(
                    text = "Sign in to comment or reply",
                    onClick = { onRequireAuth(false, "Sign in to join the conversation.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guest_comment_auth_button"),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draftComment,
                        onValueChange = { draftComment = it },
                        placeholder = { Text(if (replyToId != null) "Write a reply..." else "Add a comment...") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        viewModel.submitComment(draftComment, replyToId)
                        draftComment = ""
                        replyToId = null
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send comment")
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
