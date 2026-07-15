package com.example.miniterm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private lateinit var ptyManager: PTYManager
    private lateinit var pkgManager: RealPackageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalView = findViewById(R.id.terminalView)

        // Bootstrap directories inside app-private storage:
        //   .../files/home   -> $HOME
        //   .../files/usr/bin -> on PATH, where "pkg install" writes real binaries
        val home = File(filesDir, "home").apply { mkdirs() }
        val binDir = File(filesDir, "usr/bin").apply { mkdirs() }

        pkgManager = RealPackageManager(binDir) { output -> terminalView.appendOutput(output) }
        // Example of how to register a real package once you have a
        // verified, ABI-matching static-binary URL:
        // pkgManager.registerPackage("hello", "https://example.com/arm64/hello")

        ptyManager = PTYManager { output -> terminalView.appendOutput(output) }
        ptyManager.attachedPkgManager = pkgManager
        terminalView.attachPty(ptyManager)

        ptyManager.start(home.absolutePath)
        terminalView.appendOutput("MiniTerm ready. HOME=${home.absolutePath}\n$ ")
        terminalView.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        ptyManager.destroy()
    }
}
