import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    base
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktfmt)
}

group = "dev.sebastiano.bioparco"

configure<KtfmtExtension> { kotlinLangStyle() }

configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
    basePath.set(rootProject.layout.projectDirectory)
}

val generatedSourceExcludes = arrayOf("**/build/**", "**/generated/**")

subprojects {
    // Specimens use Jewel components only. compose.desktop.currentOs still drags in Compose
    // Material 2, so drop it everywhere, as Jewel's own standalone sample does.
    configurations.configureEach { exclude(group = "org.jetbrains.compose.material") }

    // Jewel 0.41's Icons API still pulls the IJP kotlinx-coroutines fork; that fork
    // crashes packaged standalone apps against coroutines 1.11+. Every specimen uses Jewel
    // now, so every module that can see it gets the substitution.
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

    pluginManager.withPlugin("com.ncorti.ktfmt.gradle") {
        extensions.configure<KtfmtExtension> { kotlinLangStyle() }
        tasks.withType<KtfmtCheckTask>().configureEach { exclude(*generatedSourceExcludes) }
        tasks.withType<KtfmtFormatTask>().configureEach { exclude(*generatedSourceExcludes) }
    }

    pluginManager.withPlugin("dev.detekt") {
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            parallel = true
            basePath.set(rootProject.layout.projectDirectory)
            // KMP lives under src/commonMain and src/jvmMain, not src/main.
            source.setFrom(layout.projectDirectory.dir("src"))
        }
        dependencies.add("detektPlugins", rootProject.libs.compose.rules.detekt)
        tasks.withType<Detekt>().configureEach {
            exclude(*generatedSourceExcludes)
            jvmTarget = "25"
        }
    }

    afterEvaluate {
        tasks.findByName("check")?.let { check ->
            tasks.findByName("ktfmtCheck")?.let { check.dependsOn(it) }
            tasks.findByName("detekt")?.let { check.dependsOn(it) }
        }
    }
}

tasks.check { dependsOn(subprojects.map { it.tasks.named("check") }) }

tasks.register("recordSpecimens") {
    group = "verification"
    description = "Regenerate specimen MP4s via Spectre (not part of check)."
    dependsOn(":recordings:recordSpecimens")
}
