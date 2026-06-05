package com.dlam.textediting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
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
    val charCount by remember { derivedStateOf { content.length } }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showStatsDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var statsResult by remember { mutableStateOf<StatsResult?>(null) }
    var isStatsLoading by remember { mutableStateOf(false) }

    val editTextRef = remember { mutableStateOf<LinedEditText?>(null) }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.openFile(it) } }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.saveAs(it) } }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (isSearchVisible) {
        BackHandler(onBack = viewModel::dismissSearch)
    }

    val isFileOpen = currentUri != null || content.isNotEmpty() || fileName.isNotEmpty()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (isFileOpen) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val lines = content.lines()
                                val sb = StringBuilder(lines.size * 8)
                                for (i in lines.indices) sb.append(i + 1).append('\n')
                                clipboard.setPrimaryClip(ClipData.newPlainText("行号", sb.trimEnd().toString()))
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "复制行号")
                            }
                            IconButton(onClick = { showGoToLineDialog = true }) {
                                Icon(Icons.Filled.Numbers, contentDescription = "跳转到行")
                            }
                            IconButton(onClick = {
                                showStatsDialog = true
                                isStatsLoading = true
                                statsResult = null
                            }) {
                                Icon(Icons.Filled.BarChart, contentDescription = "统计")
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
                        }
                    } else {
                        IconButton(onClick = { openFileLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.Add, contentDescription = "打开文件")
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
                    onClose = viewModel::dismissSearch
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
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    AndroidView(
                        factory = { ctx ->
                            LinedEditText(ctx).also { et ->
                                et.isVerticalScrollBarEnabled = true
                                et.textSize = 14f
                                et.typeface = android.graphics.Typeface.MONOSPACE
                                et.hint = "在此输入文本..."
                                et.setHorizontallyScrolling(false)
                                et.maxLines = Integer.MAX_VALUE

                                et.addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(
                                        s: CharSequence, start: Int, count: Int, after: Int
                                    ) {}

                                    override fun onTextChanged(
                                        s: CharSequence, start: Int, before: Int, count: Int
                                    ) {}

                                    override fun afterTextChanged(s: Editable) {
                                        viewModel.onTextChanged(s.toString())
                                    }
                                })

                                editTextRef.value = et
                                et.requestFocus()
                            }
                        },
                        update = { et ->
                            if (et.text?.toString() != content) {
                                val hadFocus = et.hasFocus()
                                et.setText(content)
                                if (hadFocus) et.requestFocus()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
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

    val chineseChars = text.count { it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' }
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

/**
 * Custom EditText with line numbers drawn in the left gutter.
 * Only draws visible line numbers for 100k+ line performance.
 */
class LinedEditText(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val gutterWidthPx: Float
    private val gutterMarginPx: Float

    private val gutterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F0F0F0")
    }
    private val gutterDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#D0D0D0")
        strokeWidth = 1.5f
    }
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#888888")
        textAlign = Paint.Align.RIGHT
    }

    init {
        val density = resources.displayMetrics.density
        gutterWidthPx = 48f * density
        gutterMarginPx = 6f * density
        lineNumberPaint.textSize = 12f * density
        setPadding(
            (gutterWidthPx + 8f * density).toInt(),
            paddingTop,
            paddingRight,
            paddingBottom
        )
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
        return super.onTouchEvent(event)
    }

    fun scrollToLine(line: Int) {
        val l = layout ?: return
        val targetLine = (line - 1).coerceIn(0, l.lineCount - 1)
        scrollTo(scrollX, l.getLineTop(targetLine))
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout
        if (l != null) drawGutter(canvas, l)
        super.onDraw(canvas)
    }

    private fun drawGutter(canvas: Canvas, layout: android.text.Layout) {
        val viewHeight = height - paddingTop - paddingBottom
        if (viewHeight <= 0) return

        canvas.drawRect(0f, 0f, gutterWidthPx, height.toFloat(), gutterBgPaint)
        canvas.drawLine(gutterWidthPx, 0f, gutterWidthPx, height.toFloat(), gutterDividerPaint)

        val firstVisibleLine = maxOf(0, layout.getLineForVertical(scrollY))
        val lastVisibleLine = minOf(layout.lineCount - 1, layout.getLineForVertical(scrollY + viewHeight))

        for (line in firstVisibleLine..lastVisibleLine) {
            canvas.drawText(
                (line + 1).toString(),
                gutterWidthPx - gutterMarginPx,
                layout.getLineBaseline(line).toFloat() - scrollY.toFloat() + paddingTop.toFloat(),
                lineNumberPaint
            )
        }
    }
}
