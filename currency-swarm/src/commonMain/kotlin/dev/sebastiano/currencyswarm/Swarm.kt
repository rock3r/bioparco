package dev.sebastiano.currencyswarm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * [count] dots that fly from one shape to the next. Shapes are `x, y` pairs in glyph units: a
 * glyph's cap height is 1, centred on the origin. The stage scales them to pixels when it draws.
 *
 * Each dot follows its own eased flight, with a small delay, a sideways bow and a puff of scatter
 * that peaks halfway, so a shape dissolves and reforms rather than sliding over. On top of that
 * every dot wobbles a little all the time, and the pointer pushes dots aside on a spring.
 *
 * All the state is in flat arrays and [step] allocates nothing, so a frame costs a few float
 * operations per dot.
 */
class Swarm(val count: Int, seed: Int = 7) {
    /** Where each dot is drawn, as of the last [step]. */
    val x = FloatArray(count)
    val y = FloatArray(count)

    /** Each dot's brightness tier, `0 until TIERS`, from dim and small to bright and big. */
    val tier = IntArray(count)

    /** How many dots each tier holds. */
    val tierSizes = IntArray(TIERS)

    internal val fromX = FloatArray(count)
    internal val fromY = FloatArray(count)
    internal val toX = FloatArray(count)
    internal val toY = FloatArray(count)

    private val random = Random(seed)
    private val delay = FloatArray(count)
    private val bow = FloatArray(count)
    private val scatterX = FloatArray(count)
    private val scatterY = FloatArray(count)
    private val wobbleSpeedX = FloatArray(count)
    private val wobbleSpeedY = FloatArray(count)
    private val wobblePhaseX = FloatArray(count)
    private val wobblePhaseY = FloatArray(count)
    private val pushX = FloatArray(count)
    private val pushY = FloatArray(count)
    private val pushVelocityX = FloatArray(count)
    private val pushVelocityY = FloatArray(count)
    private val scratch = FloatArray(count * 2)

    private var flightStart = Float.NEGATIVE_INFINITY
    private var lastStep = Float.NaN

    init {
        for (i in 0 until count) {
            val roll = random.nextFloat()
            tier[i] =
                when {
                    roll < DIM_SHARE -> 0
                    roll < DIM_SHARE + MID_SHARE -> 1
                    else -> 2
                }
            tierSizes[tier[i]]++
            wobbleSpeedX[i] = TWO_PI * (WOBBLE_HZ_MIN + random.nextFloat() * WOBBLE_HZ_SPREAD)
            wobbleSpeedY[i] = TWO_PI * (WOBBLE_HZ_MIN + random.nextFloat() * WOBBLE_HZ_SPREAD)
            wobblePhaseX[i] = random.nextFloat() * TWO_PI
            wobblePhaseY[i] = random.nextFloat() * TWO_PI
        }
        rollFlights(keepInFlight = false, now = 0f)
    }

    /** Puts every dot at rest on [points], with no flight. */
    fun place(points: FloatArray) {
        require(points.size == count * 2) { "Need ${count * 2} coordinates, got ${points.size}" }
        for (i in 0 until count) {
            fromX[i] = points[i * 2]
            fromY[i] = points[i * 2 + 1]
            toX[i] = fromX[i]
            toY[i] = fromY[i]
        }
        flightStart = Float.NEGATIVE_INFINITY
    }

    /** Where every dot is drawn now, as `x, y` pairs: what [pairAlongCurve] needs to pair them. */
    fun snapshot(): FloatArray = FloatArray(count * 2) { if (it % 2 == 0) x[it / 2] else y[it / 2] }

    /**
     * Sends the dots to [targets], starting [now], from wherever their flights have them at [now]:
     * a new shape mid-flight bends every path without a jump. [pairs] says which target each dot
     * takes; by default they are paired along the curve here and now. A caller can pair them ahead
     * of time, on another thread, from a [snapshot]: dots move a pixel or two in the meantime,
     * which makes no visible difference to who goes where.
     */
    fun morphTo(targets: FloatArray, now: Float, pairs: IntArray? = null) {
        require(targets.size == count * 2) { "Need ${count * 2} coordinates, got ${targets.size}" }
        for (i in 0 until count) {
            scratch[i * 2] = flightX(i, now)
            scratch[i * 2 + 1] = flightY(i, now)
        }
        val inFlight = !isSettled(now)
        val pairs = pairs ?: pairAlongCurve(scratch, targets)
        for (i in 0 until count) {
            fromX[i] = scratch[i * 2]
            fromY[i] = scratch[i * 2 + 1]
            toX[i] = targets[pairs[i] * 2]
            toY[i] = targets[pairs[i] * 2 + 1]
        }
        rollFlights(keepInFlight = inFlight, now = now)
        flightStart = now
    }

    /** Whether every flight has landed by [now]. */
    fun isSettled(now: Float): Boolean = now - flightStart >= LONGEST_FLIGHT

    /**
     * Moves every dot to where it is at [now] (seconds, on any steady clock). [pointer] is in glyph
     * units, or [Offset.Unspecified] when it is not over the stage.
     */
    fun step(now: Float, pointer: Offset = Offset.Unspecified) {
        val dt = if (lastStep.isNaN()) 0f else (now - lastStep).coerceIn(0f, MAX_STEP)
        lastStep = now
        val hasPointer = pointer.isSpecified
        val px = if (hasPointer) pointer.x else 0f
        val py = if (hasPointer) pointer.y else 0f
        for (i in 0 until count) {
            val baseX = flightX(i, now)
            val baseY = flightY(i, now)
            var forceX = -SPRING * pushX[i] - DAMPING * pushVelocityX[i]
            var forceY = -SPRING * pushY[i] - DAMPING * pushVelocityY[i]
            if (hasPointer) {
                var dx = baseX + pushX[i] - px
                var dy = baseY + pushY[i] - py
                val distance = sqrt(dx * dx + dy * dy)
                if (distance < REPEL_RADIUS) {
                    if (distance < 1e-5f) {
                        // Dead under the pointer: pick a side, the dot's own scatter direction.
                        dx = scatterX[i] + 1e-3f
                        dy = scatterY[i]
                    }
                    val length = sqrt(dx * dx + dy * dy)
                    val falloff = 1f - distance / REPEL_RADIUS
                    val push = REPEL * falloff * falloff / length
                    forceX += dx * push
                    forceY += dy * push
                }
            }
            pushVelocityX[i] += forceX * dt
            pushVelocityY[i] += forceY * dt
            pushX[i] += pushVelocityX[i] * dt
            pushY[i] += pushVelocityY[i] * dt
            x[i] = baseX + pushX[i] + WOBBLE * sin(now * wobbleSpeedX[i] + wobblePhaseX[i])
            y[i] = baseY + pushY[i] + WOBBLE * cos(now * wobbleSpeedY[i] + wobblePhaseY[i])
        }
    }

    private fun progress(i: Int, now: Float): Float =
        ((now - flightStart - delay[i]) / TRAVEL).coerceIn(0f, 1f)

    private fun flightX(i: Int, now: Float): Float {
        val t = progress(i, now)
        if (t >= 1f) return toX[i]
        val eased = easeInOutCubic(t)
        val dx = toX[i] - fromX[i]
        val dy = toY[i] - fromY[i]
        return fromX[i] + dx * eased - dy * bow[i] * sin(PI_F * eased) + scatterX[i] * sin(PI_F * t)
    }

    private fun flightY(i: Int, now: Float): Float {
        val t = progress(i, now)
        if (t >= 1f) return toY[i]
        val eased = easeInOutCubic(t)
        val dx = toX[i] - fromX[i]
        val dy = toY[i] - fromY[i]
        return fromY[i] + dy * eased + dx * bow[i] * sin(PI_F * eased) + scatterY[i] * sin(PI_F * t)
    }

    /**
     * New delays, bows and scatter for the next flights. A dot already in the air leaves at once:
     * waiting out a fresh delay would freeze it mid-flight.
     */
    private fun rollFlights(keepInFlight: Boolean, now: Float) {
        for (i in 0 until count) {
            val wasFlying = keepInFlight && progress(i, now) in 0.001f..0.999f
            delay[i] = if (wasFlying) 0f else random.nextFloat() * MAX_DELAY
            bow[i] = (random.nextFloat() * 2f - 1f) * MAX_BOW
            val angle = random.nextFloat() * TWO_PI
            val reach = SCATTER * sqrt(random.nextFloat())
            scatterX[i] = cos(angle) * reach
            scatterY[i] = sin(angle) * reach
        }
    }

    companion object {
        const val TIERS = 3

        /** How far a resting dot wanders, in glyph units, on each axis. */
        const val WOBBLE = 0.0045f

        /** Seconds a single dot takes to fly between shapes. */
        const val TRAVEL = 1.0f

        /** The longest a dot waits before it leaves, in seconds. */
        const val MAX_DELAY = 0.35f

        /** Seconds from a [morphTo] until every dot has landed. */
        const val LONGEST_FLIGHT = TRAVEL + MAX_DELAY

        /** How close, in glyph units, the pointer has to be to push a dot. */
        const val REPEL_RADIUS = 0.2f

        private const val REPEL = 14f
        private const val SPRING = 90f
        // 0.6 of critical damping: pushed dots overshoot a hair on the way back.
        private const val DAMPING = 2f * 0.6f * 9.4868f
        private const val MAX_STEP = 1f / 30f
        private const val MAX_BOW = 0.22f
        private const val SCATTER = 0.07f
        private const val WOBBLE_HZ_MIN = 0.7f
        private const val WOBBLE_HZ_SPREAD = 1.6f
        private const val DIM_SHARE = 0.32f
        private const val MID_SHARE = 0.43f
        private const val PI_F = PI.toFloat()
        private const val TWO_PI = 2f * PI_F
    }
}

internal fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t
    else {
        val u = -2f * t + 2f
        1f - u * u * u / 2f
    }

/**
 * [count] points in a soft round cloud of about [radius], denser in the middle, as `x, y` pairs:
 * where the dots wait before the first shape.
 */
fun cloud(count: Int, radius: Float, random: Random): FloatArray {
    val points = FloatArray(count * 2)
    for (i in 0 until count) {
        val angle = random.nextFloat() * 2f * PI.toFloat()
        // A Gaussian radius, folded in: dense core, ragged edge.
        val r = radius * 0.55f * sqrt(-2f * ln(1f - random.nextFloat() * 0.995f))
        points[i * 2] = cos(angle) * r
        points[i * 2 + 1] = sin(angle) * r
    }
    return points
}
