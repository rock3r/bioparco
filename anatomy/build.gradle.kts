plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktfmt)
}

kotlin {
    jvm()
    jvmToolchain(25)

    sourceSets {
        commonMain.dependencies { implementation(kotlin("stdlib")) }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmMain.dependencies {
            implementation(project(":tracing"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.desktop.currentOs)
            implementation(libs.jewel.int.ui.standalone)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.sebastiano.anatomy.MainKt"
        nativeDistributions {
            packageName = "Anatomy"
            packageVersion = "1.0.0"
            description = "bioparco specimen: crafted isometric figures of real, working objects"
        }
    }
}

// The kit is ported from MIT-licensed code, so the jar (and every app that bundles it) carries
// that notice too, not just this folder.
tasks.named<Jar>("jvmJar") {
    from("LICENSE") {
        into("META-INF")
        rename { "LICENSE-anatomy.txt" }
    }
}

// Renders a figure to PNG for review: `./gradlew :anatomy:capture --args="dial-indicator --light"`.
// See .agents/skills/anatomy/references/verify.md.
tasks.register<JavaExec>("capture") {
    group = "verification"
    description = "Render an Anatomy figure to PNG: poses, themes, widths and close-ups."
    val jvm = kotlin.jvm()
    val main = jvm.compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass = "dev.sebastiano.anatomy.CaptureKt"
    workingDir = rootProject.projectDir
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    systemProperty("java.awt.headless", "true")
}
