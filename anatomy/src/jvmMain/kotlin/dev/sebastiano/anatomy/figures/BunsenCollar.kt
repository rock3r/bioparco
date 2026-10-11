package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.BunsenFlame.drawFlame
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.ShaderLayer
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.toComposePath
import dev.sebastiano.anatomy.kit.translateAlong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The collar, drawn each frame: its two slots turn over the tube's two ports, and what shows
 * through where they overlap is the open port. The tab under it points at the air scale.
 */
object BunsenCollar {
    private val d = BunsenBench

    /** The slot's centre for a collar [opening]: a full slot-width off the port when shut. */
    fun slotAngle(opening: Double): Double = d.PORT + (1 - opening) * 2 * d.SLOT_HALF

    /** A band on the collar's face from angle [from] to [to], clipped to the side that shows. */
    private fun band(from: Double, to: Double): IsoPath {
        val limit = 84 * PI / 180
        val a = max(from, d.FACING - limit)
        val b = min(to, d.FACING + limit)
        if (b - a < 1e-3) return IsoPath.Empty
        val steps = 12
        val bottom =
            (0..steps).map { d.onCylinder(d.COLLAR_R, a + (b - a) * it / steps, d.SLOT_Z0) }
        val top = (0..steps).map { d.onCylinder(d.COLLAR_R, b - (b - a) * it / steps, d.SLOT_Z1) }
        return pathOf(bottom + top, closed = true)
    }

    private fun IsoScope.tab(angle: Double) {
        val c = cos(angle)
        val s = sin(angle)
        val half = 1.8
        val ring =
            listOf(d.COLLAR_R - 0.4 to -half, 12.4 to -half, 12.4 to half, d.COLLAR_R - 0.4 to half)
                .map { (r, w) -> Floor(r * c - w * s, r * s + w * c) }
        val z = d.FLANGE_TOP + 0.4
        solid(extrude(ring, z, 3.0, d.P, true, 0.3), tone = Tone.Hi, crease = Tone.Lo, lit = true)
        line(
            lineOnTop(Floor(9.6 * c, 9.6 * s), Floor(12.4 * c, 12.4 * s), z + 3, d.P),
            tone = Tone.Lit,
            width = 0.9,
        )
    }

    private fun IsoScope.slots(opening: Double) {
        val slot = slotAngle(opening)
        val recess = band(slot - d.SLOT_HALF, slot + d.SLOT_HALF)
        if (recess.isEmpty) return
        draw.drawPath(recess.toComposePath(), palette.shades[1])
        val open = band(max(slot, d.PORT) - d.SLOT_HALF, min(slot, d.PORT) + d.SLOT_HALF)
        if (!open.isEmpty) {
            draw.drawPath(open.toComposePath(), if (palette.isDark) palette.page else palette.mid)
        }
        line(recess, tone = Tone.Mid)
    }

    private fun IsoScope.knurl(angle: Double) {
        val lines = ArrayList<IsoPath>()
        for (index in 0 until 40) {
            val a = angle + index * PI / 20
            if (cos(a - d.FACING) < 0.15) continue
            lines.add(
                pathOf(
                    listOf(
                        d.onCylinder(d.COLLAR_R, a, d.COLLAR_TOP - 3.2),
                        d.onCylinder(d.COLLAR_R, a, d.COLLAR_TOP - 0.5),
                    )
                )
            )
        }
        line(IsoPath.join(lines), tone = Tone.Lo)
    }

    /** The collar at [opening], with its tab in front of or behind it as it turns. */
    fun IsoScope.collar(opening: Double) {
        val slot = slotAngle(opening)
        val tab = slot + d.TAB_LEAD
        val near = cos(tab - d.FACING) > -0.3
        if (!near) tab(tab)
        drawArt(d.COLLAR)
        slots(opening)
        knurl(slot)
        if (near) tab(tab)
    }

    /** The whole figure in painter's order, with the flame between the back and the front art. */
    fun IsoScope.paint(opening: Double, time: Double, flame: ShaderLayer?) {
        val rise = Bunsen.flameHeight(opening)
        drawArt(d.BACK) { part ->
            if (part == "cursor") translateAlong(d.P, Point3(0.0, 0.0, rise)) else null
        }
        collar(opening)
        drawArt(d.TUBE)
        draw.drawPath(d.BORE.toComposePath(), if (palette.isDark) palette.page else palette.lo)
        line(d.BORE_BACK, tone = Tone.Lo)
        if (flame != null) drawFlame(flame, opening, time)
        drawArt(d.FRONT)
    }
}
