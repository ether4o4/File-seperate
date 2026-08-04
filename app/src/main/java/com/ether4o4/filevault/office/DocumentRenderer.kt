package com.ether4o4.filevault.office

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

/**
 * Abstraction over a document-rendering engine (LibreOfficeKit today, but the
 * viewer never depends on it directly). Modelled on Android's PdfRenderer so the
 * office viewer can page through documents lazily.
 */
interface DocumentRenderer {
    val engineName: String

    /** True only when the native engine and its payload are actually installed. */
    fun isAvailable(): Boolean

    /** Opens a document for rendering, or null if it can't be handled. */
    suspend fun open(file: File): OpenDocument?
}

interface OpenDocument : Closeable {
    /** Pages / slides / sheets. */
    val pageCount: Int

    /** Renders a single page to a bitmap no wider than [targetWidthPx]. */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap?
}
