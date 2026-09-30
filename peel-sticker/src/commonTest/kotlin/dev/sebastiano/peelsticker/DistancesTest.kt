package dev.sebastiano.peelsticker

import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistancesTest {
    @Test
    fun `every pixel gets the exact distance to its nearest feature`() {
        val random = Random(7)
        for ((width, height, density) in CASES) {
            val features = BooleanArray(width * height) { random.nextFloat() < density }
            features[random.nextInt(features.size)] = true
            val found = distances(width, height, features)
            for (i in found.indices) {
                assertEquals(bruteForce(features, width, i), found[i], 1e-4f, "pixel $i")
            }
        }
    }

    @Test
    fun `with no feature at all every distance is past the grid`() {
        val found = distances(7, 5, BooleanArray(7 * 5))
        assertTrue(found.all { it > 7 + 5 }, found.joinToString())
    }

    private fun bruteForce(features: BooleanArray, width: Int, i: Int): Float {
        var best = Float.POSITIVE_INFINITY
        for (j in features.indices) {
            if (!features[j]) continue
            val dx = (i % width - j % width).toFloat()
            val dy = (i / width - j / width).toFloat()
            best = minOf(best, sqrt(dx * dx + dy * dy))
        }
        return best
    }

    private companion object {
        /** Width, height and the share of pixels that are features. */
        val CASES =
            listOf(
                Triple(1, 1, 1f),
                Triple(9, 1, 0.2f),
                Triple(1, 9, 0.2f),
                Triple(23, 17, 0.01f),
                Triple(23, 17, 0.1f),
                Triple(31, 29, 0.5f),
                Triple(40, 3, 0.05f),
            )
    }
}
