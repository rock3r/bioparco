// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Palette
import kotlin.math.floor

/** The CSS lamp and cable ramps, cached rather than allocated for every item on every frame. */
internal class ArcadePalettes(private val base: Palette) {
    private val hot =
        base.copy(
            shades = List(4) { base.lit },
            top = base.lit,
            hi = base.lit,
            mid = base.lit,
            lo = base.lit,
        )
    private val tube = base.copy(top = base.litTop, shades = List(4) { base.litTop }, hi = base.lit)
    private val plexi = base.copy(top = base.litShade, shades = List(4) { base.litShade })
    private val lamp =
        base.copy(top = base.litTop, shades = List(4) { base.litShade }, hi = base.lit)
    private val unlitButton = base.copy(shades = List(4) { base.shades[2] })
    private val ledOn = base.copy(dot = base.lit)
    private val ledOff = base.copy(dot = base.dot.copy(alpha = 0.18f))

    fun of(name: String, m: ArcadeMachine, pulsing: Boolean): Palette =
        when {
            pulsing -> hot
            name == "pcb.led" -> if (m.power) ledOn else ledOff
            name == "marquee.tube" && marqueeLevel(m) > 0.5 -> tube
            name == "marquee.plexi" && marqueeLevel(m) > 0.5 -> plexi
            name == "button.p1" && m.power ->
                if (m.credits > 0 && floor(m.clock / 0.5).toInt() % 2 == 0) lamp else unlitButton
            name == "button.p2" && m.power -> unlitButton
            (name == "button.fire" || name == "button.jump") && m.power -> lamp
            else -> base
        }
}
