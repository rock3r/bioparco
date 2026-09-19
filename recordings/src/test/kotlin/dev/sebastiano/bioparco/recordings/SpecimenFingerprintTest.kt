package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SpecimenFingerprintTest {
    @Test
    fun hashChangesOnlyWhenSpecimenSourcesChange(@TempDir root: Path) {
        writeTree(
            root,
            "widget/src/commonMain/kotlin/App.kt" to "fun app() = 1\n",
            "recordings/src/test/kotlin/dev/sebastiano/bioparco/recordings/WidgetRecordingTest.kt" to
                "class WidgetRecordingTest { val movie = \"widget.mp4\" }\n",
            "recordings/src/test/kotlin/dev/sebastiano/bioparco/recordings/SpecimenWindow.kt" to
                "class SpecimenWindow\n",
            "widget/README.md" to "ignore me\n",
        )
        val first = SpecimenFingerprint.hash(root, "widget")
        writeTree(root, "widget/README.md" to "still ignored\n")
        assertEquals(first, SpecimenFingerprint.hash(root, "widget"))

        writeTree(root, "widget/src/commonMain/kotlin/App.kt" to "fun app() = 2\n")
        val afterSource = SpecimenFingerprint.hash(root, "widget")
        assertNotEquals(first, afterSource)

        writeTree(
            root,
            "recordings/src/test/kotlin/dev/sebastiano/bioparco/recordings/SpecimenWindow.kt" to
                "class SpecimenWindow changed\n",
        )
        assertNotEquals(afterSource, SpecimenFingerprint.hash(root, "widget"))
    }

    @Test
    fun staleNamesAreSpecimensWhoseHashMoved() {
        val published =
            mapOf(
                "grabby-stepper" to
                    PublishedSpecimen("old-hash", "https://static.sebastiano.dev/public/a.mp4"),
                "chat-bubble-transition" to
                    PublishedSpecimen("same", "https://static.sebastiano.dev/public/b.mp4"),
            )
        val current = mapOf("grabby-stepper" to "new-hash", "chat-bubble-transition" to "same")
        assertEquals(listOf("grabby-stepper"), SpecimenFingerprint.staleNames(current, published))
    }

    @Test
    fun missingPublicationIsStale() {
        assertEquals(
            listOf("processing-field"),
            SpecimenFingerprint.staleNames(
                mapOf("processing-field" to "abc"),
                published = emptyMap(),
            ),
        )
    }

    @Test
    fun publishedSpecimenRejectsGithubReleaseUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            PublishedSpecimen(
                "abc",
                "https://github.com/rock3r/bioparco/releases/download/recordings/grabby-stepper.mp4",
            )
        }
    }

    @Test
    fun parsePublishedManifest() {
        val text =
            """
            {
              "grabby-stepper": {
                "sourceHash": "abc123",
                "url": "https://static.sebastiano.dev/public/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.mp4"
              }
            }
            """
                .trimIndent()
        val parsed = PublishedManifest.parse(text)
        assertEquals("abc123", parsed.getValue("grabby-stepper").sourceHash)
        assertTrue(
            parsed
                .getValue("grabby-stepper")
                .url
                .startsWith("https://static.sebastiano.dev/public/")
        )
    }

    private fun writeTree(root: Path, vararg files: Pair<String, String>) {
        files.forEach { (relative, contents) ->
            val path = root.resolve(relative)
            Files.createDirectories(path.parent)
            Files.writeString(path, contents)
        }
    }
}
