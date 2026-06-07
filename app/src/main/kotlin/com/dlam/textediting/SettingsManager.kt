package com.dlam.textediting

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized settings management using SharedPreferences.
 * Exposes settings as StateFlow for reactive UI updates.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("textediting_settings", Context.MODE_PRIVATE)

    // ── font size (sp) ──
    private val _fontSize = MutableStateFlow(prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    fun setFontSize(sp: Int) {
        _fontSize.value = sp
        prefs.edit().putInt(KEY_FONT_SIZE, sp).apply()
    }

    // ── max open tabs ──
    private val _maxTabs = MutableStateFlow(prefs.getInt(KEY_MAX_TABS, DEFAULT_MAX_TABS))
    val maxTabs: StateFlow<Int> = _maxTabs.asStateFlow()

    fun setMaxTabs(count: Int) {
        _maxTabs.value = count
        prefs.edit().putInt(KEY_MAX_TABS, count).apply()
    }

    // ── show line numbers ──
    private val _showLineNumbers = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LINE_NUMBERS, true))
    val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

    fun setShowLineNumbers(show: Boolean) {
        _showLineNumbers.value = show
        prefs.edit().putBoolean(KEY_SHOW_LINE_NUMBERS, show).apply()
    }

    // ── word wrap ──
    private val _wordWrap = MutableStateFlow(prefs.getBoolean(KEY_WORD_WRAP, true))
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    fun setWordWrap(wrap: Boolean) {
        _wordWrap.value = wrap
        prefs.edit().putBoolean(KEY_WORD_WRAP, wrap).apply()
    }

    // ── auto-save interval (seconds, 0 = disabled) ──
    private val _autoSaveInterval = MutableStateFlow(prefs.getInt(KEY_AUTO_SAVE, 0))
    val autoSaveInterval: StateFlow<Int> = _autoSaveInterval.asStateFlow()

    fun setAutoSaveInterval(seconds: Int) {
        _autoSaveInterval.value = seconds
        prefs.edit().putInt(KEY_AUTO_SAVE, seconds).apply()
    }

    fun getAllFontSizes(): List<Int> = FONT_SIZE_OPTIONS
    fun getAllMaxTabs(): List<Int> = MAX_TABS_OPTIONS
    fun getAllAutoSaveIntervals(): List<Int> = AUTO_SAVE_OPTIONS

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_MAX_TABS = "max_tabs"
        private const val KEY_SHOW_LINE_NUMBERS = "show_line_numbers"
        private const val KEY_WORD_WRAP = "word_wrap"
        private const val KEY_AUTO_SAVE = "auto_save_interval"

        const val DEFAULT_FONT_SIZE = 14
        const val DEFAULT_MAX_TABS = 10

        val FONT_SIZE_OPTIONS = listOf(10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24)
        val MAX_TABS_OPTIONS = listOf(5, 8, 10, 15, 20)
        val AUTO_SAVE_OPTIONS = listOf(0, 30, 60, 120, 300)
    }
}
