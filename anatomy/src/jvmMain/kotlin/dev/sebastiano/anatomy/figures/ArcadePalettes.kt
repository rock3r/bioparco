// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.graphics.lerp
import dev.sebastiano.anatomy.kit.Palette
import kotlin.math.floor

/** The CSS lamp and cable ramps, cached rather than allocated for every item on every frame. */
internal class ArcadePalettes(private val base: Palette) {
    private val hot =
        base.copy(
            shades = List(4) { base.lit },
            top = base.lit,
        )
    private val tube = base.copy(top = base.litTop, shades = List(4) { base.litTop }, hi = base.lit)
    private val plexi = base.copy(top = base.litShade, shades = List(4) { base.litShade })
    private val lamp =
        base.copy(top = base.litTop, shades = List(4) { base.litShade }, hi = base.lit)
    private val unlitButton = base.copy(shades = List(4) { base.shades[2] })
    private val pressed = base.copy(hi = base.lit)
    private val glassFade = ArcadeFade(0.6)
    private val marqueeFade = ArcadeFade(0.2)
    private val ledFade = ArcadeFade(0.3)
    private var led = base
    var glassInk = base.shades[0]
        private set

    var marqueeInk = base.shades[3]
        private set

    fun tick(m: ArcadeMachine, p: ArcadePicture) {
        glassInk =
            lerp(
                base.shades[0],
                base.litShade,
                glassFade.value(m.clock, p.bright > 0.05 && p.sx > 0, m.calm),
            )
        marqueeInk =
            lerp(
                base.shades[3],
                base.lit,
                marqueeFade.value(m.clock, marqueeLevel(m) > 0.5, m.calm),
            )
        val opacity = 0.18f + 0.82f * ledFade.value(m.clock, m.power, m.calm)
        led = base.copy(dot = (if (m.power) base.lit else base.dot).copy(alpha = opacity))
    }

    fun of(name: String, m: ArcadeMachine, pulsing: Boolean): Palette =
        when {
            pulsing -> hot
            name == "pcb.led" -> led
            name == "marquee.tube" && marqueeLevel(m) > 0.5 -> tube
            name == "marquee.plexi" && marqueeLevel(m) > 0.5 -> plexi
            name == "button.p1" && m.power ->
                if (m.credits > 0 && floor(m.clock / 0.5).toInt() % 2 == 0) lamp else unlitButton
            name == "button.p2" && m.power -> unlitButton
            name == "door.slot" && m.power -> lamp
            name == "button.fire" && m.fire > 0.5 -> pressed
            else -> base
        }
}
