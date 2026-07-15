package com.example.miniterm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * TerminalView
 *
 * Original scrollback-buffer terminal renderer. Draws monospace text
 * lines onto a Canvas and accepts keyboard input via a minimal
 * InputConnection implementation. Not derived from any existing
 * terminal-emulator codebase.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val scrollback = StringBuilder()
    private var currentLine = StringBuilder()

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 32f
        typeface = android.graphics.Typeface.MONOSPACE
        isAntiAlias = true
    }

    private var lineHeight = textPaint.fontSpacing
    private var ptyManager: PTYManager? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun attachPty(manager: PTYManager) {
        ptyManager = manager
    }

    fun appendOutput(text: String) {
        val clean = EscapeSequenceParser.stripAnsiCodes(text)
        scrollback.append(clean)
        // Keep buffer bounded
        if (scrollback.length > 50_000) {
            scrollback.delete(0, scrollback.length - 50_000)
        }
        post { invalidate() }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                currentLine.append(text)
                appendOutput(text.toString())
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    val cmd = currentLine.toString()
                    currentLine.clear()
                    appendOutput("\n")
                    ptyManager?.sendCommand(cmd)
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (currentLine.isNotEmpty()) {
                    currentLine.deleteCharAt(currentLine.length - 1)
                    if (scrollback.isNotEmpty()) {
                        scrollback.deleteCharAt(scrollback.length - 1)
                        post { invalidate() }
                    }
                }
                return true
            }
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        val lines = scrollback.toString().split("\n")
        val visibleLines = (height / lineHeight).toInt().coerceAtLeast(1)
        val start = (lines.size - visibleLines).coerceAtLeast(0)

        var y = lineHeight
        for (i in start until lines.size) {
            canvas.drawText(lines[i], 8f, y, textPaint)
            y += lineHeight
        }
    }
}
