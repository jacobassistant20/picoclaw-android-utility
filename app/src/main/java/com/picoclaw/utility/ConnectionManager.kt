package com.picoclaw.utility

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * ConnectionManager manages WebSocket server for receiving remote commands.
 * Uses Accessibility Service for execution.
 */
object ConnectionManager {
    
    private const val TAG = "ConnectionManager"
    private const val DEFAULT_PORT = 8765
    private const val DEFAULT_STREAM_PORT = 8080
    
    private var webSocketServer: WebSocketServer? = null
    private var isRunning = false
    private var context: Context? = null
    
    private val clients = mutableSetOf<WebSocket>()
    
    // Screen stream related
    private var streamThread: Thread? = null
    private var isStreaming = false
    private val httpClient = OkHttpClient()
    private var streamPort = DEFAULT_STREAM_PORT
    
    /**
     * Initialize the WebSocket server
     */
    fun initialize(ctx: Context, port: Int = DEFAULT_PORT): Boolean {
        if (isRunning) {
            Log.d(TAG, "Connection already running")
            return true
        }
        
        context = ctx
        
        return try {
            webSocketServer = object : WebSocketServer(InetSocketAddress(port)) {
                override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
                    conn?.let { clients.add(it) }
                    Log.d(TAG, "Client connected: ${conn?.remoteSocketAddress}")
                    conn?.send(createResponse("connected", "PicoClaw service ready"))
                }
                
                override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
                    clients.remove(conn)
                    Log.d(TAG, "Client disconnected: $reason")
                }
                
                override fun onMessage(conn: WebSocket?, message: String?) {
                    Log.d(TAG, "Received: $message")
                    message?.let { handleCommand(it, conn) }
                }
                
                override fun onStart() {
                    Log.d(TAG, "WebSocket server started on port $port")
                }
                
                override fun onError(conn: WebSocket?, ex: Exception?) {
                    Log.e(TAG, "WebSocket error", ex)
                }
            }
            
            webSocketServer?.start()
            isRunning = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            false
        }
    }
    
    /**
     * Disconnect and stop server
     */
    fun disconnect() {
        isRunning = false
        isStreaming = false
        streamThread?.interrupt()
        streamThread = null
        clients.forEach { it.close() }
        clients.clear()
        
        webSocketServer?.stop()
        webSocketServer = null
        
        Log.d(TAG, "Server stopped")
    }
    
    /**
     * Handle incoming command JSON
     */
    private fun handleCommand(message: String, client: WebSocket?) {
        val service = PicoClawAccessibilityService.getInstance()
        if (service == null) {
            client?.send(createResponse("error", "Accessibility service not active"))
            return
        }
        
        try {
            val json = JSONObject(message)
            val command = json.optString("command", "unknown")
            val params = json.optJSONObject("params") ?: JSONObject()
            
            when (command) {
                "tap", "click" -> {
                    val x = params.getDouble("x").toFloat()
                    val y = params.getDouble("y").toFloat()
                    service.performClick(x, y) { success ->
                        broadcast(createResponse(command, if (success) "ok" else "failed", params))
                    }
                }
                
                "swipe" -> {
                    val x1 = params.getDouble("x1").toFloat()
                    val y1 = params.getDouble("y1").toFloat()
                    val x2 = params.getDouble("x2").toFloat()
                    val y2 = params.getDouble("y2").toFloat()
                    val duration = params.optLong("duration", 500)
                    service.performSwipe(x1, y1, x2, y2, duration) { success ->
                        broadcast(createResponse(command, if (success) "ok" else "failed", params))
                    }
                }
                
                "long_press" -> {
                    val x = params.getDouble("x").toFloat()
                    val y = params.getDouble("y").toFloat()
                    val duration = params.optLong("duration", 800)
                    service.performLongPress(x, y, duration) { success ->
                        broadcast(createResponse(command, if (success) "ok" else "failed", params))
                    }
                }
                
                "back" -> {
                    val success = service.performBack()
                    broadcast(createResponse(command, if (success) "ok" else "failed"))
                }
                
                "home" -> {
                    val success = service.performHome()
                    broadcast(createResponse(command, if (success) "ok" else "failed"))
                }
                
                "recents" -> {
                    val success = service.performRecents()
                    broadcast(createResponse(command, if (success) "ok" else "failed"))
                }
                
                "notifications" -> {
                    val success = service.performNotifications()
                    broadcast(createResponse(command, if (success) "ok" else "failed"))
                }
                
                "quick_settings" -> {
                    val success = service.performQuickSettings()
                    broadcast(createResponse(command, if (success) "ok" else "failed"))
                }
                
                "find_element" -> {
                    val text = params.optString("text", "")
                    val byId = params.optString("id", "")
                    
                    val node = if (text.isNotEmpty()) {
                        service.findNodeByText(text)
                    } else {
                        service.findNodeById(byId)
                    }
                    
                    val bounds = service.getElementBounds(node)
                    if (bounds != null) {
                        broadcast(createResponse("element_found", "ok", JSONObject().apply {
                            put("text", node?.text?.toString() ?: "")
                            put("id", node?.viewIdResourceName ?: "")
                            put("class", node?.className?.toString() ?: "")
                            put("bounds", JSONObject().apply {
                                put("left", bounds.left)
                                put("top", bounds.top)
                                put("right", bounds.right)
                                put("bottom", bounds.bottom)
                                put("center_x", bounds.centerX())
                                put("center_y", bounds.centerY())
                            })
                            put("clickable", node?.isClickable ?: false)
                        }))
                    } else {
                        broadcast(createResponse("element_not_found", "No element matching criteria"))
                    }
                }
                
                "click_element" -> {
                    val text = params.optString("text", "")
                    val byId = params.optString("id", "")
                    val node = if (text.isNotEmpty()) {
                        service.findNodeByText(text)
                    } else {
                        service.findNodeById(byId)
                    }
                    
                    val bounds = service.getElementBounds(node)
                    if (bounds != null) {
                        service.performClick(bounds.centerX().toFloat(), bounds.centerY().toFloat()) { success ->
                            broadcast(createResponse("click_element", if (success) "ok" else "failed"))
                        }
                    } else {
                        broadcast(createResponse("click_element", "element_not_found"))
                    }
                }
                
                "set_text" -> {
                    val text = params.optString("text", "")
                    val targetText = params.optString("target_text", "")
                    val targetId = params.optString("target_id", "")
                    
                    val node = if (targetText.isNotEmpty()) {
                        service.findNodeByText(targetText)
                    } else if (targetId.isNotEmpty()) {
                        service.findNodeById(targetId)
                    } else {
                        null
                    }
                    
                    val success = service.setNodeText(node, text)
                    broadcast(createResponse("set_text", if (success) "ok" else "failed"))
                }
                
                "get_ui_hierarchy" -> {
                    val hierarchy = service.getCurrentWindowHierarchy()
                    broadcast(createResponse("ui_hierarchy", "ok", JSONObject().apply {
                        put("hierarchy", hierarchy)
                    }))
                }
                
                "get_clickable_elements" -> {
                    val elements = service.getClickableElements()
                    broadcast(createResponse("clickable_elements", "ok", JSONObject().apply {
                        put("count", elements.size)
                        put("elements", org.json.JSONArray().apply {
                            elements.take(50).forEach { element ->
                                put(JSONObject().apply {
                                    put("text", element.text)
                                    put("id", element.viewId)
                                    put("class", element.className)
                                    put("clickable", element.isClickable)
                                    put("scrollable", element.isScrollable)
                                    put("center_x", element.getCenterX())
                                    put("center_y", element.getCenterY())
                                })
                            }
                        })
                    }))
                }
                
                "ping" -> {
                    broadcast(createResponse("pong", "alive"))
                }
                
                "get_dimensions" -> {
                    val (width, height) = service.getScreenDimensions()
                    broadcast(createResponse("dimensions", "ok", JSONObject().apply {
                        put("width", width)
                        put("height", height)
                    }))
                }
                
                // ========== SCREEN STREAM COMMANDS ==========
                
                "stream_capture" -> {
                    val port = params.optInt("port", DEFAULT_STREAM_PORT)
                    thread {
                        try {
                            val frame = fetchSingleFrame(port)
                            if (frame != null) {
                                val base64 = MjpegUtil.encodeToBase64(frame)
                                val dims = MjpegUtil.getImageDimensions(frame)
                                val (w, h) = dims ?: Pair(0, 0)
                                broadcast(createResponse("stream_capture", "ok", JSONObject().apply {
                                    put("image", base64)
                                    put("width", w)
                                    put("height", h)
                                }))
                            } else {
                                broadcast(createResponse("stream_capture", "failed", JSONObject().apply {
                                    put("error", "Could not fetch frame")
                                }))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "stream_capture error", e)
                            broadcast(createResponse("stream_capture", "error", JSONObject().apply {
                                put("error", e.message)
                            }))
                        }
                    }
                }
                
                "stream_start" -> {
                    val port = params.optInt("port", DEFAULT_STREAM_PORT)
                    if (isStreaming) {
                        broadcast(createResponse("stream_start", "already_streaming", JSONObject().apply {
                            put("port", streamPort)
                        }))
                        return@handleCommand
                    }
                    
                    streamPort = port
                    isStreaming = true
                    
                    broadcast(createResponse("stream_start", "ok", JSONObject().apply {
                        put("port", port)
                        put("url", "http://localhost:$port/stream.mjpeg")
                    }))
                    
                    // Start streaming thread
                    streamThread = thread {
                        startStreaming(port)
                    }
                }
                
                "stream_stop" -> {
                    if (!isStreaming) {
                        broadcast(createResponse("stream_stop", "not_streaming"))
                        return@handleCommand
                    }
                    
                    isStreaming = false
                    streamThread?.interrupt()
                    streamThread = null
                    
                    broadcast(createResponse("stream_stop", "ok"))
                }
                
                else -> {
                    broadcast(createResponse("error", "Unknown command: $command"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command", e)
            client?.send(createResponse("error", "Parse error: ${e.message}"))
        }
    }
    
    // ========== STREAMING HELPERS ==========
    
    private fun fetchSingleFrame(port: Int): ByteArray? {
        val url = "http://localhost:$port/stream.mjpeg"
        val request = Request.Builder().url(url).build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            
            val body = response.body ?: return null
            val buffer = ByteArray(1024 * 1024) // 1MB buffer
            val stream = body.byteStream()
            
            // Read enough data to find a frame
            var totalRead = 0
            var read: Int
            while (stream.read(buffer, totalRead, buffer.size - totalRead).also { read = it } != -1) {
                totalRead += read
                if (totalRead >= buffer.size - 1) break
                
                // Try to extract frame
                val frame = MjpegUtil.extractJpegFrame(buffer.copyOf(totalRead))
                if (frame != null) return frame
            }
            
            return MjpegUtil.extractJpegFrame(buffer.copyOf(totalRead))
        }
    }
    
    private fun startStreaming(port: Int) {
        val url = "http://localhost:$port/stream.mjpeg"
        var frameBuffer = ByteArray(0)
        
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    broadcast(createResponse("stream_error", "Failed to connect", JSONObject().apply {
                        put("error", "HTTP ${response.code}")
                    }))
                    return
                }
                
                val body = response.body ?: return
                val stream = body.byteStream()
                val buffer = ByteArray(256 * 1024) // 256KB buffer
                var totalRead = 0
                var read: Int
                
                while (isStreaming && !Thread.currentThread().isInterrupted) {
                    read = stream.read(buffer, totalRead, buffer.size - totalRead)
                    if (read == -1) break
                    
                    totalRead += read
                    
                    // Try to extract complete frame
                    val frame = MjpegUtil.extractJpegFrame(buffer.copyOf(totalRead))
                    if (frame != null) {
                        val base64 = MjpegUtil.encodeToBase64(frame)
                        broadcast(JSONObject().apply {
                            put("type", "frame")
                            put("image", base64)
                            put("timestamp", System.currentTimeMillis())
                        }.toString())
                        
                        // Reset buffer after frame
                        frameBuffer = buffer.copyOf(totalRead)
                        totalRead = 0
                    }
                    
                    // Expand buffer if needed
                    if (totalRead > buffer.size * 0.8) {
                        buffer.copyOf(totalRead)
                    }
                }
            }
        } catch (e: InterruptedException) {
            Log.d(TAG, "Streaming interrupted")
        } catch (e: Exception) {
            Log.e(TAG, "Streaming error", e)
            broadcast(createResponse("stream_error", e.message ?: "Unknown error"))
        } finally {
            isStreaming = false
        }
    }
    
    private fun createResponse(command: String, status: String, params: JSONObject = JSONObject()): String {
        return JSONObject().apply {
            put("command", command)
            put("status", status)
            put("params", params)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }
    
    private fun broadcast(message: String) {
        clients.forEach { client ->
            try {
                client.send(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to client", e)
            }
        }
    }
}