plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.desktop.sourceApi)
    implementation(projects.desktop.sourceServer)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "mihon.desktop.client.MainKt"
    applicationName = "Mihon"
}

fun registerWindowsPackage(taskName: String, packageType: String) = tasks.register<Exec>(taskName) {
    group = "distribution"
    description = "Package the desktop client as a Windows $packageType using jpackage."
    dependsOn(tasks.installDist)
    onlyIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }

    val outputDir = layout.buildDirectory.dir("windows-package/$packageType")
    outputs.dir(outputDir)
    commandLine(
        "jpackage",
        "--type",
        packageType,
        "--name",
        "Mihon",
        "--app-version",
        "0.1.0",
        "--input",
        tasks.installDist.get().destinationDir.resolve("lib").absolutePath,
        "--main-jar",
        "client.jar",
        "--main-class",
        "mihon.desktop.client.MainKt",
        "--dest",
        outputDir.get().asFile.absolutePath,
    )
}

registerWindowsPackage("packageWindowsPortable", "app-image")
registerWindowsPackage("packageWindowsExe", "exe")
registerWindowsPackage("packageWindowsMsi", "msi")

tasks.register<Zip>("packageWindowsPortableZip") {
    group = "distribution"
    description = "Create a portable Windows ZIP containing Mihon.exe and its bundled runtime."
    dependsOn("packageWindowsPortable")
    onlyIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }

    val appImageDir = layout.buildDirectory.dir("windows-package/app-image/Mihon")
    from(appImageDir) {
        into("Mihon")
    }
    archiveFileName.set("Mihon-portable-windows.zip")
    destinationDirectory.set(layout.buildDirectory.dir("windows-package/portable"))
}
