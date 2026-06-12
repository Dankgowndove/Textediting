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

/**
 * 主 ViewModel — 应用所有状态的中央枢纽
 *
 * 采用 MVVM 架构，通过 StateFlow 向 Compose UI 暴露响应式状态。
 * 所有文件 I/O 通过 Android SAF（DocumentFile）执行，在主线程之外运行。
 *
 * ## 核心职责
 * - 文件操作：打开、保存、另存为、新建
 * - 标签页管理：添加、切换、关闭、移动
 * - 文本搜索：本地搜索（大小写/全字匹配）+ 全局目录搜索
 * - 文件树：延迟加载、缓存、展开/折叠、CRUD
 * - 撤销/重做：委托给 UndoManager
 * - 自动保存：可配置间隔的定时器
 * - 语法高亮：后台分析 + 主线程应用
 * - 全局替换：单文件/全目录替换
 *
 * @param application Android Application 实例
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── 子管理器（懒加载）──
    val settings by lazy { SettingsManager(application) }
    val recentFiles by lazy { RecentFilesManager(application) }

    // ════════════════════════════════════════════
    //  编辑器状态
    // ════════════════════════════════════════════

    private val _textContent = MutableStateFlow("")
    /** 当前编辑器文本内容 */
    val textContent: StateFlow<String> = _textContent.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    /** 是否有未保存的修改 */
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    private val _currentUri = MutableStateFlow<Uri?>(null)
    /** 当前文件的 SAF URI */
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _fileName = MutableStateFlow("")
    /** 当前文件名 */
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    /** 是否正在加载文件 */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ════════════════════════════════════════════
    //  搜索状态
    // ════════════════════════════════════════════

    private val _searchQuery = MutableStateFlow("")
    /** 搜索查询文本 */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    /** 搜索栏是否可见 */
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _searchMatchCount = MutableStateFlow(0)
    /** 搜索匹配总数 */
    val searchMatchCount: StateFlow<Int> = _searchMatchCount.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    /** 当前匹配项索引（-1 表示无匹配） */
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    private val _searchPositions = MutableStateFlow<List<Int>>(emptyList())
    /** 所有匹配位置列表 */
    val searchPositions: StateFlow<List<Int>> = _searchPositions.asStateFlow()

    // ════════════════════════════════════════════
    //  撤销/重做
    // ════════════════════════════════════════════

    /** 撤销/重做管理器实例 */
    val undoManager = UndoManager()
    /** 上次保存时的文本内容，用于比较是否修改 */
    private var savedText: String = ""

    val canUndo: Boolean get() = undoManager.canUndo
    val canRedo: Boolean get() = undoManager.canRedo

    // ════════════════════════════════════════════
    //  事件和通知
    // ════════════════════════════════════════════

    private val _snackbarEvent = MutableSharedFlow<String>()
    /** Snackbar 消息事件流 */
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val _pendingScrollToLine = MutableSharedFlow<Int>()
    /** 跳转到行事件流（从全局搜索触发） */
    val pendingScrollToLine: SharedFlow<Int> = _pendingScrollToLine.asSharedFlow()

    // ════════════════════════════════════════════
    //  文件树状态
    // ════════════════════════════════════════════

    private val _fileTree = MutableStateFlow(FileTreeState())
    /** 文件树 UI 完整状态 */
    val fileTree: StateFlow<FileTreeState> = _fileTree.asStateFlow()

    // ════════════════════════════════════════════
    //  标签页状态
    // ════════════════════════════════════════════

    private val _openTabs = MutableStateFlow<List<OpenTab>>(emptyList())
    /** 打开的标签页列表 */
    val openTabs: StateFlow<List<OpenTab>> = _openTabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(-1)
    /** 当前活跃标签索引（-1 表示无） */
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    // ════════════════════════════════════════════
    //  全局搜索状态
    // ════════════════════════════════════════════

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _globalSearchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val globalSearchResults: StateFlow<List<GlobalSearchResult>> = _globalSearchResults.asStateFlow()

    private val _isGlobalSearching = MutableStateFlow(false)
    val isGlobalSearching: StateFlow<Boolean> = _isGlobalSearching.asStateFlow()

    // ════════════════════════════════════════════
    //  搜索选项
    // ════════════════════════════════════════════

    private val _isCaseSensitive = MutableStateFlow(false)
    val isCaseSensitive: StateFlow<Boolean> = _isCaseSensitive.asStateFlow()

    private val _isWholeWord = MutableStateFlow(false)
    val isWholeWord: StateFlow<Boolean> = _isWholeWord.asStateFlow()

    // ════════════════════════════════════════════
    //  语法高亮状态
    // ════════════════════════════════════════════

    /** 语法高亮是否对当前文件生效 */
    private val _syntaxHighlightingActive = MutableStateFlow(false)
    val syntaxHighlightingActive: StateFlow<Boolean> = _syntaxHighlightingActive.asStateFlow()

    // ── 协程 Job 引用（用于取消正在进行的操作）──
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var autoSaveJob: kotlinx.coroutines.Job? = null
    private var highlightJob: kotlinx.coroutines.Job? = null

    /** 剪贴板 URI（文件复制粘贴使用，非系统剪贴板） */
    private var clipboardUri: Uri? = null

    // ── 文件树缓存：目录 URI → (子 URI, 名称, 是否目录) 三元组列表 ──
    private val dirCache = mutableMapOf<Uri, List<Triple<Uri, String, Boolean>>>()

    /** 清空文件树缓存（在手动刷新或根目录变更时调用） */
    fun clearFileTreeCache() {
        dirCache.clear()
    }

    /**
     * 支持的文本文件扩展名集合
     * 用于全局搜索和替换过滤非文本文件
     */
    private val textExtensions = setOf(
        "txt", "md", "json", "xml", "csv", "ini", "cfg", "log",
        "yml", "yaml", "java", "kt", "html", "htm", "css", "js",
        "ts", "py", "sh", "bat", "properties", "gradle", "kts",
        "c", "cpp", "h", "hpp", "go", "rs", "rb", "php", "sql"
    )

    // ════════════════════════════════════════════
    //  文件 I/O 操作
    // ════════════════════════════════════════════

    /**
     * 通过 SAF 打开文件
     *
     * 在 IO 线程读取文件内容，UTF-8 编码。成功后更新所有相关状态：
     * 文件名、URI、文本内容、修改标记、撤销历史、标签页、最近文件。
     *
     * @param uri 文件的 SAF URI
     */
    fun openFile(uri: Uri) {
        if (_isLoading.value) return  // 防止重复加载
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                // IO 线程读取文件
                val (text, name) = withContext(Dispatchers.IO) {
                    val t = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
                    } ?: throw Exception("无法打开文件")
                    val n = getFileName(uri)
                    Pair(t, n)
                }
                // 重置搜索状态
                dismissSearch()
                // 更新编辑器状态
                _fileName.value = name
                _currentUri.value = uri
                _textContent.value = text
                savedText = text
                _isModified.value = false
                undoManager.clear()
                undoManager.record(text)
                // 标签页管理
                addOrSwitchTab(uri, name, text)
                recentFiles.recordFile(uri, name)
                _snackbarEvent.emit("已打开：$name")
            } catch (e: Exception) {
                _snackbarEvent.emit("打开失败：${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 创建无标题新文件 */
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

    /**
     * 文本变更处理
     *
     * 由 TextWatcher.afterTextChanged 调用。
     * 更新文本内容、修改标记和撤销历史。
     */
    fun onTextChanged(newText: String) {
        _textContent.value = newText
        _isModified.value = newText != savedText
        undoManager.record(newText)
    }

    // ════════════════════════════════════════════
    //  [Bug #5 修复] 撤销/重做完成后同步 ViewModel 状态
    // ════════════════════════════════════════════

    /**
     * 撤销/重做操作将文本写入 EditText 后，调用此方法同步 ViewModel 状态。
     *
     * 原问题：撤销/重做只调用了 et.setText()，但 _textContent 和 _isModified
     * 没有同步更新，导致：
     *   1. 工具栏保存按钮状态（enabled）不更新
     *   2. 标签页圆点修改指示器不更新
     *   3. 语法高亮不触发
     *   4. 自动保存判断错误
     *
     * 调用时机：在 prepareUndo/prepareRedo + et.setText() 之后、finishUndoRedo() 之前调用。
     */
    fun onUndoRedoApplied(text: String) {
        _textContent.value = text
        _isModified.value = text != savedText
        // 同步更新当前活跃标签页状态
        val tabs = _openTabs.value.toMutableList()
        val idx = _activeTabIndex.value
        if (idx in tabs.indices) {
            tabs[idx] = tabs[idx].copy(
                content = text,
                isModified = text != savedText
            )
            _openTabs.value = tabs
        }
    }

    /** 保存当前文件到原始 URI */
    fun saveFile() {
        viewModelScope.launch {
            val uri = _currentUri.value ?: return@launch
            try {
                val text = _textContent.value
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    // "wt" 模式：写入 + 截断
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

    /** 另存为新 URI */
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

    // ════════════════════════════════════════════
    //  标签页管理
    // ════════════════════════════════════════════

    /** 保存后更新当前标签页的 savedText */
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

    /** 另存为后替换当前标签页信息 */
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

    /**
     * 添加或切换到标签页
     *
     * 如果 URI 对应的标签已存在，直接切换；否则添加新标签。
     * 超出最大标签数时，优先淘汰未修改的标签；全部已修改则拒绝。
     */
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
            // 查找第一个未修改的标签进行淘汰
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

    /**
     * 切换到指定索引的标签页
     *
     * 先将当前编辑器状态保存到旧标签的 OpenTab 中，
     * 再从目标标签恢复文件名、URI、文本内容和修改状态。
     */
    fun switchToTab(index: Int) {
        val tabs = _openTabs.value
        if (index < 0 || index >= tabs.size) return
        // 保存当前标签页状态
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
        // 从目标标签页恢复状态
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

    /** 关闭指定索引的标签页 */
    fun closeTab(index: Int) {
        val tabs = _openTabs.value.toMutableList()
        if (index < 0 || index >= tabs.size) return
        tabs.removeAt(index)
        _openTabs.value = tabs
        if (tabs.isEmpty()) {
            // 所有标签已关闭，重置为初始状态
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

    /** 移动标签页位置（预留拖拽排序功能） */
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

    // ════════════════════════════════════════════
    //  本地搜索
    // ════════════════════════════════════════════

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

    /** 执行搜索：在全文文本中查找所有匹配位置 */
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

    /** 获取当前搜索匹配位置和长度 */
    fun getSearchPosition(): Pair<Int, Int>? {
        val positions = _searchPositions.value
        val query = _searchQuery.value
        val idx = _currentSearchIndex.value
        if (positions.isEmpty() || query.isEmpty() || idx < 0) return null
        return Pair(positions[idx], query.length)
    }

    // [Bug #8 修复] searchNext/searchPrevious 直接使用缓存的 _searchPositions，
    // 不再重复调用 findAllPositions 全文搜索。只有 onSearchQueryChanged 触发的
    // performSearch 会重新搜索。对于大文件（>1MB）每次按键都整篇搜索会卡顿。
    fun searchNext() {
        val positions = _searchPositions.value
        if (positions.isEmpty()) return
        val current = _currentSearchIndex.value
        _currentSearchIndex.value = if (current < 0) 0 else (current + 1) % positions.size
    }

    /** 跳转到上一个搜索匹配（循环） */
    fun searchPrevious() {
        val positions = _searchPositions.value
        if (positions.isEmpty()) return
        val current = _currentSearchIndex.value
        _currentSearchIndex.value = if (current <= 0) positions.size - 1 else current - 1
    }

    /**
     * 在全文文本中查找所有匹配位置
     *
     * 支持大小写敏感和全字匹配两种过滤模式。
     *
     * @param text 全文文本
     * @param query 搜索词
     * @return 所有匹配的起始位置列表
     */
    private fun findAllPositions(text: String, query: String): List<Int> {
        val positions = mutableListOf<Int>()
        if (query.isEmpty()) return positions
        val caseSensitive = _isCaseSensitive.value
        val searchText = if (caseSensitive) text else text.lowercase()
        val searchQuery = if (caseSensitive) query else query.lowercase()
        val wholeWord = _isWholeWord.value
        var index = searchText.indexOf(searchQuery, 0)
        while (index >= 0) {
            // 全字匹配：检查匹配前后的字符是否为字母/数字
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

    /** 切换大小写敏感搜索选项，如有查询则重新搜索 */
    fun toggleCaseSensitive() {
        _isCaseSensitive.value = !_isCaseSensitive.value
        if (_searchQuery.value.isNotEmpty()) performSearch(_searchQuery.value)
    }

    /** 切换全字匹配搜索选项，如有查询则重新搜索 */
    fun toggleWholeWord() {
        _isWholeWord.value = !_isWholeWord.value
        if (_searchQuery.value.isNotEmpty()) performSearch(_searchQuery.value)
    }

    // ════════════════════════════════════════════
    //  文件树操作
    // ════════════════════════════════════════════

    /**
     * 选择文件树根目录
     *
     * 获取持久化 URI 权限（重启后仍然有效），清空缓存并刷新整个文件树。
     */
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

    /** 完整刷新文件树 — 清空缓存并重建整个树结构 */
    fun refreshFileTree() {
        val rootUri = _fileTree.value.rootUri ?: return
        refreshJob?.cancel()
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

    /**
     * 获取或加载目录列表（带缓存）
     *
     * 排序策略：目录优先 → 按名称不区分大小写排序
     */
    private fun getOrLoadDirListing(uri: Uri): List<Triple<Uri, String, Boolean>> {
        dirCache[uri]?.let { return it }
        val context = getApplication<Application>()
        val docFile = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
        if (docFile == null || !docFile.exists()) return emptyList()
        val children = docFile.listFiles().toList()
        val sorted = children.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }
                .thenBy { it.name?.lowercase() ?: "" }
        )
        val listing = sorted.map { Triple(it.uri, it.name ?: "未知", it.isDirectory) }
        dirCache[uri] = listing
        return listing
    }

    /** 递归构建文件树节点列表，最大深度 10 层 */
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

    /**
     * 切换目录展开/折叠 — 增量更新节点列表，无需全量重建
     */
    fun toggleExpandDir(uri: Uri) {
        val currentState = _fileTree.value
        val currentExpanded = currentState.expandedUris.toMutableSet()
        val isExpanding = !currentExpanded.contains(uri)

        if (isExpanding) {
            // ── 展开目录 ──
            currentExpanded.add(uri)
            _fileTree.value = currentState.copy(expandedUris = currentExpanded, isLoading = true)

            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                try {
                    val newNodes = currentState.nodes.toMutableList()
                    val parentIdx = newNodes.indexOfFirst { it.uri == uri }
                    if (parentIdx >= 0) {
                        val parentDepth = newNodes[parentIdx].depth
                        // 移除已存在的子节点（防御性编程）
                        var removeIdx = parentIdx + 1
                        while (removeIdx < newNodes.size && newNodes[removeIdx].depth > parentDepth) {
                            removeIdx++
                        }
                        if (removeIdx > parentIdx + 1) {
                            newNodes.subList(parentIdx + 1, removeIdx).clear()
                        }
                        // 加载目录子节点
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
            // ── 折叠目录：从节点列表中移除所有子孙节点 ──
            currentExpanded.remove(uri)
            // 同时移除所有后代 URI 的展开状态
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

    // ════════════════════════════════════════════
    //  文件/目录 CRUD 操作
    // ════════════════════════════════════════════

    /** 在指定目录中创建新文件 */
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

    /** 在指定目录中创建新文件夹 */
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

    /**
     * 删除文件/目录
     *
     * 如果被删除的文件当前在标签页中打开，会自动关闭对应的标签页。
     * 如果被关闭的是当前活跃标签，则切换到相邻标签。
     */
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

    /** 重命名文件/目录 */
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

    /** 复制文件到内部剪贴板（非系统剪贴板） */
    fun copyFileToClipboard(uri: Uri) {
        clipboardUri = uri
        viewModelScope.launch {
            _snackbarEvent.emit("已复制")
        }
    }

    /** 从内部剪贴板粘贴文件到目标目录 */
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

    // ════════════════════════════════════════════
    //  全局搜索与替换
    // ════════════════════════════════════════════

    /**
     * 在整个工作区目录中搜索文本内容
     *
     * 递归遍历所有文本文件，忽略大小写。
     * 安全限制：最大深度 10 层，最多 500 个结果。
     */
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

    /** 递归搜索目录中的文本文件 */
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
                if (ext !in textExtensions) continue  // 跳过非文本文件
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
                } catch (_: Exception) {}  // 忽略单个文件的读取错误
            }
        }
    }

    /** 从全局搜索结果打开文件，并可选地跳转到指定行 */
    fun openFileFromGlobalSearch(uri: Uri, lineNumber: Int = 0) {
        openFile(uri)
        if (lineNumber > 0) {
            viewModelScope.launch {
                _pendingScrollToLine.emit(lineNumber)
            }
        }
    }

    /**
     * 执行全局替换
     *
     * @param find 查找内容
     * @param replace 替换为
     * @param onlyCurrentFile 仅替换当前文件（false = 全局替换）
     */
    fun performGlobalReplace(find: String, replace: String, onlyCurrentFile: Boolean) {
        if (find.isBlank()) return
        viewModelScope.launch {
            try {
                if (onlyCurrentFile) {
                    // ── 仅当前文件 ──
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
                    // ── 全局替换 ──
                    val rootUri = _fileTree.value.rootUri ?: return@launch
                    val context = getApplication<Application>()
                    val currentUri = _currentUri.value
                    withContext(Dispatchers.IO) {
                        replaceInDir(rootUri, find, replace, context, 0)
                    }
                    refreshFileTree()
                    // 刷新所有打开的标签页内容
                    val tabs = _openTabs.value
                    val newTabs = tabs.map { tab ->
                        val uri = tab.uri ?: return@map tab
                        if (currentUri != null && uri.toString() == currentUri.toString()) {
                            tab  // 当前文件已在上面单独处理
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

    /** 递归在目录中执行文本替换 */
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

    // ════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════

    /**
     * 从 SAF URI 获取显示名称
     *
     * 优先查询 ContentResolver 的 DISPLAY_NAME 列，
     * 如果不可用则回退到 URI 的 lastPathSegment。
     */
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

    // ════════════════════════════════════════════
    //  自动保存
    // ════════════════════════════════════════════

    /**
     * 根据当前 [SettingsManager.autoSaveInterval] 启动或重启自动保存计时器
     *
     * 当间隔 ≤ 0 或内容未修改时，不启动计时器。
     * 应在 LaunchedEffect 中响应间隔和修改状态的变化。
     */
    fun scheduleAutoSave() {
        cancelAutoSave()
        val interval = settings.autoSaveInterval.value
        if (interval <= 0 || !_isModified.value) return
        autoSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(interval * 1000L)
            if (_isModified.value && _currentUri.value != null) {
                saveFile()
            }
        }
    }

    fun cancelAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    // ════════════════════════════════════════════
    //  语法高亮
    // ════════════════════════════════════════════

    /** 高亮结果就绪事件 */
    private val _highlightsReady = MutableSharedFlow<Unit>()
    val highlightsReady: SharedFlow<Unit> = _highlightsReady.asSharedFlow()

    /**
     * 触发语法高亮分析
     *
     * 在后台线程执行分析，通过 300ms 防抖避免频繁输入时重复计算。
     * 分析完成后通过 [_highlightsReady] 事件通知 UI 线程应用结果。
     */
    fun triggerSyntaxHighlight(text: CharSequence, fileName: String, darkMode: Boolean) {
        val rules = com.dlam.textediting.editor.SyntaxHighlighter.detectLanguage(fileName)
        if (rules == null || !settings.syntaxHighlight.value) {
            _syntaxHighlightingActive.value = false
            return
        }
        _syntaxHighlightingActive.value = true
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                kotlinx.coroutines.delay(300) // 防抖：300ms 内无新输入再分析
                val commands = com.dlam.textediting.editor.SyntaxHighlighter.analyse(text, rules)
                withContext(Dispatchers.Main) {
                    _lastHighlightCommands = commands
                    _lastHighlightDark = darkMode
                    _highlightsReady.emit(Unit)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 取消是预期行为（防抖逻辑），静默处理
            } catch (_: Exception) {
                _syntaxHighlightingActive.value = false
            }
        }
    }

    /** 缓存最近一次的高亮指令 */
    private var _lastHighlightCommands: List<com.dlam.textediting.editor.SyntaxHighlighter.SpanCommand> = emptyList()
    private var _lastHighlightDark: Boolean = false

    /**
     * 将已准备好的高亮指令应用到 Spannable
     *
     * 先清除旧的高亮 Span，再应用新的指令。
     */
    fun applyHighlightIfReady(spannable: android.text.Spannable, darkMode: Boolean) {
        if (_lastHighlightCommands.isEmpty()) return
        com.dlam.textediting.editor.SyntaxHighlighter.clearSpans(spannable)
        com.dlam.textediting.editor.SyntaxHighlighter.applyTo(spannable, _lastHighlightCommands, _lastHighlightDark)
    }
}
