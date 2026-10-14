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

tasks.register<Exec>("packageWindowsExe") {
    group = "distribution"
    description = "Package the desktop client as a Windows installer using jpackage."
    dependsOn(tasks.installDist)
    onlyIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }

    val outputDir = layout.buildDirectory.dir("windows-package")
    outputs.dir(outputDir)
    commandLine(
        "jpackage",
        "--type",
        "exe",
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
