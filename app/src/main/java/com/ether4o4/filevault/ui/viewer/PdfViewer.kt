package com.ether4o4.filevault.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF viewer built on Android's framework [PdfRenderer]. Pages render lazily and
 * are serialized through a mutex (PdfRenderer allows only one open page at a time).
 */
@Composable
fun PdfViewer(file: File) {
    val renderer = remember(file) {
        runCatching {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }

    if (renderer == null) {
        Centered("Could not open this PDF.")
        return
    }
    val mutex = remember { Mutex() }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceIn(256, 2048)
        val pages = remember(renderer) { (0 until renderer.pageCount).toList() }
        LazyColumn {
            items(pages, key = { it }) { index ->
                PdfPage(renderer, mutex, index, widthPx)
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, mutex: Mutex, index: Int, widthPx: Int) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(index) { mutableStateOf(0.7f) }

    LaunchedEffect(index, widthPx) {
        val result = withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    renderer.openPage(index).use { page ->
                        val scale = widthPx.toFloat() / page.width
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp to (widthPx.toFloat() / height)
                    }
                }.getOrNull()
            }
        }
        if (result != null) {
            bitmap = result.first
            ratio = result.second
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        )
    } else {
        // Placeholder keeps scroll position stable while the page renders.
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .padding(4.dp)
                .background(androidx.compose.ui.graphics.Color(0xFFDDDDDD)),
        )
    }
}
