package com.dlam.textediting.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    settings: com.dlam.textediting.SettingsManager,
    onDismiss: () -> Unit
) {
    val fontSize by settings.fontSize.collectAsState()
    val maxTabs by settings.maxTabs.collectAsState()
    val showLineNumbers by settings.showLineNumbers.collectAsState()
    val wordWrap by settings.wordWrap.collectAsState()
    val autoSaveInterval by settings.autoSaveInterval.collectAsState()

    var showFontSizeMenu by remember { mutableStateOf(false) }
    var showMaxTabsMenu by remember { mutableStateOf(false) }
    var showAutoSaveMenu by remember { mutableStateOf(false) }

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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // ── Font Size ──
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

                // ── Max Tabs ──
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

                // ── Show Line Numbers ──
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

                // ── Word Wrap ──
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

                // ── Auto-save ──
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
