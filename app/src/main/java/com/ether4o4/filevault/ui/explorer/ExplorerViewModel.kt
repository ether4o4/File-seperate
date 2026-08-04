package com.ether4o4.filevault.ui.explorer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ether4o4.filevault.VaultApplication
import com.ether4o4.filevault.data.db.FileNode
import com.ether4o4.filevault.importer.VaultImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ExplorerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as VaultApplication).repository
    private val importer = VaultImporter(app, repository)

    val nodes: StateFlow<List<FileNode>> =
        repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _expanded = MutableStateFlow<Set<Long>>(emptySet())
    val expanded: StateFlow<Set<Long>> = _expanded.asStateFlow()

    /** File currently open in the viewer pane (null = none). */
    private val _openFile = MutableStateFlow<FileNode?>(null)
    val openFile: StateFlow<FileNode?> = _openFile.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun toggleExpanded(id: Long) {
        _expanded.value = _expanded.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun onNodeClicked(node: FileNode) {
        if (node.isFolder) toggleExpanded(node.id) else _openFile.value = node
    }

    fun closeViewer() { _openFile.value = null }

    fun consumeStatus() { _status.value = null }

    fun blobFile(node: FileNode): File? = repository.blobFile(node)

    fun createFolder(name: String, parentId: Long?) = viewModelScope.launch {
        repository.createFolder(name, parentId)
        parentId?.let { _expanded.value = _expanded.value + it }
    }

    fun rename(node: FileNode, newName: String) = viewModelScope.launch {
        repository.rename(node, newName)
    }

    fun delete(node: FileNode) = viewModelScope.launch {
        if (_openFile.value?.id == node.id) _openFile.value = null
        repository.delete(node)
    }

    fun move(node: FileNode, newParentId: Long?) = viewModelScope.launch {
        repository.move(node, newParentId)
    }

    fun importFiles(uris: List<Uri>, parentId: Long?) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        val r = importer.importFiles(uris, parentId)
        parentId?.let { _expanded.value = _expanded.value + it }
        _status.value = buildString {
            append("Imported ${r.files} file(s)")
            if (r.failures > 0) append(" · ${r.failures} failed")
        }
    }

    fun importTree(treeUri: Uri, parentId: Long?) = viewModelScope.launch {
        val r = importer.importTree(treeUri, parentId)
        parentId?.let { _expanded.value = _expanded.value + it }
        _status.value = "Imported ${r.files} file(s) in ${r.folders} folder(s)" +
            if (r.failures > 0) " · ${r.failures} failed" else ""
    }
}
