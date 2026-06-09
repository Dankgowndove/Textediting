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
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText

/**
 * A text editor widget with a line-number gutter, current-line highlight, and
 * bracket-matching drawn on top of the standard EditText content.
 *
 * **Gutter drawing:**
 * - Only visible lines + 3-line buffer are drawn — constant cost regardless of file size.
 * - Line numbers use a reusable [CharArray] to avoid per-frame allocations.
 * - The gutter is drawn **after** [super.onDraw] so numbers always sit on top.
 *
 * **Current-line highlight:**
 * - A subtle background bar spans the full width of the current cursor line.
 * - Drawn **before** [super.onDraw] so text and selection render on top.
 *
 * **Bracket matching:**
 * - When the cursor is immediately after an opening or closing bracket — `()[]{}` —
 *   the matching partner is highlighted via a temporary [BackgroundColorSpan].
 * - The span is cleared whenever the selection moves again.
 *
 * **Scroll correctness:**
 * - [onScrollChanged] calls [invalidate] to ensure line numbers update on every scroll
 *   step, matching the visible text.
 * - [setTextSize] is overridden to keep the line-number paint in sync with the editor
 *   font size — eliminating the #1 alignment bug.
 */
class LinedEditText(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    // ── externally configurable ──
    var showLineNumbers: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                recomputePadding()
                invalidate()
            }
        }

    var darkMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyColors()
                invalidate()
            }
        }

    /** Enable / disable current-line highlight (default true). */
    var highlightCurrentLine: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Enable / disable bracket-matching highlight (default true). */
    var bracketMatching: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (!value) clearBracketSpans()
            }
        }

    /** Enable / disable visible whitespace (dots for spaces, arrows for tabs). */
    var showWhitespace: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // ── gutter metrics (px) ──
    private var gutterWidthPx: Float = 0f
    private var gutterMarginPx: Float = 0f
    private var contentMarginPx: Float = 0f

    // ── paints ──
    private val gutterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
    }
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    // Current-line highlight paint
    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Whitespace dot paint
    private val whitespacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    /** Reusable char buffer for formatting line numbers — zero allocations during scroll. */
    private val numBuf = CharArray(10)

    /** Track the previously-highlighted bracket pair so we can clear it. */
    private var lastBracketStart: Int = -1
    private var lastBracketEnd: Int = -1

    init {
        val density = resources.displayMetrics.density
        gutterWidthPx = 56f * density
        gutterMarginPx = 6f * density
        contentMarginPx = 8f * density

        // Match the paint to current textSize; setTextSize in init isn't called yet
        // on a fresh view, so fall back to the inherited EditText default.
        lineNumberPaint.textSize = textSize
        whitespacePaint.textSize = textSize

        applyColors()
        recomputePadding()

        isFocusable = true
        isFocusableInTouchMode = true
        gravity = Gravity.TOP
        isVerticalScrollBarEnabled = true
    }

    // ── keep line-number paint in sync with editor text size ──

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        lineNumberPaint.textSize = size
        whitespacePaint.textSize = size
    }

    // ── colour application ──

    private fun applyColors() {
        val gc = gutterColors(darkMode)
        gutterBgPaint.color = gc.background
        gutterDividerPaint.color = gc.divider
        lineNumberPaint.color = gc.lineNumber

        val ec = editorColors(darkMode)
        setTextColor(ec.text)
        setBackgroundColor(ec.background)
        highlightColor = ec.highlight

        // Current-line highlight: subtle tint of the accent colour
        currentLinePaint.color = modifyAlpha(ec.accent, 0.12f)

        // Whitespace: muted grey
        whitespacePaint.color = if (darkMode) 0x66444444.toInt() else 0x66BBBBBB.toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { textCursorDrawable?.setTint(ec.accent) } catch (_: Exception) {}
        }
    }

    private fun modifyAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF) * factor
        return (color and 0x00FFFFFF) or ((alpha.toInt() and 0xFF) shl 24)
    }

    // ── padding ──

    private fun recomputePadding() {
        val left = if (showLineNumbers) {
            (gutterWidthPx + contentMarginPx).toInt()
        } else {
            contentMarginPx.toInt()
        }
        setPadding(left, paddingTop, paddingRight, paddingBottom)
    }

    // ── system callbacks ──

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    /** Ensure line numbers + current-line highlight update when the user scrolls. */
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (showLineNumbers || highlightCurrentLine) {
            invalidate()
        }
    }

    /** Bracket matching: highlight partner when cursor lands next to a bracket. */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (bracketMatching && selStart == selEnd) {
            updateBracketHighlight(selStart)
        } else {
            clearBracketSpans()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            if (!hasFocus()) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            clearFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
            return false
        }
        return super.onKeyPreIme(keyCode, event)
    }

    // ── public helpers ──

    fun scrollToLine(line: Int) {
        val l = layout ?: return
        val target = (line - 1).coerceIn(0, l.lineCount - 1)
        scrollTo(scrollX, l.getLineTop(target))
    }

    /** Returns the 1-based line number at the given y-coordinate relative to the
     *  EditText content area (i.e. y = touch event Y minus paddingTop). */
    fun getLineAtY(contentY: Float): Int {
        val l = layout ?: return 1
        val line = l.getLineForVertical((contentY + scrollY).toInt())
        return (line + 1).coerceIn(1, l.lineCount)
    }

    // ── bracket matching ──

    private val bracketPairs = mapOf(
        '(' to ')', ')' to '(',
        '[' to ']', ']' to '[',
        '{' to '}', '}' to '{'
    )

    private fun updateBracketHighlight(cursor: Int) {
        clearBracketSpans()
        val text = text ?: return
        if (cursor <= 0 || cursor > text.length) return

        // Check character immediately before cursor (standard convention)
        var idx = cursor - 1
        var ch = if (idx in text.indices) text[idx] else return
        if (ch !in bracketPairs) {
            // Also check character at cursor
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
            val hlColor = if (darkMode) 0x55FFD700.toInt() else 0x55FFA000.toInt()
            spannable.setSpan(
                BackgroundColorSpan(hlColor),
                idx, idx + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                BackgroundColorSpan(hlColor),
                match, match + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            lastBracketStart = idx
            lastBracketEnd = match
        }
    }

    private fun findMatchingBracket(
        text: CharSequence, start: Int, ch: Char, partner: Char, isOpening: Boolean
    ): Int {
        var depth = 0
        if (isOpening) {
            for (i in start until text.length) {
                val c = text[i]
                if (c == ch) depth++
                else if (c == partner) {
                    depth--
                    if (depth == 0) return i
                }
            }
        } else {
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

    private fun clearBracketSpans() {
        if (lastBracketStart < 0) return
        val spannable = text as? Spannable ?: return
        val spans = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
        for (span in spans) {
            val flags = spannable.getSpanFlags(span)
            // Only clear spans that look like bracket highlights (not syntax spans)
            if (flags and Spannable.SPAN_EXCLUSIVE_EXCLUSIVE != 0) {
                spannable.removeSpan(span)
            }
        }
        lastBracketStart = -1
        lastBracketEnd = -1
    }

    // ── drawing ──

    override fun onDraw(canvas: Canvas) {
        val l = layout
        val h = height

        // 1. Current-line highlight (behind text)
        if (highlightCurrentLine && h > 0 && l != null && l.lineCount > 0) {
            val top = paddingTop.toFloat()
            val visibleHeight = (h - paddingBottom).toFloat() - top
            if (visibleHeight > 0) {
                val selLine = l.getLineForOffset(selectionStart.coerceIn(0, text?.length ?: 0))
                val lineTop = l.getLineTop(selLine).toFloat() - scrollY.toFloat() + top
                val lineBottom = l.getLineBottom(selLine).toFloat() - scrollY.toFloat() + top
                // Only draw if the line is within the visible area
                if (lineBottom >= top && lineTop <= top + visibleHeight) {
                    canvas.drawRect(0f, lineTop, width.toFloat(), lineBottom, currentLinePaint)
                }
            }
        }

        // 2. Draw the EditText (text, cursor, selection)
        super.onDraw(canvas)

        // 3. Overlay the gutter on top
        if (showLineNumbers && l != null && l.lineCount > 0) {
            drawGutter(canvas, l, h)
        }

        // 4. Whitespace visualization (on top of text but under gutter)
        if (showWhitespace && l != null && l.lineCount > 0) {
            drawWhitespace(canvas, l, h)
        }
    }

    private fun drawGutter(canvas: Canvas, layout: Layout, viewHeight: Int) {
        if (viewHeight <= 0) return

        val top = paddingTop.toFloat()
        val bottom = (viewHeight - paddingBottom).toFloat()
        val visibleHeight = bottom - top
        if (visibleHeight <= 0) return

        // Dynamically widen gutter if line count exceeds current capacity
        val maxLine = layout.lineCount
        val neededDigits = maxLine.toString().length
        val neededGutter = gutterMarginPx * 2 + lineNumberPaint.measureText("9".repeat(neededDigits))
        if (neededGutter > gutterWidthPx && neededDigits > 5) {
            gutterWidthPx = neededGutter + 8f
        }

        // Clip to gutter region
        canvas.save()
        canvas.clipRect(0f, top, gutterWidthPx, bottom)

        // Background + divider line
        canvas.drawRect(0f, top, gutterWidthPx, bottom, gutterBgPaint)
        canvas.drawLine(gutterWidthPx, top, gutterWidthPx, bottom, gutterDividerPaint)

        val scrolly = scrollY
        val firstLine = maxOf(0, layout.getLineForVertical(scrolly) - 3)
        val lastLine = minOf(
            layout.lineCount - 1,
            layout.getLineForVertical(scrolly + visibleHeight.toInt()) + 3
        )

        val numX = gutterWidthPx - gutterMarginPx

        for (line in firstLine..lastLine) {
            val screenBaseline = layout.getLineBaseline(line).toFloat() - scrolly + top
            drawLineNumber(canvas, line + 1, numX, screenBaseline)
        }

        canvas.restore()
    }

    private fun drawLineNumber(canvas: Canvas, num: Int, x: Float, baseline: Float) {
        var n = num
        var pos = numBuf.size
        if (n == 0) {
            numBuf[--pos] = '0'
        } else {
            while (n > 0) {
                numBuf[--pos] = ('0' + (n % 10))
                n /= 10
            }
        }
        canvas.drawText(numBuf, pos, numBuf.size - pos, x, baseline, lineNumberPaint)
    }

    /**
     * Draws visible whitespace characters: middle-dot (·) for spaces and
     * right-arrow (→) for tabs. Only rendered on visible lines.
     */
    private fun drawWhitespace(canvas: Canvas, layout: Layout, viewHeight: Int) {
        val text = text ?: return
        if (text.isEmpty()) return

        val top = paddingTop.toFloat()
        val bottom = (viewHeight - paddingBottom).toFloat()
        val visibleHeight = bottom - top
        if (visibleHeight <= 0) return

        val scrolly = scrollY
        val firstLine = maxOf(0, layout.getLineForVertical(scrolly))
        val lastLine = minOf(
            layout.lineCount - 1,
            layout.getLineForVertical(scrolly + visibleHeight.toInt())
        )

        val paint = whitespacePaint
        val spaceDot = "·"
        val tabArrow = "→"

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
