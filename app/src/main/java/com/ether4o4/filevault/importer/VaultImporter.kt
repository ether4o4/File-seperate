package com.ether4o4.filevault.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.ether4o4.filevault.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Brings external data INTO the vault through the Storage Access Framework.
 *
 * SAF is how Android exposes USB-OTG drives, SD cards and other volumes to apps
 * without broad storage permissions: the user picks a file or a folder, the
 * system hands us a content:// Uri, and we copy the bytes into private storage.
 * Nothing leaves the device and nothing touches a network.
 */
class VaultImporter(
    private val context: Context,
    private val repository: VaultRepository,
) {
    data class Result(val files: Int, val folders: Int, val failures: Int)

    /** Import one or more individually-picked files (ACTION_OPEN_DOCUMENT). */
    suspend fun importFiles(uris: List<Uri>, parentId: Long?): Result =
        withContext(Dispatchers.IO) {
            var files = 0
            var failures = 0
            for (uri in uris) {
                try {
                    val (name, mime) = queryNameAndType(uri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        repository.importStream(
                            name = name,
                            mime = mime,
                            parentId = parentId,
                            input = input,
                            sourceHint = "Imported file",
                        )
                        files++
                    } ?: failures++
                } catch (_: Exception) {
                    failures++
                }
            }
            Result(files, 0, failures)
        }

    /**
     * Import a whole folder tree (ACTION_OPEN_DOCUMENT_TREE) — e.g. an entire USB
     * drive — recreating its structure inside the vault under [parentId].
     */
    suspend fun importTree(treeUri: Uri, parentId: Long?): Result =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result(0, 0, 1)
            val counter = Counter()
            // The picked folder itself becomes a folder in the vault.
            val rootName = root.name?.takeIf { it.isNotBlank() } ?: "Imported"
            val rootId = repository.createFolder(rootName, parentId)
            copyChildren(root, rootId, counter)
            Result(counter.files, counter.folders + 1, counter.failures)
        }

    private suspend fun copyChildren(dir: DocumentFile, parentId: Long, counter: Counter) {
        for (child in dir.listFiles()) {
            try {
                if (child.isDirectory) {
                    val name = child.name?.takeIf { it.isNotBlank() } ?: "folder"
                    val childId = repository.createFolder(name, parentId)
                    counter.folders++
                    copyChildren(child, childId, counter)
                } else {
                    val name = child.name?.takeIf { it.isNotBlank() } ?: "file"
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        repository.importStream(
                            name = name,
                            mime = child.type,
                            parentId = parentId,
                            input = input,
                            sourceHint = "Imported from external storage",
                        )
                        counter.files++
                    } ?: counter.failures++
                }
            } catch (_: Exception) {
                counter.failures++
            }
        }
    }

    private fun queryNameAndType(uri: Uri): Pair<String, String?> {
        var name = "file"
        val mime = context.contentResolver.getType(uri)
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                cursor.getString(idx)?.let { name = it }
            }
        }
        return name to mime
    }

    private class Counter {
        var files = 0
        var folders = 0
        var failures = 0
    }
}
