package com.example.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.network.ConversationDto
import com.example.core.network.MessageDto
import com.example.core.network.MessageRequestDto
import com.example.core.network.NotificationDto
import com.example.core.network.RealtimeEvent
import com.example.data.repository.SkillectRepository
import com.example.data.repository.markConversationRead
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessagesNotificationsUiState(
    val conversations: List<ConversationDto> = emptyList(),
    val incomingMessageRequests: List<MessageRequestDto> = emptyList(),
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
    private val messageCache = mutableMapOf<String, List<MessageDto>>()
    private val messageLoadsInFlight = mutableSetOf<String>()
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
                        is RealtimeEvent.NewNotification -> { refreshNotifications(); refreshInbox() }
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
        _uiState.update { it.copy(isLoading = it.conversations.isEmpty(), errorMessage = null) }
        viewModelScope.launch {
            val requestsTask = async { repository.fetchIncomingMessageRequests() }
            val conversationsTask = async { repository.fetchConversations() }
            val requests = requestsTask.await().getOrDefault(emptyList())
            conversationsTask.await().fold(
                onSuccess = { list -> _uiState.update { it.copy(conversations = list, incomingMessageRequests = requests, isLoading = false) } },
                onFailure = { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message, incomingMessageRequests = requests) } },
            )
        }
    }

    fun loadMessages(conversationId: String) {
        val cached = messageCache[conversationId]
        _uiState.update { state ->
            state.copy(
                activeConversationId = conversationId,
                messages = cached ?: if (state.activeConversationId == conversationId) state.messages else emptyList(),
                statusMessage = null,
                isSendingMessage = false,
                errorMessage = null,
            )
        }
        if (!AppConfig.isSupabaseConfigured || !messageLoadsInFlight.add(conversationId)) return
        viewModelScope.launch {
            val uid = repository.sessionStore.currentSession().userId
            val optimisticReadAt = com.example.core.util.TimeCompat.nowUtcIso()
            _uiState.update { state ->
                state.copy(
                    conversations = state.conversations.map { conv ->
                        if (conv.id != conversationId) conv
                        else conv.copy(
                            readMarkers = conv.readMarkers.filterNot { it.userId == uid } + com.example.core.network.ConversationReadDto(conversationId, uid, optimisticReadAt),
                        )
                    },
                )
            }
            val messagesTask = async { repository.fetchMessages(conversationId) }
            val readTask = async { repository.markConversationRead(conversationId) }
            messagesTask.await().fold(
                onSuccess = { list ->
                    messageCache[conversationId] = list
                    if (_uiState.value.activeConversationId == conversationId) {
                        _uiState.update { it.copy(messages = list, errorMessage = null) }
                    }
                },
                onFailure = { err ->
                    if (_uiState.value.activeConversationId == conversationId) {
                        _uiState.update { it.copy(errorMessage = err.message) }
                    }
                },
            )
            readTask.await().fold(
                onSuccess = { persistedReadAt ->
                    _uiState.update { state ->
                        state.copy(
                            conversations = state.conversations.map { conv ->
                                if (conv.id != conversationId) conv
                                else conv.copy(
                                    readMarkers = conv.readMarkers.filterNot { it.userId == uid } + com.example.core.network.ConversationReadDto(conversationId, uid, persistedReadAt),
                                )
                            },
                        )
                    }
                },
                onFailure = { err ->
                    if (_uiState.value.activeConversationId == conversationId) {
                        _uiState.update { it.copy(statusMessage = err.message) }
                    }
                },
            )
            messageLoadsInFlight.remove(conversationId)
        }
    }

    fun respondToMessageRequest(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToMessageRequest(requestId, accept).fold(
                onSuccess = { conversationId ->
                    _uiState.update { state ->
                        state.copy(
                            incomingMessageRequests = state.incomingMessageRequests.filterNot { it.id == requestId },
                            statusMessage = if (accept) "Message request accepted." else "Message request declined.",
                        )
                    }
                    if (accept && !conversationId.isNullOrBlank()) loadMessages(conversationId)
                    refreshInbox()
                },
                onFailure = { error -> _uiState.update { it.copy(statusMessage = error.message ?: "Could not process this request.") } },
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
                    val cachedMessages = messageCache[conversationId].orEmpty()
                    val updatedMessages = if (_uiState.value.activeConversationId == conversationId) {
                        _uiState.value.messages + msg
                    } else {
                        cachedMessages + msg
                    }
                    messageCache[conversationId] = updatedMessages
                    _uiState.update { state ->
                        state.copy(
                            messages = if (state.activeConversationId == conversationId) updatedMessages else state.messages,
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
        messageCache.clear()
        messageLoadsInFlight.clear()
        _uiState.update {
            it.copy(
                conversations = emptyList(),
                incomingMessageRequests = emptyList(),
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
