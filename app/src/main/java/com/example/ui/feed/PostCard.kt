package com.example.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.config.AppConfig
import com.example.core.designsystem.HashtagChip
import com.example.core.designsystem.SkillChip
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectCrimson
import com.example.core.designsystem.VerifiedCreatorBadge
import com.example.core.media.VideoPlayerBox
import com.example.core.network.PostDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCard(
    post: PostDto,
    currentUserId: String,
    isLiked: Boolean,
    isLikePending: Boolean = false,
    isSaved: Boolean,
    isFollowingAuthor: Boolean,
    isFollowPending: Boolean = false,
    votedOptionId: String?,
    onAuthorClick: (String) -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onRepostClick: () -> Unit,
    onQuoteClick: () -> Unit,
    onSaveClick: () -> Unit,
    onFollowClick: () -> Unit,
    onVotePoll: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    val author = post.author
    val isOwner = post.authorId == currentUserId && currentUserId.isNotBlank()

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "like_pop",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .testTag("post_card_${post.id}"),
    ) {
        if (post.postType == "repost" || post.postType == "quote" || post.repostedPostId != null || post.quotedPostId != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (post.postType == "quote" || post.quotedPostId != null) "Quote repost" else "Reposted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Editorial Post Header: Avatar with subtle ring, handle/name + verified + timestamp, inline Follow, ...
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkillectAvatar(
                avatarUrl = author?.avatarUrl,
                contentDescription = "${author?.displayName ?: "Member"} avatar",
                size = 38.dp,
                hasActiveStory = true,
                onClick = { onAuthorClick(post.authorId) },
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAuthorClick(post.authorId) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = author?.username?.takeIf { it.isNotBlank() }
                            ?: author?.displayName ?: "skillect_member",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (author?.isVerified == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedCreatorBadge()
                    }
                }

                val subLine = buildString {
                    val region = author?.regionLabel?.ifBlank { author.countryCode }.orEmpty()
                    if (region.isNotBlank()) append(region)
                    if (post.isEdited) {
                        if (isNotEmpty()) append(" · ")
                        append("Edited")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subLine.isNotBlank()) {
                        Text(
                            text = subLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (post.postType in listOf("project_update", "professional_update", "long_discussion")) {
                        if (subLine.isNotBlank()) Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = when (post.postType) {
                                    "project_update" -> "Project"
                                    "professional_update" -> "Career"
                                    else -> "Discussion"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (!isOwner) {
                TextButton(
                    enabled = !isFollowPending,
                    onClick = onFollowClick,
                    modifier = Modifier.testTag("follow_btn_${post.id}"),
                ) {
                    Text(
                        text = if (isFollowingAuthor) "Connected" else "Connect",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isFollowingAuthor) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "Post options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Quote repost") },
                        leadingIcon = { Icon(Icons.Rounded.Repeat, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onQuoteClick()
                        },
                    )
                    if (isOwner) {
                        DropdownMenuItem(
                            text = { Text("Edit post") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete post") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Report content") },
                            leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onReportClick()
                            },
                        )
                    }
                }
            }
        }

        // Body text
        if (post.content.isNotBlank()) {
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Full-bleed Media Attachments (single image, carousel with 1/N pill + circular arrow, or video)
        if (post.mediaUrls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            if (post.mediaTypes.firstOrNull() == "video" || post.postType == "video") {
                VideoPlayerBox(
                    videoUrl = post.mediaUrls.first(),
                    isActive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f),
                )
            } else if (post.mediaUrls.size == 1) {
                AsyncImage(
                    model = post.mediaUrls.first(),
                    contentDescription = "Post image attachment",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                )
            } else {
                val pagerState = rememberPagerState(pageCount = { post.mediaUrls.size })
                Box(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                    ) { page ->
                        AsyncImage(
                            model = post.mediaUrls[page],
                            contentDescription = "Carousel image ${page + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Top-right 1/N pill counter
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${post.mediaUrls.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    // Circular prev / next overlay arrows
                    if (pagerState.currentPage > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 10.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.85f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Previous media",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    if (pagerState.currentPage < post.mediaUrls.lastIndex) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.85f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Next media",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Link Preview
        if (!post.linkUrl.isNullOrBlank()) {
            val safeLink = post.linkUrl.takeIf { AppConfig.isValidHttpUrl(it) }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (safeLink != null) {
                            Modifier.clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeLink))
                                context.startActivity(intent)
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.linkTitle?.takeIf { it.isNotBlank() } ?: "External Resource",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = post.linkUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Poll Options
        if (post.pollOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            val totalVotes = post.pollOptions.sumOf { it.votesCount }.coerceAtLeast(1)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                post.pollOptions.forEach { option ->
                    val isSelected = votedOptionId == option.id
                    val ratio = if (votedOptionId != null) option.votesCount.toFloat() / totalVotes else 0f
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = votedOptionId == null) {
                                onVotePoll(option.id)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(option.optionText, style = MaterialTheme.typography.bodyMedium)
                                if (votedOptionId != null) {
                                    Text(
                                        "${(ratio * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            if (votedOptionId != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hashtags & Skill Tags
        if (post.hashtags.isNotEmpty() || post.skillTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                post.hashtags.forEach { tag ->
                    HashtagChip(tag = tag, onClick = { onHashtagClick(tag) })
                }
                post.skillTags.forEach { skill ->
                    SkillChip(label = skill, showCheckWhenSelected = false)
                }
            }
        }

        // Action Bar: Like, Comment, Repost, Save, Share
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = !isLikePending,
                    onClick = onLikeClick,
                    modifier = Modifier.testTag("like_btn_${post.id}"),
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) SkillectCrimson else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer(scaleX = likeScale, scaleY = likeScale),
                    )
                }
                Text(
                    text = AppConfig.formatCompactCount(post.likesCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier.testTag("comment_btn_${post.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = AppConfig.formatCompactCount(post.commentsCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onRepostClick,
                    modifier = Modifier.testTag("repost_btn_${post.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repost",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = AppConfig.formatCompactCount(post.repostsCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${author?.displayName ?: "Skillect"}: ${post.content}",
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share via Skillect"))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share post",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(
                    onClick = onSaveClick,
                    modifier = Modifier.testTag("save_btn_${post.id}"),
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isSaved) "Unsave" else "Save post",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
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
