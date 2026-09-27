package dev.sebastiano.componentanatomy

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class AxonometryTest {
    private val pivot = Offset(60f, 15f)

    @Test
    fun aFlatViewWithNoDepthLeavesPointsWhereTheyAre() {
        val view = Axonometry(yawDegrees = 0f, pitchDegrees = 0f)
        assertNear(Offset(10f, 20f), view.project(Offset(10f, 20f), depth = 0f, pivot = pivot))
    }

    @Test
    fun scaleGrowsAroundThePivot() {
        val view = Axonometry(yawDegrees = 0f, pitchDegrees = 0f, scale = 3f)
        assertNear(pivot, view.project(pivot, depth = 0f, pivot = pivot))
        assertNear(
            Offset(pivot.x + 30f, pivot.y),
            view.project(Offset(pivot.x + 10f, pivot.y), 0f, pivot),
        )
    }

    @Test
    fun depthIsInvisibleHeadOnButSlidesSidewaysOnceTheStackTurns() {
        val headOn = Axonometry(yawDegrees = 0f, pitchDegrees = 0f)
        assertNear(pivot, headOn.project(pivot, depth = 40f, pivot = pivot))

        // A negative yaw turns the stack's front face to the left, so layers nearer the viewer
        // move left on screen. A positive pitch tips the top away, so the face looks up and
        // nearer layers move up.
        val turned = Axonometry(yawDegrees = -30f, pitchDegrees = 20f)
        val near = turned.project(pivot, depth = 40f, pivot = pivot)
        assertTrue(near.x < pivot.x, "near layer should move left, was $near")
        assertTrue(near.y < pivot.y, "near layer should move up, was $near")
    }

    @Test
    fun theDrawingMatrixAgreesWithTheProjection() {
        // Layers draw through the matrix and labels anchor through project(). They must never
        // disagree, or a label points at empty space.
        val views =
            listOf(
                Axonometry(yawDegrees = -32f, pitchDegrees = 24f, scale = 3f),
                Axonometry(
                    yawDegrees = 18f,
                    pitchDegrees = -12f,
                    scale = 2f,
                    cameraDistance = 900f,
                ),
            )
        val points = listOf(Offset(0f, 0f), Offset(120f, 0f), Offset(120f, 30f), Offset(37f, 11f))
        for (view in views) {
            for (depth in listOf(-50f, 0f, 75f)) {
                val matrix = view.matrix(depth = depth, pivot = pivot)
                for (point in points) {
                    assertNear(view.project(point, depth, pivot), matrix.map(point))
                }
            }
        }
    }

    @Test
    fun perspectiveMakesNearerLayersBigger() {
        val view = Axonometry(yawDegrees = 0f, pitchDegrees = 0f, cameraDistance = 1000f)
        val edge = Offset(pivot.x + 50f, pivot.y)
        val farWidth = view.project(edge, depth = -100f, pivot = pivot).x - pivot.x
        val nearWidth = view.project(edge, depth = 100f, pivot = pivot).x - pivot.x
        assertTrue(nearWidth > farWidth, "near $nearWidth should be wider than far $farWidth")
    }

    private fun assertNear(expected: Offset, actual: Offset) {
        assertTrue(
            abs(expected.x - actual.x) < 0.01f && abs(expected.y - actual.y) < 0.01f,
            "expected $expected, was $actual",
        )
    }
}
