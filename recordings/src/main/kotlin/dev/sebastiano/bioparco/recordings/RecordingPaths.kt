package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path

object RecordingPaths {
    const val GRABBY_STEPPER = "grabby-stepper.mp4"
    const val CHAT_BUBBLE = "chat-bubble-transition.mp4"
    const val PROCESSING_FIELD = "processing-field.mp4"
    const val MIN_USABLE_BYTES = 1_000L

    private val houseModules = setOf("showcase", "recordings")
    private val includePattern = Regex("""include\(":([^"]+)"\)""")

    fun expectedNames(settingsFile: Path = settingsGradleKts()): List<String> =
        includePattern
            .findAll(Files.readString(settingsFile))
            .map { it.groupValues[1] }
            .filter { it !in houseModules }
            .map { "$it.mp4" }
            .toList()

    fun expectedSpecimens(settingsFile: Path = settingsGradleKts()): List<String> =
        expectedNames(settingsFile).map { it.removeSuffix(".mp4") }

    fun recordingTestInclude(specimen: String): String {
        // chat-bubble-transition's test is ChatBubbleRecordingTest, not ChatBubbleTransition…
        if (specimen == "chat-bubble-transition") return "*ChatBubbleRecordingTest"
        val camel =
            specimen.split('-').joinToString("") { part ->
                part.replaceFirstChar { it.uppercase() }
            }
        return "*${camel}RecordingTest"
    }

    fun requestedMovieNames(
        only: String? = System.getenv("BIOPARCO_RECORD_ONLY"),
        settingsFile: Path = settingsGradleKts(),
    ): List<String> {
        val expected = expectedNames(settingsFile)
        val raw = only?.trim().orEmpty()
        if (raw.isEmpty()) return expected
        val movies =
            raw.split(',')
                .map { it.trim().removeSuffix(".mp4") }
                .filter { it.isNotEmpty() }
                .map { "$it.mp4" }
        val unknown = movies.filter { it !in expected }
        check(unknown.isEmpty()) { "unknown specimens: $unknown" }
        return movies
    }

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
     * Names the README movies expect that are missing or too small under [directory]. CI uses this
     * so a skipped or broken Spectre run cannot publish a partial set. Pass [names] to check only
     * the specimens this run was asked to record.
     */
    fun missingOutputs(
        directory: Path,
        minBytes: Long = MIN_USABLE_BYTES,
        names: List<String> = expectedNames(),
    ): List<String> = names.filter { name ->
        val output = directory.resolve(name)
        !Files.isRegularFile(output) || Files.size(output) < minBytes
    }
}
