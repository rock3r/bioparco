// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.cameraOf
import dev.sebastiano.anatomy.kit.iso

/** World layout and camera from `examples/spot-plate/rig.ts`. */
object SpotRig {
    val GAPS = listOf(4.0, 10.0, 16.0, 24.0, 32.0, 44.0)
    const val RA = 30.0
    const val RB = 22.0
    const val ROWS = 6
    const val MERGE = 20.0
    const val MERGE_MOST = 48.0
    const val WIDTH = 600.0
    const val HEIGHT = 340.0
    const val AZIMUTH = 50.0
    const val MARGIN = 6.0
    const val PITCH = 84.0
    const val WELL_HALF = RA + MARGIN
    const val BIG_Y = MARGIN + RA
    const val DATUM_Y = BIG_Y + RA
    const val WELL_D = DATUM_Y + 44 + 2 * RB + MARGIN
    const val BORDER = 14.0
    const val COLS = 3
    const val ROW_RIM = 16.0

    fun rowX(row: Int) = (row % COLS) * PITCH

    fun rowY(row: Int) = (row / COLS) * (WELL_D + ROW_RIM)

    val WELLS =
        GAPS.indices.map { Plan(rowX(it) - WELL_HALF, rowY(it), WELL_HALF * 2, WELL_D, WELL_HALF) }
    val TRAY =
        Plan(
            -WELL_HALF - BORDER,
            -BORDER,
            rowX(COLS - 1) + (WELL_HALF + BORDER) * 2,
            rowY(ROWS - 1) + WELL_D + BORDER * 2,
            16.0,
        )
    const val TRAY_H = 13.0
    const val FLOOR = TRAY_H - 3
    const val THICK = 8.0
    const val GLASS_TOP = FLOOR + THICK
    val DECK = Plan(TRAY.x + TRAY.w + 10, TRAY.y, 172.0, TRAY.d, 12.0)
    const val DECK_H = 6.0
    val PIVOT = Floor(DECK.x + 22, DECK.y + DECK.d / 2 + 6)
    const val BLADE_LONG = 104.0
    const val LEAD_LONG = 113.0
    const val STOP_R = 111.0
    const val SCALE_R = 120.0
    const val SWEEP = 180.0
    const val WASHER_H = 0.8
    const val STACK_Z = DECK_H + WASHER_H

    fun thicknessOf(gap: Double) = 0.45 + gap / 20

    const val LEAD_H = 1.1
    val BLADE_Z = buildList {
        var z = STACK_Z
        for (gap in GAPS) {
            add(z)
            z += thicknessOf(gap) + 0.12
        }
        add(z)
    }
    val STACK_TOP = BLADE_Z[ROWS] + LEAD_H

    fun angleOf(merge: Double) = merge.coerceIn(0.0, MERGE_MOST) / MERGE_MOST * SWEEP

    val BOTTLE = Floor(DECK.x + DECK.w - 33, DECK.y + 35)
    const val BOTTLE_TOP = DECK_H + 64
    val BASE =
        Plan(TRAY.x - 14, TRAY.y - 14, DECK.x + DECK.w + 14 - (TRAY.x - 14), TRAY.d + 28, 10.0)
    const val BASE_H = 10.0
    const val FOOT_H = 5.0
    val P =
        fit(
            boxCorners(BASE, -BASE_H - FOOT_H, 0.0) +
                boxCorners(DECK, 0.0, STACK_TOP + 6) +
                Point3(BOTTLE.x, BOTTLE.y, BOTTLE_TOP)
        )

    fun at(point: Point3): Flat = iso(point, P)

    private val CAMERA = cameraOf(P)
    val VIEW = Floor(CAMERA.cosA, CAMERA.sinA)
    val LIGHT = Floor(-CAMERA.sinA, CAMERA.cosA)
    val CLIMB = CAMERA.sinE / CAMERA.cosE

    private fun fit(points: List<Point3>): Projection {
        val flat = points.map { iso(it, Projection(Flat(0.0, 0.0), 1.0, AZIMUTH)) }
        val left = flat.minOf { it.x }
        val right = flat.maxOf { it.x }
        val up = flat.minOf { it.y }
        val down = flat.maxOf { it.y }
        val scale = minOf((WIDTH - 80 * 2) / (right - left), (HEIGHT - 34 * 2) / (down - up))
        return Projection(
            Flat(WIDTH / 2 - (left + right) / 2 * scale, HEIGHT / 2 - (up + down) / 2 * scale),
            scale,
            AZIMUTH,
        )
    }
}
