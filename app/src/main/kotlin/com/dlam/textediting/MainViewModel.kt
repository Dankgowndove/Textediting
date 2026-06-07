package com.dlam.textediting

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings by lazy { SettingsManager(application) }

    private val _textContent = MutableStateFlow("")
    val textContent: StateFlow<String> = _textContent.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _fileName = MutableStateFlow("")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _searchMatchCount = MutableStateFlow(0)
    val searchMatchCount: StateFlow<Int> = _searchMatchCount.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    private val _searchPositions = MutableStateFlow<List<Int>>(emptyList())
    val searchPositions: StateFlow<List<Int>> = _searchPositions.asStateFlow()

    val undoManager = UndoManager()
    private var savedText: String = ""

    val canUndo: Boolean get() = undoManager.canUndo
    val canRedo: Boolean get() = undoManager.canRedo

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val _fileTree = MutableStateFlow(FileTreeState())
    val fileTree: StateFlow<FileTreeState> = _fileTree.asStateFlow()

    private val _openTabs = MutableStateFlow<List<OpenTab>>(emptyList())
    val openTabs: StateFlow<List<OpenTab>> = _openTabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(-1)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _globalSearchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val globalSearchResults: StateFlow<List<GlobalSearchResult>> = _globalSearchResults.asStateFlow()

    private val _isGlobalSearching = MutableStateFlow(false)
    val isGlobalSearching: StateFlow<Boolean> = _isGlobalSearching.asStateFlow()

    private val _isCaseSensitive = MutableStateFlow(false)
    val isCaseSensitive: StateFlow<Boolean> = _isCaseSensitive.asStateFlow()

    private val _isWholeWord = MutableStateFlow(false)
    val isWholeWord: StateFlow<Boolean> = _isWholeWord.asStateFlow()

    private val _pendingScrollToLine = MutableSharedFlow<Int>()
    val pendingScrollToLine: SharedFlow<Int> = _pendingScrollToLine.asSharedFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    private var clipboardUri: Uri? = null

    // File tree cache: maps directory URI to its (sorted child URIs, names, isDir) triplets
    private val dirCache = mutableMapOf<Uri, List<Triple<Uri, String, Boolean>>>()

    fun clearFileTreeCache() {
        dirCache.clear()
    }

    private val textExtensions = setOf(
        "txt", "md", "json", "xml", "csv", "ini", "cfg", "log",
        "yml", "yaml", "java", "kt", "html", "htm", "css", "js",
        "ts", "py", "sh", "bat", "properties", "gradle", "kts",
        "c", "cpp", "h", "hpp", "go", "rs", "rb", "php", "sql"
    )

    fun openFile(uri: Uri) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val (text, name) = withContext(Dispatchers.IO) {
                    val t = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
                    } ?: throw Exception("无法打开文件")
                    val n = getFileName(uri)
                    Pair(t, n)
                }
                dismissSearch()
                _fileName.value = name
                _currentUri.value = uri
                _textContent.value = text
                savedText = text
                _isModified.value = false
                undoManager.clear()
                undoManager.record(text)
                addOrSwitchTab(uri, name, text)
                _snackbarEvent.emit("已打开：$name")
            } catch (e: Exception) {
                _snackbarEvent.emit("打开失败：${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createNewFile() {
        dismissSearch()
        _fileName.value = "无标题"
        _currentUri.value = null
        _textContent.value = ""
        savedText = ""
        _isModified.value = false
        undoManager.clear()
        undoManager.record("")
    }

    fun onTextChanged(newText: String) {
        _textContent.value = newText
        _isModified.value = newText != savedText
        undoManager.record(newText)
    }

    fun saveFile() {
        viewModelScope.launch {
            val uri = _currentUri.value ?: return@launch
            try {
                val text = _textContent.value
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("无法写入文件")
                }
                savedText = text
                _isModified.value = false
                updateActiveTabSaved(text)
                _snackbarEvent.emit("已保存")
            } catch (e: Exception) {
                _snackbarEvent.emit("保存失败：${e.message}")
            }
        }
    }

    fun saveAs(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = _textContent.value
                val name = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("无法写入文件")
                    getFileName(uri)
                }
                _currentUri.value = uri
                _fileName.value = name
                savedText = text
                _isModified.value = false
                replaceActiveTabWith(uri, name, text)
                _snackbarEvent.emit("已保存")
            } catch (e: Exception) {
                _snackbarEvent.emit("保存失败：${e.message}")
            }
        }
    }

    private fun updateActiveTabSaved(text: String) {
        val tabs = _openTabs.value.toMutableList()
        val idx = _activeTabIndex.value
        if (idx in tabs.indices) {
            tabs[idx] = tabs[idx].copy(
                content = text,
                savedText = text,
                isModified = false
            )
            _openTabs.value = tabs
        }
    }

    private fun replaceActiveTabWith(uri: Uri, name: String, text: String) {
        val tabs = _openTabs.value.toMutableList()
        val idx = _activeTabIndex.value
        if (idx in tabs.indices) {
            tabs[idx] = OpenTab(
                uri = uri,
                fileName = name,
                content = text,
                savedText = text,
                isModified = false
            )
            _openTabs.value = tabs
        }
    }

    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _searchQuery.value = ""
        }
    }

    fun dismissSearch() {
        _isSearchVisible.value = false
        _searchQuery.value = ""
        _searchMatchCount.value = 0
        _currentSearchIndex.value = -1
        _searchPositions.value = emptyList()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        performSearch(query)
    }

    private fun performSearch(query: String) {
        val text = _textContent.value
        if (query.isEmpty()) {
            _searchPositions.value = emptyList()
            _searchMatchCount.value = 0
            _currentSearchIndex.value = -1
            return
        }
        val positions = findAllPositions(text, query)
        _searchPositions.value = positions
        _searchMatchCount.value = positions.size
        _currentSearchIndex.value = if (positions.isNotEmpty()) 0 else -1
    }

    fun getSearchPosition(): Pair<Int, Int>? {
        val positions = _searchPositions.value
        val query = _searchQuery.value
        val idx = _currentSearchIndex.value
        if (positions.isEmpty() || query.isEmpty() || idx < 0) return null
        return Pair(positions[idx], query.length)
    }

    fun searchNext() {
        val query = _searchQuery.value
        if (query.isEmpty()) return
        val text = _textContent.value
        val positions = findAllPositions(text, query)
        if (positions.isEmpty()) {
            _searchPositions.value = emptyList()
            _searchMatchCount.value = 0
            _currentSearchIndex.value = -1
            return
        }
        _searchPositions.value = positions
        _searchMatchCount.value = positions.size
        val current = _currentSearchIndex.value
        _currentSearchIndex.value = if (current < 0) 0 else (current + 1) % positions.size
    }

    fun searchPrevious() {
        val query = _searchQuery.value
        if (query.isEmpty()) return
        val text = _textContent.value
        val positions = findAllPositions(text, query)
        if (positions.isEmpty()) {
            _searchPositions.value = emptyList()
            _searchMatchCount.value = 0
            _currentSearchIndex.value = -1
            return
        }
        _searchPositions.value = positions
        _searchMatchCount.value = positions.size
        val current = _currentSearchIndex.value
        _currentSearchIndex.value = if (current <= 0) positions.size - 1 else current - 1
    }

    private fun findAllPositions(text: String, query: String): List<Int> {
        val positions = mutableListOf<Int>()
        if (query.isEmpty()) return positions
        val caseSensitive = _isCaseSensitive.value
        val searchText = if (caseSensitive) text else text.lowercase()
        val searchQuery = if (caseSensitive) query else query.lowercase()
        val wholeWord = _isWholeWord.value
        var index = searchText.indexOf(searchQuery, 0)
        while (index >= 0) {
            if (wholeWord) {
                val before = if (index > 0) searchText[index - 1] else ' '
                val after = if (index + searchQuery.length < searchText.length) searchText[index + searchQuery.length] else ' '
                if (before.isLetterOrDigit() || after.isLetterOrDigit()) {
                    index = searchText.indexOf(searchQuery, index + 1)
                    continue
                }
            }
            positions.add(index)
            index = searchText.indexOf(searchQuery, index + 1)
        }
        return positions
    }

    fun selectRootDir(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                dirCache.clear()
                _fileTree.value = _fileTree.value.copy(rootUri = uri, isLoading = true)
                refreshFileTree()
            } catch (e: Exception) {
                _snackbarEvent.emit("选择目录失败：${e.message}")
            }
        }
    }

    /** Full refresh — clears cache and rebuilds entire tree. Used for manual refresh or after file ops. */
    fun refreshFileTree() {
        val rootUri = _fileTree.value.rootUri ?: return
        refreshJob?.cancel()
        // Only invalidate cache on full manual refresh
        dirCache.clear()
        refreshJob = viewModelScope.launch {
            val expandedSnapshot = _fileTree.value.expandedUris
            _fileTree.value = _fileTree.value.copy(isLoading = true, error = null)
            try {
                val nodes = withContext(Dispatchers.IO) {
                    buildFileTree(rootUri, 0, expandedSnapshot)
                }
                if (isActive) {
                    _fileTree.value = _fileTree.value.copy(
                        nodes = nodes, isLoading = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    _fileTree.value = _fileTree.value.copy(
                        isLoading = false, error = e.message
                    )
                }
            }
        }
    }

    private fun getOrLoadDirListing(uri: Uri): List<Triple<Uri, String, Boolean>> {
        dirCache[uri]?.let { return it }
        val context = getApplication<Application>()
        val docFile = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
        if (docFile == null || !docFile.exists()) return emptyList()
        val children = docFile.listFiles().toList()
        // Sort: directories first, then by name (case-insensitive)
        val sorted = children.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }
                .thenBy { it.name?.lowercase() ?: "" }
        )
        val listing = sorted.map { Triple(it.uri, it.name ?: "未知", it.isDirectory) }
        dirCache[uri] = listing
        return listing
    }

    private fun buildFileTree(uri: Uri, depth: Int, expandedUris: Set<Uri> = emptySet()): List<FileNode> {
        if (depth > 10) return emptyList()
        val children = getOrLoadDirListing(uri)
        val result = mutableListOf<FileNode>()
        for ((childUri, name, isDir) in children) {
            result.add(FileNode(
                uri = childUri,
                name = name,
                isDirectory = isDir,
                depth = depth
            ))
            if (isDir && expandedUris.contains(childUri)) {
                result.addAll(buildFileTree(childUri, depth + 1, expandedUris))
            }
        }
        return result
    }

    /** Toggle expand/collapse a directory — updates the node list incrementally without full rebuild. */
    fun toggleExpandDir(uri: Uri) {
        val currentState = _fileTree.value
        val currentExpanded = currentState.expandedUris.toMutableSet()
        val isExpanding = !currentExpanded.contains(uri)

        if (isExpanding) {
            currentExpanded.add(uri)
            _fileTree.value = currentState.copy(expandedUris = currentExpanded, isLoading = true)

            // Load children for just this directory in background
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                try {
                    val newNodes = currentState.nodes.toMutableList()
                    val parentIdx = newNodes.indexOfFirst { it.uri == uri }
                    if (parentIdx >= 0) {
                        val parentDepth = newNodes[parentIdx].depth
                        // Remove any previously cached children at this position (shouldn't exist, but be safe)
                        var removeIdx = parentIdx + 1
                        while (removeIdx < newNodes.size && newNodes[removeIdx].depth > parentDepth) {
                            removeIdx++
                        }
                        if (removeIdx > parentIdx + 1) {
                            newNodes.subList(parentIdx + 1, removeIdx).clear()
                        }
                        // Load new children
                        val children = withContext(Dispatchers.IO) {
                            buildFileTree(uri, parentDepth + 1, currentExpanded)
                        }
                        newNodes.addAll(parentIdx + 1, children)
                    }
                    if (isActive) {
                        _fileTree.value = _fileTree.value.copy(
                            nodes = newNodes, expandedUris = currentExpanded, isLoading = false
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) {
                        _fileTree.value = _fileTree.value.copy(
                            isLoading = false, error = e.message
                        )
                    }
                }
            }
        } else {
            // Collapsing: remove children from node list
            currentExpanded.remove(uri)
            // Also remove all descendant URIs from expanded set
            val descendantsToRemove = currentExpanded.filter { expandedUri ->
                expandedUri.toString().startsWith(uri.toString())
            }.toSet()
            currentExpanded.removeAll(descendantsToRemove)
            val newNodes = currentState.nodes.toMutableList()
            val parentIdx = newNodes.indexOfFirst { it.uri == uri }
            if (parentIdx >= 0) {
                val parentDepth = newNodes[parentIdx].depth
                var removeIdx = parentIdx + 1
                while (removeIdx < newNodes.size && newNodes[removeIdx].depth > parentDepth) {
                    removeIdx++
                }
                if (removeIdx > parentIdx + 1) {
                    newNodes.subList(parentIdx + 1, removeIdx).clear()
                }
            }
            _fileTree.value = _fileTree.value.copy(
                nodes = newNodes, expandedUris = currentExpanded
            )
        }
    }

    fun createFile(parentUri: Uri, name: String) {
        viewModelScope.launch {
            try {
                val docFile = DocumentFile.fromTreeUri(
                    getApplication(), parentUri
                ) ?: throw Exception("无法访问目录")
                val created = withContext(Dispatchers.IO) {
                    val fileName = if (name.contains('.')) name else "$name.txt"
                    docFile.createFile("text/plain", fileName)
                }
                if (created != null) {
                    refreshFileTree()
                    _snackbarEvent.emit("已创建：$name")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("创建失败：${e.message}")
            }
        }
    }

    fun createFolder(parentUri: Uri, name: String) {
        viewModelScope.launch {
            try {
                val docFile = DocumentFile.fromTreeUri(
                    getApplication(), parentUri
                ) ?: throw Exception("无法访问目录")
                val created = withContext(Dispatchers.IO) {
                    docFile.createDirectory(name)
                }
                if (created != null) {
                    refreshFileTree()
                    _snackbarEvent.emit("已创建文件夹：$name")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("创建失败：${e.message}")
            }
        }
    }

    fun deleteFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val docFile = DocumentFile.fromSingleUri(getApplication(), uri)
                    ?: throw Exception("无法访问文件")
                val deleted = withContext(Dispatchers.IO) { docFile.delete() }
                if (deleted) {
                    val removedIdx = _openTabs.value.indexOfFirst { it.uri?.toString() == uri.toString() }
                    val wasActive = removedIdx == _activeTabIndex.value
                    val tabs = _openTabs.value.filter { it.uri?.toString() != uri.toString() }
                    _openTabs.value = tabs
                    if (tabs.isEmpty()) {
                        _activeTabIndex.value = -1
                        _fileName.value = ""
                        _currentUri.value = null
                        _textContent.value = ""
                        savedText = ""
                        _isModified.value = false
                        undoManager.clear()
                    } else if (wasActive) {
                        val newIdx = removedIdx.coerceAtMost(tabs.size - 1)
                        switchToTab(newIdx)
                    } else if (_activeTabIndex.value > removedIdx) {
                        _activeTabIndex.value = _activeTabIndex.value - 1
                    }
                    refreshFileTree()
                    _snackbarEvent.emit("已删除")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("删除失败：${e.message}")
            }
        }
    }

    fun renameFile(uri: Uri, newName: String) {
        viewModelScope.launch {
            try {
                val docFile = DocumentFile.fromSingleUri(getApplication(), uri)
                    ?: throw Exception("无法访问文件")
                val renamed = withContext(Dispatchers.IO) { docFile.renameTo(newName) }
                if (renamed != null) {
                    val tabs = _openTabs.value.toMutableList()
                    val idx = tabs.indexOfFirst { it.uri?.toString() == uri.toString() }
                    if (idx >= 0) {
                        tabs[idx] = tabs[idx].copy(fileName = newName)
                        _openTabs.value = tabs
                        if (_activeTabIndex.value == idx) {
                            _fileName.value = newName
                        }
                    }
                    refreshFileTree()
                    _snackbarEvent.emit("已重命名为：$newName")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("重命名失败：${e.message}")
            }
        }
    }

    fun copyFileToClipboard(uri: Uri) {
        clipboardUri = uri
        viewModelScope.launch {
            _snackbarEvent.emit("已复制")
        }
    }

    fun pasteFile(targetDirUri: Uri) {
        val sourceUri = clipboardUri ?: return
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
                    ?: throw Exception("无法读取源文件")
                val targetDir = DocumentFile.fromTreeUri(context, targetDirUri)
                    ?: throw Exception("无法访问目标目录")

                withContext(Dispatchers.IO) {
                    val name = sourceDoc.name ?: "未命名"
                    val newFile = targetDir.createFile("text/plain", name)
                    if (newFile != null) {
                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            context.contentResolver.openOutputStream(newFile.uri, "wt")?.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                refreshFileTree()
                _snackbarEvent.emit("已粘贴")
            } catch (e: Exception) {
                _snackbarEvent.emit("粘贴失败：${e.message}")
            }
        }
    }

    fun addOrSwitchTab(uri: Uri?, fileName: String, content: String) {
        val tabs = _openTabs.value.toMutableList()
        val existingIdx = if (uri != null) {
            tabs.indexOfFirst { it.uri?.toString() == uri.toString() }
        } else -1

        if (existingIdx >= 0) {
            switchToTab(existingIdx)
            return
        }
        val maxTabs = settings.maxTabs.value
        if (tabs.size >= maxTabs) {
            val oldestUnmodified = tabs.indexOfFirst { !it.isModified }
            if (oldestUnmodified >= 0) {
                tabs.removeAt(oldestUnmodified)
            } else {
                viewModelScope.launch {
                    _snackbarEvent.emit("已达最大标签数（$maxTabs）")
                }
                return
            }
        }
        tabs.add(OpenTab(
            uri = uri,
            fileName = fileName,
            content = content,
            savedText = content
        ))
        _openTabs.value = tabs
        switchToTab(tabs.size - 1)
    }

    fun switchToTab(index: Int) {
        val tabs = _openTabs.value
        if (index < 0 || index >= tabs.size) return
        val currentActive = _activeTabIndex.value
        if (currentActive in tabs.indices) {
            val current = tabs[currentActive]
            _openTabs.value = tabs.toMutableList().apply {
                this[currentActive] = current.copy(
                    content = _textContent.value,
                    isModified = _isModified.value,
                    savedText = savedText
                )
            }
        }
        val target = _openTabs.value[index]
        _fileName.value = target.fileName
        _currentUri.value = target.uri
        _textContent.value = target.content
        savedText = target.savedText
        _isModified.value = target.isModified
        _activeTabIndex.value = index
        undoManager.clear()
        undoManager.record(target.content)
    }

    fun closeTab(index: Int) {
        val tabs = _openTabs.value.toMutableList()
        if (index < 0 || index >= tabs.size) return
        tabs.removeAt(index)
        _openTabs.value = tabs
        if (tabs.isEmpty()) {
            _fileName.value = ""
            _currentUri.value = null
            _textContent.value = ""
            savedText = ""
            _isModified.value = false
            _activeTabIndex.value = -1
            undoManager.clear()
        } else {
            val newIdx = if (index < tabs.size) index else tabs.size - 1
            switchToTab(newIdx)
        }
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        val tabs = _openTabs.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= tabs.size) return
        if (toIndex < 0 || toIndex >= tabs.size) return
        val item = tabs.removeAt(fromIndex)
        tabs.add(toIndex, item)
        _openTabs.value = tabs
        if (_activeTabIndex.value == fromIndex) {
            _activeTabIndex.value = toIndex
        }
    }

    fun toggleCaseSensitive() {
        _isCaseSensitive.value = !_isCaseSensitive.value
        if (_searchQuery.value.isNotEmpty()) performSearch(_searchQuery.value)
    }

    fun toggleWholeWord() {
        _isWholeWord.value = !_isWholeWord.value
        if (_searchQuery.value.isNotEmpty()) performSearch(_searchQuery.value)
    }

    fun startGlobalSearch(query: String) {
        if (query.isBlank()) return
        val rootUri = _fileTree.value.rootUri ?: return
        _globalSearchQuery.value = query
        _isGlobalSearching.value = true
        _globalSearchResults.value = emptyList()
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val found = mutableListOf<GlobalSearchResult>()
                    searchInDir(rootUri, query, found, 0)
                    found
                }
                _globalSearchResults.value = results
            } catch (e: Exception) {
                _snackbarEvent.emit("搜索出错：${e.message}")
            } finally {
                _isGlobalSearching.value = false
            }
        }
    }

    private fun searchInDir(dirUri: Uri, query: String, results: MutableList<GlobalSearchResult>, depth: Int) {
        if (depth > 10 || results.size >= 500) return
        val context = getApplication<Application>()
        val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return
        val children = dir.listFiles()
        for (child in children) {
            if (results.size >= 500) break
            if (child.isDirectory) {
                searchInDir(child.uri, query, results, depth + 1)
            } else {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in textExtensions) continue
                try {
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                        var lineNum = 0
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line ?: continue
                            lineNum++
                            val matchIdx = l.indexOf(query, ignoreCase = true)
                            if (matchIdx >= 0) {
                                results.add(GlobalSearchResult(
                                    fileUri = child.uri,
                                    fileName = name,
                                    lineNumber = lineNum,
                                    lineContent = l.trim(),
                                    matchStart = matchIdx
                                ))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun openFileFromGlobalSearch(uri: Uri, lineNumber: Int = 0) {
        openFile(uri)
        if (lineNumber > 0) {
            viewModelScope.launch {
                _pendingScrollToLine.emit(lineNumber)
            }
        }
    }

    fun performGlobalReplace(find: String, replace: String, onlyCurrentFile: Boolean) {
        if (find.isBlank()) return
        viewModelScope.launch {
            try {
                if (onlyCurrentFile) {
                    val uri = _currentUri.value ?: return@launch
                    val newContent = _textContent.value.replace(find, replace, ignoreCase = true)
                    withContext(Dispatchers.IO) {
                        val context = getApplication<Application>()
                        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                            os.write(newContent.toByteArray(Charsets.UTF_8))
                        }
                    }
                    _textContent.value = newContent
                    savedText = newContent
                    _isModified.value = false
                    undoManager.record(newContent)
                    val tabs = _openTabs.value.toMutableList()
                    val idx = tabs.indexOfFirst { it.uri?.toString() == uri.toString() }
                    if (idx >= 0) {
                        tabs[idx] = tabs[idx].copy(content = newContent, savedText = newContent, isModified = false)
                        _openTabs.value = tabs
                    }
                    _snackbarEvent.emit("替换完成")
                } else {
                    val rootUri = _fileTree.value.rootUri ?: return@launch
                    val context = getApplication<Application>()
                    val currentUri = _currentUri.value
                    withContext(Dispatchers.IO) {
                        replaceInDir(rootUri, find, replace, context, 0)
                    }
                    refreshFileTree()
                    val tabs = _openTabs.value
                    val newTabs = tabs.map { tab ->
                        val uri = tab.uri ?: return@map tab
                        if (currentUri != null && uri.toString() == currentUri.toString()) {
                            tab
                        } else {
                            try {
                                val newText = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                                    } ?: tab.content
                                }
                                if (newText != tab.content) {
                                    tab.copy(content = newText, savedText = newText, isModified = false)
                                } else {
                                    tab
                                }
                            } catch (_: Exception) {
                                tab
                            }
                        }
                    }
                    _openTabs.value = newTabs
                    val currentContent = _textContent.value
                    val newContent = currentContent.replace(find, replace, ignoreCase = true)
                    if (newContent != currentContent) {
                        _textContent.value = newContent
                        savedText = newContent
                        _isModified.value = false
                        undoManager.record(newContent)
                    }
                    _snackbarEvent.emit("全部替换完成")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("替换失败：${e.message}")
            }
        }
    }

    private fun replaceInDir(dirUri: Uri, find: String, replace: String, context: Application, depth: Int) {
        if (depth > 10) return
        val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                replaceInDir(child.uri, find, replace, context, depth + 1)
            } else {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in textExtensions) continue
                try {
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        val text = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                        val newText = text.replace(find, replace, ignoreCase = true)
                        if (newText != text) {
                            context.contentResolver.openOutputStream(child.uri, "wt")?.use { os ->
                                os.write(newText.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val context = getApplication<Application>()
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) return cursor.getString(nameIdx)
            }
        }
        return uri.lastPathSegment ?: "未知文件"
    }
}
