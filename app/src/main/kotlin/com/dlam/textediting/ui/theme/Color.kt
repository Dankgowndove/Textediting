package com.dlam.textediting.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 主题色板定义
 *
 * 定义亮色和暗色两套配色方案。
 * Android 12+ 上可通过 dynamicColorScheme 使用系统动态取色，
 * 此时这些静态颜色作为回退（fallback）方案。
 */

// ── 暗色主题色板 ──
/** 暗色主题主色（紫色 80% 亮度） */
val Purple80 = Color(0xFFD0BCFF)
/** 暗色主题次要色（紫灰色 80% 亮度） */
val PurpleGrey80 = Color(0xFFCCC2DC)
/** 暗色主题第三色（粉色 80% 亮度） */
val Pink80 = Color(0xFFEFB8C8)

// ── 亮色主题色板 ──
/** 亮色主题主色（紫色 40% 亮度） */
val Purple40 = Color(0xFF6650a4)
/** 亮色主题次要色（紫灰色 40% 亮度） */
val PurpleGrey40 = Color(0xFF625b71)
/** 亮色主题第三色（粉色 40% 亮度） */
val Pink40 = Color(0xFF7D5260)
