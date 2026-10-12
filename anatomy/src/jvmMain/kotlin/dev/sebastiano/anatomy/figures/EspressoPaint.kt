package dev.sebastiano.anatomy.figures

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import dev.sebastiano.anatomy.figures.EspressoMachine.GROUP
import dev.sebastiano.anatomy.figures.EspressoMachine.GROUP_X
import dev.sebastiano.anatomy.figures.EspressoMachine.P
import dev.sebastiano.anatomy.figures.EspressoMachine.SPOUT_Z
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.AuditItem
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.pointAlong
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.scaleOf
import dev.sebastiano.anatomy.kit.solidOf
import dev.sebastiano.anatomy.kit.sphereOf
import dev.sebastiano.anatomy.kit.toComposePath
import dev.sebastiano.anatomy.kit.tubePieces
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/*
 * What the live figure draws over the settled art each frame: the lever at its angle, the gauge's
 * needle, the water moving in the pipes, the coffee falling into the cup and the cup filling.
 */
internal object EspressoPaint {
    /** What one frame shows. Flows are in ml a second, by water route. */
    class View {
        var lever by mutableDoubleStateOf(LEVER_DOWN)
        var pressure by mutableDoubleStateOf(0.0)
        var clock by mutableDoubleStateOf(0.0)
        var flows: Map<String, Double> by mutableStateOf(emptyMap())
        var grams by mutableDoubleStateOf(0.0)
        var pour by mutableDoubleStateOf(0.0)
    }

    private val parts = EspressoMachine.PARTS
    private val order: List<AuditItem> = parts.recorder.painted()

    /** The cup's inside wall, 2.2 mm in from its outside, and its floor. */
    private fun inside(z: Double) = cupRadius(z) - 2.2

    private const val CUP_FLOOR = CUP_FOOT + 3

    /** The water each bead stands for, in ml. */
    const val BEAD_ML = 0.25

    /** The coffee's height in the cup for each whole gram, from the cup's inside volume. */
    private val LEVELS: List<Double> = run {
        val out = arrayListOf(CUP_FLOOR)
        var volume = 0.0
        var z = CUP_FLOOR
        while (z < CUP_RIM - 1) {
            val r = inside(z + 0.125)
            volume += PI * r * r * 0.25
            z += 0.25
            while (volume >= out.size * 1000.0) out.add(z)
        }
        out
    }

    fun levelOf(grams: Double): Double {
        val whole = floor(grams).toInt()
        if (whole + 1 >= LEVELS.size) return LEVELS.last()
        val t = grams - whole
        return LEVELS[whole] + (LEVELS[whole + 1] - LEVELS[whole]) * t
    }

    fun IsoScope.paint(view: View) {
        for (art in parts.under) drawArt(art)
        for (item in order) {
            when (item.name) {
                "lever.arm" -> arm(view.lever)
                "lever.knob" -> solid(sphereOf(knobAt(view.lever), KNOB_R, P, 40), tone = Tone.Lo)
                else -> drawArt(item.art!!)
            }
            parts.chunks[item.name]?.let { beads(it, view) }
            when (item.name) {
                "boiler.water" -> water()
                "gauge.face" -> needle(view.pressure)
                "cup.body" -> coffee(view)
            }
        }
    }

    /** The boiler's water, seen through its cut face: a tint of the figure's ink. */
    private val WATER: Path by lazy { EspressoShell.waterFace(P).toComposePath() }

    private fun IsoScope.water() {
        draw.drawPath(WATER, palette.ink(Tone.Lo), alpha = if (palette.isDark) 0.16f else 0.10f)
    }

    private fun IsoScope.arm(angle: Double) {
        val frame = frameAlong(PIVOT, leverDirection(angle))
        solid(disc(ARM_START, LEVER_ARM, ARM_R, frame, P), tone = Tone.Hi)
    }

    private fun IsoScope.needle(bar: Double) {
        val a = dialAngle(bar)
        val s = DIAL_S + 0.2
        line(
            pathOf(listOf(iso(pointOf(GAUGE, s, -3.5, a), P), iso(pointOf(GAUGE, s, 14.6, a), P))),
            tone = Tone.Lit,
            width = 1.0,
            free = Free.Both,
        )
        dots(listOf(iso(pointOf(GAUGE, s, 0.0, 0.0), P)), size = 1.3, tone = Tone.Lit)
    }

    /** One bead every [BEAD_ML], moving at the water's true speed through the pipe's bore. */
    private fun IsoScope.beads(chunk: WaterChunk, view: View) {
        val flow = view.flows[chunk.route] ?: return
        if (flow <= 0) return
        val route = parts.water.getValue(chunk.route)
        val spacing = BEAD_ML * 1000.0 / (PI * route.bore * route.bore)
        val travel = view.clock * flow * spacing
        var at = travel % spacing + floor(chunk.from / spacing) * spacing
        val points = ArrayList<dev.sebastiano.anatomy.kit.Flat>()
        while (at <= chunk.to) {
            if (at >= chunk.from) points.add(iso(pointAlong(route.points, at), P))
            at += spacing
        }
        dots(points, size = max(0.6, route.radius * scaleOf(P) * 0.42), tone = Tone.Hi)
    }

    private fun IsoScope.coffee(view: View) {
        val rim = circleOf(GROUP, CUP_RIM, inside(CUP_RIM), P, 64)
        val mouth = rim.toComposePath()
        draw.drawPath(mouth, if (palette.isDark) palette.page else palette.shades[1])
        // The cup's floor, seen through the mouth when it's near enough.
        val floor = circleOf(GROUP, CUP_FLOOR, inside(CUP_FLOOR), P, 48).toComposePath()
        draw.drawPath(Path().apply { op(floor, mouth, PathOperation.Intersect) }, palette.shades[0])
        if (view.grams > 0.05) {
            val z = levelOf(view.grams)
            val surface = circleOf(GROUP, z, inside(z), P, 64).toComposePath()
            val seen = Path().apply { op(surface, mouth, PathOperation.Intersect) }
            draw.drawPath(seen, palette.lo)
        }
        line(rim, tone = Tone.Lo)
        if (view.pour > 0.02) {
            val outside = Path().apply { op(SCREEN, CUP_FILL, PathOperation.Difference) }
            val visible = Path().apply { op(outside, mouth, PathOperation.Union) }
            draw.clipPath(visible) { pour(view) }
        }
    }

    /** The cup's silhouette: the falling coffee is hidden behind its near wall. */
    private val CUP_FILL: Path by lazy {
        val knots =
            (0..23).map { Knot(CUP_FOOT + 2.0 * it, cupRadius(CUP_FOOT + 2.0 * it), it in 1..22) }
        solidOf(knots, GROUP, P, smooth = Smooth(slope = ::cupSlope, radius = ::cupRadius))
            .fill
            .toComposePath()
    }

    private val SCREEN: Path by lazy {
        Path().apply { addRect(Rect(-2000f, -2000f, 4000f, 4000f)) }
    }

    /** Two streams from the spouts down to the coffee, as thick as the flow. */
    private fun IsoScope.pour(view: View) {
        val r = 0.5 + 0.55 * sqrt(view.pour)
        val bottom = max(levelOf(view.grams), CUP_FLOOR)
        for (y in listOf(-6.0, 6.0)) {
            val stream = listOf(Point3(GROUP_X, y, SPOUT_Z), Point3(GROUP_X, y, bottom))
            for (piece in tubePieces(stream, r, P, maxLength = 200.0, stripes = false)) {
                draw.drawPath(piece.body.toComposePath(), palette.lo)
                line(piece.edges, tone = Tone.Lo)
            }
        }
    }
}
