package com.example.miniterm

/**
 * EscapeSequenceParser
 *
 * Minimal, original implementation for handling ANSI escape sequences
 * in terminal output. This does not implement the full VT100/xterm
 * spec; it strips common color/cursor codes so plain text renders
 * cleanly in a simple scrollback view.
 */
object EscapeSequenceParser {

    private val ansiRegex = Regex("\u001B\\[[0-9;]*[a-zA-Z]")

    fun stripAnsiCodes(input: String): String {
        return ansiRegex.replace(input, "")
    }

    fun containsEscapeSequence(input: String): Boolean {
        return input.contains('\u001B')
    }
}
