/**
 * Textediting - 轻量级高性能 Android 文本编辑器
 *
 * 本文件为应用入口 Activity，负责：
 * - 初始化 Compose UI 环境
 * - 处理来自其他应用的 VIEW/EDIT Intent
 * - 绑定 ViewModel 与 Compose 界面
 *
 * 启动模式为 singleTask，确保只有一个编辑器实例，
 * 新 Intent 通过 onNewIntent() 接收。
 */
package com.dlam.textediting

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dlam.textediting.ui.theme.ComposeEmptyActivityTheme

/**
 * 应用主 Activity
 *
 * 使用 MVVM 架构：MainViewModel 持有所有应用状态，
 * MainScreen Compose 函数负责 UI 渲染。
 */
class MainActivity : ComponentActivity() {

    // 通过 viewModels() 委托自动创建 ViewModel，生命周期由 Activity 管理
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用全面屏（Edge-to-Edge）显示
        enableEdgeToEdge()
        // 设置 Compose 内容，应用 Material 3 主题
        setContent {
            ComposeEmptyActivityTheme {
                MainScreen(viewModel = viewModel)
            }
        }
        // 处理启动时的 Intent（如从文件管理器打开文件）
        handleIntent(intent)
    }

    /**
     * 当 Activity 已有实例时，新 Intent 通过此方法传入
     * （因为 launchMode 设置为 singleTask）
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * 处理 Intent：从中提取文件 URI 并打开文件
     *
     * 支持的 Intent Action：
     * - ACTION_VIEW：查看文件
     * - ACTION_EDIT：编辑文件
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            intent.data?.let { uri ->
                viewModel.openFile(uri)
            }
        }
    }
}
