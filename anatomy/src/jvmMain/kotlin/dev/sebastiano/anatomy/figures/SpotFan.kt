// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.pathOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SpotBlade(val paths: SolidPaths, val line: IsoPath)

data class SpotFanPose(val blades: List<SpotBlade>, val halo: IsoPath, val out: List<Boolean>)

/** `fan.ts`: each blade stops at its gap; only the top blade follows the whole sweep. */
object SpotFan {
    private const val RADIANS = PI / 180

    private fun outlineOf(length: Double, tip: Double): List<Floor> = buildList {
        for (step in 0..10) {
            val angle = (-90 + 180.0 * step / 10) * RADIANS
            add(Floor(length + tip * cos(angle), tip * sin(angle)))
        }
        for (step in 0..10) {
            val angle = (90 + 180.0 * step / 10) * RADIANS
            add(Floor(7.2 * cos(angle), 7.2 * sin(angle)))
        }
    }

    private val BLADE = outlineOf(SpotRig.BLADE_LONG, 4.2)
    private val LEAD = outlineOf(SpotRig.LEAD_LONG, 3.2)

    private fun place(shape: List<Floor>, degrees: Double): List<Floor> {
        val a = degrees * RADIANS
        val dx = sin(a)
        val dy = -cos(a)
        val nx = cos(a)
        val ny = sin(a)
        return shape.map { (u, v) ->
            Floor(SpotRig.PIVOT.x + u * dx + v * nx, SpotRig.PIVOT.y + u * dy + v * ny)
        }
    }

    private fun along(degrees: Double, from: Double, to: Double, z: Double): IsoPath {
        val a = degrees * RADIANS
        val dx = sin(a)
        val dy = -cos(a)
        return pathOf(
            listOf(from, to).map {
                SpotRig.at(Point3(SpotRig.PIVOT.x + it * dx, SpotRig.PIVOT.y + it * dy, z))
            }
        )
    }

    private fun bladeAt(
        shape: List<Floor>,
        degrees: Double,
        z: Double,
        height: Double,
        length: Double,
    ) =
        SpotBlade(
            extrude(place(shape, degrees), z, height, SpotRig.P, convex = true),
            along(degrees, 13.0, length - 6, z + height),
        )

    fun fanOf(merge: Double): SpotFanPose {
        val lead = SpotRig.angleOf(merge)
        val blades = ArrayList<SpotBlade>()
        val halo = ArrayList<IsoPath>()
        val out = ArrayList<Boolean>()
        SpotRig.GAPS.forEachIndexed { index, gap ->
            val angle = minOf(lead, SpotRig.angleOf(gap))
            out.add(gap < merge)
            blades.add(
                bladeAt(
                    BLADE,
                    angle,
                    SpotRig.BLADE_Z[index],
                    SpotRig.thicknessOf(gap),
                    SpotRig.BLADE_LONG,
                )
            )
            halo.add(
                pathOf(
                    place(BLADE, angle).map { SpotRig.at(Point3(it.x, it.y, SpotRig.DECK_H)) },
                    true,
                )
            )
        }
        blades.add(
            bladeAt(LEAD, lead, SpotRig.BLADE_Z[SpotRig.ROWS], SpotRig.LEAD_H, SpotRig.LEAD_LONG)
        )
        halo.add(
            pathOf(place(LEAD, lead).map { SpotRig.at(Point3(it.x, it.y, SpotRig.DECK_H)) }, true)
        )
        return SpotFanPose(blades, IsoPath.join(halo), out)
    }
}
