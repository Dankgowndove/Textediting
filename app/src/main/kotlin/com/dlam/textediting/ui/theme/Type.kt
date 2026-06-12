package com.dlam.textediting.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 排版（Typography）定义
 *
 * 当前仅定义了 bodyLarge 样式，其他样式使用 Material 3 默认值。
 * 使用系统默认字体（FontFamily.Default），适用于中英文混排场景。
 */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,  // 系统默认字体
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
