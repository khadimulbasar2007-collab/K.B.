package com.basar.filebridge.host

import android.os.Environment
import com.basar.filebridge.model.FileEntry
import java.io.File

object FileRepository {

    private val root: File get() = Environment.getExternalStorageDirectory()

    fun list(relativePath: String): List<FileEntry> {
        val dir = File(root, relativePath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                FileEntry(
                    name = f.name,
                    path = if (relativePath.isEmpty()) f.name else "$relativePath/${f.name}",
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isDirectory) 0 else f.length(),
                    modifiedAt = f.lastModified()
                )
            }
    }

    fun readFileBytes(relativePath: String): ByteArray? {
        val f = File(root, relativePath)
        if (!f.exists() || f.isDirectory || !f.canRead()) return null
        return f.readBytes()
    }
}
