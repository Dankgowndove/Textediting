package com.dlam.textediting.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 主题色板定义
 *
 * 定义亮色和暗色两套完整配色方案。
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

// ── 暗色主题完整色板（用于非动态取色场景）──
val DarkColorSchemeColors = androidx.compose.material3.darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = PurpleGrey80,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Pink80,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF6750A4),
    surfaceTint = Purple80
)

// ── 亮色主题完整色板（用于非动态取色场景）──
val LightColorSchemeColors = androidx.compose.material3.lightColorScheme(
    primary = Purple40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleGrey40,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Pink40,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF1C1B1F),
    inverseOnSurface = Color(0xFFE6E1E5),
    inversePrimary = Color(0xFFD0BCFF),
    surfaceTint = Purple40
)
