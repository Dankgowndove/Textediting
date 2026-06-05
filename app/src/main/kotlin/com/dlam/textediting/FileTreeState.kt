package com.dlam.textediting

import android.net.Uri

data class FileTreeState(
    val rootUri: Uri? = null,
    val nodes: List<FileNode> = emptyList(),
    val expandedUris: Set<Uri> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class FileNode(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int
)

data class OpenTab(
    val uri: Uri?,
    val fileName: String,
    val isModified: Boolean = false,
    val content: String = "",
    val savedText: String = ""
)

data class GlobalSearchResult(
    val fileUri: Uri,
    val fileName: String,
    val lineNumber: Int,
    val lineContent: String,
    val matchStart: Int
)
