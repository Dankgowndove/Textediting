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

class MainViewModel(application: Application) : AndroidViewModel(application) {

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
                _fileName.value = name
                _currentUri.value = uri
                _textContent.value = text
                savedText = text
                _isModified.value = false
                undoManager.clear()
                undoManager.record(text)
                _snackbarEvent.emit("已打开：$name")
            } catch (e: Exception) {
                _snackbarEvent.emit("打开失败：${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createNewFile() {
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
                _snackbarEvent.emit("已保存")
            } catch (e: Exception) {
                _snackbarEvent.emit("保存失败：${e.message}")
            }
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
            _searchMatchCount.value = 0
            _currentSearchIndex.value = -1
            _searchPositions.value = emptyList()
            return
        }
        val positions = mutableListOf<Int>()
        var index = text.indexOf(query, 0)
        while (index >= 0) {
            positions.add(index)
            index = text.indexOf(query, index + 1)
        }
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
        _searchPositions.value = positions
        _searchMatchCount.value = positions.size
        if (positions.isEmpty()) {
            _currentSearchIndex.value = -1
            return
        }
        val next = (_currentSearchIndex.value + 1) % positions.size
        _currentSearchIndex.value = next
    }

    fun searchPrevious() {
        val query = _searchQuery.value
        if (query.isEmpty()) return
        val text = _textContent.value
        val positions = findAllPositions(text, query)
        _searchPositions.value = positions
        _searchMatchCount.value = positions.size
        if (positions.isEmpty()) {
            _currentSearchIndex.value = -1
            return
        }
        val prev = if (_currentSearchIndex.value > 0) _currentSearchIndex.value - 1 else positions.size - 1
        _currentSearchIndex.value = prev
    }

    private fun findAllPositions(text: String, query: String): List<Int> {
        val positions = mutableListOf<Int>()
        var index = text.indexOf(query, 0)
        while (index >= 0) {
            positions.add(index)
            index = text.indexOf(query, index + 1)
        }
        return positions
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
