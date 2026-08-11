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

    // ── 主题模式（0=跟随系统, 1=浅色, 2=深色）──
    private val _darkThemeMode = MutableStateFlow(prefs.getInt(KEY_DARK_THEME_MODE, DEFAULT_DARK_THEME_MODE))
    val darkThemeMode: StateFlow<Int> = _darkThemeMode.asStateFlow()

    fun setDarkThemeMode(mode: Int) {
        _darkThemeMode.value = mode
        prefs.edit().putInt(KEY_DARK_THEME_MODE, mode).apply()
    }

    /** 获取所有可选字体大小列表 */
    fun getAllFontSizes(): List<Int> = FONT_SIZE_OPTIONS

    companion object {
        // SharedPreferences 键名常量
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_SHOW_LINE_NUMBERS = "show_line_numbers"
        private const val KEY_WORD_WRAP = "word_wrap"
        private const val KEY_DARK_THEME_MODE = "dark_theme_mode"

        /** 默认字体大小（sp） */
        const val DEFAULT_FONT_SIZE = 14
        /** 默认主题模式：跟随系统 */
        const val DEFAULT_DARK_THEME_MODE = 0

        /** 可选的字体大小值（sp） */
        val FONT_SIZE_OPTIONS = listOf(10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24)
    }
}