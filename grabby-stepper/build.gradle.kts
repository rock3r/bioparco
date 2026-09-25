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
        mainClass = "dev.sebastiano.grabbystepper.MainKt"
        nativeDistributions {
            packageName = "GrabbyStepper"
            packageVersion = "1.0.0"
            description = "bioparco specimen: grabby pill stepper"
        }
    }
}
