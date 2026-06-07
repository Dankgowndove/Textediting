package com.dlam.textediting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val content by viewModel.textContent.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val isModified by viewModel.isModified.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchMatchCount by viewModel.searchMatchCount.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()
    val currentUri by viewModel.currentUri.collectAsState()
    val lineCount by remember { derivedStateOf { content.lines().size } }
    val openTabs by viewModel.openTabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val isCaseSensitive by viewModel.isCaseSensitive.collectAsState()
    val isWholeWord by viewModel.isWholeWord.collectAsState()
    val fontSize by viewModel.settings.fontSize.collectAsState()
    val showLineNumbers by viewModel.settings.showLineNumbers.collectAsState()
    val wordWrap by viewModel.settings.wordWrap.collectAsState()
    val maxTabs by viewModel.settings.maxTabs.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var showStatsDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var statsResult by remember { mutableStateOf<StatsResult?>(null) }
    var isStatsLoading by remember { mutableStateOf(false) }

    val editTextRef = remember { mutableStateOf<LinedEditText?>(null) }
    val isKeyboardVisible = remember { mutableStateOf(false) }
    var ignoreTextChange by remember { mutableStateOf(false) }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.openFile(it) } }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.saveAs(it) } }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pendingScrollToLine.collect { line ->
            kotlinx.coroutines.delay(150)
            editTextRef.value?.scrollToLine(line)
        }
    }

    // Handle external text changes (file open, undo/redo, tab switch, etc.)
    // Skip when the change was triggered by user input (ignoreTextChange flag)
    LaunchedEffect(content) {
        if (ignoreTextChange) {
            ignoreTextChange = false
            return@LaunchedEffect
        }
        editTextRef.value?.let { et ->
            if (et.text?.toString() != content) {
                val hadFocus = et.hasFocus()
                val selStart = et.selectionStart
                val selEnd = et.selectionEnd
                et.setText(content)
                if (selStart in 0..content.length && selEnd in selStart..content.length) {
                    et.setSelection(selStart, selEnd)
                }
                if (hadFocus) et.requestFocus()
            }
        }
    }

    // Back press handling: dismiss keyboard first, then search
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

    val isFileOpen = openTabs.isNotEmpty() || currentUri != null || content.isNotEmpty() || fileName.isNotEmpty()
    val title = when {
        fileName.isEmpty() -> "文本编辑器"
        else -> fileName + if (isModified) "  ●" else ""
    }

    fun scrollToSearchMatch() {
        val pos = viewModel.getSearchPosition() ?: return
        editTextRef.value?.let { et ->
            et.setSelection(pos.first, pos.first + pos.second)
        }
    }

    fun dismissKeyboard() {
        editTextRef.value?.let { et ->
            et.clearFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
            isKeyboardVisible.value = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
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
                TopAppBar(
                    title = {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                    if (isFileOpen) {
                        // Essential actions always visible
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                        IconButton(
                            onClick = {
                                val et = editTextRef.value ?: return@IconButton
                                val result = viewModel.undoManager.prepareUndo()
                                if (result != null) {
                                    try {
                                        et.setText(result)
                                    } finally {
                                        viewModel.undoManager.finishUndoRedo()
                                    }
                                }
                            },
                            enabled = viewModel.canUndo
                        ) {
                            Icon(Icons.Filled.Undo, contentDescription = "撤销")
                        }
                        IconButton(
                            onClick = {
                                val et = editTextRef.value ?: return@IconButton
                                val result = viewModel.undoManager.prepareRedo()
                                if (result != null) {
                                    try {
                                        et.setText(result)
                                    } finally {
                                        viewModel.undoManager.finishUndoRedo()
                                    }
                                }
                            },
                            enabled = viewModel.canRedo
                        ) {
                            Icon(Icons.Filled.Redo, contentDescription = "重做")
                        }
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
                        // Overflow menu for less-used actions
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "更多操作"
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
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
                                    leadingIcon = {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("跳转到行") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showGoToLineDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Numbers, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("文本统计") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showStatsDialog = true
                                        isStatsLoading = true
                                        statsResult = null
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.BarChart, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("设置") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showSettingsDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Settings, contentDescription = null)
                                    }
                                )
                            }
                        }
                    } else {
                        Row {
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置")
                            }
                            IconButton(onClick = { openFileLauncher.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Filled.Add, contentDescription = "打开文件")
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
            if (openTabs.isNotEmpty()) {
                TabBar(
                    tabs = openTabs,
                    activeIndex = activeTabIndex,
                    maxTabs = maxTabs,
                    onTabClick = { idx -> viewModel.switchToTab(idx) },
                    onTabClose = { idx -> viewModel.closeTab(idx) },
                    onTabMove = { from, to -> viewModel.moveTab(from, to) }
                )
            }

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

            if (!isFileOpen && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalButton(
                            onClick = { openFileLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("打开文件")
                        }
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { viewModel.createNewFile() }
                        ) {
                            Text("新建文件")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusable(true)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    AndroidView(
                        factory = { ctx ->
                            LinedEditText(ctx, showLineNumbers = showLineNumbers).also { et ->
                                // inputType MUST be set first — it resets internal flags
                                et.inputType = EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
                                        EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                et.setHorizontallyScrolling(!wordWrap)
                                et.gravity = Gravity.TOP
                                et.isVerticalScrollBarEnabled = true
                                et.movementMethod = android.text.method.ScrollingMovementMethod()
                                et.textSize = fontSize.toFloat()
                                et.typeface = android.graphics.Typeface.MONOSPACE
                                et.hint = "在此输入文本..."
                                et.maxLines = Int.MAX_VALUE
                                et.minLines = 1

                                // IME action: handle keyboard dismiss via back key
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

                                et.addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(
                                        s: CharSequence, start: Int, count: Int, after: Int
                                    ) {}

                                    override fun onTextChanged(
                                        s: CharSequence, start: Int, before: Int, count: Int
                                    ) {}

                                    override fun afterTextChanged(s: Editable) {
                                        ignoreTextChange = true
                                        viewModel.onTextChanged(s.toString())
                                    }
                                })

                                editTextRef.value = et
                                et.requestFocus()
                            }
                        },
                        update = { et ->
                            if (et.textSize != fontSize.toFloat()) {
                                et.textSize = fontSize.toFloat()
                            }
                            et.setHorizontallyScrolling(!wordWrap)
                            et.setShowLineNumbers(showLineNumbers)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    }

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

    if (showStatsDialog) {
        StatsDialog(
            text = content,
            isLoading = isStatsLoading,
            result = statsResult,
            onDismiss = { showStatsDialog = false },
            onResult = { result ->
                statsResult = result
                isStatsLoading = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = viewModel.settings,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    settings: SettingsManager,
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

@Composable
private fun SearchBar(
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
private fun TabBar(
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
                item {
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
private fun GoToLineDialog(
    totalLines: Int,
    onDismiss: () -> Unit,
    onGoToLine: (Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到行") },
        text = {
            Column {
                Text(
                    text = "共 $totalLines 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { v ->
                        input = v.filter { it.isDigit() }
                        error = null
                    },
                    label = { Text("行号") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
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

@Composable
private fun StatsDialog(
    text: String,
    isLoading: Boolean,
    result: StatsResult?,
    onDismiss: () -> Unit,
    onResult: (StatsResult) -> Unit
) {
    LaunchedEffect(text) {
        if (isLoading && result == null) {
            val r = withContext(Dispatchers.Default) { computeStats(text) }
            onResult(r)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文本统计") },
        text = {
            if (isLoading && result == null) {
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

private data class StatsResult(
    val totalCharsWithSpace: String,
    val totalCharsNoSpace: String,
    val totalLines: String,
    val nonEmptyLines: String,
    val totalWords: String,
    val totalParagraphs: String,
    val chineseChars: String,
    val englishChars: String,
    val digitChars: String,
    val punctuationChars: String,
    val spaceChars: String,
    val estimatedReadTime: String
) {
    val totalLinesValue: Int
        get() = totalLines.replace(",", "").toIntOrNull() ?: 0
}

private fun computeStats(text: String): StatsResult {
    val fmt = NumberFormat.getIntegerInstance()
    val totalCharsWithSpace = text.length
    val totalCharsNoSpace = text.count { !it.isWhitespace() }
    val lines = text.lines()
    val totalLines = lines.size
    val nonEmptyLines = lines.count { it.isNotBlank() }
    val totalWords = text.split(Regex("\\s+")).count { it.isNotEmpty() }
    val paragraphs = text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }

    val chineseChars = text.count { it in '一'..'鿿' || it in '㐀'..'䶿' }
    val englishChars = text.count { it in 'a'..'z' || it in 'A'..'Z' }
    val digitChars = text.count { it in '0'..'9' }
    val punctSet = setOf('，','。','、','；','：','？','！','.',',',';',':','?','!','"','\'','(',')','（','）','【','】','《','》','<','>','—','…','·')
    val punctuationChars = text.count { it in punctSet }
    val spaceChars = text.count { it.isWhitespace() }

    val readTimeMinutes = (totalWords / 200f).coerceAtLeast(0.1f)
    val readTimeStr = if (readTimeMinutes < 1) {
        "${(readTimeMinutes * 60).toInt()} 秒"
    } else {
        "${readTimeMinutes.toInt()} 分 ${((readTimeMinutes % 1) * 60).toInt()} 秒"
    }

    return StatsResult(
        totalCharsWithSpace = fmt.format(totalCharsWithSpace),
        totalCharsNoSpace = fmt.format(totalCharsNoSpace),
        totalLines = fmt.format(totalLines),
        nonEmptyLines = fmt.format(nonEmptyLines),
        totalWords = fmt.format(totalWords),
        totalParagraphs = fmt.format(paragraphs),
        chineseChars = fmt.format(chineseChars),
        englishChars = fmt.format(englishChars),
        digitChars = fmt.format(digitChars),
        punctuationChars = fmt.format(punctuationChars),
        spaceChars = fmt.format(spaceChars),
        estimatedReadTime = readTimeStr
    )
}

// ── Gutter and editor colour helpers: returns light/dark-appropriate colours ──
private fun gutterColors(isDark: Boolean) = if (isDark) {
    // bg: slightly lighter than editor bg so gutter is subtly distinct
    // divider: soft border between gutter and text
    // num: bright enough to read easily on dark
    Triple(0xFF1A1A1A.toInt(), 0xFF3A3A3A.toInt(), 0xFFAAAAAA.toInt())
} else {
    Triple(0xFFF0F0F0.toInt(), 0xFFD0D0D0.toInt(), 0xFF888888.toInt())
}

private fun editorColors(isDark: Boolean) = if (isDark) {
    Triple(
        0xFFEEEEEE.toInt(),  // text color: near white — clearly visible on dark background
        0xFF121212.toInt(),  // background color: darker for better contrast
        0xFFBB86FC.toInt()   // cursor/handle color: accent purple
    )
} else {
    Triple(
        0xFF1A1A1A.toInt(),  // text color: near black — clearly visible on light background
        0xFFFFFFFF.toInt(),  // background color: white
        0xFF6650A4.toInt()   // cursor/handle color: accent purple
    )
}

/**
 * Custom EditText with line numbers drawn in the left gutter.
 * Supports show/hide line numbers and dark/light theme-aware colors.
 *
 * Key design decisions for large-file performance:
 * - Only draws line numbers for lines visible on screen (no full-list rendering)
 * - Uses a reusable CharArray for number formatting to avoid per-frame String allocations
 * - Draws the gutter AFTER super.onDraw() so line numbers always render on top
 * - Gutter is drawn inside a clip-rect for clean boundaries
 */
class LinedEditText(
    context: Context,
    attrs: AttributeSet? = null,
    private var showLineNumbers: Boolean = true
) : AppCompatEditText(context, attrs) {

    private var gutterWidthPx: Float = 0f
    private var gutterMarginPx: Float = 0f

    private val gutterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
    }
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    // Reusable buffer for formatting line numbers — avoids String allocations during scroll
    private val tmpChars = CharArray(10)

    init {
        val density = resources.displayMetrics.density
        gutterWidthPx = 56f * density
        gutterMarginPx = 6f * density
        lineNumberPaint.textSize = 12f * density
        updateAllColors()
        updatePadding()
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /** Detect system dark mode from Configuration */
    private fun isSystemInDarkMode(): Boolean {
        val nightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateAllColors() {
        val isDark = isSystemInDarkMode()

        // ── gutter colours ──
        val (bg, div, num) = gutterColors(isDark)
        gutterBgPaint.color = bg
        gutterDividerPaint.color = div
        lineNumberPaint.color = num

        // ── editor text & background colours ──
        val (textCol, backCol, accentCol) = editorColors(isDark)
        setTextColor(textCol)
        setBackgroundColor(backCol)
        // selection highlight: light on dark, dark on light
        highlightColor = if (isDark) 0x44FFFFFF.toInt() else 0x33000000.toInt()
        // cursor / text-select handle colour
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable?.setTint(accentCol)
            }
        } catch (_: Exception) {}
    }

    private fun updatePadding() {
        val density = resources.displayMetrics.density
        val leftPad = if (showLineNumbers) {
            (gutterWidthPx + 8f * density).toInt()
        } else {
            (8f * density).toInt()
        }
        setPadding(leftPad, paddingTop, paddingRight, paddingBottom)
    }

    fun setShowLineNumbers(show: Boolean) {
        if (showLineNumbers != show) {
            showLineNumbers = show
            updatePadding()
            invalidate()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        // Re-apply colours when system switches between light/dark mode
        updateAllColors()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            // Only show keyboard if not already focused (prevent re-showing)
            if (!hasFocus()) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        // Dismiss keyboard on back key
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            clearFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
            return false // let the system handle the rest
        }
        return super.onKeyPreIme(keyCode, event)
    }

    fun scrollToLine(line: Int) {
        val l = layout ?: return
        val targetLine = (line - 1).coerceIn(0, l.lineCount - 1)
        scrollTo(scrollX, l.getLineTop(targetLine))
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Let the EditText draw its text, cursor, selection etc. first
        super.onDraw(canvas)

        // 2. Draw the gutter on top so line numbers are never covered by text rendering
        if (showLineNumbers) {
            val l = layout
            if (l != null && l.lineCount > 0) {
                drawGutter(canvas, l)
            }
        }
    }

    private fun drawGutter(canvas: Canvas, layout: android.text.Layout) {
        val viewHeight = height - paddingTop - paddingBottom
        if (viewHeight <= 0) return

        // Clip to gutter bounds for clean rendering
        val clipLeft = 0f
        val clipTop = paddingTop.toFloat()
        val clipRight = gutterWidthPx
        val clipBottom = (height - paddingBottom).toFloat()
        canvas.save()
        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom)

        // Gutter background and divider
        canvas.drawRect(clipLeft, clipTop, clipRight, clipBottom, gutterBgPaint)
        canvas.drawLine(gutterWidthPx, clipTop, gutterWidthPx, clipBottom, gutterDividerPaint)

        // Draw visible line numbers with safety margin to prevent edge-case clipping
        val firstVisibleLine = maxOf(0, layout.getLineForVertical(scrollY) - 2)
        val contentBottom = scrollY + viewHeight
        val lastVisibleLine = minOf(layout.lineCount - 1, layout.getLineForVertical(contentBottom) + 2)

        val padTop = paddingTop.toFloat()
        val scrollYf = scrollY.toFloat()
        val numX = gutterWidthPx - gutterMarginPx

        for (line in firstVisibleLine..lastVisibleLine) {
            val baseline = layout.getLineBaseline(line).toFloat() - scrollYf + padTop
            drawLineNumber(canvas, line + 1, numX, baseline)
        }

        canvas.restore()
    }

    /** Format [num] into [tmpChars] and draw it — zero String allocation. */
    private fun drawLineNumber(canvas: Canvas, num: Int, x: Float, baseline: Float) {
        var n = num
        var pos = tmpChars.size
        if (n == 0) {
            tmpChars[--pos] = '0'
        } else {
            while (n > 0) {
                tmpChars[--pos] = ('0' + (n % 10))
                n /= 10
            }
        }
        canvas.drawText(tmpChars, pos, tmpChars.size - pos, x, baseline, lineNumberPaint)
    }
}
