package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path

object RecordingPaths {
    const val GRABBY_STEPPER = "grabby-stepper.mp4"
    const val CHAT_BUBBLE = "chat-bubble-transition.mp4"
    const val PROCESSING_FIELD = "processing-field.mp4"
    const val THINKING_ORBS = "thinking-orbs.mp4"
    const val DOT_MATRIX_RECORDER = "dot-matrix-recorder.mp4"
    const val BORDER_BEAM = "border-beam.mp4"
    const val ACHIEVEMENT_BADGE = "achievement-badge.mp4"
    const val MIN_USABLE_BYTES = 1_000L

    private val houseModules = setOf("showcase", "recordings")
    // Every `include(...)` call, with any whitespace and any number of project paths.
    private val includeCall = Regex("""\binclude\s*\(([^)]*)\)""")
    private val projectPath = Regex(""""\s*:?([^"]+?)\s*"""")

    fun expectedNames(settingsFile: Path = settingsGradleKts()): List<String> =
        specimenModules(settingsFile).map { "$it.mp4" }

    /** The specimen modules included in [settingsFile], in include order. */
    fun specimenModules(settingsFile: Path = settingsGradleKts()): List<String> =
        includeCall
            .findAll(Files.readString(settingsFile))
            .flatMap { call -> projectPath.findAll(call.groupValues[1]).map { it.groupValues[1] } }
            .filter { it !in houseModules }
            .toList()

    fun settingsGradleKts(start: Path = Path.of("").toAbsolutePath()): Path {
        var dir: Path? = start.normalize()
        while (dir != null) {
            val candidate = dir.resolve("settings.gradle.kts")
            if (Files.isRegularFile(candidate)) return candidate
            dir = dir.parent
        }
        error("settings.gradle.kts not found from $start")
    }

    fun directory(explicit: String? = System.getProperty("bioparco.recordings.dir")): Path {
        val value = explicit?.trim().orEmpty()
        return if (value.isEmpty()) Path.of("build", "recordings") else Path.of(value)
    }

    fun file(name: String, directory: Path = directory()): Path = directory.resolve(name)

    /**
     * Names the README release links expect that are missing or too small under [directory]. CI
     * uses this so a skipped or broken Spectre run cannot publish a partial set.
     */
    fun missingOutputs(directory: Path, minBytes: Long = MIN_USABLE_BYTES): List<String> =
        expectedNames().filter { name ->
            val output = directory.resolve(name)
            !Files.isRegularFile(output) || Files.size(output) < minBytes
        }
}
