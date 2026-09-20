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
            implementation(compose.material3)
            implementation(compose.ui)
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.ui)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.sebastiano.processingfield.MainKt"
        nativeDistributions {
            packageName = "ProcessingField"
            packageVersion = "1.0.0"
            description = "bioparco specimen: a grid of marks whose sizes describe one soft mass"
        }
    }
}
