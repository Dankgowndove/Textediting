package com.dlam.textediting.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 编辑器组件独立配色方案
 *
 * 编辑器（LinedEditText）和行号栏使用自己独立的配色，
 * 不依赖 Material 3 主题色，以确保代码编辑区域的最佳对比度和可读性。
 *
 * 配色分为亮色和暗色两套方案，由 [LinedEditText.darkMode] 属性控制切换。
 */

/**
 * 行号栏配色
 *
 * @property background 行号栏背景色
 * @property divider 行号栏右侧分割线颜色
 * @property lineNumber 行号文字颜色
 */
internal data class GutterColors(
    val background: Int,
    val divider: Int,
    val lineNumber: Int
)

/**
 * 编辑器配色
 *
 * @property text 文本颜色
 * @property background 编辑器背景色
 * @property accent 强调色（光标、括号高亮等）
 * @property highlight 选区高亮色
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
 * @return 对应的 GutterColors
 */
internal fun gutterColors(isDark: Boolean): GutterColors = if (isDark) {
    // 暗色主题：深色背景 + 浅灰分割线 + 浅灰行号
    GutterColors(
        background = 0xFF1A1A1A.toInt(),
        divider = 0xFF3A3A3A.toInt(),
        lineNumber = 0xFFAAAAAA.toInt()
    )
} else {
    // 亮色主题：浅灰背景 + 灰分割线 + 深灰行号
    GutterColors(
        background = 0xFFF0F0F0.toInt(),
        divider = 0xFFD0D0D0.toInt(),
        lineNumber = 0xFF888888.toInt()
    )
}

/**
 * 获取编辑器配色
 *
 * @param isDark 是否暗色模式
 * @return 对应的 EditorColors
 */
internal fun editorColors(isDark: Boolean): EditorColors = if (isDark) {
    // 暗色主题：亮文本 + 深底 + 紫色强调
    EditorColors(
        text = 0xFFEEEEEE.toInt(),
        background = 0xFF121212.toInt(),
        accent = 0xFFBB86FC.toInt(),
        highlight = 0x44FFFFFF.toInt()
    )
} else {
    // 亮色主题：深文本 + 白底 + 紫色强调
    EditorColors(
        text = 0xFF1A1A1A.toInt(),
        background = 0xFFFFFFFF.toInt(),
        accent = 0xFF6650A4.toInt(),
        highlight = 0x33000000.toInt()
    )
}
