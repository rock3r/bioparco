// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The core of the kit: `kit/iso-kit.mjs` of the original, function for function and in the same
 * argument order, so a recipe in the skill reads the same in Kotlin. World x and y are on the
 * floor, z is up. The camera is orthographic: no perspective, no hidden-line removal. Solids are
 * filled with the paper colour and painted back to front, so a nearer one covers what is behind.
 */

const val AZIMUTH: Double = 45.0
const val ELEVATION: Double = 30.0
const val SHADES: Int = 4
internal const val RADIANS = PI / 180
private val UNIT = sqrt(1.6)

/** An isometric camera: where the world origin lands, the scale, and the view direction. */
data class Projection(
    val origin: Flat,
    val scale: Double,
    val azimuth: Double = AZIMUTH,
    val elevation: Double = ELEVATION,
) {
    internal val camera: Camera by lazy { cameraOf(this) }
}

/** The trigonometry of a [Projection], computed once. */
class Camera(
    val sinA: Double,
    val cosA: Double,
    val sinE: Double,
    val cosE: Double,
    val k: Double,
)

fun cameraOf(projection: Projection): Camera {
    val a = projection.azimuth * RADIANS
    val e = projection.elevation * RADIANS
    return Camera(sin(a), cos(a), sin(e), cos(e), projection.scale * UNIT)
}

/** A world point on screen. */
fun iso(point: Point3, projection: Projection): Flat {
    val (x, y, z) = point
    val c = projection.camera
    return Flat(
        projection.origin.x + (x * c.sinA - y * c.cosA) * c.k,
        projection.origin.y + ((x * c.cosA + y * c.sinA) * c.sinE - z * c.cosE) * c.k,
    )
}

/** The floor direction that points at the viewer. */
fun towardViewer(projection: Projection): Floor {
    val c = projection.camera
    return Floor(c.cosA, c.sinA)
}

/** How near a point is to the viewer. Larger is nearer: paint in ascending order. */
fun depthOf(point: Point3, projection: Projection): Double {
    val c = projection.camera
    return (point.x * c.cosA + point.y * c.sinA) * c.cosE + point.z * c.sinE
}

/** The screen vector of a world offset. Use it to turn pointer movement into world movement. */
fun axisVector(projection: Projection, by: Point3): Flat {
    val origin = iso(Point3(0.0, 0.0, 0.0), projection)
    return iso(by, projection) - origin
}

/**
 * The screen offset that moves a pre-built group by a world offset: carriages, drawers, plungers
 * and lifts. The original returns a `translate(…)` string; here it is the offset itself.
 */
fun translateAlong(projection: Projection, by: Point3): Flat = axisVector(projection, by)

/** An affine map `(u, v) → origin + u·[u] + v·[v]`, for laying text and art on a face. */
data class FaceMatrix(val u: Flat, val v: Flat, val origin: Flat) {
    fun map(x: Double, y: Double): Flat =
        Flat(origin.x + u.x * x + v.x * y, origin.y + u.y * x + v.y * y)
}

private class Axes(val origin: Flat, val x: Flat, val y: Flat, val z: Flat)

private fun axesAt(at: Floor, z: Double, projection: Projection): Axes {
    val origin = iso(Point3(at.x, at.y, z), projection)
    fun along(point: Point3) = iso(point, projection) - origin
    return Axes(
        origin,
        along(Point3(at.x + 1, at.y, z)),
        along(Point3(at.x, at.y + 1, z)),
        along(Point3(at.x, at.y, z + 1)),
    )
}

/** Which way text runs on a top face. */
enum class Along {
    X,
    Y,
}

/** Lays flat art (text) on a top face at [at], running along [along]. */
fun topMatrix(at: Floor, z: Double, projection: Projection, along: Along = Along.X): FaceMatrix {
    val a = axesAt(at, z, projection)
    return if (along == Along.X) FaceMatrix(a.x, a.y, a.origin)
    else FaceMatrix(Flat(-a.y.x, -a.y.y), a.x, a.origin)
}

/** Which vertical face [sideMatrix] lays art on. */
enum class Face {
    Left,
    Right,
}

/** Lays flat art on a vertical face at [at], with local y running down the face. */
fun sideMatrix(at: Floor, z: Double, projection: Projection, face: Face): FaceMatrix {
    val a = axesAt(at, z, projection)
    val down = Flat(-a.z.x, -a.z.y)
    return if (face == Face.Left) FaceMatrix(a.x, down, a.origin)
    else FaceMatrix(Flat(-a.y.x, -a.y.y), down, a.origin)
}

/** How many screen units a world [height] rises. */
fun screenRise(height: Double, projection: Projection): Double {
    val c = projection.camera
    return height * c.cosE * c.k
}

/**
 * The projection that centres [points] in a [width] by [height] viewBox with [pad] around them,
 * leaving [headroom] free at the top.
 */
fun fitProjection(
    points: List<Point3>,
    width: Double,
    height: Double,
    pad: Double = 24.0,
    headroom: Double = 0.0,
    azimuth: Double = AZIMUTH,
    elevation: Double = ELEVATION,
): Projection {
    val unit = Projection(Flat(0.0, 0.0), 1.0, azimuth, elevation)
    val flat = points.map { iso(it, unit) }
    val left = flat.minOf { it.x }
    val right = flat.maxOf { it.x }
    val up = flat.minOf { it.y }
    val down = flat.maxOf { it.y }
    val scale = min((width - pad * 2) / (right - left), (height - pad * 2 - headroom) / (down - up))
    return Projection(
        origin =
            Flat(
                width / 2 - (left + right) / 2 * scale,
                headroom + (height - headroom) / 2 - (up + down) / 2 * scale,
            ),
        scale = scale,
        azimuth = azimuth,
        elevation = elevation,
    )
}
