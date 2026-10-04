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
        mainClass = "dev.sebastiano.hairline.MainKt"
        nativeDistributions {
            packageName = "Hairline"
            packageVersion = "1.0.0"
            description =
                "bioparco specimen: nineteen isometric line figures that answer the pointer"
        }
    }
}

// The figures are ported from MIT-licensed code, so the jar (and every app that bundles it) carries
// that notice too, not just this folder.
tasks.named<Jar>("jvmJar") {
    from("LICENSE") {
        into("META-INF")
        rename { "LICENSE-hairline.txt" }
    }
}
