package dev.sebastiano.layoutmorph

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrangementsTest {
    private val field = Field(900f, 420f)
    private val count = 24

    @Test
    fun `the grid reads row by row, eight across and three down`() {
        val grid = Arrangements.grid(count, field)
        val columns = grid.map { it.x }.distinctBy { (it * 10).toInt() }
        val rows = grid.map { it.y }.distinctBy { (it * 10).toInt() }
        assertEquals(8, columns.size)
        assertEquals(3, rows.size)
        // Card 1 sits right of card 0 and card 8 starts the second row.
        assertTrue(grid[1].x > grid[0].x)
        assertEquals(grid[0].y, grid[1].y, 0.01f)
        assertEquals(grid[0].x, grid[8].x, 0.01f)
        assertTrue(grid[8].y > grid[0].y)
        assertTrue(grid.all { it.caption == 1f && it.row == 0f })
    }

    @Test
    fun `the list reads column by column, so its first column is the grid's first row`() {
        val list = Arrangements.list(count, field)
        for (i in 1 until 8) {
            assertEquals(list[0].x, list[i].x, 0.01f)
            assertTrue(list[i].y > list[i - 1].y)
        }
        assertTrue(list[8].x > list[0].x)
        assertEquals(list[0].y, list[8].y, 0.01f)
        assertTrue(list.all { it.row == 1f && it.width > it.height * 4 })
    }

    @Test
    fun `flat arrangements fit the field without overlapping`() {
        val tall = ShopCollection.pieces.map { it.tall }
        val flat =
            mapOf(
                "grid" to Arrangements.grid(count, field),
                "masonry" to Arrangements.masonry(tall, field),
                "list" to Arrangements.list(count, field),
            )
        for ((name, poses) in flat) {
            for (pose in poses) {
                assertTrue(pose.x - pose.width / 2 >= -0.01f, "$name spills left: $pose")
                assertTrue(pose.x + pose.width / 2 <= field.width + 0.01f, "$name spills right")
                assertTrue(pose.y - pose.height / 2 >= -0.01f, "$name spills over the top")
                assertTrue(pose.y + pose.height / 2 <= field.height + 0.01f, "$name spills below")
            }
            for (a in poses.indices) for (b in a + 1 until poses.size) {
                assertTrue(!overlaps(poses[a], poses[b]), "$name overlaps cards $a and $b")
            }
        }
    }

    @Test
    fun `masonry keeps each piece's proportions in equal columns`() {
        val tall = ShopCollection.pieces.map { it.tall }
        val masonry = Arrangements.masonry(tall, field)
        val width = masonry[0].width
        masonry.forEachIndexed { i, pose ->
            assertEquals(width, pose.width, 0.01f)
            assertEquals(tall[i], pose.height / pose.width, 0.001f)
        }
        assertEquals(8, masonry.map { it.x }.distinctBy { (it * 10).toInt() }.size)
    }

    @Test
    fun `the stack fans from left to right, each card above the one before`() {
        val stack = Arrangements.stack(count, field)
        for (i in 1 until count) {
            assertTrue(stack[i].rotation > stack[i - 1].rotation)
            assertTrue(stack[i].x > stack[i - 1].x)
            assertTrue(stack[i].depth > stack[i - 1].depth)
        }
        assertEquals(-stack.first().rotation, stack.last().rotation, 0.01f)
        assertEquals(field.width / 2, (stack.first().x + stack.last().x) / 2, 0.5f)
    }

    @Test
    fun `a lifted stack card is pulled out along its own tilt`() {
        val rest = Arrangements.stack(count, field)
        val lifted = Arrangements.stack(count, field) { if (it == 3) 1f else 0f }
        val dx = lifted[3].x - rest[3].x
        val dy = lifted[3].y - rest[3].y
        assertTrue(dy < -10f, "the card rises")
        // Card 3 leans left, so it comes out up and to the left.
        assertTrue(dx < 0f)
        assertEquals(rest[4], lifted[4])
    }

    @Test
    fun `the carousel centres the focused card and slides with its position`() {
        val pitch = Arrangements.carouselPitch(field)
        val atFive = Arrangements.carousel(count, field, 5f)
        assertEquals(field.width / 2, atFive[5].x, 0.01f)
        assertEquals(pitch, atFive[6].x - atFive[5].x, 0.01f)
        val between = Arrangements.carousel(count, field, 5.5f)
        assertEquals(field.width / 2 - pitch / 2, between[5].x, 0.01f)
        assertTrue(atFive[5].depth > atFive[4].depth && atFive[5].depth > atFive[6].depth)
    }

    @Test
    fun `the helix faces its front card out at full strength and fades the far side`() {
        val helix = Arrangements.helix(count, field, spin = 0f)
        // The middle card sits at angle 0: in front, face on, centred.
        val front = helix[count / 2]
        assertEquals(field.width / 2, front.x, 0.01f)
        assertEquals(field.height / 2, front.y, 0.01f)
        assertEquals(0f, front.turn, 0.01f)
        assertEquals(1f, front.alpha, 0.01f)
        val far = helix.maxBy { abs(abs(it.turn) - 180f).let { d -> -d } }
        assertTrue(far.alpha < 0.5f)
        assertTrue(front.depth > far.depth)
    }

    @Test
    fun `the helix's ends, where cards wrap round, are faded out`() {
        val helix = Arrangements.helix(count, field, spin = 0f)
        // Card 0 starts the spiral, just above the field.
        assertEquals(0f, helix[0].alpha, 0.001f)
        assertTrue(helix[0].y < 0f)
        // Wrapping round, it reaches the far end, just below the field, still invisible.
        val wrapped = Arrangements.helix(count, field, spin = -0.001f)
        assertEquals(0f, wrapped[0].alpha, 0.001f)
        assertTrue(wrapped[0].y > field.height)
    }

    @Test
    fun `spinning the helix turns every card the same way and screws it along`() {
        val before = Arrangements.helix(count, field, spin = 0f)
        val after = Arrangements.helix(count, field, spin = 10f)
        val i = count / 2
        assertEquals(10f, after[i].turn - before[i].turn, 0.01f)
        assertTrue(after[i].y != before[i].y)
    }

    private fun overlaps(a: CardPose, b: CardPose): Boolean =
        abs(a.x - b.x) < (a.width + b.width) / 2 - 0.01f &&
            abs(a.y - b.y) < (a.height + b.height) / 2 - 0.01f
}
