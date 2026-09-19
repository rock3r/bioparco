plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktfmt)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":grabby-stepper"))
    implementation(project(":chat-bubble-transition"))
    implementation(project(":processing-field"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spectre.core)
    testImplementation(libs.spectre.testing)
    testImplementation(libs.spectre.recording)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.spectre.recording.macos)
    testRuntimeOnly(libs.spectre.recording.linux)
    testRuntimeOnly(libs.spectre.recording.windows)
}

tasks.test { useJUnitPlatform { excludeTags("recording") } }

val recordingsOutput = layout.buildDirectory.dir("recordings")

tasks.register<Test>("recordSpecimens") {
    group = "verification"
    description = "Drive each specimen with Spectre and write MP4s under build/recordings/."
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("recording") }
    systemProperty("bioparco.recordings.dir", recordingsOutput.get().asFile.absolutePath)
    systemProperty("java.awt.headless", "false")
    // Linux Xvfb / CI: software Skiko. macOS Aqua on Coso leaves this unset.
    if (
        providers.environmentVariable("CI").orNull == "true" ||
            providers.environmentVariable("BIOPARCO_SOFTWARE_RENDER").isPresent
    ) {
        systemProperty("skiko.renderApi", "SOFTWARE_COMPAT")
    }
    // Always re-record. A cached empty directory would still look up-to-date.
    outputs.dir(recordingsOutput)
    outputs.upToDateWhen { false }
    doLast {
        val dir = recordingsOutput.get().asFile
        val houseModules = setOf("showcase", "recordings")
        val missing =
            rootProject.subprojects
                .map { it.name }
                .filter { it !in houseModules }
                .map { "$it.mp4" }
                .filter { name ->
                    val file = dir.resolve(name)
                    !file.isFile || file.length() < 1_000
                }
        check(missing.isEmpty()) {
            "recordSpecimens did not write usable README movies under ${dir.absolutePath}: $missing"
        }
    }
}
