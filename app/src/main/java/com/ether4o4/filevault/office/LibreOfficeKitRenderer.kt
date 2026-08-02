package com.ether4o4.filevault.office

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Renders office documents with LibreOfficeKit (the native LibreOffice engine
 * that Collabora maintains for Android).
 *
 * WHY REFLECTION: the LOKit classes (`org.libreoffice.kit.*`) and their ~200 MB
 * of native libraries + `program/` assets are NOT bundled in this repo — they
 * are added separately (see docs/LIBREOFFICE.md). Talking to them via reflection
 * lets the whole app compile and run WITHOUT the payload: [isAvailable] simply
 * returns false and the office viewer shows a helpful message. Drop the AAR +
 * jniLibs + assets in, and this renderer lights up with zero code changes.
 *
 * The canonical, direct (non-reflection) implementation is documented in
 * docs/LIBREOFFICE.md — swap this class for it once the dependency is a hard one.
 */
class LibreOfficeKitRenderer(private val appContext: Context) : DocumentRenderer {

    override val engineName: String = "LibreOfficeKit"

    private val kitClass: Class<*>? by lazy {
        runCatching { Class.forName("org.libreoffice.kit.LibreOfficeKit") }.getOrNull()
    }

    @Volatile
    private var initialized = false

    override fun isAvailable(): Boolean = kitClass != null

    private fun ensureInit(): Boolean {
        if (initialized) return true
        val cls = kitClass ?: return false
        return runCatching {
            // LibreOfficeKit.init(Context) sets up native lib dirs and unpacks
            // the program/ assets on first run.
            cls.getMethod("init", Context::class.java).invoke(null, appContext)
            initialized = true
            true
        }.getOrDefault(false)
    }

    override suspend fun open(file: File): OpenDocument? = withContext(Dispatchers.IO) {
        if (!ensureInit()) return@withContext null
        runCatching {
            val cls = kitClass!!
            val office = cls.getMethod("getOffice").invoke(null)
                ?: return@runCatching null
            val documentLoad = office.javaClass.getMethod("documentLoad", String::class.java)
            val document = documentLoad.invoke(office, file.absolutePath)
                ?: return@runCatching null
            LokDocument(document)
        }.getOrNull()
    }

    /** Wraps a reflected `org.libreoffice.kit.Document`. */
    private class LokDocument(private val document: Any) : OpenDocument {

        private val doc = document.javaClass

        override val pageCount: Int =
            runCatching { doc.getMethod("getParts").invoke(document) as Int }
                .getOrDefault(1)
                .coerceAtLeast(1)

        override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? =
            withContext(Dispatchers.IO) {
                runCatching {
                    doc.getMethod("setPart", Int::class.javaPrimitiveType)
                        .invoke(document, index)

                    // Document size is reported in twips (1/1440 inch).
                    val size = LongArray(2)
                    doc.getMethod("getDocumentSize", LongArray::class.java, LongArray::class.java)
                        .let { m ->
                            val w = LongArray(1); val h = LongArray(1)
                            m.invoke(document, w, h)
                            size[0] = w[0]; size[1] = h[0]
                        }
                    val twipsW = size[0].coerceAtLeast(1)
                    val twipsH = size[1].coerceAtLeast(1)

                    val canvasW = targetWidthPx
                    val canvasH = (targetWidthPx * twipsH / twipsW).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                    val buffer = ByteBuffer.allocateDirect(canvasW * canvasH * 4)

                    // paintTile(buffer, canvasW, canvasH, tilePosX, tilePosY, tileW, tileH)
                    doc.getMethod(
                        "paintTile",
                        ByteBuffer::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ).invoke(
                        document, buffer, canvasW, canvasH,
                        0, 0, twipsW.toInt(), twipsH.toInt(),
                    )
                    buffer.rewind()
                    bitmap.copyPixelsFromBuffer(buffer)
                    bitmap
                }.getOrNull()
            }

        override fun close() {
            runCatching { doc.getMethod("destroy").invoke(document) }
        }
    }
}
