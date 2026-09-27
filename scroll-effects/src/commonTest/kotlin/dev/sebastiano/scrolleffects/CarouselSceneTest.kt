package dev.sebastiano.scrolleffects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarouselSceneTest {
    private val stage =
        CarouselStage(width = 900f, height = 560f, cardWidth = 272f, cardHeight = 344f)
    private val flatLeft = stage.centerX - stage.cardWidth / 2f
    private val flatTop = stage.centerY - stage.cardHeight / 2f

    @Test
    fun `the focused card is drawn last so it lands on top`() {
        for (effect in CarouselEffect.entries) {
            val layers = CarouselScene(6).layout(effect, stage, position = 0f, time = 0f)
            assertEquals(0, layers.last().card, "$effect")
            assertTrue(layers.zipWithNext().all { (a, b) -> abs(a.offset) >= abs(b.offset) })
        }
    }

    @Test
    fun `flat effects leave the resting centre card exactly in its slot`() {
        val flat = listOf(CarouselEffect.Stretch, CarouselEffect.Shatter, CarouselEffect.Thanos)
        for (effect in flat + CarouselEffect.Glitch) {
            val centre = CarouselScene(6).layout(effect, stage, 0f, time = 1.3f).last()
            val mesh = assertNotNull(centre.mesh, "$effect")
            for (vertex in 0 until mesh.vertexCount) {
                val u = mesh.uvs[vertex * 2]
                val v = mesh.uvs[vertex * 2 + 1]
                assertEquals(flatLeft + u * stage.cardWidth, mesh.positions[vertex * 2], 0.01f)
                assertEquals(flatTop + v * stage.cardHeight, mesh.positions[vertex * 2 + 1], 0.01f)
            }
            mesh.alphas?.forEach { assertEquals(1f, it, "$effect") }
            assertNull(centre.rim)
            assertEquals(0f, centre.rgbSplit)
            assertTrue(centre.bars.isEmpty())
        }
    }

    @Test
    fun `Stretch smears a resting neighbour out to the stage edge`() {
        val right =
            CarouselScene(6).layout(CarouselEffect.Stretch, stage, 0f, 0f).first { it.card == 1 }
        val mesh = assertNotNull(right.mesh)
        val furthest = (0 until mesh.vertexCount).maxOf { mesh.positions[it * 2] }
        assertTrue(furthest > stage.width, "smear reaches past the right edge, got $furthest")
        assertNotNull(right.rim)
    }

    @Test
    fun `Bulge shrinks the neighbours and Drum grows them`() {
        fun neighbourHeight(effect: CarouselEffect): Float {
            val layer = CarouselScene(6).layout(effect, stage, 0f, 0f).first { it.card == 1 }
            val mesh = assertNotNull(layer.mesh)
            val ys = (0 until mesh.vertexCount).map { mesh.positions[it * 2 + 1] }
            return ys.max() - ys.min()
        }
        assertTrue(neighbourHeight(CarouselEffect.Bulge) < stage.cardHeight)
        assertTrue(neighbourHeight(CarouselEffect.Drum) > stage.cardHeight)
    }

    @Test
    fun `Thanos turns a resting neighbour partly to dust`() {
        val left =
            CarouselScene(6).layout(CarouselEffect.Thanos, stage, 0f, 0f).first { it.card == 5 }
        val alphas = assertNotNull(assertNotNull(left.mesh).alphas)
        assertTrue(alphas.any { it < 1f }, "some grains fade")
        assertTrue(alphas.any { it == 1f }, "the inner edge is still whole")
    }

    @Test
    fun `Glitch shrinks and damages the neighbours`() {
        val right =
            CarouselScene(6).layout(CarouselEffect.Glitch, stage, 0f, 0.4f).first { it.card == 1 }
        val mesh = assertNotNull(right.mesh)
        val ys = (0 until mesh.vertexCount).map { mesh.positions[it * 2 + 1] }
        assertTrue(ys.max() - ys.min() < stage.cardHeight)
        assertTrue(right.rgbSplit > 0f)
    }
}
