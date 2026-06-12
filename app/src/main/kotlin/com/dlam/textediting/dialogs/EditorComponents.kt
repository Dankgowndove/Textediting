package com.dlam.textediting.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlam.textediting.OpenTab

/**
 * 文本搜索栏组件
 *
 * 提供搜索输入框、匹配计数显示、上/下一个导航按钮以及搜索选项。
 * 支持大小写敏感和全字匹配两种过滤模式。
 *
 * @param query 当前搜索查询
 * @param onQueryChange 查询文本变化回调
 * @param matchCount 匹配总数
 * @param currentIndex 当前匹配项索引（0-based）
 * @param onPrevious 上一个匹配项回调
 * @param onNext 下一个匹配项回调
 * @param onClose 关闭搜索回调
 * @param isCaseSensitive 是否大小写敏感
 * @param isWholeWord 是否全字匹配
 * @param onToggleCaseSensitive 切换大小写敏感回调
 * @param onToggleWholeWord 切换全字匹配回调
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    isCaseSensitive: Boolean = false,
    isWholeWord: Boolean = false,
    onToggleCaseSensitive: () -> Unit = {},
    onToggleWholeWord: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 搜索输入行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索图标
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            // 搜索文本输入
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                )
            )
            // 有查询内容时显示匹配计数和导航按钮
            if (query.isNotEmpty()) {
                val displayIndex = if (matchCount > 0) currentIndex + 1 else 0
                Text(
                    text = "$displayIndex/$matchCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                // 上一个匹配
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一个")
                }
                // 下一个匹配
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一个")
                }
            }
            // 关闭搜索
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
            }
        }
        // 搜索选项过滤 Chips
        if (query.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 大小写敏感切换
                FilterChip(
                    selected = isCaseSensitive,
                    onClick = onToggleCaseSensitive,
                    label = { Text("Aa", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = if (isCaseSensitive) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.height(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                // 全字匹配切换
                FilterChip(
                    selected = isWholeWord,
                    onClick = onToggleWholeWord,
                    label = { Text("全字", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = if (isWholeWord) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}

/**
 * 标签栏组件
 *
 * 使用 LazyRow 水平滚动显示所有打开的标签页。
 * 每个标签页显示：修改指示点/文件图标、文件名、关闭按钮。
 * 活跃标签高亮显示，未保存的标签显示圆点指示器。
 *
 * @param tabs 打开的标签页列表
 * @param activeIndex 当前活跃标签索引
 * @param maxTabs 最大标签数上限
 * @param onTabClick 标签点击回调
 * @param onTabClose 标签关闭回调
 * @param onTabMove 标签移动回调（预留）
 */
@Composable
fun TabBar(
    tabs: List<OpenTab>,
    activeIndex: Int,
    maxTabs: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onTabMove: (Int, Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(tabs, key = { idx, _ -> idx }) { index, tab ->
                // 单个标签页
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .clickable { onTabClick(index) },
                    // 活跃标签使用不同背景色和高度
                    color = if (index == activeIndex) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (index == activeIndex) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 修改状态指示：已修改显示圆点，否则显示文件图标
                        Icon(
                            imageVector = if (tab.isModified) Icons.Filled.FiberManualRecord else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (tab.isModified) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        // 文件名（最大宽度 120dp，超出省略）
                        Text(
                            text = tab.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                        // 关闭按钮
                        IconButton(
                            onClick = { onTabClose(index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            // 达到上限时显示提示
            if (tabs.size >= maxTabs) {
                items(1) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .height(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "已达上限",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * 全局替换对话框
 *
 * 支持在当前文件或整个工作区目录中批量替换文本。
 * 包含查找/替换输入框、范围选择（当前文件/全部文件）和二次确认机制。
 *
 * @param onDismiss 关闭对话框回调
 * @param onReplace 执行替换回调：查找内容、替换为、是否仅当前文件
 */
@Composable
fun GlobalReplaceDialog(
    onDismiss: () -> Unit,
    onReplace: (find: String, replace: String, onlyCurrentFile: Boolean) -> Unit
) {
    var find by remember { mutableStateOf("") }        // 查找内容
    var replace by remember { mutableStateOf("") }      // 替换为
    var onlyCurrent by remember { mutableStateOf(true) } // 仅替换当前文件
    var showConfirm by remember { mutableStateOf(false) } // 是否显示确认对话框

    if (showConfirm) {
        // ── 确认对话框（二次确认，防止误操作）──
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认替换") },
            text = {
                Text(
                    if (onlyCurrent) "将替换当前文件中所有「${find}」为「${replace}」，此操作不可逆。"
                    else "将替换根目录下所有文本文件中的「${find}」为「${replace}」，此操作不可逆。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onReplace(find, replace, onlyCurrent)
                }) { Text("确认执行") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            }
        )
    } else {
        // ── 替换输入对话框 ──
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("全局替换") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 查找内容输入
                    OutlinedTextField(
                        value = find,
                        onValueChange = { find = it },
                        label = { Text("查找内容") },
                        singleLine = true
                    )
                    // 替换为输入
                    OutlinedTextField(
                        value = replace,
                        onValueChange = { replace = it },
                        label = { Text("替换为") },
                        singleLine = true
                    )
                    // 范围选择：仅当前文件 / 全部文件
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = onlyCurrent, onCheckedChange = { onlyCurrent = it })
                        Text("仅替换当前文件", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = true },
                    enabled = find.isNotBlank()  // 查找内容不能为空
                ) { Text("替换") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }
}
