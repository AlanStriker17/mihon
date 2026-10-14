$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gradleWrapper = Join-Path $repositoryRoot "gradlew.bat"
$outputDirectory = Join-Path $PSScriptRoot "build\windows-package"

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper was not found. Run this script from a Mihon repository checkout."
}

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    throw "JDK 21 is required. Install a JDK 21 distribution and ensure java is on PATH."
}

$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21(\.|")') {
    throw "This build requires Java 21. Detected: $($javaVersion.Trim())"
}

Push-Location $repositoryRoot
try {
    & $gradleWrapper :desktop:client:packageWindowsExe --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed to build the Windows installer (exit code $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $outputDirectory)) {
    throw "Build completed but the installer directory was not created: $outputDirectory"
}

$installer = Get-ChildItem -Path $outputDirectory -Filter "*.exe" -File | Select-Object -First 1
if (-not $installer) {
    throw "Build completed but no Windows installer was found in $outputDirectory"
}

Write-Host "Installer created: $($installer.FullName)"
Start-Process explorer.exe -ArgumentList $outputDirectory
