package dev.sebastiano.scrolleffects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shatter: away from the centre a card breaks into glassy pieces that drift apart, tilt and turn.
 * On the way back in they close up again, and the last thing to go is the web of cracks between
 * them.
 */
internal class ShatterCard(card: Int) {
    private val seed = card * SEED_STRIDE + 7
    private val shards = shatter(cols = SHARD_COLS, rows = SHARD_ROWS, seed = seed)
    private val mesh = Mesh(shards.sumOf { it.corners } * 3)

    init {
        var vertex = 0
        for (shard in shards) {
            for (i in 0 until shard.corners) {
                val j = (i + 1) % shard.corners
                // A fan from the centroid: centre, corner i, corner j.
                mesh.uvs[vertex * 2] = shard.centroidU
                mesh.uvs[vertex * 2 + 1] = shard.centroidV
                mesh.uvs[vertex * 2 + 2] = shard.polygon[i * 2]
                mesh.uvs[vertex * 2 + 3] = shard.polygon[i * 2 + 1]
                mesh.uvs[vertex * 2 + 4] = shard.polygon[j * 2]
                mesh.uvs[vertex * 2 + 5] = shard.polygon[j * 2 + 1]
                vertex += 3
            }
        }
    }

    fun update(offset: Float, stage: CarouselStage, time: Float, layer: CardLayer) {
        val strength = smoothstep(START, END, abs(offset))
        val side = signOf(offset)
        val unit = stage.unit
        val cardX = stage.centerX + offset * stage.cardWidth * PITCH
        val cardY = stage.centerY
        var vertex = 0
        shards.forEachIndexed { index, shard ->
            val r1 = hash01(seed, index, 11)
            val r2 = hash01(seed, index, 12)
            val r3 = hash01(seed, index, 13)
            val r4 = hash01(seed, index, 14)
            val r5 = hash01(seed, index, 15)
            // The shard's centre, relative to the card's.
            val cx = (shard.centroidU - 0.5f) * stage.cardWidth
            val cy = (shard.centroidV - 0.5f) * stage.cardHeight
            val drift = sin(time * DRIFT_SPEED + r3 * TAU) * DRIFT * unit
            val moveX = strength * (cx * SPREAD_X + side * (PUSH + PUSH_RANDOM * r1) * unit)
            val moveY = strength * (cy * SPREAD_Y + (r2 - 0.5f) * SCATTER_Y * unit + drift)
            val spin = (r3 - 0.5f) * SPIN * strength
            val cosSpin = cos(spin)
            val sinSpin = sin(spin)
            // A tilt out of the screen plane squashes the shard along a random axis.
            val axis = r4 * PI.toFloat()
            val ax = cos(axis)
            val ay = sin(axis)
            val squash = 1f - TILT * r5 * strength
            val shrink = 1f - SHRINK * strength
            val originX = cardX + cx + moveX
            val originY = cardY + cy + moveY
            repeat(shard.corners * 3) {
                val u = mesh.uvs[vertex * 2]
                val v = mesh.uvs[vertex * 2 + 1]
                var qx = (u - shard.centroidU) * stage.cardWidth * shrink
                var qy = (v - shard.centroidV) * stage.cardHeight * shrink
                val along = (qx * ax + qy * ay) * (squash - 1f)
                qx += along * ax
                qy += along * ay
                mesh.positions[vertex * 2] = originX + qx * cosSpin - qy * sinSpin
                mesh.positions[vertex * 2 + 1] = originY + qx * sinSpin + qy * cosSpin
                vertex++
            }
        }
        layer.mesh = mesh
    }

    private companion object {
        const val SHARD_COLS = 6
        const val SHARD_ROWS = 8
        const val SEED_STRIDE = 101
        const val PITCH = 1.2f
        const val START = 0.04f
        const val END = 0.95f
        const val SPREAD_X = 0.28f
        const val SPREAD_Y = 0.22f
        const val PUSH = 16f
        const val PUSH_RANDOM = 36f
        const val SCATTER_Y = 36f
        const val SPIN = 1f
        const val TILT = 0.8f
        const val SHRINK = 0.08f
        const val DRIFT = 5f
        const val DRIFT_SPEED = 1.1f
        const val TAU = (2.0 * PI).toFloat()
    }
}
