// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import androidx.compose.ui.graphics.Color
import dev.sebastiano.anatomy.kit.Anchor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Tone

/*
 * What a plane draws: the original passes SVG markup (`<path class="iso-line">`, `<text>`,
 * `<circle>`) that the runtime places with one matrix. Here it is a small list of marks in the
 * plane's own `u`, `v` units, placed and faded the same way.
 */

/** One mark on a plane. */
sealed class PlaneMark

/** A hairline in plane units: ticks, rings, rules. [free] ends may stop on a bare face. */
class PlaneLine(val path: IsoPath, val tone: Tone = Tone.Lo, val free: Free = Free.None) :
    PlaneMark()

/**
 * Engraved text: `translate(x y) rotate(degrees) translate(0 dy)` in plane units, [size] units
 * tall, anchored on its baseline like SVG's `text-anchor`.
 */
class PlaneText(
    val text: String,
    val x: Double,
    val y: Double,
    val rotate: Double,
    val dy: Double,
    val size: Double,
    val tone: Tone = Tone.Mid,
    val anchor: Anchor = Anchor.Middle,
) : PlaneMark()

/** A filled disc of radius [r] round the plane's origin: a billboard's lens or glow. */
class PlaneDisc(val r: Double, val alpha: Float = 1f, val fill: (Palette) -> Color) : PlaneMark()

/** The marks of one plane, painted in order. */
class PlaneArt(val marks: List<PlaneMark>) {
    companion object {
        val Empty = PlaneArt(emptyList())
    }
}
