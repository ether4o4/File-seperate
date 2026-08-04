package com.ether4o4.filevault.ui.viewer

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a local HTML file in a sandboxed WebView.
 *
 * Loaded via loadDataWithBaseURL(null, …): the page runs with a null origin, so
 * even with JavaScript on it cannot read other vault files (file access is off)
 * and cannot reach the network (the app holds no INTERNET permission).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlViewer(file: File) {
    val html = produceHtml(file)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }
        },
        update = { web ->
            web.loadDataWithBaseURL(null, html.value, "text/html", "UTF-8", null)
        },
    )
}

@Composable
private fun produceHtml(file: File) =
    androidx.compose.runtime.produceState(initialValue = "", file) {
        value = withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrElse { "<h3>Could not read file</h3>" }
        }
    }
