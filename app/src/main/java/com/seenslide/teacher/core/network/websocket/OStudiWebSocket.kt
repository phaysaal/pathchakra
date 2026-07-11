package com.seenslide.teacher.core.network.websocket

import android.util.Log
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawTool
import com.seenslide.teacher.core.drawing.StrokePoint
import com.seenslide.teacher.core.drawing.toHexColor
import com.seenslide.teacher.core.drawing.toWireString
import com.seenslide.teacher.core.network.auth.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

@Singleton
class OStudiWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
) {
    private var webSocket: WebSocket? = null
    private var currentGroupId: String? = null
    private var intentionalDisconnect = false
    private var reconnectAttempt = 0
    private var reconnectTimer: Timer? = null

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(groupId: String) {
        if (webSocket != null && currentGroupId == groupId &&
            _connectionState.value == WsConnectionState.CONNECTED
        ) return

        intentionalDisconnect = false
        reconnectAttempt = 0
        cancelReconnectTimer()
        doConnect(groupId)
    }

    fun disconnect() {
        intentionalDisconnect = true
        cancelReconnectTimer()
        reconnectAttempt = 0
        webSocket?.close(1000, "leaving")
        webSocket = null
        currentGroupId = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    private fun doConnect(groupId: String) {
        webSocket?.close(1000, null)
        webSocket = null

        currentGroupId = groupId
        val token = tokenStore.getToken() ?: run {
            _connectionState.value = WsConnectionState.DISCONNECTED
            onError?.invoke("No auth token")
            return
        }

        _connectionState.value = if (reconnectAttempt == 0) {
            WsConnectionState.CONNECTING
        } else {
            WsConnectionState.RECONNECTING
        }

        val baseUrl = BuildConfig.API_BASE_URL
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')

        val url = "$baseUrl/ws/groups/$groupId?token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected to group $groupId")
                reconnectAttempt = 0
                _connectionState.value = WsConnectionState.CONNECTED
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.v(TAG, "WS received: ${text.take(200)}")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                handleDisconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error (attempt $reconnectAttempt)", t)
                webSocket = null
                handleDisconnect()
                onError?.invoke(t.message ?: "Connection failed")
            }
        })
    }

    private fun handleDisconnect() {
        webSocket = null
        if (intentionalDisconnect) {
            _connectionState.value = WsConnectionState.DISCONNECTED
            onDisconnected?.invoke()
            return
        }

        // Auto-reconnect with exponential backoff
        val groupId = currentGroupId ?: return
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached, giving up")
            _connectionState.value = WsConnectionState.DISCONNECTED
            onDisconnected?.invoke()
            return
        }

        _connectionState.value = WsConnectionState.RECONNECTING
        val delayMs = calculateBackoff(reconnectAttempt)
        reconnectAttempt++
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${delayMs}ms")

        reconnectTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (!intentionalDisconnect && currentGroupId == groupId) {
                        doConnect(groupId)
                    }
                }
            }, delayMs)
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, capped at 30s
        val baseMs = 1000L
        val maxMs = 30_000L
        val delay = baseMs * (1L shl attempt.coerceAtMost(5))
        return delay.coerceAtMost(maxMs)
    }

    private fun cancelReconnectTimer() {
        reconnectTimer?.cancel()
        reconnectTimer = null
    }

    /**
     * Send stroke_start — notifies viewers that teacher is beginning to draw.
     */
    fun sendStrokeStart(slideId: String, tool: DrawTool, color: Long, width: Float) {
        val msg = JSONObject().apply {
            put("type", "stroke_start")
            put("slide_id", slideId)
            put("color", color.toHexColor())
            put("width", width.toDouble())
            put("tool", tool.toWireString())
        }
        send(msg)
    }

    /**
     * Send stroke_points — batched points during drawing for live preview.
     */
    fun sendStrokePoints(points: List<StrokePoint>) {
        if (points.isEmpty()) return
        val arr = JSONArray()
        for (p in points) {
            arr.put(JSONArray().apply {
                put(p.x.toDouble())
                put(p.y.toDouble())
                put(p.pressure.toDouble())
            })
        }
        val msg = JSONObject().apply {
            put("type", "stroke_points")
            put("points", arr)
        }
        send(msg)
    }

    /**
     * Send stroke_end — complete stroke with all points for persistence.
     * This matches the existing web viewer protocol exactly.
     */
    fun sendStrokeEnd(element: DrawElement, slideId: String) {
        when (element) {
            is DrawElement.FreehandElement -> {
                val stroke = element.stroke
                val pointsArr = JSONArray()
                for (p in stroke.points) {
                    pointsArr.put(JSONArray().apply {
                        put(p.x.toDouble())
                        put(p.y.toDouble())
                        put(p.pressure.toDouble())
                    })
                }
                val msg = JSONObject().apply {
                    put("type", "stroke_end")
                    put("slide_id", slideId)
                    put("stroke_data", pointsArr)
                    put("tool", stroke.tool.toWireString())
                    put("color", stroke.color.toHexColor())
                    put("width", stroke.width.toDouble())
                    put("ephemeral", false)
                }
                send(msg)
            }
            is DrawElement.ShapeElement -> {
                val shape = element.shape
                val pointsArr = JSONArray()
                pointsArr.put(JSONArray().apply {
                    put(shape.startX.toDouble())
                    put(shape.startY.toDouble())
                    put(0.5)
                })
                pointsArr.put(JSONArray().apply {
                    put(shape.endX.toDouble())
                    put(shape.endY.toDouble())
                    put(0.5)
                })
                val msg = JSONObject().apply {
                    put("type", "stroke_end")
                    put("slide_id", slideId)
                    put("stroke_data", pointsArr)
                    put("tool", "pen")
                    put("color", shape.color.toHexColor())
                    put("width", shape.width.toDouble())
                    put("ephemeral", false)
                    put("shape_type", shape.tool.name.lowercase())
                }
                send(msg)
            }
            is DrawElement.TextElement -> {
                val t = element.textStroke
                val msg = JSONObject().apply {
                    put("type", "stroke_end")
                    put("slide_id", slideId)
                    put("tool", "text")
                    put("color", t.color.toHexColor())
                    put("text", t.text)
                    put("x", t.x.toDouble())
                    put("y", t.y.toDouble())
                    put("font_size", t.fontSize.toDouble())
                    put("ephemeral", false)
                }
                send(msg)
            }
        }
    }

    fun sendStrokeDelete(strokeId: String) {
        val msg = JSONObject().apply {
            put("type", "stroke_delete")
            put("stroke_id", strokeId)
        }
        send(msg)
    }

    /**
     * Send current slide number so viewers stay in sync.
     */
    fun sendSlideChange(slideNumber: Int) {
        val msg = JSONObject().apply {
            put("type", "slide_change")
            put("slide_number", slideNumber)
        }
        send(msg)
    }

    val isConnected: Boolean get() = _connectionState.value == WsConnectionState.CONNECTED

    private fun send(json: JSONObject) {
        val text = json.toString()
        val sent = webSocket?.send(text) ?: false
        if (!sent) {
            Log.w(TAG, "WebSocket send failed (not connected)")
        }
    }

    companion object {
        private const val TAG = "OStudiWS"
        private const val MAX_RECONNECT_ATTEMPTS = 8
    }
}
