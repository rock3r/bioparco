// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
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
import dev.sebastiano.anatomy.kit.Anchor
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Spring
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.Tone.Faint
import dev.sebastiano.anatomy.kit.Tone.Hi
import dev.sebastiano.anatomy.kit.Tone.Lo
import dev.sebastiano.anatomy.kit.Tone.Mid
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.follow
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.knurl
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.radialTicks
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.sideArc
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.spring
import dev.sebastiano.anatomy.kit.topMatrix
import dev.sebastiano.anatomy.kit.topTicks
import dev.sebastiano.anatomy.kit.translateAlong
import dev.sebastiano.anatomy.oneDecimal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

/*
 * `examples/dial-indicator/build.mjs` of the original: a dial gauge on a stand over a carriage of
 * gauge blocks. The smallest example. Hover to slide the carriage; the plunger rides onto the block
 * beneath it, and one turn of the needle is 20 px of travel, so each block reads its own height.
 */
object DialIndicator {
    const val WIDTH = 600.0
    const val HEIGHT = 380.0

    private val PLATE = Plan(-128.0, -64.0, 262.0, 128.0, 8.0)
    private const val PLATE_H = 12.0
    private const val FOOT_H = 5.0

    private val MAG = Plan(-112.0, -20.0, 44.0, 40.0, 3.0)
    private const val MAG_H = 24.0
    private val POST = Floor(-98.0, 0.0)
    private const val POST_TOP = 122.0
    private const val CLAMP_Z = 100.0

    private val DIAL = Floor(58.0, 0.0)
    private const val DIAL_R = 20.0
    private const val DIAL_Z = 82.0
    private const val DIAL_H = 10.0
    private const val DIAL_TOP = DIAL_Z + DIAL_H
    const val PER_TURN = 20.0

    private const val RAIL_FROM = 2.0
    private const val RAIL_TO = 124.0
    private val CARRIAGE = Plan(-28.0, -24.0, 56.0, 48.0, 3.0)
    private const val CARRIAGE_Z = 3.0
    const val CARRIAGE_TOP = 8.0
    val BLOCKS = listOf(18.0, 14.0, 10.0)
    private const val BLOCK_W = 15.6
    private const val STEP = 16.0
    private const val TIP_R = 2.6
    private const val CENTRE = -24 + BLOCK_W / 2

    /** Where the carriage stops with each block under the plunger, block 1 first. */
    val STOPS: List<Double> = BLOCKS.indices.map { DIAL.x - CENTRE - STEP * it }

    /** How far the pointer can slide the carriage. */
    val REACH: ClosedFloatingPointRange<Double> = STOPS.last() - 6..STOPS.first() + 6

    val P =
        fitProjection(
            boxCorners(PLATE, -PLATE_H - FOOT_H, 0.0) +
                listOf(
                    Point3(POST.x, POST.y, POST_TOP + 2),
                    Point3(DIAL.x + DIAL_R, 0.0, DIAL_TOP),
                ),
            WIDTH,
            HEIGHT,
            pad = 30.0,
            azimuth = 42.0,
        )

    const val LABEL =
        "A dial indicator on a surface plate. A magnetic base holds a post and an arm; the arm " +
            "carries a dial whose plunger rests on three gauge blocks, 18, 14 and 10 tall, on a " +
            "carriage that slides along two rails. Wherever the carriage stops, the plunger rises to " +
            "the block under it and the needle turns by the same amount."

    private fun at(x: Double, y: Double, z: Double) = iso(Point3(x, y, z), P)

    private fun byDepth(points: List<Floor>) = points.sortedBy {
        depthOf(Point3(it.x, it.y, 0.0), P)
    }

    private fun ArtBuilder.plate() {
        halo(haloOf(PLATE, -PLATE_H - FOOT_H, PLATE_H, P))
        for ((x, y) in byDepth(corners(PLATE, 22.0))) {
            solid(cylinder(x, y, 7.0, -PLATE_H - FOOT_H, 1.6, P, 28), tone = Lo)
            solid(cylinder(x, y, 5.4, -PLATE_H - FOOT_H + 1.6, FOOT_H - 1.6, P, 28), tone = Mid)
        }
        solid(slabOf(PLATE, -PLATE_H, PLATE_H, P, 8, 1.6), tone = Mid)
        line(planOutline(insetPlan(PLATE, 5.0), 0.0, P), tone = Lo)
        val screws = corners(PLATE, 10.0)
        line(IsoPath.join(screws.map { ring(it.x, it.y, 2.2, 0.0, P, 16) }), tone = Lo)
        dots(screws.map { at(it.x, it.y, 0.0) }, size = 0.5)
        val strip = Plan(-110.0, PLATE.y + PLATE.d - 15, 230.0, 7.0, 1.0)
        solid(slabOf(strip, 0.0, 0.6, P, 3), tone = Lo, crease = null)
        val ticks =
            topTicks(strip.x + 4, strip.x + strip.w - 4, 4.0, 5, strip.y + 0.8, 0.6, 2.0 to 3.6, P)
        line(ticks.minor, tone = Lo)
        line(ticks.major, tone = Mid)
        dots(
            listOf(
                at(strip.x + 2, strip.y + 3.5, 0.6),
                at(strip.x + strip.w - 2, strip.y + 3.5, 0.6),
            ),
            size = 0.45,
        )
    }

    private fun ArtBuilder.rails() {
        for (y in listOf(-20.0, 15.0)) {
            solid(
                slabOf(Plan(RAIL_FROM, y, RAIL_TO - RAIL_FROM, 5.0, 1.0), 0.0, 3.0, P, 3, 0.4),
                tone = Mid,
            )
            dots(listOf(RAIL_FROM + 6, RAIL_TO - 6).map { at(it, y + 2.5, 3.0) }, size = 0.4)
        }
        for (x in listOf(RAIL_FROM - 5, RAIL_TO)) {
            solid(slabOf(Plan(x, -23.0, 5.0, 46.0, 1.2), 0.0, 7.0, P, 3, 0.5), tone = Mid)
            dots(listOf(at(x + 2.5, -18.0, 7.0), at(x + 2.5, 18.0, 7.0)), size = 0.45)
        }
    }

    private fun ArtBuilder.fixture() {
        solid(slabOf(MAG, 0.0, 11.0, P, 6, 0.8), tone = Mid)
        solid(slabOf(insetPlan(MAG, 0.8), 11.0, MAG_H - 11, P, 6, 0.8), tone = Mid)
        line(planOutline(insetPlan(MAG, 3.0), MAG_H, P), tone = Faint)
        dots(
            listOf(
                at(MAG.x + 5, MAG.y + 5, MAG_H),
                at(MAG.x + 5, MAG.y + MAG.d - 5, MAG_H),
                at(MAG.x + MAG.w - 5, MAG.y + 5, MAG_H),
            ),
            size = 0.45,
        )
        val knob = Floor(MAG.x + 32, MAG.y + 28)
        solid(cylinder(knob.x, knob.y, 6.0, MAG_H, 4.0, P, 32), tone = Mid)
        line(knurl(knob.x, knob.y, 6.0, MAG_H, MAG_H + 4, 36, P), tone = Lo)
        line(lineOnTop(knob, Floor(knob.x + 6, knob.y), MAG_H + 4, P), tone = Hi)
        dots(listOf(at(knob.x, knob.y, MAG_H + 4)), size = 0.5, tone = Hi)
        solid(cylinder(POST.x, POST.y, 5.0, MAG_H, 4.0, P, 28), tone = Mid)
        val clamp = Plan(POST.x - 7, POST.y - 7, 14.0, 14.0, 2.5)
        solid(
            cylinder(POST.x, POST.y, 3.2, MAG_H + 4, CLAMP_Z - 4 - MAG_H - 4, P, 28),
            tone = Mid,
            crease = null,
        )
        line(
            IsoPath.join(listOf(60.0, 80.0).map { sideArc(POST.x, POST.y, 3.2, it, P, 16) }),
            Faint,
        )
        solid(slabOf(clamp, CLAMP_Z - 4, 12.0, P, 4, 0.6), tone = Mid)
        solid(
            cylinder(POST.x, POST.y, 3.2, CLAMP_Z + 8, POST_TOP - CLAMP_Z - 8, P, 28),
            tone = Mid,
            crease = null,
        )
        solid(cylinder(POST.x, POST.y, 3.8, POST_TOP, 2.0, P, 28), tone = Mid)
        val arm = Plan(POST.x + 7, -3.0, DIAL.x - DIAL_R - POST.x - 7 + 2, 6.0, 2.0)
        solid(slabOf(arm, CLAMP_Z, 4.0, P, 4, 0.5), tone = Mid)
        dots(listOf(at(arm.x + 4, 0.0, CLAMP_Z + 4), at(arm.x + arm.w - 5, 0.0, CLAMP_Z + 4)), 0.45)
        val thumb = Floor(clamp.x + 7, clamp.y + clamp.d + 3)
        solid(
            slabOf(Plan(thumb.x - 1.4, clamp.y + clamp.d, 2.8, 3.0, 0.8), CLAMP_Z + 1, 4.0, P, 2),
            tone = Mid,
            crease = null,
        )
        solid(cylinder(thumb.x, thumb.y + 1.4, 2.6, CLAMP_Z, 6.0, P, 20), tone = Mid)
        line(knurl(thumb.x, thumb.y + 1.4, 2.6, CLAMP_Z, CLAMP_Z + 6, 18, P), tone = Lo)
    }

    private fun ArtBuilder.carriage() {
        halo(haloOf(insetPlan(CARRIAGE, 3.0), CARRIAGE_Z, 0.1, P), blur = 4.0)
        solid(slabOf(CARRIAGE, CARRIAGE_Z, CARRIAGE_TOP - CARRIAGE_Z, P, 5, 0.6), tone = Mid)
        dots(
            listOf(-22.0, -8.0, 8.0, 22.0).map {
                at(it, CARRIAGE.y + CARRIAGE.d - 2.6, CARRIAGE_TOP)
            },
            size = 0.4,
            tone = Lo,
        )
        val left = CARRIAGE.x
        val right = CARRIAGE.x + CARRIAGE.w
        line(lineOnTop(Floor(left, -15.5), Floor(right, -15.5), CARRIAGE_TOP, P), tone = Faint)
        line(lineOnTop(Floor(left, 15.5), Floor(right, 15.5), CARRIAGE_TOP, P), tone = Faint)
        BLOCKS.forEachIndexed { index, height ->
            val plan = Plan(-24 + STEP * index, -12.0, BLOCK_W, 24.0, 1.0)
            solid(slabOf(plan, CARRIAGE_TOP, height, P, 3, 0.5), tone = Hi, crease = Lo)
            line(planOutline(insetPlan(plan, 2.0), CARRIAGE_TOP + height, P, 3), tone = Faint)
        }
        val handle = Floor(CARRIAGE.x + CARRIAGE.w - 8, CARRIAGE.y + CARRIAGE.d + 3)
        solid(
            slabOf(
                Plan(handle.x - 2, CARRIAGE.y + CARRIAGE.d - 1, 4.0, 4.0, 1.0),
                CARRIAGE_Z + 1,
                3.0,
                P,
                2,
            ),
            tone = Mid,
            crease = null,
        )
        solid(cylinder(handle.x, handle.y + 1, 3.0, CARRIAGE_Z, 5.0, P, 20), tone = Mid)
        line(knurl(handle.x, handle.y + 1, 3.0, CARRIAGE_Z, CARRIAGE_Z + 5, 18, P), tone = Lo)
    }

    private fun ArtBuilder.plunger() {
        solid(cylinder(DIAL.x, DIAL.y, TIP_R, 0.0, 2.4, P, 20), tone = Hi, lit = true)
        solid(cylinder(DIAL.x, DIAL.y, 1.4, 2.4, 50.0, P, 16), tone = Hi, crease = null, lit = true)
    }

    private fun ArtBuilder.dial() {
        val (x, y) = DIAL
        solid(cylinder(x, y, 2.9, 54.0, DIAL_Z - 54, P, 20), tone = Mid)
        line(sideArc(x, y, 2.9, 60.0, P, 12), tone = Lo)
        solid(
            slabOf(
                Plan(x - DIAL_R - 6, -4.0, 8.0, 8.0, 1.5),
                DIAL_Z + 2,
                CLAMP_Z - DIAL_Z - 2,
                P,
                3,
                0.4,
            ),
            tone = Mid,
        )
        solid(cylinder(x, y, DIAL_R, DIAL_Z, DIAL_H, P, 64, 0.8), tone = Hi, crease = Lo)
        line(knurl(x, y, DIAL_R, DIAL_TOP - 3.4, DIAL_TOP, 84, P, 0.3), tone = Lo)
        line(sideArc(x, y, DIAL_R, DIAL_TOP - 3.4, P, 40), tone = Faint)
        line(ring(x, y, DIAL_R - 2.2, DIAL_TOP, P, 64), tone = Lo)
        val ticks = radialTicks(x, y, DIAL_R - 3, 50, 5, DIAL_TOP, 1.3 to 2.8, P)
        line(ticks.minor, tone = Lo)
        line(ticks.major, tone = Mid)
        solid(slabOf(Plan(x + DIAL_R - 1, -2.4, 5.0, 4.8, 1.2), DIAL_Z + 2, 5.0, P, 3), tone = Mid)
        solid(
            slabOf(Plan(x + DIAL_R + 3.6, -3.2, 2.6, 6.4, 1.2), DIAL_Z + 1.2, 6.6, P, 3),
            tone = Mid,
        )
        line(ring(x, y, 1.6, DIAL_TOP, P, 16), tone = Mid)
        faceText(
            topMatrix(Floor(x, y + 9), DIAL_TOP, P),
            "0.01",
            size = 2.4,
            tone = Lo,
            anchor = Anchor.Middle,
        )
    }

    /** The static art, painted back to front. `carriage` and `plunger` are moved live. */
    val ART: Art = art {
        plate()
        rails()
        fixture()
        group("carriage") { carriage() }
        group("plunger") { plunger() }
        dial()
    }

    /** The needle for [turn] turns of the dial. Rebuilt each frame: rotations can't be offsets. */
    fun needleAt(turn: Double): IsoPath {
        val angle = turn * PI * 2
        val (x, y) = DIAL
        return lineOnTop(
            Floor(x - sin(angle) * 3.4, y + cos(angle) * 3.4),
            Floor(x + sin(angle) * (DIAL_R - 4), y - cos(angle) * (DIAL_R - 4)),
            DIAL_TOP + 0.2,
            P,
        )
    }

    /** The height of the plunger's tip with the carriage at [cx]: the tallest block under it. */
    fun heightUnder(cx: Double): Double {
        var top = CARRIAGE_TOP
        BLOCKS.forEachIndexed { index, height ->
            val from = cx - 24 + STEP * index
            val to = from + BLOCK_W
            if (to > DIAL.x - TIP_R && from < DIAL.x + TIP_R) top = max(top, CARRIAGE_TOP + height)
        }
        return top
    }

    /** The block whose top is under the plunger's axis, or -1 between blocks. */
    fun blockUnder(cx: Double): Int =
        BLOCKS.indices.indexOfFirst { index ->
            val from = cx - 24 + STEP * index
            DIAL.x >= from && DIAL.x <= from + BLOCK_W
        }

    fun readoutOf(cx: Double, tip: Double): String {
        val index = blockUnder(cx)
        val where = if (index < 0) "between blocks" else "block ${index + 1}"
        return "$where · reads ${oneDecimal(tip - CARRIAGE_TOP)} px"
    }

    /** The idle tour: block to block, 2.2 s each. */
    fun tourAt(t: Double): Double = STOPS[floor(t / 2.2).toInt() % STOPS.size]
}

private const val IDLE_MS = 3_600L

@Composable
fun DialIndicatorFigure(
    palette: Palette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    isOnScreen: () -> Boolean = { true },
) {
    val d = DialIndicator
    val state = remember {
        object {
            var x = d.STOPS.first()
            var target = x
            val tip = Spring(d.CARRIAGE_TOP + d.BLOCKS.first())
            var touring = true
            var clock = 0.0
        }
    }
    // What the canvas reads: written by the loop, read in the draw phase only.
    var carriageX by remember { mutableDoubleStateOf(state.x) }
    var tipAt by remember { mutableDoubleStateOf(state.tip.at) }
    var readout by remember { mutableStateOf(d.readoutOf(state.x, state.tip.at)) }
    var touches by remember { mutableIntStateOf(0) }
    if (reducedMotion) state.touring = false
    val kick =
        rememberFrameLoop(reducedMotion, isOnScreen, trace = "dial-indicator tick") { dt, calm ->
            val s = state
            if (s.touring) {
                s.clock += dt
                s.target = d.tourAt(s.clock)
            }
            s.x = follow(s.x, s.target, dt, 0.28, calm)
            val want = d.heightUnder(s.x)
            val settled = spring(s.tip, want, dt, 420.0, 26.0, calm)
            readout = d.readoutOf(s.x, s.tip.at)
            carriageX = s.x
            tipAt = s.tip.at
            s.touring || s.x != s.target || !settled
        }
    LaunchedEffect(touches, reducedMotion) {
        if (touches == 0) return@LaunchedEffect
        delay(IDLE_MS)
        if (!reducedMotion) {
            state.touring = true
            state.clock = 0.0
            kick()
        }
    }
    fun steer(next: Double) {
        state.touring = false
        state.target = next.coerceIn(d.REACH)
        touches++
        kick()
    }
    Plate(
        fig = "Fig 1",
        title = "Dial indicator",
        hint = "Hover to slide the carriage",
        readout = { readout },
        palette = palette,
        modifier = modifier,
        keys =
            listOf(
                LegendKey(KeyMark.Lit, "Plunger and needle: what moves when the height changes"),
                LegendKey(KeyMark.Raised, "Three gauge blocks, 18, 14 and 10 tall"),
            ),
        caption =
            "A dial indicator on a surface plate. Slide the carriage and the plunger rides up or " +
                "down onto the block beneath it; one turn of the needle is 20 px of travel, so " +
                "every block reads its own height.",
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
                            val position = event.changes.firstOrNull()?.position ?: continue
                            when (event.type) {
                                PointerEventType.Move,
                                PointerEventType.Enter -> {
                                    val share =
                                        ((position.x / size.width - 0.25) / 0.5).coerceIn(0.0, 1.0)
                                    steer(
                                        d.REACH.start +
                                            share * (d.REACH.endInclusive - d.REACH.start)
                                    )
                                }
                                PointerEventType.Exit -> steer(state.target)
                            }
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val here = d.STOPS.indices.minBy { abs(d.STOPS[it] - state.target) }
                    val to =
                        when (event.key) {
                            Key.DirectionRight,
                            Key.DirectionUp -> max(0, here - 1)
                            Key.DirectionLeft,
                            Key.DirectionDown -> minOf(d.STOPS.size - 1, here + 1)
                            Key.MoveHome -> 0
                            Key.MoveEnd -> d.STOPS.size - 1
                            else -> return@onKeyEvent false
                        }
                    steer(d.STOPS[to])
                    true
                }
                .focusable(),
            trace = "dial-indicator draw",
        ) {
            val x = carriageX
            val tip = tipAt
            drawArt(d.ART) { part ->
                when (part) {
                    "carriage" -> translateAlong(d.P, Point3(x, 0.0, 0.0))
                    "plunger" -> translateAlong(d.P, Point3(0.0, 0.0, tip))
                    else -> null
                }
            }
            line(
                d.needleAt((tip - d.CARRIAGE_TOP) / d.PER_TURN),
                tone = Tone.Lit,
                width = 1.1,
                // The tail stops on the bare dial face, as the original's `data-free="start"`.
                free = Free.Start,
            )
        }
    }
}
