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
    val activeConversationId: String? = null,
    val messages: List<MessageDto> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
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
        refreshInbox()
        refreshNotifications()
        viewModelScope.launch {
            repository.realtimeClient.events.collect { event ->
                when (event) {
                    is RealtimeEvent.NewMessage -> {
                        if (event.conversationId == _uiState.value.activeConversationId) {
                            loadMessages(event.conversationId)
                        }
                        refreshInbox()
                    }
                    is RealtimeEvent.NewNotification -> refreshNotifications()
                    RealtimeEvent.FeedUpdated -> Unit
                }
            }
        }
    }

    fun refreshInbox() {
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            if (!repository.sessionStore.currentSession().isAuthenticated) {
                _uiState.update { it.copy(conversations = emptyList(), messages = emptyList(), isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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
        onComplete: (Boolean) -> Unit = {},
    ) {
        if ((content.isBlank() && mediaUrl.isNullOrBlank()) || _uiState.value.isSendingMessage) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingMessage = true, statusMessage = null) }
            repository.sendMessage(conversationId, content.trim(), mediaUrl).fold(
                onSuccess = { msg ->
                    _uiState.update { state ->
                        state.copy(messages = state.messages + msg, isSendingMessage = false)
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSendingMessage = false, statusMessage = err.message) }
                    onComplete(false)
                },
            )
        }
    }

    fun blockPeer(peerUserId: String) {
        viewModelScope.launch {
            repository.blockUser(peerUserId).fold(
                onSuccess = { _uiState.update { it.copy(statusMessage = "User blocked.") } },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun reportConversationMessage(targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            repository.submitReport("message", targetId, reason, details).fold(
                onSuccess = { _uiState.update { it.copy(statusMessage = "Message reported for safety review.") } },
                onFailure = { err -> _uiState.update { it.copy(statusMessage = err.message) } },
            )
        }
    }

    fun refreshNotifications() {
        if (!AppConfig.isSupabaseConfigured) return
        viewModelScope.launch {
            if (!repository.sessionStore.currentSession().isAuthenticated) {
                _uiState.update { it.copy(notifications = emptyList()) }
                return@launch
            }
            repository.fetchNotifications().fold(
                onSuccess = { list -> _uiState.update { it.copy(notifications = list) } },
                onFailure = { _ -> Unit },
            )
        }
    }

    fun clearPrivateState() {
        _uiState.value = MessagesNotificationsUiState()
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        notifications = state.notifications.map {
                            if (it.id == id) it.copy(isRead = true) else it
                        },
                    )
                }
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead().onSuccess {
                _uiState.update { state ->
                    state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
                }
            }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
