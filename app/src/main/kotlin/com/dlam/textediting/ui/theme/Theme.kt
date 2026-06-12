package com.dlam.textediting.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 应用 Material 3 主题包装器
 *
 * 配色策略优先级：
 * 1. Android 12+ 且开启动态取色 → 系统壁纸动态取色
 * 2. 暗色模式 → DarkColorScheme（完整色板）
 * 3. 亮色模式 → LightColorScheme（完整色板）
 *
 * 注入自定义 [AppShapes] 和 [Typography] 以确保视觉一致性。
 *
 * @param darkTheme 是否使用暗色主题，默认跟随系统
 * @param dynamicColor 是否启用动态取色（Android 12+），默认开启
 * @param content 子 Composable 内容
 */
@Composable
fun TextEditingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ 动态取色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 静态完整色板
        darkTheme -> DarkColorSchemeColors
        else -> LightColorSchemeColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
