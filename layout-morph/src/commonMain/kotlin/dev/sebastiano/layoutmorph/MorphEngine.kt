package dev.sebastiano.layoutmorph

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Moves every card between arrangements. Plain Kotlin, stepped by the frame clock.
 *
 * Each card follows its arrangement's live target plus an offset that springs to zero. Switching
 * arrangement turns the card's current pose into a fresh offset from its new target, so a card
 * never jumps, even mid-flight, and a target that moves (the carousel, the helix) is followed
 * exactly once the offset has died away. Cards switch one after another, left to right.
 */
class MorphEngine(
    pieces: List<Piece>,
    field: Field,
    initial: Arrangement,
    initialFocus: Int = 0,
) {
    private val count = pieces.size
    private val tall = pieces.map { it.tall }

    /** The card field in dp. Cards follow a resize straight away. */
    var field: Field = field

    /** The arrangement the cards are heading for. */
    var arrangement: Arrangement = initial
        private set

    private val assigned = Array(count) { initial }
    private val pending = arrayOfNulls<Arrangement>(count)
    private val switchAt = LongArray(count)
    private val offset = FloatArray(count * CHANNELS)
    private val velocity = FloatArray(count * CHANNELS)
    private val laps = IntArray(count)
    private var last = Long.MIN_VALUE

    private val lift = FloatArray(count)
    private val liftVelocity = FloatArray(count)

    /** The card under the pointer, which lifts in the flat arrangements and the stack. */
    var hovered: Int? = null
        private set

    private val carousel = CarouselDrive(count, initialFocus)
    private val helix = HelixDrive()

    /** The helix's turn, in degrees. */
    val spin: Float
        get() = helix.spin

    /** How fast the helix turns, in degrees a second. */
    val spinVelocity: Float
        get() = helix.velocity

    /** The carousel's position in cards: card 2 is centred at 2. */
    val carouselPosition: Float
        get() = carousel.position

    /** The carousel's card: where it is heading, or where a drag has it now. */
    val focused: Int
        get() = carousel.focused

    /** Every card's pose as of the last [step]. */
    var poses: List<CardPose> = targets(initial)
        private set

    fun pose(index: Int): CardPose = poses[index]

    /** Heads for [next]. Cards set off left to right, a beat apart, starting at [nanos]. */
    fun select(next: Arrangement, nanos: Long) {
        if (next == arrangement) return
        arrangement = next
        hovered = null
        // A drag in the old arrangement ends where it is.
        carousel.release(0f)
        helix.release(0f)
        val order = poses.indices.sortedBy { poses[it].x + poses[it].y * ROW_LEAN }
        order.forEachIndexed { rank, i ->
            pending[i] = if (assigned[i] == next) null else next
            switchAt[i] = nanos + rank * STAGGER_NANOS
        }
    }

    /** Advances to [nanos]. Returns whether anything is still moving. */
    fun step(nanos: Long): Boolean {
        val dt = if (last == Long.MIN_VALUE) 0f else ((nanos - last) / 1e9f).coerceIn(0f, MAX_DT)
        last = nanos
        var moving = carousel.advance(dt) or advanceLifts(dt)
        if (helixInUse()) {
            helix.advance(dt)
            moving = true
        }
        val targets = arrayOfNulls<List<CardPose>>(Arrangement.entries.size)
        fun target(a: Arrangement) =
            targets[a.ordinal] ?: targets(a).also { targets[a.ordinal] = it }

        for (i in 0 until count) {
            val next = pending[i] ?: continue
            moving = true
            if (nanos >= switchAt[i]) switch(i, next, target(next)[i])
        }
        if (integrate(dt)) moving = true

        poses =
            List(count) { i ->
                val base = target(assigned[i])[i]
                val flying = isFlying(i)
                compose(i, base, if (flying) FLIGHT_DEPTH else 0f)
            }
        relap()
        return moving
    }

    /** After an idle spell the next [step] starts afresh, rather than catching up on the gap. */
    fun resume() {
        last = Long.MIN_VALUE
    }

    /** Points the pointer at card [index], or at none. */
    fun hover(index: Int?) {
        hovered = index
    }

    /** A drag by [dx] dp: slides the carousel or turns the helix. */
    fun drag(dx: Float) {
        when (arrangement) {
            Arrangement.Carousel -> carousel.drag(-dx / Arrangements.carouselPitch(field))
            Arrangement.Helix -> helix.drag(dx * DEGREES_PER_DP)
            else -> Unit
        }
    }

    /** Lets go of a drag moving at [velocity] dp a second. */
    fun release(velocity: Float) {
        when (arrangement) {
            Arrangement.Carousel -> carousel.release(-velocity / Arrangements.carouselPitch(field))
            Arrangement.Helix -> helix.release(velocity * DEGREES_PER_DP)
            else -> Unit
        }
    }

    /** Moves the carousel [delta] cards along, stopping at the ends. */
    fun stepFocus(delta: Int) = carousel.stepFocus(delta)

    /** Centres card [index] in the carousel. */
    fun focus(index: Int) = carousel.focus(index)

    private fun targets(a: Arrangement): List<CardPose> =
        when (a) {
            Arrangement.Grid -> Arrangements.grid(count, field, ::liftOf)
            Arrangement.Masonry -> Arrangements.masonry(tall, field, ::liftOf)
            Arrangement.List -> Arrangements.list(count, field, ::liftOf)
            Arrangement.Stack -> Arrangements.stack(count, field, ::liftOf)
            Arrangement.Carousel -> Arrangements.carousel(count, field, carousel.position)
            Arrangement.Helix -> Arrangements.helix(count, field, spin, laps)
        }

    private fun liftOf(index: Int) = lift[index]

    private fun helixInUse() =
        arrangement == Arrangement.Helix ||
            (0 until count).any {
                assigned[it] == Arrangement.Helix || pending[it] == Arrangement.Helix
            }

    /** Card [i] sets off for [next], where it lands on [to]. */
    private fun switch(i: Int, next: Arrangement, to: CardPose) {
        retarget(i, poses[i], to)
        assigned[i] = next
        pending[i] = null
    }

    /** Card [i]'s offset becomes the gap from [to] back to where it is, [from]. */
    private fun retarget(i: Int, from: CardPose, to: CardPose) {
        val o = i * CHANNELS
        offset[o + X] = from.x - to.x
        offset[o + Y] = from.y - to.y
        offset[o + WIDTH] = from.width - to.width
        offset[o + HEIGHT] = from.height - to.height
        offset[o + ROTATION] = wrapDegrees(from.rotation - to.rotation)
        offset[o + TURN] = wrapDegrees(from.turn - to.turn)
        offset[o + SCALE] = from.scale - to.scale
        offset[o + ALPHA] = from.alpha - to.alpha
        offset[o + ROW] = from.row - to.row
        offset[o + CAPTION] = from.caption - to.caption
    }

    private fun compose(i: Int, base: CardPose, depthBoost: Float): CardPose {
        val o = i * CHANNELS
        return CardPose(
            x = base.x + offset[o + X],
            y = base.y + offset[o + Y],
            width = base.width + offset[o + WIDTH],
            height = base.height + offset[o + HEIGHT],
            rotation = base.rotation + offset[o + ROTATION],
            turn = base.turn + offset[o + TURN],
            scale = base.scale + offset[o + SCALE],
            alpha = (base.alpha + offset[o + ALPHA]).coerceIn(0f, 1f),
            row = (base.row + offset[o + ROW]).coerceIn(0f, 1f),
            caption = (base.caption + offset[o + CAPTION]).coerceIn(0f, 1f),
            depth = base.depth + depthBoost,
        )
    }

    private fun isFlying(i: Int): Boolean {
        val o = i * CHANNELS
        return abs(offset[o + X]) + abs(offset[o + Y]) > FLYING_DISTANCE
    }

    /**
     * Settled helix cards take up the spiral's wrap again. A card that landed past the end wraps
     * round to a spot that may already show, so it starts there see-through and fades in.
     */
    private fun relap() {
        for (i in 0 until count) {
            val inHelix = assigned[i] == Arrangement.Helix
            // A helix card keeps its lap until it has landed.
            if (inHelix && !isSettled(i)) continue
            val lap = Arrangements.helixLap(i, count, spin)
            if (lap != laps[i]) {
                laps[i] = lap
                // Rare (a card at a time, at the faded ends): the spiral can be laid out again.
                if (inHelix)
                    offset[i * CHANNELS + ALPHA] =
                        -Arrangements.helix(count, field, spin, laps)[i].alpha
            }
        }
    }

    private fun isSettled(i: Int): Boolean {
        val o = i * CHANNELS
        return (0 until CHANNELS).all { offset[o + it] == 0f && velocity[o + it] == 0f }
    }

    /** Springs every offset towards zero. Returns whether any is still moving. */
    private fun integrate(dt: Float): Boolean {
        if (dt <= 0f) return offset.any { it != 0f }
        val steps = (dt / MAX_SUBSTEP).toInt() + 1
        val h = dt / steps
        var moving = false
        for (k in offset.indices) {
            var x = offset[k]
            var v = velocity[k]
            if (x == 0f && v == 0f) continue
            repeat(steps) {
                v += (-STIFFNESS * x - DAMPING * v) * h
                x += v * h
            }
            val rest = if (k % CHANNELS < SCALE) POSITION_REST else UNIT_REST
            if (abs(x) < rest && abs(v) < rest * VELOCITY_REST) {
                x = 0f
                v = 0f
            } else {
                moving = true
            }
            offset[k] = x
            velocity[k] = v
        }
        return moving
    }

    private fun advanceLifts(dt: Float): Boolean {
        val lifts = arrangement in LIFTING
        var moving = false
        for (i in 0 until count) {
            val goal = if (lifts && hovered == i) 1f else 0f
            var x = lift[i]
            var v = liftVelocity[i]
            if (x == goal && v == 0f) continue
            val steps = (dt / MAX_SUBSTEP).toInt() + 1
            val h = dt / steps
            repeat(steps) {
                v += (-LIFT_STIFFNESS * (x - goal) - LIFT_DAMPING * v) * h
                x += v * h
            }
            if (abs(x - goal) < UNIT_REST && abs(v) < UNIT_REST * VELOCITY_REST) {
                x = goal
                v = 0f
            } else {
                moving = true
            }
            lift[i] = x
            liftVelocity[i] = v
        }
        return moving
    }

    private companion object {
        const val X = 0
        const val Y = 1
        const val WIDTH = 2
        const val HEIGHT = 3
        const val ROTATION = 4
        const val TURN = 5
        const val SCALE = 6
        const val ALPHA = 7
        const val ROW = 8
        const val CAPTION = 9
        const val CHANNELS = 10

        /** Each card follows the one before by 16 ms: 24 cards set off over about 0.4 s. */
        const val STAGGER_NANOS = 16_000_000L

        /** The departure order reads left to right, a row's height counting for a third. */
        const val ROW_LEAN = 0.35f

        /** The flight spring: settles in about half a second with a whisper of overshoot. */
        const val STIFFNESS = 120f
        val DAMPING = 2 * 0.8f * sqrt(STIFFNESS)

        const val LIFT_STIFFNESS = 260f
        val LIFT_DAMPING = 2 * 0.75f * sqrt(LIFT_STIFFNESS)

        const val DEGREES_PER_DP = 0.35f

        /** Flying cards draw over settled ones. */
        const val FLIGHT_DEPTH = 100f
        const val FLYING_DISTANCE = 1f

        const val POSITION_REST = 0.02f
        const val UNIT_REST = 0.0005f
        const val VELOCITY_REST = 10f
        const val MAX_SUBSTEP = 1f / 240f
        const val MAX_DT = 0.05f

        val LIFTING =
            setOf(Arrangement.Grid, Arrangement.Masonry, Arrangement.List, Arrangement.Stack)
    }
}
