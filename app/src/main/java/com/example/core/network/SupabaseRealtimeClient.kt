package com.example.core.network

import android.util.Log
import com.example.core.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

sealed class RealtimeEvent {
    data class NewMessage(val conversationId: String, val messageId: String) : RealtimeEvent()
    data class NewNotification(val recipientId: String) : RealtimeEvent()
    data object FeedUpdated : RealtimeEvent()
}

class SupabaseRealtimeClient(
    private val okHttpClient: OkHttpClient,
    private val sessionStore: SessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)

    fun connect(userId: String) {
        if (!AppConfig.isSupabaseConfigured || userId.isBlank()) return
        disconnect()

        scope.launch {
            val session = sessionStore.currentSession()
            val wsBase = AppConfig.supabaseUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
            val url = "$wsBase/realtime/v1/websocket?apikey=${AppConfig.supabaseAnonKey}&vsn=1.0.0"

            val request = Request.Builder().url(url).build()
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    val token = session.accessToken.ifBlank {
                        AppConfig.supabaseAnonKey.takeIf { !it.startsWith("sb_publishable_") }.orEmpty()
                    }
                    joinChannel(ws, "realtime:public:messages", token)
                    joinChannel(ws, "realtime:public:notifications", token)
                    joinChannel(ws, "realtime:public:posts", token)
                    startHeartbeat(ws)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleIncomingFrame(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w("SkillectRealtime", "WebSocket closed: ${t.message}")
                }
            })
        }
    }

    private fun joinChannel(ws: WebSocket, topic: String, accessToken: String) {
        val ref = refCounter.getAndIncrement().toString()
        val payload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put(
                "payload",
                JSONObject().apply {
                    put(
                        "config",
                        JSONObject().apply {
                            put(
                                "postgres_changes",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("event", "INSERT")
                                        put("schema", "public")
                                    },
                                ),
                            )
                        },
                    )
                    if (accessToken.isNotBlank()) {
                        put("access_token", accessToken)
                    }
                },
            )
            put("ref", ref)
        }
        ws.send(payload.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(25_000L)
                val ping = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                ws.send(ping.toString())
            }
        }
    }

    private fun handleIncomingFrame(raw: String) {
        try {
            val root = JSONObject(raw)
            val event = root.optString("event")
            if (event != "postgres_changes") return
            val payload = root.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return
            val table = data.optString("table")
            val record = data.optJSONObject("record") ?: return

            when (table) {
                "messages" -> {
                    val convId = record.optString("conversation_id")
                    val msgId = record.optString("id")
                    if (convId.isNotBlank()) {
                        _events.tryEmit(RealtimeEvent.NewMessage(convId, msgId))
                    }
                }
                "notifications" -> {
                    val recipientId = record.optString("user_id").ifBlank { record.optString("recipient_id") }
                    if (recipientId.isNotBlank()) {
                        _events.tryEmit(RealtimeEvent.NewNotification(recipientId))
                    }
                }
                "posts" -> _events.tryEmit(RealtimeEvent.FeedUpdated)
            }
        } catch (e: Exception) {
            Log.w("SkillectRealtime", "Malformed frame: ${e.message}")
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
    }
}
