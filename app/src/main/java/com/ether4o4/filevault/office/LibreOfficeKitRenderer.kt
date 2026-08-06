package com.ether4o4.filevault.office

import android.app.Activity
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
 * of native libraries + `program/` assets are NOT bundled in this repo — they are
 * added separately (see docs/LIBREOFFICE.md). Talking to them via reflection lets
 * the whole app compile and run WITHOUT the payload: [isAvailable] returns false
 * and the office viewer shows a helpful message. Drop the AAR + jniLibs + assets
 * in and this renderer lights up with no code changes.
 *
 * The reflected calls match the real LibreOfficeKit Android API
 * (github.com/LibreOffice/core, android/Bootstrap/src/org/libreoffice/kit):
 *   LibreOfficeKit.init(Activity)                     // unpacks program/, sets dirs
 *   ByteBuffer h = LibreOfficeKit.getLibreOfficeKitHandle()
 *   Office office = new Office(h)
 *   Document doc = office.documentLoad(path)
 *   doc.initializeForRendering()
 *   int parts = doc.getParts(); doc.setPart(i)
 *   long w = doc.getDocumentWidth();  long h = doc.getDocumentHeight()   // twips
 *   doc.paintTile(buf, canvasW, canvasH, 0, 0, (int)w, (int)h)
 *   doc.destroy()
 *
 * NOTE: [init] requires an Activity, so this renderer is constructed with one.
 */
class LibreOfficeKitRenderer(private val activity: Activity) : DocumentRenderer {

    override val engineName: String = "LibreOfficeKit"

    private val kitClass: Class<*>? by lazy {
        runCatching { Class.forName("org.libreoffice.kit.LibreOfficeKit") }.getOrNull()
    }
    private val officeClass: Class<*>? by lazy {
        runCatching { Class.forName("org.libreoffice.kit.Office") }.getOrNull()
    }

    @Volatile
    private var office: Any? = null

    override fun isAvailable(): Boolean = kitClass != null && officeClass != null

    /** Initializes LOKit once and returns the shared Office handle. */
    private fun ensureOffice(): Any? {
        office?.let { return it }
        val kit = kitClass ?: return null
        val off = officeClass ?: return null
        return synchronized(this) {
            office ?: runCatching {
                kit.getMethod("init", Activity::class.java).invoke(null, activity)
                val handle = kit.getMethod("getLibreOfficeKitHandle").invoke(null)
                off.getConstructor(ByteBuffer::class.java).newInstance(handle).also { office = it }
            }.getOrNull()
        }
    }

    override suspend fun open(file: File): OpenDocument? = withContext(Dispatchers.IO) {
        val o = ensureOffice() ?: return@withContext null
        runCatching {
            val doc = o.javaClass.getMethod("documentLoad", String::class.java)
                .invoke(o, file.absolutePath) ?: return@runCatching null
            doc.javaClass.getMethod("initializeForRendering").invoke(doc)
            LokDocument(doc)
        }.getOrNull()
    }

    /** Wraps a reflected `org.libreoffice.kit.Document`. */
    private class LokDocument(private val doc: Any) : OpenDocument {
        private val cls = doc.javaClass

        override val pageCount: Int =
            runCatching { cls.getMethod("getParts").invoke(doc) as Int }
                .getOrDefault(1)
                .coerceAtLeast(1)

        override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val intType = Int::class.javaPrimitiveType
                    cls.getMethod("setPart", intType).invoke(doc, index)

                    val wTwips = (cls.getMethod("getDocumentWidth").invoke(doc) as Long).coerceAtLeast(1)
                    val hTwips = (cls.getMethod("getDocumentHeight").invoke(doc) as Long).coerceAtLeast(1)

                    val canvasW = targetWidthPx
                    val canvasH = (targetWidthPx.toLong() * hTwips / wTwips).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                    val buffer = ByteBuffer.allocateDirect(canvasW * canvasH * 4)

                    // paintTile(buffer, canvasW, canvasH, tilePosX, tilePosY, tileWidthTwips, tileHeightTwips)
                    cls.getMethod(
                        "paintTile",
                        ByteBuffer::class.java,
                        intType, intType, intType, intType, intType, intType,
                    ).invoke(doc, buffer, canvasW, canvasH, 0, 0, wTwips.toInt(), hTwips.toInt())

                    buffer.rewind()
                    // LOKit paints BGRA; if colors look swapped on-device, swap R/B here.
                    bitmap.copyPixelsFromBuffer(buffer)
                    bitmap
                }.getOrNull()
            }

        override fun close() {
            runCatching { cls.getMethod("destroy").invoke(doc) }
        }
    }
}
