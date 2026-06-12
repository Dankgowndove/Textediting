package com.dlam.textediting.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 形状系统
 *
 * 统一应用中所有组件的圆角半径，确保视觉一致性。
 * - extraSmall (4dp)：小型芯片、徽章
 * - small (8dp)：卡片、列表项
 * - medium (12dp)：对话框、弹出菜单
 * - large (16dp)：ModalBottomSheet
 * - extraLarge (28dp)：大尺寸浮动操作
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
