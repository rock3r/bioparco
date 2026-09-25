package dev.sebastiano.borderbeam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeamMathTest {
    @Test
    fun pingPongRestsAtTheEndsAndPeaksHalfway() {
        assertEquals(0f, pingPong(0f), TOLERANCE)
        assertEquals(1f, pingPong(0.5f), TOLERANCE)
        assertEquals(0f, pingPong(1f), TOLERANCE)
        assertEquals(0.5f, pingPong(0.25f), TOLERANCE)
    }

    @Test
    fun oscillatorPingPongsBetweenItsEndpoints() {
        assertEquals(
            0.2f,
            oscillatorValue(0.2f, 0.8f, period = 4f, delay = 1f, timeSeconds = 1f),
            TOLERANCE,
        )
        assertEquals(
            0.8f,
            oscillatorValue(0.2f, 0.8f, period = 4f, delay = 1f, timeSeconds = 3f),
            TOLERANCE,
        )
        assertEquals(
            0.2f,
            oscillatorValue(0.2f, 0.8f, period = 4f, delay = 1f, timeSeconds = 5f),
            TOLERANCE,
        )
    }

    @Test
    fun lineTravelKeyframesMatchTheLibrary() {
        val travel =
            listOf(
                0f to 0.06f,
                0.10f to 0.15f,
                0.20f to 0.25f,
                0.30f to 0.35f,
                0.40f to 0.44f,
                0.50f to 0.50f,
                0.60f to 0.56f,
                0.70f to 0.65f,
                0.80f to 0.75f,
                0.90f to 0.85f,
                1f to 0.94f,
            )
        assertEquals(0.06f, sampleKeyframes(travel, 0f), TOLERANCE)
        assertEquals(0.50f, sampleKeyframes(travel, 0.5f), TOLERANCE)
        assertEquals(0.94f, sampleKeyframes(travel, 1f), TOLERANCE)
        assertEquals(0.105f, sampleKeyframes(travel, 0.05f), TOLERANCE)
    }

    @Test
    fun easedKeyframesSlowTheMiddleOfASegment() {
        val ramp = listOf(0f to 0f, 1f to 1f)
        assertEquals(0.25f, sampleKeyframes(ramp, 0.25f), TOLERANCE)
        assertTrue(sampleKeyframes(ramp, 0.25f, eased = true) < 0.25f)
        assertEquals(0f, sampleKeyframes(ramp, 0f, eased = true), TOLERANCE)
        assertEquals(1f, sampleKeyframes(ramp, 1f, eased = true), TOLERANCE)
    }

    @Test
    fun strengthAndCssOpacityStayInsideTheUnitInterval() {
        assertEquals(0f, clampUnit(-0.2f), TOLERANCE)
        assertEquals(0.7f, clampUnit(0.7f), TOLERANCE)
        assertEquals(1f, clampUnit(1.4f), TOLERANCE)
        assertEquals(1f, cssOpacity(1.14f), TOLERANCE)
        assertEquals(0f, cssOpacity(-0.2f), TOLERANCE)
        assertEquals(
            1f,
            layerOpacity(preset = 1.54f, BeamColorVariant.Colorful, strength = 1f, fade = 1f),
            TOLERANCE,
        )
        assertEquals(
            0.35f,
            layerOpacity(preset = 1f, BeamColorVariant.Colorful, strength = 0.7f, fade = 0.5f),
            TOLERANCE,
        )
        assertEquals(
            0.5f,
            layerOpacity(preset = 1f, BeamColorVariant.Mono, strength = 1f, fade = 1f),
            TOLERANCE,
        )
    }

    @Test
    fun durationsHueAndMonoMatchTheLibraryDefaults() {
        assertEquals(1.96f, defaultDurationSeconds(BeamSize.Md), TOLERANCE)
        assertEquals(1.96f, defaultDurationSeconds(BeamSize.Sm), TOLERANCE)
        assertEquals(3.1f, defaultDurationSeconds(BeamSize.Line), TOLERANCE)
        assertEquals(2.3f, defaultDurationSeconds(BeamSize.PulseInner), TOLERANCE)
        assertEquals(2.3f, defaultDurationSeconds(BeamSize.PulseOutside), TOLERANCE)
        assertEquals(13f, effectiveHueRange(BeamSize.Line, 30f), TOLERANCE)
        assertEquals(30f, effectiveHueRange(BeamSize.Md, 30f), TOLERANCE)
        assertTrue(forcesStaticColors(BeamColorVariant.Mono, requested = false))
        assertFalse(forcesStaticColors(BeamColorVariant.Colorful, requested = false))
        assertTrue(forcesStaticColors(BeamColorVariant.Ocean, requested = true))
        assertEquals(0.5f, monoOpacityMultiplier(BeamColorVariant.Mono), TOLERANCE)
        assertEquals(1f, monoOpacityMultiplier(BeamColorVariant.Sunset), TOLERANCE)
    }

    @Test
    fun hueShiftFollowsPingPongOrAFullTurn() {
        assertEquals(
            0f,
            hueShiftDegrees(1f, range = 30f, period = 12f, staticColors = true, continuous = false),
            TOLERANCE,
        )
        assertEquals(
            -30f,
            hueShiftDegrees(
                0f,
                range = 30f,
                period = 12f,
                staticColors = false,
                continuous = false,
            ),
            TOLERANCE,
        )
        assertEquals(
            30f,
            hueShiftDegrees(
                6f,
                range = 30f,
                period = 12f,
                staticColors = false,
                continuous = false,
            ),
            TOLERANCE,
        )
        assertEquals(
            180f,
            hueShiftDegrees(
                8f,
                range = 360f,
                period = 16f,
                staticColors = false,
                continuous = true,
            ),
            TOLERANCE,
        )
        assertEquals(
            0f,
            hueShiftDegrees(
                16f,
                range = 360f,
                period = 16f,
                staticColors = false,
                continuous = true,
            ),
            TOLERANCE,
        )
    }

    @Test
    fun filterMatrixIsIdentityUntilHueBrightnessOrSaturationChange() {
        assertMatrix(identity(), composedFilterMatrix(0f, brightness = 1f, saturation = 1f))
        assertMatrix(scaledIdentity(2f), composedFilterMatrix(0f, brightness = 2f, saturation = 1f))
        val grey = composedFilterMatrix(0f, brightness = 1f, saturation = 0f)
        assertEquals(0.213f, grey[0], TOLERANCE)
        assertEquals(0.715f, grey[1], TOLERANCE)
        assertEquals(0.072f, grey[2], TOLERANCE)
    }

    @Test
    fun pulseGlowScaleClampsAroundTheReferenceCard() {
        assertEquals(1f, pulseGlowScale(350f, referencePx = 350f), TOLERANCE)
        assertEquals(1f, pulseGlowScale(140f, referencePx = 140f), TOLERANCE)
        assertEquals(0.35f, pulseGlowScale(100f, referencePx = 350f), TOLERANCE)
        assertEquals(4f, pulseGlowScale(2_000f, referencePx = 350f), TOLERANCE)
        assertEquals(0.571f, pulseGlowScale(200f, referencePx = 350f), TOLERANCE)
    }

    @Test
    fun fractionalTurnLoopsTheRotateDuration() {
        assertEquals(0f, fractionalTurn(0f, 1.96f), TOLERANCE)
        assertEquals(0.5f, fractionalTurn(0.98f, 1.96f), TOLERANCE)
        assertEquals(0f, fractionalTurn(1.96f, 1.96f), TOLERANCE)
        assertEquals(0.25f, fractionalTurn(2.45f, 1.96f), TOLERANCE)
    }

    @Test
    fun outsideDarkCornerStartsAtTheSpecAlpha() {
        val params = pulseParams(BeamSize.PulseOutside, BeamTheme.Dark, duration = 2.3f)
        assertEquals(0.54f, pulseCornerAlpha(params, BeamCorner.Tl, timeSeconds = 0f), TOLERANCE)
        assertEquals(
            1f,
            pulseCornerAlpha(params, BeamCorner.Tl, timeSeconds = params.bs / 2f),
            TOLERANCE,
        )
        assertEquals(1f - params.gh, pulseBreathe(params, timeSeconds = 0f), TOLERANCE)
    }

    @Test
    fun playbackFadesInAndDoesNotJumpAcrossAPause() {
        val clock = BeamPlaybackClock()
        clock.onFrame(nanos(0.0), active = true)
        assertEquals(0f, clock.seconds, TOLERANCE)
        assertEquals(0f, clock.opacity, TOLERANCE)

        clock.onFrame(nanos(0.3), active = true)
        assertEquals(0.3f, clock.seconds, TOLERANCE)
        assertEquals(0.5f, clock.opacity, TOLERANCE)

        clock.onFrame(nanos(0.9), active = true)
        assertEquals(1f, clock.opacity, TOLERANCE)
        val held = clock.seconds

        clock.onFrame(nanos(1.15), active = false)
        assertTrue(clock.opacity < 1f)
        assertTrue(clock.seconds > held)

        repeat(20) { step -> clock.onFrame(nanos(1.15 + (step + 1) * 0.05), active = false) }
        assertEquals(0f, clock.opacity, TOLERANCE)
        val frozen = clock.seconds
        clock.onFrame(nanos(20.0), active = false)
        assertEquals(frozen, clock.seconds, TOLERANCE)

        clock.onFrame(nanos(20.1), active = true)
        assertEquals(frozen + 0.1f, clock.seconds, 0.02f)
    }

    private fun assertMatrix(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index], actual[index], TOLERANCE)
        }
    }

    private fun identity(): FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun scaledIdentity(scale: Float): FloatArray =
        floatArrayOf(scale, 0f, 0f, 0f, scale, 0f, 0f, 0f, scale)

    private fun nanos(seconds: Double): Long = (seconds * 1_000_000_000.0).toLong()

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
