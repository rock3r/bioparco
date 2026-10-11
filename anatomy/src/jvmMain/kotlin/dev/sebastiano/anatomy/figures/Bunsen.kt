package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.smooth
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A Bunsen burner on methane, as a model: the air collar's opening sets how much primary air the
 * gas jet draws in, and that air-to-gas ratio decides the flame's shape, colour and temperature.
 *
 * - Methane needs 9.52 volumes of air per volume to burn completely. Fully open, the collar admits
 *   three quarters of that (75% primary aeration, an equivalence ratio of 1.33); the ports are the
 *   restriction, so the air drawn is in proportion to their open area.
 * - The flame's length is Roper's correlation for a laminar jet flame from a round port, `L =
 *   1330·Q / ln(1 + 1/S)`, with `Q` the volume of gas plus primary air through the port and `S` the
 *   volumes of air still needed per volume of that mixture. Primary air shortens the flame.
 * - The inner cone is Gouy's: a premixed front standing where the burning velocity balances the
 *   flow, `h = R·sqrt((U/S_L)² − 1)`, with laminar burning velocities of methane in air. Richer
 *   than about 1.6 the front is held up by the outer flame and stands nearly as tall as it.
 * - Below about 40% primary aeration the fuel cracks to soot, which glows yellow (a luminous
 *   diffusion flame, about 1,000 °C); a blue premixed flame burns about 1,500 °C just above the tip
 *   of its inner cone.
 */
object Bunsen {
    /** Volumes of air per volume of methane for complete combustion. */
    const val STOICHIOMETRIC_AIR = 9.52

    /** The share of [STOICHIOMETRIC_AIR] the fully open collar draws in. */
    const val OPEN_AERATION = 0.75

    /** The gas tap's setting: 20 litres of methane an hour, about 0.2 kW. */
    const val GAS_LITRES_PER_HOUR = 20.0

    /** The tube's bore, in mm. */
    const val BORE_MM = 10.0

    private const val ROPER = 1330.0
    private const val GAS = GAS_LITRES_PER_HOUR / 1000 / 3600
    private val PORT = PI * (BORE_MM / 2000) * (BORE_MM / 2000)

    /** Laminar burning velocity of methane in air (m/s) by equivalence ratio, 1 atm and 25 °C. */
    private val BURNING =
        listOf(
            1.0 to 0.36,
            1.1 to 0.38,
            1.2 to 0.32,
            1.3 to 0.24,
            1.4 to 0.15,
            1.5 to 0.08,
            1.6 to 0.04,
            1.7 to 0.0,
        )

    /** Primary air drawn in per volume of gas, with the collar [opening] from 0 to 1. */
    fun airToGas(opening: Double): Double =
        OPEN_AERATION * STOICHIOMETRIC_AIR * opening.coerceIn(0.0, 1.0)

    /** Fuel over air, relative to stoichiometric: infinite with the collar shut. */
    fun equivalenceRatio(opening: Double): Double =
        airToGas(opening).let {
            if (it == 0.0) Double.POSITIVE_INFINITY else STOICHIOMETRIC_AIR / it
        }

    /** How tall the flame stands above the rim, in mm. */
    fun flameHeight(opening: Double): Double {
        val air = airToGas(opening)
        val flow = GAS * (1 + air)
        val still = (STOICHIOMETRIC_AIR - air) / (1 + air)
        return ROPER * flow / ln(1 + 1 / still) * 1000
    }

    /** The mixture's speed up the tube, in m/s. */
    fun speed(opening: Double): Double = GAS * (1 + airToGas(opening)) / PORT

    /** The laminar burning velocity at equivalence ratio [phi], in m/s. */
    fun burningVelocity(phi: Double): Double {
        if (phi <= BURNING.first().first) return BURNING.first().second
        for ((a, b) in BURNING.zipWithNext()) {
            if (phi <= b.first) {
                return a.second + (b.second - a.second) * (phi - a.first) / (b.first - a.first)
            }
        }
        return 0.0
    }

    /** The inner cone's height above the rim, in mm. */
    fun coneHeight(opening: Double): Double {
        val cap = CONE_CAP * flameHeight(opening)
        val burning = burningVelocity(equivalenceRatio(opening))
        if (burning <= 0) return cap
        val ratio = speed(opening) / burning
        val gouy = BORE_MM / 2 * sqrt((ratio * ratio - 1).coerceAtLeast(0.0))
        return minOf(gouy, cap)
    }

    /** How strongly soot glows yellow: 1 with the collar shut, 0 past 50% primary aeration. */
    fun sootiness(opening: Double): Double = 1 - smooth(0.2, 0.5, aeration(opening))

    /**
     * How clearly the inner blue cone stands: it sharpens from an equivalence ratio of 2.3 to 1.45.
     */
    fun coneStrength(opening: Double): Double {
        val phi = equivalenceRatio(opening)
        return if (phi.isInfinite()) 0.0 else smooth(0.0, 1.0, (2.3 - phi) / 0.85)
    }

    /** The hottest point of the flame, in °C: 1,000 luminous, 1,500 with the collar open. */
    fun hottest(opening: Double): Double = 1_000.0 + 500.0 * aeration(opening) / OPEN_AERATION

    /** Primary air as a share of what the gas needs. */
    fun aeration(opening: Double): Double = airToGas(opening) / STOICHIOMETRIC_AIR

    fun phaseOf(opening: Double): String =
        when {
            sootiness(opening) >= 0.5 -> "yellow flame"
            sootiness(opening) > 0.05 -> "yellow tips"
            coneStrength(opening) >= 0.5 -> "blue cone"
            else -> "blue flame"
        }

    fun readoutOf(opening: Double): String {
        val percent = (opening.coerceIn(0.0, 1.0) * 100).roundToInt()
        val degrees = (hottest(opening) / 10).roundToInt() * 10
        val grouped = "%,d".format(java.util.Locale.ROOT, degrees)
        return "collar $percent% open · ${phaseOf(opening)} · $grouped °C"
    }

    private const val CONE_CAP = 0.85
}
