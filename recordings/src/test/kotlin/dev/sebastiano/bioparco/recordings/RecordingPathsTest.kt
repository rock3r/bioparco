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
        // One row per specimen: its constant, and the file it names.
        val movies =
            listOf(
                RecordingPaths.CHAT_BUBBLE to "chat-bubble-transition.mp4",
                RecordingPaths.PROCESSING_FIELD to "processing-field.mp4",
                RecordingPaths.THINKING_ORBS to "thinking-orbs.mp4",
                RecordingPaths.DOT_MATRIX_RECORDER to "dot-matrix-recorder.mp4",
                RecordingPaths.BORDER_BEAM to "border-beam.mp4",
                RecordingPaths.ACHIEVEMENT_BADGE to "achievement-badge.mp4",
                RecordingPaths.COMPONENT_ANATOMY to "component-anatomy.mp4",
                RecordingPaths.SCROLL_EFFECTS to "scroll-effects.mp4",
                RecordingPaths.PEEL_STICKER to "peel-sticker.mp4",
                RecordingPaths.HONEYCOMB to "honeycomb.mp4",
                RecordingPaths.HAIRLINE to "hairline.mp4",
                RecordingPaths.DEPARTURE_BOARD to "departure-board.mp4",
                RecordingPaths.HOLO_CARD to "holo-card.mp4",
                RecordingPaths.WHEEL_PICKER to "wheel-picker.mp4",
                RecordingPaths.THINKING_BLOOM to "thinking-bloom.mp4",
                RecordingPaths.SQUEEZE_PROGRESS to "squeeze-progress.mp4",
                RecordingPaths.ANATOMY to "anatomy.mp4",
                RecordingPaths.EMAIL_INVITE to "email-invite.mp4",
                RecordingPaths.ROUTINE_TIMELINE to "routine-timeline.mp4",
                RecordingPaths.LAYOUT_MORPH to "layout-morph.mp4",
                RecordingPaths.CURRENCY_SWARM to "currency-swarm.mp4",
                RecordingPaths.STAMP_BOOK to "stamp-book.mp4",
                RecordingPaths.EXPANDING_SEARCH to "expanding-search.mp4",
                RecordingPaths.PARTICLE_RING to "particle-ring.mp4",
                RecordingPaths.KNOCKOUT_BADGE to "knockout-badge.mp4",
                RecordingPaths.TAB_SWITCH to "tab-switch.mp4",
            )
        for ((movie, name) in movies) assertEquals(
            dir.resolve(name),
            RecordingPaths.file(movie, dir),
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
                "achievement-badge.mp4",
                "component-anatomy.mp4",
                "scroll-effects.mp4",
                "peel-sticker.mp4",
                "honeycomb.mp4",
                "hairline.mp4",
                "departure-board.mp4",
                "holo-card.mp4",
                "wheel-picker.mp4",
                "thinking-bloom.mp4",
                "squeeze-progress.mp4",
                "anatomy.mp4",
                "email-invite.mp4",
                "routine-timeline.mp4",
                "layout-morph.mp4",
                "currency-swarm.mp4",
                "stamp-book.mp4",
                "expanding-search.mp4",
                "particle-ring.mp4",
                "knockout-badge.mp4",
                "tab-switch.mp4",
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
    fun specimenModulesReadEveryIncludeForm(@TempDir dir: Path) {
        Files.writeString(
            dir.resolve("settings.gradle.kts"),
            """
            include(":alpha", ":beta")
            include ( ":gamma" )
            include(
                ":delta",
                ":showcase",
            )
            include(":recordings")
            """
                .trimIndent(),
        )
        assertEquals(
            listOf("alpha", "beta", "gamma", "delta"),
            RecordingPaths.specimenModules(dir.resolve("settings.gradle.kts")),
        )
    }

    @Test
    fun specimenModulesSkipCommentedOutIncludes(@TempDir dir: Path) {
        Files.writeString(
            dir.resolve("settings.gradle.kts"),
            """
            include(":kept")
            // include(":removed")
            /* include(":parked") */
            include(":also-kept") // a trailing comment
            """
                .trimIndent(),
        )
        assertEquals(
            listOf("kept", "also-kept"),
            RecordingPaths.specimenModules(dir.resolve("settings.gradle.kts")),
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
                "achievement-badge.mp4",
                "component-anatomy.mp4",
                "scroll-effects.mp4",
                "peel-sticker.mp4",
                "honeycomb.mp4",
                "hairline.mp4",
                "departure-board.mp4",
                "holo-card.mp4",
                "wheel-picker.mp4",
                "thinking-bloom.mp4",
                "squeeze-progress.mp4",
                "anatomy.mp4",
                "email-invite.mp4",
                "routine-timeline.mp4",
                "layout-morph.mp4",
                "currency-swarm.mp4",
                "stamp-book.mp4",
                "expanding-search.mp4",
                "particle-ring.mp4",
                "knockout-badge.mp4",
                "tab-switch.mp4",
            ),
            RecordingPaths.missingOutputs(dir),
        )

        Files.write(dir.resolve("chat-bubble-transition.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("processing-field.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("thinking-orbs.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("dot-matrix-recorder.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("border-beam.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("achievement-badge.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("component-anatomy.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("scroll-effects.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("peel-sticker.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("honeycomb.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("hairline.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("departure-board.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("holo-card.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("wheel-picker.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("thinking-bloom.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("squeeze-progress.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("anatomy.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("email-invite.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("routine-timeline.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("layout-morph.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("currency-swarm.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("stamp-book.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("expanding-search.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("particle-ring.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("knockout-badge.mp4"), ByteArray(2_000))
        Files.write(dir.resolve("tab-switch.mp4"), ByteArray(2_000))
        assertEquals(emptyList<String>(), RecordingPaths.missingOutputs(dir))
    }
}
