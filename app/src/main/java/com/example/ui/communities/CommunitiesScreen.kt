package com.example.ui.communities

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.config.AppConfig
import com.example.core.designsystem.BackendConfigBanner
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.SkillChip
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectCard
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.ui.explore.ExploreCommunitiesViewModel
import com.example.ui.explore.ExploreUiState

@Composable
fun CommunitiesScreen(
    uiState: ExploreUiState,
    currentUserId: String,
    viewModel: ExploreCommunitiesViewModel,
    onOpenCommunity: (String) -> Unit,
    onBack: () -> Unit,
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val isGuest = currentUserId.isBlank()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text("Communities & Groups", style = MaterialTheme.typography.titleLarge)
                }
                SkillectPrimaryButton(
                    text = "New Group",
                    leadingIcon = Icons.Rounded.Add,
                    shape = RoundedCornerShape(50),
                    onClick = {
                        if (isGuest) onRequireAuth(true, "Create an account or sign in to start a community.")
                        else showCreateDialog = true
                    },
                    modifier = Modifier.testTag("create_community_button"),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val cats = listOf("All") + AppConfig.communityCategories
                items(cats) { category ->
                    SkillChip(
                        label = category,
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!AppConfig.isSupabaseConfigured) {
                    item { BackendConfigBanner() }
                }
                if (!uiState.errorMessage.isNullOrBlank()) {
                    item {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }
                if (!uiState.statusMessage.isNullOrBlank()) {
                    item {
                        Text(
                            text = uiState.statusMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }

                if (uiState.isLoading && uiState.communities.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                } else if (uiState.communities.isEmpty() && !uiState.isLoading) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Groups,
                            title = "Communities",
                            subtitle = "Discover groups for builders, artists, designers and more, or start your own.",
                            actionLabel = "Create Community",
                            onAction = {
                                if (isGuest) onRequireAuth(true, "Create an account or sign in to start a community.")
                                else showCreateDialog = true
                            },
                        )
                    }
                } else {
                    items(uiState.communities, key = { it.id }) { comm ->
                        val isMember = comm.id in uiState.joinedCommunityIds
                        SkillectCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenCommunity(comm.id) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SkillectAvatar(
                                    avatarUrl = comm.avatarUrl,
                                    contentDescription = "${comm.name} community avatar",
                                    size = 56.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(comm.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${comm.category} · ${AppConfig.formatCompactCount(comm.membersCount)} members" +
                                            if (comm.isPrivate) " · Private" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (isMember) {
                                    SkillectOutlinedButton(
                                        text = "Joined",
                                        enabled = comm.id !in uiState.communityActionsInProgress,
                                        shape = RoundedCornerShape(50),
                                        onClick = {
                                            if (isGuest) onRequireAuth(false, "Sign in to manage community memberships.")
                                            else viewModel.toggleJoinCommunity(comm.id)
                                        },
                                    )
                                } else {
                                    SkillectPrimaryButton(
                                        text = if (comm.id in uiState.communityActionsInProgress) "Joining..." else "Join",
                                        enabled = comm.id !in uiState.communityActionsInProgress,
                                        shape = RoundedCornerShape(50),
                                        onClick = {
                                            if (isGuest) onRequireAuth(false, "Sign in to join communities.")
                                            else viewModel.toggleJoinCommunity(comm.id)
                                        },
                                    )
                                }
                            }
                            if (comm.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = comm.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(AppConfig.communityCategories.first()) }
        var isPrivate by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Community") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Community name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Topic & rules") },
                        minLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AppConfig.communityCategories) { cat ->
                            SkillChip(
                                label = cat,
                                selected = category == cat,
                                onClick = { category = cat },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                        Text("Private group (approval required)")
                    }
                    if (!uiState.statusMessage.isNullOrBlank()) {
                        Text(
                            text = uiState.statusMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Create",
                    enabled = name.isNotBlank() && !uiState.isCreatingCommunity,
                    isLoading = uiState.isCreatingCommunity,
                    onClick = {
                        viewModel.createCommunity(name, desc, category, isPrivate) { created ->
                            if (created) showCreateDialog = false
                        }
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun CommunityDetailScreen(
    communityId: String,
    currentUserId: String,
    uiState: ExploreUiState,
    viewModel: ExploreCommunitiesViewModel,
    onCreateCommunityPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onBack: () -> Unit,
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val isGuest = currentUserId.isBlank()
    LaunchedEffect(communityId) {
        viewModel.openCommunityDetail(communityId)
    }

    val comm = uiState.selectedCommunity
    val isMember = communityId in uiState.joinedCommunityIds

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(comm?.name ?: "Community", style = MaterialTheme.typography.titleLarge)
                }
            }

            if (uiState.isLoading && comm == null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            }
            if (!uiState.errorMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    AsyncImage(
                        model = comm?.bannerUrl?.takeIf { it.isNotBlank() } ?: R.drawable.img_community_banner,
                        contentDescription = "${comm?.name ?: "Community"} banner",
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.img_community_banner),
                        error = painterResource(id = R.drawable.img_community_banner),
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = comm?.name ?: "Community Hub",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Text(
                            text = "${comm?.category ?: "Discussion"} · ${comm?.membersCount ?: 0} members",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkillectPrimaryButton(
                        text = if (communityId in uiState.communityActionsInProgress) "Please wait..." else if (isMember) "Leave Group" else "Join Group",
                        enabled = communityId !in uiState.communityActionsInProgress,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            if (isGuest) onRequireAuth(false, "Sign in to join communities.")
                            else viewModel.toggleJoinCommunity(communityId)
                        },
                    )
                    SkillectOutlinedButton(
                        text = "Post in Group",
                        shape = RoundedCornerShape(50),
                        onClick = {
                            if (isGuest) onRequireAuth(true, "Create an account or sign in to post in communities.")
                            else onCreateCommunityPost(communityId)
                        },
                    )
                }
            }

            if (uiState.communityMembers.isNotEmpty()) {
                item {
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Members & Moderators", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(uiState.communityMembers, key = { it.userId }) { member ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    SkillectAvatar(
                                        avatarUrl = member.profile?.avatarUrl,
                                        contentDescription = "Member avatar",
                                        size = 44.dp,
                                        onClick = { onOpenProfile(member.userId) },
                                    )
                                    Text(
                                        text = member.profile?.displayName ?: "Member",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        text = member.role.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.communityPosts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Groups,
                        title = "No discussions yet",
                        subtitle = "Start a topic or share a resource with this community.",
                        actionLabel = "Write Group Post",
                        onAction = {
                            if (isGuest) onRequireAuth(true, "Create an account or sign in to post in communities.")
                            else onCreateCommunityPost(communityId)
                        },
                    )
                }
            } else {
                items(uiState.communityPosts, key = { it.id }) { post ->
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SkillectAvatar(
                                avatarUrl = post.author?.avatarUrl,
                                contentDescription = "${post.author?.displayName ?: "Member"} avatar",
                                size = 38.dp,
                                onClick = { onOpenProfile(post.authorId) },
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = post.author?.displayName ?: "Skillect Member",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "@${post.author?.username ?: "member"} · ${AppConfig.formatRelativeTimestamp(post.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(post.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
