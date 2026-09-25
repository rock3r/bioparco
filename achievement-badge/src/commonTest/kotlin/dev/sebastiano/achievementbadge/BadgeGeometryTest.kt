package dev.sebastiano.achievementbadge

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BadgeGeometryTest {
    @Test
    fun starTipsSitOnTheRadiusAndValleysOnTheNinetyDegreeRadius() {
        val star = starVertices(offsetDegrees = -90f, radius = 100f)
        assertEquals(12, star.size)
        star.forEachIndexed { index, point ->
            val expected = if (index % 2 == 0) 100f else 100f * (sqrt(3f) - 1f)
            assertEquals(expected, (point - BadgeArt.CENTER).getDistance(), TOLERANCE)
        }
        assertEquals(BadgeArt.CENTER.y - 100f, star[0].y, TOLERANCE)
    }

    @Test
    fun starTipsAreRightAngles() {
        val star = starVertices(offsetDegrees = 0f, radius = 300f)
        for (tip in star.indices step 2) {
            val angle = cornerDegrees(star[(tip + 11) % 12], star[tip], star[(tip + 1) % 12])
            assertEquals(90f, angle, 0.01f)
        }
    }

    @Test
    fun insetPolygonMovesEveryEdgeInwardByTheInset() {
        val star = starVertices(offsetDegrees = -90f, radius = 300f)
        val inset = insetPolygon(star, 25f)
        for (i in star.indices) {
            val a = star[i]
            val b = star[(i + 1) % star.size]
            val distance = distanceToLine(inset[i], a, b)
            assertEquals(25f, distance, 0.01f)
            // Parallel: both inset ends are the same distance from the original edge.
            assertEquals(25f, distanceToLine(inset[(i + 1) % star.size], a, b), 0.01f)
        }
    }

    @Test
    fun roundedCornerIsTangentToBothEdges() {
        val ops =
            roundedPolygon(listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f))) { 10f }
        val arcs = ops.filterIsInstance<PathOp.ArcTo>()
        assertEquals(3, arcs.size)
        // The right-angle corner at (100, 0): the circle centre is 10 in from both edges.
        val corner = arcs[0]
        assertEquals(Offset(90f, 10f).x, corner.center.x, TOLERANCE)
        assertEquals(Offset(90f, 10f).y, corner.center.y, TOLERANCE)
        assertEquals(90f, abs(corner.sweepDegrees), TOLERANCE)
        assertIs<PathOp.Close>(ops.last())
    }

    @Test
    fun neonTopRunEndsWhereTheSourceArtEndsIt() {
        val top = BadgeArt.neonTopRun
        val first = (top.first() as PathOp.MoveTo).point
        val last = (top.last() as PathOp.LineTo).point
        assertEquals(473.5f - 159f, first.y, TOLERANCE)
        assertEquals(473.5f - 159f, last.y, TOLERANCE)
        // Symmetric about the star centre.
        assertEquals(BadgeArt.CENTER.x, (first.x + last.x) / 2f, 0.01f)
    }

    @Test
    fun fanEdgePassesThroughItsMeasuredPoints() {
        val edge = fanEdge(x480 = 350f, x405 = 322f, x345 = 295f)
        assertEquals(350f, edge.xAt(480f), TOLERANCE)
        assertEquals(322f, edge.xAt(405f), TOLERANCE)
        assertEquals(295f, edge.xAt(345f), TOLERANCE)
    }

    private fun cornerDegrees(a: Offset, v: Offset, b: Offset): Float {
        val ua = (a - v) / (a - v).getDistance()
        val ub = (b - v) / (b - v).getDistance()
        return acos(ua.x * ub.x + ua.y * ub.y) * 180f / kotlin.math.PI.toFloat()
    }

    private fun distanceToLine(p: Offset, a: Offset, b: Offset): Float {
        val d = b - a
        return abs(d.x * (a.y - p.y) - d.y * (a.x - p.x)) / d.getDistance()
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
