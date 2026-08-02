package com.ether4o4.filevault.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ether4o4.filevault.data.FileKind
import com.ether4o4.filevault.data.db.FileNode
import com.ether4o4.filevault.ui.explorer.ExplorerViewModel
import com.ether4o4.filevault.ui.explorer.formatSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(node: FileNode, vm: ExplorerViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val file: File? = vm.blobFile(node)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                }
            },
            title = {
                Column {
                    Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatSize(node.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Box(Modifier.fillMaxSize()) {
            if (file == null || !file.exists()) {
                Centered("File data is missing.")
            } else {
                when (FileKind.of(node.name, node.mimeType)) {
                    FileKind.IMAGE -> ImageViewer(file)
                    FileKind.PDF -> PdfViewer(file)
                    FileKind.HTML -> HtmlViewer(file)
                    FileKind.SQLITE -> SqliteViewer(file)
                    FileKind.AUDIO, FileKind.VIDEO -> MediaViewer(file, node.mimeType)
                    FileKind.CODE, FileKind.TEXT -> TextCodeViewer(file, node.name)
                    FileKind.OFFICE -> OfficeViewer(file, node.name)
                    FileKind.OTHER -> UnknownViewer(node)
                }
            }
        }
    }
}

@Composable
private fun UnknownViewer(node: FileNode) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No bundled viewer for this file type", style = MaterialTheme.typography.titleMedium)
            Text(
                node.mimeType ?: "unknown type",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Centered(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
