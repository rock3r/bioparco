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
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(project(":grabby-stepper"))
    implementation(project(":chat-bubble-transition"))
    implementation(project(":thinking-orbs"))

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

val recordSpecimens by
    tasks.registering(Test::class) {
        group = "verification"
        description = "Drive each specimen with Spectre and write MP4s under build/recordings/."
        testClassesDirs = tasks.test.get().testClassesDirs
        classpath = tasks.test.get().classpath
        useJUnitPlatform { includeTags("recording") }
        systemProperty(
            "bioparco.recordings.dir",
            layout.buildDirectory.dir("recordings").get().asFile.absolutePath,
        )
        // Real windows + Screen Recording TCC. Never part of ./gradlew check.
        outputs.dir(layout.buildDirectory.dir("recordings"))
    }
