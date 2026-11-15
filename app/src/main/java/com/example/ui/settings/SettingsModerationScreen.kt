package com.example.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.config.AppConfig
import com.example.core.config.ThemeMode
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.SkillChip
import com.example.core.designsystem.SkillectCard
import com.example.core.designsystem.SkillectCrimson
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.ui.profile.ProfileSettingsUiState
import com.example.ui.profile.ProfileSettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: ProfileSettingsUiState,
    currentUserId: String,
    viewModel: ProfileSettingsViewModel,
    onOpenAdminModeration: () -> Unit,
    onBack: () -> Unit,
    onRequireAuth: (createAccount: Boolean, reason: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val isGuest = currentUserId.isBlank()
    var showGuidelinesDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val prefs = uiState.preferences
    val myProfile = uiState.myProfile

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Text("Settings, Privacy & Safety", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Theme Preferences (Dark-First + Light Mode support)
            item {
                SkillectCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            SkillChip(
                                label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                modifier = Modifier.testTag("theme_${mode.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // International Language Preferences
            item {
                SkillectCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Language & International Formatting", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppConfig.supportedLanguages.forEach { (code, name) ->
                            SkillChip(
                                label = name,
                                selected = prefs.languageCode == code,
                                onClick = { viewModel.setLanguageCode(code) },
                            )
                        }
                    }
                }
            }

            if (isGuest) {
                item {
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Account, Privacy & Safety", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sign in or create an account to manage profile privacy, direct message permissions, blocked accounts, and notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkillectPrimaryButton(
                                text = "Create Account",
                                onClick = { onRequireAuth(true, "Create an account to manage privacy and safety controls.") },
                                modifier = Modifier.weight(1f),
                            )
                            SkillectOutlinedButton(
                                text = "Sign In",
                                onClick = { onRequireAuth(false, "Sign in to access your account settings.") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item {
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Safety Policy & Guidelines", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        SkillectOutlinedButton(
                            text = "Community Guidelines",
                            onClick = { showGuidelinesDialog = true },
                        )
                    }
                }
            } else {
                // Privacy & 16+ Safety Controls
                item {
                    var isPrivate by remember(myProfile) { mutableStateOf(myProfile?.isPrivate ?: false) }
                    var allowDms by remember(myProfile) { mutableStateOf(myProfile?.allowDmsFrom ?: "following_only") }

                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Privacy & 16+ Safety Controls", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Private Account", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Only approved network connections see private updates.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isPrivate,
                                onCheckedChange = {
                                    isPrivate = it
                                    viewModel.updatePrivacySettings(it, allowDms)
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Direct Messaging Permissions", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "everyone" to "Everyone",
                                "following_only" to "Following Only",
                                "nobody" to "Nobody",
                            ).forEach { (key, label) ->
                                SkillChip(
                                    label = label,
                                    selected = allowDms == key,
                                    onClick = {
                                        allowDms = key
                                        viewModel.updatePrivacySettings(isPrivate, key)
                                    },
                                )
                            }
                        }
                    }
                }

                // Notification Preferences & Android 13+ Runtime Permission
                item {
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Push & In-App Notifications", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SkillectOutlinedButton(
                                text = "Enable System Push Permission",
                                onClick = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        NotificationToggleRow("Likes & Reposts", prefs.pushLikes) {
                            viewModel.updateNotificationPrefs(it, prefs.pushComments, prefs.pushMessages, prefs.pushCommunities)
                        }
                        NotificationToggleRow("Comments & Replies", prefs.pushComments) {
                            viewModel.updateNotificationPrefs(prefs.pushLikes, it, prefs.pushMessages, prefs.pushCommunities)
                        }
                        NotificationToggleRow("Direct Messages", prefs.pushMessages) {
                            viewModel.updateNotificationPrefs(prefs.pushLikes, prefs.pushComments, it, prefs.pushCommunities)
                        }
                        NotificationToggleRow("Communities & Projects", prefs.pushCommunities) {
                            viewModel.updateNotificationPrefs(prefs.pushLikes, prefs.pushComments, prefs.pushMessages, it)
                        }
                    }
                }

                // Blocked Accounts
                item {
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Blocked Accounts (${uiState.blockedAccounts.size})", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (uiState.blockedAccounts.isEmpty()) {
                            Text(
                                "No blocked accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            uiState.blockedAccounts.forEach { block ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("@${block.blocked?.username ?: block.blockedId}")
                                    TextButton(onClick = { viewModel.unblockUser(block.blockedId) }) {
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }

                // Community Guidelines, Safety & Server-Gated Admin Moderation Dashboard
                item {
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Safety Policy, Terms & Governance", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkillectOutlinedButton(
                                text = "Community Guidelines",
                                onClick = { showGuidelinesDialog = true },
                            )
                            SkillectOutlinedButton(
                                text = "Moderation Hub",
                                leadingIcon = Icons.Rounded.AdminPanelSettings,
                                onClick = onOpenAdminModeration,
                                modifier = Modifier.testTag("open_admin_moderation_button"),
                            )
                        }
                    }
                }

                // Logout & Account Deletion
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SkillectOutlinedButton(
                            text = "Sign Out",
                            leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                            onClick = {
                                viewModel.signOut()
                                onBack()
                            },
                            modifier = Modifier.weight(1f).testTag("sign_out_button"),
                        )
                        SkillectOutlinedButton(
                            text = "Delete Account",
                            leadingIcon = Icons.Rounded.DeleteForever,
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showGuidelinesDialog) {
        AlertDialog(
            onDismissRequest = { showGuidelinesDialog = false },
            title = { Text("Skillect Safety & Community Guidelines (16+)") },
            text = {
                Text(
                    "Skillect enforces zero tolerance for CSAM, sexual exploitation, grooming, harassment, bullying, threats, hate speech, explicit sexual content, scams, spam, impersonation, and malicious links. Moderation decisions are backed by server-side audit logs and human review appeals.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                SkillectPrimaryButton(text = "Understood", onClick = { showGuidelinesDialog = false })
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Request Account Deletion?") },
            text = {
                Text("This permanently schedules deletion of your Supabase account, profile, and personal records.")
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Confirm Delete",
                    onClick = {
                        viewModel.deleteAccount()
                        showDeleteConfirm = false
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NotificationToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ModerationAdminScreen(
    uiState: ProfileSettingsUiState,
    viewModel: ProfileSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        viewModel.loadModerationDashboard()
    }

    var showAppealDialog by remember { mutableStateOf(false) }
    val role = uiState.myProfile?.role ?: "member"
    val isAuthorizedMod = role == "admin" || role == "moderator"

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Text("Moderation & Governance", style = MaterialTheme.typography.titleLarge)
                    }
                    SkillectOutlinedButton(
                        text = "Submit Appeal",
                        onClick = { showAppealDialog = true },
                    )
                }
            }

            if (!isAuthorizedMod) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Gavel,
                        title = "Server-Side Role Authorization Required",
                        subtitle = "Your current Supabase role is '$role'. Administrative moderation queues, content removal, and suspension actions are strictly gated by PostgreSQL Row Level Security (is_admin_or_mod()).",
                    )
                }
            }

            if (uiState.moderationReports.isNotEmpty()) {
                item { Text("Reports Queue", style = MaterialTheme.typography.titleMedium) }
                items(uiState.moderationReports, key = { it.id }) { report ->
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${report.targetType.uppercase()} · ${report.reason}",
                            style = MaterialTheme.typography.titleSmall,
                            color = SkillectCrimson,
                        )
                        Text(
                            text = "Status: ${report.status} · ${AppConfig.formatRelativeTimestamp(report.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (report.details.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(report.details, style = MaterialTheme.typography.bodySmall)
                        }
                        if (isAuthorizedMod && report.status == "pending") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkillectPrimaryButton(
                                    text = "Take Action",
                                    onClick = { viewModel.resolveReport(report.id, "action_taken") },
                                )
                                SkillectOutlinedButton(
                                    text = "Dismiss",
                                    onClick = { viewModel.resolveReport(report.id, "dismissed") },
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.moderationAppeals.isNotEmpty()) {
                item { Text("Moderation Appeals", style = MaterialTheme.typography.titleMedium) }
                items(uiState.moderationAppeals, key = { "appeal_${it.id}" }) { appeal ->
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Appeal (${appeal.status})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(appeal.reason, style = MaterialTheme.typography.bodySmall)
                        if (!appeal.reviewerNote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reviewer note: ${appeal.reviewerNote}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (uiState.auditLogs.isNotEmpty()) {
                item { Text("Moderation Audit Logs", style = MaterialTheme.typography.titleMedium) }
                items(uiState.auditLogs, key = { "audit_${it.id}" }) { log ->
                    SkillectCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${log.action.uppercase()} · ${log.targetType}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (log.notes.isNotBlank()) {
                            Text(log.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = AppConfig.formatRelativeTimestamp(log.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showAppealDialog) {
        var appealReason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppealDialog = false },
            title = { Text("Submit Moderation Appeal") },
            text = {
                OutlinedTextField(
                    value = appealReason,
                    onValueChange = { appealReason = it },
                    label = { Text("Explain why a decision should be reviewed") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                SkillectPrimaryButton(
                    text = "Submit",
                    enabled = appealReason.trim().length >= 5,
                    onClick = {
                        viewModel.submitModerationAppeal(appealReason)
                        showAppealDialog = false
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showAppealDialog = false }) { Text("Cancel") }
            },
        )
    }
}
