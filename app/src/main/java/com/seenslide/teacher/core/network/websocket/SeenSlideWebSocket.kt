package com.seenslide.teacher.core.network.websocket

import android.util.Log
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawTool
import com.seenslide.teacher.core.drawing.StrokePoint
import com.seenslide.teacher.core.drawing.toHexColor
import com.seenslide.teacher.core.drawing.toWireString
import com.seenslide.teacher.core.network.auth.TokenStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeenSlideWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
) {
    private var webSocket: WebSocket? = null
    private var currentGroupId: String? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(groupId: String) {
        if (webSocket != null && currentGroupId == groupId) return
        disconnect()

        currentGroupId = groupId
        val token = tokenStore.getToken() ?: return

        val baseUrl = BuildConfig.API_BASE_URL
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')

        val url = "$baseUrl/ws/groups/$groupId?token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected to group $groupId")
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // We primarily send; receiving is for future bidirectional features
                Log.v(TAG, "WS received: ${text.take(200)}")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                onDisconnected?.invoke()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                onError?.invoke(t.message ?: "Connection failed")
                webSocket = null
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "leaving")
        webSocket = null
        currentGroupId = null
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
            arr.put(JSONObject().apply {
                put("x", p.x.toDouble())
                put("y", p.y.toDouble())
                put("pressure", p.pressure.toDouble())
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
                    pointsArr.put(JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("pressure", p.pressure.toDouble())
                    })
                }
                val msg = JSONObject().apply {
                    put("type", "stroke_end")
                    put("slide_id", slideId)
                    put("points", pointsArr)
                    put("tool", stroke.tool.toWireString())
                    put("color", stroke.color.toHexColor())
                    put("width", stroke.width.toDouble())
                    put("ephemeral", false)
                }
                send(msg)
            }
            is DrawElement.ShapeElement -> {
                // Convert shape to a stroke with start/end points for wire compatibility
                val shape = element.shape
                val pointsArr = JSONArray()
                pointsArr.put(JSONObject().apply {
                    put("x", shape.startX.toDouble())
                    put("y", shape.startY.toDouble())
                    put("pressure", 0.5)
                })
                pointsArr.put(JSONObject().apply {
                    put("x", shape.endX.toDouble())
                    put("y", shape.endY.toDouble())
                    put("pressure", 0.5)
                })
                val msg = JSONObject().apply {
                    put("type", "stroke_end")
                    put("slide_id", slideId)
                    put("points", pointsArr)
                    put("tool", "pen") // shapes sent as pen for now
                    put("color", shape.color.toHexColor())
                    put("width", shape.width.toDouble())
                    put("ephemeral", false)
                    // Custom field for shape type — viewer can interpret
                    put("shape_type", shape.tool.name.lowercase())
                }
                send(msg)
            }
            is DrawElement.TextElement -> {
                // Text as a special annotation
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

    val isConnected: Boolean get() = webSocket != null

    private fun send(json: JSONObject) {
        val text = json.toString()
        val sent = webSocket?.send(text) ?: false
        if (!sent) {
            Log.w(TAG, "WebSocket send failed (not connected)")
        }
    }

    companion object {
        private const val TAG = "SeenSlideWS"
    }
}
