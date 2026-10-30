package dev.sebastiano.tabswitch

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Everything that moves in the bar, stepped on the frame clock. All lengths are in the units of
 * [layout], measured from the inside of the bar's padding.
 *
 * - The pill's two edges each chase the selected tab on a critically damped spring. The trailing
 *   edge leaves at once on a stiff spring; the leading edge waits [LEAD_DELAY] and follows on a
 *   softer one. So the pill pinches in on its way across and stretches out on arrival, like an
 *   inchworm, but is never narrower than [minPillWidth].
 * - Every tab opens or closes on the same spring, so the tabs always fill the bar between them.
 * - The old label fades out at once; the new one waits until the pill covers most of its tab.
 * - A hovered tab lights up, and the pill leans [LEAN] towards it.
 *
 * Every position is critically damped, so nothing overshoots, and a step longer than [MAX_STEP] is
 * cut short, so a stalled frame never flings the pill.
 */
class TabMotion(val layout: TabBarLayout, private val minPillWidth: Float, initial: Int = 0) {
    private val count = layout.count

    var selected: Int = initial
        private set

    var hovered: Int? = null
        private set

    /** How open each tab is, from 0 to 1. They add up to one. */
    val expansion = FloatArray(count) { if (it == initial) 1f else 0f }

    /** How much of each tab's label shows, from 0 to 1. */
    val labelAlpha = FloatArray(count) { if (it == initial) 1f else 0f }

    /** How lit up each tab's icon is by the pointer, from 0 to 1. */
    val highlight = FloatArray(count)

    /** Each tab's ends right now, as `left0, right0, left1, right1, …`. */
    val spans = FloatArray(2 * count)

    val pillLeft: Float
        get() = left.value

    val pillRight: Float
        get() = right.value

    private val left = Spring(layout.settledLeft(initial), TRAIL_OMEGA, EDGE_PRECISION)
    private val right = Spring(layout.settledRight(initial), TRAIL_OMEGA, EDGE_PRECISION)
    private val opening = Array(count) { Spring(expansion[it], OPEN_OMEGA, OPENING_PRECISION) }
    private val lights = Array(count) { Spring(0f, LIGHT_OMEGA, LIGHT_PRECISION) }

    /** Which tab each edge is heading for; the leading edge keeps its old goal for a moment. */
    private var leftGoal = initial
    private var rightGoal = initial
    private var leadFrom = 0L
    private var leadTo = initial

    /** +1 while the pill heads right, -1 left, 0 before any switch. */
    private var travel = 0

    private var last = Long.MIN_VALUE

    init {
        layout.spans(expansion, spans)
    }

    /**
     * Picks [tab] at [nanos] on the frame clock. Returns whether that changed anything: picking the
     * selected tab does nothing.
     */
    fun select(tab: Int, nanos: Long): Boolean {
        require(tab in 0 until count) { "No tab $tab" }
        if (tab == selected) return false
        val centre = (left.value + right.value) / 2f
        val goal = (layout.settledLeft(tab) + layout.settledRight(tab)) / 2f
        travel = if (goal > centre) 1 else -1
        selected = tab
        if (last == Long.MIN_VALUE) last = nanos
        val (trailing, leading) = if (travel > 0) left to right else right to left
        trailing.omega = TRAIL_OMEGA
        leading.omega = LEAD_OMEGA
        if (travel > 0) leftGoal = tab else rightGoal = tab
        leadFrom = nanos + (LEAD_DELAY * NANOS).toLong()
        leadTo = tab
        for (i in 0 until count) opening[i].target = if (i == tab) 1f else 0f
        return true
    }

    /** The pointer is over [tab], or over no tab at all. */
    fun hover(tab: Int?) {
        hovered = tab
    }

    /** Moves everything on to [nanos]. Returns whether anything is still moving. */
    fun step(nanos: Long): Boolean {
        val dt = if (last == Long.MIN_VALUE) 0f else min((nanos - last) / NANOS, MAX_STEP)
        last = nanos
        if (nanos >= leadFrom) {
            if (travel > 0) rightGoal = leadTo else if (travel < 0) leftGoal = leadTo
        }
        aimEdges()
        left.step(dt)
        right.step(dt)
        keepPillRound()

        var moving = !left.settled || !right.settled || leftGoal != rightGoal
        for (tab in 0 until count) {
            val spring = opening[tab]
            spring.step(dt)
            expansion[tab] = spring.value
            lights[tab].target = if (tab == hovered && tab != selected) 1f else 0f
            lights[tab].step(dt)
            highlight[tab] = lights[tab].value
            moving = moving || !spring.settled || !lights[tab].settled
            moving = stepLabel(tab, dt) || moving
        }
        layout.spans(expansion, spans)
        return moving
    }

    /** Each edge heads for its goal tab's end, pushed out by the lean if that tab is selected. */
    private fun aimEdges() {
        val lean =
            when (val over = hovered) {
                null,
                selected -> 0f
                else -> if (over > selected) LEAN else -LEAN
            }
        left.target = layout.settledLeft(leftGoal) + if (leftGoal == selected) min(lean, 0f) else 0f
        right.target =
            layout.settledRight(rightGoal) + if (rightGoal == selected) max(lean, 0f) else 0f
    }

    /** The trailing edge is held back so the pill is never narrower than [minPillWidth]. */
    private fun keepPillRound() {
        if (right.value - left.value >= minPillWidth) return
        if (travel >= 0) {
            left.value = right.value - minPillWidth
            left.velocity = min(left.velocity, right.velocity)
        } else {
            right.value = left.value + minPillWidth
            right.velocity = max(right.velocity, left.velocity)
        }
    }

    /** Fades [tab]'s label; returns whether it is still fading. */
    private fun stepLabel(tab: Int, dt: Float): Boolean {
        val alpha = labelAlpha[tab]
        labelAlpha[tab] =
            if (tab == selected && cover(tab) >= LABEL_COVER) min(1f, alpha + dt / LABEL_IN)
            else max(0f, alpha - dt / LABEL_OUT)
        return labelAlpha[tab] != alpha || (tab == selected && labelAlpha[tab] < 1f)
    }

    /** How much of [tab]'s settled span the pill covers, from 0 to 1. */
    private fun cover(tab: Int): Float {
        val start = layout.settledLeft(tab)
        val end = layout.settledRight(tab)
        return (min(right.value, end) - max(left.value, start)).coerceAtLeast(0f) / (end - start)
    }

    /**
     * A critically damped spring with natural frequency [omega], stepped in small sub-steps. It
     * snaps to its target once within [precision] of it and slower than ten [precision]s a second.
     * It integrates in doubles: near rest, a sub-step moves a pill edge less than a float resolves.
     */
    private class Spring(value: Float, var omega: Float, private val precision: Float) {
        private var x = value.toDouble()
        private var v = 0.0
        var target = value

        var value: Float
            get() = x.toFloat()
            set(new) {
                x = new.toDouble()
            }

        var velocity: Float
            get() = v.toFloat()
            set(new) {
                v = new.toDouble()
            }

        val settled: Boolean
            get() = abs(x - target) < precision && abs(v) < precision * SETTLE_RATE

        fun step(dt: Float) {
            if (dt <= 0f) return
            val steps = ceil(dt / SUB_STEP).toInt()
            val h = dt.toDouble() / steps
            val w = omega.toDouble()
            repeat(steps) {
                v += (w * w * (target - x) - 2.0 * w * v) * h
                x += v * h
            }
            if (settled) {
                x = target.toDouble()
                v = 0.0
            }
        }
    }

    companion object {
        /** How far the pill leans towards a hovered tab. */
        const val LEAN = 8f

        /** How long the leading edge waits before following the trailing one, in seconds. */
        const val LEAD_DELAY = 0.085f

        // Natural frequencies, in radians per second, fitted to the source video at 60 fps: the
        // trailing edge covers 95% of its way in 0.2 s, the leading edge in 0.26 s after its wait.
        private const val TRAIL_OMEGA = 24f
        private const val LEAD_OMEGA = 18f
        private const val OPEN_OMEGA = 22f
        private const val LIGHT_OMEGA = 26f

        /**
         * Label fades, in seconds: out at once, and in once the pill covers [LABEL_COVER] of the
         * tab, which for a neighbour is about 0.27 s after the click, as in the source video.
         */
        private const val LABEL_OUT = 0.07f
        private const val LABEL_IN = 0.12f
        private const val LABEL_COVER = 0.92f

        private const val MAX_STEP = 1f / 30f
        private const val SUB_STEP = 1f / 1000f

        /**
         * How close each spring must come before it snaps: a hundredth of a dp for the pill, but
         * finer for the tabs' openings, which must snap together to keep filling the bar.
         */
        private const val EDGE_PRECISION = 0.01f
        private const val OPENING_PRECISION = 1e-4f
        private const val LIGHT_PRECISION = 1e-3f
        private const val SETTLE_RATE = 10f
        private const val NANOS = 1e9f
    }
}
