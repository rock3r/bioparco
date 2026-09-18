package dev.sebastiano.bioparco.recordings

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
    }
}
