package com.example.ui.notifications

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.config.AppConfig
import com.example.core.designsystem.BackendConfigBanner
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.ErrorRetryCard
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectGradientIconTile
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.ui.messages.MessagesNotificationsUiState
import com.example.ui.messages.MessagesNotificationsViewModel

@Composable
fun NotificationsScreen(
    uiState: MessagesNotificationsUiState,
    currentUserId: String,
    viewModel: MessagesNotificationsViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
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
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text("Notifications", style = MaterialTheme.typography.titleLarge)
                }
                if (currentUserId.isNotBlank() && uiState.unreadNotificationsCount > 0) {
                    TextButton(onClick = viewModel::markAllNotificationsRead) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark all read")
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (currentUserId.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("guest_notifications_gate"),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SkillectGradientIconTile(icon = Icons.Rounded.NotificationsNone)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Sign in for activity notifications",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Get real-time alerts for new network connections, replies, mentions, reposts, and community activity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        SkillectPrimaryButton(
                            text = "Create an account",
                            onClick = { onRequireAuth(true, "Create an account to receive activity notifications.") },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SkillectOutlinedButton(
                            text = "Sign in",
                            onClick = { onRequireAuth(false, "Sign in to view your notifications.") },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                return@Column
            }

            LaunchedEffect(currentUserId) {
                viewModel.refreshNotifications()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (!AppConfig.isSupabaseConfigured) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            BackendConfigBanner()
                        }
                    }
                }

                if (uiState.errorMessage != null) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            ErrorRetryCard(
                                message = uiState.errorMessage,
                                onRetry = viewModel::refreshNotifications,
                            )
                        }
                    }
                }

                if (uiState.notifications.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.NotificationsNone,
                            title = "In-App & Push Alert Centre",
                            subtitle = "Networks, likes, comments, mentions, reposts, messages, community activity, and project interactions appear here in real time via Supabase + FCM.",
                        )
                    }
                } else {
                    items(uiState.notifications, key = { it.id }) { notif ->
                        val rowBg = if (notif.isRead) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .clickable {
                                    viewModel.markNotificationRead(notif.id)
                                    notif.actorId?.let(onOpenProfile)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SkillectAvatar(
                                    avatarUrl = notif.actor?.avatarUrl,
                                    contentDescription = "Actor avatar",
                                    size = 42.dp,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val isNetworkNotification = notif.type.equals("follow", ignoreCase = true)
                                    Text(
                                        text = if (isNetworkNotification) "New network connection" else notif.title.ifBlank { notif.type.replaceFirstChar { it.uppercase() } },
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    val visibleBody = if (isNetworkNotification) "Connected with your Skillect profile." else notif.body
                                    if (visibleBody.isNotBlank()) {
                                        Text(
                                            text = visibleBody,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = AppConfig.formatRelativeTimestamp(notif.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
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
            }
        }
    }
}
