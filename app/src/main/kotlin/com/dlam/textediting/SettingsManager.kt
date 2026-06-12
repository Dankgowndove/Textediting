package com.dlam.textediting

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 集中式设置管理器，基于 SharedPreferences 持久化
 *
 * 所有设置项以 StateFlow 形式暴露，UI 层通过 collectAsState() 响应式订阅。
 * 每个 setter 同时更新 StateFlow 和 SharedPreferences，确保数据一致性和实时生效。
 *
 * @param context Android Context，用于获取 SharedPreferences
 */
class SettingsManager(context: Context) {

    // SharedPreferences 实例，使用私有模式存储
    private val prefs: SharedPreferences =
        context.getSharedPreferences("textediting_settings", Context.MODE_PRIVATE)

    // ── 字体大小（sp）──
    private val _fontSize = MutableStateFlow(prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    fun setFontSize(sp: Int) {
        _fontSize.value = sp
        prefs.edit().putInt(KEY_FONT_SIZE, sp).apply()
    }

    // ── 最大标签页数 ──
    private val _maxTabs = MutableStateFlow(prefs.getInt(KEY_MAX_TABS, DEFAULT_MAX_TABS))
    val maxTabs: StateFlow<Int> = _maxTabs.asStateFlow()

    fun setMaxTabs(count: Int) {
        _maxTabs.value = count
        prefs.edit().putInt(KEY_MAX_TABS, count).apply()
    }

    // ── 是否显示行号 ──
    private val _showLineNumbers = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LINE_NUMBERS, true))
    val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

    fun setShowLineNumbers(show: Boolean) {
        _showLineNumbers.value = show
        prefs.edit().putBoolean(KEY_SHOW_LINE_NUMBERS, show).apply()
    }

    // ── 自动换行 ──
    private val _wordWrap = MutableStateFlow(prefs.getBoolean(KEY_WORD_WRAP, true))
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    fun setWordWrap(wrap: Boolean) {
        _wordWrap.value = wrap
        prefs.edit().putBoolean(KEY_WORD_WRAP, wrap).apply()
    }

    // ── 自动保存间隔（秒，0 表示禁用）──
    private val _autoSaveInterval = MutableStateFlow(prefs.getInt(KEY_AUTO_SAVE, 0))
    val autoSaveInterval: StateFlow<Int> = _autoSaveInterval.asStateFlow()

    fun setAutoSaveInterval(seconds: Int) {
        _autoSaveInterval.value = seconds
        prefs.edit().putInt(KEY_AUTO_SAVE, seconds).apply()
    }

    // ── 语法高亮 ──
    private val _syntaxHighlight = MutableStateFlow(prefs.getBoolean(KEY_SYNTAX_HIGHLIGHT, true))
    val syntaxHighlight: StateFlow<Boolean> = _syntaxHighlight.asStateFlow()

    fun setSyntaxHighlight(enabled: Boolean) {
        _syntaxHighlight.value = enabled
        prefs.edit().putBoolean(KEY_SYNTAX_HIGHLIGHT, enabled).apply()
    }

    // ── 括号匹配 ──
    private val _bracketMatching = MutableStateFlow(prefs.getBoolean(KEY_BRACKET_MATCHING, true))
    val bracketMatching: StateFlow<Boolean> = _bracketMatching.asStateFlow()

    fun setBracketMatching(enabled: Boolean) {
        _bracketMatching.value = enabled
        prefs.edit().putBoolean(KEY_BRACKET_MATCHING, enabled).apply()
    }

    // ── 当前行高亮 ──
    private val _highlightCurrentLine = MutableStateFlow(prefs.getBoolean(KEY_HIGHLIGHT_CURRENT_LINE, true))
    val highlightCurrentLine: StateFlow<Boolean> = _highlightCurrentLine.asStateFlow()

    fun setHighlightCurrentLine(enabled: Boolean) {
        _highlightCurrentLine.value = enabled
        prefs.edit().putBoolean(KEY_HIGHLIGHT_CURRENT_LINE, enabled).apply()
    }

    // ── 显示空白字符 ──
    private val _showWhitespace = MutableStateFlow(prefs.getBoolean(KEY_SHOW_WHITESPACE, false))
    val showWhitespace: StateFlow<Boolean> = _showWhitespace.asStateFlow()

    fun setShowWhitespace(enabled: Boolean) {
        _showWhitespace.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_WHITESPACE, enabled).apply()
    }

    /** 获取所有可选字体大小列表 */
    fun getAllFontSizes(): List<Int> = FONT_SIZE_OPTIONS
    /** 获取所有可选最大标签数列表 */
    fun getAllMaxTabs(): List<Int> = MAX_TABS_OPTIONS
    /** 获取所有可选自动保存间隔列表 */
    fun getAllAutoSaveIntervals(): List<Int> = AUTO_SAVE_OPTIONS

    companion object {
        // SharedPreferences 键名常量
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_MAX_TABS = "max_tabs"
        private const val KEY_SHOW_LINE_NUMBERS = "show_line_numbers"
        private const val KEY_WORD_WRAP = "word_wrap"
        private const val KEY_AUTO_SAVE = "auto_save_interval"
        private const val KEY_SYNTAX_HIGHLIGHT = "syntax_highlight"
        private const val KEY_BRACKET_MATCHING = "bracket_matching"
        private const val KEY_HIGHLIGHT_CURRENT_LINE = "highlight_current_line"
        private const val KEY_SHOW_WHITESPACE = "show_whitespace"

        /** 默认字体大小（sp） */
        const val DEFAULT_FONT_SIZE = 14
        /** 默认最大标签页数 */
        const val DEFAULT_MAX_TABS = 10

        /** 可选的字体大小值（sp） */
        val FONT_SIZE_OPTIONS = listOf(10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24)
        /** 可选的最大标签数 */
        val MAX_TABS_OPTIONS = listOf(5, 8, 10, 15, 20)
        /** 可选的自动保存间隔（0=关闭, 30秒, 1/2/5分钟） */
        val AUTO_SAVE_OPTIONS = listOf(0, 30, 60, 120, 300)
    }
}
