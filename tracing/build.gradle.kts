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
        jvmMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.androidx.tracing.wire)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
