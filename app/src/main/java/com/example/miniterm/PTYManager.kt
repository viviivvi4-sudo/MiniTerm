package com.example.miniterm

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * PTYManager
 *
 * Original, from-scratch implementation of a shell process manager.
 * Uses ProcessBuilder (no native PTY library, no code copied from any
 * third-party terminal-emulator project) to spawn a shell and stream
 * its stdout/stderr back to a listener line-by-line.
 *
 * FIX (v2): removed "-i" (interactive) flag. Without a real kernel PTY,
 * "-i" makes /system/bin/sh try to grab a controlling terminal and job
 * control ("can't find tty fd", "won't have job control"), and also
 * causes it to echo back input, which combined with our own on-screen
 * typing echo produced duplicated lines ("ls" appearing 2-3 times).
 * A plain, non-interactive "sh" reading commands from a piped stdin
 * does not echo, so only our local TerminalView echo shows the typed
 * command, and only the command's real output comes from the process.
 *
 * This is still a simplified session manager: it does not allocate a
 * true kernel PTY (that requires native/JNI code), so full-screen
 * curses apps (vim, top) will not render correctly.
 */
class PTYManager(private val onOutput: (String) -> Unit) {

    private var process: Process? = null
    private var writer: OutputStream? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var lastSentCommand: String? = null

    /** Optional hook: if set, "pkg"/"apt" commands are intercepted here
     *  instead of being forwarded to the real shell. */
    var attachedPkgManager: RealPackageManager? = null

    fun start(homeDir: String) {
        val binDir = java.io.File(homeDir, "../usr/bin").canonicalFile
        binDir.mkdirs()

        val pb = ProcessBuilder("/system/bin/sh")
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = homeDir
        pb.environment()["TERM"] = "dumb"
        pb.environment()["PATH"] =
            "${binDir.absolutePath}:/system/bin:/system/xbin"
        pb.directory(java.io.File(homeDir))

        process = pb.start()
        writer = process?.outputStream

        executor.submit {
            try {
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val text = line ?: ""
                    // Skip the line if it's just the shell echoing back
                    // the command we just sent (can happen depending on
                    // shell/build), to avoid showing it twice.
                    if (text == lastSentCommand) {
                        lastSentCommand = null
                        continue
                    }
                    onOutput(text + "\n")
                }
            } catch (e: Exception) {
                onOutput("\n[session ended: ${e.message}]\n")
            }
        }
    }

    fun sendCommand(command: String) {
        if (attachedPkgManager?.handle(command) == true) {
            onOutput("$ ")
            return
        }
        try {
            lastSentCommand = command
            writer?.write((command + "\n").toByteArray())
            writer?.flush()
        } catch (e: Exception) {
            onOutput("\n[write error: ${e.message}]\n")
        }
    }

    /** Real, non-simulated bin directory packages get installed into. */
    fun binDir(homeDir: String): java.io.File =
        java.io.File(homeDir, "../usr/bin").canonicalFile

    fun destroy() {
        process?.destroy()
        executor.shutdownNow()
    }
}
