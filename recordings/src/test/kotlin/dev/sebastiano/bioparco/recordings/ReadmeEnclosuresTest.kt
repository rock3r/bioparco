package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test

class ReadmeEnclosuresTest {
    @Test
    fun enclosureCatalogMatchesSettingsIncludes() {
        assertEquals(
            RecordingPaths.expectedSpecimens().toSet(),
            Enclosures.all.map { it.module }.toSet(),
        )
    }

    @Test
    fun enclosureIndexIsATableOfHostedMp4s() {
        val repoRoot = RecordingPaths.settingsGradleKts().parent
        val published = PublishedManifest.load(repoRoot.resolve("recordings/published.json"))
        assumeFalse(
            published.isEmpty(),
            "published.json is written after the first static.sebastiano.dev upload",
        )
        val readme = Files.readString(repoRoot.resolve("README.md"))

        assertFalse(
            readme.contains(".gif", ignoreCase = true),
            "do not convert README movies to GIFs",
        )
        assertFalse(
            readme.contains("raw.githubusercontent.com"),
            "GitHub strips <video> for raw.githubusercontent.com",
        )
        assertFalse(
            readme.contains("releases/download"),
            "GitHub treats release assets as attachments, not playable movies",
        )
        assertTrue(readme.contains("<table"), "enclosure index must be a table")
        assertTrue(
            readme.contains("<video"),
            "GitHub README can embed <video> when the host is allowed",
        )

        RecordingPaths.expectedNames().forEach { movie ->
            val specimen = movie.removeSuffix(".mp4")
            val url = published.getValue(specimen).url
            assertTrue(url.startsWith(PublishedSpecimen.HOSTED_PREFIX), url)
            assertTrue(url.endsWith(".mp4"), url)
            assertTrue(readme.contains(url), "README must embed $url for $specimen")
            assertTrue(readme.contains("<a href=\"$specimen/\""), "README must link to $specimen/")
        }
    }

    @Test
    fun renderWritesATableCellPerHostedMovie() {
        val published =
            mapOf(
                "grabby-stepper" to
                    PublishedSpecimen(
                        "hash-a",
                        "https://static.sebastiano.dev/public/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.mp4",
                    ),
                "chat-bubble-transition" to
                    PublishedSpecimen(
                        "hash-b",
                        "https://static.sebastiano.dev/public/bbbbbbbb-cccc-dddd-eeee-ffffffffffff.mp4",
                    ),
                "processing-field" to
                    PublishedSpecimen(
                        "hash-c",
                        "https://static.sebastiano.dev/public/cccccccc-dddd-eeee-ffff-000000000000.mp4",
                    ),
            )
        val html = ReadmeEnclosures.render(published)
        assertTrue(html.contains("<table"))
        published.values.forEach { specimen ->
            assertTrue(html.contains("<video src=\"${specimen.url}\""), specimen.url)
        }
        val replaced =
            ReadmeEnclosures.replace(
                "## Enclosures\n\nold\n\n## Run the showcase\n",
                published,
            )
        assertTrue(replaced.contains(ReadmeEnclosures.START))
        assertTrue(replaced.contains("## Run the showcase"))
        assertFalse(replaced.contains("\nold\n"))
    }
}
