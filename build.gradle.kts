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
