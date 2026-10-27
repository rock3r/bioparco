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
            implementation(libs.jewel.int.ui.standalone)
            implementation(compose.ui)
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

compose.desktop {
    application {
        mainClass = "dev.sebastiano.thinkingbloom.MainKt"
        nativeDistributions {
            packageName = "ThinkingBloom"
            packageVersion = "1.0.0"
            description =
                "bioparco specimen: a 'Thinking…' label that blooms into flowers as a band sweeps it"
        }
    }
}
