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
    var isSelectingRoot by remember { mutableStateOf(false) }

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
                            style = MaterialTheme.typography.labelLarge,
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
                contextMenuUri = null
            },
            onNewFolder = {
                showNewFolderDialog = true
                contextMenuUri = null
            },
            onDelete = {
                showDeleteConfirm = true
            },
            onRename = {
                showRenameDialog = true
                contextMenuUri = null
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
    if (showNewFileDialog && contextMenuUri != null) {
        CreateItemDialog(
            title = "新建文件",
            hint = "文件名（默认 .txt）",
            onDismiss = { showNewFileDialog = false; contextMenuUri = null },
            onConfirm = { name ->
                contextMenuUri?.let { viewModel.createFile(it, name) }
                showNewFileDialog = false
                contextMenuUri = null
            }
        )
    }

    // ── 新建文件夹对话框 ──
    if (showNewFolderDialog && contextMenuUri != null) {
        CreateItemDialog(
            title = "新建文件夹",
            hint = "文件夹名称",
            onDismiss = { showNewFolderDialog = false; contextMenuUri = null },
            onConfirm = { name ->
                contextMenuUri?.let { viewModel.createFolder(it, name) }
                showNewFolderDialog = false
                contextMenuUri = null
            }
        )
    }

    // ── 重命名对话框 ──
    if (showRenameDialog && contextMenuUri != null) {
        RenameDialog(
            currentName = contextMenuName,
            onDismiss = { showRenameDialog = false; contextMenuUri = null; contextMenuName = "" },
            onConfirm = { name ->
                contextMenuUri?.let { viewModel.renameFile(it, name) }
                showRenameDialog = false
                contextMenuUri = null
                contextMenuName = ""
            }
        )
    }

    // ── 删除确认对话框 ──
    if (showDeleteConfirm && contextMenuUri != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; contextMenuUri = null },
            title = { Text("确认删除") },
            text = { Text("此操作不可撤销，确定要删除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    contextMenuUri?.let { viewModel.deleteFile(it) }
                    showDeleteConfirm = false
                    contextMenuUri = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; contextMenuUri = null }) { Text("取消") }
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
    val bgColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer  // 活跃文件高亮
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (16 + node.depth * 20).dp)  // 根据深度缩进
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 目录/文件图标
        Icon(
            imageVector = if (node.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (node.isDirectory)
                MaterialTheme.colorScheme.tertiary      // 目录使用第三色
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作") },
        text = {
            Column {
                // 目录专属操作
                if (isDirectory) {
                    TextButton(
                        onClick = onNewFile,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.NoteAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("新建文件")
                    }
                    TextButton(
                        onClick = onNewFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("新建文件夹")
                    }
                }
                // 通用操作
                TextButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重命名")
                }
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("复制")
                }
                // 粘贴仅对目录有效
                if (isDirectory) {
                    TextButton(
                        onClick = onPaste,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("粘贴")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                // 删除操作（红色警告）
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
                            // 文件名（主题色）
                            Text(
                                text = result.fileName,
                                style = MaterialTheme.typography.bodySmall,
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
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
