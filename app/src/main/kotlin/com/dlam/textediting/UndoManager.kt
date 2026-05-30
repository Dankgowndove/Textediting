package com.dlam.textediting

class UndoManager(private val maxHistory: Int = 100) {
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    fun saveState(text: String) {
        if (undoStack.lastOrNull() != text) {
            undoStack.add(text)
            if (undoStack.size > maxHistory) {
                undoStack.removeFirst()
            }
        }
        redoStack.clear()
    }

    fun undo(currentText: String): String? {
        if (undoStack.isEmpty()) return null
        redoStack.add(currentText)
        return undoStack.removeLast()
    }

    fun redo(currentText: String): String? {
        if (redoStack.isEmpty()) return null
        undoStack.add(currentText)
        return redoStack.removeLast()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}
