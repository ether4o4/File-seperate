package com.ether4o4.filevault.ui.explorer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ether4o4.filevault.data.db.FileNode
import com.ether4o4.filevault.ui.viewer.ViewerScreen

private data class NewFolderReq(val parentId: Long?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(vm: ExplorerViewModel = viewModel()) {
    val nodes by vm.nodes.collectAsState()
    val expanded by vm.expanded.collectAsState()
    val openFile by vm.openFile.collectAsState()
    val status by vm.status.collectAsState()

    val childrenByParent = remember(nodes) { nodes.groupBy { it.parentId } }
    val visibleRows = remember(nodes, expanded) { flatten(childrenByParent, expanded) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(status) {
        status?.let {
            snackbar.showSnackbar(it)
            vm.consumeStatus()
        }
    }

    // Import wiring (Storage Access Framework).
    var pendingImportParent by remember { mutableStateOf<Long?>(null) }
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.importFiles(uris, pendingImportParent) }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { vm.importTree(it, pendingImportParent) } }

    fun importFilesInto(parentId: Long?) {
        pendingImportParent = parentId
        filesLauncher.launch(arrayOf("*/*"))
    }
    fun importFolderInto(parentId: Long?) {
        pendingImportParent = parentId
        treeLauncher.launch(null)
    }

    // Dialog state.
    var newFolderReq by remember { mutableStateOf<NewFolderReq?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var moveTarget by remember { mutableStateOf<FileNode?>(null) }

    fun handleAction(node: FileNode, action: NodeAction) {
        when (action) {
            NodeAction.OPEN -> vm.onNodeClicked(node)
            NodeAction.NEW_SUBFOLDER -> newFolderReq = NewFolderReq(node.id)
            NodeAction.IMPORT_FILES_HERE -> importFilesInto(node.id)
            NodeAction.IMPORT_FOLDER_HERE -> importFolderInto(node.id)
            NodeAction.RENAME -> renameTarget = node
            NodeAction.MOVE -> moveTarget = node
            NodeAction.DELETE -> deleteTarget = node
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("File Vault") },
                actions = {
                    IconButton(onClick = { newFolderReq = NewFolderReq(null) }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = { importFilesInto(null) }) {
                        Icon(Icons.Filled.UploadFile, contentDescription = "Import files")
                    }
                    IconButton(onClick = { importFolderInto(null) }) {
                        Icon(Icons.Filled.DriveFolderUpload, contentDescription = "Import folder")
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val wide = maxWidth >= 640.dp

            val tree: @Composable (Modifier) -> Unit = { mod ->
                if (visibleRows.isEmpty()) {
                    EmptyVault(
                        modifier = mod,
                        onNewFolder = { newFolderReq = NewFolderReq(null) },
                        onImportFiles = { importFilesInto(null) },
                        onImportFolder = { importFolderInto(null) },
                    )
                } else {
                    FileTree(
                        rows = visibleRows,
                        expanded = expanded,
                        openFileId = openFile?.id,
                        onClick = vm::onNodeClicked,
                        onAction = ::handleAction,
                        modifier = mod,
                    )
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    tree(Modifier.weight(0.42f).fillMaxSize())
                    VerticalDivider()
                    Box(Modifier.weight(0.58f).fillMaxSize()) {
                        val f = openFile
                        if (f != null) {
                            ViewerScreen(node = f, vm = vm, onClose = vm::closeViewer)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Select a file to view it",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                tree(Modifier.fillMaxSize())
                val f = openFile
                if (f != null) {
                    Surface(Modifier.fillMaxSize()) {
                        ViewerScreen(node = f, vm = vm, onClose = vm::closeViewer)
                    }
                }
            }
        }
    }

    // ---- Dialogs ----
    newFolderReq?.let { req ->
        NameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name -> vm.createFolder(name, req.parentId); newFolderReq = null },
            onDismiss = { newFolderReq = null },
        )
    }
    renameTarget?.let { node ->
        NameDialog(
            title = "Rename",
            initial = node.name,
            confirmLabel = "Rename",
            onConfirm = { name -> vm.rename(node, name); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { node ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${if (node.isFolder) "folder" else "file"}?") },
            text = {
                Text(
                    if (node.isFolder) "\"${node.name}\" and everything inside it will be permanently removed from the vault."
                    else "\"${node.name}\" will be permanently removed from the vault.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(node); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
    moveTarget?.let { node ->
        MoveDialog(
            node = node,
            allNodes = nodes,
            childrenByParent = childrenByParent,
            onConfirm = { newParent -> vm.move(node, newParent); moveTarget = null },
            onDismiss = { moveTarget = null },
        )
    }
}

private fun flatten(
    childrenByParent: Map<Long?, List<FileNode>>,
    expanded: Set<Long>,
): List<VisibleRow> {
    val out = ArrayList<VisibleRow>()
    fun rec(parentId: Long?, depth: Int) {
        val kids = childrenByParent[parentId] ?: return
        for (k in kids) {
            out += VisibleRow(k, depth)
            if (k.isFolder && k.id in expanded) rec(k.id, depth + 1)
        }
    }
    rec(null, 0)
    return out
}

@Composable
private fun EmptyVault(
    modifier: Modifier,
    onNewFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text("Your vault is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything you add here stays inside this app, on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onNewFolder) { Text("Create a folder") }
            OutlinedButton(onClick = onImportFiles) { Text("Import files") }
            OutlinedButton(onClick = onImportFolder) { Text("Import a folder / USB drive") }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveDialog(
    node: FileNode,
    allNodes: List<FileNode>,
    childrenByParent: Map<Long?, List<FileNode>>,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Folders can't move into themselves or their own descendants.
    val blocked = remember(node, childrenByParent) {
        val ids = mutableSetOf(node.id)
        val queue = ArrayDeque<Long>().apply { add(node.id) }
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            childrenByParent[p]?.forEach { c ->
                if (c.isFolder && ids.add(c.id)) queue.add(c.id)
            }
        }
        ids
    }
    val folders = remember(allNodes, blocked) {
        allNodes.filter { it.isFolder && it.id !in blocked }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${node.name}\" to…") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                item {
                    MoveTargetRow(
                        label = "Vault (root)",
                        enabled = node.parentId != null,
                        onClick = { onConfirm(null) },
                    )
                    HorizontalDivider()
                }
                items(folders, key = { it.id }) { f ->
                    MoveTargetRow(
                        label = f.name,
                        enabled = node.parentId != f.id,
                        onClick = { onConfirm(f.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveTargetRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Normal,
        )
    }
}
