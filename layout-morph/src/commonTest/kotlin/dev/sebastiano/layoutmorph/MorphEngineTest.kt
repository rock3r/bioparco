package dev.sebastiano.layoutmorph

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MorphEngineTest {
    private val field = Field(900f, 420f)
    private val pieces = ShopCollection.pieces
    private val count = pieces.size

    private fun engine(initial: Arrangement = Arrangement.Grid) =
        MorphEngine(pieces, field, initial).also { it.step(0L) }

    /** Steps [engine] at 60 Hz from [from] for [seconds], returning the time reached. */
    private fun run(engine: MorphEngine, from: Long, seconds: Double, each: (Long) -> Unit = {}) =
        run {
            var now = from
            repeat((seconds * 60).toInt()) {
                now += FRAME
                engine.step(now)
                each(now)
            }
            now
        }

    @Test
    fun `at rest every card sits on its arrangement`() {
        val engine = engine()
        val grid = Arrangements.grid(count, field)
        for (i in 0 until count) assertEquals(grid[i], engine.pose(i))
        assertFalse(engine.step(FRAME))
    }

    @Test
    fun `cards set off one after another, left to right, and all land`() {
        val engine = engine()
        val grid = Arrangements.grid(count, field)
        engine.select(Arrangement.List, 0L)
        engine.step(FRAME * 2)
        // Card 0 sits leftmost in the grid's top row and leaves first; card 7 sits rightmost.
        assertTrue(engine.pose(0) != grid[0], "the first card has set off")
        assertEquals(grid[7], engine.pose(7), "the last card in the row is still waiting")
        val end = run(engine, FRAME * 2, 3.0)
        assertFalse(engine.step(end + FRAME), "everything settles within three seconds")
        val list = Arrangements.list(count, field)
        for (i in 0 until count) assertClose(list[i], engine.pose(i))
    }

    @Test
    fun `a change of mind mid-flight never makes a card jump`() {
        val engine = engine()
        engine.select(Arrangement.Stack, 0L)
        var previous = engine.poses
        var largest = 0f
        val track = { _: Long ->
            val now = engine.poses
            for (i in 0 until count) {
                val step = hypot(now[i].x - previous[i].x, now[i].y - previous[i].y)
                largest = maxOf(largest, step)
            }
            previous = now
        }
        var time = run(engine, 0L, 0.3, track)
        engine.select(Arrangement.Masonry, time)
        time = run(engine, time, 0.2, track)
        engine.select(Arrangement.Helix, time)
        run(engine, time, 2.5, track)
        // Far below what a teleport across the field would show in one frame.
        assertTrue(largest < 60f, "largest single-frame move was $largest dp")
    }

    @Test
    fun `cards landing on a spinning helix never teleport in sight`() {
        val engine = engine()
        engine.select(Arrangement.Helix, 0L)
        var previous = engine.poses
        var largest = 0f
        // Spin hard while the cards fly in, so the spiral wraps cards round under them.
        run(engine, 0L, 3.0) {
            engine.drag(6f)
            val now = engine.poses
            for (i in 0 until count) {
                if (!inSight(previous[i]) && !inSight(now[i])) continue
                largest = maxOf(largest, hypot(now[i].x - previous[i].x, now[i].y - previous[i].y))
            }
            previous = now
        }
        assertTrue(largest < 60f, "a card in sight moved $largest dp in one frame")
    }

    @Test
    fun `a fast-spinning helix keeps the cards in front solid`() {
        val engine = engine()
        engine.select(Arrangement.Helix, 0L)
        var time = run(engine, 0L, 2.0)
        repeat(14) {
            engine.drag(26f)
            time += 18_000_000L
            engine.step(time)
        }
        engine.release(1_400f)
        var faintest = 1f
        run(engine, time, 3.0) {
            for (pose in engine.poses) {
                val inFront =
                    abs(pose.turn) < 30f &&
                        pose.y > field.height * 0.3f &&
                        pose.y < field.height * 0.7f
                if (inFront) faintest = minOf(faintest, pose.alpha)
            }
        }
        assertTrue(faintest > 0.95f, "a card in front faded to $faintest")
    }

    /** Showing at all, and over the field. The helix wraps cards round at its faded-out ends. */
    private fun inSight(pose: CardPose) =
        pose.alpha > 0.02f &&
            pose.y + pose.height / 2 > 0f &&
            pose.y - pose.height / 2 < field.height

    @Test
    fun `the turn takes the short way round`() {
        val engine = engine(Arrangement.Helix)
        val time = run(engine, 0L, 0.5)
        engine.select(Arrangement.Grid, time)
        var widest = 0f
        run(engine, time, 3.0) { widest = maxOf(widest, engine.poses.maxOf { abs(it.turn) }) }
        // Never more than half a turn (plus a spring's small overshoot) from facing the front.
        assertTrue(widest <= 185f, "a card turned $widest° on its way back")
    }

    @Test
    fun `a carousel drag follows the pointer and settles on the nearest card`() {
        val engine = engine(Arrangement.Carousel)
        val pitch = Arrangements.carouselPitch(field)
        engine.drag(-pitch * 2.3f)
        assertEquals(2.3f, engine.carouselPosition, 0.001f)
        engine.release(0f)
        run(engine, 0L, 2.0)
        assertEquals(2, engine.focused)
        assertEquals(2f, engine.carouselPosition, 0.01f)
    }

    @Test
    fun `a carousel flick carries on, and stops at the ends`() {
        val engine = engine(Arrangement.Carousel)
        engine.drag(-10f)
        engine.release(-3_000f)
        run(engine, 0L, 2.0)
        assertTrue(engine.focused > 2, "a fast flick travels several cards")
        engine.stepFocus(100)
        assertEquals(count - 1, engine.focused)
        engine.stepFocus(-1)
        assertEquals(count - 2, engine.focused)
    }

    @Test
    fun `a thrown helix keeps turning, then slows to its idle drift`() {
        val engine = engine(Arrangement.Helix)
        engine.drag(20f)
        engine.release(2_000f)
        val thrown = engine.spinVelocity
        val time = run(engine, 0L, 0.5)
        assertTrue(engine.spinVelocity > 0f && engine.spinVelocity < thrown)
        run(engine, time, 8.0)
        assertTrue(abs(engine.spinVelocity) < 20f, "drifting at ${engine.spinVelocity}°/s")
        assertTrue(engine.step(FRAME * 1_000), "the helix never stops drifting")
    }

    @Test
    fun `hovering lifts one card, and the hover clears`() {
        val engine = engine(Arrangement.Stack)
        // The fan's last card lies on top of the others, so its centre is all its own.
        val top = count - 1
        val rest = engine.pose(top)
        engine.point(rest.x, rest.y)
        assertEquals(top, engine.hovered)
        val time = run(engine, 0L, 1.0)
        assertTrue(engine.pose(top).y < rest.y - 10f)
        engine.point(null, null)
        assertNull(engine.hovered)
        run(engine, time, 1.0)
        assertClose(rest, engine.pose(top))
    }

    @Test
    fun `under a still pointer, the hovered card follows the carousel`() {
        val engine = engine(Arrangement.Carousel)
        val centre = engine.pose(0)
        engine.point(centre.x, centre.y)
        assertEquals(0, engine.hovered)
        engine.stepFocus(2)
        run(engine, 0L, 2.0)
        assertEquals(2, engine.hovered, "the card now under the pointer")
        engine.point(null, null)
        assertNull(engine.hovered)
    }

    @Test
    fun `under a still pointer, the hovered card follows the turning helix`() {
        val engine = engine(Arrangement.Helix)
        val front = engine.pose(count / 2)
        engine.point(front.x, front.y)
        assertEquals(count / 2, engine.hovered)
        engine.drag(400f)
        engine.release(0f)
        engine.step(FRAME)
        assertEquals(hitTest(engine.poses, front.x, front.y), engine.hovered)
        assertTrue(engine.hovered != count / 2, "the front card has turned away")
    }

    private fun assertClose(expected: CardPose, actual: CardPose) {
        val near = { a: Float, b: Float -> abs(a - b) < 0.1f }
        assertTrue(
            near(expected.x, actual.x) &&
                near(expected.y, actual.y) &&
                near(expected.width, actual.width) &&
                near(expected.height, actual.height) &&
                near(expected.rotation, actual.rotation) &&
                near(expected.alpha, actual.alpha),
            "expected $expected, was $actual",
        )
    }

    private companion object {
        const val FRAME = 16_666_667L
    }
}
