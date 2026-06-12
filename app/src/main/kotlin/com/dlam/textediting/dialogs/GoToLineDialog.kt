package com.dlam.textediting.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 跳转到行对话框
 *
 * 允许用户输入行号快速定位到文档中的指定行。
 * 仅接受数字输入，实时验证行号范围 [1, totalLines]。
 * 支持键盘 IME Go 操作和按钮点击两种确认方式。
 *
 * @param totalLines 文档总行数
 * @param onDismiss 关闭对话框回调
 * @param onGoToLine 跳转到指定行回调，参数为 1-based 行号
 */
@Composable
fun GoToLineDialog(
    totalLines: Int,
    onDismiss: () -> Unit,
    onGoToLine: (Int) -> Unit
) {
    // 用户输入的行号文本
    var input by remember { mutableStateOf("") }
    // 验证错误信息，null 表示无错误
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到行") },
        text = {
            Column {
                // 显示总行数提示
                Text(
                    text = "共 $totalLines 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { v ->
                        // 过滤非数字字符，确保只能输入数字
                        input = v.filter { it.isDigit() }
                        error = null
                    },
                    label = { Text("行号") },
                    // 数字键盘 + Go 按钮
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    // 键盘 Go 操作：验证并跳转
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val line = input.toIntOrNull()
                            if (line != null && line in 1..totalLines) {
                                onGoToLine(line)
                            } else {
                                error = "行号超出范围 (1-$totalLines)"
                            }
                        }
                    ),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 按钮点击：验证并跳转
                    val line = input.toIntOrNull()
                    if (line != null && line in 1..totalLines) {
                        onGoToLine(line)
                    } else {
                        error = "行号超出范围 (1-$totalLines)"
                    }
                }
            ) { Text("跳转") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
