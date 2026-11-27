package com.basar.filebridge.model

import org.json.JSONArray
import org.json.JSONObject

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long
)

fun FileEntry.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("path", path)
    .put("isDirectory", isDirectory)
    .put("sizeBytes", sizeBytes)
    .put("modifiedAt", modifiedAt)

fun List<FileEntry>.toJsonArray(): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr
}

fun JSONArray.toFileEntries(): List<FileEntry> {
    val list = mutableListOf<FileEntry>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        list.add(
            FileEntry(
                name = o.getString("name"),
                path = o.getString("path"),
                isDirectory = o.getBoolean("isDirectory"),
                sizeBytes = o.getLong("sizeBytes"),
                modifiedAt = o.getLong("modifiedAt")
            )
        )
    }
    return list
}
