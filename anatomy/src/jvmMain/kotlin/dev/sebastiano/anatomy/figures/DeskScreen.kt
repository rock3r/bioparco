// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.FillInk
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Shape
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.cameraOf
import dev.sebastiano.anatomy.kit.fill
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.roundedPlan
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

/** A front-face opening, with facing-shaded inner walls and a back plane behind the rim. */
class DeskRecess(val plan: Plan, val depth: Double, val y: Double, val ink: FillInk) {
    val shape = DeskComputer.flat(plan.x, plan.y, plan.x + plan.w, plan.y + plan.d, plan.r)
    val plane = DeskComputer.plane(y)
    val backPlane = DeskComputer.plane(y - depth)
    val clip = Shape(shape.onFace(y))
    val top: IsoPath
    val sides: List<IsoPath>
    val walls: Art

    init {
        val tops = mutableListOf<IsoPath>()
        val shades = List(4) { mutableListOf<IsoPath>() }
        val ring = roundedPlan(plan, 6)
        ring.forEachIndexed { index, a ->
            val b = ring[(index + 1) % ring.size]
            val length = hypot(b.x - a.x, b.y - a.y).takeIf { it != 0.0 } ?: 1.0
            var nx = (b.y - a.y) / length
            var nz = -(b.x - a.x) / length
            if (
                nx * (plan.x + plan.w / 2 - (a.x + b.x) / 2) +
                    nz * (plan.y + plan.d / 2 - (a.y + b.y) / 2) < 0
            ) {
                nx = -nx
                nz = -nz
            }
            if (nx > 0 || nz > 0) {
                val quad =
                    pathOf(
                        listOf(
                            DeskComputer.at(a.x, y, a.y),
                            DeskComputer.at(b.x, y, b.y),
                            DeskComputer.at(b.x, y - depth, b.y),
                            DeskComputer.at(a.x, y - depth, a.y),
                        ),
                        true,
                    )
                if (nz >= nx) tops.add(quad)
                else
                    shades[
                            floor(((nx * -cameraOf(DeskComputer.P).sinA + 1) / 2) * 4)
                                .toInt()
                                .coerceIn(0, 3)]
                        .add(quad)
            }
        }
        top = IsoPath.join(tops)
        sides = shades.map { IsoPath.join(it) }
        walls = art {
            fill(top, FillInk.Top)
            sides.forEachIndexed { index, path ->
                if (!path.isEmpty) fill(path, FillInk.entries[FillInk.Shade0.ordinal + index])
            }
        }
    }

    val back: Art by lazy { art { fill(shape.onFace(y - depth), ink) } }
    val rim: Art by lazy { art { line(shape.onFace(y), tone = Tone.Lo) } }
    val edge: Art by lazy { art { line(shape.onFace(y - depth), tone = Tone.Faint) } }
}

/** Face coordinates have x across the case and y down (−world z). */
fun IsoPath.onFace(y: Double): IsoPath {
    val m = DeskComputer.plane(y)
    return IsoPath(
        runs.map { run -> IsoPath.Run(run.points.map { m.map(it.x, it.y) }, run.closed) }
    )
}

object DeskScreen {
    const val MID = 113.5
    const val GLASS_Y = DeskComputer.FRONT - 1.6 - 5
    const val TEXT_X = -32.0
    const val TEXT_Z = 91.0
    const val TEXT_SIZE = 5.2
    val BEZEL = DeskRecess(Plan(-48.0, 68.0, 96.0, 90.0, 6.0), 1.6, DeskComputer.FRONT, FillInk.Top)
    val CRT =
        DeskRecess(
            Plan(-40.0, 77.0, 80.0, 73.0, 8.0),
            5.0,
            DeskComputer.FRONT - 1.6,
            FillInk.LitShade,
        )
    val SLOT = DeskRecess(Plan(8.0, 45.0, 38.0, 5.5, 2.4), 4.0, DeskComputer.FRONT, FillInk.Shade0)
    val SLOT_LINE = pathOf(listOf(Flat(8.0, -46.6), Flat(46.0, -46.6)))
    val GLOW = DeskComputer.flat(-30.0, 85.0, 30.0, 142.0, 26.0, 10)
    val SCAN: IsoPath = run {
        val lines = mutableListOf<IsoPath>()
        var z = 78.2
        while (z < 150) {
            lines.add(pathOf(listOf(Flat(-40.0, -z), Flat(40.0, -z))))
            z += 1.6
        }
        IsoPath.join(lines)
    }
    val LOGO =
        glyph(
            listOf(
                0.0 to 0.0,
                0.37 to 1.0,
                0.55 to 1.0,
                0.92 to 0.0,
                0.71 to 0.0,
                0.63 to 0.23,
                0.29 to 0.23,
                0.21 to 0.0,
            )
        ) +
            glyph(listOf(0.35 to 0.41, 0.57 to 0.41, 0.46 to 0.72)) +
            glyph(listOf(0.68 to 1.0, 0.89 to 1.0, 1.26 to 0.0, 1.05 to 0.0))

    private fun glyph(points: List<Pair<Double, Double>>) =
        pathOf(points.map { (x, y) -> Flat(-19 * 0.61 + x * 19, -(MID + 2 + y * 19)) }, true)

    val BEAM = pathOf(listOf(Flat(-36.0, -MID), Flat(36.0, -MID)))
    val REFLECTION = listOf(Flat(-34.0, -134.0), Flat(-33.0, -143.0), Flat(-24.0, -144.0))
    val reflectionPath =
        pathOf(
            List(25) { i ->
                val t = i / 24.0
                REFLECTION[0] * ((1 - t) * (1 - t)) +
                    REFLECTION[1] * (2 * (1 - t) * t) +
                    REFLECTION[2] * (t * t)
            }
        )
}

/** Scale about the CRT's centre, with the original's line and dot envelopes. */
data class DeskPicture(
    val sx: Double,
    val sy: Double,
    val beam: Double,
    val dot: Double,
    val lit: Boolean,
)

fun deskPicture(power: Boolean, time: Double): DeskPicture {
    fun ease(t: Double) = 1 - (1 - t.coerceIn(0.0, 1.0)).pow(3)
    return if (power) {
        when {
            time < 0.14 -> DeskPicture(ease(time / 0.14), 0.012, 1.0, 0.0, true)
            time < 0.36 -> {
                val t = ease((time - 0.14) / 0.22)
                DeskPicture(1.0, 0.012 + 0.988 * t, 1 - t, 0.0, true)
            }
            else -> DeskPicture(1.0, 1.0, 0.0, 0.0, true)
        }
    } else {
        when {
            time < 0.1 -> {
                val t = ease(time / 0.1)
                DeskPicture(1.0, 1 - 0.988 * t, t, 0.0, true)
            }
            time < 0.26 -> DeskPicture(1 - ease((time - 0.1) / 0.16), 0.012, 1.0, 0.0, true)
            else -> DeskPicture(0.0, 0.0, 0.0, maxOf(0.0, 1 - (time - 0.26) / 0.5), false)
        }
    }
}
