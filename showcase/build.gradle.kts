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
        commonMain.dependencies {
            implementation(project(":tracing"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(project(":grabby-stepper"))
            implementation(project(":chat-bubble-transition"))
            implementation(project(":processing-field"))
            implementation(project(":thinking-orbs"))
            implementation(project(":dot-matrix-recorder"))
            implementation(project(":border-beam"))
            implementation(project(":achievement-badge"))
            implementation(project(":component-anatomy"))
            implementation(project(":scroll-effects"))
            implementation(project(":peel-sticker"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jewel.int.ui.standalone)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.sebastiano.bioparco.showcase.MainKt"
        nativeDistributions {
            packageName = "bioparco"
            packageVersion = "1.0.0"
            description = "A public collection of cute Compose Desktop experiments"
        }
    }
}
