package dev.sebastiano.anatomy.figures

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Spring
import dev.sebastiano.anatomy.kit.Tone.Faint
import dev.sebastiano.anatomy.kit.Tone.Hi
import dev.sebastiano.anatomy.kit.Tone.Lo
import dev.sebastiano.anatomy.kit.Tone.Mid
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.dotGrid
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.knurl
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.radialTicks
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.segment
import dev.sebastiano.anatomy.kit.sideArc
import dev.sebastiano.anatomy.kit.sideSeam
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.topTicks
import dev.sebastiano.anatomy.kit.towardViewer
import dev.sebastiano.anatomy.kit.translateAlong
import dev.sebastiano.anatomy.oneDecimal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * A token bucket rate limiter: it holds at most [capacity] tokens, gains [rate] tokens a second
 * while it has room, and lets a request through only if it can take one whole token.
 */
class TokenBucket(val capacity: Int, val rate: Double, tokens: Double = capacity.toDouble()) {
    /** The tokens in the bucket, fractions included. */
    var tokens: Double = tokens
        private set

    /** Every token the refill has ever added: the feed's total travel. */
    var added: Double = 0.0
        private set

    var passed: Int = 0
        private set

    var refused: Int = 0
        private set

    /** Refills for [dt] seconds, never above capacity. Returns how many whole tokens arrived. */
    fun advance(dt: Double): Int {
        val add = min(capacity - tokens, rate * dt).coerceAtLeast(0.0)
        if (add == 0.0) return 0
        val before = floor(added + EPSILON)
        tokens = snap(tokens + add)
        added = snap(added + add)
        return (floor(added + EPSILON) - before).toInt()
    }

    /** One request: it takes a whole token and passes, or finds less than one and is refused. */
    fun request(): Boolean =
        if (tokens >= 1 - EPSILON) {
            tokens = snap(tokens - 1)
            passed++
            true
        } else {
            refused++
            false
        }

    /** Seconds until the next request would pass. */
    fun waitSeconds(): Double = if (tokens >= 1 - EPSILON) 0.0 else (1 - tokens) / rate

    private fun snap(value: Double): Double {
        val whole = value.roundToLong().toDouble()
        return if (abs(value - whole) < EPSILON) whole else value
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}

/*
 * A token dispenser on a bench. A feed wheel under a hopper carries tokens round at one a second
 * and drops each into a magazine that holds eight; pressing the plunger pushes the bottom token out
 * along the tray into the payout box. The magazine is the bucket, the wheel is the refill, and a
 * stroke that finds the magazine empty is a refused request.
 */
object TokenBucketDispenser {
    const val WIDTH = 600.0
    const val HEIGHT = 360.0

    private const val CAPACITY = DispenserMotion.CAPACITY

    private val PLATE = Plan(-66.0, -44.0, 136.0, 74.0, 8.0)
    private const val PLATE_H = 10.0
    private const val FOOT_H = 5.0

    private val DECK = Plan(-58.0, -36.0, 92.0, 50.0, 2.0)
    private const val ZD = 10.0

    /** A token: radius and thickness. The magazine stacks them [TH] apart. */
    private const val R = DispenserMotion.R
    private const val TH = 3.0

    /** How far the stack rides up on the blade while a token is pushed out from under it. */
    const val LIFT = TH
    private const val ZF = ZD + CAPACITY * TH + 5
    private const val PLATE_T = 3.0
    private const val WHEEL_T = 3.0
    private const val WHEEL_TOP = ZF + PLATE_T + WHEEL_T

    private val WC = Floor(-20 * cos(PI / 4), -20 * sin(PI / 4))
    private const val POCKET = 20.0
    private const val WHEEL_R = 32.0
    private const val FEED_R = 36.0

    private val HOPPER = Floor(WC.x - POCKET * cos(PI / 4), WC.y - POCKET * sin(PI / 4))
    private const val HOPPER_R = 11.5
    private const val HOPPER_TOP = WHEEL_TOP + 18.0
    private val PILLAR = Floor(-50.0, -30.0)
    private const val CLAMP_Z = HOPPER_TOP - 12

    private const val GATE = 11.6
    private const val GATE_Z = ZD + TH + 0.6

    /** The plunger: the blade's front face at rest, and how far one stroke pushes it. */
    private const val HOME = DispenserMotion.HOME
    private const val STROKE = DispenserMotion.STROKE
    private const val BLADE_L = 38.0
    private val BRIDGE = Plan(-19.5, -7.6, 5.0, 15.2, 0.8)

    private const val TRAY_END = DispenserMotion.TRAY_END
    private val BOX = Plan(TRAY_END, -16.0, 26.0, 32.0, 2.0)
    private const val BOX_H = 26.0

    val P =
        fitProjection(
            boxCorners(PLATE, -PLATE_H - FOOT_H, 0.0) +
                boxCorners(BOX, 0.0, BOX_H) +
                listOf(
                    Point3(HOPPER.x, HOPPER.y, HOPPER_TOP + 2),
                    Point3(PILLAR.x, PILLAR.y, HOPPER_TOP + 2),
                ),
            WIDTH,
            HEIGHT,
            pad = 18.0,
            azimuth = 55.0,
        )

    const val LABEL =
        "A token dispenser on a bench, explaining a token bucket rate limiter. A feed wheel under " +
            "a hopper drops one token a second into a magazine that holds eight. Each request " +
            "presses a plunger that pushes the bottom token out along a tray into a payout box; " +
            "when the magazine has no whole token, the plunger strokes empty and the request is " +
            "refused. When the magazine is full the wheel stops."

    private fun at(x: Double, y: Double, z: Double) = iso(Point3(x, y, z), P)

    private fun ArtBuilder.base() {
        halo(haloOf(PLATE, -PLATE_H - FOOT_H, PLATE_H, P))
        for ((x, y) in corners(PLATE, 16.0).sortedBy { depthOf(Point3(it.x, it.y, 0.0), P) }) {
            solid(cylinder(x, y, 7.0, -PLATE_H - FOOT_H, 1.6, P, 28), tone = Lo)
            solid(cylinder(x, y, 5.4, -PLATE_H - FOOT_H + 1.6, FOOT_H - 1.6, P, 28), tone = Mid)
        }
        solid(slabOf(PLATE, -PLATE_H, PLATE_H, P, 8, 1.6), tone = Mid)
        line(planOutline(insetPlan(PLATE, 4.0), 0.0, P), tone = Lo)
        val screws = corners(PLATE, 8.5)
        line(IsoPath.join(screws.map { ring(it.x, it.y, 2.0, 0.0, P, 16) }), tone = Lo)
        dots(screws.map { at(it.x, it.y, 0.0) }, size = 0.5)
        val label = Plan(-58.0, 17.0, 26.0, 8.0, 1.0)
        solid(slabOf(label, 0.0, 0.8, P, 3), tone = Lo, crease = null)
        line(planOutline(insetPlan(label, 1.4), 0.8, P, 3), tone = Faint)
        for ((index, length) in listOf(16.0, 11.0, 13.0).withIndex()) {
            val y = label.y + 2.6 + index * 1.4
            line(
                lineOnTop(Floor(label.x + 3, y), Floor(label.x + 3 + length, y), 0.8, P),
                tone = Lo,
                free = Free.Both,
            )
        }
        dots(listOf(at(label.x + 22.5, label.y + 2.4, 0.8), at(label.x + 22.5, label.y + 5.6, 0.8)))
        val strip = Plan(-26.0, 18.0, 88.0, 6.0, 1.0)
        solid(slabOf(strip, 0.0, 0.6, P, 3), tone = Lo, crease = null)
        val ticks =
            topTicks(strip.x + 4, strip.x + strip.w - 4, 4.0, 5, strip.y + 0.6, 0.6, 1.8 to 3.4, P)
        line(ticks.minor, tone = Lo)
        line(ticks.major, tone = Mid)
        dots(
            listOf(at(strip.x + 2, strip.y + 3, 0.6), at(strip.x + strip.w - 2, strip.y + 3, 0.6)),
            size = 0.45,
        )
    }

    private fun ArtBuilder.deck() {
        solid(
            slabOf(DECK, 0.0, ZD, P, 6, 1.0),
            tone = Mid,
            inner = art { line(sideSeam(DECK, 4.0, P, 6), tone = Faint) },
        )
        dots(corners(DECK, 4.0).map { at(it.x, it.y, ZD) }, size = 0.5)
        // The seat the stack stands on, worn into the deck under the magazine.
        line(ring(0.0, 0.0, R + 0.8, ZD, P, 40), tone = Faint)
        // The tray: two lips from the gate to the payout box.
        for (y in listOf(-12.6, 11.0)) {
            solid(slabOf(Plan(14.0, y, TRAY_END - 14, 1.6, 0.5), ZD, 2.4, P, 2, 0.3), tone = Mid)
        }
    }

    private fun ArtBuilder.pillarBelow() {
        solid(cylinder(PILLAR.x, PILLAR.y, 4.6, ZD, 2.0, P, 28), tone = Mid)
        solid(
            cylinder(PILLAR.x, PILLAR.y, 3.0, ZD + 2, CLAMP_Z - ZD - 2, P, 24),
            tone = Mid,
            crease = null,
        )
        line(sideArc(PILLAR.x, PILLAR.y, 3.0, 40.0, P, 14), tone = Faint)
    }

    private fun ArtBuilder.column() {
        val column = Plan(WC.x - 6, WC.y - 6, 12.0, 12.0, 1.5)
        solid(
            slabOf(column, ZD, ZF - ZD, P, 3, 0.6),
            tone = Mid,
            inner =
                art {
                    line(
                        sideSeam(column, ZD + 12, P, 3) + sideSeam(column, ZF - 8, P, 3),
                        tone = Faint,
                    )
                },
        )
    }

    private fun ArtBuilder.backOfMagazine() {
        solid(slabOf(Plan(-1.5, -R - 2.2, 3.0, 1.8, 0.4), ZD, ZF - ZD, P, 2), tone = Mid)
        solid(slabOf(Plan(-19.5, -7.6, 5.0, 2.0, 0.4), ZD, TH + 0.6, P, 2), tone = Mid)
    }

    private fun ArtBuilder.plunger() {
        val blade = Plan(HOME - BLADE_L, -5.0, BLADE_L, 10.0, 1.0)
        solid(slabOf(blade, ZD, TH, P, 3, 0.4), tone = Mid)
        val stem = Floor(blade.x + 4.5, 0.0)
        line(lineOnTop(Floor(stem.x + 4, 0.0), Floor(HOME, 0.0), ZD + TH, P), tone = Faint)
        solid(cylinder(stem.x, stem.y, 2.2, ZD + TH, 7.0, P, 20), tone = Mid, crease = null)
        val knob = ZD + TH + 7
        solid(cylinder(stem.x, stem.y, 4.6, knob, 4.0, P, 32, 0.4), tone = Mid)
        line(knurl(stem.x, stem.y, 4.6, knob, knob + 4, 30, P), tone = Lo)
        line(ring(stem.x, stem.y, 2.6, knob + 4, P, 20), tone = Lo)
        dots(listOf(at(stem.x, stem.y, knob + 4)), size = 0.5, tone = Hi)
    }

    private fun ArtBuilder.bridge() {
        solid(slabOf(Plan(-19.5, 5.6, 5.0, 2.0, 0.4), ZD, TH + 0.6, P, 2), tone = Mid)
        solid(slabOf(BRIDGE, ZD + TH + 0.6, 2.8, P, 3, 0.4), tone = Mid)
        dots(
            listOf(at(-17.0, -5.0, ZD + TH + 3.4), at(-17.0, 5.0, ZD + TH + 3.4)),
            size = 0.45,
        )
        solid(cylinder(-GATE, 0.0, 1.4, GATE_Z, ZF - GATE_Z, P, 14), tone = Mid, crease = null)
    }

    private fun ArtBuilder.frontOfMagazine() {
        solid(cylinder(GATE, 0.0, 1.4, GATE_Z, ZF - GATE_Z, P, 14), tone = Mid, crease = null)
        val bar = Plan(-1.5, R + 0.4, 3.0, 1.8, 0.4)
        solid(slabOf(bar, ZD, ZF - ZD, P, 2), tone = Mid)
        // A token-height scale on the bar's face: a tick at every token, longer every four.
        val face = bar.y + bar.d
        for (index in 0..CAPACITY) {
            val z = ZD + index * TH
            val long = index % 4 == 0
            line(
                segment(
                    Point3(bar.x, face, z),
                    Point3(bar.x + if (long) bar.w else 1.6, face, z),
                    P,
                ),
                tone = if (long) Mid else Lo,
            )
        }
    }

    private fun ArtBuilder.feed() {
        solid(cylinder(WC.x, WC.y, FEED_R, ZF, PLATE_T, P, 72, 0.8), tone = Mid)
        val ticks = radialTicks(WC.x, WC.y, FEED_R - 0.8, 40, 10, ZF + PLATE_T, 1.2 to 2.4, P)
        line(ticks.minor, tone = Lo)
        line(ticks.major, tone = Mid)
        solid(cylinder(WC.x, WC.y, WHEEL_R, ZF + PLATE_T, WHEEL_T, P, 72, 0.6), tone = Mid)
    }

    private fun ArtBuilder.hub() {
        solid(cylinder(WC.x, WC.y, 5.0, WHEEL_TOP, 2.0, P, 28), tone = Mid)
        solid(cylinder(WC.x, WC.y, 3.4, WHEEL_TOP + 2, 3.0, P, 24, 0.3), tone = Mid)
        line(knurl(WC.x, WC.y, 3.4, WHEEL_TOP + 2, WHEEL_TOP + 5, 22, P), tone = Lo)
        dots(listOf(at(WC.x, WC.y, WHEEL_TOP + 5)), size = 0.5, tone = Hi)
    }

    private fun ArtBuilder.hopper() {
        val clamp = Plan(PILLAR.x - 4, PILLAR.y - 4, 8.0, 8.0, 1.5)
        solid(slabOf(clamp, CLAMP_Z, 8.0, P, 3, 0.5), tone = Mid)
        solid(
            cylinder(PILLAR.x, PILLAR.y, 3.0, CLAMP_Z + 8, HOPPER_TOP - CLAMP_Z - 8, P, 24),
            tone = Mid,
            crease = null,
        )
        solid(cylinder(PILLAR.x, PILLAR.y, 3.6, HOPPER_TOP, 1.6, P, 24), tone = Mid)
        val arm =
            Plan(clamp.x + clamp.w, HOPPER.y - 2, HOPPER.x - HOPPER_R - clamp.x - 8 + 0.6, 4.0, 0.8)
        solid(slabOf(arm, CLAMP_Z + 2, 4.0, P, 2, 0.4), tone = Mid)
        val z0 = WHEEL_TOP + 0.6
        solid(
            cylinder(HOPPER.x, HOPPER.y, HOPPER_R, z0, HOPPER_TOP - z0, P, 48),
            tone = Mid,
            crease = null,
            inner =
                art {
                    line(
                        sideArc(HOPPER.x, HOPPER.y, HOPPER_R, z0 + 4, P, 28) +
                            sideArc(HOPPER.x, HOPPER.y, HOPPER_R, HOPPER_TOP - 4, P, 28),
                        tone = Faint,
                    )
                },
        )
        solid(cylinder(HOPPER.x, HOPPER.y, HOPPER_R + 1.0, HOPPER_TOP, 2.0, P, 48, 0.5), tone = Mid)
        line(ring(HOPPER.x, HOPPER.y, HOPPER_R - 1.2, HOPPER_TOP + 2, P, 40), tone = Lo)
        // The supply: the top token in the hopper.
        line(ring(HOPPER.x, HOPPER.y, R, HOPPER_TOP - 2, P, 40), tone = Faint)
    }

    private fun ArtBuilder.box() {
        solid(
            slabOf(BOX, 0.0, BOX_H, P, 4, 1.0),
            tone = Mid,
            inner = art { line(sideSeam(BOX, 9.0, P, 4), tone = Faint) },
        )
        line(planOutline(insetPlan(BOX, 3.0), BOX_H, P, 4), tone = Faint)
        dots(corners(BOX, 5.0).map { at(it.x, it.y, BOX_H) }, size = 0.5)
        dots(dotGrid(BOX.x + 8.0, BOX.y + 9.0, 4, 4, 3.0, BOX_H, P), size = 0.45, tone = Lo)
        val pull = Plan(BOX.x + 8, BOX.y + BOX.d, 10.0, 2.2, 0.8)
        solid(slabOf(pull, 3.0, 2.4, P, 3, 0.3), tone = Mid)
    }

    /** Everything up to and including the plunger and the back of the magazine. */
    val LOW: Art = art {
        base()
        deck()
        pillarBelow()
        column()
        backOfMagazine()
        group("plunger") { plunger() }
        bridge()
    }

    /** The front of the magazine: the gate and the graduated bar. */
    val FRONT: Art = art { frontOfMagazine() }

    /** The feed plate and the wheel, under the per-frame pockets. */
    val FEED: Art = art { feed() }

    /** The hub, the hopper on its pillar, and the payout box. */
    val TOP: Art = art {
        hub()
        hopper()
        box()
    }

    /** One token in the magazine at slot 0; moved up by its slot. */
    val STACKED: Art = art { group("token") { token(lit = true) } }

    /** A token on its way out to the payout box. */
    val SPENT: Art = art { group("token") { token(lit = false) } }

    private fun ArtBuilder.token(lit: Boolean) {
        solid(cylinder(0.0, 0.0, R, ZD, TH, P, 48, 0.4), tone = Hi, crease = Lo, lit = lit)
        line(ring(0.0, 0.0, R - 2.2, ZD + TH, P, 40), tone = Faint)
    }

    /** A pocket in the wheel at the wheel's centre; moved round per frame. */
    val POCKET_BORE: Art = art {
        group("pocket") { line(ring(WC.x, WC.y, R + 0.6, WHEEL_TOP, P, 40), tone = Lo) }
    }

    /** A screw in the wheel's top, between two pockets; moved round per frame. */
    val WHEEL_SCREW: Art = art {
        group("pocket") {
            line(ring(WC.x, WC.y, 1.6, WHEEL_TOP, P, 14), tone = Lo)
            dots(listOf(at(WC.x, WC.y, WHEEL_TOP)), size = 0.45)
        }
    }

    /** The far wall of an empty pocket: where its floor, the feed plate, meets the bore. */
    val POCKET_FLOOR: Art = art {
        group("pocket") { line(backArc(WC.x, WC.y, R + 0.6, ZF + PLATE_T), tone = Faint) }
    }

    /** The far half of a horizontal circle: what shows of a bore's floor through its mouth. */
    private fun backArc(cx: Double, cy: Double, r: Double, z: Double): IsoPath {
        val view = towardViewer(P)
        val facing = atan2(view.y, view.x)
        return onTop(
            (0..24).map {
                val angle = facing + PI / 2 + it / 24.0 * PI
                Floor(cx + r * cos(angle), cy + r * sin(angle))
            },
            z,
            P,
        )
    }

    /** A token sitting in a pocket, flush with the wheel's top. */
    val POCKET_TOKEN: Art = art {
        group("pocket") {
            solid(cylinder(WC.x, WC.y, R, WHEEL_TOP - 0.3, 0.3, P, 40), tone = Hi, flat = true)
            line(ring(WC.x, WC.y, R - 2.2, WHEEL_TOP, P, 40), tone = Faint)
        }
    }

    /**
     * How far the stack's new top token falls: from its pocket in the wheel, through the hole in
     * the feed plate (which hides it until it is clear), down onto the stack.
     */
    fun dropHeight(count: Int): Double = (WHEEL_TOP - TH) - (ZD + (count - 1) * TH)

    /** Spent tokens past this point are in front of the gate and the bar. */
    const val CLEAR_OF_GATE = GATE + R + 2

    /** Where the pockets are, for [added] tokens fed: offset from the wheel centre, and full. */
    fun pockets(added: Double): List<Triple<Double, Double, Boolean>> {
        val first = floor(added)
        return (0..3)
            .map { index ->
                val p = first + index - added
                val angle = 3 * PI / 4 - p * PI / 2
                Triple(POCKET * sin(angle), -POCKET * cos(angle), p > 1e-9 && p <= 2 + 1e-9)
            }
            .sortedBy { depthOf(Point3(WC.x + it.first, WC.y + it.second, 0.0), P) }
    }

    /** The wheel's three screws, between the pockets the index mark doesn't use. */
    fun screws(added: Double): List<Pair<Double, Double>> =
        (1..3).map {
            val angle = PI + added * PI / 2 + it * PI / 2
            Pair(24.0 * sin(angle), -24.0 * cos(angle))
        }

    /** The wheel's index mark, between two pockets, for [added] tokens fed. */
    fun indexAt(added: Double): IsoPath {
        val angle = PI + added * PI / 2
        val sx = sin(angle)
        val sy = -cos(angle)
        return lineOnTop(
            Floor(WC.x + sx * 5.0, WC.y + sy * 5.0),
            Floor(WC.x + sx * WHEEL_R, WC.y + sy * WHEEL_R),
            WHEEL_TOP,
            P,
        )
    }

    /** Paints the figure in painter's order, with the moving parts in their slots. */
    fun IsoScope.paint(view: View) {
        val count = floor(view.tokens + 1e-9).toInt()
        drawArt(LOW) { part ->
            if (part == "plunger") translateAlong(P, Point3(STROKE * view.push, 0.0, 0.0)) else null
        }
        // A token on its way out lies under the stack riding on the blade: paint it first.
        val spent = view.spent
        for (x in spent) {
            if (x < CLEAR_OF_GATE) drawArt(SPENT) { translateAlong(P, Point3(x, 0.0, 0.0)) }
        }
        for (index in 0 until count) {
            val z = index * TH + view.lift + if (index == count - 1) view.fall else 0.0
            drawArt(STACKED) { translateAlong(P, Point3(0.0, 0.0, z)) }
        }
        drawArt(FRONT)
        for (x in spent) {
            if (x >= CLEAR_OF_GATE) drawArt(SPENT) { translateAlong(P, Point3(x, 0.0, 0.0)) }
        }
        drawArt(FEED)
        for ((dx, dy, full) in pockets(view.added)) {
            val offset = { _: String -> translateAlong(P, Point3(dx, dy, 0.0)) }
            if (full) drawArt(POCKET_TOKEN, offset) else drawArt(POCKET_FLOOR, offset)
            drawArt(POCKET_BORE, offset)
        }
        for ((dx, dy) in screws(view.added)) {
            drawArt(WHEEL_SCREW) { translateAlong(P, Point3(dx, dy, 0.0)) }
        }
        line(indexAt(view.added), tone = Hi, width = 0.9)
        drawArt(TOP)
    }

    /** What the canvas reads each frame: snapshot state, written by the loop, read in draw. */
    class View {
        var tokens by mutableDoubleStateOf(CAPACITY.toDouble())
        var added by mutableDoubleStateOf(0.0)
        var push by mutableDoubleStateOf(0.0)
        var lift by mutableDoubleStateOf(0.0)
        var fall by mutableDoubleStateOf(0.0)
        var spent: List<Double> by mutableStateOf(emptyList())
    }
}

/**
 * The dispenser's timeline: the plunger's stroke, a spent token's slide, a token's fall, the idle
 * tour and the readout. Plain functions of time, so the frame loop only keeps ages.
 */
object DispenserMotion {
    const val CAPACITY = 8
    const val RATE = 1.0

    /** A token's radius. */
    const val R = 10.0

    /** The blade's front face at rest, and how far one stroke pushes it. */
    const val HOME = -R - 0.5
    const val STROKE = 21.0

    /** Where the tray meets the payout box. */
    const val TRAY_END = 34.0

    /** The stroke: out (accelerating), hold, back, in seconds. */
    const val OUT = 0.22
    const val HOLD = 0.06
    const val BACK = 0.2
    const val STROKE_S = OUT + HOLD + BACK
    private const val FRICTION = 520.0

    /** How far out the plunger is, 0 to 1, [age] seconds into a stroke. */
    fun pushAt(age: Double): Double =
        when {
            age < 0 -> 0.0
            age < OUT -> (age / OUT) * (age / OUT)
            age < OUT + HOLD -> 1.0
            age < STROKE_S -> {
                val t = (age - OUT - HOLD) / BACK
                1 - t * t * (3 - 2 * t)
            }
            else -> 0.0
        }

    /** Whether the blade, [age] seconds into a stroke, is still under the stack holding it up. */
    fun underStack(age: Double): Boolean = HOME + STROKE * pushAt(age) > -R

    /**
     * Where a spent token's centre is along the tray, [age] seconds after its stroke began, or null
     * once it is inside the payout box. The blade pushes it, then it slides on and slows.
     */
    fun spentAt(age: Double): Double? {
        val pushed = HOME + STROKE * pushAt(min(age, OUT)) + R
        if (age < OUT) return maxOf(0.0, pushed)
        val speed = 2 * STROKE / OUT
        val t = age - OUT
        val stop = speed / FRICTION
        val slid = if (t < stop) speed * t - FRICTION * t * t / 2 else speed * stop / 2
        val x = pushed + slid
        return if (x - R > TRAY_END + 1 || t >= stop) null else x
    }

    fun readoutOf(tokens: Double, outcome: Boolean?): String {
        val word =
            when {
                outcome == true -> "passed"
                outcome == false -> "refused"
                tokens >= CAPACITY -> "full"
                else -> "refilling"
            }
        return "$word · ${oneDecimal(tokens)} of $CAPACITY tokens"
    }

    /** The tour: a burst of 24 requests, 2.5 a second, then 8.5 s to refill. */
    const val TOUR_PERIOD = 20.0
    private const val TOUR_FROM = 1.5
    private const val TOUR_GAP = 0.4
    private const val TOUR_COUNT = 24

    /** How many tour requests have been sent by [t] seconds. */
    fun tourSent(t: Double): Int {
        val cycles = floor(t / TOUR_PERIOD)
        val within = t - cycles * TOUR_PERIOD
        val here =
            if (within < TOUR_FROM) 0
            else min(TOUR_COUNT, floor((within - TOUR_FROM) / TOUR_GAP).toInt() + 1)
        return cycles.toInt() * TOUR_COUNT + here
    }

    /** A fall under gravity onto a stop at 0, with one small bounce. Returns true when at rest. */
    fun drop(state: Spring, dt: Double, calm: Boolean): Boolean {
        if (calm || state.at <= 0 && abs(state.rate) < 1e-3) {
            state.at = 0.0
            state.rate = 0.0
            return true
        }
        state.rate -= GRAVITY * dt
        state.at += state.rate * dt
        if (state.at <= 0) {
            state.at = 0.0
            state.rate = if (-state.rate > 25) -state.rate * 0.2 else 0.0
        }
        return state.at == 0.0 && state.rate == 0.0
    }

    private const val GRAVITY = 900.0
}

private const val IDLE_MS = 3_600L
private const val OUTCOME_S = 1.4
private const val QUEUE = 4

@Composable
fun TokenBucketFigure(palette: Palette, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val d = TokenBucketDispenser
    val m = DispenserMotion
    val state = remember {
        object {
            val bucket = TokenBucket(m.CAPACITY, m.RATE)
            var pending = 0
            var stroke = -1.0
            val spent = ArrayList<Double>()
            val lift = Spring(0.0)
            val fall = Spring(0.0)
            var outcome: Boolean? = null
            var outcomeAge = 0.0
            var touring = true
            var clock = 0.0
            val view = TokenBucketDispenser.View()
        }
    }
    var readout by remember { mutableStateOf(m.readoutOf(state.bucket.tokens, null)) }
    var touches by remember { mutableIntStateOf(0) }
    if (reducedMotion) state.touring = false
    val kick =
        rememberFrameLoop(reducedMotion) { dt, calm ->
            val s = state
            if (s.touring) {
                val before = m.tourSent(s.clock)
                s.clock += dt
                s.pending = min(QUEUE, s.pending + m.tourSent(s.clock) - before)
            }
            if (s.bucket.advance(dt) > 0) {
                s.fall.at = d.dropHeight(floor(s.bucket.tokens + 1e-9).toInt())
                s.fall.rate = 0.0
            }
            if (s.stroke < 0 && s.pending > 0) {
                s.pending--
                val passed = s.bucket.request()
                s.outcome = passed
                s.outcomeAge = 0.0
                s.stroke = 0.0
                if (passed) {
                    if (!calm) s.spent.add(0.0)
                    s.lift.at = d.LIFT
                    s.lift.rate = 0.0
                }
            }
            if (s.stroke >= 0) {
                s.stroke += dt
                if (calm || s.stroke >= m.STROKE_S) s.stroke = -1.0
            }
            for (index in s.spent.indices) s.spent[index] += dt
            s.spent.removeAll { m.spentAt(it) == null }
            val holding = s.stroke >= 0 && m.underStack(s.stroke)
            val lifted = holding || m.drop(s.lift, dt, calm).not()
            val fallen = m.drop(s.fall, dt, calm)
            s.outcomeAge += dt
            if (s.outcomeAge > OUTCOME_S && s.stroke < 0) s.outcome = null
            val v = s.view
            v.tokens = s.bucket.tokens
            v.added = s.bucket.added
            v.push = if (calm) 0.0 else m.pushAt(s.stroke)
            v.lift = s.lift.at
            v.fall = s.fall.at
            v.spent = s.spent.mapNotNull { m.spentAt(it) }
            readout = m.readoutOf(s.bucket.tokens, s.outcome)
            s.touring ||
                s.pending > 0 ||
                s.stroke >= 0 ||
                s.spent.isNotEmpty() ||
                lifted ||
                !fallen ||
                s.outcome != null ||
                s.bucket.tokens < m.CAPACITY
        }
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay(IDLE_MS)
        if (!reducedMotion) {
            state.touring = true
            state.clock = 0.0
            kick()
        }
    }
    fun request() {
        state.touring = false
        state.pending = min(QUEUE, state.pending + 1)
        touches++
        kick()
    }
    Plate(
        fig = "Fig 2",
        title = "Token bucket",
        hint = "Click or press Space to send a request",
        readout = { readout },
        palette = palette,
        modifier = modifier,
        keys =
            listOf(
                LegendKey(KeyMark.Lit, "Tokens in the magazine: the bucket, at most 8"),
                LegendKey(KeyMark.Raised, "Feed wheel: one token a second, a tick per 0.1"),
            ),
        caption =
            "A token dispenser. The wheel feeds one token a second into a magazine that holds " +
                "eight, and stops while it is full. Each request presses the plunger: it passes " +
                "if there is a whole token to push out, and is refused if not, so a burst of " +
                "eight gets through at once and after that one a second.",
    ) {
        IsoCanvas(
            d.WIDTH,
            d.HEIGHT,
            palette,
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = d.LABEL
                    stateDescription = readout
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) request()
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Spacebar,
                        Key.Enter -> {
                            request()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            with(d) { paint(state.view) }
        }
    }
}
