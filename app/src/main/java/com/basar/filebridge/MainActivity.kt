package com.basar.filebridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.basar.filebridge.ui.ClientScreen
import com.basar.filebridge.ui.HostScreen

private enum class Screen { HOME, HOST, CLIENT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(Screen.HOME) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            onPickHost = { screen = Screen.HOST },
                            onPickClient = { screen = Screen.CLIENT }
                        )
                        Screen.HOST -> HostScreen(onBack = { screen = Screen.HOME })
                        Screen.CLIENT -> ClientScreen(onBack = { screen = Screen.HOME })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onPickHost: () -> Unit, onPickClient: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("FileBridge", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("এই ফোনটা কোন ভূমিকায় থাকবে?", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))

        Button(onClick = onPickHost, modifier = Modifier.fillMaxWidth()) {
            Text("Host — এই ফোনের ফাইল শেয়ার করুন")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onPickClient, modifier = Modifier.fillMaxWidth()) {
            Text("Client — অন্য ফোনের ফাইল দেখুন")
        }
    }
}
