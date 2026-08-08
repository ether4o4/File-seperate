package com.ether4o4.filevault.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ether4o4.filevault.office.NativeLokRenderer
import com.ether4o4.filevault.office.OpenDocument
import java.io.File
import java.util.Locale

/**
 * Office-document viewer backed by LibreOfficeKit. When the LOKit payload is
 * installed it renders each page/slide/sheet to a bitmap. When it isn't, it
 * explains how to enable it and, for plain-text formats, offers a text fallback.
 */
@Composable
fun OfficeViewer(file: File, fileName: String) {
    val context = LocalContext.current
    val renderer = remember { NativeLokRenderer(context.applicationContext) }

    if (!renderer.isAvailable()) {
        OfficeUnavailable(file, fileName)
        return
    }

    var doc by remember(file) { mutableStateOf<OpenDocument?>(null) }
    var failed by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        val opened = renderer.open(file)
        if (opened == null) failed = true else doc = opened
    }
    DisposableEffect(doc) { onDispose { runCatching { doc?.close() } } }

    when {
        failed -> Centered("LibreOffice could not open this document.")
        doc == null -> Centered("Rendering with ${renderer.engineName}…")
        else -> OfficePages(doc!!)
    }
}

@Composable
private fun OfficePages(doc: OpenDocument) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceIn(256, 2048)
        val pages = remember(doc) { (0 until doc.pageCount).toList() }
        LazyColumn {
            items(pages, key = { it }) { index ->
                OfficePage(doc, index, widthPx)
            }
        }
    }
}

@Composable
private fun OfficePage(doc: OpenDocument, index: Int, widthPx: Int) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, widthPx) {
        bitmap = doc.renderPage(index, widthPx)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(4.dp),
        )
    } else {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Page ${index + 1}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OfficeUnavailable(file: File, fileName: String) {
    var showAsText by remember { mutableStateOf(false) }
    if (showAsText) {
        TextCodeViewer(file, fileName)
        return
    }
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val textual = ext in setOf("csv", "txt", "rtf", "fodt", "fods", "fodp")

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "LibreOffice engine not installed",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "This build doesn't include the LibreOfficeKit native payload, so " +
                    "\"$fileName\" can't be rendered yet. See docs/LIBREOFFICE.md to add it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (textual) {
                OutlinedButton(onClick = { showAsText = true }) { Text("Open as text instead") }
            }
        }
    }
}
