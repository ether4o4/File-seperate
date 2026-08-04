package com.ether4o4.filevault.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One node in the vault's virtual file tree.
 *
 * The tree structure (folders, names, nesting) lives entirely in this table.
 * The actual bytes of a file live in the app's private internal storage, in a
 * flat blob directory, referenced by [storageName]. This separation means:
 *  - the "file system" the user sees is fully owned by the app (true isolation),
 *  - names can be anything (no clashes with real filesystem naming rules),
 *  - moving/renaming is a cheap metadata update, never a byte copy.
 */
@Entity(
    tableName = "file_nodes",
    indices = [Index("parentId"), Index("storageName", unique = true)],
)
data class FileNode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Parent folder id, or null for a top-level node under the vault root. */
    val parentId: Long? = null,

    val name: String,

    val isFolder: Boolean,

    /** MIME type for files (best-effort), null for folders. */
    val mimeType: String? = null,

    /** Byte size for files, 0 for folders. */
    val sizeBytes: Long = 0,

    /** Physical blob filename inside internal storage; null for folders. */
    val storageName: String? = null,

    /** Human-readable note about where this was imported from (display only). */
    val sourceHint: String? = null,

    val createdAt: Long = 0,
    val modifiedAt: Long = 0,
)
