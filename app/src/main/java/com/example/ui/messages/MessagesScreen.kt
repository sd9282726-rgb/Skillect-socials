package com.example.ui.messages

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.config.AppConfig
import com.example.core.util.TimeCompat
import com.example.core.designsystem.EmptyStateCard
import com.example.core.designsystem.ErrorRetryCard
import com.example.core.designsystem.MinorSafeBadge
import com.example.core.designsystem.SkillectAvatar
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectGradientIconTile
import com.example.core.designsystem.SkillectOutlinedButton
import com.example.core.designsystem.SkillectPrimaryButton
import com.example.ui.feed.ReportContentDialog

@Composable
fun MessagesInboxScreen(
    uiState: MessagesNotificationsUiState,
    currentUserId: String,
    viewModel: MessagesNotificationsViewModel,
    onOpenConversation: (conversationId: String, peerName: String) -> Unit,
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkillectBrandBadge(size = 28.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Messages", style = MaterialTheme.typography.titleLarge)
                }
                MinorSafeBadge()
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (currentUserId.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("guest_messages_gate"),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SkillectGradientIconTile(icon = Icons.Rounded.Lock)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Sign in to message creators & peers",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your private messages stay between you and the people you choose. Safety settings apply to younger members.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        SkillectPrimaryButton(
                            text = "Create an account",
                            onClick = { onRequireAuth(true, "Create an account to send and receive private messages.") },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SkillectOutlinedButton(
                            text = "Sign in",
                            onClick = { onRequireAuth(false, "Sign in to access your private messages.") },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                return@Column
            }

            LaunchedEffect(currentUserId) {
                viewModel.refreshInbox()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (!AppConfig.isSupabaseConfigured) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("Messages are temporarily unavailable. Please try again shortly.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (uiState.incomingMessageRequests.isNotEmpty()) {
                    item {
                        Text(
                            text = "Message requests",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    items(uiState.incomingMessageRequests, key = { it.id }) { request ->
                        val sender = request.sender
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkillectAvatar(
                                    avatarUrl = sender?.avatarUrl,
                                    contentDescription = "Message request sender",
                                    size = 42.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sender?.let { it.displayName.ifBlank { it.username } } ?: "Skillect member", style = MaterialTheme.typography.titleSmall)
                                    Text("@${sender?.username ?: "member"} tried to contact you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (request.initialMessage.isNotBlank()) {
                                Text(request.initialMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 52.dp, top = 8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 52.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { viewModel.respondToMessageRequest(request.id, false) }) { Text("Reject") }
                                TextButton(onClick = { viewModel.respondToMessageRequest(request.id, true) }) { Text("Accept") }
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (uiState.errorMessage != null) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            ErrorRetryCard(
                                message = uiState.errorMessage,
                                onRetry = viewModel::refreshInbox,
                            )
                        }
                    }
                }

                if (uiState.conversations.isEmpty() && uiState.incomingMessageRequests.isEmpty() && !uiState.isLoading) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.ChatBubbleOutline,
                            title = "Real-Time Private Messaging",
                            subtitle = "Start a conversation, share ideas, and stay connected. Members aged 16–17 can message within their connected network.",
                        )
                    }
                } else {
                    items(uiState.conversations, key = { it.id }) { conv ->
                        val peer = conv.participants.firstOrNull { it.userId != currentUserId }?.profile
                        val peerName = peer?.displayName ?: "Direct Conversation"
                        val myReadMarker = conv.readMarkers.firstOrNull { it.userId == currentUserId }
                        val hasUnreadMessages = TimeCompat.isAfter(
                            conv.updatedAt,
                            myReadMarker?.lastReadAt ?: conv.createdAt,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.loadMessages(conv.id)
                                    onOpenConversation(conv.id, peerName)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SkillectAvatar(
                                    avatarUrl = peer?.avatarUrl,
                                    contentDescription = "$peerName avatar",
                                    size = 48.dp,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(peerName, style = MaterialTheme.typography.titleMedium)
                                        if (hasUnreadMessages) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(50),
                                            ) {
                                                Text(
                                                    text = "New",
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "@${peer?.username ?: "member"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
fun ChatDetailScreen(
    conversationId: String,
    peerName: String,
    currentUserId: String,
    uiState: MessagesNotificationsUiState,
    viewModel: MessagesNotificationsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var reportMessageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversationId) {
        viewModel.loadMessages(conversationId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(peerName.ifBlank { "Conversation" }, style = MaterialTheme.typography.titleLarge)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Conversation safety options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Report latest message") },
                            leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                reportMessageId = uiState.messages.lastOrNull()?.id ?: conversationId
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Block user") },
                            leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val peerId = uiState.conversations
                                    .firstOrNull { it.id == conversationId }
                                    ?.participants
                                    ?.firstOrNull { it.userId != currentUserId }
                                    ?.userId
                                if (peerId != null) viewModel.blockPeer(peerId)
                            },
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (!uiState.statusMessage.isNullOrBlank()) {
                Text(
                    text = uiState.statusMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.widthIn(max = 300.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (!msg.mediaUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = msg.mediaUrl,
                                        contentDescription = "Shared chat image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                if (msg.content.isNotBlank()) {
                                    Text(
                                        text = msg.content,
                                        color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    if (isMine) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.DoneAll,
                                            contentDescription = msg.deliveryStatus,
                                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Write a message...") },
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                )
                IconButton(
                    enabled = draft.isNotBlank() && !uiState.isSendingMessage,
                    onClick = {
                        viewModel.sendMessage(conversationId, draft) { sent ->
                            if (sent) draft = ""
                        }
                    },
                    modifier = Modifier.testTag("chat_send_button"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send message",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    reportMessageId?.let { targetId ->
        ReportContentDialog(
            title = "Report Message",
            onDismiss = { reportMessageId = null },
            onSubmit = { reason, details ->
                viewModel.reportConversationMessage(targetId, reason, details)
                reportMessageId = null
            },
        )
    }
}
