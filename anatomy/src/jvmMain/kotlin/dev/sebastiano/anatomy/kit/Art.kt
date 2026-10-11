// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.ui.graphics.Path

/*
 * Retained art: what the original builds as an SVG string with `solidSvg`, `lineSvg`, `dotsSvg`,
 * `wireSvg`, `faceTextSvg` and `groupSvg`. A figure builds its static art once, in painter's order,
 * and `drawArt` paints it every frame. Groups are named, so live code moves them by a screen
 * offset, the way the original sets `transform` on a `<g data-part>`. Tones resolve at draw time,
 * so the same art paints in both themes.
 */

/** Line and fill tones. Solids use the first four; lines may also be `Lit` or a colour channel. */
enum class Tone {
    Hi,
    Mid,
    Lo,
    Faint,
    Lit,
    Red,
    Green,
    Blue,
}

/** How a line is stroked. `Flow` dashes march along the line, for things moving along a path. */
enum class Stroke {
    Solid,
    Dotted,
    Dashed,
    Flow,
}

/** Which ends of a line may stop on a bare face: rule lines for printed text, a needle's tail. */
enum class Free {
    None,
    Start,
    End,
    Both,
}

/** Which ends of a wire get a terminal dot. */
enum class Ends {
    Both,
    From,
    To,
    None,
}

/** Where face text is anchored on its baseline. */
enum class Anchor {
    Start,
    Middle,
    End,
}

/** One drawable in a piece of art. */
sealed class Mark

/** A geometry path, converted to a Compose path once. */
class Shape(val iso: IsoPath) {
    val path: Path by lazy { iso.toComposePath() }
}

class SolidMark(
    val paths: SolidPaths,
    val tone: Tone,
    val crease: Tone?,
    val lit: Boolean,
    val flat: Boolean,
    val inner: Art?,
) : Mark() {
    val fill = Shape(paths.fill)
    val shades = paths.shades.map { Shape(it) }
    val top = Shape(paths.top)
    val creaseLine = Shape(paths.crease)
    val bevel = paths.bevel?.let { Shape(it) }
    val outline = Shape(paths.outline)
}

class LineMark(
    val shape: Shape,
    val tone: Tone,
    val stroke: Stroke,
    val free: Free,
    val width: Double?,
    val alpha: Double,
) : Mark()

class DotsMark(
    val points: List<Flat>,
    val alphas: List<Double>?,
    val size: Double,
    val tone: Tone,
) : Mark()

class WireMark(val line: LineMark, val ends: List<Flat>, val size: Double, val tone: Tone) : Mark()

class TextMark(
    val matrix: FaceMatrix,
    val text: String,
    val size: Double,
    val tone: Tone,
    val anchor: Anchor,
) : Mark()

/** A soft shadow: the path filled with the halo colour and blurred by [blur] screen px. */
class HaloMark(val shape: Shape, val blur: Double) : Mark()

/** A line that fades out over its first [fadeStart] and last [fadeEnd] screen units. */
class FadeLineMark(
    val points: List<Flat>,
    val fadeStart: Double,
    val fadeEnd: Double,
    val tone: Tone,
) : Mark() {
    val shape = Shape(pathOf(points))
}

/** A named group that live code moves by a screen offset. */
class GroupMark(val part: String, val art: Art) : Mark()

/** Static art, in painter's order. */
class Art(val marks: List<Mark>) {
    operator fun plus(other: Art): Art = Art(marks + other.marks)

    companion object {
        val Empty: Art = Art(emptyList())
    }
}

/** Builds [Art] in painter's order: the first mark is painted first. */
fun art(build: ArtBuilder.() -> Unit): Art = ArtBuilder().apply(build).build()

class ArtBuilder {
    private val marks = ArrayList<Mark>()

    fun build(): Art = Art(marks.toList())

    /** Adds art built elsewhere. */
    fun add(art: Art) {
        marks.addAll(art.marks)
    }

    /**
     * A solid: fill, shades, top, crease, bevel, [inner] lines, outline. [crease] null draws no
     * crease or bevel. [lit] makes it the one accent: a lit edge and lighter top. [flat] skips the
     * shades and top, for floors and inlays thinner than 1 unit.
     */
    fun solid(
        paths: SolidPaths,
        tone: Tone = Tone.Hi,
        crease: Tone? = Tone.Faint,
        halo: Boolean = false,
        lit: Boolean = false,
        flat: Boolean = false,
        inner: Art? = null,
    ) {
        if (halo) halo(paths.fill)
        marks.add(SolidMark(paths, tone, crease, lit, flat, inner))
    }

    /** A line. [width] overrides the hairline weight, in screen px. */
    fun line(
        path: IsoPath,
        tone: Tone = Tone.Lo,
        stroke: Stroke = Stroke.Solid,
        free: Free = Free.None,
        width: Double? = null,
    ) {
        if (path.isEmpty) return
        marks.add(LineMark(Shape(path), tone, stroke, free, width, 1.0))
    }

    /**
     * Lines that each carry an opacity: ribs, fins, knurls fading toward a limb. Lines under 0.02
     * are dropped and the rest grouped in steps of 0.05, as the original's `fadedSvg` does.
     */
    fun faded(lines: List<Pair<IsoPath, Double>>, tone: Tone = Tone.Lo) {
        val levels = LinkedHashMap<Double, IsoPath>()
        for ((path, alpha) in lines) {
            if (path.isEmpty || alpha <= 0.02) continue
            val level = minOf(1.0, jsRound(alpha * 20) / 20)
            levels[level] = (levels[level] ?: IsoPath.Empty) + path
        }
        for ((level, path) in levels) {
            marks.add(LineMark(Shape(path), tone, Stroke.Solid, Free.None, null, level))
        }
    }

    /**
     * One line that fades out over its first [start] and last [end] screen units instead of
     * stopping: a groove that runs out, a weld line short of a corner.
     */
    fun fadeLine(points: List<Flat>, start: Double, end: Double, tone: Tone = Tone.Lo) {
        if (points.size < 2) return
        marks.add(FadeLineMark(points, start, end, tone))
    }

    /** Dots of radius [size]: screws, rivets, perforations. [alphas] fades them one by one. */
    fun dots(
        points: List<Flat>,
        size: Double = 0.5,
        tone: Tone = Tone.Mid,
        alphas: List<Double>? = null,
    ) {
        if (points.isEmpty()) return
        marks.add(DotsMark(points, alphas, size, tone))
    }

    /** A connector from [from] through [via] to [to], with terminal dots that land on objects. */
    fun wire(
        from: Flat,
        to: Flat,
        via: List<Flat> = emptyList(),
        tone: Tone = Tone.Lo,
        stroke: Stroke = Stroke.Solid,
        ends: Ends = Ends.Both,
        size: Double = 1.2,
    ) {
        val line =
            LineMark(Shape(pathOf(listOf(from) + via + to)), tone, stroke, Free.None, null, 1.0)
        val dots =
            when (ends) {
                Ends.Both -> listOf(from, to)
                Ends.From -> listOf(from)
                Ends.To -> listOf(to)
                Ends.None -> emptyList()
            }
        marks.add(WireMark(line, dots, size, tone))
    }

    /** Text on a face: at most one tiny engraved label per figure. */
    fun faceText(
        matrix: FaceMatrix,
        text: String,
        size: Double = 3.0,
        tone: Tone = Tone.Mid,
        anchor: Anchor = Anchor.Start,
    ) {
        marks.add(TextMark(matrix, text, size, tone, anchor))
    }

    /** A soft shadow under a base or a lifted part. [blur] is in screen px. */
    fun halo(path: IsoPath, blur: Double = 9.0) {
        marks.add(HaloMark(Shape(path), blur))
    }

    /** A group that live code moves as one: a carriage, a plunger, a drawer. */
    fun group(part: String, build: ArtBuilder.() -> Unit) {
        marks.add(GroupMark(part, art(build)))
    }
}

/** The Compose path for a kit path. */
fun IsoPath.toComposePath(): Path {
    val path = Path()
    for (run in runs) {
        run.points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
            else path.lineTo(point.x.toFloat(), point.y.toFloat())
        }
        if (run.closed) path.close()
    }
    return path
}
