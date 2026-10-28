package dev.sebastiano.layoutmorph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HitTestTest {
    private val card = CardPose(x = 100f, y = 100f, width = 40f, height = 80f)

    @Test
    fun `a point inside a card hits it, outside misses`() {
        assertEquals(0, hitTest(listOf(card), 110f, 130f))
        assertNull(hitTest(listOf(card), 125f, 100f))
    }

    @Test
    fun `a rotated card is hit along its own axes`() {
        val turned = card.copy(rotation = 90f)
        // Turned a quarter, the tall card lies on its side.
        assertEquals(0, hitTest(listOf(turned), 135f, 100f))
        assertNull(hitTest(listOf(turned), 100f, 135f))
    }

    @Test
    fun `scale grows the target`() {
        assertEquals(0, hitTest(listOf(card.copy(scale = 1.5f)), 128f, 100f))
    }

    @Test
    fun `the deepest card wins, then the later one`() {
        val under = card
        val over = card.copy(x = 110f)
        assertEquals(1, hitTest(listOf(under, over), 105f, 100f))
        assertEquals(0, hitTest(listOf(under.copy(depth = 2f), over), 105f, 100f))
    }

    @Test
    fun `turned cards narrow, and faded or edge-on cards cannot be picked`() {
        // Turned 60 degrees, the card shows half its width.
        val turned = card.copy(turn = 60f)
        assertEquals(0, hitTest(listOf(turned), 109f, 100f))
        assertNull(hitTest(listOf(turned), 115f, 100f))
        assertNull(hitTest(listOf(card.copy(turn = 89f)), 100f, 100f))
        assertNull(hitTest(listOf(card.copy(alpha = 0.3f)), 100f, 100f))
    }
}
