package dev.sebastiano.wheelpicker

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Where a tick lands once the ruler is wrapped around a drum. [x] is its distance from the centre
 * on screen, [visibility] goes from 1 at the centre to 0 at the rim, and [edge] from 0 at the
 * centre to 1 at the rim: it drives the blur and the colour fringe.
 */
data class LensPoint(val x: Float, val visibility: Float, val edge: Float)

private const val VISIBILITY_POWER = 1.5f

private val RIM = (PI / 2).toFloat()

/**
 * The ruler printed on a drum of [radius] seen face on. A tick [distance] along the ruler from the
 * indicator sits at `radius × sin(distance / radius)`: true to size at the centre, squeezed tighter
 * and tighter toward the rim, where it turns away and is gone.
 */
fun lens(distance: Float, radius: Float): LensPoint {
    val angle = distance / radius
    if (abs(angle) >= RIM) return LensPoint(sign(distance) * radius, 0f, 1f)
    val facing = cos(angle)
    return LensPoint(radius * sin(angle), facing.pow(VISIBILITY_POWER), 1f - facing)
}

/** The distance along the ruler of a point [x] from the centre on screen: [lens] backwards. */
fun unlens(x: Float, radius: Float): Float = radius * asin((x / radius).coerceIn(-1f, 1f))
