package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RecordingPathsTest {
    @Test
    fun defaultsToBuildRecordings() {
        assertEquals(Path.of("build", "recordings"), RecordingPaths.directory(""))
        assertEquals(
            Path.of("build", "recordings", "grabby-stepper.mp4"),
            RecordingPaths.file(RecordingPaths.GRABBY_STEPPER, RecordingPaths.directory("")),
        )
    }

    @Test
    fun honorsExplicitDirectory() {
        val dir = RecordingPaths.directory("/tmp/bioparco-recordings")
        assertEquals(Path.of("/tmp/bioparco-recordings"), dir)
        assertEquals(
            dir.resolve("chat-bubble-transition.mp4"),
            RecordingPaths.file(RecordingPaths.CHAT_BUBBLE, dir),
        )
        assertEquals(
            dir.resolve("processing-field.mp4"),
            RecordingPaths.file(RecordingPaths.PROCESSING_FIELD, dir),
        )
        assertEquals(
            dir.resolve("thinking-orbs.mp4"),
            RecordingPaths.file(RecordingPaths.THINKING_ORBS, dir),
        )
        assertEquals(
            dir.resolve("dot-matrix-recorder.mp4"),
            RecordingPaths.file(RecordingPaths.DOT_MATRIX_RECORDER, dir),
        )
        assertEquals(
            dir.resolve("border-beam.mp4"),
            RecordingPaths.file(RecordingPaths.BORDER_BEAM, dir),
        )
    }

    @Test
    fun expectedNamesFollowIncludedSpecimenModules() {
        val settings = RecordingPaths.settingsGradleKts()
        assertEquals(
            listOf(
                "grabby-stepper.mp4",
                "chat-bubble-transition.mp4",
                "processing-field.mp4",
                "thinking-orbs.mp4",
                "dot-matrix-recorder.mp4",
                "border-beam.mp4",
            ),
            RecordingPaths.expectedNames(settings),
        )
        assertTrue(RecordingPaths.expectedNames(settings).contains("processing-field.mp4"))
        assertTrue(RecordingPaths.expectedNames(settings).all { it.endsWith(".mp4") })
    }

    @Test
    fun expectedNamesIgnoreHouseModules(@TempDir dir: Path) {
        Files.writeString(
            dir.resolve("settings.gradle.kts"),
            """
            include(":new-specimen")
            include(":showcase")
            include(":recordings")
            """
                .trimIndent(),
        )
        assertEquals(
            listOf("new-specimen.mp4"),
            RecordingPaths.expectedNames(dir.resolve("settings.gradle.kts")),
        )
    }

    @Test
    fun missingOutputsReportsAbsentAndTinyFiles(@TempDir dir: Path) {
        assertEquals(RecordingPaths.expectedNames(), RecordingPaths.missingOutputs(dir))

        Files.write(dir.resolve("chat-bubble-transition.mp4"), ByteArray(64))
        Files.write(dir.resolve("grabby-stepper.mp4"), ByteArray(2_000))
        assertEquals(
            listOf(
                "chat-bubble-transition.mp4",
                "processing-field.mp4",
                "thinking-orbs.mp4",
                "dot-matrix-recorder.mp4",
                "border-beam.mp4",
            ),
            RecordingPaths.missingOutputs(dir),
        )

        Files.write(dir.resolve("chat-bubble-transition.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("processing-field.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("thinking-orbs.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("dot-matrix-recorder.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("border-beam.mp4"), ByteArray(2_000))
        assertEquals(emptyList<String>(), RecordingPaths.missingOutputs(dir))
    }
}
