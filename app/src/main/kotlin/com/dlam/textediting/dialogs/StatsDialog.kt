package com.dlam.textediting.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dlam.textediting.util.StatsResult
import com.dlam.textediting.util.computeStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文本统计对话框
 *
 * 对当前文档内容进行详细的文本统计分析，包括字符计数、行/词/段落计数、
 * 中英文分项统计和阅读时间估算。统计计算在后台线程执行，避免阻塞 UI。
 *
 * @param text 待统计的文本内容
 * @param onDismiss 关闭对话框回调
 */
@Composable
fun StatsDialog(
    text: String,
    onDismiss: () -> Unit
) {
    // 加载状态：计算中显示加载指示器
    var isLoading by remember { mutableStateOf(true) }
    // 统计结果：后台计算完成后赋值
    var result by remember { mutableStateOf<StatsResult?>(null) }

    // 当文本变化时，在后台线程重新计算统计信息
    LaunchedEffect(text) {
        isLoading = true
        result = null
        // 在 Default 调度器执行统计计算（不阻塞主线程）
        val r = withContext(Dispatchers.Default) { computeStats(text) }
        result = r
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文本统计") },
        text = {
            if (isLoading) {
                // 加载状态：显示进度指示器和提示文字
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("正在统计...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                // 结果展示：逐项显示统计信息
                result?.let { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // ── 基础统计 ──
                        StatsRow("总字符数（含空格）", r.totalCharsWithSpace)
                        StatsRow("总字符数（不含空格）", r.totalCharsNoSpace)
                        StatsRow("总行数", r.totalLines)
                        StatsRow("非空行数", r.nonEmptyLines)
                        StatsRow("总词数", r.totalWords)
                        StatsRow("段落数", r.totalParagraphs)
                        // ── 字符分类 ──
                        StatsRow("中文字符", r.chineseChars)
                        StatsRow("英文字符", r.englishChars)
                        StatsRow("数字", r.digitChars)
                        StatsRow("标点符号", r.punctuationChars)
                        StatsRow("空格数", r.spaceChars)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        // ── 阅读时间 ──
                        StatsRow("估算阅读时间", r.estimatedReadTime)
                        // 超大文档提示
                        if (r.totalLinesValue > 100000) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "文档较大（${r.totalLinesValue} 行），已分段统计",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 统计信息行组件
 *
 * 左侧显示标签，右侧显示数值（主题色高亮）。
 *
 * @param label 统计项名称
 * @param value 已格式化的统计值
 */
@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}
