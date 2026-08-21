package com.ether4o4.filevault.ui.tools

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ether4o4.filevault.tools.OrganizerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { OrganizerEngine(context) }
    var treeUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var output by remember { mutableStateOf("Choose your All folder. Dry Run never moves files.") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Throwable) { }
            treeUri = it
            output = "Selected: ${DocumentFile.fromTreeUri(context, it)?.name ?: it}"
        }
    }

    fun runTool(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            output = try { block() } catch (t: Throwable) { "ERROR: ${t.message}" }
            busy = false
        }
    }

    suspend fun folderStats(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext "Folder unavailable"
        var files = 0L
        var bytes = 0L
        fun walk(dir: DocumentFile) {
            for (f in dir.listFiles()) {
                if (f.isDirectory) walk(f) else { files++; bytes += f.length().coerceAtLeast(0) }
            }
        }
        walk(root)
        "Files: $files\nSize: %.2f GB\nSize: %.2f MB".format(bytes / 1073741824.0, bytes / 1048576.0)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Toolbox") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("File Organizer", style = MaterialTheme.typography.titleLarge)
                        Text("Aggressive Apple/iPhone, metadata, dashboard, web, media, code and archive classification.")
                        Button(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (treeUri == null) "Choose All Folder" else "Change Folder")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = treeUri != null && !busy,
                                onClick = {
                                    runTool {
                                        val r = engine.organize(treeUri!!, true)
                                        "DRY RUN\nScanned: ${r.scanned}\n\n" + r.counts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Dry Run") }
                            Button(
                                enabled = treeUri != null && !busy,
                                onClick = {
                                    runTool {
                                        val r = engine.organize(treeUri!!, false)
                                        "ORGANIZED\nScanned: ${r.scanned}\nMoved: ${r.moved}\nErrors: ${r.errors}"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Organize") }
                        }
                        OutlinedButton(enabled = !busy, onClick = { runTool { "Restored ${engine.undo()} files." } }, modifier = Modifier.fillMaxWidth()) { Text("Undo Last Organize") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Storage Tools", style = MaterialTheme.typography.titleLarge)
                        Button(enabled = treeUri != null && !busy, onClick = { runTool { folderStats(treeUri!!) } }, modifier = Modifier.fillMaxWidth()) { Text("Storage Report") }
                        Button(enabled = !busy, onClick = {
                            runTool {
                                val pm = context.packageManager
                                "Installed packages: ${pm.getInstalledApplications(0).size}"
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Package Inventory") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Device Tools", style = MaterialTheme.typography.titleLarge)
                        Button(enabled = !busy, onClick = {
                            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                            val temp = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it >= 0 }?.div(10f)
                            output = "Battery: $pct%\nTemperature: ${temp ?: "?"}°C"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Battery Report") }
                        Button(enabled = !busy, onClick = {
                            output = "Manufacturer: ${Build.MANUFACTURER}\nModel: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nSDK: ${Build.VERSION.SDK_INT}\nABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Device Info") }
                        Button(enabled = !busy, onClick = {
                            output = "Max runtime memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB\nProcessors: ${Runtime.getRuntime().availableProcessors()}"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Memory / CPU") }
                        Button(enabled = !busy, onClick = {
                            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val network = cm.activeNetwork
                            val caps = network?.let { cm.getNetworkCapabilities(it) }
                            output = if (caps == null) "No active network" else "Network active\nWi-Fi: ${caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)}\nCellular: ${caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)}"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Network Status") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Output", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(output)
                    }
                }
            }
        }
    }
}
