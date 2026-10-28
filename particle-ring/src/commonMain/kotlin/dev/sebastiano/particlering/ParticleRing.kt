package dev.sebastiano.particlering

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Where the show is: waiting in the icon, bursting out, reading as a ring, folding, done. */
enum class RingPhase {
    Icon,
    Burst,
    Ring,
    Collapse,
    Logo,
}

/**
 * [count] particles that pour out of a Health icon, swirl as a tilted ring while it reads, then
 * fold into a heart inside an arc. Positions are in ring units: the ring's radius is 1 and its
 * centre is the origin, `y` grows downwards. The stage scales them to pixels when it draws.
 *
 * The ring is a torus whose tube is a flat ribbon, tipped outwards like a lampshade's band, and
 * seen from above at a slant, in perspective. That is what gives the original its look: the far
 * side shows its broad face, the near side only a thin bright lip, and the ribbon's edges, seen
 * edge-on, draw bright lines round the hole. The near side is bigger and brighter. Each particle
 * sits on or just under the ribbon's skin, and drifts along the ring and round the ribbon, so the
 * ring churns.
 *
 * Every position is a function of time, not of the last frame, so a stalled frame never flings a
 * particle. To keep a frame cheap, particles share [SPEED_CLASSES] speeds along the ring and as
 * many round the ribbon: a frame takes the sine and cosine of each class once, and every particle
 * adds its own starting angle to its class's with a few multiplications. [step] allocates nothing.
 */
class ParticleRing(val count: Int, seed: Int = 11) {
    /** Where each particle is drawn, as of the last [step]. */
    val x = FloatArray(count)
    val y = FloatArray(count)

    /**
     * How near each particle is, from 0 at the back of the ring to 1 at the front. Below 0, it is
     * still in the icon and not drawn.
     */
    val depth = FloatArray(count)

    /** Each particle's tone, `0 until TONES`, from deep pink to blush white. */
    val tone = IntArray(count)

    /** How many particles each tone holds. */
    val toneSizes = IntArray(TONES)

    var phase = RingPhase.Icon
        private set

    /** Where each particle's place in the ring is, as of the last [step]. */
    internal val ringX = FloatArray(count)
    internal val ringY = FloatArray(count)
    internal val ringDepth = FloatArray(count)

    /**
     * How far towards the viewer each particle's place in the ring is, from -1 to 1, before the
     * slant: the ring's own front and back.
     */
    internal val ringFront = FloatArray(count)

    private val random = Random(seed)

    // The particle's place in the ring: its angle along the ring and round the tube, how far out
    // in the tube it sits, and which speed class it drifts with along each.
    private val cosAlong = FloatArray(count)
    private val sinAlong = FloatArray(count)
    private val cosAround = FloatArray(count)
    private val sinAround = FloatArray(count)
    private val tubeOut = FloatArray(count)
    private val alongClass = IntArray(count)
    private val aroundClass = IntArray(count)
    private val classCosAlong = FloatArray(SPEED_CLASSES)
    private val classSinAlong = FloatArray(SPEED_CLASSES)
    private val classCosAround = FloatArray(SPEED_CLASSES)
    private val classSinAround = FloatArray(SPEED_CLASSES)

    // The burst: where the particle waits in the icon, when it leaves, and the bend of its flight.
    private val sourceX = FloatArray(count)
    private val sourceY = FloatArray(count)
    private val launch = FloatArray(count)
    private val launchesInOrder: FloatArray
    private val bendX = FloatArray(count)
    private val bendY = FloatArray(count)

    // The collapse: when the particle leaves the ring, and where it lands in the logo.
    private val foldDelay = FloatArray(count)
    private val toHeart = BooleanArray(count)
    private val targetX = FloatArray(count)
    private val targetY = FloatArray(count)
    private val heartPlaces: FloatArray
    private val arcPlaces: FloatArray

    private var burstStart = Float.NaN
    private var collapseStart = Float.NaN
    private var resetAt = Float.NEGATIVE_INFINITY
    private var lastSpin = 0f
    private var lastTilt = 0f

    init {
        var hearts = 0
        for (i in 0 until count) {
            val roll = random.nextFloat()
            tone[i] =
                when {
                    roll < DEEP_SHARE -> 0
                    roll < DEEP_SHARE + MID_SHARE -> 1
                    else -> 2
                }
            toneSizes[tone[i]]++

            val along = random.nextFloat() * TWO_PI
            val around = random.nextFloat() * TWO_PI
            cosAlong[i] = cos(along)
            sinAlong[i] = sin(along)
            cosAround[i] = cos(around)
            sinAround[i] = sin(around)
            // On or just under the ribbon's skin: a shell, so its edges, seen edge-on, draw lines.
            tubeOut[i] = INNER_SHARE + (1f - INNER_SHARE) * sqrt(random.nextFloat())
            alongClass[i] = random.nextInt(SPEED_CLASSES)
            aroundClass[i] = random.nextInt(SPEED_CLASSES)

            launch[i] = LAST_LAUNCH * random.nextFloat().let { it * (0.35f + 0.65f * it) }
            foldDelay[i] = MAX_FOLD_DELAY * random.nextFloat()
            toHeart[i] = i < (count * HEART_SHARE).toInt()
            if (toHeart[i]) hearts++
        }
        // The icon's heart, with every particle waiting somewhere in it.
        val sources = HeartShape.sample(count, random)
        for (i in 0 until count) {
            sourceX[i] = sources[i * 2] * ICON_HEART_WIDTH
            sourceY[i] = ICON_Y + sources[i * 2 + 1] * ICON_HEART_WIDTH
            // The fountain: straight up out of the icon, fanning out the further from the
            // middle a particle starts, and rising to well above the ring before it settles.
            bendX[i] = sourceX[i] * FAN + (random.nextFloat() - 0.5f) * FAN_SCATTER
            bendY[i] = ICON_Y - LIFT - random.nextFloat() * LIFT_SCATTER
        }
        launchesInOrder = launch.copyOf().also { it.sort() }
        heartPlaces = HeartShape.sample(hearts, random)
        for (i in 0 until hearts) {
            heartPlaces[i * 2] *= HEART_WIDTH
            heartPlaces[i * 2 + 1] = heartPlaces[i * 2 + 1] * HEART_WIDTH + HEART_Y
        }
        arcPlaces = FloatArray((count - hearts) * 2)
        for (i in 0 until count - hearts) {
            val angle = ARC_START + random.nextFloat() * ARC_SWEEP
            val radius = ARC_RADIUS + (random.nextFloat() * 2f - 1f) * ARC_WIDTH / 2f
            arcPlaces[i * 2] = cos(angle) * radius
            arcPlaces[i * 2 + 1] = sin(angle) * radius
        }
        depth.fill(-1f)
    }

    /** The share of particles that have left the icon by [now]. */
    fun launchedShare(now: Float): Float =
        when (phase) {
            RingPhase.Icon -> 0f
            RingPhase.Burst -> launchedBy(now - burstStart) / count.toFloat()
            else -> 1f
        }

    /** How many particles leave in the first [seconds] of the burst: a search of the launches. */
    private fun launchedBy(seconds: Float): Int {
        var low = 0
        var high = count
        while (low < high) {
            val middle = (low + high) ushr 1
            if (launchesInOrder[middle] <= seconds) low = middle + 1 else high = middle
        }
        return low
    }

    /**
     * How much of the icon shows at [now]: it fades in on a replay, and out once the burst is under
     * way.
     */
    fun iconShare(now: Float): Float =
        when (phase) {
            RingPhase.Icon -> ((now - resetAt) / ICON_FADE_IN).coerceIn(0f, 1f)
            RingPhase.Burst ->
                1f - ((now - burstStart - ICON_FADE_FROM) / ICON_FADE_OUT).coerceIn(0f, 1f)
            else -> 0f
        }

    /** How much of the solid logo shows at [now]: it fades in as the last particles land. */
    fun logoShare(now: Float): Float =
        when (phase) {
            RingPhase.Collapse,
            RingPhase.Logo ->
                ((now - collapseStart - COLLAPSE_SECONDS + LOGO_LEAD) / LOGO_FADE).coerceIn(0f, 1f)
            else -> 0f
        }

    /**
     * The next step of the show at [now], if it is waiting for one: the icon bursts, the ring
     * folds, the finished logo plays again. Returns whether anything happened.
     */
    fun advance(now: Float): Boolean {
        when (phase) {
            RingPhase.Icon -> {
                burstStart = now
                phase = RingPhase.Burst
            }
            RingPhase.Ring -> {
                // Places in the ring as of now, to pair each particle with a place in the logo.
                placeOnRing(now, lastSpin, lastTilt)
                pairWithLogo()
                collapseStart = now
                phase = RingPhase.Collapse
            }
            RingPhase.Logo -> {
                resetAt = now
                phase = RingPhase.Icon
                depth.fill(-1f)
            }
            RingPhase.Burst,
            RingPhase.Collapse -> return false
        }
        return true
    }

    /**
     * Moves every particle to where it is at [now] (seconds, on any steady clock), with the ring
     * spun by [spin] about its axis and leaning [tilt] further towards the viewer, both in radians.
     */
    fun step(now: Float, spin: Float = 0f, tilt: Float = 0f) {
        lastSpin = spin
        lastTilt = tilt
        if (phase == RingPhase.Burst && now - burstStart >= BURST_SECONDS) phase = RingPhase.Ring
        if (phase == RingPhase.Collapse && now - collapseStart >= COLLAPSE_SECONDS) {
            phase = RingPhase.Logo
        }
        when (phase) {
            RingPhase.Icon -> Unit
            RingPhase.Burst -> {
                placeOnRing(now, spin, tilt)
                fly(now)
            }
            RingPhase.Ring -> {
                placeOnRing(now, spin, tilt)
                ringX.copyInto(x)
                ringY.copyInto(y)
                ringDepth.copyInto(depth)
            }
            RingPhase.Collapse -> {
                placeOnRing(now, spin, tilt)
                fold(now)
            }
            RingPhase.Logo -> {
                targetX.copyInto(x)
                targetY.copyInto(y)
                depth.fill(1f)
            }
        }
    }

    /** Fills [ringX], [ringY], [ringDepth] and [ringFront] with every particle's place at [now]. */
    private fun placeOnRing(now: Float, spin: Float, tilt: Float) {
        for (k in 0 until SPEED_CLASSES) {
            val along = now * ALONG_SPEED * classSpeed(k) + spin
            val around = now * AROUND_SPEED * classSpeed(SPEED_CLASSES - 1 - k)
            classCosAlong[k] = cos(along)
            classSinAlong[k] = sin(along)
            classCosAround[k] = cos(around)
            classSinAround[k] = sin(around)
        }
        val slant = SLANT + tilt
        val cosSlant = cos(slant)
        val sinSlant = sin(slant)
        for (i in 0 until count) {
            val a = alongClass[i]
            val b = aroundClass[i]
            // The particle's own angles plus its class's: sums of angles, without a sine each.
            val cu = cosAlong[i] * classCosAlong[a] - sinAlong[i] * classSinAlong[a]
            val su = sinAlong[i] * classCosAlong[a] + cosAlong[i] * classSinAlong[a]
            val cv = cosAround[i] * classCosAround[b] - sinAround[i] * classSinAround[b]
            val sv = sinAround[i] * classCosAround[b] + cosAround[i] * classSinAround[b]

            // Across the ribbon, then tipped: outwards from the ring's centre and up.
            val across = TUBE * tubeOut[i] * cv
            val through = TUBE * RIBBON * tubeOut[i] * sv
            val outwards = across * COS_TIP - through * SIN_TIP
            val up = across * SIN_TIP + through * COS_TIP
            // The ring lies flat: x right, z towards the viewer, y up.
            val out = 1f + outwards
            val px = out * cu
            val pz = out * su
            val py = up
            ringFront[i] = pz / (1f + TUBE)

            // Seen from above at a slant: the far side rises on screen and recedes.
            val upY = py * cosSlant - pz * sinSlant
            val nearZ = py * sinSlant + pz * cosSlant
            val perspective = CAMERA / (CAMERA - nearZ)
            val sx = px * perspective
            val sy = -upY * perspective
            ringX[i] = sx * COS_ROLL - sy * SIN_ROLL
            ringY[i] = sx * SIN_ROLL + sy * COS_ROLL
            ringDepth[i] = ((nearZ / (1f + TUBE) + 1f) / 2f).coerceIn(0f, 1f)
        }
    }

    /**
     * The burst: each particle leaves the icon at its launch time and flies a curve up past its
     * bend point and down onto its place in the ring, which moves as the ring turns. It leaves fast
     * and eases in, so it lands on its place without a bump.
     */
    private fun fly(now: Float) {
        val since = now - burstStart
        for (i in 0 until count) {
            val t = (since - launch[i]) / BURST_FLIGHT
            when {
                t < 0f -> {
                    // Still in the icon: not drawn, but where it will first show.
                    depth[i] = -1f
                    x[i] = sourceX[i]
                    y[i] = sourceY[i]
                }
                t >= 1f -> {
                    x[i] = ringX[i]
                    y[i] = ringY[i]
                    depth[i] = ringDepth[i]
                }
                else -> flyAlong(i, t)
            }
        }
    }

    /** Particle [i], [t] of the way through its flight from the icon to the ring. */
    private fun flyAlong(i: Int, t: Float) {
        val u = 1f - t
        val e = 1f - u * u
        val a = (1f - e) * (1f - e)
        val b = 2f * (1f - e) * e
        val c = e * e
        x[i] = a * sourceX[i] + b * bendX[i] + c * ringX[i]
        y[i] = a * sourceY[i] + b * bendY[i] + c * ringY[i]
        depth[i] = 1f + (ringDepth[i] - 1f) * e
    }

    /**
     * The collapse: each particle leaves its place in the ring after its own short delay and eases
     * to its place in the logo, swirling round the centre on the way.
     */
    private fun fold(now: Float) {
        val since = now - collapseStart
        for (i in 0 until count) {
            val t = ((since - foldDelay[i]) / FOLD_FLIGHT).coerceIn(0f, 1f)
            val e = easeInOutCubic(t)
            val lx = ringX[i] + (targetX[i] - ringX[i]) * e
            val ly = ringY[i] + (targetY[i] - ringY[i]) * e
            val swirl = SWIRL * sin(PI_F * e)
            val cs = cos(swirl)
            val sn = sin(swirl)
            x[i] = lx * cs - ly * sn
            y[i] = lx * sn + ly * cs
            depth[i] = ringDepth[i] + (1f - ringDepth[i]) * e
        }
    }

    /**
     * Gives every particle a place in the logo. The heart's particles and the arc's are each put in
     * order of their angle round the centre, as are their places, and the n-th takes the n-th: the
     * ring closes in on the logo without particles crossing it. The arc's order starts at its gap,
     * at the bottom, so the particles nearest the gap go to the arc's ends.
     */
    private fun pairWithLogo() {
        pair(heart = true, places = heartPlaces)
        pair(heart = false, places = arcPlaces)
    }

    private fun pair(heart: Boolean, places: FloatArray) {
        val size = places.size / 2
        val particles = LongArray(size)
        var n = 0
        for (i in 0 until count) {
            if (toHeart[i] != heart) continue
            particles[n++] = angleKey(ringX[i], ringY[i], i)
        }
        val targets = LongArray(size) { angleKey(places[it * 2], places[it * 2 + 1], it) }
        particles.sort()
        targets.sort()
        for (rank in 0 until size) {
            val particle = (particles[rank] and INDEX_MASK).toInt()
            val place = (targets[rank] and INDEX_MASK).toInt()
            targetX[particle] = places[place * 2]
            targetY[particle] = places[place * 2 + 1]
        }
    }

    companion object {
        const val TONES = 3

        /** Where the icon sits under the ring, and half its width, in ring units. */
        const val ICON_Y = 1.75f
        const val ICON_HALF = 0.24f

        /** How wide the heart in the icon is. */
        const val ICON_HEART_WIDTH = 0.26f

        /** The latest a particle leaves the icon, after the burst starts, in seconds. */
        const val LAST_LAUNCH = 1.4f

        /** How long one particle's flight from the icon to the ring takes. */
        const val BURST_FLIGHT = 1.6f

        /** From the burst's start until every particle is in the ring. */
        const val BURST_SECONDS = LAST_LAUNCH + BURST_FLIGHT

        /** The latest a particle leaves the ring, and how long its flight to the logo takes. */
        const val MAX_FOLD_DELAY = 0.45f
        const val FOLD_FLIGHT = 1.35f

        /** From the collapse's start until every particle is in the logo. */
        const val COLLAPSE_SECONDS = MAX_FOLD_DELAY + FOLD_FLIGHT

        /** The logo: a heart inside an arc that leaves a quarter turn open at the bottom. */
        const val HEART_Y = 0.03f
        const val HEART_WIDTH = 0.5f
        const val HEART_SHARE = 0.58f
        const val ARC_RADIUS = 0.56f
        const val ARC_WIDTH = 0.12f

        /** How far either side of [ARC_RADIUS] an arc particle can land. */
        const val ARC_BAND = ARC_WIDTH / 2f + 1e-4f

        /** Whether (`x`, `y`) is on the icon. */
        fun isOnIcon(x: Float, y: Float): Boolean =
            abs(x) <= ICON_HALF && abs(y - ICON_Y) <= ICON_HALF

        /** Whether (`x`, `y`) is on the finished logo. */
        fun isOnLogo(x: Float, y: Float): Boolean =
            x * x + y * y <= (ARC_RADIUS + ARC_WIDTH) * (ARC_RADIUS + ARC_WIDTH)

        private const val PI_F = PI.toFloat()
        private const val TWO_PI = 2f * PI_F

        // The ribbon: its half-width, against the ring's radius of 1, how thin it is against its
        // width, and how far it tips outwards and up. Particles sit from 0.75 of the way out to
        // the skin.
        private const val TUBE = 0.38f
        private const val RIBBON = 0.26f
        private const val TIP = 0.8f
        private val COS_TIP = cos(TIP)
        private val SIN_TIP = sin(TIP)
        private const val INNER_SHARE = 0.75f

        // Seen from 25° above the ring's plane, from 4 radii away, and rolled so the right rises.
        private const val SLANT = 0.44f
        private const val CAMERA = 4f
        private const val ROLL = -0.28f
        private val COS_ROLL = cos(ROLL)
        private val SIN_ROLL = sin(ROLL)

        // Radians a second along the ring and round the tube, before each class's speed.
        private const val ALONG_SPEED = 0.42f
        private const val AROUND_SPEED = 0.9f
        private const val SPEED_CLASSES = 6

        private fun classSpeed(k: Int): Float = 0.7f + 0.6f * k / (SPEED_CLASSES - 1)

        private const val DEEP_SHARE = 0.5f
        private const val MID_SHARE = 0.32f

        // The fountain out of the icon.
        private const val FAN = 4f
        private const val FAN_SCATTER = 1.6f
        private const val LIFT = 2.1f
        private const val LIFT_SCATTER = 0.9f

        private const val ICON_FADE_IN = 0.4f
        private const val ICON_FADE_FROM = 1.6f
        private const val ICON_FADE_OUT = 1.2f

        private const val SWIRL = 0.9f
        private const val LOGO_LEAD = 0.25f
        private const val LOGO_FADE = 0.55f

        // The arc runs from 135° clockwise, on screen, round to 45°: the gap is at the bottom.
        private const val ARC_START = 0.75f * PI_F
        private const val ARC_SWEEP = 1.5f * PI_F
    }
}

private const val INDEX_BITS = 32
private const val INDEX_MASK = (1L shl INDEX_BITS) - 1
private const val ANGLE_STEPS = 1 shl 24

/**
 * Packs the angle of (`x`, `y`) round the origin, counted clockwise on screen from the bottom,
 * above [index], so a primitive sort puts points in order round the centre.
 */
private fun angleKey(x: Float, y: Float, index: Int): Long {
    // atan2(-x, y) is 0 straight down and grows clockwise on a screen whose y points down.
    var angle = atan2(-x, y)
    if (angle < 0f) angle += 2f * PI.toFloat()
    val step = (angle / (2f * PI.toFloat()) * ANGLE_STEPS).toLong()
    return (step shl INDEX_BITS) or index.toLong()
}

internal fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t
    else {
        val u = -2f * t + 2f
        1f - u * u * u / 2f
    }
