package com.ether4o4.filevault.data

import com.ether4o4.filevault.data.db.FileNode
import com.ether4o4.filevault.data.db.FileNodeDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Single entry point for all vault operations. Combines the tree metadata
 * ([FileNodeDao]) with the physical byte store ([VaultStorage]).
 */
class VaultRepository(
    private val dao: FileNodeDao,
    private val storage: VaultStorage,
) {
    fun observeAll(): Flow<List<FileNode>> = dao.observeAll()

    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { storage.usedBytes() }

    fun blobFile(node: FileNode): File? =
        node.storageName?.let { storage.blobFile(it) }

    suspend fun createFolder(name: String, parentId: Long?): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insert(
            FileNode(
                parentId = parentId,
                name = uniqueName(parentId, name.trim().ifEmpty { "New folder" }, isFolder = true),
                isFolder = true,
                createdAt = now,
                modifiedAt = now,
            ),
        )
    }

    suspend fun rename(node: FileNode, newName: String) = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean == node.name) return@withContext
        dao.update(
            node.copy(
                name = uniqueName(node.parentId, clean, node.isFolder, excludeId = node.id),
                modifiedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun move(node: FileNode, newParentId: Long?) = withContext(Dispatchers.IO) {
        if (newParentId == node.parentId) return@withContext
        // Guard: a folder cannot be moved into itself or a descendant.
        if (node.isFolder && newParentId != null && isDescendantOf(newParentId, node.id)) return@withContext
        dao.update(
            node.copy(
                parentId = newParentId,
                name = uniqueName(newParentId, node.name, node.isFolder, excludeId = node.id),
                modifiedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Imports raw bytes into the vault as a new file node. Returns the new id. */
    suspend fun importStream(
        name: String,
        mime: String?,
        parentId: Long?,
        input: InputStream,
        sourceHint: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val (storageName, size) = storage.writeNewBlob(input)
        val now = System.currentTimeMillis()
        dao.insert(
            FileNode(
                parentId = parentId,
                name = uniqueName(parentId, name.ifBlank { "file" }, isFolder = false),
                isFolder = false,
                mimeType = mime,
                sizeBytes = size,
                storageName = storageName,
                sourceHint = sourceHint,
                createdAt = now,
                modifiedAt = now,
            ),
        )
    }

    /** Recursively deletes a node, all descendants, and their blobs. */
    suspend fun delete(node: FileNode) = withContext(Dispatchers.IO) {
        val toVisit = ArrayDeque<FileNode>().apply { add(node) }
        val collected = mutableListOf<FileNode>()
        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeFirst()
            collected += current
            if (current.isFolder) toVisit.addAll(dao.childrenOf(current.id))
        }
        // Delete leaves-first so a crash mid-delete never orphans blobs.
        for (n in collected.asReversed()) {
            storage.deleteBlob(n.storageName)
            dao.deleteById(n.id)
        }
    }

    private suspend fun isDescendantOf(candidateId: Long, ancestorId: Long): Boolean {
        var cursor: Long? = candidateId
        while (cursor != null) {
            if (cursor == ancestorId) return true
            cursor = dao.getById(cursor)?.parentId
        }
        return false
    }

    /** Appends " (n)" until the name is unique among siblings of the same type. */
    private suspend fun uniqueName(
        parentId: Long?,
        desired: String,
        isFolder: Boolean,
        excludeId: Long = -1,
    ): String {
        if (dao.countNamed(parentId, desired, isFolder) == 0) return desired
        // If the only match is the node itself (rename to same-ish), keep it.
        val siblings = dao.childrenOf(parentId).filter { it.isFolder == isFolder && it.id != excludeId }
        if (siblings.none { it.name.equals(desired, ignoreCase = true) }) return desired

        val dot = desired.lastIndexOf('.')
        val base = if (!isFolder && dot > 0) desired.substring(0, dot) else desired
        val ext = if (!isFolder && dot > 0) desired.substring(dot) else ""
        var n = 2
        val existing = siblings.map { it.name.lowercase() }.toSet()
        while (true) {
            val candidate = "$base ($n)$ext"
            if (candidate.lowercase() !in existing) return candidate
            n++
        }
    }
}
