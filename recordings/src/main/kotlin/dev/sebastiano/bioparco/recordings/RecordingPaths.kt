package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path

object RecordingPaths {
    const val GRABBY_STEPPER = "grabby-stepper.mp4"
    const val CHAT_BUBBLE = "chat-bubble-transition.mp4"
    const val PROCESSING_FIELD = "processing-field.mp4"
    const val MIN_USABLE_BYTES = 1_000L

    fun expectedNames(): List<String> = listOf(GRABBY_STEPPER, CHAT_BUBBLE, PROCESSING_FIELD)

    fun directory(explicit: String? = System.getProperty("bioparco.recordings.dir")): Path {
        val value = explicit?.trim().orEmpty()
        return if (value.isEmpty()) Path.of("build", "recordings") else Path.of(value)
    }

    fun file(name: String, directory: Path = directory()): Path = directory.resolve(name)

    /**
     * Names the README release links expect that are missing or too small under [directory].
     * CI uses this so a skipped or broken Spectre run cannot publish a partial set.
     */
    fun missingOutputs(directory: Path, minBytes: Long = MIN_USABLE_BYTES): List<String> =
        expectedNames().filter { name ->
            val output = directory.resolve(name)
            !Files.isRegularFile(output) || Files.size(output) < minBytes
        }
}
