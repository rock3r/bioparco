package dev.sebastiano.scrolleffects

import kotlin.math.abs

/**
 * Lays out every visible card for one frame of one effect. It keeps each card's meshes between
 * frames, so a frame only rewrites vertex positions.
 */
class CarouselScene(val cardCount: Int) {
    private val layers = List(cardCount) { CardLayer(it) }
    private val visible = ArrayList<CardLayer>(cardCount)
    private val stretch by lazy { List(cardCount) { StretchCard() } }
    private val bulge by lazy { List(cardCount) { CylinderCard(convex = true) } }
    private val drum by lazy { List(cardCount) { CylinderCard(convex = false) } }
    private val shatter by lazy { List(cardCount) { ShatterCard(it) } }
    private val thanos by lazy { List(cardCount) { ThanosCard(it) } }
    private val glitch by lazy { List(cardCount) { GlitchCard(it) } }

    /**
     * The cards to draw at carousel [position] and animation [time] (seconds), back to front: the
     * furthest from the centre first, so the focused card lands on top.
     */
    fun layout(
        effect: CarouselEffect,
        stage: CarouselStage,
        position: Float,
        time: Float,
    ): List<CardLayer> {
        visible.clear()
        for (layer in layers) {
            val offset = slotOffset(layer.card, position, cardCount)
            if (abs(offset) > MAX_VISIBLE_OFFSET) continue
            layer.offset = offset
            layer.mesh = null
            layer.rim = null
            layer.rgbSplit = 0f
            layer.bars.clear()
            val card = layer.card
            when (effect) {
                CarouselEffect.Stretch -> stretch[card].update(offset, stage, layer)
                CarouselEffect.Bulge -> bulge[card].update(offset, stage, layer)
                CarouselEffect.Drum -> drum[card].update(offset, stage, layer)
                CarouselEffect.Shatter -> shatter[card].update(offset, stage, time, layer)
                CarouselEffect.Thanos -> thanos[card].update(offset, stage, time, layer)
                CarouselEffect.Glitch -> glitch[card].update(offset, stage, time, layer)
            }
            if (layer.mesh != null) visible += layer
        }
        visible.sortByDescending { abs(it.offset) }
        return visible
    }

    private companion object {
        const val MAX_VISIBLE_OFFSET = 2.6f
    }
}
