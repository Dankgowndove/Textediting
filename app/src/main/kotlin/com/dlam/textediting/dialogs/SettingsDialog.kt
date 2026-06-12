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
 * 提供所有用户可配置选项的 UI，包括：
 * - 字体大小、最大标签数、自动保存间隔（下拉菜单选择）
 * - 行号显示、自动换行、语法高亮、括号匹配等（开关切换）
 *
 * 所有设置实时生效，无需重启应用。变更通过 SettingsManager
 * 同步更新 StateFlow 和 SharedPreferences。
 *
 * @param settings 设置管理器实例
 * @param onDismiss 关闭对话框回调
 */
@Composable
fun SettingsDialog(
    settings: com.dlam.textediting.SettingsManager,
    onDismiss: () -> Unit
) {
    // ── 从 SettingsManager 收集所有设置状态 ──
    val fontSize by settings.fontSize.collectAsState()
    val maxTabs by settings.maxTabs.collectAsState()
    val showLineNumbers by settings.showLineNumbers.collectAsState()
    val wordWrap by settings.wordWrap.collectAsState()
    val autoSaveInterval by settings.autoSaveInterval.collectAsState()
    val syntaxHighlight by settings.syntaxHighlight.collectAsState()
    val bracketMatching by settings.bracketMatching.collectAsState()
    val highlightCurrentLine by settings.highlightCurrentLine.collectAsState()
    val showWhitespace by settings.showWhitespace.collectAsState()

    // ── 下拉菜单展开状态 ──
    var showFontSizeMenu by remember { mutableStateOf(false) }
    var showMaxTabsMenu by remember { mutableStateOf(false) }
    var showAutoSaveMenu by remember { mutableStateOf(false) }

    // 自动保存间隔 → 显示标签映射
    val autoSaveLabels = mapOf(
        0 to "关闭",
        30 to "30 秒",
        60 to "1 分钟",
        120 to "2 分钟",
        300 to "5 分钟"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                // 设置过多时允许垂直滚动
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ── 字体大小（下拉菜单）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("字体大小", style = MaterialTheme.typography.bodyMedium)
                    Box {
                        TextButton(onClick = { showFontSizeMenu = true }) {
                            Text("${fontSize}sp", style = MaterialTheme.typography.bodyMedium)
                        }
                        DropdownMenu(
                            expanded = showFontSizeMenu,
                            onDismissRequest = { showFontSizeMenu = false }
                        ) {
                            settings.getAllFontSizes().forEach { size ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${size}sp",
                                            // 当前值加粗显示
                                            fontWeight = if (size == fontSize) {
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                            } else androidx.compose.ui.text.font.FontWeight.Normal
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
                }

                HorizontalDivider()

                // ── 最大标签数（下拉菜单）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("最大标签数", style = MaterialTheme.typography.bodyMedium)
                    Box {
                        TextButton(onClick = { showMaxTabsMenu = true }) {
                            Text("$maxTabs", style = MaterialTheme.typography.bodyMedium)
                        }
                        DropdownMenu(
                            expanded = showMaxTabsMenu,
                            onDismissRequest = { showMaxTabsMenu = false }
                        ) {
                            settings.getAllMaxTabs().forEach { count ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "$count",
                                            fontWeight = if (count == maxTabs) {
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                            } else androidx.compose.ui.text.font.FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        settings.setMaxTabs(count)
                                        showMaxTabsMenu = false
                                    }
                                )
                            }
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

                // ── 语法高亮（开关）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("语法高亮", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = syntaxHighlight,
                        onCheckedChange = { settings.setSyntaxHighlight(it) }
                    )
                }

                HorizontalDivider()

                // ── 括号匹配（开关）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("括号匹配", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = bracketMatching,
                        onCheckedChange = { settings.setBracketMatching(it) }
                    )
                }

                HorizontalDivider()

                // ── 当前行高亮（开关）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("当前行高亮", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = highlightCurrentLine,
                        onCheckedChange = { settings.setHighlightCurrentLine(it) }
                    )
                }

                HorizontalDivider()

                // ── 显示空白字符（开关）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("显示空白字符", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showWhitespace,
                        onCheckedChange = { settings.setShowWhitespace(it) }
                    )
                }

                HorizontalDivider()

                // ── 自动保存（下拉菜单）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("自动保存", style = MaterialTheme.typography.bodyMedium)
                    Box {
                        TextButton(onClick = { showAutoSaveMenu = true }) {
                            Text(
                                autoSaveLabels[autoSaveInterval] ?: "关闭",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        DropdownMenu(
                            expanded = showAutoSaveMenu,
                            onDismissRequest = { showAutoSaveMenu = false }
                        ) {
                            settings.getAllAutoSaveIntervals().forEach { interval ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            autoSaveLabels[interval] ?: "$interval 秒",
                                            fontWeight = if (interval == autoSaveInterval) {
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                            } else androidx.compose.ui.text.font.FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        settings.setAutoSaveInterval(interval)
                                        showAutoSaveMenu = false
                                    }
                                )
                            }
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
