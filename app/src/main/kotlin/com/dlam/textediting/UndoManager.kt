package com.dlam.textediting

/**
 * Undo/Redo manager with unified history + index pointer.
 *
 * Thread-safe for main-thread-only use.
 *
 * Performance note: each [record] call stores a full-text snapshot. For very
 * large files (>500 KB) this can increase memory pressure. The [maxHistory]
 * cap (default 200) bounds worst-case usage.
 *
 * The try/finally pattern in [prepareUndo]/[finishUndoRedo] ensures the
 * internal flag is always cleared, even if the caller throws.
 */
class UndoManager(private val maxHistory: Int = 200) {

    private val history = mutableListOf<String>()
    private var index = -1
    private var isUndoingRedoing = false

    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < history.size - 1

    /**
     * Record a new state. Call this from [android.text.TextWatcher.afterTextChanged].
     * Skips recording if we're mid-undo/redo, or if the text hasn't actually changed
     * (fast length check first, then full equality only if lengths match).
     */
    fun record(text: String) {
        if (isUndoingRedoing) return
        // Fast-path: if lengths differ, text definitely changed
        val prev = if (index >= 0 && index < history.size) history[index] else null
        if (prev != null && prev.length == text.length && prev == text) return

        // Truncate any redo history past current position
        while (history.size > index + 1) {
            history.removeAt(history.lastIndex)
        }
        history.add(text)
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
        index = history.size - 1
    }

    /**
     * Prepare for undo. Returns the text to restore, or null.
     * MUST be followed by [finishUndoRedo] in a finally block.
     */
    fun prepareUndo(): String? {
        if (index <= 0) return null
        isUndoingRedoing = true
        index--
        return history[index]
    }

    /**
     * Prepare for redo. Returns the text to restore, or null.
     * MUST be followed by [finishUndoRedo] in a finally block.
     */
    fun prepareRedo(): String? {
        if (index >= history.size - 1) return null
        isUndoingRedoing = true
        index++
        return history[index]
    }

    /** Clear the isUndoingRedoing flag. Always call this after [prepareUndo] or [prepareRedo]. */
    fun finishUndoRedo() {
        isUndoingRedoing = false
    }

    fun clear() {
        history.clear()
        index = -1
        isUndoingRedoing = false
    }
}
