package com.dlam.textediting

/**
 * 撤销/重做管理器
 *
 * 采用全文本快照策略：每次 [record] 存储全文副本。
 * 通过历史列表 + 索引指针实现统一的撤销/重做导航。
 *
 * ## 线程安全
 * 仅限主线程使用（与 EditText TextWatcher 配合）。
 *
 * ## 性能说明
 * 每个 [record] 调用存储全文快照。对于超大文件（>500 KB），
 * 可能导致内存压力增加。[maxHistory] 上限（默认 200）限制了最坏情况。
 *
 * ## 使用规范
 * [prepareUndo]/[prepareRedo] 必须在 finally 块中配合 [finishUndoRedo] 使用，
 * 确保内部标志始终被清除，即使调用方抛出异常。
 *
 * @param maxHistory 最大历史步数，默认 200
 */
class UndoManager(private val maxHistory: Int = 200) {

    // 历史快照列表，每个元素是全文副本
    private val history = mutableListOf<String>()
    // 当前指针位置：指向当前显示的快照
    private var index = -1
    // 防止撤销/重做操作触发 record() 导致递归记录
    private var isUndoingRedoing = false

    /** 是否可以撤销：index > 0 表示有更早的历史 */
    val canUndo: Boolean get() = index > 0
    /** 是否可以重做：index 之后还有快照 */
    val canRedo: Boolean get() = index < history.size - 1

    /**
     * 记录新的文本状态
     *
     * 应从 [android.text.TextWatcher.afterTextChanged] 调用。
     * 以下情况会跳过记录：
     * - 正在执行撤销/重做操作（防止递归）
     * - 文本与上一次快照完全相同（先比较长度，相同再全文比较）
     */
    fun record(text: String) {
        // 撤销/重做过程中不记录
        if (isUndoingRedoing) return
        // 快速路径：长度不同则确定已变更
        val prev = if (index >= 0 && index < history.size) history[index] else null
        if (prev != null && prev.length == text.length && prev == text) return

        // 截断当前位置之后的重做历史（新操作导致旧的重做路径失效）
        while (history.size > index + 1) {
            history.removeAt(history.lastIndex)
        }
        history.add(text)
        // 超出最大历史数时移除最旧的快照
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
        index = history.size - 1
    }

    /**
     * 准备撤销操作
     *
     * @return 需要恢复到的文本快照，若无历史则返回 null
     *
     * **重要**：调用后必须在 finally 块中调用 [finishUndoRedo]
     */
    fun prepareUndo(): String? {
        if (index <= 0) return null
        isUndoingRedoing = true
        index--
        return history[index]
    }

    /**
     * 准备重做操作
     *
     * @return 需要恢复到的文本快照，若无重做历史则返回 null
     *
     * **重要**：调用后必须在 finally 块中调用 [finishUndoRedo]
     */
    fun prepareRedo(): String? {
        if (index >= history.size - 1) return null
        isUndoingRedoing = true
        index++
        return history[index]
    }

    /**
     * 清除 isUndoingRedoing 标志
     *
     * **必须**在 [prepareUndo] 或 [prepareRedo] 之后调用，
     * 建议放在 finally 块中以确保异常时也能清除。
     */
    fun finishUndoRedo() {
        isUndoingRedoing = false
    }

    /** 清除所有历史记录（如打开新文件时） */
    fun clear() {
        history.clear()
        index = -1
        isUndoingRedoing = false
    }
}
