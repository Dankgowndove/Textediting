package com.dlam.textediting

/**
 * Undo/Redo manager with unified history + index pointer.
 * 
 * Thread-safe for main-thread-only use: TextWatcher.afterTextChanged fires
 * synchronously during setText(), so the isUndoingRedoing flag protects
 * against re-recording during undo/redo.
 */
class UndoManager(private val maxHistory: Int = 200) {
    private val history = mutableListOf<String>()
    private var index = -1
    private var isUndoingRedoing = false

    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < history.size - 1

    fun record(text: String) {
        if (isUndoingRedoing) return
        if (index >= 0 && history[index] == text) return
        while (history.size > index + 1) {
            history.removeAt(history.lastIndex)
        }
        history.add(text)
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
        index = history.size - 1
    }

    fun prepareUndo(): String? {
        if (index <= 0) return null
        isUndoingRedoing = true
        index--
        return history[index]
    }

    fun prepareRedo(): String? {
        if (index >= history.size - 1) return null
        isUndoingRedoing = true
        index++
        return history[index]
    }

    fun finishUndoRedo() {
        isUndoingRedoing = false
    }

    fun clear() {
        history.clear()
        index = -1
        isUndoingRedoing = false
    }
}
