// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.circleRing
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.ringOf
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Helpers in `geometry.ts`; the original cleans duplicate rounded-plan corners. */
internal object SpotGeometry {
    val viewAngle = atan2(SpotRig.VIEW.y, SpotRig.VIEW.x)

    fun line(points: List<Point3>) = pathOf(points.map(SpotRig::at))

    fun lines(runs: List<List<Point3>>) = IsoPath.join(runs.map(::line))

    fun clean(ring: List<Floor>) = ring.filterIndexed { index, p ->
        val n = ring[(index + 1) % ring.size]
        hypot(n.x - p.x, n.y - p.y) > 1e-6
    }

    fun solid(plan: Plan, z: Double, height: Double, steps: Int = 6, bevel: Double = 0.0) =
        extrude(clean(ringOf(plan, steps)), z, height, SpotRig.P, convex = true, bevel = bevel)

    fun cyl(x: Double, y: Double, r: Double, z: Double, h: Double, steps: Int = 28) =
        extrude(circleRing(x, y, r, steps), z, h, SpotRig.P)

    fun ring(x: Double, y: Double, r: Double, z: Double, steps: Int = 24) =
        onTop(circleRing(x, y, r, steps), z, SpotRig.P, true)

    fun knurl(x: Double, y: Double, r: Double, z0: Double, z1: Double, count: Int = 9) =
        lines(
            (0 until count).map { index ->
                val angle = viewAngle - PI * 0.42 + PI * 0.84 * index / (count - 1)
                val px = x + r * cos(angle)
                val py = y + r * sin(angle)
                listOf(Point3(px, py, z0 + 0.4), Point3(px, py, z1 - 0.4))
            }
        )

    fun runOf(points: List<Floor>, want: Boolean): List<Floor> {
        val count = points.size
        val facing =
            SpotContours.normalsOf(points).map {
                it.x * SpotRig.VIEW.x + it.y * SpotRig.VIEW.y > 1e-9
            }
        val start =
            facing.indices.firstOrNull {
                facing[it] == want && facing[(it + count - 1) % count] != want
            } ?: return emptyList()
        return buildList {
            add(points[start])
            var step = 0
            while (step < count && facing[(start + step) % count] == want) {
                add(points[(start + step + 1) % count])
                step++
            }
        }
    }

    fun visibleRun(plan: Plan, z: Double, steps: Int = 8) =
        onTop(runOf(clean(ringOf(plan, steps)), true), z, SpotRig.P)

    fun corners(plan: Plan, inset: Double) =
        with(plan) {
            listOf(
                Floor(x + inset, y + inset),
                Floor(x + w - inset, y + inset),
                Floor(x + w - inset, y + d - inset),
                Floor(x + inset, y + d - inset),
            )
        }

    fun polar(r: Double, degrees: Double, z: Double): Point3 {
        val angle = degrees * (PI / 180)
        return Point3(SpotRig.PIVOT.x + r * sin(angle), SpotRig.PIVOT.y - r * cos(angle), z)
    }

    fun sector(r0: Double, r1: Double, from: Double, to: Double, steps: Int = 40): List<Floor> =
        buildList {
            fun point(r: Double, degrees: Double): Floor {
                val p = polar(r, degrees, 0.0)
                return Floor(p.x, p.y)
            }
            for (step in 0..steps) add(point(r1, from + (to - from) * step / steps))
            for (step in steps downTo 0) add(point(r0, from + (to - from) * step / steps))
        }
}
