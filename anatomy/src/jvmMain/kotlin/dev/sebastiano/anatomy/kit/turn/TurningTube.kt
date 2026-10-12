// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.RouteOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.tube as tubeShape
import dev.sebastiano.anatomy.kit.cameraOf
import dev.sebastiano.anatomy.kit.cut
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * `T.tube`: the kit's `tubePieces` semantics on a turning group, chunked so each chunk is a convex
 * part. A route runs from mount surface to mount surface; the port, gland or clamp at each end is
 * its own part with `owner` set to the tube's name, and its span along the route goes in [gaps].
 */

/** One chunk of a route by arc length, and how each end meets its neighbour or a gap. */
private class TubeSpanOf(
    val from: Double,
    val to: Double,
    val open0: Boolean,
    val open1: Boolean,
    val gap0: Boolean,
    val gap1: Boolean,
)

/** The routes's chunks, at most [maxLength] long, ending at [breaks] and at [gaps]. */
private fun spansOf(
    total: Double,
    gaps: List<Pair<Double, Double>>,
    breaks: List<Double>,
    maxLength: Double,
): List<TubeSpanOf> {
    val cuts =
        gaps
            .map { (a, b) -> max(0.0, min(a, b)) to min(total, max(a, b)) }
            .filter { (a, b) -> b - a > 1e-6 }
            .sortedWith { x, y ->
                (x.first - y.first).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
            }
    class RunOf(val from: Double, val to: Double, val gapStart: Boolean, val gapEnd: Boolean)
    val runs = ArrayList<RunOf>()
    var at = 0.0
    for ((a, b) in cuts) {
        if (a > at + 1e-6) runs.add(RunOf(at, a, at > 0, true))
        at = max(at, b)
    }
    if (total > at + 1e-6)
        runs.add(RunOf(at, total, at > 0, cuts.isNotEmpty() && cuts.last().second >= total - 1e-6))
    val pieces = ArrayList<TubeSpanOf>()
    for (run in runs) {
        val stops =
            listOf(run.from) +
                breaks.filter { it > run.from + 1e-6 && it < run.to - 1e-6 }.sorted() +
                run.to
        for (index in 0 until stops.size - 1) {
            val a = stops[index]
            val b = stops[index + 1]
            val count = max(1, ceil((b - a) / maxLength - 1e-9).toInt())
            for (c in 0 until count) {
                val first = index == 0 && c == 0
                val last = index == stops.size - 2 && c == count - 1
                pieces.add(
                    TubeSpanOf(
                        a + ((b - a) * c) / count,
                        a + ((b - a) * (c + 1)) / count,
                        !first,
                        !last,
                        first && run.gapStart,
                        last && run.gapEnd,
                    )
                )
            }
        }
    }
    return pieces
}

/** What a tube route is, besides its points and radius. */
data class TubeOptions(
    val chunk: Double = 6.0,
    val turn: Double = 20.0,
    val gaps: List<Pair<Double, Double>> = emptyList(),
    val breaks: List<Double> = emptyList(),
    val caps: Pair<Boolean, Boolean> = true to true,
    val rings: Rings? = null,
    /** `Red`, `Green` or `Blue`: a coloured sheath. */
    val hue: Tone? = null,
    val spacing: Double = 0.8,
    val touch: List<String> = emptyList(),
    val bundle: String? = null,
    val rims: List<Double>? = null,
    val free: Pair<Boolean, Boolean> = false to false,
    val limp: Boolean = true,
)

/** The chunks of a live tube. */
class TurnTube
internal constructor(
    val name: String,
    val chunks: List<TurnPart>,
    val total: Double,
    val dense: List<Point3>,
)

/**
 * A live pipe or cable along [route] (world points at rest) of radius [r], chunked so each chunk is
 * a convex part. Records `R.route` for the kit audit.
 */
fun Turning.tube(
    name: String,
    group: TurnGroup?,
    layer: LiveLayer,
    route: List<Point3>,
    r: Double,
    options: TubeOptions = TubeOptions(),
    style: PartStyle = PartStyle(),
): TurnTube {
    val o = options
    val dense = resample(route, o.spacing)
    val total = pathLength(dense)
    val turnBreaks = ArrayList<Double>()
    var walked = 0.0
    var bent = 0.0
    for (i in 1 until dense.size - 1) {
        walked += len3(sub3(dense[i], dense[i - 1]))
        val a = unit3(sub3(dense[i], dense[i - 1]))
        val b = unit3(sub3(dense[i + 1], dense[i]))
        bent += (jsAcos(max(-1.0, min(1.0, dot3(a, b)))) * 180) / Math.PI
        if (bent > o.turn) {
            turnBreaks.add(walked)
            bent = 0.0
        }
    }
    val allBreaks = (o.breaks + turnBreaks).sorted()
    val pieces = spansOf(total, o.gaps, allBreaks, o.chunk)
    val wide = r * cameraOf(projection).k > 1.3
    val routeIndex = routes.size
    routes.add(
        TurnRoute(
            name,
            group,
            dense.map { local(group, it) },
            r,
            o.spacing,
            wide,
            o.hue,
            o.rings,
            route,
            o.gaps,
            allBreaks,
            o.chunk,
            o.caps,
        )
    )
    val owner = style.owner ?: name
    recorder?.route(
        name,
        route,
        r,
        RouteOptions(
            owner = owner,
            touch = o.touch,
            bundle = o.bundle,
            limp = o.limp,
            gaps = o.gaps,
            rims = o.rims,
            free = o.free,
        ),
    )
    val chunks = pieces.mapIndexed { index, piece ->
        val part = common("$name:$index", PartKind.Tube, group, layer, style, Details())
        part.route = name
        part.chunk = index
        part.hue = o.hue
        part.spec =
            TubeSpec(
                routeIndex,
                piece.from,
                piece.to,
                piece.open0,
                piece.open1,
                piece.gap0,
                piece.gap1,
                o.caps,
                wide,
                o.rings?.tone,
            )
        val core = cut(dense, piece.from, piece.to)
        part.shapes.add(tubeShape(core, r, ShapeMeta(name = part.name, owner = owner)))
        for (i in core.indices) {
            val t = unit3(sub3(core[min(core.size - 1, i + 1)], core[max(0, i - 1)]))
            val f = frameAlong(core[i], t)
            for (j in 0 until 10) part.samples.add(
                local(group, pointOf(f, 0.0, r, (j.toDouble() / 10) * TAU))
            )
        }
        part.hints.add(unit3(sub3(core[1], core[0])))
        part.hints.add(unit3(sub3(core[core.size - 1], core[core.size - 2])))
        parts.add(part)
        part
    }
    return TurnTube(name, chunks, total, dense)
}
