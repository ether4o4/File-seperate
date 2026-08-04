package com.ether4o4.filevault.ui.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ether4o4.filevault.data.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Text & source-code viewer. Renders in a WebView using a bundled, fully offline
 * copy of highlight.js (app/src/main/assets/highlight). If those assets are ever
 * absent it falls back to plain monospace text — the viewer always works.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TextCodeViewer(file: File, fileName: String) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val html = produceState(initialValue = "", file, dark) {
        value = withContext(Dispatchers.IO) { buildHtml(context, file, fileName, dark) }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }
        },
        update = { web -> web.loadDataWithBaseURL(null, html.value, "text/html", "UTF-8", null) },
    )
}

private const val MAX_HIGHLIGHT_BYTES = 1_000_000L

private fun buildHtml(context: Context, file: File, fileName: String, dark: Boolean): String {
    val raw = runCatching { file.readText() }.getOrElse { return "<pre>Could not read file.</pre>" }
    val escaped = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    val themeFile = if (dark) "github-dark.min.css" else "github.min.css"
    val css = readAsset(context, "highlight/$themeFile")
    val hljs = if (file.length() <= MAX_HIGHLIGHT_BYTES) readAsset(context, "highlight/highlight.min.js") else null

    val lang = FileKind.syntaxLanguage(fileName)
    val codeClass = if (lang != null) "language-$lang" else ""
    val bg = if (dark) "#0d1117" else "#ffffff"
    val fg = if (dark) "#c9d1d9" else "#1f2328"

    val highlightBlock = if (hljs != null && css != null) {
        """
        <style>$css</style>
        <script>$hljs</script>
        <script>hljs.highlightAll();</script>
        """.trimIndent()
    } else {
        "" // plain fallback
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          html,body { margin:0; padding:0; background:$bg; color:$fg; }
          pre { margin:0; padding:12px; white-space:pre; }
          code { font-family: monospace; font-size: 13px; line-height:1.45; }
        </style>
        $highlightBlock
        </head>
        <body>
          <pre><code class="$codeClass">$escaped</code></pre>
        </body>
        </html>
    """.trimIndent()
}

private fun readAsset(context: Context, path: String): String? =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
