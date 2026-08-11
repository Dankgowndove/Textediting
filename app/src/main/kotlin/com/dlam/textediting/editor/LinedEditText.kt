package com.dlam.textediting.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText

/**
 * 带行号栏、当前行高亮和括号匹配的自定义文本编辑器控件
 *
 * 继承 [AppCompatEditText]，通过 Canvas 直接绘制行号栏和相关视觉元素。
 *
 * ## 行号栏绘制
 * - 仅绘制可见行 + 3 行缓冲区 — 无论文件多大都是常量开销
 * - 使用可复用 [CharArray] 实现零分配行号格式化
 * - 行号栏在 [super.onDraw] **之后**绘制，确保数字始终位于最上层
 *
 * ## 当前行高亮
 * - 在当前光标所在行绘制半透明背景条
 * - 在 [super.onDraw] **之前**绘制，确保文本和选区渲染在高亮之上
 *
 * ## 括号匹配
 * - 当光标位于括号 `()[]{}` 旁边时，高亮显示匹配的括号
 * - 通过临时 [BackgroundColorSpan] 实现，光标移动时自动清除
 *
 * ## 滚动正确性
 * - [onScrollChanged] 调用 [invalidate] 确保每次滚动步进时行号同步更新
 * - 重写 [setTextSize] 使行号 Paint 字体大小与编辑器同步
 *
 * @param context Android Context
 * @param attrs XML 属性集（可选）
 */
class LinedEditText(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    // ── 外部可配置属性 ──

    // [Bug #3 修复] 括号 Span 专属标记接口，用于精确区分括号高亮和语法高亮 Span
    private interface BracketSpan

    /** 是否显示行号栏（默认开启） */
    var showLineNumbers: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                recomputePadding()  // 重新计算左侧内边距
                invalidate()
            }
        }

    /** 是否使用暗色模式配色（默认亮色） */
    var darkMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyColors()
                invalidate()
            }
        }

    /** [M3 优化] Material 3 配色方案引用：非 null 时编辑器从主题派生颜色 */
    var colorScheme: androidx.compose.material3.ColorScheme? = null
        set(value) {
            if (field != value) {
                field = value
                applyColors()
                invalidate()
            }
        }

    /** 是否高亮当前行（默认开启） */
    var highlightCurrentLine: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 是否启用括号匹配高亮（默认开启） */
    var bracketMatching: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (!value) clearBracketSpans()
            }
        }

    /** 是否显示空白字符：空格显示为 ·，制表符显示为 →（默认关闭） */
    var showWhitespace: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // ── 行号栏尺寸（像素）──
    private var gutterWidthPx: Float = 0f     // 行号栏总宽度
    private var gutterMarginPx: Float = 0f     // 行号右侧边距
    private var contentMarginPx: Float = 0f    // 内容区左侧边距

    // ── Paint 对象（复用，避免频繁创建）──
    private val gutterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)       // 行号栏背景
    private val gutterDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f                                         // 分割线宽度
    }
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT                              // 行号右对齐
    }

    // 当前行高亮 Paint
    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 空白字符 Paint
    private val whitespacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    /** 可复用的字符缓冲区 — 滚动时零分配格式化行号 */
    private val numBuf = CharArray(10)

    /** 记录上次高亮的括号对，用于清除 */
    private var lastBracketStart: Int = -1
    private var lastBracketEnd: Int = -1

    init {
        // 根据屏幕密度计算行号栏尺寸
        val density = resources.displayMetrics.density
        gutterWidthPx = 56f * density
        gutterMarginPx = 6f * density
        contentMarginPx = 8f * density

        // 初始化 Paint 字体大小（与 EditText 默认值一致）
        lineNumberPaint.textSize = textSize
        whitespacePaint.textSize = textSize

        applyColors()
        recomputePadding()

        // 基本配置
        isFocusable = true
        isFocusableInTouchMode = true
        gravity = Gravity.TOP                   // 内容顶部对齐
        isVerticalScrollBarEnabled = true
    }

    // ── 字体大小同步 ──

    /**
     * 重写 setTextSize，确保行号 Paint 字体大小与编辑器文本大小保持同步
     *
     * [Bug #1 修复] super.setTextSize(unit, size) 内部会把 sp 转成 px 存入 Paint，
     * 此后 textSize 属性返回 px 值。直接读取 textSize（已是 px）赋给行号 Paint，
     * 避免将 sp 数值（如 14f）直接当 px 使用导致行号字体极小/极大。
     */
    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        // textSize 在 super 调用后已经是 px，直接赋值即可
        val pxSize = textSize
        lineNumberPaint.textSize = pxSize
        whitespacePaint.textSize = pxSize
        // 字体变化时行号栏宽度也需要重新计算
        recomputePadding()
    }

    // ── 颜色应用 ──

    /** 根据 darkMode 和可选的 colorScheme 设置所有 Paint 和 EditText 的颜色 */
    private fun applyColors() {
        // 行号栏颜色 — 优先从 Material 3 主题派生
        val gc = gutterColors(darkMode, colorScheme)
        gutterBgPaint.color = gc.background
        gutterDividerPaint.color = gc.divider
        lineNumberPaint.color = gc.lineNumber

        // 编辑器颜色 — 优先从 Material 3 主题派生
        val ec = editorColors(darkMode, colorScheme)
        setTextColor(ec.text)
        setBackgroundColor(ec.background)
        highlightColor = ec.highlight

        // 当前行高亮：强调色的 12% 透明度
        currentLinePaint.color = modifyAlpha(ec.accent, 0.12f)

        // 空白字符：柔和的灰色
        whitespacePaint.color = if (darkMode) 0x66444444.toInt() else 0x66BBBBBB.toInt()

        // Android 10+ 设置光标颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { textCursorDrawable?.setTint(ec.accent) } catch (_: Exception) {}
        }
    }

    /** 修改颜色的 Alpha 通道 */
    private fun modifyAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF) * factor
        return (color and 0x00FFFFFF) or ((alpha.toInt() and 0xFF) shl 24)
    }

    // ── 内边距计算 ──

    /** 根据是否显示行号重新计算左侧内边距 */
    private fun recomputePadding() {
        val left = if (showLineNumbers) {
            (gutterWidthPx + contentMarginPx).toInt()
        } else {
            contentMarginPx.toInt()
        }
        setPadding(left, paddingTop, paddingRight, paddingBottom)
    }

    /**
     * 根据当前文件总行数更新行号栏宽度
     *
     * [Bug #8 修复] 在 onLayout（布局阶段）计算行号栏宽度并调整 padding，
     * 避免在 onDraw（绘制阶段）修改 padding —— 绘制阶段改 padding 会触发
     * requestLayout，导致本帧行号与文本错位，且行数位数变化时整视图反复重排抖动。
     * 宽度只增不减（打开大文件后再打开小文件不缩窄），布局最多多执行一次后稳定。
     */
    private fun updateGutterWidth() {
        if (!showLineNumbers) return
        val l = layout ?: return
        val digits = l.lineCount.toString().length
        val needed = gutterMarginPx * 2 +
                lineNumberPaint.measureText("0".repeat(digits))
        if (needed > gutterWidthPx) {
            gutterWidthPx = needed + 8f
            recomputePadding()
        }
    }

    // ── 系统回调 ──

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    /**
     * 布局阶段更新行号栏宽度
     *
     * [Bug #8 修复] 行号栏宽度只在布局阶段计算（setText / 字体变化 / 尺寸变化都会触发
     * onLayout），绘制阶段只读，保证滚动路径零测量零分配、无 requestLayout 抖动。
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateGutterWidth()
    }

    /** 滚动时更新行号和当前行高亮 */
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (showLineNumbers || highlightCurrentLine) {
            // [Bug #7 修复] 用 postInvalidateOnAnimation 让行号随绘制节拍更新，
            // 减少低刷新率下滚动时行号滞后于文本的撕裂感
            postInvalidateOnAnimation()
        }
    }

    /** 光标位置变化时更新括号匹配高亮 */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (bracketMatching && selStart == selEnd) {
            updateBracketHighlight(selStart)  // 光标无选区时匹配括号
        } else {
            clearBracketSpans()               // 有选区时清除高亮
        }
    }

    // ── 公共工具方法 ──

    /**
     * 滚动到指定行
     *
     * @param line 1-based 行号
     */
    fun scrollToLine(line: Int) {
        val l = layout ?: return
        val target = (line - 1).coerceIn(0, l.lineCount - 1)
        scrollTo(scrollX, l.getLineTop(target))
    }

    /**
     * 根据 y 坐标获取行号（1-based）
     *
     * @param contentY 相对于 EditText 内容区域的 y 坐标（即触摸事件 Y 减去 paddingTop）
     * @return 1-based 行号
     */
    fun getLineAtY(contentY: Float): Int {
        val l = layout ?: return 1
        val line = l.getLineForVertical((contentY + scrollY).toInt())
        return (line + 1).coerceIn(1, l.lineCount)
    }

    // ── 括号匹配 ──

    /** 括号配对映射表 */
    private val bracketPairs = mapOf(
        '(' to ')', ')' to '(',
        '[' to ']', ']' to '[',
        '{' to '}', '}' to '{'
    )

    /**
     * 更新括号高亮
     *
     * 检查光标前一个字符（标准约定）或光标所在字符是否为括号，
     * 如果是则查找匹配括号并高亮两者。
     */
    private fun updateBracketHighlight(cursor: Int) {
        clearBracketSpans()
        val text = text ?: return
        if (cursor <= 0 || cursor > text.length) return

        // 优先检查光标前一个字符
        var idx = cursor - 1
        var ch = if (idx in text.indices) text[idx] else return
        if (ch !in bracketPairs) {
            // 其次检查光标位置字符
            if (cursor < text.length) {
                idx = cursor
                ch = text[idx]
                if (ch !in bracketPairs) return
            } else return
        }

        val partner = bracketPairs[ch] ?: return
        val isOpening = ch in "([{"
        val match = findMatchingBracket(text, idx, ch, partner, isOpening)
        if (match >= 0) {
            val spannable = text as? Spannable ?: return
            // 金色半透明高亮
            val hlColor = if (darkMode) 0x55FFD700.toInt() else 0x55FFA000.toInt()
            // [Bug #3 修复] 使用匿名 BracketSpan 子类，便于精确清除
            spannable.setSpan(
                object : BackgroundColorSpan(hlColor), BracketSpan {},
                idx, idx + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                object : BackgroundColorSpan(hlColor), BracketSpan {},
                match, match + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            lastBracketStart = idx
            lastBracketEnd = match
        }
    }

    /**
     * 查找匹配的括号
     *
     * 开括号：正向遍历，按深度匹配
     * 闭括号：反向遍历，按深度匹配
     *
     * @return 匹配括号的索引，未找到返回 -1
     */
    private fun findMatchingBracket(
        text: CharSequence, start: Int, ch: Char, partner: Char, isOpening: Boolean
    ): Int {
        var depth = 0
        if (isOpening) {
            // 开括号：向→查找
            for (i in start until text.length) {
                val c = text[i]
                if (c == ch) depth++
                else if (c == partner) {
                    depth--
                    if (depth == 0) return i
                }
            }
        } else {
            // 闭括号：向←查找
            for (i in start downTo 0) {
                val c = text[i]
                if (c == ch) depth++
                else if (c == partner) {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /**
     * 清除所有括号高亮 Span
     * [Bug #3 修复] 改为精确按接口类型清除，只查找 BracketSpan 类型，
     * 完全不干扰 ForegroundColorSpan 等语法高亮 Span。
     */
    private fun clearBracketSpans() {
        if (lastBracketStart < 0) return
        val spannable = text as? Spannable ?: return
        val spans = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
        for (span in spans) {
            if (span is BracketSpan) {
                spannable.removeSpan(span)
            }
        }
        lastBracketStart = -1
        lastBracketEnd = -1
    }

    // ── 绘制 ──

    /**
     * 自定义绘制：按层叠顺序绘制
     *
     * 绘制顺序：
     * 1. 当前行高亮（底层）
     * 2. EditText 文本、光标、选区
     * 3. 行号栏（顶层）
     * 4. 空白字符可视化
     */
    override fun onDraw(canvas: Canvas) {
        val l = layout
        val h = height

        // 1. 当前行高亮（在文本下方）
        if (highlightCurrentLine && h > 0 && l != null && l.lineCount > 0) {
            // [Bug #4 修复] 使用 extendedPaddingTop 保证与实际文本对齐
            val top = extendedPaddingTop.toFloat()
            val visibleHeight = (h - extendedPaddingBottom).toFloat() - top
            if (visibleHeight > 0) {
                val selLine = l.getLineForOffset(selectionStart.coerceIn(0, text?.length ?: 0))
                val lineTop = l.getLineTop(selLine).toFloat() - scrollY.toFloat() + top
                val lineBottom = l.getLineBottom(selLine).toFloat() - scrollY.toFloat() + top
                // 仅在可见区域绘制
                if (lineBottom >= top && lineTop <= top + visibleHeight) {
                    canvas.drawRect(0f, lineTop, width.toFloat(), lineBottom, currentLinePaint)
                }
            }
        }

        // 2. 绘制 EditText 内容（文本、光标、选区）
        super.onDraw(canvas)

        // 3. 在文本上方叠加行号栏
        if (showLineNumbers && l != null && l.lineCount > 0) {
            drawGutter(canvas, l, h)
        }

        // 4. 空白字符可视化
        if (showWhitespace && l != null && l.lineCount > 0) {
            drawWhitespace(canvas, l, h)
        }
    }

    /**
     * 绘制行号栏
     *
     * [Bug #8 修复] 行号栏宽度由 onLayout 阶段的 updateGutterWidth() 统一计算，
     * 本函数只读 gutterWidthPx —— 绘制阶段零测量、零字符串分配、零宽度修改，
     * 滚动路径保持常量开销，消除行号与文本错位及滞后。
     * 仅绘制可见行 ± 3 行缓冲区，大文件也保持常量开销。
     */
    private fun drawGutter(canvas: Canvas, layout: Layout, viewHeight: Int) {
        if (viewHeight <= 0) return

        val top = extendedPaddingTop.toFloat()
        val bottom = (viewHeight - extendedPaddingBottom).toFloat()
        val visibleHeight = bottom - top
        if (visibleHeight <= 0) return

        // 裁剪到行号栏区域
        canvas.save()
        canvas.clipRect(0f, top, gutterWidthPx, bottom)

        // 背景 + 分割线
        canvas.drawRect(0f, top, gutterWidthPx, bottom, gutterBgPaint)
        canvas.drawLine(gutterWidthPx, top, gutterWidthPx, bottom, gutterDividerPaint)

        // 计算可见行范围 ± 3 行缓冲区
        val scrolly = scrollY
        val firstLine = maxOf(0, layout.getLineForVertical(scrolly) - 3)
        val lastLine = minOf(
            layout.lineCount - 1,
            layout.getLineForVertical(scrolly + visibleHeight.toInt()) + 3
        )

        val numX = gutterWidthPx - gutterMarginPx

        // 逐行绘制行号
        for (line in firstLine..lastLine) {
            // [Bug #4 修复] 用 extendedPaddingTop 而非 paddingTop
            val screenBaseline = layout.getLineBaseline(line).toFloat() - scrolly + top
            drawLineNumber(canvas, line + 1, numX, screenBaseline)
        }

        canvas.restore()
    }

    /**
     * 绘制单个行号
     *
     * 使用可复用的 CharArray 进行零分配数字格式化。
     */
    private fun drawLineNumber(canvas: Canvas, num: Int, x: Float, baseline: Float) {
        // [Bug #7 修复] 防止行号超过 numBuf 容量（10 位）导致越界写：行号 ≥ 10^10
        // 时直接回退到 String 绘制，保证大文件极端情况不崩溃
        if (num >= 10_000_000_000L) {
            canvas.drawText(num.toString(), x, baseline, lineNumberPaint)
            return
        }
        var n = num
        var pos = numBuf.size
        // 整数转字符（从右到左填充缓冲区）
        if (n == 0) {
            numBuf[--pos] = '0'
        } else {
            while (n > 0) {
                numBuf[--pos] = ('0' + (n % 10))
                n /= 10
            }
        }
        // 从缓冲区绘制
        canvas.drawText(numBuf, pos, numBuf.size - pos, x, baseline, lineNumberPaint)
    }

    /**
     * 绘制空白字符可视化
     *
     * 空格显示为 ·，制表符显示为 →。仅在可见行上绘制。
     * [Bug #4 修复] 使用 extendedPaddingTop 保证与实际文本对齐。
     */
    private fun drawWhitespace(canvas: Canvas, layout: Layout, viewHeight: Int) {
        val text = text ?: return
        if (text.isEmpty()) return

        val top = extendedPaddingTop.toFloat()
        val bottom = (viewHeight - extendedPaddingBottom).toFloat()
        val visibleHeight = bottom - top
        if (visibleHeight <= 0) return

        val scrolly = scrollY
        val firstLine = maxOf(0, layout.getLineForVertical(scrolly))
        val lastLine = minOf(
            layout.lineCount - 1,
            layout.getLineForVertical(scrolly + visibleHeight.toInt())
        )

        val paint = whitespacePaint
        val spaceDot = "·"     // 空格显示为中点
        val tabArrow = "→"     // 制表符显示为右箭头

        for (line in firstLine..lastLine) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            val baseline = layout.getLineBaseline(line).toFloat() - scrolly + top

            var col = lineStart
            while (col < lineEnd) {
                val ch = text[col]
                val x = layout.getPrimaryHorizontal(col) - scrollX.toFloat() + paddingLeft.toFloat()
                when (ch) {
                    ' ' -> canvas.drawText(spaceDot, x, baseline, paint)
                    '\t' -> canvas.drawText(tabArrow, x, baseline, paint)
                }
                col++
            }
        }
    }
}
