package com.dlam.textediting.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 暗色主题配色方案（静态回退）
 *
 * 当设备不支持动态取色（Android 12 以下）或用户关闭动态取色时使用
 */
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * 亮色主题配色方案（静态回退）
 */
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * 应用 Material 3 主题包装器
 *
 * 配色策略优先级：
 * 1. Android 12+ 且开启动态取色 → 系统壁纸动态取色
 * 2. 暗色模式 → DarkColorScheme
 * 3. 亮色模式 → LightColorScheme
 *
 * @param darkTheme 是否使用暗色主题，默认跟随系统
 * @param dynamicColor 是否启用动态取色（Android 12+），默认开启
 * @param content 子 Composable 内容
 */
@Composable
fun ComposeEmptyActivityTheme(
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
        // 静态暗色/亮色方案
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
