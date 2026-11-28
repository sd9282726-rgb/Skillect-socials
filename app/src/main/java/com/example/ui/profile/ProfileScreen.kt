package com.example.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.core.config.AppConfig
import com.example.core.designsystem.BackendConfigBanner
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.ErrorRetryCard
import com.example.core.designsystem.MinorSafeBadge
import com.example.core.designsystem.SkillChip
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectCard
import com.example.core.designsystem.SkillectGradientIconTile
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.core.designsystem.VerifiedCreatorBadge
import com.example.core.media.MediaUploadManager
import com.example.core.media.VideoPlayerBox
import com.example.core.network.StoryDto
import com.example.ui.feed.ReportContentDialog
import com.example.ui.auth.OnboardingSkillCatalog
import com.example.ui.auth.OnboardingValidation
import kotlinx.coroutines.launch

/** Adds invisible break opportunities to very long unbroken profile strings (URLs/IDs/etc.). */
private fun String.withProfileLineBreaks(maxRunLength: Int = 20): String {
    if (length <= maxRunLength) return this
    val output = StringBuilder(length + 8)
    var index = 0
    var runLength = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        val count = Character.charCount(codePoint)
        if (Character.isWhitespace(codePoint)) {
            output.appendCodePoint(codePoint)
            runLength = 0
        } else {
            if (runLength >= maxRunLength) {
                output.append('\u200B')
                runLength = 0
            }
            output.appendCodePoint(codePoint)
            runLength++
        }
        index += count
    }
    return output.toString()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    targetUserId: String?,
    currentUserId: String,
    uiState: ProfileSettingsUiState,
    memories: List<StoryDto> = emptyList(),
    onDeleteMemory: (String) -> Unit = {},
    viewModel: ProfileSettingsViewModel,
    mediaUploadManager: MediaUploadManager,
    onOpenSettings: () -> Unit,
    onOpenConversation: (conversationId: String, peerName: String) -> Unit = { _, _ -> },
    onOpenProfile: (userId: String) -> Unit = {},
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaScope = rememberCoroutineScope()
    val effectiveCurrentUserId = currentUserId
    var mediaStatus by remember { mutableStateOf<String?>(null) }
    var isSavingProfile by remember { mutableStateOf(false) }
    val isGuestOwnProfile = targetUserId == null && effectiveCurrentUserId.isBlank()

    if (isGuestOwnProfile) {
        GuestProfileView(
            onCreateAccount = { onRequireAuth(true, "Create a Skillect account to build your profile, showcase projects, and connect.") },
            onSignIn = { onRequireAuth(false, "Sign in to access your Skillect profile and saved collections.") },
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(targetUserId, effectiveCurrentUserId) {
        viewModel.loadProfile(targetUserId)
    }

    val profile = if (targetUserId == null) uiState.myProfile ?: uiState.viewedProfile else uiState.viewedProfile
    var avatarUrlForEdit by remember(profile?.id, profile?.avatarUrl) { mutableStateOf(profile?.avatarUrl) }
    var coverUrlForEdit by remember(profile?.id, profile?.coverUrl) { mutableStateOf(profile?.coverUrl) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) mediaScope.launch {
            mediaStatus = "Uploading profile photo..."
            mediaUploadManager.uploadProfileImage(uri, currentUserId, "avatar").fold(
                onSuccess = { url -> avatarUrlForEdit = url; mediaStatus = "Profile photo ready to save." },
                onFailure = { error -> mediaStatus = error.message ?: "Profile photo upload failed." },
            )
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) mediaScope.launch {
            mediaStatus = "Uploading profile background..."
            mediaUploadManager.uploadProfileImage(uri, currentUserId, "cover").fold(
                onSuccess = { url -> coverUrlForEdit = url; mediaStatus = "Profile background ready to save." },
                onFailure = { error -> mediaStatus = error.message ?: "Profile background upload failed." },
            )
        }
    }
    val isOwnProfile = targetUserId == null || (effectiveCurrentUserId.isNotBlank() && profile?.id == effectiveCurrentUserId)
    val profileMemories = remember(memories, profile?.id) { memories.filter { it.authorId == profile?.id } }
    var activeMemory by remember(profile?.id) { mutableStateOf<StoryDto?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var showExperienceDialog by remember { mutableStateOf(false) }
    var showAchievementDialog by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showNetworkSheet by remember { mutableStateOf(false) }
    var showPrivateMessageDialog by remember { mutableStateOf(false) }
    var pendingProfileDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (!profile?.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.coverUrl,
                            contentDescription = "Profile cover image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_community_banner),
                            contentDescription = "Default profile cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Row {
                            if (isOwnProfile) {
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape).testTag("profile_settings_button"),
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                                }
                            } else {
                                Box {
                                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Profile actions", tint = Color.White)
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Share profile") },
                                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "Connect with @${profile?.username ?: "member"} on Skillect")
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share Profile"))
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mute account") },
                                            onClick = {
                                                menuExpanded = false
                                                if (effectiveCurrentUserId.isBlank()) {
                                                    onRequireAuth(false, "Sign in to mute accounts.")
                                                } else {
                                                    profile?.id?.let(viewModel::muteUser)
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Block account") },
                                            leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                if (effectiveCurrentUserId.isBlank()) {
                                                    onRequireAuth(false, "Sign in to block accounts.")
                                                } else {
                                                    profile?.id?.let(viewModel::blockUser)
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Report account") },
                                            leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                if (effectiveCurrentUserId.isBlank()) {
                                                    onRequireAuth(false, "Sign in to submit safety reports.")
                                                } else {
                                                    showReportDialog = true
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .align(Alignment.BottomStart)
                            .offset(y = 38.dp)
                            .clickable(enabled = profileMemories.isNotEmpty()) { activeMemory = profileMemories.firstOrNull() }
                            .testTag("profile_avatar_memories"),
                    ) {
                        SkillectAvatar(
                            avatarUrl = profile?.avatarUrl,
                            contentDescription = "Profile avatar",
                            size = 82.dp,
                            hasActiveStory = profileMemories.isNotEmpty(),
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 46.dp)) {
                    if (!AppConfig.isSupabaseConfigured) {
                        Text(
                            "Some profile details are temporarily unavailable.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    if (!uiState.errorMessage.isNullOrBlank()) {
                        ErrorRetryCard(
                            message = uiState.errorMessage,
                            onRetry = { viewModel.loadProfile(targetUserId) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (profile?.displayName ?: "Skillect Member").withProfileLineBreaks(),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (profile?.isVerified == true) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    VerifiedCreatorBadge()
                                }
                            }
                            Text(
                                text = "@${profile?.username ?: "skillect_user"}".withProfileLineBreaks(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (isOwnProfile) {
                            SkillectOutlinedButton(
                                text = "Edit Profile",
                                leadingIcon = Icons.Rounded.Edit,
                                onClick = { showEditDialog = true },
                                modifier = Modifier.testTag("edit_profile_button"),
                            )
                        } else if (profile != null && !isOwnProfile) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkillectOutlinedButton(
                                    text = "Message",
                                    onClick = {
                                        if (effectiveCurrentUserId.isBlank()) {
                                            onRequireAuth(false, "Sign in to send direct messages.")
                                        } else if (profile.isPrivate && !uiState.isFollowing) {
                                            showPrivateMessageDialog = true
                                        } else {
                                            viewModel.startDirectConversation(
                                                profile.id,
                                                onSuccess = { conversationId ->
                                                    onOpenConversation(conversationId, profile.displayName)
                                                },
                                                onRequestRequired = { showPrivateMessageDialog = true },
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("profile_message_button"),
                                )
                                SkillectPrimaryButton(
                                    text = if (uiState.isFollowing) "Disconnect" else "Connect",
                                    onClick = {
                                        if (effectiveCurrentUserId.isBlank()) {
                                            onRequireAuth(false, "Sign in to connect with creators and build your network.")
                                        } else {
                                            viewModel.toggleFollow(profile.id)
                                        }
                                    },
                                    modifier = Modifier.testTag("profile_follow_button"),
                                )
                            }
                        }
                    }

                    if (!uiState.statusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.statusMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("profile_status_message"),
                        )
                    }

                    if (profile?.headline?.isNotBlank() == true) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = profile.headline.withProfileLineBreaks(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    if (profile?.bio?.isNotBlank() == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = profile.bio.withProfileLineBreaks(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), softWrap = true)
                    }

                    if (profile?.website?.isNotBlank() == true) {
                        val websiteUrl = profile.website.trim().let { raw ->
                            if (raw.startsWith("https://", true) || raw.startsWith("http://", true)) raw else "https://$raw"
                        }
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))) }
                                }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.WorkOutline, contentDescription = "Website", tint = MaterialTheme.colorScheme.primary)
                            Text(profile.website.withProfileLineBreaks(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, softWrap = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (profile != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${profile.regionLabel.ifBlank { profile.countryCode }} · ${profile.timezone}".withProfileLineBreaks(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (profile?.isMinorSafeDefaults == true) {
                            MinorSafeBadge()
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatCounter(uiState.postsCount ?: maxOf(profile?.postsCount ?: 0, uiState.posts.size), "Posts")
                        val canViewNetwork = profile?.isPrivate != true || isOwnProfile || uiState.isFollowing
                        if (canViewNetwork) {
                            StatCounter(
                                uiState.followersCount ?: profile?.followersCount ?: 0,
                                "Networks",
                                modifier = Modifier.clickable {
                                    profile?.id?.let { viewModel.loadNetworkList(it, followers = true) }
                                    showNetworkSheet = true
                                }.testTag("profile_followers_counter"),
                            )
                            StatCounter(
                                uiState.followingCount ?: profile?.followingCount ?: 0,
                                "Connected",
                                modifier = Modifier.clickable {
                                    profile?.id?.let { viewModel.loadNetworkList(it, followers = false) }
                                    showNetworkSheet = true
                                }.testTag("profile_following_counter"),
                            )
                        }
                    }

                    if (profile != null && (profile.skills.isNotEmpty() || profile.interests.isNotEmpty())) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val allProfileSkills = (profile.skills + profile.interests).distinct()
                        val visibleInterests = allProfileSkills.take(3)
                        val extraInterests = (allProfileSkills.size - visibleInterests.size).coerceAtLeast(0)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            visibleInterests.forEach { SkillChip(label = it) }
                            if (extraInterests > 0) {
                                SkillChip(label = "+$extraInterests more", showCheckWhenSelected = false)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(14.dp))
                val tabs = if (isOwnProfile) listOf("Posts", "Quotes", "Projects", "Career", "Saved") else listOf("Posts", "Quotes", "Projects", "Career")
                // Intrinsic-width tabs prevent labels from wrapping into fragments on phones.
                // The row scrolls horizontally on narrow screens instead of compressing text.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { idx, label ->
                        val isSel = selectedTab == idx
                        Column(
                            modifier = Modifier
                                .widthIn(min = 42.dp)
                                .clickable { selectedTab = idx }
                                .padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(9.dp))
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent),
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            when (selectedTab) {
                0 -> {
                    val regularPosts = uiState.posts.filterNot { it.postType == "quote" || it.quotedPostId != null }
                    if (regularPosts.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Rounded.Person,
                                    title = "No posts yet",
                                    subtitle = "Published posts, visual media, threads, and project updates will appear here.",
                                )
                            }
                        }
                    } else {
                        items(regularPosts, key = { it.id }) { post ->
                            SkillectCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenProfile(post.authorId) }
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SkillectAvatar(
                                        avatarUrl = post.author?.avatarUrl ?: profile?.avatarUrl,
                                        contentDescription = "Post author avatar",
                                        size = 36.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            post.author?.displayName?.takeIf { it.isNotBlank() }
                                                ?: post.author?.username?.takeIf { it.isNotBlank() }
                                                ?: profile?.displayName?.takeIf { it.isNotBlank() }
                                                ?: profile?.username?.takeIf { it.isNotBlank() }
                                                ?: "Member",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            "@" + (post.author?.username?.takeIf { it.isNotBlank() } ?: profile?.username.orEmpty()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isOwnProfile) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { pendingProfileDelete = "post" to post.id }) { Text("Delete") }
                                    }
                                }
                                if (post.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(post.content.withProfileLineBreaks(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), softWrap = true, maxLines = Int.MAX_VALUE)
                                }
                                if (post.mediaUrls.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (post.postType == "video" || post.mediaTypes.firstOrNull() == "video") {
                                        VideoPlayerBox(
                                            videoUrl = post.mediaUrls.first(),
                                            isActive = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f),
                                        )
                                    } else {
                                        AsyncImage(
                                            model = post.mediaUrls.first(),
                                            contentDescription = "Post media",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${AppConfig.formatCompactCount(post.likesCount)} likes · ${AppConfig.formatCompactCount(post.commentsCount)} comments",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                1 -> {
                    val quotePosts = uiState.posts.filter { it.postType == "quote" || it.quotedPostId != null }
                    if (quotePosts.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Rounded.Repeat,
                                    title = "No quotes yet",
                                    subtitle = "When this member quotes a post or video, it will appear here.",
                                )
                            }
                        }
                    } else {
                        items(quotePosts, key = { "quote_${it.id}" }) { quote ->
                            SkillectCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                                Text("QUOTE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                if (quote.content.isNotBlank()) {
                                    Text(
                                        text = quote.content.withProfileLineBreaks(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.fillMaxWidth(),
                                        softWrap = true,
                                        maxLines = Int.MAX_VALUE,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Quoted post or video", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (isOwnProfile) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { pendingProfileDelete = "post" to quote.id }) { Text("Delete") }
                                    }
                                }
                                Text(
                                    "${AppConfig.formatCompactCount(quote.likesCount)} likes · ${AppConfig.formatCompactCount(quote.commentsCount)} comments",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                2 -> {
                    if (isOwnProfile) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                SkillectPrimaryButton(
                                    text = "Add Project",
                                    leadingIcon = Icons.Rounded.Add,
                                    onClick = { showProjectDialog = true },
                                )
                            }
                        }
                    }
                    if (uiState.projects.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Rounded.Code,
                                    title = "Project Showcase",
                                    subtitle = "Showcase builds, creative portfolios, open-source work, and collaborator details.",
                                )
                            }
                        }
                    } else {
                        items(uiState.projects, key = { it.id }) { proj ->
                            SkillectCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = proj.title.withProfileLineBreaks(),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.fillMaxWidth(),
                                        softWrap = true,
                                        maxLines = Int.MAX_VALUE,
                                    )
                                    if (isOwnProfile) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { pendingProfileDelete = "project" to proj.id }) { Text("Delete") }
                                        }
                                    }
                                }
                                Text(proj.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = proj.description.withProfileLineBreaks(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                    softWrap = true,
                                    maxLines = Int.MAX_VALUE,
                                )
                                if (proj.skillsUsed.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        proj.skillsUsed.forEach { SkillChip(label = it) }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    if (isOwnProfile) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                SkillectOutlinedButton(
                                    text = "Add Experience",
                                    leadingIcon = Icons.Rounded.Add,
                                    onClick = { showExperienceDialog = true },
                                )
                                SkillectOutlinedButton(
                                    text = "Add Achievement",
                                    leadingIcon = Icons.Rounded.EmojiEvents,
                                    onClick = { showAchievementDialog = true },
                                )
                            }
                        }
                    }
                    if (uiState.experiences.isEmpty() && uiState.achievements.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Rounded.WorkOutline,
                                    title = "Experience & Achievements",
                                    subtitle = "Add roles, creative collaborations, education, and career milestones.",
                                )
                            }
                        }
                    } else {
                        if (uiState.experiences.isNotEmpty()) {
                            items(uiState.experiences, key = { it.id }) { exp ->
                                SkillectCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = exp.title.withProfileLineBreaks(),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.fillMaxWidth(),
                                            softWrap = true,
                                            maxLines = Int.MAX_VALUE,
                                        )
                                        if (isOwnProfile) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = { pendingProfileDelete = "experience" to exp.id }) { Text("Delete") }
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${exp.organization} · ${exp.startDate}".withProfileLineBreaks(),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        softWrap = true,
                                        maxLines = Int.MAX_VALUE,
                                    )
                                    if (exp.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = exp.description.withProfileLineBreaks(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.fillMaxWidth(),
                                            softWrap = true,
                                            maxLines = Int.MAX_VALUE,
                                        )
                                    }
                                }
                            }
                        }
                        if (uiState.achievements.isNotEmpty()) {
                            items(uiState.achievements, key = { "ach_${it.id}" }) { ach ->
                                SkillectCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.EmojiEvents,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = ach.title.withProfileLineBreaks(),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f),
                                            softWrap = true,
                                            maxLines = Int.MAX_VALUE,
                                        )
                                    }
                                    if (isOwnProfile) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { pendingProfileDelete = "achievement" to ach.id }) { Text("Delete") }
                                        }
                                    }
                                    val subtitle = listOfNotNull(
                                        ach.issuer.takeIf { it.isNotBlank() },
                                        ach.awardedAt?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · ")
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            text = subtitle.withProfileLineBreaks(),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary,
                                            softWrap = true,
                                            maxLines = Int.MAX_VALUE,
                                        )
                                    }
                                    if (ach.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = ach.description.withProfileLineBreaks(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.fillMaxWidth(),
                                            softWrap = true,
                                            maxLines = Int.MAX_VALUE,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Private Saved Collections", style = MaterialTheme.typography.titleMedium)
                            SkillectOutlinedButton(
                                text = "New Collection",
                                leadingIcon = Icons.Rounded.Add,
                                onClick = { showCollectionDialog = true },
                            )
                        }
                    }
                    if (uiState.savedPosts.isEmpty() && uiState.savedCollections.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Rounded.Bookmark,
                                    title = "Saved Content is Private",
                                    subtitle = "Bookmark posts into private collections visible only to you.",
                                )
                            }
                        }
                    } else {
                        items(uiState.savedCollections, key = { it.id }) { col ->
                            SkillectCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                                Text(col.name.withProfileLineBreaks(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), softWrap = true)
                                Text("Private collection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(uiState.savedPosts, key = { it.postId }) { saved ->
                            SkillectCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                                Text(
                                    text = (saved.post?.content ?: "Saved post ${saved.postId}").withProfileLineBreaks(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    activeMemory?.let { memory ->
        Dialog(
            onDismissRequest = { activeMemory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
            ) {
                if (memory.mediaType == "video") {
                    VideoPlayerBox(
                        videoUrl = memory.mediaUrl,
                        isActive = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = memory.mediaUrl,
                        contentDescription = "Memory media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkillectAvatar(
                        avatarUrl = memory.author?.avatarUrl,
                        contentDescription = "Memory author",
                        size = 38.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(memory.author?.displayName ?: "Member", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Text("Memory · 12-hour post", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { activeMemory = null }) {
                        Text("Close", color = Color.White)
                    }
                }
                if (memory.caption.isNotBlank()) {
                    Text(
                        text = memory.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp),
                    )
                }
            }
        }
    }

    if (showPrivateMessageDialog && profile != null) {
        var messageText by remember(profile.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPrivateMessageDialog = false },
            title = { Text("This profile is private") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Send a message request. They can accept or reject it from their Messages inbox.")
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("Your message") },
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Send request",
                    enabled = messageText.isNotBlank(),
                    onClick = {
                        viewModel.sendMessageRequest(profile.id, messageText)
                        showPrivateMessageDialog = false
                    },
                )
            },
            dismissButton = { TextButton(onClick = { showPrivateMessageDialog = false }) { Text("Cancel") } },
        )
    }

    pendingProfileDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingProfileDelete = null },
            title = { Text("Delete this item?") },
            text = { Text("This will permanently remove it from your profile.") },
            confirmButton = {
                TextButton(onClick = {
                    when (target.first) {
                        "post" -> viewModel.deletePost(target.second)
                        "project" -> viewModel.deleteProject(target.second)
                        "experience" -> viewModel.deleteExperience(target.second)
                        "achievement" -> viewModel.deleteAchievement(target.second)
                    }
                    pendingProfileDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingProfileDelete = null }) { Text("Cancel") } },
        )
    }

    if (showEditDialog) {
        var displayName by remember { mutableStateOf(profile?.displayName.orEmpty()) }
        var username by remember { mutableStateOf(profile?.username.orEmpty()) }
        var selectedSkills by remember(profile?.id) { mutableStateOf(profile?.skills?.filter { it in OnboardingSkillCatalog.all }?.toSet().orEmpty()) }
        var skillsMenuExpanded by remember { mutableStateOf(false) }
        var skillSearch by remember { mutableStateOf("") }
        var selectedCountry by remember(profile?.id) {
            mutableStateOf(AppConfig.supportedCountries.firstOrNull { it.isoCode.equals(profile?.countryCode, true) } ?: AppConfig.supportedCountries.first())
        }
        var countryMenuExpanded by remember { mutableStateOf(false) }
        var countrySearch by remember { mutableStateOf("") }
        var selectedInterests by remember(profile?.id) {
            mutableStateOf(profile?.interests?.filter { it in OnboardingSkillCatalog.interests }?.toSet().orEmpty())
        }
        var interestsMenuExpanded by remember { mutableStateOf(false) }
        var website by remember { mutableStateOf(profile?.website.orEmpty()) }
        var bio by remember { mutableStateOf(profile?.bio.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Edit your profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Make it easier for people to discover what you do", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("BASIC INFORMATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") })
                    OutlinedTextField(value = username, onValueChange = { username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' } }, label = { Text("Username") }, supportingText = { Text("3–24 letters, numbers or underscores") })
                    Text("SKILLS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkillectOutlinedButton(
                                text = if (selectedSkills.isEmpty()) "Choose skills" else "Skills (${selectedSkills.size} selected)",
                                leadingIcon = Icons.Rounded.Code,
                                onClick = { skillsMenuExpanded = true },
                            )
                            if (selectedSkills.isNotEmpty()) {
                                Text(
                                    selectedSkills.sorted().joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = skillsMenuExpanded,
                            onDismissRequest = { skillsMenuExpanded = false },
                        ) {
                            OutlinedTextField(
                                value = skillSearch,
                                onValueChange = { skillSearch = it },
                                label = { Text("Search skills") },
                                singleLine = true,
                                modifier = Modifier.padding(horizontal = 10.dp).widthIn(min = 220.dp),
                            )
                            OnboardingSkillCatalog.categories.forEach { (category, skills) ->
                                val filteredSkills = skills.filter { it.contains(skillSearch, ignoreCase = true) }
                                if (filteredSkills.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(category, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                        onClick = {},
                                        enabled = false,
                                    )
                                }
                                filteredSkills.forEach { skill ->
                                    DropdownMenuItem(
                                        text = { Text(skill) },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = skill in selectedSkills,
                                                onCheckedChange = null,
                                            )
                                        },
                                        onClick = {
                                            selectedSkills = if (skill in selectedSkills) selectedSkills - skill else selectedSkills + skill
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Text("PUBLIC PROFILE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text("Website URL") },
                        placeholder = { Text("yourwebsite.com") },
                        supportingText = { Text("Your website will appear as a clickable link on your profile.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Text("COUNTRY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SkillectOutlinedButton(
                            text = "${OnboardingValidation.countryFlagEmoji(selectedCountry.isoCode)}  ${selectedCountry.name}",
                            leadingIcon = Icons.Rounded.Flag,
                            onClick = { countryMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = countryMenuExpanded,
                            onDismissRequest = { countryMenuExpanded = false; countrySearch = "" },
                        ) {
                            OutlinedTextField(
                                value = countrySearch,
                                onValueChange = { countrySearch = it },
                                placeholder = { Text("Search countries") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                                modifier = Modifier.padding(horizontal = 10.dp).widthIn(min = 240.dp),
                            )
                            AppConfig.supportedCountries.filter {
                                countrySearch.isBlank() || it.name.contains(countrySearch, true) || it.isoCode.contains(countrySearch, true)
                            }.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text("${OnboardingValidation.countryFlagEmoji(country.isoCode)}  ${country.name}") },
                                    onClick = { selectedCountry = country; countryMenuExpanded = false; countrySearch = "" },
                                )
                            }
                        }
                    }
                    Text("INTERESTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkillectOutlinedButton(
                                text = if (selectedInterests.isEmpty()) "Choose interests" else "Interests (${selectedInterests.size} selected)",
                                leadingIcon = Icons.Rounded.Flag,
                                onClick = { interestsMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (selectedInterests.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    selectedInterests.sorted().forEach { SkillChip(label = it, selected = true) }
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = interestsMenuExpanded,
                            onDismissRequest = { interestsMenuExpanded = false },
                        ) {
                            OnboardingSkillCatalog.interests.forEach { interest ->
                                DropdownMenuItem(
                                    text = { Text(interest) },
                                    leadingIcon = { Checkbox(checked = interest in selectedInterests, onCheckedChange = null) },
                                    onClick = {
                                        selectedInterests = if (interest in selectedInterests) selectedInterests - interest else selectedInterests + interest
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkillectAvatar(
                            avatarUrl = avatarUrlForEdit,
                            contentDescription = "Selected profile photo",
                            size = 58.dp,
                        )
                        if (!coverUrlForEdit.isNullOrBlank()) {
                            AsyncImage(
                                model = coverUrlForEdit,
                                contentDescription = "Selected profile background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(MaterialTheme.shapes.small),
                            )
                        } else {
                            Text(
                                "No custom background selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkillectOutlinedButton(
                            text = "Choose photo",
                            leadingIcon = Icons.Rounded.Person,
                            onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                        SkillectOutlinedButton(
                            text = "Choose background",
                            leadingIcon = Icons.Rounded.Edit,
                            onClick = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                    }
                    mediaStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (!uiState.statusMessage.isNullOrBlank()) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.statusMessage == "Profile updated.") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Save",
                    enabled = mediaStatus?.startsWith("Uploading") != true && !isSavingProfile,
                    isLoading = isSavingProfile,
                    onClick = {
                        isSavingProfile = true
                        viewModel.updateProfile(
                            displayName, website, bio, selectedCountry.isoCode, selectedCountry.name, selectedInterests.sorted().joinToString(", "), username, selectedSkills.sorted().joinToString(", "), avatarUrlForEdit, coverUrlForEdit,
                        ) { saved ->
                            isSavingProfile = false
                            if (saved) showEditDialog = false
                        }
                    },
                )
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } },
        )
    }

    if (showProjectDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Technology") }
        var skills by remember { mutableStateOf("") }
        var collaborators by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showProjectDialog = false },
            title = { Text("Showcase Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Project Title") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, minLines = 3)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                    OutlinedTextField(value = skills, onValueChange = { skills = it }, label = { Text("Skills used (comma-separated)") })
                    OutlinedTextField(value = collaborators, onValueChange = { collaborators = it }, label = { Text("Collaborators note") })
                }
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Publish",
                    enabled = title.isNotBlank() && desc.isNotBlank(),
                    onClick = {
                        viewModel.addProject(title, desc, category, skills, collaborators)
                        showProjectDialog = false
                    },
                )
            },
            dismissButton = { TextButton(onClick = { showProjectDialog = false }) { Text("Cancel") } },
        )
    }

                    if (showExperienceDialog) {
                        var title by remember { mutableStateOf("") }
                        var org by remember { mutableStateOf("") }
                        var start by remember { mutableStateOf("2024-01-01") }
                        var desc by remember { mutableStateOf("") }

                        AlertDialog(
                            onDismissRequest = { showExperienceDialog = false },
                            title = { Text("Add Experience") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Role / Title") })
                                    OutlinedTextField(value = org, onValueChange = { org = it }, label = { Text("Organization / Studio") })
                                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start Date (YYYY-MM-DD)") })
                                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Highlights") }, minLines = 2)
                                }
                            },
                            confirmButton = {
                                SkillectPrimaryButton(
                                    text = "Save",
                                    enabled = title.isNotBlank() && org.isNotBlank(),
                                    onClick = {
                                        viewModel.addExperience(title, org, start, desc)
                                        showExperienceDialog = false
                                    },
                                )
                            },
                            dismissButton = { TextButton(onClick = { showExperienceDialog = false }) { Text("Cancel") } },
                        )
                    }

                    if (showAchievementDialog) {
                        var title by remember { mutableStateOf("") }
                        var issuer by remember { mutableStateOf("") }
                        var awardedAt by remember { mutableStateOf("") }
                        var desc by remember { mutableStateOf("") }

                        AlertDialog(
                            onDismissRequest = { showAchievementDialog = false },
                            title = { Text("Add Achievement") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Achievement / Certification") })
                                    OutlinedTextField(value = issuer, onValueChange = { issuer = it }, label = { Text("Issuer / Organization") })
                                    OutlinedTextField(value = awardedAt, onValueChange = { awardedAt = it }, label = { Text("Date (YYYY-MM-DD, optional)") })
                                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Details") }, minLines = 2)
                                }
                            },
                            confirmButton = {
                                SkillectPrimaryButton(
                                    text = "Save",
                                    enabled = title.isNotBlank(),
                                    onClick = {
                                        viewModel.addAchievement(title, issuer, awardedAt, desc)
                                        showAchievementDialog = false
                                    },
                                )
                            },
                            dismissButton = { TextButton(onClick = { showAchievementDialog = false }) { Text("Cancel") } },
                        )
                    }

    if (showCollectionDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCollectionDialog = false },
            title = { Text("New Private Collection") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Collection Name") })
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Create",
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createSavedCollection(name)
                        showCollectionDialog = false
                    },
                )
            },
            dismissButton = { TextButton(onClick = { showCollectionDialog = false }) { Text("Cancel") } },
        )
    }

    if (showNetworkSheet) {
        ModalBottomSheet(onDismissRequest = { showNetworkSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(uiState.networkTitle, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    uiState.isLoadingNetwork && uiState.networkProfiles.isEmpty() -> CircularProgressIndicator()
                    uiState.networkProfiles.isEmpty() -> Text(
                        "No accounts to show yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                        items(uiState.networkProfiles, key = { it.id }) { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        showNetworkSheet = false
                                        onOpenProfile(member.id)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SkillectAvatar(avatarUrl = member.avatarUrl, contentDescription = member.displayName + " avatar", size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(member.displayName.ifBlank { member.username }, style = MaterialTheme.typography.titleSmall)
                                    Text("@" + member.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    if (showReportDialog && profile != null) {
        ReportContentDialog(
            title = "Report Account",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                viewModel.reportProfile(profile.id, reason, details)
                showReportDialog = false
            },
        )
    }
}

@Composable
private fun StatCounter(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = AppConfig.formatCompactCount(count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GuestProfileView(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("guest_profile_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkillectBrandBadge(size = 28.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("profile_settings_button"),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SkillectGradientIconTile(icon = Icons.Rounded.Person)
                        Spacer(modifier = Modifier.height(18.dp))
                        MinorSafeBadge()
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your skills, projects, networks, and posts in one global profile. Create an account or sign in to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        SkillectPrimaryButton(
                            text = "Create an account",
                            onClick = onCreateAccount,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guest_create_account_button"),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SkillectOutlinedButton(
                            text = "Sign in",
                            onClick = onSignIn,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guest_sign_in_button"),
                        )
                    }
                }
            }
        }
    }
}
