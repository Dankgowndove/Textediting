package com.dlam.textediting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlam.textediting.dialogs.GlobalReplaceDialog

/**
 * 文件树侧边栏主组件
 *
 * 包含：工具栏（选择目录/刷新/全局搜索）、文件树列表、全局搜索面板、
 * 右键上下文菜单及相关的操作对话框。
 *
 * 侧边栏宽度固定为 280dp，通过 [ModalNavigationDrawer] 实现抽屉效果。
 *
 * @param viewModel 主 ViewModel
 * @param onClose 关闭侧边栏回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeSidebar(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    // ── 从 ViewModel 收集状态 ──
    val fileTreeState by viewModel.fileTree.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val globalSearchQuery by viewModel.globalSearchQuery.collectAsState()
    val globalSearchResults by viewModel.globalSearchResults.collectAsState()
    val isGlobalSearching by viewModel.isGlobalSearching.collectAsState()
    val recentFilesList by viewModel.recentFiles.recentFiles.collectAsState()

    // ── 对话框和菜单状态 ──
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }

    // 上下文菜单：长按文件/目录时记录的目标信息
    var contextMenuUri by remember { mutableStateOf<Uri?>(null) }
    var contextMenuIsDir by remember { mutableStateOf(false) }
    var contextMenuName by remember { mutableStateOf("") }
    // 待删除的 URI（独立于 contextMenuUri，避免 onClick 中 onDismiss 提前清空）
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
    // 待操作的 URI（用于新建文件/文件夹、重命名等需要 URI 的操作）
    var pendingActionUri by remember { mutableStateOf<Uri?>(null) }
    var pendingActionName by remember { mutableStateOf("") }

    // 目录选择器启动器（SAF OpenDocumentTree）
    val rootDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.selectRootDir(it) } }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // ── 顶部工具栏 ──
        TopAppBar(
            title = {
                Text(
                    text = if (fileTreeState.rootUri != null) "文件浏览器" else "工作区",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "关闭侧边栏")
                }
            },
            actions = {
                // 全局搜索切换
                IconButton(onClick = {
                    showGlobalSearch = !showGlobalSearch
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "全局搜索")
                }
                // 选择工作区目录
                IconButton(onClick = {
                    rootDirLauncher.launch(null)
                }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "选择工作区")
                }
                // 刷新文件树（仅在有根目录时显示）
                if (fileTreeState.rootUri != null) {
                    IconButton(onClick = { viewModel.refreshFileTree() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // ── 全局搜索面板（动画展开/收起）──
        AnimatedVisibility(visible = showGlobalSearch) {
            GlobalSearchPanel(
                query = globalSearchQuery,
                results = globalSearchResults,
                isSearching = isGlobalSearching,
                onQueryChange = { viewModel.startGlobalSearch(it) },
                onResultClick = { result ->
                    viewModel.openFileFromGlobalSearch(result.fileUri, result.lineNumber)
                    onClose()
                },
                onReplaceClick = { showReplaceDialog = true }
            )
        }

        // ── 内容区域（根据状态显示不同内容）──
        when {
            // 加载中
            fileTreeState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // 加载出错
            fileTreeState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "错误：${fileTreeState.error}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            // 未选择工作区
            fileTreeState.rootUri == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    // 选择目录引导
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "请选择工作区目录",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = { rootDirLauncher.launch(null) }) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("选择目录")
                            }
                        }
                    }

                    // 最近打开文件列表
                    if (recentFilesList.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Text(
                            "最近打开",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                        recentFilesList.take(15).forEach { recent ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 1.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        viewModel.openFile(recent.uri)
                                        onClose()
                                    },
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(10.dp))
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
            // 目录为空
            fileTreeState.nodes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("目录为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 显示文件树
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(fileTreeState.nodes, key = { it.uri.toString() }) { node ->
                        FileTreeItem(
                            node = node,
                            isActive = openTabs.getOrNull(activeTabIndex)?.uri?.toString() == node.uri.toString(),
                            onClick = {
                                if (node.isDirectory) {
                                    viewModel.toggleExpandDir(node.uri)  // 目录：展开/折叠
                                } else {
                                    viewModel.openFile(node.uri)         // 文件：打开
                                    onClose()
                                }
                            },
                            onLongClick = {
                                // 长按显示上下文菜单
                                contextMenuUri = node.uri
                                contextMenuIsDir = node.isDirectory
                                contextMenuName = node.name
                            }
                        )
                    }
                }
            }
        }
    }

    // ── 上下文菜单对话框 ──
    if (contextMenuUri != null) {
        ContextMenuDialog(
            isDirectory = contextMenuIsDir,
            onDismiss = { contextMenuUri = null },
            onNewFile = {
                showNewFileDialog = true
                pendingActionUri = contextMenuUri
            },
            onNewFolder = {
                showNewFolderDialog = true
                pendingActionUri = contextMenuUri
            },
            onDelete = {
                pendingDeleteUri = contextMenuUri
                showDeleteConfirm = true
            },
            onRename = {
                showRenameDialog = true
                pendingActionUri = contextMenuUri
                pendingActionName = contextMenuName
            },
            onCopy = {
                contextMenuUri?.let { viewModel.copyFileToClipboard(it) }
                contextMenuUri = null
            },
            onPaste = {
                contextMenuUri?.let { viewModel.pasteFile(it) }
                contextMenuUri = null
            }
        )
    }

    // ── 新建文件对话框 ──
    if (showNewFileDialog && pendingActionUri != null) {
        CreateItemDialog(
            title = "新建文件",
            hint = "文件名（默认 .txt）",
            onDismiss = { showNewFileDialog = false; pendingActionUri = null },
            onConfirm = { name ->
                pendingActionUri?.let { viewModel.createFile(it, name) }
                showNewFileDialog = false
                pendingActionUri = null
            }
        )
    }

    // ── 新建文件夹对话框 ──
    if (showNewFolderDialog && pendingActionUri != null) {
        CreateItemDialog(
            title = "新建文件夹",
            hint = "文件夹名称",
            onDismiss = { showNewFolderDialog = false; pendingActionUri = null },
            onConfirm = { name ->
                pendingActionUri?.let { viewModel.createFolder(it, name) }
                showNewFolderDialog = false
                pendingActionUri = null
            }
        )
    }

    // ── 重命名对话框 ──
    if (showRenameDialog && pendingActionUri != null) {
        RenameDialog(
            currentName = pendingActionName,
            onDismiss = {
                showRenameDialog = false
                pendingActionUri = null
                pendingActionName = ""
            },
            onConfirm = { name ->
                pendingActionUri?.let { viewModel.renameFile(it, name) }
                showRenameDialog = false
                pendingActionUri = null
                pendingActionName = ""
            }
        )
    }

    // ── 删除确认对话框 ──
    if (showDeleteConfirm && pendingDeleteUri != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; pendingDeleteUri = null },
            title = { Text("确认删除") },
            text = { Text("此操作不可撤销，确定要删除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteUri?.let { viewModel.deleteFile(it) }
                    showDeleteConfirm = false
                    pendingDeleteUri = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; pendingDeleteUri = null }) { Text("取消") }
            }
        )
    }

    // ── 全局替换对话框 ──
    if (showReplaceDialog) {
        GlobalReplaceDialog(
            onDismiss = { showReplaceDialog = false },
            onReplace = { find, replace, onlyCurrent ->
                viewModel.performGlobalReplace(find, replace, onlyCurrent)
                showReplaceDialog = false
            }
        )
    }
}

/**
 * 文件树中单个条目的 Composable
 *
 * 根据节点深度缩进（每层 20dp），目录显示文件夹图标，文件显示文档图标。
 * 当前打开的文件使用 primaryContainer 背景色高亮。
 *
 * @param node 文件节点
 * @param isActive 是否为当前打开的文件
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeItem(
    node: FileNode,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // [M3 优化] 使用 Surface 替代 Row+background，获得正确的点击涟漪和 elevation
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (16 + node.depth * 20).dp)  // 根据深度缩进
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = when {
            isActive -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isActive) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 目录/文件图标
            Icon(
                imageVector = if (node.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (node.isDirectory)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            // 文件名
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 文件/目录上下文菜单对话框
 *
 * 目录类型显示更多操作（新建文件/文件夹、粘贴），文件类型操作较少。
 *
 * @param isDirectory 是否为目录
 * @param onDismiss 关闭回调
 * @param onNewFile 新建文件回调
 * @param onNewFolder 新建文件夹回调
 * @param onDelete 删除回调
 * @param onRename 重命名回调
 * @param onCopy 复制回调
 * @param onPaste 粘贴回调
 */
/**
 * [M3 修复] 长按上下文菜单 — 使用 Card + Box 实现 DropdownMenu 风格菜单，
 * 替代原 AlertDialog 反模式（AlertDialog 应用于确认提示，非操作菜单）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextMenuDialog(
    isDirectory: Boolean,
    onDismiss: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    // 全屏透明遮罩，点击外部关闭
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.widthIn(min = 200.dp)) {
                // 目录专属操作
                if (isDirectory) {
                    DropdownMenuItem(
                        text = { Text("新建文件") },
                        onClick = { onNewFile(); onDismiss() },
                        leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("新建文件夹") },
                        onClick = { onNewFolder(); onDismiss() },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) }
                    )
                }
                // 通用操作
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = { onRename(); onDismiss() },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = { onCopy(); onDismiss() },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }
                )
                // 粘贴仅对目录有效
                if (isDirectory) {
                    DropdownMenuItem(
                        text = { Text("粘贴") },
                        onClick = { onPaste(); onDismiss() },
                        leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                // 删除操作（红色警告）
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = { onDelete(); onDismiss() },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

/**
 * 新建文件/文件夹对话框
 *
 * @param title 对话框标题
 * @param hint 输入框提示文字
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调（传入输入的名称）
 */
@Composable
private fun CreateItemDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(hint) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onConfirm(input.trim()) },
                enabled = input.isNotBlank()  // 空输入禁用
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 重命名对话框
 *
 * 预填当前文件名，允许用户修改。
 *
 * @param currentName 当前文件名
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调（传入新名称）
 */
@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("新名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onConfirm(input.trim()) },
                enabled = input.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 全局搜索面板
 *
 * 在文件树侧边栏内嵌入的搜索组件，支持在指定目录中搜索文件内容。
 * 显示搜索结果列表，每项显示文件名、行号和匹配行内容。
 *
 * @param query 搜索查询
 * @param results 搜索结果列表
 * @param isSearching 是否正在搜索
 * @param onQueryChange 查询文本变化回调
 * @param onResultClick 结果项点击回调
 * @param onReplaceClick 替换按钮点击回调
 */
@Composable
private fun GlobalSearchPanel(
    query: String,
    results: List<GlobalSearchResult>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onResultClick: (GlobalSearchResult) -> Unit,
    onReplaceClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        // 搜索输入框
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索文件内容...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        // 搜索结果统计与替换按钮
        if (query.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSearching) "搜索中..." else "共 ${results.size} 个结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onReplaceClick) {
                    Icon(Icons.Filled.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("替换", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // 搜索结果列表（最多 300dp 高度）
        if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                items(results) { result ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onResultClick(result) },
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            // 文件名（主题色，略大字体）
                            Text(
                                text = result.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // 行号 + 匹配行内容
                            Row {
                                Text(
                                    text = "${result.lineNumber}: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = result.lineContent,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
