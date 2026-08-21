package com.ether4o4.filevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ether4o4.filevault.ui.explorer.ExplorerScreen
import com.ether4o4.filevault.ui.theme.FileVaultTheme
import com.ether4o4.filevault.ui.tools.ToolsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FileVaultTheme {
                Surface(Modifier.fillMaxSize()) {
                    val page = remember { mutableStateOf("home") }
                    when (page.value) {
                        "vault" -> ExplorerScreen()
                        "tools" -> ToolsScreen(onBack = { page.value = "home" })
                        else -> Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text("File Vault + Toolbox", style = MaterialTheme.typography.headlineMedium)
                            Text("Organize the All folder, inspect the device, and keep the original file explorer in one APK.")
                            Button(onClick = { page.value = "tools" }, modifier = Modifier.fillMaxWidth()) { Text("Open Toolbox") }
                            OutlinedButton(onClick = { page.value = "vault" }, modifier = Modifier.fillMaxWidth()) { Text("Open File Vault") }
                        }
                    }
                }
            }
        }
    }
}
