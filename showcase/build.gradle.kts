plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktfmt)
}

kotlin {
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(project(":grabby-stepper"))
            implementation(project(":chat-bubble-transition"))
            implementation(project(":processing-field"))
            implementation(project(":thinking-orbs"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jewel.int.ui.standalone)
        }
    }
}

// Jewel 0.41 Icons API still pulls the IJP kotlinx-coroutines fork; that fork
// crashes packaged standalone apps against coroutines 1.11+.
dependencies {
    modules {
        module("org.jetbrains.intellij.deps.kotlinx:kotlinx-coroutines-core-jvm") {
            replacedBy(
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm",
                "The IJP fork lags upstream",
            )
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
