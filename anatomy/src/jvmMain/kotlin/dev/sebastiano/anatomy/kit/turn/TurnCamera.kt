// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.jsHypot

/*
 * Poses and cameras: `t3Poses`, `t3Camera` and `t3Cams`. A group's pose is relative to its parent
 * and composed down the tree; each group gets a camera that sees its parts, written in the group's
 * rest-local coordinates, where the world camera would see them posed. A root turn group takes the
 * azimuth shortcut: turning by θ is the kit's own camera at azimuth `a − θ`.
 */

/** How a group moves. */
enum class GroupKind {
    Turn,
    Slide,
    Hinge,
    Free,
}

/**
 * A free group's pose relative to its parent (or to the world with [world]): a rotation as a
 * row-major matrix [r], a quaternion [q] `(x, y, z, w)` or an [axis] and [angle] in degrees, plus a
 * translation [t].
 */
class FreePose(
    val r: DoubleArray? = null,
    val q: DoubleArray? = null,
    val axis: Point3? = null,
    val angle: Double = 0.0,
    val t: Point3? = null,
    val world: Boolean = false,
)

/**
 * The values a figure drives: numbers by name (group values in degrees or world units, and any
 * figure values a `follow` maps onto groups), and free group poses (null is rest, attached).
 */
data class TurnValues(
    val numbers: Map<String, Double> = emptyMap(),
    val free: Map<String, FreePose?> = emptyMap(),
) {
    operator fun get(name: String): Double? = numbers[name]

    /** These values with [more] on top. */
    operator fun plus(more: TurnValues): TurnValues =
        TurnValues(numbers + more.numbers, free + more.free)

    fun with(name: String, value: Double): TurnValues = copy(numbers = numbers + (name to value))

    fun with(name: String, pose: FreePose?): TurnValues = copy(free = free + (name to pose))
}

/** A group as the runtime sees it, the original's packed `data.groups` entry. */
class GroupData(
    val name: String,
    val parent: Int,
    val kind: GroupKind,
    val origin: Point3,
    val pivot: Point3? = null,
    val direction: Point3? = null,
    val travel: Double? = null,
    val point: Point3? = null,
    val axis: Point3? = null,
    val range: Pair<Double, Double>? = null,
    val upright: Boolean,
    val loose: Boolean,
)

/**
 * One group's pose in the world: rotation, translation, and the azimuth shortcut for root turns.
 */
class GroupPose(
    val r: DoubleArray,
    val t: Point3,
    val turn: Double? = null,
    val pivot: Point3? = null,
)

/** A camera for one group: rest-local points to screen, the view and light in the group's frame. */
class TurnCam(
    val m: DoubleArray,
    val ox: Double,
    val oy: Double,
    val v: Point3,
    val l: Point3,
    val k: Double,
    val turn: Double,
    val r: DoubleArray,
    val t: Point3,
    val lx: Double,
    val ly: Double,
    val sa: Double,
    val ca: Double,
    val se: Double,
    val ce: Double,
)

/** Every group's camera, the world's, and the poses they came from. */
class TurnCams(val groups: List<TurnCam>, val world: TurnCam, val poses: List<GroupPose>) {
    operator fun get(index: Int): TurnCam = groups[index]

    /** The camera of a group by index, or the world's for -1. */
    fun of(group: Int): TurnCam = if (group >= 0) groups[group] else world
}

/** The camera that sees a group posed by [pose], or the world's for null. */
internal fun t3Camera(p: Projection, pose: GroupPose?): TurnCam {
    val a = p.azimuth * T3_RAD
    val e = p.elevation * T3_RAD
    val k = p.scale * T3_ROOT
    val sa = jsSin(a)
    val ca = jsCos(a)
    val se = jsSin(e)
    val ce = jsCos(e)
    val lw = jsHypot(sa, ca, 1.2)
    return if (pose != null && pose.turn == null) matrixCamera(p, pose, k, sa, ca, se, ce, lw)
    else turnCamera(p, pose, k, sa, ca, se, ce, lw)
}

private fun finish(
    m: DoubleArray,
    ox: Double,
    oy: Double,
    v: Point3,
    l: Point3,
    k: Double,
    turn: Double,
    r: DoubleArray,
    t: Point3,
    trig: DoubleArray,
): TurnCam {
    val lh = jsHypot(l.x, l.y).let { if (it == 0.0 || it.isNaN()) 1.0 else it }
    return TurnCam(
        m,
        ox,
        oy,
        v,
        l,
        k,
        turn,
        r,
        t,
        l.x / lh,
        l.y / lh,
        trig[0],
        trig[1],
        trig[2],
        trig[3],
    )
}

private fun matrixCamera(
    p: Projection,
    pose: GroupPose,
    k: Double,
    sa: Double,
    ca: Double,
    se: Double,
    ce: Double,
    lw: Double,
): TurnCam {
    val r = pose.r
    val t = pose.t
    val r0 = doubleArrayOf(sa * k, -ca * k, 0.0)
    val r1 = doubleArrayOf(ca * se * k, sa * se * k, -ce * k)
    val m = DoubleArray(6)
    for (c in 0 until 3) {
        m[c] = r0[0] * r[c] + r0[1] * r[3 + c] + r0[2] * r[6 + c]
        m[3 + c] = r1[0] * r[c] + r1[1] * r[3 + c] + r1[2] * r[6 + c]
    }
    val ox = p.origin.x + r0[0] * t.x + r0[1] * t.y + r0[2] * t.z
    val oy = p.origin.y + r1[0] * t.x + r1[1] * t.y + r1[2] * t.z
    val view = doubleArrayOf(ca * ce, sa * ce, se)
    val light = doubleArrayOf(-sa / lw, ca / lw, 1.2 / lw)
    val v = DoubleArray(3)
    val l = DoubleArray(3)
    for (c in 0 until 3) {
        v[c] = r[c] * view[0] + r[3 + c] * view[1] + r[6 + c] * view[2]
        l[c] = r[c] * light[0] + r[3 + c] * light[1] + r[6 + c] * light[2]
    }
    return finish(
        m,
        ox,
        oy,
        Point3(v[0], v[1], v[2]),
        Point3(l[0], l[1], l[2]),
        k,
        0.0,
        r,
        t,
        doubleArrayOf(sa, ca, se, ce),
    )
}

private fun turnCamera(
    p: Projection,
    pose: GroupPose?,
    k: Double,
    sa: Double,
    ca: Double,
    se: Double,
    ce: Double,
    lw: Double,
): TurnCam {
    val turn = pose?.turn ?: 0.0
    val pivot = pose?.pivot ?: Point3(0.0, 0.0, 0.0)
    val b = p.azimuth * T3_RAD - turn * T3_RAD
    val sb = jsSin(b)
    val cb = jsCos(b)
    val m = doubleArrayOf(sb * k, -cb * k, 0.0, cb * se * k, sb * se * k, -ce * k)
    val ox = p.origin.x + (pivot.x * sa - pivot.y * ca) * k
    val oy = p.origin.y + (pivot.x * ca + pivot.y * sa) * se * k
    val c = jsCos(turn * T3_RAD)
    val s = jsSin(turn * T3_RAD)
    val r = doubleArrayOf(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0)
    return finish(
        m,
        ox,
        oy,
        Point3(cb * ce, sb * ce, se),
        Point3(-sb / lw, cb / lw, 1.2 / lw),
        k,
        turn,
        r,
        Point3(pivot.x, pivot.y, 0.0),
        doubleArrayOf(sa, ca, se, ce),
    )
}

/** Every group's world pose for [values], parents first. */
internal fun t3Poses(groups: List<GroupData>, values: TurnValues): List<GroupPose> {
    val list = ArrayList<GroupPose>(groups.size)
    for (g in groups) {
        val v = values.numbers[g.name] ?: 0.0
        var parent = if (g.parent < 0) null else list[g.parent]
        val po = if (parent != null) groups[g.parent].origin else Point3(0.0, 0.0, 0.0)
        var r = EYE
        var t = Point3(g.origin.x - po.x, g.origin.y - po.y, g.origin.z - po.z)
        when (g.kind) {
            GroupKind.Turn -> r = t3Rot(Point3(0.0, 0.0, 1.0), v)
            GroupKind.Hinge -> r = t3Rot(g.axis!!, v)
            GroupKind.Slide -> {
                val d = t3Unit(g.direction!!)
                t = Point3(t.x + d.x * v, t.y + d.y * v, t.z + d.z * v)
            }
            GroupKind.Free -> {
                val free = values.free[g.name]
                if (free != null) {
                    r = freeRotation(free)
                    if (free.world) {
                        parent = null
                        t = g.origin
                    }
                    free.t?.let { t = Point3(t.x + it.x, t.y + it.y, t.z + it.z) }
                }
            }
        }
        if (parent == null) {
            list.add(
                if (g.kind == GroupKind.Turn)
                    GroupPose(r, t, v, Point3(g.origin.x, g.origin.y, 0.0))
                else GroupPose(r, t)
            )
            continue
        }
        val tw = t3Apply(parent.r, t)
        list.add(
            GroupPose(
                t3Mul(parent.r, r),
                Point3(tw.x + parent.t.x, tw.y + parent.t.y, tw.z + parent.t.z),
            )
        )
    }
    return list
}

private fun freeRotation(v: FreePose): DoubleArray =
    when {
        v.r != null -> v.r
        v.q != null -> t3Quat(v.q)
        v.axis != null -> t3Rot(v.axis, v.angle)
        else -> EYE
    }

/** Every group's camera for [values], and the world's. */
internal fun t3Cams(p: Projection, groups: List<GroupData>, values: TurnValues): TurnCams {
    val poses = t3Poses(groups, values)
    return TurnCams(poses.map { t3Camera(p, it) }, t3Camera(p, null), poses)
}
