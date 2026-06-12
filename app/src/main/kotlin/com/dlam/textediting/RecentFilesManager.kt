package com.dlam.textediting

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 最近打开文件管理器
 *
 * 将最近打开的文件路径持久化到 SharedPreferences 中。
 * 最多保存 [MAX_RECENT] 条记录，使用简单的扁平列表格式：
 *
 * ```
 * encodedUri1|displayName1||encodedUri2|displayName2||...
 * ```
 *
 * 每次打开文件时，该文件会被移到列表最前面。
 *
 * @param context Android Context
 */
class RecentFilesManager(context: Context) {

    // 持久化存储
    private val prefs: SharedPreferences =
        context.getSharedPreferences("textediting_recent", Context.MODE_PRIVATE)

    // 最近文件列表的响应式状态
    private val _recentFiles = MutableStateFlow<List<RecentFile>>(emptyList())
    val recentFiles: StateFlow<List<RecentFile>> = _recentFiles.asStateFlow()

    init {
        // 初始化时从 SharedPreferences 加载
        _recentFiles.value = load()
    }

    /**
     * 记录一个被打开的文件，将其移到列表最前面
     *
     * @param uri 文件 URI
     * @param displayName 显示名称（文件名）
     */
    fun recordFile(uri: Uri, displayName: String) {
        val list = _recentFiles.value.toMutableList()
        // 移除已存在的同名 URI 条目（去重）
        list.removeAll { it.uri == uri }
        // 添加到列表最前面
        list.add(0, RecentFile(uri = uri, displayName = displayName))
        // 超出最大数量时截断
        while (list.size > MAX_RECENT) list.removeAt(list.lastIndex)
        _recentFiles.value = list
        save(list)
    }

    /** 移除单个文件记录 */
    fun remove(uri: Uri) {
        val list = _recentFiles.value.filter { it.uri != uri }
        _recentFiles.value = list
        save(list)
    }

    /** 清空所有最近文件记录 */
    fun clear() {
        _recentFiles.value = emptyList()
        prefs.edit().remove(KEY_RECENT).apply()
    }

    /**
     * 从 SharedPreferences 加载最近文件列表
     *
     * 存储格式：uri1|name1||uri2|name2||...
     */
    private fun load(): List<RecentFile> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("||")        // 按双竖线分割条目
            .mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)  // 按单竖线分割 URI 和名称
                if (parts.size == 2) {
                    try {
                        RecentFile(uri = Uri.parse(parts[0]), displayName = parts[1])
                    } catch (_: Exception) { null }  // 忽略解析失败的条目
                } else null
            }
    }

    /** 序列化并保存最近文件列表到 SharedPreferences */
    private fun save(list: List<RecentFile>) {
        val raw = list.joinToString("||") { "${it.uri}|${it.displayName}" }
        prefs.edit().putString(KEY_RECENT, raw).apply()
    }

    companion object {
        /** SharedPreferences 键名 */
        private const val KEY_RECENT = "recent_files"
        /** 最大记录数 */
        const val MAX_RECENT = 20
    }
}

/**
 * 最近文件条目数据类
 *
 * @property uri 文件 SAF URI
 * @property displayName 显示名称
 */
data class RecentFile(
    val uri: Uri,
    val displayName: String
)
