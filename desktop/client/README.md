# Mihon Desktop

For a client-friendly Windows build guide, open `http://127.0.0.1:8766/build` in a browser
while the desktop app is running, or open [build.html](./build.html) directly. The local
page links to the manual Windows GitHub Actions workflow and documents local packaging.
The embedded server is loopback-only, so this page is available only on the computer running
the app.

The JVM desktop client currently includes:

- Browse/search against installed desktop `SourcePlugin` implementations.
- View manga chapters and page lists returned by those plugins.
- A basic page reader with previous/next navigation and bounded image decode.
- A persistent local library at `~/.mihon/library.json`.
- Per-chapter reading position saved at `~/.mihon/reading-progress.json` and restored when
  opening a chapter's page list.
- JSON library backup import/export through native file pickers.
- An embedded loopback-only source server; no separate server command is required.

Build a runnable distribution on JDK 21:

```sh
./gradlew :desktop:client:installDist
desktop/client/build/install/client/bin/client
```

On Windows with JDK 21 installed, build an installer using:

```powershell
.\gradlew.bat :desktop:client:packageWindowsExe
```

Alternatively, from a Windows PowerShell prompt in the repository, run
`.\desktop\client\Build-Mihon.ps1`. It validates Java 21, builds the installer,
and opens the output directory. The installer task uses `jpackage` and WiX; it
must run on Windows. The manual GitHub Actions workflow at
`.github/workflows/build-desktop-windows.yml` builds the installer on a Windows
runner and uploads it as the `mihon-desktop-windows` artifact for 14 days.
Source plugins
implement `SourcePluginProvider`, compile against `desktop:source-api`, and
must be present on the application's runtime classpath. Existing Mihon Android
APK extensions are not loadable on desktop without rebuilding their source
implementations.

This is an incremental desktop client, not full Mihon feature parity. Sync,
tracking providers, Android backup formats, extension installation UI, and a
production-grade paged image viewer are not yet implemented.
