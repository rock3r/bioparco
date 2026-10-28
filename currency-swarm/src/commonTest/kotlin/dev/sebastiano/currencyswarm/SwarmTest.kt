package dev.sebastiano.currencyswarm

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwarmTest {
    private val count = 240
    private val frame = 1f / 60f
    private val restTolerance = Swarm.WOBBLE * sqrt(2f) + 1e-4f

    @Test
    fun morphEndsWithEveryDotOnItsOwnTarget() {
        val swarm = Swarm(count, seed = 1)
        swarm.place(points(Random(1), 0f))
        val targets = points(Random(2), 0.3f)
        swarm.morphTo(targets, now = 0f)
        assertFalse(swarm.isSettled(0.5f))
        val end = Swarm.LONGEST_FLIGHT + 0.01f
        run(swarm, from = 0f, until = end)
        assertTrue(swarm.isSettled(end))

        val claimed = (0 until count).map { swarm.toX[it] to swarm.toY[it] }.sortedBy { it.first }
        val wanted =
            (0 until count).map { targets[it * 2] to targets[it * 2 + 1] }.sortedBy { it.first }
        assertEquals(wanted, claimed)
        for (i in 0 until count) {
            val off = hypot(swarm.x[i] - swarm.toX[i], swarm.y[i] - swarm.toY[i])
            assertTrue(off <= restTolerance, "dot $i is $off from its target")
        }
    }

    @Test
    fun aNewTargetNeverMakesADotJump() {
        val swarm = Swarm(count, seed = 2)
        swarm.place(points(Random(3), 0f))
        swarm.morphTo(points(Random(4), 0.4f), now = 0f)
        // Mid-flight, when dots are furthest from both shapes.
        val now = run(swarm, from = 0f, until = 0.55f)
        val beforeX = swarm.x.copyOf()
        val beforeY = swarm.y.copyOf()
        swarm.morphTo(points(Random(5), -0.4f), now = now)
        swarm.step(now)
        for (i in 0 until count) {
            assertEquals(beforeX[i], swarm.x[i], 1e-4f, "dot $i jumped in x")
            assertEquals(beforeY[i], swarm.y[i], 1e-4f, "dot $i jumped in y")
        }
    }

    @Test
    fun thePointerPushesNearbyDotsAwayAndTheyComeBack() {
        val swarm = Swarm(count, seed = 3)
        val rest = points(Random(6), 0f)
        swarm.place(rest)
        var now = run(swarm, from = 0f, until = 0.2f)
        val pointer = Offset(0f, 0f)
        val near =
            (0 until count).filter {
                hypot(rest[it * 2], rest[it * 2 + 1]) < Swarm.REPEL_RADIUS / 2f
            }
        assertTrue(near.isNotEmpty())

        now = run(swarm, from = now, until = now + 0.4f, pointer = pointer)
        for (i in near) {
            val restDistance = hypot(rest[i * 2], rest[i * 2 + 1])
            val pushed = hypot(swarm.x[i], swarm.y[i])
            assertTrue(pushed > restDistance + Swarm.WOBBLE * 2f, "dot $i stayed at $pushed")
        }

        now = run(swarm, from = now, until = now + 2.5f)
        for (i in 0 until count) {
            val off = hypot(swarm.x[i] - rest[i * 2], swarm.y[i] - rest[i * 2 + 1])
            assertTrue(off <= restTolerance + 1e-3f, "dot $i is still $off away")
        }
    }

    @Test
    fun dotsFarFromThePointerDoNotMove() {
        val swarm = Swarm(count, seed = 4)
        val rest = points(Random(7), 0f)
        swarm.place(rest)
        run(swarm, from = 0f, until = 1f, pointer = Offset(5f, 5f))
        for (i in 0 until count) {
            val off = hypot(swarm.x[i] - rest[i * 2], swarm.y[i] - rest[i * 2 + 1])
            assertTrue(off <= restTolerance, "dot $i drifted $off")
        }
    }

    @Test
    fun aLongPauseDoesNotFlingDots() {
        val swarm = Swarm(count, seed = 5)
        val rest = points(Random(8), 0f)
        swarm.place(rest)
        swarm.step(0f, Offset(0f, 0f))
        // A frame after a two second stall, with the pointer still on the swarm.
        swarm.step(2f, Offset(0f, 0f))
        for (i in 0 until count) {
            val off = hypot(swarm.x[i] - rest[i * 2], swarm.y[i] - rest[i * 2 + 1])
            assertTrue(off < Swarm.REPEL_RADIUS * 2f, "dot $i flew $off")
        }
    }

    @Test
    fun tiersSplitTheDotsIntoDimMidAndBright() {
        val swarm = Swarm(1_000, seed = 6)
        val sizes = swarm.tierSizes
        assertEquals(Swarm.TIERS, sizes.size)
        assertEquals(1_000, sizes.sum())
        assertTrue(sizes.all { it > 100 }, "tiers ${sizes.toList()}")
    }

    /** Steps [swarm] a frame at a time from [from] to [until] and returns the last time. */
    private fun run(
        swarm: Swarm,
        from: Float,
        until: Float,
        pointer: Offset = Offset.Unspecified,
    ): Float {
        var now = from
        while (now < until) {
            now += frame
            swarm.step(now, pointer)
        }
        return now
    }

    /** [count] points in a 0.6-wide square centred on ([cx], 0). */
    private fun points(random: Random, cx: Float) =
        FloatArray(count * 2) {
            val centre = if (it % 2 == 0) cx else 0f
            centre + (random.nextFloat() - 0.5f) * 0.6f
        }
}
