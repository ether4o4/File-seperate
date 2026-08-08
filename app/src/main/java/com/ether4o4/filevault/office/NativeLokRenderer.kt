package com.ether4o4.filevault.office

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [DocumentRenderer] backed by the bundled LibreOfficeKit engine via [NativeLok].
 *
 * Renders Writer/Calc/Impress/Draw documents to bitmaps entirely on-device and
 * offline. Available only in builds where the engine has been staged
 * (scripts/setup-libreoffice.sh); otherwise [isAvailable] is false and the
 * office viewer falls back to its "engine not installed" message.
 */
class NativeLokRenderer(private val context: Context) : DocumentRenderer {

    override val engineName: String = "LibreOfficeKit (Collabora)"

    override fun isAvailable(): Boolean = LoEnvironment.engineBundled(context)

    override suspend fun open(file: File): OpenDocument? = withContext(Dispatchers.IO) {
        if (!LoEnvironment.ensureReady(context)) return@withContext null
        val handle = NativeLok.nativeLoadDoc(file.absolutePath)
        if (handle == 0L) null else LokDocument(handle)
    }

    private class LokDocument(private val handle: Long) : OpenDocument {
        override val pageCount: Int = NativeLok.nativeGetParts(handle).coerceAtLeast(1)

        override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? =
            withContext(Dispatchers.IO) {
                val size = NativeLok.nativeGetSize(handle, index)
                val twW = size.getOrElse(0) { 0L }.coerceAtLeast(1)
                val twH = size.getOrElse(1) { 0L }.coerceAtLeast(1)
                val w = targetWidthPx.coerceAtLeast(1)
                val h = (targetWidthPx.toLong() * twH / twW).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                if (NativeLok.nativePaint(handle, index, bmp)) bmp else null
            }

        override fun close() {
            NativeLok.nativeDestroyDoc(handle)
        }
    }
}
