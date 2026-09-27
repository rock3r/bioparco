package dev.sebastiano.scrolleffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShardsTest {
    @Test
    fun `the shards tile the whole card without overlapping`() {
        val shards = shatter(cols = 6, rows = 8, seed = 7)
        assertEquals(48, shards.size)
        assertEquals(1f, shards.sumOf { it.area.toDouble() }.toFloat(), absoluteTolerance = 1e-4f)
        assertTrue(shards.all { it.area > 0f }, "every shard has a positive area")
    }

    @Test
    fun `every shard stays on the card and holds its own centroid`() {
        for (shard in shatter(cols = 6, rows = 8, seed = 42)) {
            assertTrue(shard.corners >= 3)
            for (i in 0 until shard.corners) {
                val u = shard.polygon[i * 2]
                val v = shard.polygon[i * 2 + 1]
                assertTrue(u in -1e-5f..1.00001f && v in -1e-5f..1.00001f, "corner ($u, $v)")
            }
            assertTrue(shard.centroidU in 0f..1f && shard.centroidV in 0f..1f)
        }
    }

    @Test
    fun `the same seed always breaks the card the same way`() {
        val first = shatter(cols = 4, rows = 5, seed = 3).map { it.polygon.toList() }
        val second = shatter(cols = 4, rows = 5, seed = 3).map { it.polygon.toList() }
        assertEquals(first, second)
    }
}
