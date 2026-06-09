package com.dlam.textediting

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists recently-opened file paths in SharedPreferences.
 *
 * Stores up to [MAX_RECENT] entries as a simple flat list:
 *   encodedUri1|displayName1||encodedUri2|displayName2
 */
class RecentFilesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("textediting_recent", Context.MODE_PRIVATE)

    private val _recentFiles = MutableStateFlow<List<RecentFile>>(emptyList())
    val recentFiles: StateFlow<List<RecentFile>> = _recentFiles.asStateFlow()

    init {
        _recentFiles.value = load()
    }

    /** Record a file that was opened. Moves it to the front of the list. */
    fun recordFile(uri: Uri, displayName: String) {
        val list = _recentFiles.value.toMutableList()
        // Remove existing entry for this URI
        list.removeAll { it.uri == uri }
        // Add to front
        list.add(0, RecentFile(uri = uri, displayName = displayName))
        // Trim
        while (list.size > MAX_RECENT) list.removeAt(list.lastIndex)
        _recentFiles.value = list
        save(list)
    }

    /** Remove a single entry. */
    fun remove(uri: Uri) {
        val list = _recentFiles.value.filter { it.uri != uri }
        _recentFiles.value = list
        save(list)
    }

    /** Clear all recent entries. */
    fun clear() {
        _recentFiles.value = emptyList()
        prefs.edit().remove(KEY_RECENT).apply()
    }

    private fun load(): List<RecentFile> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("||")
            .mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) {
                    try {
                        RecentFile(uri = Uri.parse(parts[0]), displayName = parts[1])
                    } catch (_: Exception) { null }
                } else null
            }
    }

    private fun save(list: List<RecentFile>) {
        val raw = list.joinToString("||") { "${it.uri}|${it.displayName}" }
        prefs.edit().putString(KEY_RECENT, raw).apply()
    }

    companion object {
        private const val KEY_RECENT = "recent_files"
        const val MAX_RECENT = 20
    }
}

data class RecentFile(
    val uri: Uri,
    val displayName: String
)
