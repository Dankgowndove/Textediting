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

@Composable
fun StatsDialog(
    text: String,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<StatsResult?>(null) }

    LaunchedEffect(text) {
        isLoading = true
        result = null
        val r = withContext(Dispatchers.Default) { computeStats(text) }
        result = r
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文本统计") },
        text = {
            if (isLoading) {
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
                result?.let { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatsRow("总字符数（含空格）", r.totalCharsWithSpace)
                        StatsRow("总字符数（不含空格）", r.totalCharsNoSpace)
                        StatsRow("总行数", r.totalLines)
                        StatsRow("非空行数", r.nonEmptyLines)
                        StatsRow("总词数", r.totalWords)
                        StatsRow("段落数", r.totalParagraphs)
                        StatsRow("中文字符", r.chineseChars)
                        StatsRow("英文字符", r.englishChars)
                        StatsRow("数字", r.digitChars)
                        StatsRow("标点符号", r.punctuationChars)
                        StatsRow("空格数", r.spaceChars)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        StatsRow("估算阅读时间", r.estimatedReadTime)
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
