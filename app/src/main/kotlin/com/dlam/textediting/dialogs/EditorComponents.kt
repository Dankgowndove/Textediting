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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                )
            )
            if (query.isNotEmpty()) {
                val displayIndex = if (matchCount > 0) currentIndex + 1 else 0
                Text(
                    text = "$displayIndex/$matchCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一个")
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
            }
        }
        if (query.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(tabs, key = { idx, _ -> idx }) { index, tab ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .clickable { onTabClick(index) },
                    color = if (index == activeIndex) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (index == activeIndex) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (tab.isModified) Icons.Filled.FiberManualRecord else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (tab.isModified) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tab.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
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

@Composable
fun GlobalReplaceDialog(
    onDismiss: () -> Unit,
    onReplace: (find: String, replace: String, onlyCurrentFile: Boolean) -> Unit
) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var onlyCurrent by remember { mutableStateOf(true) }
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
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
                }) { Text("确认执行", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("全局替换") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = find,
                        onValueChange = { find = it },
                        label = { Text("查找内容") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = replace,
                        onValueChange = { replace = it },
                        label = { Text("替换为") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = onlyCurrent, onCheckedChange = { onlyCurrent = it })
                        Text("仅替换当前文件", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = true },
                    enabled = find.isNotBlank()
                ) { Text("替换") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }
}
