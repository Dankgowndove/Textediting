package com.dlam.textediting

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 主 ViewModel — 应用状态的中央枢纽
 *
 * 采用 MVVM 架构，通过 StateFlow 向 Compose UI 暴露响应式状态。
 * 所有文件 I/O 通过 Android SAF 执行，在主线程之外运行。
 *
 * ## 核心职责
 * - 文件操作：打开、保存、另存为、新建
 * - 文本搜索：查找、上一个/下一个
 * - 跳转到行
 *
 * @param application Android Application 实例
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── 子管理器（懒加载）──
    val settings by lazy { SettingsManager(application) }

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
    //  事件和通知
    // ════════════════════════════════════════════

    private val _snackbarEvent = MutableSharedFlow<String>()
    /** Snackbar 消息事件流 */
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    // ════════════════════════════════════════════
    //  文件 I/O 操作
    // ════════════════════════════════════════════

    /**
     * 通过 SAF 打开文件
     *
     * 在 IO 线程读取文件内容，UTF-8 编码。成功后更新文件名、URI、
     * 文本内容与修改标记。
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
                _isModified.value = false
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
        _isModified.value = false
    }

    /**
     * 文本变更处理
     *
     * 由 TextWatcher.afterTextChanged 调用。
     * 更新文本内容并标记为已修改。
     */
    fun onTextChanged(newText: String) {
        _textContent.value = newText
        // 编辑即标记为已修改（无需全文比较，撤销历史已移除）
        _isModified.value = true
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
                _isModified.value = false
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
                _isModified.value = false
                _snackbarEvent.emit("已保存")
            } catch (e: Exception) {
                _snackbarEvent.emit("保存失败：${e.message}")
            }
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

    /**
     * searchNext/searchPrevious 直接使用缓存的 _searchPositions，
     * 不再重复调用 findAllPositions 全文搜索。只有 onSearchQueryChanged 触发的
     * performSearch 会重新搜索。对于大文件（>1MB）每次按键都整篇搜索会卡顿。
     */
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
     * @param text 全文文本
     * @param query 搜索词
     * @return 所有匹配的起始位置列表
     */
    private fun findAllPositions(text: String, query: String): List<Int> {
        val positions = mutableListOf<Int>()
        if (query.isEmpty()) return positions
        var index = text.indexOf(query, 0)
        while (index >= 0) {
            positions.add(index)
            index = text.indexOf(query, index + 1)
        }
        return positions
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
}