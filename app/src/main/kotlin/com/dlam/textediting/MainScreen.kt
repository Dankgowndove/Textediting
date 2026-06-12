package com.dlam.textediting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dlam.textediting.dialogs.*
import com.dlam.textediting.editor.LinedEditText
import com.dlam.textediting.editor.SyntaxHighlighter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 Composable
 *
 * 这是整个应用的顶层 UI 组件，组合了所有子组件：
 * - 顶部工具栏（TopAppBar）：文件操作、撤销/重做、搜索、更多菜单
 * - 标签栏（TabBar）：文件标签页管理
 * - 搜索栏（SearchBar）：文本搜索
 * - 编辑器区域：AndroidView 包装的 LinedEditText
 * - 侧边栏：文件浏览器（FileTreeSidebar）
 * - 对话框：跳转到行、文本统计、设置
 *
 * ## 编辑器单向数据流
 * 用户输入通过 `ignoreTextChange` 标志与外部变更（打开文件/撤销/重做）
 * 解耦，防止 Compose ↔ EditText 之间的反馈循环导致光标跳动。
 *
 * @param viewModel 主 ViewModel，持有所有应用状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    // ── 从 ViewModel 收集所有状态（StateFlow → Compose State）──
    val content by viewModel.textContent.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val isModified by viewModel.isModified.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchMatchCount by viewModel.searchMatchCount.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()
    val currentUri by viewModel.currentUri.collectAsState()
    val lineCount by remember { derivedStateOf { content.lines().size } }  // 派生的行数
    val openTabs by viewModel.openTabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val isCaseSensitive by viewModel.isCaseSensitive.collectAsState()
    val isWholeWord by viewModel.isWholeWord.collectAsState()
    // ── 设置项 ──
    val fontSize by viewModel.settings.fontSize.collectAsState()
    val showLineNumbers by viewModel.settings.showLineNumbers.collectAsState()
    val wordWrap by viewModel.settings.wordWrap.collectAsState()
    val maxTabs by viewModel.settings.maxTabs.collectAsState()
    val syntaxHighlight by viewModel.settings.syntaxHighlight.collectAsState()
    val bracketMatching by viewModel.settings.bracketMatching.collectAsState()
    val highlightCurrentLine by viewModel.settings.highlightCurrentLine.collectAsState()
    val showWhitespace by viewModel.settings.showWhitespace.collectAsState()
    val autoSaveInterval by viewModel.settings.autoSaveInterval.collectAsState()
    val recentFilesList by viewModel.recentFiles.recentFiles.collectAsState()

    // ── Compose 本地状态 ──
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)  // 侧边栏状态

    // 对话框显示状态
    var showStatsDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // 编辑器引用和状态
    val editTextRef = remember { mutableStateOf<LinedEditText?>(null) }  // EditText 弱引用
    val isKeyboardVisible = remember { mutableStateOf(false) }            // 键盘可见状态
    var ignoreTextChange by remember { mutableStateOf(false) }            // 阻断回写标志

    // [M3 优化] 根据用户设置或系统决定暗色模式
    val darkThemeMode by viewModel.settings.darkThemeMode.collectAsState()
    val isDarkMode = when (darkThemeMode) {
        1 -> false  // 始终浅色
        2 -> true   // 始终深色
        else -> androidx.compose.foundation.isSystemInDarkTheme()  // 0=跟随系统
    }

    // SAF 文件选择器启动器
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.openFile(it) } }

    // SAF 文件另存为启动器
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.saveAs(it) } }

    val scope = rememberCoroutineScope()

    // ════════════════════════════════════════════
    //  LaunchedEffect 副作用处理
    // ════════════════════════════════════════════

    // 收集 Snackbar 事件（一次性消息提示）
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 收集跳转到行事件（从全局搜索等触发）
    LaunchedEffect(Unit) {
        viewModel.pendingScrollToLine.collect { line ->
            kotlinx.coroutines.delay(150)  // 等待编辑器准备就绪
            editTextRef.value?.scrollToLine(line)
        }
    }

    // ── 自动保存计时器 ──
    LaunchedEffect(isModified, autoSaveInterval, currentUri) {
        viewModel.scheduleAutoSave()
    }

    // ── 语法高亮触发 ──
    LaunchedEffect(content, fileName, syntaxHighlight) {
        if (syntaxHighlight && content.isNotEmpty() && fileName.isNotEmpty()) {
            viewModel.triggerSyntaxHighlight(content, fileName, isDarkMode)
        }
    }

    // ── 语法高亮结果应用 ──
    LaunchedEffect(Unit) {
        viewModel.highlightsReady.collect {
            editTextRef.value?.let { et ->
                val spannable = et.text as? Spannable ?: return@collect
                viewModel.applyHighlightIfReady(spannable, isDarkMode)
            }
        }
    }

    // ── 外部文本变更同步到编辑器 ──
    // 这是编辑器单向数据流的关键：仅当文本变更是由外部触发时
    // （打开文件/撤销/重做/切换标签）才更新 EditText 内容
    LaunchedEffect(content) {
        if (ignoreTextChange) {
            // 用户输入触发的变更：跳过回写，仅清除标志
            ignoreTextChange = false
            return@LaunchedEffect
        }
        editTextRef.value?.let { et ->
            if (et.text?.toString() != content) {
                val hadFocus = et.hasFocus()
                val selStart = et.selectionStart
                val selEnd = et.selectionEnd
                et.setText(content)
                // 尽可能恢复光标位置
                if (selStart in 0..content.length && selEnd in selStart..content.length) {
                    et.setSelection(selStart, selEnd)
                }
                if (hadFocus) et.requestFocus()
            }
        }
    }

    // ════════════════════════════════════════════
    //  返回键处理
    // ════════════════════════════════════════════

    // 优先隐藏键盘，其次关闭搜索
    if (isKeyboardVisible.value) {
        BackHandler {
            editTextRef.value?.let { et ->
                et.clearFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(et.windowToken, 0)
                isKeyboardVisible.value = false
            }
        }
    } else if (isSearchVisible) {
        BackHandler(onBack = viewModel::dismissSearch)
    }

    // ── 计算派生 UI 状态 ──
    val isFileOpen = openTabs.isNotEmpty() || currentUri != null || content.isNotEmpty() || fileName.isNotEmpty()
    val title = when {
        fileName.isEmpty() -> "文本编辑器"
        else -> fileName + if (isModified) "  ●" else ""  // 修改标记
    }

    /** 滚动到当前搜索匹配项 */
    fun scrollToSearchMatch() {
        val pos = viewModel.getSearchPosition() ?: return
        editTextRef.value?.let { et ->
            et.setSelection(pos.first, pos.first + pos.second)
        }
    }

    // ════════════════════════════════════════════
    //  UI 结构
    // ════════════════════════════════════════════

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // 侧边栏：文件浏览器
            ModalDrawerSheet {
                FileTreeSidebar(
                    viewModel = viewModel,
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // ── 顶部工具栏 ──
                TopAppBar(
                    title = {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    tonalElevation = 3.dp,
                    navigationIcon = {
                        // 菜单按钮（打开侧边栏）
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        if (isFileOpen) {
                            // ── 文件已打开时的工具栏按钮 ──
                            // 搜索切换
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索")
                            }
                            // [Bug #5 修复] 撤销：操作完成后同步 ViewModel 状态
                            IconButton(
                                onClick = {
                                    val et = editTextRef.value ?: return@IconButton
                                    val result = viewModel.undoManager.prepareUndo()
                                    if (result != null) {
                                        try {
                                            ignoreTextChange = true   // 阻止 TextWatcher 再次 record
                                            et.setText(result)
                                            viewModel.onUndoRedoApplied(result)
                                        } finally {
                                            viewModel.undoManager.finishUndoRedo()
                                        }
                                    }
                                },
                                enabled = viewModel.canUndo
                            ) {
                                Icon(Icons.Filled.Undo, contentDescription = "撤销")
                            }
                            // [Bug #5 修复] 重做：操作完成后同步 ViewModel 状态
                            IconButton(
                                onClick = {
                                    val et = editTextRef.value ?: return@IconButton
                                    val result = viewModel.undoManager.prepareRedo()
                                    if (result != null) {
                                        try {
                                            ignoreTextChange = true
                                            et.setText(result)
                                            viewModel.onUndoRedoApplied(result)
                                        } finally {
                                            viewModel.undoManager.finishUndoRedo()
                                        }
                                    }
                                },
                                enabled = viewModel.canRedo
                            ) {
                                Icon(Icons.Filled.Redo, contentDescription = "重做")
                            }
                            // 保存
                            IconButton(
                                onClick = {
                                    if (currentUri != null) {
                                        viewModel.saveFile()
                                    } else {
                                        saveAsLauncher.launch("new_file.txt")
                                    }
                                },
                                enabled = isModified
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = "保存")
                            }
                            // 更多操作（溢出菜单）
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    // 复制行号
                                    DropdownMenuItem(
                                        text = { Text("复制行号") },
                                        onClick = {
                                            showOverflowMenu = false
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val lines = content.lines()
                                            val sb = StringBuilder(lines.size * 8)
                                            for (i in lines.indices) sb.append(i + 1).append('\n')
                                            clipboard.setPrimaryClip(ClipData.newPlainText("行号", sb.trimEnd().toString()))
                                        },
                                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }
                                    )
                                    // 跳转到行
                                    DropdownMenuItem(
                                        text = { Text("跳转到行") },
                                        onClick = {
                                            showOverflowMenu = false
                                            showGoToLineDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) }
                                    )
                                    // 文本统计
                                    DropdownMenuItem(
                                        text = { Text("文本统计") },
                                        onClick = {
                                            showOverflowMenu = false
                                            showStatsDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) }
                                    )
                                    // 设置
                                    DropdownMenuItem(
                                        text = { Text("设置") },
                                        onClick = {
                                            showOverflowMenu = false
                                            showSettingsDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                                    )
                                }
                            }
                        } else {
                            // ── 无文件打开时的工具栏按钮 ──
                            Row {
                                IconButton(onClick = { showSettingsDialog = true }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                                }
                                IconButton(onClick = { openFileLauncher.launch(arrayOf("*/*")) }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "打开文件")
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── 标签栏（带动画）──
                AnimatedVisibility(
                    visible = openTabs.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TabBar(
                        tabs = openTabs,
                        activeIndex = activeTabIndex,
                        maxTabs = maxTabs,
                        onTabClick = { idx -> viewModel.switchToTab(idx) },
                        onTabClose = { idx -> viewModel.closeTab(idx) },
                        onTabMove = { from, to -> viewModel.moveTab(from, to) }
                    )
                }

                // ── 搜索栏（动画显示/隐藏）──
                AnimatedVisibility(visible = isSearchVisible) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        matchCount = searchMatchCount,
                        currentIndex = currentSearchIndex,
                        onPrevious = {
                            viewModel.searchPrevious()
                            scrollToSearchMatch()
                        },
                        onNext = {
                            viewModel.searchNext()
                            scrollToSearchMatch()
                        },
                        onClose = viewModel::dismissSearch,
                        isCaseSensitive = isCaseSensitive,
                        isWholeWord = isWholeWord,
                        onToggleCaseSensitive = viewModel::toggleCaseSensitive,
                        onToggleWholeWord = viewModel::toggleWholeWord
                    )
                }

                // ── 内容区域（带动画切换）──
                AnimatedContent(
                    targetState = isFileOpen && !isLoading,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                    }
                ) { showEditor ->
                    if (!showEditor) {
                        // ── 空状态：欢迎页面 ──
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "文本编辑器",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(24.dp))
                            // 打开文件按钮
                            FilledTonalButton(
                                onClick = { openFileLauncher.launch(arrayOf("*/*")) }
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("打开文件")
                            }
                            Spacer(Modifier.height(12.dp))
                            // 新建文件按钮
                            FilledTonalButton(
                                onClick = { viewModel.createNewFile() }
                            ) {
                                Text("新建文件")
                            }

                            // ── 最近打开文件列表 ──
                            if (recentFilesList.isNotEmpty()) {
                                Spacer(Modifier.height(32.dp))
                                Text(
                                    "最近打开",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .widthIn(max = 420.dp)
                                        .heightIn(max = 240.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(recentFilesList.take(10)) { recent ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.openFile(recent.uri) },
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            tonalElevation = 0.dp
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.Description,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    recent.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                        } // end Box
                } else {
                    // ── 编辑器区域 ──
                    // [M3 优化] 使用 Surface 包裹以获得深度层次感
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusable(true)
                    ) {
                        // 加载指示器
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        // [M3 优化] 捕获 Material3 配色方案供编辑器使用
                        val editorColorScheme = MaterialTheme.colorScheme

                        // LinedEditText 通过 AndroidView 集成
                        AndroidView(
                            factory = { ctx ->
                                // 创建编辑器实例并配置
                                LinedEditText(ctx).also { et ->
                                    // 多行文本 + 禁用拼写建议
                                    et.inputType = EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
                                            EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                    et.setHorizontallyScrolling(!wordWrap)
                                    et.isVerticalScrollBarEnabled = true
                                    // [Bug #6 修复] 使用 SP 单位设置字体大小，而非直接传 float（会被误当 px）
                                    et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
                                    et.typeface = android.graphics.Typeface.MONOSPACE  // 等宽字体
                                    et.hint = "在此输入文本..."
                                    et.maxLines = Int.MAX_VALUE
                                    et.minLines = 1

                                    // [M3 优化] 编辑器颜色融入 Material 主题
                                    et.colorScheme = editorColorScheme

                                    // 应用当前设置
                                    et.showLineNumbers = showLineNumbers
                                    et.darkMode = isDarkMode
                                    et.highlightCurrentLine = highlightCurrentLine
                                    et.bracketMatching = bracketMatching
                                    et.showWhitespace = showWhitespace

                                    // IME 操作处理
                                    et.setOnEditorActionListener { _, actionId, event ->
                                        if (actionId == EditorInfo.IME_ACTION_DONE ||
                                            (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP)
                                        ) {
                                            et.clearFocus()
                                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.hideSoftInputFromWindow(et.windowToken, 0)
                                            isKeyboardVisible.value = false
                                            true
                                        } else false
                                    }

                                    // 文本变更监听器（编辑器单向数据流的入口）
                                    et.addTextChangedListener(object : TextWatcher {
                                        override fun beforeTextChanged(
                                            s: CharSequence, start: Int, count: Int, after: Int
                                        ) {}

                                        override fun onTextChanged(
                                            s: CharSequence, start: Int, before: Int, count: Int
                                        ) {}

                                        override fun afterTextChanged(s: Editable) {
                                            // 先设置阻断标志，再通知 ViewModel
                                            // 这样 LaunchedEffect(content) 会跳过回写
                                            ignoreTextChange = true
                                            viewModel.onTextChanged(s.toString())
                                        }
                                    })

                                    editTextRef.value = et  // 保存引用
                                    et.requestFocus()
                                }
                            },
                            update = { et ->
                                // AndroidView.update 仅更新非文本属性
                                // 绝不在此处比较或设置文本内容！
                                // [Bug #6 修复] 统一用 SP 单位比较和设置字体大小
                                val targetPx = android.util.TypedValue.applyDimension(
                                    android.util.TypedValue.COMPLEX_UNIT_SP,
                                    fontSize.toFloat(),
                                    et.resources.displayMetrics
                                )
                                if (Math.abs(et.textSize - targetPx) > 0.5f) {
                                    et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
                                }
                                // [M3 优化] 主题变化时同步更新编辑器配色
                                et.colorScheme = editorColorScheme
                                et.setHorizontallyScrolling(!wordWrap)
                                et.showLineNumbers = showLineNumbers
                                et.darkMode = isDarkMode
                                et.highlightCurrentLine = highlightCurrentLine
                                et.bracketMatching = bracketMatching
                                et.showWhitespace = showWhitespace
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    } // end Surface
                }
            }
        }
    }

    // ════════════════════════════════════════════
    //  对话框
    // ════════════════════════════════════════════

    // 跳转到行对话框
    if (showGoToLineDialog) {
        GoToLineDialog(
            totalLines = lineCount,
            onDismiss = { showGoToLineDialog = false },
            onGoToLine = { line ->
                editTextRef.value?.scrollToLine(line)
                showGoToLineDialog = false
            }
        )
    }

    // 文本统计对话框
    if (showStatsDialog) {
        StatsDialog(
            text = content,
            onDismiss = { showStatsDialog = false }
        )
    }

    // 设置对话框
    if (showSettingsDialog) {
        SettingsDialog(
            settings = viewModel.settings,
            onDismiss = { showSettingsDialog = false }
        )
    }
}
