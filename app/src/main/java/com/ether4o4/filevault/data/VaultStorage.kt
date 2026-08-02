package com.ether4o4.filevault.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Owns the physical bytes of the vault.
 *
 * Everything is written under [Context.getFilesDir], i.e. the app's PRIVATE
 * internal storage (/data/data/<pkg>/files). Other apps and the phone's file
 * managers cannot see or touch it, and it is excluded from cloud backup by the
 * manifest. This is what makes the vault a "separate memory".
 */
class VaultStorage(context: Context) {

    private val blobDir: File = File(context.filesDir, "blobs").apply { mkdirs() }

    fun blobFile(storageName: String): File = File(blobDir, storageName)

    /**
     * Streams [input] into a freshly created blob and returns its (storageName, size).
     * The stream is fully consumed but not closed (caller owns it).
     */
    fun writeNewBlob(input: InputStream): Pair<String, Long> {
        val storageName = UUID.randomUUID().toString()
        val target = File(blobDir, storageName)
        var total = 0L
        target.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                out.write(buf, 0, read)
                total += read
            }
            out.flush()
        }
        return storageName to total
    }

    fun deleteBlob(storageName: String?) {
        if (storageName != null) File(blobDir, storageName).delete()
    }

    /** Total bytes currently stored in the vault. */
    fun usedBytes(): Long =
        blobDir.listFiles()?.sumOf { it.length() } ?: 0L
}
