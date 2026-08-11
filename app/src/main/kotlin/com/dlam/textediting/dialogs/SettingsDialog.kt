package com.dlam.textediting.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 设置对话框
 *
 * 提供所有可配置选项的 UI，包括：
 * - 字体大小（下拉菜单选择）
 * - 主题模式（下拉菜单选择）
 * - 行号显示、自动换行（开关切换）
 *
 * 所有设置实时生效，无需重启应用。变更通过 SettingsManager
 * 同步更新 StateFlow 和 SharedPreferences。
 *
 * @param settings 设置管理器实例
 * @param onDismiss 关闭对话框回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: com.dlam.textediting.SettingsManager,
    onDismiss: () -> Unit
) {
    // ── 从 SettingsManager 收集所有设置状态 ──
    val fontSize by settings.fontSize.collectAsState()
    val showLineNumbers by settings.showLineNumbers.collectAsState()
    val wordWrap by settings.wordWrap.collectAsState()
    val darkThemeMode by settings.darkThemeMode.collectAsState()

    // ── 下拉菜单展开状态 ──
    var showFontSizeMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    // 主题模式标签
    val themeModeLabels = mapOf(
        0 to "跟随系统",
        1 to "浅色主题",
        2 to "深色主题"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── 字体大小（M3 ExposedDropdownMenuBox）──
                ExposedDropdownMenuBox(
                    expanded = showFontSizeMenu,
                    onExpandedChange = { showFontSizeMenu = it }
                ) {
                    OutlinedTextField(
                        value = "${fontSize}sp",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("字体大小") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFontSizeMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showFontSizeMenu,
                        onDismissRequest = { showFontSizeMenu = false }
                    ) {
                        settings.getAllFontSizes().forEach { size ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${size}sp",
                                        fontWeight = if (size == fontSize)
                                            androidx.compose.ui.text.font.FontWeight.Bold
                                        else androidx.compose.ui.text.font.FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    settings.setFontSize(size)
                                    showFontSizeMenu = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ── 显示行号（开关）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("显示行号", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showLineNumbers,
                        onCheckedChange = { settings.setShowLineNumbers(it) }
                    )
                }

                HorizontalDivider()

                // ── 自动换行（开关）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("自动换行", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = wordWrap,
                        onCheckedChange = { settings.setWordWrap(it) }
                    )
                }

                HorizontalDivider()

                // ── 主题模式（M3 ExposedDropdownMenuBox）──
                ExposedDropdownMenuBox(
                    expanded = showThemeMenu,
                    onExpandedChange = { showThemeMenu = it }
                ) {
                    OutlinedTextField(
                        value = themeModeLabels[darkThemeMode] ?: "跟随系统",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("主题模式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showThemeMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showThemeMenu,
                        onDismissRequest = { showThemeMenu = false }
                    ) {
                        themeModeLabels.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (mode == darkThemeMode)
                                            androidx.compose.ui.text.font.FontWeight.Bold
                                        else androidx.compose.ui.text.font.FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    settings.setDarkThemeMode(mode)
                                    showThemeMenu = false
                                }
                            )
                        }
                    }
                }

                // 提示：设置实时生效
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "设置实时生效，无需重启应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}