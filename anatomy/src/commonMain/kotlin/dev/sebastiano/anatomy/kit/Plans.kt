// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* Plans: rounded rectangles and circles on the floor, as outlines and points. */

/** A rounded rectangle as points, [steps] per corner, starting at the far right corner. */
fun roundedPlan(plan: Plan, steps: Int = 8): List<Floor> {
    val radius = max(0.0, min(plan.r, min(plan.w / 2, plan.d / 2)))
    val left = plan.x + radius
    val right = plan.x + plan.w - radius
    val near = plan.y + radius
    val far = plan.y + plan.d - radius
    val corners =
        listOf(
            Triple(right, near, -90.0),
            Triple(right, far, 0.0),
            Triple(left, far, 90.0),
            Triple(left, near, 180.0),
        )
    val out = ArrayList<Floor>()
    for ((cx, cy, start) in corners) {
        val count = if (radius > 0) steps else 0
        for (step in 0..count) {
            val angle = (start + if (count != 0) 90.0 * step / count else 45.0) * RADIANS
            out.add(Floor(cx + radius * cos(angle), cy + radius * sin(angle)))
        }
    }
    return out
}

/** A rounded-rectangle outline as points. The same as [roundedPlan]. */
fun ringOf(plan: Plan, steps: Int = 8): List<Floor> = roundedPlan(plan, steps)

/** A circle outline as points. */
fun circleRing(cx: Double, cy: Double, radius: Double, steps: Int = 40): List<Floor> =
    List(steps) { step ->
        val angle = step.toDouble() / steps * PI * 2
        Floor(cx + radius * cos(angle), cy + radius * sin(angle))
    }

/** Shrinks a plan by [by] on every side (a negative [by] grows it), keeping the radius sensible. */
fun insetPlan(plan: Plan, by: Double): Plan =
    Plan(plan.x + by, plan.y + by, plan.w - by * 2, plan.d - by * 2, max(0.0, plan.r - by))

/** Four floor points inset from the corners, for feet and screws. */
fun corners(plan: Plan, inset: Double = 0.0): List<Floor> {
    val left = plan.x + inset
    val right = plan.x + plan.w - inset
    val near = plan.y + inset
    val far = plan.y + plan.d - inset
    return listOf(Floor(left, near), Floor(right, near), Floor(left, far), Floor(right, far))
}

/** The eight corners of a plan raised from [z0] to [z1], for [fitProjection]. */
fun boxCorners(plan: Plan, z0: Double, z1: Double): List<Point3> {
    val out = ArrayList<Point3>()
    for (px in listOf(plan.x, plan.x + plan.w)) {
        for (py in listOf(plan.y, plan.y + plan.d)) {
            for (z in listOf(z0, z1)) out.add(Point3(px, py, z))
        }
    }
    return out
}
