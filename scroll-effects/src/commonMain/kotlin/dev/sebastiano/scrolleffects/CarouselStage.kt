package dev.sebastiano.scrolleffects

/**
 * The stage the carousel draws on, in pixels. The focused card is centred; [cardWidth] and
 * [cardHeight] are the size of one card as the flat, unbent carousel would show it.
 */
data class CarouselStage(
    val width: Float,
    val height: Float,
    val cardWidth: Float,
    val cardHeight: Float,
) {
    val centerX: Float
        get() = width / 2f

    val centerY: Float
        get() = height / 2f

    /** A length that scales with the card: 1 on the 272 dp card the numbers were tuned on. */
    val unit: Float
        get() = cardWidth / TUNED_CARD_WIDTH

    private companion object {
        const val TUNED_CARD_WIDTH = 272f
    }
}

/**
 * What one card needs drawn this frame. The effects fill it in; the renderer reads it. Draw [mesh]
 * with the card art, then [rim] and [bars] on top.
 */
class CardLayer(val card: Int) {
    /** The card's [slotOffset] this frame. */
    var offset: Float = 0f

    var mesh: Mesh? = null

    /** The Stretch effect's iridescent edge, or `null`. */
    var rim: ColorMesh? = null

    /** Glitch: how far apart the red and blue channels sit, in pixels. */
    var rgbSplit: Float = 0f

    /** Glitch: coloured scan bars over the card. */
    val bars: MutableList<GlitchBar> = ArrayList()

    /** Glitch: the seed the texture noise uses this frame. */
    var glitchSeed: Float = 0f
}

/** A horizontal bar of flat colour that the Glitch effect draws across a card. */
data class GlitchBar(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val color: Long,
)
