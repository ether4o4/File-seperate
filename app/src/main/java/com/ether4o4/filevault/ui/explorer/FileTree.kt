package com.ether4o4.filevault.ui.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ether4o4.filevault.data.FileKind
import com.ether4o4.filevault.data.db.FileNode

/** A node plus its depth in the currently-expanded tree. */
data class VisibleRow(val node: FileNode, val depth: Int)

/** Actions a row can trigger. Folder-only actions are ignored for files. */
enum class NodeAction { OPEN, NEW_SUBFOLDER, IMPORT_FILES_HERE, IMPORT_FOLDER_HERE, RENAME, MOVE, DELETE }

@Composable
fun FileTree(
    rows: List<VisibleRow>,
    expanded: Set<Long>,
    openFileId: Long?,
    onClick: (FileNode) -> Unit,
    onAction: (FileNode, NodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(rows, key = { it.node.id }) { row ->
            FileRow(
                row = row,
                isExpanded = row.node.id in expanded,
                isSelected = row.node.id == openFileId,
                onClick = { onClick(row.node) },
                onAction = { onAction(row.node, it) },
            )
        }
    }
}

@Composable
private fun FileRow(
    row: VisibleRow,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAction: (NodeAction) -> Unit,
) {
    val node = row.node
    var menuOpen by remember { mutableStateOf(false) }
    val bg = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface

    Surface(color = bg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = (8 + row.depth * 16).dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            // Expand/collapse chevron for folders; spacer for files to align names.
            if (node.isFolder) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                    else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.width(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = iconFor(node),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (node.isFolder) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                Column {
                    Text(
                        text = node.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!node.isFolder) {
                        Text(
                            text = formatSize(node.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Box {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Actions",
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { menuOpen = true }
                        .padding(8.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    fun item(label: String, action: NodeAction) = DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { menuOpen = false; onAction(action) },
                    )
                    if (!node.isFolder) item("Open", NodeAction.OPEN)
                    if (node.isFolder) {
                        item("New subfolder", NodeAction.NEW_SUBFOLDER)
                        item("Import files here", NodeAction.IMPORT_FILES_HERE)
                        item("Import folder here", NodeAction.IMPORT_FOLDER_HERE)
                    }
                    item("Rename", NodeAction.RENAME)
                    item("Move to…", NodeAction.MOVE)
                    item("Delete", NodeAction.DELETE)
                }
            }
        }
    }
}

private fun iconFor(node: FileNode): ImageVector {
    if (node.isFolder) return Icons.Filled.Folder
    return when (FileKind.of(node.name, node.mimeType)) {
        FileKind.IMAGE -> Icons.Filled.Image
        FileKind.PDF -> Icons.Filled.PictureAsPdf
        FileKind.AUDIO -> Icons.Filled.Audiotrack
        FileKind.VIDEO -> Icons.Filled.Movie
        FileKind.CODE, FileKind.HTML -> Icons.Filled.Code
        FileKind.SQLITE -> Icons.Filled.Storage
        FileKind.OFFICE, FileKind.TEXT -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
