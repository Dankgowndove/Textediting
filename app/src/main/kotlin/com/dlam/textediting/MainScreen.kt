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
import androidx.compose.animation.AnimatedVisibility
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
    val syntaxHighlight by viewModel.settings.syntaxHighlight.collectAsState()
    val bracketMatching by viewModel.settings.bracketMatching.collectAsState()
    val highlightCurrentLine by viewModel.settings.highlightCurrentLine.collectAsState()
    val showWhitespace by viewModel.settings.showWhitespace.collectAsState()
    val autoSaveInterval by viewModel.settings.autoSaveInterval.collectAsState()
    val recentFilesList by viewModel.recentFiles.recentFiles.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var showStatsDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val editTextRef = remember { mutableStateOf<LinedEditText?>(null) }
    val isKeyboardVisible = remember { mutableStateOf(false) }
    var ignoreTextChange by remember { mutableStateOf(false) }

    // Detect system dark mode for editor theming
    val isDarkMode = androidx.compose.foundation.isSystemInDarkTheme()

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.openFile(it) } }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.saveAs(it) } }

    val scope = rememberCoroutineScope()

    // Collect snackbar events
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Collect scroll-to-line events
    LaunchedEffect(Unit) {
        viewModel.pendingScrollToLine.collect { line ->
            kotlinx.coroutines.delay(150)
            editTextRef.value?.scrollToLine(line)
        }
    }

    // ── Auto-save timer ──
    LaunchedEffect(isModified, autoSaveInterval, currentUri) {
        viewModel.scheduleAutoSave()
    }

    // ── Syntax highlighting trigger ──
    LaunchedEffect(content, fileName, syntaxHighlight) {
        if (syntaxHighlight && content.isNotEmpty() && fileName.isNotEmpty()) {
            viewModel.triggerSyntaxHighlight(content, fileName, isDarkMode)
        }
    }

    // ── Apply syntax highlighting when results are ready ──
    LaunchedEffect(Unit) {
        viewModel.highlightsReady.collect {
            editTextRef.value?.let { et ->
                val spannable = et.text as? Spannable ?: return@collect
                viewModel.applyHighlightIfReady(spannable, isDarkMode)
            }
        }
    }

    // Handle external text changes (file open, undo/redo, tab switch).
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

    // Back press: dismiss keyboard first, then search
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
                            // Overflow menu
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "更多操作")
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
                                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("跳转到行") },
                                        onClick = {
                                            showOverflowMenu = false
                                            showGoToLineDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("文本统计") },
                                        onClick = {
                                            showOverflowMenu = false
                                            showStatsDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) }
                                    )
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
                // ── Tab bar ──
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

                // ── Search bar ──
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

                // ── Content area ──
                if (!isFileOpen && !isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "文本编辑器",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))
                            FilledTonalButton(
                                onClick = { openFileLauncher.launch(arrayOf("*/*")) }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("打开文件")
                            }
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { viewModel.createNewFile() }
                            ) {
                                Text("新建文件")
                            }

                            // ── Recent files ──
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
                                LinedEditText(ctx).also { et ->
                                    et.inputType = EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
                                            EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                    et.setHorizontallyScrolling(!wordWrap)
                                    et.isVerticalScrollBarEnabled = true
                                    et.textSize = fontSize.toFloat()
                                    et.typeface = android.graphics.Typeface.MONOSPACE
                                    et.hint = "在此输入文本..."
                                    et.maxLines = Int.MAX_VALUE
                                    et.minLines = 1
                                    et.showLineNumbers = showLineNumbers
                                    et.darkMode = isDarkMode
                                    et.highlightCurrentLine = highlightCurrentLine
                                    et.bracketMatching = bracketMatching
                                    et.showWhitespace = showWhitespace

                                    // IME action
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

                                    // Text change listener
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
                                et.showLineNumbers = showLineNumbers
                                et.darkMode = isDarkMode
                                et.highlightCurrentLine = highlightCurrentLine
                                et.bracketMatching = bracketMatching
                                et.showWhitespace = showWhitespace
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ──
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
            onDismiss = { showStatsDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = viewModel.settings,
            onDismiss = { showSettingsDialog = false }
        )
    }
}
