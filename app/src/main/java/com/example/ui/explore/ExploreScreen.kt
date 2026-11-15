package com.example.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.config.AppConfig
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.ErrorRetryCard
import com.example.core.designsystem.HashtagChip
import com.example.core.designsystem.SkillChip
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectCard
import com.example.core.designsystem.VerifiedCreatorBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    uiState: ExploreUiState,
    viewModel: ExploreCommunitiesViewModel,
    onOpenProfile: (String) -> Unit,
    onOpenCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkillectBrandBadge(size = 28.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Explore",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                viewModel.setSort(
                                    if (uiState.sort == ExploreSort.TRENDING) ExploreSort.LATEST else ExploreSort.TRENDING,
                                )
                            },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.sort == ExploreSort.TRENDING) "Trending" else "Latest",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::updateQuery,
                    placeholder = { Text("Search people, @usernames, #tags, skills, projects...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = viewModel::searchNow) {
                            Icon(Icons.Rounded.Search, contentDescription = "Run search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_search_input"),
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExploreFilter.entries) { filter ->
                        SkillChip(
                            label = filter.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = uiState.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (!AppConfig.isSupabaseConfigured) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("Discovery is temporarily unavailable. Please try again shortly.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (uiState.errorMessage != null) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            ErrorRetryCard(
                                message = uiState.errorMessage,
                                onRetry = viewModel::refreshExplore,
                            )
                        }
                    }
                }

                val hashtags = uiState.posts.flatMap { it.hashtags }.distinct()
                if (hashtags.isNotEmpty() && (uiState.filter == ExploreFilter.ALL || uiState.filter == ExploreFilter.HASHTAGS)) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "TRENDING TOPICS",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                hashtags.take(12).forEach { tag ->
                                    HashtagChip(tag = tag, onClick = {
                                        viewModel.updateQuery(tag)
                                        viewModel.searchNow()
                                    })
                                }
                            }
                        }
                    }
                }

                if (uiState.people.isNotEmpty() && (uiState.filter == ExploreFilter.ALL || uiState.filter == ExploreFilter.PEOPLE)) {
                    item {
                        Text(
                            text = "CREATORS & PROFESSIONALS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    items(uiState.people, key = { "person_${it.id}" }) { person ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(person.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SkillectAvatar(
                                    avatarUrl = person.avatarUrl,
                                    contentDescription = "${person.displayName} avatar",
                                    size = 50.dp,
                                    hasActiveStory = person.isVerified,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "@${person.username}",
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (person.isVerified) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            VerifiedCreatorBadge()
                                        }
                                    }
                                    val loc = person.regionLabel.ifBlank { person.countryCode }
                                    val sub = person.headline.ifBlank {
                                        listOf(person.displayName, loc)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                            .ifBlank { person.bio.ifBlank { "Skillect Creator" } }
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val allBadges = (person.skills + person.interests).distinct()
                                    val badges = allBadges.take(3)
                                    val extraBadges = (allBadges.size - badges.size).coerceAtLeast(0)
                                    if (badges.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            badges.forEach { tag ->
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                            if (extraBadges > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                ) {
                                                    Text(
                                                        text = "+$extraBadges more",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        text = "${AppConfig.formatCompactCount(person.followersCount)} networks",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }

                if (uiState.posts.isNotEmpty() && (uiState.filter == ExploreFilter.ALL || uiState.filter == ExploreFilter.POSTS)) {
                    item {
                        Text(
                            text = "POSTS & DISCUSSIONS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    items(uiState.posts, key = { "exp_post_${it.id}" }) { post ->
                        SkillectCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            onClick = { onOpenProfile(post.authorId) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkillectAvatar(
                                    avatarUrl = post.author?.avatarUrl,
                                    contentDescription = "${post.author?.displayName ?: "Member"} avatar",
                                    size = 34.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
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
                            if (post.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(post.content, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${AppConfig.formatCompactCount(post.likesCount)} likes · ${AppConfig.formatCompactCount(post.commentsCount)} comments",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (uiState.projects.isNotEmpty() && (uiState.filter == ExploreFilter.ALL || uiState.filter == ExploreFilter.PROJECTS)) {
                    item {
                        Text(
                            text = "FEATURED PROJECTS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    items(uiState.projects, key = { "proj_${it.id}" }) { proj ->
                        SkillectCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            onClick = { onOpenProfile(proj.ownerId) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(proj.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(proj.description, style = MaterialTheme.typography.bodyMedium)
                            if (proj.skillsUsed.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    proj.skillsUsed.forEach { SkillChip(label = it, showCheckWhenSelected = false) }
                                }
                            }
                        }
                    }
                }

                if (uiState.communities.isNotEmpty() && (uiState.filter == ExploreFilter.ALL || uiState.filter == ExploreFilter.COMMUNITIES)) {
                    item {
                        Text(
                            text = "POPULAR COMMUNITIES",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    items(uiState.communities, key = { "comm_${it.id}" }) { comm ->
                        SkillectCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            onClick = { onOpenCommunity(comm.id) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SkillectAvatar(
                                    avatarUrl = comm.avatarUrl,
                                    contentDescription = "${comm.name} community avatar",
                                    size = 48.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(comm.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${comm.category} · ${AppConfig.formatCompactCount(comm.membersCount)} members",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
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

                if (uiState.skills.isNotEmpty() && (uiState.filter == ExploreFilter.ALL || uiState.filter == ExploreFilter.SKILLS)) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                text = "SKILL DIRECTORY",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.skills.forEach { skill ->
                                    SkillChip(
                                        label = skill.name,
                                        onClick = {
                                            viewModel.updateQuery(skill.name)
                                            viewModel.refreshExplore()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                val hasVisibleResults = when (uiState.filter) {
                    ExploreFilter.ALL -> uiState.people.isNotEmpty() || uiState.posts.isNotEmpty() ||
                        uiState.communities.isNotEmpty() || uiState.projects.isNotEmpty() ||
                        uiState.skills.isNotEmpty() || hashtags.isNotEmpty()
                    ExploreFilter.PEOPLE -> uiState.people.isNotEmpty()
                    ExploreFilter.POSTS -> uiState.posts.isNotEmpty()
                    ExploreFilter.HASHTAGS -> hashtags.isNotEmpty()
                    ExploreFilter.COMMUNITIES -> uiState.communities.isNotEmpty()
                    ExploreFilter.PROJECTS -> uiState.projects.isNotEmpty()
                    ExploreFilter.SKILLS -> uiState.skills.isNotEmpty()
                }
                if (uiState.isLoading && !hasVisibleResults) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                } else if (!uiState.isLoading && !hasVisibleResults) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Explore,
                            title = when (uiState.filter) {
                                ExploreFilter.PEOPLE -> "No people found"
                                ExploreFilter.POSTS -> "No posts found"
                                ExploreFilter.HASHTAGS -> "No trending topics yet"
                                ExploreFilter.COMMUNITIES -> "No communities found"
                                ExploreFilter.PROJECTS -> "No projects found"
                                ExploreFilter.SKILLS -> "No skills found"
                                ExploreFilter.ALL -> "Explore"
                            },
                            subtitle = if (uiState.query.isNotBlank()) {
                                "Try another search or clear your search text."
                            } else if (AppConfig.isSupabaseConfigured) {
                                "There is nothing to show in this section yet. Check back as the community grows."
                            } else {
                                "Discovery is temporarily unavailable. Please try again shortly."
                            },
                        )
                    }
                }
            }
        }
    }
}
