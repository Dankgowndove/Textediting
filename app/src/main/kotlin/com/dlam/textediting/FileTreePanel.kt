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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeSidebar(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val fileTreeState by viewModel.fileTree.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val globalSearchQuery by viewModel.globalSearchQuery.collectAsState()
    val globalSearchResults by viewModel.globalSearchResults.collectAsState()
    val isGlobalSearching by viewModel.isGlobalSearching.collectAsState()

    var showGlobalSearch by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var contextMenuUri by remember { mutableStateOf<Uri?>(null) }
    var contextMenuIsDir by remember { mutableStateOf(false) }
    var contextMenuName by remember { mutableStateOf("") }
    var isSelectingRoot by remember { mutableStateOf(false) }

    val rootDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.selectRootDir(it) } }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
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
                IconButton(onClick = {
                    showGlobalSearch = !showGlobalSearch
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "全局搜索")
                }
                IconButton(onClick = {
                    rootDirLauncher.launch(null)
                }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "选择工作区")
                }
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

        when {
            fileTreeState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
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
            fileTreeState.rootUri == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
            }
            fileTreeState.nodes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("目录为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                                    viewModel.toggleExpandDir(node.uri)
                                } else {
                                    viewModel.openFile(node.uri)
                                    onClose()
                                }
                            },
                            onLongClick = {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeItem(
    node: FileNode,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (16 + node.depth * 20).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                enabled = input.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

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
                            Text(
                                text = result.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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

