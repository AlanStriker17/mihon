# Mihon Desktop

For a client-friendly Windows build guide, open `http://127.0.0.1:8766/build` in a browser
while the desktop app is running, or open [build.html](./build.html) directly. The local
page links to the manual Windows GitHub Actions workflow and documents local packaging.
The embedded server is loopback-only, so this page is available only on the computer running
the app.

The JVM desktop client currently includes:

- Browse/search against installed desktop `SourcePlugin` implementations.
- MangaDex search, chapter lists, and reader pages through its public API.
- Install and remove trusted desktop plugin JARs from the Extensions tab. Plugins are
  loaded at application startup, so restart Mihon after changing installed plugins.
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

On Windows with JDK 21 installed, build a portable ZIP without WiX:

```powershell
.\gradlew.bat :desktop:client:packageWindowsPortableZip
```

Alternatively, from a Windows PowerShell prompt in the repository, run
`.\desktop\client\Build-Mihon.ps1 -PackageType portable`. This creates a ZIP
containing `Mihon.exe` and its bundled runtime, without requiring WiX. To build
traditional installers, use `-PackageType exe` or `-PackageType msi`; these
require WiX Toolset on the Windows machine. All `jpackage` tasks must run on
Windows. The manual GitHub Actions workflow at
`.github/workflows/build-desktop-windows.yml` builds the installer on a Windows
runner; select portable, EXE, or MSI when starting it. It uploads the selected
package artifact for 14 days. Pushing to the `add-windows-desktop-build` branch
automatically builds a portable ZIP; the workflow run appears in GitHub Actions.
Source plugins
implement `SourcePluginProvider`, compile against `desktop:source-api`, and
must either be present on the application's runtime classpath or be installed as
a JAR in `~/.mihon/plugins`. A plugin JAR must include
`META-INF/services/mihon.desktop.source.SourcePluginProvider` and its required
runtime dependencies. Plugins are executable code with access to the user's
computer; install only JARs from publishers you trust. Existing Mihon Android APK
extensions are not loadable on desktop without rebuilding their source
implementations.

This is an incremental desktop client, not full Mihon feature parity. Sync,
tracking providers, Android backup formats, automatic extension discovery from
Android repositories, and a production-grade paged image viewer are not yet
implemented.
