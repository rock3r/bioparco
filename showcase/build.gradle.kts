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
