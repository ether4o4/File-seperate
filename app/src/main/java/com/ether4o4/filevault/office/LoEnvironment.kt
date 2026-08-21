package com.ether4o4.filevault.office

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Prepares the on-device LibreOffice environment:
 *  1. unpacks the bundled `assets/lo/lo-assets.zip` (the LibreOffice program/share
 *     resource tree) into app-private storage on first run,
 *  2. loads the native engine, and
 *  3. calls LibreOfficeKit's init with the unpacked program dir + a user profile.
 *
 * Everything lives under filesDir (private, offline) — consistent with the vault's
 * isolation guarantees.
 */
object LoEnvironment {

    private const val ASSET_ZIP = "lo/lo-assets.zip"
    private val initMutex = Mutex()

    @Volatile private var ready = false

    /** True only if this build actually bundled the engine (asset present). */
    fun engineBundled(context: Context): Boolean =
        runCatching {
            context.assets.open(ASSET_ZIP).close(); true
        }.getOrDefault(false)

    /** Idempotently unpacks resources, loads the engine, and inits LOKit. */
    suspend fun ensureReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (ready) return@withContext true
        initMutex.withLock {
            if (ready) return@withLock true
            if (!engineBundled(context)) return@withLock false
            val loDir = File(context.filesDir, "lo")
            val marker = File(loDir, ".ready")
            if (!marker.exists()) {
                runCatching { loDir.deleteRecursively() }
                loDir.mkdirs()
                unzipAsset(context, ASSET_ZIP, loDir)
                File(loDir, "user").mkdirs()
                marker.writeText("1")
            }
            if (!NativeLok.ensureLoaded()) return@withLock false
            val program = File(loDir, "program").absolutePath
            val userUrl = "file://" + File(loDir, "user").absolutePath
            ready = NativeLok.nativeInit(program, userUrl)
            ready
        }
    }

    fun lastError(): String? = runCatching { NativeLok.nativeGetError() }.getOrNull()

    private fun unzipAsset(context: Context, assetPath: String, destDir: File) {
        context.assets.open(assetPath).use { raw ->
            ZipInputStream(raw.buffered()).use { zin ->
                var entry = zin.nextEntry
                val destPath = destDir.canonicalPath + File.separator
                while (entry != null) {
                    val out = File(destDir, entry.name)
                    // Guard against zip-slip.
                    if (!out.canonicalPath.startsWith(destPath)) {
                        entry = zin.nextEntry; continue
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zin.copyTo(it) }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
        }
    }
}
