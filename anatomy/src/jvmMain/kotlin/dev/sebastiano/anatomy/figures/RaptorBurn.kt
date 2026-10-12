// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Settling
import dev.sebastiano.anatomy.kit.burst
import dev.sebastiano.anatomy.kit.settle
import dev.sebastiano.anatomy.kit.smooth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The firing sequence of the original's live.js: chill-down and venting, the preburners lit and
 * the pumps spinning up, main stage at 300 bar, and shutdown with its purge. Chamber pressure
 * follows its target on a spring; every glow, the plume's reach, the splash and the steam settle
 * after it, and the main-stage start and the shutdown pop fire short bursts.
 */

/** The figure's engine numbers: chamber pressure, thrust, mass. */
internal object RaptorNumbers {
    const val CHAMBER_BAR = Raptor.CHAMBER_BAR
    val BACK_TF = Raptor.BACK_TF
    /** Thrust in vacuum: sea-level thrust plus the back pressure on the 1.3 m exit. */
    val VACUUM_TF = Raptor.SEA_LEVEL_TF + BACK_TF
    const val PLUME_LENGTH = Raptor.Z_EXIT - Raptor.CONE_TIP
    const val DRY_KG = 1630

    /** Chamber pressure at a share [pc] of full, in bar. */
    fun bar(pc: Double) = pc * CHAMBER_BAR

    /** Sea-level thrust at a share [pc] of full chamber pressure, in tonnes-force. */
    fun thrust(pc: Double) = max(0.0, VACUUM_TF * pc - BACK_TF)

    /** The original's `Math.round(value).toLocaleString("en-US")`. */
    fun fmt(value: Double): String = String.format(Locale.US, "%,d", Math.round(value))
}

/** The phases the burn announces. */
internal enum class BurnPhase(val say: String) {
    None(""),
    Arming("arming"),
    ChillDown("chill-down, venting"),
    Preburners("preburners lit"),
    Main("main stage"),
    Shutdown("shutdown"),
    Cold("cold"),
}

/** This frame's values for the plume, veil, rim, deck and flash. */
internal class BurnView {
    var pc = 0.0
    var hue = 0.0
    var glow = 0.0
    var pre = 0.0
    var vent = 0.0
    var smoke = 0.0
    var reach = 0.0
    var splash = 0.0
    var flash = 0.0
    var shockR = 0.0
    var shockA = 0.0
    var ringR = 0.0
    var ringA = 0.0
    var pop = 0.0
    var popR = 0.0
    var jitterX = 0.0
    var jitterY = 0.0
    var shake = 0.0
    var rim = 0.0
    var deckGlow = 0.0
    var active = false
    var veiled = false
}

internal class RaptorBurn {
    var on = false
    var armed = false
    var tour = false
    var t = -1.0
    var stopAt = Double.POSITIVE_INFINITY
    var stopping = -1.0
    var pc = 0.0
    var pcRate = 0.0
    val hue = Settling(0.0)
    private var glowStop: Triple<Double, Double, Double>? = null
    private var lastGlow = 0.0
    private var popped = false
    var phase = BurnPhase.None
        private set

    val pre = Settling(0.0)
    val vent = Settling(0.0)
    val smoke = Settling(0.0)
    val reach = Settling(0.0)
    val splash = Settling(0.0)
    var mainAt = -99.0
    var popAt = -99.0
    val view = BurnView()

    /** The readout while burning. */
    var say = ""
        private set

    /** Lights the engine; it waits, armed, until the assemblies are home. */
    fun ignite(fromTour: Boolean) {
        if (on) return
        on = true
        armed = true
        tour = fromTour
        t = 0.0
        stopping = -1.0
        glowStop = null
        popped = false
        stopAt = if (fromTour) MAIN + 4.2 else MAIN + STEADY
    }

    fun shutdown() {
        if (!on || stopping >= 0) return
        stopping = t
    }

    /** What chamber pressure aims for now, as a share of full. */
    fun pcTarget(): Double =
        when {
            !on || armed || stopping >= 0 -> 0.0
            t >= MAIN -> 1.0
            t >= VENT -> PREBURNER_PC
            else -> 0.0
        }

    /**
     * Advances the burn by [dt] at [clock]; [home] says the assemblies are all back. Returns true
     * while anything burns or glows; [ended] is set on the frame the burn goes cold.
     */
    fun step(dt: Double, clock: Double, home: Boolean, calm: Boolean): Boolean {
        if (on && armed && home) {
            armed = false
            if (calm) t = MAIN + 0.5
        }
        val before = t
        if (on && !armed) t += dt
        if (on && stopping < 0 && t >= stopAt) stopping = t
        val lit = on && !armed && stopping < 0
        if (lit && t >= MAIN && before < MAIN && !calm) mainAt = clock
        chamber(dt, calm)
        val glow = glowOf(lit, dt, calm)
        settleGlows(lit, dt, calm)
        if (on && stopping >= 0 && pc < 0.2 && !popped && !calm) {
            popAt = clock
            popped = true
        }
        ended = false
        if (on && stopping >= 0 && pc < 0.003 && abs(pcRate) < 0.01 && t - stopping > 0.9) {
            on = false
            t = -1.0
            pc = 0.0
            pcRate = 0.0
            glowStop = null
            phase = BurnPhase.Cold
            ended = true
        }
        bursts(glow, clock, calm)
        readout()
        return on || view.active
    }

    /** True on the frame the burn went cold. */
    var ended = false
        private set

    private fun chamber(dt: Double, calm: Boolean) {
        val target = pcTarget()
        if (calm) {
            pc = target
            pcRate = 0.0
            return
        }
        val falling = stopping >= 0
        val stiff = if (falling) 16.0 else if (target > PREBURNER_PC) 20.0 else 64.0
        val drag = if (falling) 8.2 else 2 * sqrt(stiff)
        repeat(4) {
            val h = dt / 4
            pcRate += ((target - pc) * stiff - pcRate * drag) * h
            pc = (pc + pcRate * h).coerceIn(0.0, 1.0)
        }
    }

    private fun glowOf(lit: Boolean, dt: Double, calm: Boolean): Double {
        settle(hue, if (lit && pc > 0.2) 1.0 else 0.0, dt, if (lit) 0.32 else 0.5, calm)
        val onset = smooth(0.0, 0.1, pc)
        var glow = onset * (1.15 - 0.15 * hue.x)
        if (stopping >= 0 && !calm) {
            val stop =
                glowStop ?: Triple(lastGlow, max(onset, 1e-3), max(pc, 1e-3)).also { glowStop = it }
            val (g, o, p) = stop
            glow = min(g, g * min(1.0, onset / o) * sqrt(min(1.0, pc / p)))
        }
        if (!on) glow = 0.0
        lastGlow = glow
        return glow
    }

    private fun settleGlows(lit: Boolean, dt: Double, calm: Boolean) {
        settle(
            pre,
            if (lit && t > VENT && t < MAIN + 0.1) 1.0 else 0.0,
            dt,
            if (lit && t < MAIN) 0.06 else 0.12,
            calm,
        )
        settle(
            vent,
            if (lit && t < MAIN) 1.0 else 0.0,
            dt,
            if (lit && t < MAIN) 0.16 else 0.24,
            calm,
        )
        val longest = RaptorNumbers.PLUME_LENGTH + 60
        val reachTo = 72 * smooth(0.0, 0.12, pc) + (longest - 72) * smooth(0.16, 0.75, pc)
        settle(reach, reachTo, dt, if (stopping >= 0) 0.12 else 0.07, calm)
        val length = RaptorNumbers.PLUME_LENGTH
        settle(
            splash,
            smooth(0.35, 0.9, pc) * smooth(length - 10, length + 40, reach.x),
            dt,
            0.16,
            calm,
        )
        settle(smoke, if (lit && t > MAIN + 0.4) 1.0 else 0.0, dt, if (lit) 0.9 else 1.8, calm)
    }

    private fun bursts(glow: Double, clock: Double, calm: Boolean) {
        val v = view
        val sinceMain = clock - mainAt
        val sincePop = if (popAt >= 0) clock - popAt else -1.0
        v.flash = if (calm) 0.0 else burst(sinceMain, 0.08, 0.13)
        v.shockA = if (calm) 0.0 else burst(sinceMain, 0.05, 0.12)
        v.ringA = if (calm) 0.0 else burst(sinceMain - 0.08, 0.08, 0.16)
        v.pop = if (calm || sincePop < 0) 0.0 else burst(sincePop, 0.09, 0.2)
        v.shake = if (calm) 0.0 else 3.2 * burst(sinceMain, 0.05, 0.12)
        val hum = if (calm) 0.0 else 0.32 * pc * smooth(0.02, 0.12, pc)
        v.jitterX = sin(clock * 91) * hum + sin(clock * 53) * v.shake
        v.jitterY = cos(clock * 77) * hum * 0.7 + cos(clock * 61) * v.shake
        v.pc = pc
        v.hue = hue.x
        v.glow = glow
        v.pre = pre.x
        v.vent = vent.x
        v.smoke = smoke.x
        v.reach = reach.x
        v.splash = splash.x
        v.shockR = 26 + 900 * max(0.0, sinceMain)
        v.ringR = 600 * max(0.0, sinceMain - 0.08)
        v.popR = 24 + 70 * (1 - exp(-max(0.0, sincePop) / 0.25))
        v.active =
            pc > 1e-3 ||
                pre.x > 1e-3 ||
                vent.x > 1e-3 ||
                smoke.x > 1e-3 ||
                v.flash > 1e-3 ||
                v.shockA > 1e-3 ||
                v.ringA > 1e-3 ||
                v.pop > 1e-3 ||
                (sinceMain > 0 && sinceMain < 0.6)
        v.veiled = smoke.x > 1e-3 || vent.x > 1e-3 || v.shockA > 1e-3
        val preLit = pre.x * smooth(0.0, 0.05, pc)
        v.rim = (pc * 0.85 + preLit * 0.45 + v.flash * 0.6 + v.pop * 0.7).coerceIn(0.0, 1.0)
        v.deckGlow =
            (pc * 0.9 * splash.x + v.flash * 0.5 + preLit * 0.12 + v.pop * 0.25).coerceIn(0.0, 1.0)
    }

    private fun readout() {
        if (!on) return
        val bar = RaptorNumbers.fmt(RaptorNumbers.bar(pc))
        val (text, next) =
            when {
                armed -> "closing up · arming" to BurnPhase.Arming
                stopping >= 0 ->
                    (if (pc > 0.01) "shutdown · $bar bar" else "shutdown · purging") to
                        BurnPhase.Shutdown
                t < VENT -> "chill-down · venting" to BurnPhase.ChillDown
                t < MAIN -> "spin-up · preburners lit · $bar bar" to BurnPhase.Preburners
                else ->
                    "Pc $bar bar · ${RaptorNumbers.fmt(RaptorNumbers.thrust(pc))} tf" to
                        BurnPhase.Main
            }
        say = text
        phase = next
    }

    companion object {
        /** Chill-down and venting, then the preburners light. */
        const val VENT = 0.7
        /** Main stage. */
        const val MAIN = 1.25
        /** How long a burn holds main stage. */
        const val STEADY = 9.0
        /** Chamber pressure while only the preburners burn. */
        const val PREBURNER_PC = 0.13
    }
}
