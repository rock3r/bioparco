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
    fun aSpecimenWithoutAPreviewIsReported(@TempDir root: Path) {
        repo(root, rootEntry = "[mp4]($PLAYABLE_MP4)", specimenReadme = "[mp4]($PLAYABLE_MP4)")
        val problems = ReadmeMedia.problems(root)
        assertTrue(problems.any { it.contains("README.md") && it.contains("WebP") }, "$problems")
        assertTrue(
            problems.any { it.contains("new-specimen/README.md") && it.contains("WebP") },
            "$problems",
        )
    }

    @Test
    fun linksThatDownloadInsteadOfPlayingAreReported(@TempDir root: Path) {
        val raw =
            "https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/new-specimen.mp4"
        val release =
            "https://github.com/rock3r/bioparco/releases/download/recordings/new-specimen.mp4"
        repo(
            root,
            rootEntry = "![New]($PREVIEW)\n\n[mp4]($raw)",
            specimenReadme = "![New]($PREVIEW)\n\n[mp4]($release)",
        )
        val problems = ReadmeMedia.problems(root)
        assertEquals(2, problems.count { it.contains("downloads") }, "$problems")
    }

    @Test
    fun bothReadmesMustShowTheSamePreview(@TempDir root: Path) {
        val other = "https://static.example.com/other.webp"
        repo(
            root,
            rootEntry = "![New]($PREVIEW)\n\n[mp4]($PLAYABLE_MP4)",
            specimenReadme = "![New]($other)\n\n[mp4]($PLAYABLE_MP4)",
        )
        val problems = ReadmeMedia.problems(root)
        assertEquals(1, problems.size, "$problems")
        assertTrue(problems.single().contains("same"), "$problems")
    }

    @Test
    fun bothReadmesMustLinkTheSameMp4(@TempDir root: Path) {
        val other = "https://static.example.com/other.mp4"
        repo(
            root,
            rootEntry = "![New]($PREVIEW)\n\n[mp4]($PLAYABLE_MP4)",
            specimenReadme = "![New]($PREVIEW)\n\n[mp4]($other)",
        )
        val problems = ReadmeMedia.problems(root)
        assertEquals(1, problems.size, "$problems")
        assertTrue(problems.single().contains("same MP4"), "$problems")
    }

    @Test
    fun mediaInsideHtmlCommentsDoesNotCount(@TempDir root: Path) {
        val hidden = "<!-- ![New]($PREVIEW)\n\n[mp4]($PLAYABLE_MP4) -->"
        repo(root, rootEntry = hidden, specimenReadme = hidden)
        val problems = ReadmeMedia.problems(root)
        assertEquals(4, problems.count { it.contains("has no") }, "$problems")
    }

    @Test
    fun latestReleaseDownloadsAreRejected(@TempDir root: Path) {
        val latest = "https://github.com/rock3r/bioparco/releases/latest/download/new-specimen.mp4"
        val media = "![New]($PREVIEW)\n\n[mp4]($latest)"
        repo(root, rootEntry = media, specimenReadme = media)
        assertEquals(2, ReadmeMedia.problems(root).count { it.contains("downloads") })
    }

    @Test
    fun anMp4WrittenAsAnImageIsNotALink(@TempDir root: Path) {
        val media = "![New]($PREVIEW)\n\n![mp4]($PLAYABLE_MP4)"
        repo(root, rootEntry = media, specimenReadme = media)
        assertEquals(2, ReadmeMedia.problems(root).count { it.contains("no MP4 link") })
    }

    @Test
    fun plainHttpReleaseDownloadsAreRejected(@TempDir root: Path) {
        val http = "http://github.com/rock3r/bioparco/releases/download/recordings/new-specimen.mp4"
        val media = "![New]($PREVIEW)\n\n[mp4]($http)"
        repo(root, rootEntry = media, specimenReadme = media)
        assertEquals(2, ReadmeMedia.problems(root).count { it.contains("downloads") })
    }

    @Test
    fun mediaShownOnlyAsCodeDoesNotCount(@TempDir root: Path) {
        val fenced = "```md\n![New]($PREVIEW)\n[mp4]($PLAYABLE_MP4)\n```"
        val inline = "`![New]($PREVIEW)` and `[mp4]($PLAYABLE_MP4)`"
        repo(root, rootEntry = fenced, specimenReadme = inline)
        assertEquals(4, ReadmeMedia.problems(root).count { it.contains("has no") })
    }

    @Test
    fun theSpecimenReadmeMediaMustSitUnderTheRecordingSection(@TempDir root: Path) {
        val media = "![New]($PREVIEW)\n\n[mp4]($PLAYABLE_MP4)"
        repo(
            root,
            rootEntry = media,
            specimenReadme = "## Concepts\n\n$media",
            recordingHeading = false,
        )
        val problems = ReadmeMedia.problems(root)
        assertTrue(problems.any { it.contains("## Recording") }, "$problems")
    }

    @Test
    fun aCompleteSpecimenPasses(@TempDir root: Path) {
        val media = "![New]($PREVIEW)\n\n[mp4]($PLAYABLE_MP4)"
        repo(root, rootEntry = media, specimenReadme = media)
        assertEquals(emptyList<String>(), ReadmeMedia.problems(root))
    }

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
        const val PREVIEW = "https://static.example.com/new.webp"
        const val PLAYABLE_MP4 = "https://static.example.com/new.mp4"
    }
}
