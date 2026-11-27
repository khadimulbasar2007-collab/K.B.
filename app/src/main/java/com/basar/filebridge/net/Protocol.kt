package com.basar.filebridge.net

import org.json.JSONObject

/**
 * Every message sent over the WebSocket is a small JSON object with a
 * "type" field. This file is the single place that defines what those
 * types mean, so both the Host side and the Client side agree.
 */
object MessageType {
    const val REGISTER_HOST = "register_host"
    const val HOST_REGISTERED = "host_registered"
    const val JOIN = "join"
    const val JOINED = "joined"
    const val JOIN_FAILED = "join_failed"
    const val REGISTER_FAILED = "register_failed"
    const val CLIENT_CONNECTED = "client_connected"
    const val PEER_DISCONNECTED = "peer_disconnected"

    const val LIST_REQUEST = "list_request"
    const val LIST_RESPONSE = "list_response"
    const val DOWNLOAD_REQUEST = "download_request"
    const val DOWNLOAD_CHUNK = "download_chunk"
    const val DOWNLOAD_ERROR = "download_error"
}

fun registerHostMessage(roomKey: String): String =
    JSONObject().put("type", MessageType.REGISTER_HOST).put("code", roomKey).toString()

fun joinMessage(roomKey: String): String =
    JSONObject().put("type", MessageType.JOIN).put("code", roomKey).toString()

fun listRequestMessage(path: String): String =
    JSONObject().put("type", MessageType.LIST_REQUEST).put("path", path).toString()

fun downloadRequestMessage(requestId: String, path: String): String =
    JSONObject()
        .put("type", MessageType.DOWNLOAD_REQUEST)
        .put("requestId", requestId)
        .put("path", path)
        .toString()
