package com.basar.filebridge.ui

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.basar.filebridge.client.ClientSession
import com.basar.filebridge.model.FileEntry
import com.basar.filebridge.security.PasswordStore
import kotlinx.coroutines.launch

private fun isImageFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

@Composable
fun ClientScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val passwordStore = remember { PasswordStore(context) }
    var savedPassword by remember { mutableStateOf(passwordStore.get()) }

    if (savedPassword == null) {
        EnterPasswordForm { typed ->
            passwordStore.set(typed)
            savedPassword = typed
        }
        return
    }

    ConnectedClient(
        password = savedPassword!!,
        onForgetPassword = {
            passwordStore.clear()
            savedPassword = null
        },
        onBack = onBack
    )
}

@Composable
private fun EnterPasswordForm(onConfirmed: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("এই ফোন থেকে অন্য ফোনের ফাইল দেখুন", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Host ফোনে যে পাসওয়ার্ডটা সেট করা হয়েছে, সেটা এখানে দিন।")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("পাসওয়ার্ড") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (password.isNotBlank()) onConfirmed(password) }) {
            Text("সংরক্ষণ করে কানেক্ট করুন")
        }
    }
}

@Composable
private fun ConnectedClient(password: String, onForgetPassword: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember(password) { ClientSession(password) }

    val joinState by session.joinState.collectAsState()
    val entries by session.currentEntries.collectAsState()
    val path by session.currentPath.collectAsState()
    val downloadState by session.downloadState.collectAsState()

    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var previewName by remember { mutableStateOf("") }

    LaunchedEffect(password) {
        session.start()
        scope.launch { session.listenForever() }
        session.join()
    }
    DisposableEffect(password) {
        onDispose { session.stop() }
    }

    LaunchedEffect(downloadState) {
        val state = downloadState
        if (state is ClientSession.DownloadState.Done) {
            saveToDownloads(context, state.suggestedName, state.bytes)
            if (isImageFile(state.suggestedName)) {
                val bmp = BitmapFactory.decodeByteArray(state.bytes, 0, state.bytes.size)
                if (bmp != null) {
                    previewBitmap = bmp
                    previewName = state.suggestedName
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("এই ফোন থেকে অন্য ফোনের ফাইল দেখুন", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        when (joinState) {
            is ClientSession.JoinState.Joined -> {
                LaunchedEffect(Unit) { session.openFolder("") }

                if (path.isNotEmpty()) {
                    TextButton(onClick = {
                        session.openFolder(path.substringBeforeLast("/", ""))
                    }) { Text("⬆ উপরে যান ( $path )") }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(entries) { entry ->
                        FileRow(entry, onOpen = {
                            if (entry.isDirectory) session.openFolder(entry.path)
                            else session.download(entry)
                        })
                    }
                }

                when (val d = downloadState) {
                    is ClientSession.DownloadState.InProgress ->
                        Text("ডাউনলোড হচ্ছে… ${d.received}/${d.total}")
                    is ClientSession.DownloadState.Done ->
                        Text("✅ সংরক্ষণ হয়েছে: ${d.suggestedName}")
                    is ClientSession.DownloadState.Failed ->
                        Text("❌ ডাউনলোড ব্যর্থ: ${d.message}")
                    else -> {}
                }
            }

            is ClientSession.JoinState.Connecting ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("কানেক্ট হচ্ছে…")
                }

            is ClientSession.JoinState.Failed ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("❌ কানেক্ট করা যায়নি।")
                    Text("হয় Host ফোনে শেয়ারিং চালু নেই, অথবা পাসওয়ার্ড ভুল।")
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onForgetPassword) { Text("পাসওয়ার্ড পরিবর্তন করুন") }
                }

            else -> {}
        }

        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = onForgetPassword) { Text("পাসওয়ার্ড পরিবর্তন") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("ফিরে যান") }
        }
    }

    previewBitmap?.let { bmp ->
        Dialog(onDismissRequest = { previewBitmap = null }) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = previewName,
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(
                    onClick = { previewBitmap = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name)
            if (!entry.isDirectory) {
                Text("${entry.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!entry.isDirectory) {
            Icon(Icons.Filled.Download, contentDescription = "Download")
        }
    }
}

private fun saveToDownloads(context: android.content.Context, fileName: String, bytes: ByteArray) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        java.io.File(dir, fileName).writeBytes(bytes)
    }
}
