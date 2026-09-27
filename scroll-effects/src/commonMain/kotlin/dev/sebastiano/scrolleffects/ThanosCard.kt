package dev.sebastiano.scrolleffects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Thanos: a card turns to dust as it leaves the centre. The dust front sweeps in from the card's
 * outer edge with a ragged border, and the loose grains blow outwards, spread and fade.
 */
internal class ThanosCard(card: Int) {
    private val seed = card * SEED_STRIDE + 3
    private val flat = GridMesh(cols = 1, rows = 1)
    private val dust by lazy { Mesh(GRAIN_COLS * GRAIN_ROWS * VERTICES_PER_QUAD, withAlpha = true) }
    private var dustReady = false

    /** Five random numbers per grain, fixed for the card's life. */
    private val noise by lazy { FloatArray(GRAIN_COLS * GRAIN_ROWS * NOISE_PER_GRAIN) }

    fun update(offset: Float, stage: CarouselStage, time: Float, layer: CardLayer) {
        val distance = abs(offset)
        val cardX = stage.centerX + offset * stage.cardWidth * PITCH
        if (distance * SWEEP - THRESHOLD <= 0f) {
            // Nothing has turned to dust yet: one quad is enough.
            placeFlat(cardX, stage)
            layer.mesh = flat.mesh
            return
        }
        if (!dustReady) prepareDust()
        val side = signOf(offset)
        val unit = stage.unit
        val grainWidth = stage.cardWidth / GRAIN_COLS
        val grainHeight = stage.cardHeight / GRAIN_ROWS
        val alphas = checkNotNull(dust.alphas)
        var grain = 0
        for (row in 0 until GRAIN_ROWS) {
            for (col in 0 until GRAIN_COLS) {
                val u = (col + 0.5f) / GRAIN_COLS
                val v = (row + 0.5f) / GRAIN_ROWS
                val base = grain * NOISE_PER_GRAIN
                val n1 = noise[base]
                val n2 = noise[base + 1]
                val n3 = noise[base + 2]
                val n4 = noise[base + 3]
                val n5 = noise[base + 4]
                // How far out this grain sits: 0 on the edge facing the centre, 1 on the far edge.
                val outer = if (side > 0f) u else 1f - u
                val raw = distance * SWEEP - (1f - outer) * INWARD_DELAY - n1 * RAGGED - THRESHOLD
                val dusted = (raw / SOFTNESS).coerceIn(0f, 1f)
                val speed = BLOW + BLOW_RANDOM * n4
                val dx = dusted * (side * speed + (n2 - 0.5f) * JITTER_X) * unit
                // Whole grains skip the trigonometry: most of a card is whole most of the time.
                val flutter =
                    if (dusted > 0f) sin(time * FLUTTER_SPEED + n2 * TAU) * FLUTTER else 0f
                val dy = dusted * ((n3 - 0.5f) * 2f * (RISE + RISE_RANDOM * n4) + flutter) * unit
                val shrink = 1f - SHRINK * dusted
                val x = cardX + (u - 0.5f) * stage.cardWidth + dx
                val y = stage.centerY + (v - 0.5f) * stage.cardHeight + dy
                val halfW = grainWidth * shrink / 2f
                val halfH = grainHeight * shrink / 2f
                val alpha = if (n5 < THIN_OUT * dusted) 0f else 1f - FADE * dusted
                writeQuad(grain, x - halfW, y - halfH, x + halfW, y + halfH)
                for (k in 0 until VERTICES_PER_QUAD) alphas[grain * VERTICES_PER_QUAD + k] = alpha
                grain++
            }
        }
        layer.mesh = dust
    }

    private fun placeFlat(cardX: Float, stage: CarouselStage) {
        val left = cardX - stage.cardWidth / 2f
        val top = stage.centerY - stage.cardHeight / 2f
        flat.setPoint(0, 0, left, top)
        flat.setPoint(1, 0, left + stage.cardWidth, top)
        flat.setPoint(0, 1, left, top + stage.cardHeight)
        flat.setPoint(1, 1, left + stage.cardWidth, top + stage.cardHeight)
        flat.commit()
    }

    /** Each grain samples its own little square of the card. */
    private fun prepareDust() {
        var grain = 0
        for (row in 0 until GRAIN_ROWS) {
            for (col in 0 until GRAIN_COLS) {
                val u0 = col / GRAIN_COLS.toFloat()
                val v0 = row / GRAIN_ROWS.toFloat()
                val u1 = (col + 1) / GRAIN_COLS.toFloat()
                val v1 = (row + 1) / GRAIN_ROWS.toFloat()
                quadCorners(grain, u0, v0, u1, v1, dust.uvs)
                for (k in 0 until NOISE_PER_GRAIN) {
                    noise[grain * NOISE_PER_GRAIN + k] = hash01(seed, grain, k + 1)
                }
                grain++
            }
        }
        dustReady = true
    }

    private fun writeQuad(grain: Int, left: Float, top: Float, right: Float, bottom: Float) =
        quadCorners(grain, left, top, right, bottom, dust.positions)

    private companion object {
        const val GRAIN_COLS = 90
        const val GRAIN_ROWS = 114
        const val NOISE_PER_GRAIN = 5
        const val SEED_STRIDE = 131
        const val PITCH = 1.2f
        const val SWEEP = 1f
        const val THRESHOLD = 0.25f
        const val INWARD_DELAY = 0.9f
        const val RAGGED = 0.35f
        const val SOFTNESS = 0.3f
        const val BLOW = 40f
        const val BLOW_RANDOM = 230f
        const val JITTER_X = 60f
        const val RISE = 70f
        const val RISE_RANDOM = 120f
        const val FLUTTER = 6f
        const val FLUTTER_SPEED = 0.9f
        const val SHRINK = 0.45f
        const val FADE = 0.55f
        const val THIN_OUT = 0.45f
        const val TAU = (2.0 * PI).toFloat()
    }
}

/** Writes the six corners of an axis-aligned quad into [target], in [GridMesh] order. */
internal fun quadCorners(
    quad: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    target: FloatArray,
) {
    var i = quad * VERTICES_PER_QUAD * 2
    target[i++] = left
    target[i++] = top
    target[i++] = right
    target[i++] = top
    target[i++] = left
    target[i++] = bottom
    target[i++] = right
    target[i++] = top
    target[i++] = right
    target[i++] = bottom
    target[i++] = left
    target[i] = bottom
}
