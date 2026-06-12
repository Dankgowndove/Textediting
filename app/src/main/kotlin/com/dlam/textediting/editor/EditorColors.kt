package com.dlam.textediting.editor

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 编辑器组件独立配色方案
 *
 * 编辑器（LinedEditText）和行号栏使用自己独立的配色系统，
 * 但当 [colorScheme] 参数非空时，优先从 Material 3 主题派生颜色，
 * 确保代码编辑区域与应用程序其他部分的视觉一致性。
 *
 * 向后兼容：当 [colorScheme] 为 null 时使用内置硬编码颜色。
 */

/**
 * 行号栏配色
 */
internal data class GutterColors(
    val background: Int,
    val divider: Int,
    val lineNumber: Int
)

/**
 * 编辑器配色
 */
internal data class EditorColors(
    val text: Int,
    val background: Int,
    val accent: Int,
    val highlight: Int
)

/**
 * 获取行号栏配色
 *
 * @param isDark 是否暗色模式
 * @param colorScheme Material 3 配色方案，null 时使用内置颜色
 */
internal fun gutterColors(isDark: Boolean, colorScheme: ColorScheme? = null): GutterColors {
    if (colorScheme != null) {
        // 从 Material 3 主题派生行号栏颜色
        return GutterColors(
            background = colorScheme.surfaceVariant.toArgb(),
            divider = colorScheme.outlineVariant.toArgb(),
            lineNumber = colorScheme.onSurfaceVariant.copy(alpha = 0.6f).toArgb()
        )
    }
    // 回退：内置硬编码颜色
    return if (isDark) {
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
}

/**
 * 获取编辑器配色
 *
 * @param isDark 是否暗色模式
 * @param colorScheme Material 3 配色方案，null 时使用内置颜色
 */
internal fun editorColors(isDark: Boolean, colorScheme: ColorScheme? = null): EditorColors {
    if (colorScheme != null) {
        // 从 Material 3 主题派生编辑器颜色
        return EditorColors(
            text = colorScheme.onSurface.toArgb(),
            background = colorScheme.surface.toArgb(),
            accent = colorScheme.primary.toArgb(),
            highlight = colorScheme.primary.copy(alpha = 0.2f).toArgb()
        )
    }
    // 回退：内置硬编码颜色
    return if (isDark) {
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
}
