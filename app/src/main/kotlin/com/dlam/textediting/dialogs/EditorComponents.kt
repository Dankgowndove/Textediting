package com.dlam.textediting.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
@OptIn(ExperimentalMaterial3Api::class)
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