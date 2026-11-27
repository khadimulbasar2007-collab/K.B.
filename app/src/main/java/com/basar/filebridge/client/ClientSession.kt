package com.basar.filebridge.client

import com.basar.filebridge.model.FileEntry
import com.basar.filebridge.model.toFileEntries
import com.basar.filebridge.net.MessageType
import com.basar.filebridge.net.RelayClient
import com.basar.filebridge.net.downloadRequestMessage
import com.basar.filebridge.net.joinMessage
import com.basar.filebridge.net.listRequestMessage
import com.basar.filebridge.security.PasswordCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

class ClientSession(private val password: String) {

    sealed class JoinState {
        object Idle : JoinState()
        object Connecting : JoinState()
        object Joined : JoinState()
        data class Failed(val reason: String) : JoinState()
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        data class InProgress(val received: Int, val total: Int) : DownloadState()
        data class Done(val bytes: ByteArray, val suggestedName: String) : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    val joinState = MutableStateFlow<JoinState>(JoinState.Idle)
    val currentPath = MutableStateFlow("")
    val currentEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)

    private val relay = RelayClient()
    private var activeRequestId: String? = null
    private val chunkBuffer = mutableMapOf<Int, ByteArray>()
    private var downloadFileName: String = ""

    fun start() {
        relay.connect()
    }

    suspend fun listenForever() {
        relay.messages.collect { msg -> handleMessage(msg) }
    }

    fun join() {
        joinState.value = JoinState.Connecting
        relay.send(joinMessage(PasswordCrypto.roomKeyFor(password)))
    }

    fun openFolder(path: String) {
        currentPath.value = path
        sendEncrypted(JSONObject().put("type", MessageType.LIST_REQUEST).put("path", path))
    }

    fun download(entry: FileEntry) {
        val requestId = UUID.randomUUID().toString()
        activeRequestId = requestId
        downloadFileName = entry.name
        chunkBuffer.clear()
        downloadState.value = DownloadState.InProgress(0, 1)
        sendEncrypted(
            JSONObject()
                .put("type", MessageType.DOWNLOAD_REQUEST)
                .put("requestId", requestId)
                .put("path", entry.path)
        )
    }

    private fun sendEncrypted(inner: JSONObject) {
        relay.send(PasswordCrypto.encrypt(password, inner.toString()).toString())
    }

    private fun handleMessage(envelope: JSONObject) {
        when (envelope.optString("type")) {
            MessageType.JOINED -> joinState.value = JoinState.Joined
            MessageType.JOIN_FAILED -> joinState.value =
                JoinState.Failed(envelope.optString("reason", "unknown"))
            "encrypted" -> {
                val inner = runCatching { PasswordCrypto.decrypt(password, envelope) }.getOrNull()
                    ?: return
                handleInner(inner)
            }
        }
    }

    private fun handleInner(msg: JSONObject) {
        when (msg.optString("type")) {
            MessageType.LIST_RESPONSE -> {
                val path = msg.optString("path", "")
                if (path == currentPath.value) {
                    currentEntries.value = msg.getJSONArray("entries").toFileEntries()
                }
            }
            MessageType.DOWNLOAD_CHUNK -> {
                val requestId = msg.getString("requestId")
                if (requestId != activeRequestId) return
                val index = msg.getInt("index")
                val total = msg.getInt("total")
                val base64 = msg.getString("base64")
                chunkBuffer[index] = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                downloadState.value = DownloadState.InProgress(chunkBuffer.size, total)
                if (msg.getBoolean("isLast")) {
                    val out = ByteArrayOutputStream()
                    for (i in 0 until chunkBuffer.size) {
                        chunkBuffer[i]?.let { out.write(it) }
                    }
                    downloadState.value = DownloadState.Done(out.toByteArray(), downloadFileName)
                }
            }
            MessageType.DOWNLOAD_ERROR -> {
                val requestId = msg.getString("requestId")
                if (requestId == activeRequestId) {
                    downloadState.value = DownloadState.Failed(msg.optString("message", "failed"))
                }
            }
        }
    }

    fun stop() {
        relay.disconnect()
    }
}
