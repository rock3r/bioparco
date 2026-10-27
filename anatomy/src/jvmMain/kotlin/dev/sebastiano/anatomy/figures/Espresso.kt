package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.smooth
import java.util.Locale
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A heat-exchanger espresso machine, as a model.
 *
 * - The boiler holds water and steam at 1.0 to 1.2 bar, switched by a pressurestat, so it boils at
 *   122 to 125 °C (the saturation temperature: NIST's Antoine fit for water above 106 °C).
 * - Brew water never mixes with it: the pump pushes fresh water from the tank through a tube that
 *   runs through the boiler, the heat exchanger, and on to the group.
 * - The pump is a vibration pump like Ulka's EX5: about 600 ml a minute open, about 260 against 9
 *   bar, nothing past 16 bar dead-headed. An over-pressure valve (OPV) set to 9 bar returns
 *   whatever the puck doesn't take to the tank.
 * - The E61 group's pre-infusion chamber lets the pressure build over a few seconds; the first
 *   drops come once the puck is soaked, and a double shot of 18 g of coffee is stopped at 36 g.
 */
object Espresso {
    /** The OPV's setting: the brew pressure, in bar. */
    const val BREW_BAR = 9.0

    /** The pump's flow with nothing to push against, in ml a minute. */
    const val PUMP_OPEN_ML_MIN = 600.0

    /** The pressure the pump stalls at, in bar. */
    const val PUMP_DEAD_BAR = 16.0

    /** The coffee in the basket and the drink it is stopped at, in grams. */
    const val DOSE_G = 18.0
    const val TARGET_G = 36.0

    /** When the first drops leave the spouts, after the lever is raised. */
    const val FIRST_DROPS_S = 6.0

    /** The pressurestat's band, in bar. */
    const val CUT_IN_BAR = 1.0
    const val CUT_OUT_BAR = 1.2

    /** How fast the pressure builds through the pre-infusion chamber: its time constant. */
    private const val BUILD_S = 2.0

    /** The flow through the puck at full pressure, in g (ml) a second. */
    private const val PUCK_G_S = 1.7

    private const val ATMOSPHERE_BAR = 1.01325

    /** The boiling point of water at [gaugeBar] above the atmosphere, in °C. */
    fun saturationC(gaugeBar: Double): Double {
        val p = gaugeBar + ATMOSPHERE_BAR
        return 643.748 / (3.55959 - log10(p)) + 198.043 - 273.15
    }

    /** The pump's flow against [bar], in ml a second: a straight line from open to dead-head. */
    fun pumpFlow(bar: Double): Double =
        PUMP_OPEN_ML_MIN / 60 * (1 - bar.coerceIn(0.0, PUMP_DEAD_BAR) / PUMP_DEAD_BAR)

    /** The pressure on the puck [t] seconds after the lever went up, in bar. */
    fun brewPressure(t: Double): Double = if (t <= 0) 0.0 else BREW_BAR * (1 - exp(-t / BUILD_S))

    /** The flow out of the spouts at [t], in g a second: in proportion to the pressure. */
    fun puckFlow(t: Double): Double =
        if (t < FIRST_DROPS_S) 0.0 else PUCK_G_S * brewPressure(t) / BREW_BAR

    /** The coffee in the cup at [t], in grams: the integral of [puckFlow]. */
    fun yieldAt(t: Double): Double {
        if (t <= FIRST_DROPS_S) return 0.0
        val tail = BUILD_S * (exp(-t / BUILD_S) - exp(-FIRST_DROPS_S / BUILD_S))
        return PUCK_G_S * (t - FIRST_DROPS_S + tail)
    }

    /** When the cup reaches [grams]. */
    fun shotTime(grams: Double = TARGET_G): Double {
        var lo = FIRST_DROPS_S
        var hi = FIRST_DROPS_S + 10 * grams / PUCK_G_S
        repeat(60) {
            val mid = (lo + hi) / 2
            if (yieldAt(mid) < grams) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** What the OPV sends back to the tank at [t], in ml a second: it opens near 9 bar. */
    fun bypassFlow(t: Double): Double {
        val p = brewPressure(t)
        return max(0.0, pumpFlow(p) - puckFlow(t)) * smooth(0.88, 0.99, p / BREW_BAR)
    }

    /** What goes on into the heat exchanger and the group at [t], in ml a second. */
    fun brewFlow(t: Double): Double = if (t <= 0) 0.0 else pumpFlow(brewPressure(t)) - bypassFlow(t)

    fun readoutOf(shot: Shot, boilerBar: Double): String {
        val boiler = one(boilerBar)
        return when {
            shot.running ->
                "shot ${whole(shot.time)} s · ${one(shot.pressure)} bar · ${whole(shot.grams)} g"
            shot.time > 0 ->
                "${shot.grams.roundToInt()} g in ${whole(shot.time)} s · boiler $boiler bar"
            else -> "ready · boiler $boiler bar · ${saturationC(boilerBar).roundToInt()} °C"
        }
    }

    private fun one(value: Double) = "%.1f".format(Locale.ROOT, value)

    private fun whole(value: Double) = floor(value + 1e-6).toInt()
}

/** One shot: the lever goes up, the cup fills, and it stops at the target or when pulled again. */
class Shot {
    var running = false
        private set

    /** Seconds since the lever went up, frozen when it comes down. */
    var time = 0.0
        private set

    var grams = 0.0
        private set

    /** Seconds since the lever came down: the three-way valve vents the group. */
    var vented = 0.0
        private set

    /** The pressure the brew gauge reads now, in bar. */
    val pressure: Double
        get() =
            if (running) Espresso.brewPressure(time)
            else Espresso.brewPressure(time) * exp(-vented / VENT_S)

    /** Raises the lever, or lowers it if it is up. */
    fun pull() {
        if (running) {
            running = false
            vented = 0.0
        } else {
            running = true
            time = 0.0
            grams = 0.0
        }
    }

    fun advance(dt: Double) {
        if (!running) {
            vented += dt
            return
        }
        time += dt
        grams = Espresso.yieldAt(time)
        if (grams >= Espresso.TARGET_G) {
            time = Espresso.shotTime()
            grams = Espresso.TARGET_G
            running = false
            vented = 0.0
        }
    }

    /** Finishes venting at once, for reduced motion: a stopped shot's gauge reads zero. */
    fun settle() {
        if (!running) vented = maxOf(vented, VENTED_S)
    }

    private companion object {
        const val VENT_S = 0.3

        /** Long enough that the vented pressure is well under a hundredth of a bar. */
        const val VENTED_S = VENT_S * 12
    }
}

/** The boiler's pressure switch: heats below cut-in, stops above cut-out, holds in between. */
class Pressurestat(
    private val cutIn: Double = Espresso.CUT_IN_BAR,
    private val cutOut: Double = Espresso.CUT_OUT_BAR,
) {
    var heating = true
        private set

    fun update(bar: Double): Boolean {
        if (bar < cutIn) heating = true
        if (bar > cutOut) heating = false
        return heating
    }
}
