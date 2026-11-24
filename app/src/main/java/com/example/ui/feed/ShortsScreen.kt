package com.example.ui.feed

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.AppConfig
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectCrimson
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.core.media.VideoPlayerBox
import com.example.core.network.PostDto

@Composable
fun ShortsScreen(
    uiState: FeedUiState,
    currentUserId: String,
    viewModel: FeedViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenComposer: () -> Unit,
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val isGuest = currentUserId.isBlank()
    val context = LocalContext.current
    var reportTarget by remember { mutableStateOf<PostDto?>(null) }
    var quoteTarget by remember { mutableStateOf<PostDto?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadShortVideos()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (uiState.shortVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateCard(
                    icon = Icons.Rounded.PlayCircleOutline,
                    title = "No Shorts yet",
                    subtitle = if (uiState.isBackendConfigured) {
                        "Short videos shared by the community will appear here."
                    } else {
                        "Short videos are temporarily unavailable. Please try again soon."
                    },
                    actionLabel = "Upload Short Video",
                    onAction = {
                        if (isGuest) onRequireAuth(true, "Create an account or sign in to upload short videos.")
                        else onOpenComposer()
                    },
                )
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { uiState.shortVideos.size })
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val videoPost = uiState.shortVideos[page]
                val isLiked = videoPost.id in uiState.likedPostIds
                val mediaUrl = videoPost.mediaUrls.firstOrNull().orEmpty()

                Box(modifier = Modifier.fillMaxSize()) {
                    // Only the visible page owns an ExoPlayer. Preloading players
                    // for neighboring pages was opening multiple video streams at once.
                    if (mediaUrl.isNotBlank() && pagerState.currentPage == page) {
                        VideoPlayerBox(
                            videoUrl = mediaUrl,
                            isActive = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Bottom gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                ),
                            ),
                    )

                    // Creator caption & handle
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, end = 82.dp, bottom = 18.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onOpenProfile(videoPost.authorId) },
                        ) {
                            SkillectAvatar(
                                avatarUrl = videoPost.author?.avatarUrl,
                                contentDescription = "Creator avatar",
                                size = 38.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "@${videoPost.author?.username ?: "creator"}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (videoPost.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = videoPost.content,
                                color = Color.White.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // Right-side vertical interaction column
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                enabled = videoPost.id !in uiState.pendingLikePostIds,
                                onClick = {
                                    if (isGuest) onRequireAuth(false, "Sign in to like short videos.")
                                    else viewModel.toggleLike(videoPost)
                                },
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Like video",
                                    tint = if (isLiked) SkillectCrimson else Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Text(
                                text = AppConfig.formatCompactCount(videoPost.likesCount),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = {
                                viewModel.openComments(videoPost)
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "Comments",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                text = AppConfig.formatCompactCount(videoPost.commentsCount),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(onClick = {
                            if (isGuest) onRequireAuth(false, "Sign in to quote a video.")
                            else quoteTarget = videoPost
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = "Quote video",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, videoPost.content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Short"))
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share short video",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        IconButton(onClick = {
                            if (isGuest) onRequireAuth(false, "Sign in to report short videos.")
                            else reportTarget = videoPost
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = "Report video",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        // Top Back Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("shorts_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = "Skillect Shorts",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }

    CommentsBottomSheet(
        uiState = uiState,
        isGuest = isGuest,
        viewModel = viewModel,
        onRequireAuth = onRequireAuth,
    )

    quoteTarget?.let { target ->
        var quoteText by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { quoteTarget = null },
            title = { Text("Quote this video") },
            text = {
                OutlinedTextField(
                    value = quoteText,
                    onValueChange = { quoteText = it },
                    label = { Text("Add your thoughts") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Publish quote",
                    enabled = quoteText.isNotBlank(),
                    onClick = {
                        viewModel.repost(target, quoteText)
                        quoteTarget = null
                    },
                )
            },
            dismissButton = { TextButton(onClick = { quoteTarget = null }) { Text("Cancel") } },
        )
    }

    reportTarget?.let { target ->
        ReportContentDialog(
            title = "Report Short Video",
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                viewModel.reportPost(target.id, reason, details)
                reportTarget = null
            },
        )
    }
}
