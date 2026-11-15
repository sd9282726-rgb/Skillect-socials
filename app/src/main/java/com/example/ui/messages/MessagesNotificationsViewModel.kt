package com.example.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.network.ConversationDto
import com.example.core.network.MessageDto
import com.example.core.network.NotificationDto
import com.example.core.network.RealtimeEvent
import com.example.data.repository.SkillectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessagesNotificationsUiState(
    val conversations: List<ConversationDto> = emptyList(),
    val messages: List<MessageDto> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    val activeConversationId: String? = null,
    val isLoading: Boolean = false,
    val isSendingMessage: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) {
    val unreadNotificationsCount: Int get() = notifications.count { !it.isRead }
}

class MessagesNotificationsViewModel(
    private val repository: SkillectRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessagesNotificationsUiState())
    val uiState: StateFlow<MessagesNotificationsUiState> = _uiState.asStateFlow()

    init {
        if (AppConfig.isSupabaseConfigured) {
            refreshInbox()
            refreshNotifications()
            viewModelScope.launch {
                repository.realtimeClient.events.collect { event ->
                    when (event) {
                        is RealtimeEvent.NewMessage -> {
                            if (event.conversationId == _uiState.value.activeConversationId) {
                                loadMessages(event.conversationId)
                            } else {
                                refreshInbox()
                            }
                        }
                        is RealtimeEvent.NewNotification -> refreshNotifications()
                        else -> Unit
                    }
                }
            }
        }
    }

    fun refreshInbox() {
        if (!AppConfig.isSupabaseConfigured) {
            _uiState.update { it.copy(conversations = emptyList(), messages = emptyList(), isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.fetchConversations().fold(
                onSuccess = { list -> _uiState.update { it.copy(conversations = list, isLoading = false) } },
                onFailure = { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } },
            )
        }
    }

    fun loadMessages(conversationId: String) {
        _uiState.update { it.copy(activeConversationId = conversationId, statusMessage = null, isSendingMessage = false) }
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            // Optimistic local unread clear. Persist when repo API + migration 0007 are on remote.
            val now = java.time.Instant.now().toString()
            val uid = repository.sessionStore.currentSession().userId
            _uiState.update { state ->
                state.copy(
                    conversations = state.conversations.map { conv ->
                        if (conv.id != conversationId) conv
                        else conv.copy(
                            participants = conv.participants.map { part ->
                                if (part.userId == uid) part.copy(lastReadAt = now) else part
                            },
                        )
                    },
                )
            }
            repository.fetchMessages(conversationId).fold(
                onSuccess = { list -> _uiState.update { it.copy(messages = list) } },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } },
            )
        }
    }

    fun sendMessage(
        conversationId: String,
        content: String,
        mediaUrl: String? = null,
        onSent: (Boolean) -> Unit = {},
    ) {
        if (content.isBlank()) return
        _uiState.update { it.copy(isSendingMessage = true, statusMessage = null) }
        viewModelScope.launch {
            repository.sendMessage(conversationId, content.trim(), mediaUrl).fold(
                onSuccess = { msg ->
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + msg,
                            isSendingMessage = false,
                        )
                    }
                    onSent(true)
                    refreshInbox()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSendingMessage = false, statusMessage = err.message) }
                    onSent(false)
                },
            )
        }
    }

    fun blockPeer(peerUserId: String) {
        viewModelScope.launch {
            repository.blockUser(peerUserId).fold(
                onSuccess = { _uiState.update { it.copy(statusMessage = "Member blocked.") } },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun reportConversationMessage(targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            repository.submitReport("message", targetId, reason, details).fold(
                onSuccess = { _uiState.update { it.copy(statusMessage = "Report submitted.") } },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun refreshNotifications() {
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            repository.fetchNotifications().fold(
                onSuccess = { list -> _uiState.update { it.copy(notifications = list) } },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } },
            )
        }
    }

    fun clearPrivateState() {
        _uiState.update {
            it.copy(
                conversations = emptyList(),
                messages = emptyList(),
                notifications = emptyList(),
                activeConversationId = null,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            notifications = state.notifications.map {
                                if (it.id == id) it.copy(isRead = true) else it
                            },
                        )
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead().fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
                    }
                },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
