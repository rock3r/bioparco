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
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.material3)
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
    }
}

compose.desktop {
    application {
        mainClass = "chatbubble.MainKt"
        nativeDistributions {
            packageName = "ChatBubbleTransition"
            packageVersion = "1.0.0"
            description = "bioparco specimen: Kavsoft chat-bubble send transition"
        }
    }
}
