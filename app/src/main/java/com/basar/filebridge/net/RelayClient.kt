package com.basar.filebridge.net

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around an OkHttp WebSocket connection to the relay server.
 */
class RelayClient {

    companion object {
        const val RELAY_URL = "wss://k-b-qk4c.onrender.com"
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _messages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val messages = _messages.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState = _connectionState.asSharedFlow()

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

    fun connect() {
        _connectionState.tryEmit(ConnectionState.CONNECTING)
        val request = Request.Builder().url(RELAY_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.tryEmit(ConnectionState.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text) }.getOrNull()?.let {
                    _messages.tryEmit(it)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            }
        })
    }

    fun send(json: String) {
        webSocket?.send(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}
