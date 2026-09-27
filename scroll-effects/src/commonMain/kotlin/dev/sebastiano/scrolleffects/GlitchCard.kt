package dev.sebastiano.scrolleffects

import kotlin.math.abs
import kotlin.math.floor

/**
 * Glitch: side cards shrink back and break into horizontal bands that jump sideways, with the
 * colour channels pulled apart and flat scan bars over them. The damage re-rolls several times a
 * second, so the neighbours never sit still; the centre card is clean.
 */
internal class GlitchCard(private val card: Int) {
    private val mesh = Mesh(BANDS * VERTICES_PER_QUAD)
    private val edges = FloatArray(BANDS + 1)

    fun update(offset: Float, stage: CarouselStage, time: Float, layer: CardLayer) {
        val distance = abs(offset)
        val strength = smoothstep(START, END, distance)
        val scale = 1f - SHRINK * distance.coerceAtMost(1f)
        val width = stage.cardWidth * scale
        val height = stage.cardHeight * scale
        val left = stage.centerX + offset * stage.cardWidth * PITCH - width / 2f
        val top = stage.centerY - height / 2f
        val tick = floor(time * TICKS_PER_SECOND).toInt()
        val seed = card * SEED_STRIDE + tick

        cutBands(seed)
        for (band in 0 until BANDS) {
            val v0 = edges[band]
            val v1 = edges[band + 1]
            val jumps = hash01(seed, band, 1) < JUMP_CHANCE
            val shift =
                if (jumps) (hash01(seed, band, 2) - 0.5f) * 2f * MAX_SHIFT * width * strength
                else 0f
            quadCorners(band, 0f, v0, 1f, v1, mesh.uvs)
            quadCorners(
                band,
                left + shift,
                top + v0 * height,
                left + width + shift,
                top + v1 * height,
                mesh.positions,
            )
        }
        layer.mesh = mesh
        layer.rgbSplit = strength * RGB_SPLIT * stage.unit
        layer.glitchSeed = (seed % SEED_WRAP).toFloat()
        layer.bars.clear()
        val barCount = (strength * MAX_BARS + hash01(seed, 99) * strength * 2f).toInt()
        repeat(barCount) { i ->
            val barWidth = width * (MIN_BAR + hash01(seed, i, 21) * (MAX_BAR - MIN_BAR))
            val barLeft = left + (hash01(seed, i, 22) * 1.3f - 0.3f) * (width - barWidth * 0.5f)
            layer.bars +=
                GlitchBar(
                    left = barLeft,
                    top = top + hash01(seed, i, 23) * height,
                    width = barWidth,
                    height = (2f + hash01(seed, i, 24) * 6f) * stage.unit,
                    color = barColors[(hash01(seed, i, 25) * barColors.size).toInt()],
                )
        }
    }

    /** Splits the card into [BANDS] strips of random height. */
    private fun cutBands(seed: Int) {
        var total = 0f
        edges[0] = 0f
        for (band in 1..BANDS) {
            total += MIN_BAND + hash01(seed, band, 3)
            edges[band] = total
        }
        for (band in 1..BANDS) edges[band] /= total
    }

    private companion object {
        const val BANDS = 16
        const val SEED_STRIDE = 7919
        const val SEED_WRAP = 997
        const val PITCH = 1.12f
        const val START = 0.04f
        const val END = 0.85f
        const val SHRINK = 0.22f
        const val TICKS_PER_SECOND = 11f
        const val JUMP_CHANCE = 0.45f
        const val MAX_SHIFT = 0.16f
        const val RGB_SPLIT = 5f
        const val MIN_BAND = 0.15f
        const val MAX_BARS = 5f
        const val MIN_BAR = 0.2f
        const val MAX_BAR = 0.95f
        val barColors = longArrayOf(0xE639FF8CL, 0xE0FF3DDBL, 0xD96B7CFFL, 0xCCFFFFFFL, 0xD9FFD23DL)
    }
}
