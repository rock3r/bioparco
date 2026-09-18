package dev.sebastiano.processingfield

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldFrameTest {
    @Test
    fun gridFillsWidthWithWholeCells() {
        val frame = stillFrame(width = 390.0, height = 220.0, pitch = 14.0)
        assertEquals(28, frame.columns)
        assertEquals(390.0 / 28.0, frame.step, absoluteTolerance = 1e-9)
        assertEquals(16, frame.rows)
        assertEquals(frame.step / 2.0, frame.xOfColumn(0), absoluteTolerance = 1e-9)
        assertEquals(
            390.0 - frame.step / 2.0,
            frame.xOfColumn(frame.columns - 1),
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun stillFrameParksMassInTheMiddle() {
        val frame = stillFrame(width = 320.0, height = 200.0)
        val nx = frame.normalisedX(160.0)
        val ny = frame.normalisedY(100.0)
        assertEquals(0.0, nx, absoluteTolerance = 1e-9)
        assertEquals(0.0, ny, absoluteTolerance = 1e-9)
        val centreLevel = frame.level(nx, ny)
        val cornerLevel = frame.level(frame.normalisedX(0.0), frame.normalisedY(0.0))
        assertTrue(centreLevel > 0.5, "centre should be inside the mass")
        assertTrue(cornerLevel < centreLevel, "corner should be quieter than the heart")
    }

    @Test
    fun stillFrameMatchesTunedSwellAndSmoothstep() {
        val frame = stillFrame(width = 320.0, height = 200.0, reach = 0.40, softness = 0.34)
        val level = frame.level(0.0, 0.0)
        assertEquals(0.82, level, absoluteTolerance = 1e-6)
        assertEquals(0.30 + 0.55 * 0.82, frame.inkAtLevel(level), absoluteTolerance = 1e-6)
    }

    @Test
    fun tangentAtCentreLiesFlat() {
        val frame = stillFrame()
        val (x, y) = frame.tangent(0.0, 0.0)
        assertEquals(1.0, x, absoluteTolerance = 1e-9)
        assertEquals(0.0, y, absoluteTolerance = 1e-9)
    }

    @Test
    fun movingFrameDriftsOffCentre() {
        val still = stillFrame(width = 400.0, height = 300.0)
        val moving = movingFrame(time = 2.5, width = 400.0, height = 300.0)
        val stillCentre = still.level(still.normalisedX(200.0), still.normalisedY(150.0))
        val movingCentre = moving.level(moving.normalisedX(200.0), moving.normalisedY(150.0))
        assertTrue(abs(stillCentre - movingCentre) > 1e-6)
    }

    @Test
    fun zeroSpeedsFreezeTheMovingOrigin() {
        val frozen = movingFrame(time = 40.0, driftSpeed = 0.0, foldSpeed = 0.0)
        val origin = movingFrame(time = 0.0)
        val x = frozen.xOfColumn(3)
        val y = frozen.yOfRow(2)
        assertEquals(
            origin.level(origin.normalisedX(x), origin.normalisedY(y)),
            frozen.level(frozen.normalisedX(x), frozen.normalisedY(y)),
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun goldenLevelAtKnownSample() {
        val frame =
            FieldFrame(
                width = 390.0,
                height = 220.0,
                time = 12.0,
                pitch = 14.0,
                reach = 0.22,
                softness = 0.34,
                minRadiusRatio = 0.085,
                maxRadiusRatio = 0.19,
                minInk = 0.30,
                maxInk = 0.85,
                driftSpeed = 1.0,
                foldSpeed = 1.0,
            )
        val x = frame.xOfColumn(10)
        val y = frame.yOfRow(7)
        val level = frame.level(frame.normalisedX(x), frame.normalisedY(y))
        val (tx, ty) = frame.tangent(frame.normalisedX(x), frame.normalisedY(y))
        assertEquals(0.501584306616, level, absoluteTolerance = 1e-9)
        assertEquals(1.917495619854, frame.radiusAtLevel(level), absoluteTolerance = 1e-9)
        assertEquals(0.575871368639, frame.inkAtLevel(level), absoluteTolerance = 1e-9)
        assertEquals(-0.398708362958, tx, absoluteTolerance = 1e-9)
        assertEquals(-0.917077772769, ty, absoluteTolerance = 1e-9)
    }

    private fun movingFrame(
        time: Double,
        width: Double = 320.0,
        height: Double = 200.0,
        driftSpeed: Double = 1.0,
        foldSpeed: Double = 1.0,
    ) =
        FieldFrame(
            width = width,
            height = height,
            time = time,
            pitch = 14.0,
            reach = 0.40,
            softness = 0.34,
            minRadiusRatio = 0.085,
            maxRadiusRatio = 0.19,
            minInk = 0.30,
            maxInk = 0.85,
            driftSpeed = driftSpeed,
            foldSpeed = foldSpeed,
        )

    private fun stillFrame(
        width: Double = 320.0,
        height: Double = 200.0,
        pitch: Double = 14.0,
        reach: Double = 0.40,
        softness: Double = 0.34,
    ) =
        FieldFrame(
            width = width,
            height = height,
            time = null,
            pitch = pitch,
            reach = reach,
            softness = softness,
            minRadiusRatio = 0.085,
            maxRadiusRatio = 0.19,
            minInk = 0.30,
            maxInk = 0.85,
            driftSpeed = 1.0,
            foldSpeed = 1.0,
        )
}
