package com.basar.filebridge.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.basar.filebridge.host.HostForegroundService
import com.basar.filebridge.security.PasswordStore

@Composable
fun HostScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val passwordStore = remember { PasswordStore(context) }

    var hasStoragePermission by remember { mutableStateOf(hasAllFilesPermission()) }
    var savedPassword by remember { mutableStateOf(passwordStore.get()) }
    val serviceStarted = remember { mutableStateOf(false) }

    val permissionSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = hasAllFilesPermission()
    }

    val isSharing by HostForegroundService.isSharing.collectAsState()
    val clientConnected by HostForegroundService.isClientConnected.collectAsState()
    val registerFailed by HostForegroundService.registerFailed.collectAsState()

    fun startSharing() {
        if (!serviceStarted.value) {
            serviceStarted.value = true
            val intent = Intent(context, HostForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    LaunchedEffect(hasStoragePermission, savedPassword) {
        if (hasStoragePermission && savedPassword != null) startSharing()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("এই ফোনটা Host (ফাইল যেখানে থাকবে)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        when {
            !hasStoragePermission -> {
                Text("ফাইল দেখানোর জন্য স্টোরেজ পারমিশন লাগবে।")
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    permissionSettingsLauncher.launch(intent)
                }) { Text("পারমিশন দিন") }
            }

            savedPassword == null -> {
                SetPasswordForm { newPassword ->
                    passwordStore.set(newPassword)
                    savedPassword = newPassword
                }
            }

            else -> {
                if (!isSharing) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("রিলে সার্ভারের সাথে কানেক্ট হচ্ছে…")
                } else {
                    Text(
                        if (clientConnected) "✅ একটা ফোন কানেক্টেড, ফাইল ব্রাউজ করছে"
                        else "🔒 শেয়ারিং চালু আছে, পাসওয়ার্ড দিয়ে অন্য ফোনে কানেক্ট করুন"
                    )
                }
                if (registerFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text("⚠️ এই পাসওয়ার্ডে ইতিমধ্যে আরেকটা Host চালু আছে।")
                }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = {
                    context.stopService(Intent(context, HostForegroundService::class.java))
                    passwordStore.clear()
                    savedPassword = null
                    serviceStarted.value = false
                }) { Text("পাসওয়ার্ড পরিবর্তন করুন") }
            }
        }

        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onBack) { Text("ফিরে যান") }
    }
}

@Composable
private fun SetPasswordForm(onConfirmed: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Text("একটা পাসওয়ার্ড সেট করুন।", style = MaterialTheme.typography.bodyLarge)
    Text(
        "এটা একবারই দিতে হবে। পরে শুধু যার কাছে এই পাসওয়ার্ড থাকবে সে-ই কানেক্ট করতে পারবে।",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it; error = null },
        label = { Text("পাসওয়ার্ড") },
        visualTransformation = PasswordVisualTransformation()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = confirm,
        onValueChange = { confirm = it; error = null },
        label = { Text("পাসওয়ার্ড আবার লিখুন") },
        visualTransformation = PasswordVisualTransformation()
    )
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = {
        when {
            password.length < 6 -> error = "কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন"
            password != confirm -> error = "দুইটা পাসওয়ার্ড মিলছে না"
            else -> onConfirmed(password)
        }
    }) { Text("সেভ করে শেয়ারিং চালু করুন") }
}

private fun hasAllFilesPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
