// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsRound

/*
 * The details every kind shares (`t3EmitPlanes`, `t3EmitDots`, `t3EmitRibs`) and the fixed
 * picture (`t3EmitFixed`): markup planes placed by one matrix and faded by facing, dots faded by
 * facing, knurl rows faded per rib, and kit art that never changes under the motion, moved by a
 * translation.
 */

/** Places every plane of [run]; [sx], [sy] is the part's own translation (a fixed picture's). */
internal fun emitPlanes(
    run: TurnRun,
    cam: TurnCam,
    f: TurnFrame,
    sx: Double = 0.0,
    sy: Double = 0.0,
) {
    val m = cam.m
    val v3 = cam.v
    for (j in run.planes.indices) {
        val plane = run.planes[j]
        val role = run.role.getValue("plane$j")
        val o = plane.o
        val x = cam.ox - sx + m[0] * o.x + m[1] * o.y + m[2] * o.z
        val y = cam.oy - sy + m[3] * o.x + m[4] * o.y + m[5] * o.z
        val facing = plane.n.x * v3.x + plane.n.y * v3.y + plane.n.z * v3.z
        f.o[role] = jsRound(t3Step(plane.fade.first, plane.fade.second, facing) * 100) / 100
        if (plane.billboard) {
            f.t[role] = Affine(1.0, 0.0, 0.0, 1.0, x, y, translate = true)
            continue
        }
        val u = plane.u
        val v = plane.v
        val a = m[0] * u.x + m[1] * u.y + m[2] * u.z
        val b = m[3] * u.x + m[4] * u.y + m[5] * u.z
        val c = m[0] * v.x + m[1] * v.y + m[2] * v.z
        val d = m[3] * v.x + m[4] * v.y + m[5] * v.z
        f.t[role] = Affine(a, b, c, d, x, y)
    }
}

/** Places every dot of [run] and fades it by facing. */
internal fun emitDots(
    run: TurnRun,
    cam: TurnCam,
    f: TurnFrame,
    sx: Double = 0.0,
    sy: Double = 0.0,
) {
    val m = cam.m
    val v = cam.v
    for (j in run.dots.indices) {
        val dot = run.dots[j]
        val role = run.role.getValue("c$j")
        val p = dot.p
        f.cx[role] = cam.ox - sx + m[0] * p.x + m[1] * p.y + m[2] * p.z
        f.cy[role] = cam.oy - sy + m[3] * p.x + m[4] * p.y + m[5] * p.z
        val n = dot.n
        val facing =
            if (n != null)
                t3Step(dot.fade.first, dot.fade.second, n.x * v.x + n.y * v.y + n.z * v.z)
            else 1.0
        val alpha = facing * TurnState.detail
        f.o[role] = if (alpha < 0.005) 0.0 else jsRound(alpha * 100) / 100
    }
}

/** A prepared fixed picture. */
class FixedRun internal constructor(index: Int, part: PartData, unit: Double, spec: FixedSpec) :
    TurnRun(index, part, unit) {
    val anchor: Point3 = spec.anchor
    val home: Flat = spec.home
    internal val box0: DoubleArray = spec.box
    internal val hull0: DoubleArray = spec.hull
    val frame: Frame? = spec.frame
    internal val ribs: RibSpec? = part.ribs

    override fun cloudOf(): DoubleArray = part.cloud ?: DoubleArray(0)

    override fun axesOf(): List<Point3> = listOfNotNull(frame?.a)

    override fun emit(cam: TurnCam, f: TurnFrame) {
        val a = anchor
        val m = cam.m
        val x = cam.ox + m[0] * a.x + m[1] * a.y + m[2] * a.z
        val y = cam.oy + m[3] * a.x + m[4] * a.y + m[5] * a.z
        val rx = jsRound((x - home.x) * 100) / 100
        val ry = jsRound((y - home.y) * 100) / 100
        f.t[role.getValue("move")] =
            if (rx != 0.0 || ry != 0.0) Affine(1.0, 0.0, 0.0, 1.0, rx, ry, translate = true)
            else null
        f.shiftX = rx
        f.shiftY = ry
        f.box[0] = box0[0] + rx
        f.box[1] = box0[1] + ry
        f.box[2] = box0[2] + rx
        f.box[3] = box0[3] + ry
        val count = hull0.size / 2
        val hull = DoubleArray(2 * count)
        for (i in 0 until count) {
            hull[2 * i] = hull0[2 * i] + rx
            hull[2 * i + 1] = hull0[2 * i + 1] + ry
        }
        f.setHull(hull, count)
        val ribs = ribs
        val fr = frame
        if (ribs != null && fr != null) emitRibs(this, cam, f, fr, ribs, rx, ry)
        emitPlanes(this, cam, f, rx, ry)
        emitDots(this, cam, f, rx, ry)
    }
}

/** A fixed picture's knurl rows, `t3EmitRibs`, in the picture's own (shifted) frame. */
internal fun emitRibs(
    run: TurnRun,
    cam: TurnCam,
    f: TurnFrame,
    fr: Frame,
    ribs: RibSpec,
    sx: Double,
    sy: Double,
) {
    val lines = FadedLines()
    if (TurnState.detail < 0.01) {
        f.levels = t3Faded(f, run, "r", lines)
        return
    }
    val m = cam.m
    val v = cam.v
    val r0 = ribs.r0 ?: 0.0
    val r1 = ribs.r1 ?: r0
    val slope = (r1 - r0) / (ribs.s1 - ribs.s0).let { if (it == 0.0) 1.0 else it }
    val norm = dev.sebastiano.anatomy.kit.jsHypot(1.0, slope)
    val aV = fr.a.x * v.x + fr.a.y * v.y + fr.a.z * v.z
    val uV = fr.u.x * v.x + fr.u.y * v.y + fr.u.z * v.z
    val vV = fr.v.x * v.x + fr.v.y * v.y + fr.v.z * v.z
    val seg = DoubleArray(4)
    fun at(s: Double, r: Double, t: Double, index: Int) {
        val c = jsCos(t)
        val n = jsSin(t)
        val px = fr.o.x + fr.a.x * s + r * (c * fr.u.x + n * fr.v.x)
        val py = fr.o.y + fr.a.y * s + r * (c * fr.u.y + n * fr.v.y)
        val pz = fr.o.z + fr.a.z * s + r * (c * fr.u.z + n * fr.v.z)
        seg[index] = cam.ox - sx + m[0] * px + m[1] * py + m[2] * pz
        seg[index + 1] = cam.oy - sy + m[3] * px + m[4] * py + m[5] * pz
    }
    for (i in 0 until ribs.count) {
        val t = ribs.phase + (i.toDouble() / ribs.count) * T3_TAU
        val tm = t + ribs.twist / 2
        val facing = (jsCos(tm) * uV + jsSin(tm) * vV - slope * aV) / norm
        val alpha = t3Step(ribs.fade.first, ribs.fade.second, facing)
        if (alpha * TURN_LEVELS < 0.5) continue
        at(ribs.s0, r0, t, 0)
        at(ribs.s1, r1, t + ribs.twist, 2)
        lines.add(t3Path(seg, 2, false), alpha, i)
    }
    f.levels = t3Faded(f, run, "r", lines)
}
