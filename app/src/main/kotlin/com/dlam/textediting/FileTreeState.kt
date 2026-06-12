/**
 * 数据模型定义文件
 *
 * 包含项目中所有核心数据类：
 * - FileTreeState：文件树 UI 状态
 * - FileNode：文件树节点
 * - OpenTab：标签页快照
 * - GlobalSearchResult：全局搜索结果
 */
package com.dlam.textediting

import android.net.Uri

/**
 * 文件树完整状态
 *
 * @property rootUri 工作区根目录 URI（null 表示未选择）
 * @property nodes 当前展开状态下的节点列表（扁平化）
 * @property expandedUris 已展开目录的 URI 集合
 * @property isLoading 是否正在加载
 * @property error 错误信息（null 表示无错误）
 */
data class FileTreeState(
    val rootUri: Uri? = null,
    val nodes: List<FileNode> = emptyList(),
    val expandedUris: Set<Uri> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 文件树中的单个节点
 *
 * @property uri 文件/目录的 SAF URI
 * @property name 显示名称
 * @property isDirectory 是否为目录
 * @property depth 在树中的深度（根目录 depth=0，用于计算缩进）
 */
data class FileNode(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int
)

/**
 * 打开的标签页快照
 *
 * 切换标签页时，当前编辑器状态会序列化到此对象，
 * 从目标标签页恢复时再反序列化。
 *
 * @property uri 文件 URI（未保存的新文件为 null）
 * @property fileName 文件名
 * @property isModified 是否有未保存的修改
 * @property content 文本内容快照
 * @property savedText 上次保存时的文本（用于比较是否修改）
 */
data class OpenTab(
    val uri: Uri?,
    val fileName: String,
    val isModified: Boolean = false,
    val content: String = "",
    val savedText: String = ""
)

/**
 * 全局搜索结果
 *
 * @property fileUri 文件 URI
 * @property fileName 文件名
 * @property lineNumber 匹配行号（从 1 开始）
 * @property lineContent 匹配行的文本内容
 * @property matchStart 匹配在行内的起始位置
 */
data class GlobalSearchResult(
    val fileUri: Uri,
    val fileName: String,
    val lineNumber: Int,
    val lineContent: String,
    val matchStart: Int
)
