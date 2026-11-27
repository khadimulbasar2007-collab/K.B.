package com.basar.filebridge.host

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.basar.filebridge.R
import com.basar.filebridge.model.toJsonArray
import com.basar.filebridge.net.MessageType
import com.basar.filebridge.net.RelayClient
import com.basar.filebridge.net.registerHostMessage
import com.basar.filebridge.security.PasswordCrypto
import com.basar.filebridge.security.PasswordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class HostForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "filebridge_host"
        private const val NOTIFICATION_ID = 1
        private const val CHUNK_SIZE = 64 * 1024

        val isSharing = MutableStateFlow(false)
        val isClientConnected = MutableStateFlow(false)
        val registerFailed = MutableStateFlow(false)
    }

    private lateinit var relay: RelayClient
    private lateinit var password: String
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("চালু হচ্ছে…"))

        password = PasswordStore(applicationContext).get().orEmpty()
        if (password.isEmpty()) {
            stopSelf()
            return
        }

        relay = RelayClient()
        relay.connect()

        scope.launch {
            relay.messages.collect { msg -> handleMessage(msg) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (::relay.isInitialized) {
            relay.send(registerHostMessage(PasswordCrypto.roomKeyFor(password)))
        }
        return START_STICKY
    }

    private fun handleMessage(envelope: JSONObject) {
        when (envelope.optString("type")) {
            MessageType.HOST_REGISTERED -> {
                isSharing.value = true
                registerFailed.value = false
                updateNotification("পাসওয়ার্ড দিয়ে কানেক্ট হওয়ার অপেক্ষায়…")
            }
            MessageType.REGISTER_FAILED -> {
                isSharing.value = false
                registerFailed.value = true
            }
            MessageType.CLIENT_CONNECTED -> {
                isClientConnected.value = true
                updateNotification("একটা ফোন কানেক্টেড, ফাইল ব্রাউজ করছে")
            }
            MessageType.PEER_DISCONNECTED -> {
                isClientConnected.value = false
                updateNotification("পাসওয়ার্ড দিয়ে কানেক্ট হওয়ার অপেক্ষায়…")
            }
            "encrypted" -> {
                val inner = runCatching { PasswordCrypto.decrypt(password, envelope) }.getOrNull()
                    ?: return
                handleInner(inner)
            }
        }
    }

    private fun handleInner(msg: JSONObject) {
        when (msg.optString("type")) {
            MessageType.LIST_REQUEST -> {
                val path = msg.optString("path", "")
                val entries = FileRepository.list(path)
                val inner = JSONObject()
                    .put("type", MessageType.LIST_RESPONSE)
                    .put("path", path)
                    .put("entries", entries.toJsonArray())
                sendEncrypted(inner)
            }
            MessageType.DOWNLOAD_REQUEST -> {
                val requestId = msg.getString("requestId")
                val path = msg.getString("path")
                sendFileInChunks(requestId, path)
            }
        }
    }

    private fun sendEncrypted(inner: JSONObject) {
        relay.send(PasswordCrypto.encrypt(password, inner.toString()).toString())
    }

    private fun sendFileInChunks(requestId: String, path: String) {
        val bytes = FileRepository.readFileBytes(path)
        if (bytes == null) {
            sendEncrypted(
                JSONObject()
                    .put("type", MessageType.DOWNLOAD_ERROR)
                    .put("requestId", requestId)
                    .put("message", "File not found or not readable")
            )
            return
        }
        if (bytes.isEmpty()) {
            sendEncrypted(
                JSONObject()
                    .put("type", MessageType.DOWNLOAD_CHUNK)
                    .put("requestId", requestId)
                    .put("index", 0)
                    .put("total", 1)
                    .put("base64", "")
                    .put("isLast", true)
            )
            return
        }
        val totalChunks = (bytes.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        var index = 0
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + CHUNK_SIZE, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val isLast = end >= bytes.size
            sendEncrypted(
                JSONObject()
                    .put("type", MessageType.DOWNLOAD_CHUNK)
                    .put("requestId", requestId)
                    .put("index", index)
                    .put("total", totalChunks)
                    .put("base64", Base64.encodeToString(chunk, Base64.NO_WRAP))
                    .put("isLast", isLast)
            )
            offset = end
            index++
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "FileBridge Host", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FileBridge এই ফোনের ফাইল শেয়ার করছে")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        relay.disconnect()
        isSharing.value = false
        isClientConnected.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
