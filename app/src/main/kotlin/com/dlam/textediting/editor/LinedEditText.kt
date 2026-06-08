package com.dlam.textediting.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText

/**
 * A text editor widget with a line-number gutter drawn on its left side.
 *
 * Thread-safe for main-thread-only use.
 *
 * **Gutter drawing:**
 * - Only visible lines + 2-line buffer are drawn — constant cost regardless of file size.
 * - Line numbers use a reusable [CharArray] to avoid per-frame allocations.
 * - The gutter is drawn **after** [super.onDraw] so numbers always sit on top.
 *
 * **Hardware acceleration:**
 * - [LAYER_TYPE_HARDWARE] composites the view off-screen so scrolling doesn't
 *   re-execute [onDraw] every frame.
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

    /** Reusable char buffer for formatting line numbers — zero allocations during scroll. */
    private val numBuf = CharArray(10)

    init {
        val density = resources.displayMetrics.density
        gutterWidthPx = 56f * density
        gutterMarginPx = 6f * density
        contentMarginPx = 8f * density
        lineNumberPaint.textSize = 12f * density

        applyColors()
        recomputePadding()

        isFocusable = true
        isFocusableInTouchMode = true
        gravity = Gravity.TOP
        isVerticalScrollBarEnabled = true

        // Hardware-accelerated layer — scrolls don't re-trigger onDraw
        setLayerType(LAYER_TYPE_HARDWARE, null)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { textCursorDrawable?.setTint(ec.accent) } catch (_: Exception) {}
        }
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
        // Let Compose drive dark-mode; no-op here (caller sets LinedEditText.darkMode).
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

    // ── drawing ──

    override fun onDraw(canvas: Canvas) {
        // 1. Draw the EditText (text, cursor, selection)
        super.onDraw(canvas)

        // 2. Overlay the gutter on top
        if (showLineNumbers) {
            val l = layout
            if (l != null && l.lineCount > 0) {
                drawGutter(canvas, l)
            }
        }
    }

    private fun drawGutter(canvas: Canvas, layout: Layout) {
        val h = height
        if (h <= 0) return

        val top = paddingTop.toFloat()
        val bottom = (h - paddingBottom).toFloat()
        val visibleHeight = bottom - top
        if (visibleHeight <= 0) return

        // Clip to gutter region
        canvas.save()
        canvas.clipRect(0f, top, gutterWidthPx, bottom)

        // Background + divider line
        canvas.drawRect(0f, top, gutterWidthPx, bottom, gutterBgPaint)
        canvas.drawLine(gutterWidthPx, top, gutterWidthPx, bottom, gutterDividerPaint)

        // Compute which lines are currently visible
        val scrolly = scrollY
        val firstLine = maxOf(0, layout.getLineForVertical(scrolly) - 2)
        val lastLine = minOf(
            layout.lineCount - 1,
            layout.getLineForVertical(scrolly + visibleHeight.toInt()) + 2
        )

        val numX = gutterWidthPx - gutterMarginPx

        for (line in firstLine..lastLine) {
            // getLineBaseline returns the baseline Y in text-layout space.
            // To get screen Y we subtract scrollY and add paddingTop (the
            // EditText content starts at paddingTop in the View).
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
}
