package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ReadmeMediaTest {
    @Test
    fun everySpecimenShowsItsAnimationInBothReadmes() {
        val root = RecordingPaths.settingsGradleKts().parent
        assertEquals(emptyList<String>(), ReadmeMedia.problems(root))
    }

    @Test
    fun aCompleteSpecimenPasses(@TempDir root: Path) {
        repo(root, rootEntry = MEDIA, specimenReadme = MEDIA)
        assertEquals(emptyList<String>(), problems(root))
    }

    @Test
    fun aSpecimenWithoutAPreviewIsReported(@TempDir root: Path) {
        val movieOnly = "[mp4]($MOVIE)"
        repo(root, rootEntry = movieOnly, specimenReadme = movieOnly)
        val problems = problems(root)
        assertEquals(2, problems.count { it.contains("preview") }, "$problems")
    }

    @Test
    fun anyOtherUrlIsReported(@TempDir root: Path) {
        // An old one-off upload, or a link that downloads instead of playing.
        val stale =
            "![New](https://static.example.com/public/1234.webp)\n\n" +
                "[mp4](https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/new-specimen.mp4)"
        repo(root, rootEntry = stale, specimenReadme = MEDIA)
        val problems = problems(root)
        assertEquals(2, problems.size, "$problems")
        assertTrue(problems.all { it.startsWith("README.md entry") }, "$problems")
    }

    @Test
    fun anMp4WrittenAsAnImageIsNotALink(@TempDir root: Path) {
        val asImage = "![New]($PREVIEW)\n\n![mp4]($MOVIE)"
        repo(root, rootEntry = asImage, specimenReadme = asImage)
        assertEquals(2, problems(root).count { it.contains("does not link") })
    }

    @Test
    fun mediaInsideHtmlCommentsDoesNotCount(@TempDir root: Path) {
        val hidden = "<!-- $MEDIA -->"
        repo(root, rootEntry = hidden, specimenReadme = hidden)
        assertEquals(4, problems(root).size)
    }

    @Test
    fun mediaShownOnlyAsCodeDoesNotCount(@TempDir root: Path) {
        val fenced = "```md\n$MEDIA\n```"
        val spans = "``![New]($PREVIEW)`` and `[mp4]($MOVIE)`"
        repo(root, rootEntry = fenced, specimenReadme = spans)
        assertEquals(4, problems(root).size)
    }

    @Test
    fun theSpecimenReadmeMediaMustSitUnderTheRecordingSection(@TempDir root: Path) {
        repo(
            root,
            rootEntry = MEDIA,
            specimenReadme = "## Concepts\n\n$MEDIA",
            recordingHeading = false,
        )
        val problems = problems(root)
        assertTrue(problems.any { it.contains("## Recording") }, "$problems")
    }

    private fun problems(root: Path) = ReadmeMedia.problems(root, mediaBase = BASE)

    private fun repo(
        root: Path,
        rootEntry: String,
        specimenReadme: String,
        recordingHeading: Boolean = true,
    ) {
        Files.writeString(
            root.resolve("settings.gradle.kts"),
            "include(\":new-specimen\")\ninclude(\":showcase\")\ninclude(\":recordings\")\n",
        )
        Files.writeString(
            root.resolve("README.md"),
            "# Repo\n\n## Enclosures\n\n### [New](new-specimen/)\n\n$rootEntry\n\n## Run the showcase\n",
        )
        Files.createDirectories(root.resolve("new-specimen"))
        val heading = if (recordingHeading) "## Recording\n\n" else ""
        Files.writeString(
            root.resolve("new-specimen/README.md"),
            "# New\n\n$heading$specimenReadme\n",
        )
    }

    private companion object {
        const val BASE = "https://static.example.com/stable/test/"
        const val PREVIEW = "${BASE}new-specimen.webp"
        const val MOVIE = "${BASE}new-specimen.mp4"
        const val MEDIA = "![New]($PREVIEW)\n\n[mp4]($MOVIE)"
    }
}
