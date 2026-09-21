package dev.sebastiano.thinkingorbs

import kotlin.test.Test
import kotlin.test.assertEquals

class OrbEngineTest {
    private data class GoldenCase(
        val design: OrbDesign,
        val dotCount: Int,
        val lineCount: Int,
        val first: List<Double>,
        val last: List<Double>,
        val firstLine: List<Double> = emptyList(),
    )

    @Test
    fun shapingMatchesUpstreamGoldenFrame() {
        val frame = OrbDesign.Shaping.frame(size = OrbSize.Regular, atSeconds = 0.6 / 2.405)

        assertEquals(24, frame.dots.size)
        assertEquals(0, frame.lines.size)
        assertEquals(32.0, frame.dots.first().x, 0.0001)
        assertEquals(9.301059, frame.dots.first().y, 0.0001)
        assertEquals(1.039198, frame.dots.first().radius, 0.0001)
        assertEquals(26.126072, frame.dots.last().x, 0.0001)
        assertEquals(10.078267, frame.dots.last().y, 0.0001)
    }

    @Test
    fun everyDesignProducesVisibleGeometryAtBothTunedSizes() {
        for (design in OrbDesign.entries) {
            for (size in OrbSize.entries) {
                val frame = design.frame(size = size, atSeconds = 1.7)
                assertEquals(true, frame.dots.isNotEmpty(), "$design/$size")
                assertEquals(true, frame.dots.all { it.alpha >= 0.02 }, "$design/$size")
            }
        }
    }

    @Test
    fun regularDesignsMatchUpstreamGoldenFrames() {
        val cases =
            listOf(
                GoldenCase(
                    OrbDesign.Working,
                    516,
                    0,
                    listOf(32.34438, 30.683937, -24.13443, 0.356187, 0.72, 0.200238),
                    listOf(31.65562, 33.316063, 24.13443, 0.356187, 0.72, 0.499762),
                ),
                GoldenCase(
                    OrbDesign.Searching,
                    204,
                    0,
                    listOf(33.490622, 32.435651, -0.998247, 0.3, 0.619527, 0.45),
                    listOf(30.509378, 31.564349, 0.998247, 1.047571, 0.080473, 0.452023),
                ),
                GoldenCase(
                    OrbDesign.Solving,
                    138,
                    0,
                    listOf(32.999536, 35.206761, -0.991773, 0.3, 0.617779, 1.0),
                    listOf(34.395262, 28.752372, 0.988104, 0.951566, 0.083212, 1.0),
                ),
                GoldenCase(
                    OrbDesign.Listening,
                    134,
                    0,
                    listOf(29.764214, 35.472327, -23.59208, 0.3, 0.616191, 1.0),
                    listOf(34.080924, 28.768185, 21.957968, 0.837966, 0.160169, 1.0),
                ),
                GoldenCase(
                    OrbDesign.Connecting,
                    48,
                    81,
                    listOf(23.99695, 34.67833, -0.944099, 0.662966, 0.537422, 1.0),
                    listOf(31.723599, 32.180593, 0.999917, 1.490098, 0.100019, 1.0),
                    listOf(40.743047, 12.360858, 25.929776, 13.006921, 0.42, 0.137696, 0.6),
                ),
                GoldenCase(
                    OrbDesign.Weaving,
                    153,
                    0,
                    listOf(34.742259, 29.322907, -24.016153, 0.31661, 0.78, 0.101374),
                    listOf(28.349991, 32.646624, 24.035842, 0.31661, 0.78, 0.318715),
                ),
                GoldenCase(
                    OrbDesign.Composing,
                    566,
                    0,
                    listOf(37.322389, 30.655967, -24.348868, 0.31661, 0.78, 0.102693),
                    listOf(38.863508, 29.053978, 23.816272, 0.31661, 0.78, 0.31496),
                ),
                GoldenCase(
                    OrbDesign.Breathing,
                    484,
                    0,
                    listOf(41.791591, 53.440594, -8.838984, 0.467922, 0.557908, 0.593762),
                    listOf(55.395251, 28.636271, 8.863436, 0.638987, 0.401877, 0.806532),
                ),
                GoldenCase(
                    OrbDesign.Shaping,
                    24,
                    0,
                    listOf(32.0, 9.301059, 0.0, 1.039198, 0.1, 1.0),
                    listOf(26.126072, 10.078267, 0.0, 1.039198, 0.1, 1.0),
                ),
            )

        cases.forEach { golden ->
            val frame = OrbPresets.resolve(golden.design, OrbSize.Regular).frame(64.0, 0.6)
            assertEquals(golden.dotCount, frame.dots.size, golden.design.name)
            assertEquals(golden.lineCount, frame.lines.size, golden.design.name)
            assertValues(golden.first, frame.dots.first().values(), golden.design.name)
            assertValues(golden.last, frame.dots.last().values(), golden.design.name)
            if (golden.firstLine.isNotEmpty()) {
                assertValues(golden.firstLine, frame.lines.first().values(), golden.design.name)
            }
        }
    }

    private fun OrbDot.values() = listOf(x, y, z, radius, white, alpha)

    private fun OrbLine.values() = listOf(x1, y1, x2, y2, white, alpha, width)

    private fun assertValues(expected: List<Double>, actual: List<Double>, message: String) {
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            assertEquals(want, got, 0.0001, "$message value $index")
        }
    }
}
