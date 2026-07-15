package com.example.miniterm

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * RealPackageManager
 *
 * Non-simulated "pkg"/"apt"-style installer. Unlike a mock, this
 * performs actual network downloads and writes real, executable files
 * into a local bin/ directory that is on PATH (see PTYManager), so a
 * command really becomes runnable after "pkg install <name>".
 *
 * This is original code (not copied from Termux or any other repo).
 * It is intentionally a small, manually curated index rather than a
 * full APT repository/mirror: reproducing Termux's actual package
 * repo requires their own cross-compiled bootstrap binaries and
 * signing infrastructure, which isn't something this project copies
 * or redistributes.
 *
 * IMPORTANT CAVEATS (read before relying on this):
 * - A downloaded file only runs if it's a *statically linked* binary
 *   built for the device's exact CPU ABI (arm64-v8a, armeabi-v7a, x86_64...).
 *   Regular Linux binaries linked against glibc generally will NOT run
 *   on Android out of the box.
 * - The index below is a placeholder map of name -> URL. Replace the
 *   URLs with real, legally redistributable static binaries that you
 *   have verified match your target architecture before shipping this.
 */
class RealPackageManager(
    private val binDir: File,
    private val onOutput: (String) -> Unit
) {

    private val executor = Executors.newSingleThreadExecutor()

    // Name -> direct download URL. Fill in real, verified URLs here.
    // Left empty by default so the app never silently downloads
    // something you haven't reviewed.
    private val index: MutableMap<String, String> = mutableMapOf()

    fun registerPackage(name: String, url: String) {
        index[name] = url
    }

    fun handle(command: String): Boolean {
        val parts = command.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return false

        if (parts[0] != "pkg" && parts[0] != "apt") return false

        when (parts.getOrNull(1)) {
            "list" -> {
                if (index.isEmpty()) {
                    onOutput(
                        "No packages registered yet.\n" +
                        "Call RealPackageManager.registerPackage(name, url) with a\n" +
                        "verified static-binary URL for your device's ABI to add one.\n"
                    )
                } else {
                    onOutput("Available packages:\n" + index.keys.joinToString("\n") { "  $it" } + "\n")
                }
            }
            "install" -> {
                val pkg = parts.getOrNull(2)
                val url = index[pkg]
                if (pkg == null) {
                    onOutput("Usage: pkg install <name>\n")
                } else if (url == null) {
                    onOutput("E: Unable to locate package $pkg (not registered)\n")
                } else {
                    downloadAndInstall(pkg, url)
                }
            }
            "uninstall", "remove" -> {
                val pkg = parts.getOrNull(2)
                val file = File(binDir, pkg ?: "")
                if (pkg != null && file.exists()) {
                    file.delete()
                    onOutput("Removing $pkg...\n$pkg removed.\n")
                } else {
                    onOutput("Package ${pkg ?: ""} is not installed.\n")
                }
            }
            else -> onOutput("Usage: pkg [list|install|uninstall] <package>\n")
        }
        return true
    }

    private fun downloadAndInstall(name: String, urlString: String) {
        onOutput("Fetching $name from $urlString ...\n")
        executor.submit {
            try {
                binDir.mkdirs()
                val outFile = File(binDir, name)
                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    onOutput("E: Download failed, HTTP $responseCode\n")
                    connection.disconnect()
                    return@submit
                }

                connection.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                connection.disconnect()

                val madeExecutable = outFile.setExecutable(true, false)
                if (madeExecutable) {
                    onOutput(
                        "Downloaded and installed $name to ${outFile.absolutePath}\n" +
                        "Run it by typing: $name\n" +
                        "(Note: it will only run if this binary matches your device's CPU ABI.)\n"
                    )
                } else {
                    onOutput("Downloaded $name but could not mark it executable.\n")
                }
            } catch (e: Exception) {
                onOutput("E: Install failed for $name: ${e.message}\n")
            }
        }
    }
}
