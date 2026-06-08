package com.dlam.textediting.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Gutter and editor colour definitions for light and dark themes.
 */
internal data class GutterColors(
    val background: Int,
    val divider: Int,
    val lineNumber: Int
)

internal data class EditorColors(
    val text: Int,
    val background: Int,
    val accent: Int,
    val highlight: Int
)

fun gutterColors(isDark: Boolean): GutterColors = if (isDark) {
    GutterColors(
        background = 0xFF1A1A1A.toInt(),
        divider = 0xFF3A3A3A.toInt(),
        lineNumber = 0xFFAAAAAA.toInt()
    )
} else {
    GutterColors(
        background = 0xFFF0F0F0.toInt(),
        divider = 0xFFD0D0D0.toInt(),
        lineNumber = 0xFF888888.toInt()
    )
}

fun editorColors(isDark: Boolean): EditorColors = if (isDark) {
    EditorColors(
        text = 0xFFEEEEEE.toInt(),
        background = 0xFF121212.toInt(),
        accent = 0xFFBB86FC.toInt(),
        highlight = 0x44FFFFFF.toInt()
    )
} else {
    EditorColors(
        text = 0xFF1A1A1A.toInt(),
        background = 0xFFFFFFFF.toInt(),
        accent = 0xFF6650A4.toInt(),
        highlight = 0x33000000.toInt()
    )
}
