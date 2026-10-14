plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.desktop.sourceApi)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "mihon.desktop.server.MainKt"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    from("../client/build.html") {
        into("web")
    }
}
