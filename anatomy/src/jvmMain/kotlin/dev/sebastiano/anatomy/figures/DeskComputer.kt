// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Axis
import dev.sebastiano.anatomy.kit.Face
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.coil
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.roundedPlan
import dev.sebastiano.anatomy.kit.segment
import dev.sebastiano.anatomy.kit.sideMatrix
import dev.sebastiano.anatomy.kit.slabOf

/** The original desk-computer build, split at its live screen, LED, rocker and key caps. */
object DeskComputer {
    const val WIDTH = 600.0
    const val HEIGHT = 440.0
    val PLATE = Plan(-100.0, -84.0, 210.0, 242.0, 10.0)
    val CASE = Plan(-62.0, -62.0, 124.0, 118.0, 8.0)
    const val FRONT = 56.0
    const val SIDE = 62.0
    const val TOP = 170.0
    val KB = Plan(-76.0, 84.0, 152.0, 58.0, 4.0)
    val P =
        fitProjection(
            boxCorners(PLATE, -15.0, 0.0) + boxCorners(CASE, 0.0, TOP),
            WIDTH,
            HEIGHT,
            pad = 30.0,
            azimuth = 56.0,
        )

    fun at(x: Double, y: Double, z: Double) = iso(Point3(x, y, z), P)

    fun plane(y: Double) = sideMatrix(Floor(0.0, y), 0.0, P, Face.Left)

    fun flat(x0: Double, z0: Double, x1: Double, z1: Double, r: Double, steps: Int = 6): IsoPath =
        pathOf(
            roundedPlan(Plan(x0, z0, x1 - x0, z1 - z0, r), steps).map { Flat(it.x, -it.y) },
            true,
        )

    private fun byDepth(points: List<Floor>) = points.sortedBy {
        depthOf(Point3(it.x, it.y, 0.0), P)
    }

    val BASE = art {
        halo(haloOf(PLATE, -15.0, 10.0, P))
        for ((x, y) in byDepth(corners(PLATE, 22.0))) {
            solid(cylinder(x, y, 7.0, -15.0, 1.6, P, 28), tone = Tone.Lo)
            solid(cylinder(x, y, 5.4, -13.4, 3.4, P, 28), tone = Tone.Mid)
        }
        solid(slabOf(PLATE, -10.0, 10.0, P, 8, 1.6), tone = Tone.Mid)
        line(planOutline(insetPlan(PLATE, 5.0), 0.0, P), tone = Tone.Lo)
        val screws = corners(PLATE, 10.0)
        line(IsoPath.join(screws.map { ring(it.x, it.y, 2.2, 0.0, P, 16) }), tone = Tone.Lo)
        dots(screws.map { at(it.x, it.y, 0.0) }, size = 0.5)
        val label = Plan(-88.0, 144.0, 40.0, 9.0, 1.2)
        solid(slabOf(label, 0.0, 0.8, P, 3), tone = Tone.Lo, crease = null)
        line(
            IsoPath.join(
                listOf(30.0, 22.0).mapIndexed { i, w ->
                    lineOnTop(
                        Floor(label.x + 4, label.y + 3 + i * 3),
                        Floor(label.x + 4 + w, label.y + 3 + i * 3),
                        0.8,
                        P,
                    )
                }
            ),
            tone = Tone.Lo,
            free = Free.Both,
        )
        dots(listOf(at(label.x + label.w - 3, label.y + label.d / 2, 0.8)), size = 0.45)
    }

    val SHELL = art {
        halo(haloOf(CASE, 0.0, 8.0, P))
        solid(slabOf(insetPlan(CASE, 3.0), 0.0, 5.0, P, 8), tone = Tone.Lo)
        solid(slabOf(CASE, 5.0, 165.0, P, 8, 1.6), tone = Tone.Mid)
        val handle = Plan(-42.0, CASE.y + 7, 84.0, 11.0, 5.5)
        line(planOutline(handle, TOP, P, 8), tone = Tone.Lo)
        line(planOutline(insetPlan(handle, 2.2), TOP, P, 8), tone = Tone.Faint)
        line(
            IsoPath.join(
                List(7) { i ->
                    planOutline(Plan(-34.0, CASE.y + 27.3 + i * 3.4, 68.0, 1.4, 0.7), TOP, P, 4)
                }
            ),
            tone = Tone.Faint,
        )
        line(
            lineOnTop(Floor(CASE.x, 44.0), Floor(SIDE, 44.0), TOP, P) +
                segment(Point3(SIDE, 44.0, TOP), Point3(SIDE, 44.0, 5.0), P),
            tone = Tone.Lo,
        )
        line(
            IsoPath.join(List(14) { sideSlot(CASE.y + 10 + it * 3.2, TOP - 48, TOP - 14) }),
            tone = Tone.Faint,
        )
        line(
            IsoPath.join(List(10) { sideSlot(CASE.y + 10 + it * 3.2, 20.0, 30.0) }),
            tone = Tone.Faint,
        )
    }

    private fun sideSlot(y: Double, z0: Double, z1: Double): IsoPath =
        pathOf(
            roundedPlan(Plan(y - 0.8, z0, 1.6, z1 - z0, 0.8), 4).map { at(SIDE, it.x, it.y) },
            true,
        )

    val FACE_TRIM = flat(CASE.x + 4, 9.0, SIDE - 4, TOP - 4, 5.0)
    val GRILLE = IsoPath.join(List(9) { flat(-46.7 + it * 3, 42.0, -45.3 + it * 3, 54.0, 0.7, 4) })
    val LED = art { dots(listOf(at(-46.0, FRONT, 36.0)), size = 0.8, tone = Tone.Hi) }
    val SLOT_DOT = art { dots(listOf(at(50.0, FRONT, 44.0)), size = 0.5) }
    val ROCKER_BASE = art {
        solid(slabOf(Plan(SIDE, -54.0, 1.2, 12.0, 1.0), 12.0, 15.0, P, 3), tone = Tone.Mid)
    }
    val ROCKER_ON = art {
        half(13.0, true)
        half(20.4, false)
    }
    val ROCKER_OFF = art {
        half(20.4, true)
        half(13.0, false)
    }

    private fun ArtBuilder.half(z: Double, proud: Boolean) {
        solid(
            slabOf(Plan(SIDE + 1.2, -52.5, if (proud) 2.4 else 0.8, 9.0, 0.6), z, 5.6, P, 2),
            tone = Tone.Mid,
        )
    }

    val PORT = art {
        solid(slabOf(Plan(SIDE - 0.4, 27.0, 1.4, 10.0, 1.0), 8.0, 9.0, P, 3), tone = Tone.Lo)
        solid(slabOf(Plan(SIDE + 1, 28.0, 4.4, 8.0, 1.0), 9.0, 7.0, P, 3, 0.4), tone = Tone.Mid)
        solid(slabOf(Plan(SIDE + 5.4, 30.0, 5.0, 4.0, 1.6), 10.5, 4.0, P, 4), tone = Tone.Mid)
        dots(listOf(at(SIDE + 3.2, 29.6, 16.0), at(SIDE + 3.2, 34.4, 16.0)), size = 0.4)
    }

    val KEYBOARD = art {
        halo(haloOf(KB, 0.0, 4.0, P))
        for ((x, y) in byDepth(corners(KB, 8.0))) solid(
            cylinder(x, y, 2.8, 0.0, 1.6, P, 16),
            tone = Tone.Lo,
        )
        solid(slabOf(KB, 1.6, DeskKeys.TOP - 1.6, P, 6, 1.0), tone = Tone.Mid)
        val well =
            Plan(
                DeskKeys.X - 1.4,
                DeskKeys.Y - 1.4,
                DeskKeys.U * 15 + 2.8,
                DeskKeys.U * 5 + 2.8,
                2.0,
            )
        line(planOutline(well, DeskKeys.TOP, P, 4), tone = Tone.Lo)
        dots(
            listOf(
                at(KB.x + 4, KB.y + 2.4, DeskKeys.TOP),
                at(KB.x + KB.w - 4, KB.y + 2.4, DeskKeys.TOP),
            ),
            size = 0.45,
        )
    }
    val KEYBOARD_PORT = art {
        solid(slabOf(Plan(KB.x + KB.w, 90.0, 4.0, 4.0, 1.5), 2.5, 4.0, P, 4), tone = Tone.Mid)
    }
    val CABLE = art {
        line(
            pathOf(
                listOf(
                    at(SIDE + 10.4, 32.0, 12.5),
                    at(SIDE + 14, 34.0, 11.4),
                    at(SIDE + 17, 37.5, 8.0),
                    at(SIDE + 19.6, 41.0, 5.2),
                    at(83.2, 44.0, 3.6),
                )
            ),
            tone = Tone.Mid,
            width = 1.1,
        )
        val spring = coil(44.0, 84.0, Floor(80.0, 3.6), 3.2, 17.0, Axis.Y, P)
        line(spring.back, tone = Tone.Lo)
        line(spring.front, tone = Tone.Mid)
        line(
            pathOf(
                listOf(
                    at(83.2, 84.0, 3.6),
                    at(82.6, 87.0, 3.8),
                    at(KB.x + KB.w + 6.4, 91.4, 4.4),
                    at(KB.x + KB.w + 4, 92.0, 4.5),
                )
            ),
            tone = Tone.Mid,
            width = 1.1,
        )
    }
}
