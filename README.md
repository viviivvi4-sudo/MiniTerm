# MiniTerm

A small, original Android terminal-app skeleton inspired by the general
*idea* of Termux (a terminal on Android), written from scratch:

- No code copied, forked, or vendored from Termux or any other project.
- Uses `ProcessBuilder` (not a native PTY/JNI layer), so full curses
  apps like `vim`/`top` won't render correctly — this is a starting
  point, not a drop-in Termux replacement.
- `pkg`/`apt install <name>` performs a **real HTTP download** and
  writes a real executable file into `$HOME/../usr/bin` (on `PATH`) —
  it is not a text-only simulation. See caveats below.

## What's included

```
app/src/main/java/com/example/miniterm/
  MainActivity.kt          - wires everything together
  TerminalView.kt          - custom scrollback view + keyboard input
  PTYManager.kt             - spawns /system/bin/sh and streams output
  EscapeSequenceParser.kt   - strips ANSI escape codes
  RealPackageManager.kt     - downloads & installs real binaries via HTTP
.github/workflows/build.yml - CI: builds a debug APK on every push
```

## v2 fixes

- Removed the `-i` (interactive) flag from the spawned shell. Without
  a real kernel PTY, `-i` made `/system/bin/sh` try to grab a
  controlling terminal ("can't find tty fd", "won't have job
  control") and echo input back, which combined with the app's own
  on-screen echo caused typed commands to appear duplicated.
- `PATH` now includes the app's own `usr/bin` directory so packages
  installed via `pkg install` are actually runnable by name.

## Real package manager — important caveats

`RealPackageManager` genuinely downloads a file over HTTP and
`chmod`s it executable. It is **not** connected to Termux's package
repository (that requires their own cross-compiled bootstrap
binaries and signing infrastructure, which this project does not
copy or redistribute). By default its package index is **empty** —
nothing downloads until you call:

```kotlin
pkgManager.registerPackage("hello", "https://example.com/arm64/hello")
```

with a URL you've verified yourself. Two things determine whether an
installed binary actually runs:

1. It must be a **statically linked** binary (or bundle its shared
   libraries) — ordinary glibc-linked Linux binaries generally won't
   run on Android.
2. It must match your device's exact CPU ABI (`arm64-v8a`,
   `armeabi-v7a`, `x86_64`, etc.) — check via `adb shell getprop
   ro.product.cpu.abi`.

## Building locally

```
./gradlew assembleDebug
```

## Building via GitHub Actions

Push this repo to GitHub; `.github/workflows/build.yml` builds a
debug APK and uploads it as artifact `miniterm-debug-apk`. Gradle is
pinned to 8.7 to match AGP 8.2.2 — don't bump one without the other.

## Extending toward "closer to Termux"

Real Termux-level fidelity still requires things this skeleton
leaves out:

- A real PTY via native code (JNI wrapping `openpty`/`forkpty`)
- A genuine multi-arch package repository + signing/verification
- Full VT100/xterm escape-sequence handling for cursor movement, colors
- On-screen extra-keys row (ESC, TAB, CTRL, arrow keys)

Each of these is a substantial, separate engineering task — not
something that can be copied in from another project.
