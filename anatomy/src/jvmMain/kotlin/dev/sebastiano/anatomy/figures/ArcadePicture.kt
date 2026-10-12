// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import java.util.Locale

internal class ArcadePicture(
    val mode: String,
    val t: Double,
    val bright: Double,
    val sx: Double = 1.0,
    val sy: Double = 1.0,
    val dot: Double = 0.0,
    val busy: Boolean,
)

internal fun arcadePicture(m: ArcadeMachine): ArcadePicture {
    val t = m.clock - m.powerAt
    val off = m.clock - m.offAt
    if (m.power) {
        val mode =
            if (t < 3.6) {
                if (t > 0.9) "test" else "none"
            } else if (t < 3.68) "none" else "attract"
        return ArcadePicture(mode, t, smooth(0.9, 2.6, t), busy = t < 3.8)
    }
    if (off >= 1.2 || m.offAt <= 0) return ArcadePicture("none", t, 0.0, busy = false)
    val sx = if (off < 0.09) 1 - 0.99 * smooth(0.0, 0.09, off) else if (off < 0.26) 0.01 else 0.0
    val sy = if (off < 0.09) 1.0 else if (off < 0.26) 1 - smooth(0.09, 0.26, off) else 0.0
    return ArcadePicture(
        m.lastMode,
        t,
        1.0,
        sx,
        sy,
        if (off < 0.2) 0.0 else 1 - smooth(0.26, 1.1, off),
        true,
    )
}

internal fun marqueeLevel(m: ArcadeMachine): Double {
    val t = m.clock - m.powerAt
    return when {
        !m.power || t < 0.06 -> 0.0
        t < 0.12 -> 1.0
        t < 0.24 -> 0.0
        t < 0.3 -> 1.0
        t < 0.42 -> 0.2
        else -> 1.0
    }
}

internal fun arcadeSay(m: ArcadeMachine, p: ArcadePicture): String {
    val coin = m.coins.firstOrNull { !it.landed }
    val text =
        when {
            coin != null -> coinSay(m, coin)
            !m.power ->
                if (m.clock - m.offAt < 1.2 && m.offAt > 0) "off · picture folds to a line"
                else "off · ${if (m.pile > 0) "insert another coin" else "insert coin"}"
            p.mode == "test" ->
                when {
                    p.t < 1.7 -> "self-test"
                    p.t < 2.3 -> "self-test · RAM OK"
                    else -> "self-test · RAM OK · ROM OK"
                }
            p.mode != "attract" ->
                "warming · heater ${String.format(Locale.ROOT, "%.1f", minOf(2.2, p.t))} s"
            else -> "credit ${m.credits} · 15.734 kHz" + controlSay(m)
        }
    val now = if (m.power) p.mode else "off"
    if (now != m.spokenMode) {
        m.spokenMode = now
        if (now == "attract")
            m.status = "Attract mode, ${m.credits} credit${if (m.credits == 1) "" else "s"}."
        if (now == "test") m.status = "Self-test."
    }
    return text
}

private fun controlSay(m: ArcadeMachine) =
    when {
        m.target < 0 -> " · stick ←"
        m.target > 0 -> " · stick →"
        m.fireHeld > 0 -> " · fire"
        else -> ""
    }

private fun coinSay(m: ArcadeMachine, c: ArcadeCoin) =
    when {
        c.t < 0.9 -> "coin · 24.26 mm quarter"
        c.t < 1.24 -> "coin · into the slot"
        c.t < 1.66 -> "mech · checking the coin"
        c.switched -> "coin switch · meter ${m.meter.toString().padStart(6, '0')}"
        else -> "coin · rolling down the chute"
    }
