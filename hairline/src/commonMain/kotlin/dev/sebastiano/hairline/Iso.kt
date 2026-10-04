// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The drawing maths every figure shares, with nothing of Compose in it.
 *
 * Everything is in viewBox units: every figure is drawn in a 400 × 320 box and scaled to its
 * stage, so no number here depends on the window. World space is x/y on the ground and z up; the
 * camera turns the ground by an azimuth, then squashes y by sin(el) and lifts z by cos(el). There
 * is no perspective and no hidden-line removal: plates are filled with the ground colour and
 * painted back to front, so a nearer one simply covers.
 */

data class Vec2(val x: Double, val y: Double)

data class Vec3(val x: Double, val y: Double, val z: Double)

/** A sample on a rounded outline, with its outward normal on the ground plane. */
data class Sample(val u: Double, val v: Double, val nu: Double, val nv: Double)

typealias Ring = List<Sample>

/**
 * An orthographic camera. [az] is in radians, [k] is sin(elevation) (.5 is the 2:1 view every
 * figure rests at), [s] is the scale, and [ox]/[oy] the screen offset [fit] computes. Mutable on
 * purpose: the turntable turns it.
 */
class Camera(
    var az: Double,
    var k: Double,
    var s: Double,
    var ox: Double = 0.0,
    var oy: Double = 0.0,
)

fun interface Projector {
    operator fun invoke(x: Double, y: Double, z: Double): Vec2
}

/** Two lines of a solid: its bright silhouette and the one dim crease inside it. */
class PrismPaths(val sil: PathData, val crease: PathData)

fun clamp(v: Double, a: Double, b: Double): Double = max(a, min(b, v))

fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

fun rad(d: Double): Double = d * PI / 180

/** Two decimals: enough for a hairline at any zoom. */
fun r2(n: Double): Double = jsRound(n * 100) / 100

/** JavaScript's Math.round: halves go up, towards +∞. Kotlin's [kotlin.math.round] goes to even. */
fun jsRound(n: Double): Double = floor(n + 0.5)

/* ---------- projection ---------- */

fun cam(azDeg: Double, k: Double, s: Double): Camera = Camera(rad(azDeg), k, s)

/**
 * The projector for the camera as it is now. Figures that move the camera (the turntable) call this
 * again each frame; the rest call it once.
 */
fun proj(c: Camera): Projector {
    val co = cos(c.az)
    val si = sin(c.az)
    val zf = sqrt(1 - c.k * c.k)
    val ox = c.ox
    val oy = c.oy
    val s = c.s
    val k = c.k
    return Projector { x, y, z ->
        val bx = x * co - y * si
        val by = x * si + y * co
        Vec2(ox + s * bx, oy + s * (by * k - z * zf))
    }
}

/**
 * The ground-plane inverse: the world x/y under a screen point, on the plane at height z. This is
 * how the pointer reaches the drawing: every hover in the figures is tested in world units, never
 * in pixels.
 */
fun unproj(c: Camera, sx: Double, sy: Double, z: Double): Vec2 {
    val co = cos(c.az)
    val si = sin(c.az)
    val zf = sqrt(1 - c.k * c.k)
    val bx = (sx - c.ox) / c.s
    val by = ((sy - c.oy) / c.s + z * zf) / c.k
    return Vec2(bx * co + by * si, -bx * si + by * co)
}

/** Sets the camera's offset so the bounding box of [pts] is centred on ([cx], [cy]). */
fun fit(c: Camera, pts: List<Vec3>, cx: Double, cy: Double) {
    c.ox = 0.0
    c.oy = 0.0
    val p = proj(c)
    var a = 1e9
    var b = -1e9
    var lo = 1e9
    var hi = -1e9
    for (q3 in pts) {
        val q = p(q3.x, q3.y, q3.z)
        a = min(a, q.x)
        b = max(b, q.x)
        lo = min(lo, q.y)
        hi = max(hi, q.y)
    }
    c.ox = cx - (a + b) / 2
    c.oy = cy - (lo + hi) / 2
}

/**
 * JavaScript's `Number.prototype.toFixed`, for captions: halves round up, and -0.01 keeps its sign.
 */
fun toFixed(x: Double, digits: Int): String {
    val f = 10.0.pow(digits)
    val n = jsRound(abs(x) * f).toLong()
    val whole = n / f.toLong()
    val frac = (n % f.toLong()).toString().padStart(digits, '0')
    val sign = if (x < 0) "-" else ""
    return if (digits == 0) "$sign$whole" else "$sign$whole.$frac"
}
