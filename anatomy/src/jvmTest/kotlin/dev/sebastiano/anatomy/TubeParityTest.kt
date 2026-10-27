package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.LatheParityTest.Companion.CAMERAS
import dev.sebastiano.anatomy.ParityLines.Companion.fixed
import dev.sebastiano.anatomy.ParityLines.Companion.num
import dev.sebastiano.anatomy.ParityLines.Companion.p3
import dev.sebastiano.anatomy.ParityLines.Companion.points3
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.TubePiece
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.arcPoints
import dev.sebastiano.anatomy.kit.crossingsOf
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.ringLevels
import dev.sebastiano.anatomy.kit.runsOf
import dev.sebastiano.anatomy.kit.spiral
import dev.sebastiano.anatomy.kit.tubePieces
import dev.sebastiano.anatomy.kit.viewOf
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Holds the Kotlin pipes to the original's `kit/tube.mjs`. The golden
 * `resources/goldens/tube.txt.gz` is captured by `anatomy/parity/tube.mjs` (see the note at its
 * top), which calls the original with the same cases in the same order. Every path must match to
 * the 0.1 the original writes, every number to the digits it prints.
 */
class TubeParityTest {
    @Test
    fun tubesMatchTheOriginal() {
        val out = ParityLines()
        out.runs()
        for ((name, projection) in CAMERAS) {
            out.routes(projection, name)
            out.gapsAndCaps(projection, name)
            out.closedRoutes(projection, name)
            out.ringsOf(projection, name)
        }
        val expected =
            GZIPInputStream(javaClass.getResourceAsStream("/goldens/tube.txt.gz")!!)
                .bufferedReader()
                .readText()
                .trimEnd('\n')
                .split('\n')
        expected.zip(out.lines).forEachIndexed { index, (want, got) ->
            assertEquals(want, got, "line ${index + 1} differs")
        }
        assertEquals(expected.size, out.lines.size, "line count")
    }

    private fun ParityLines.pieces(name: String, list: List<TubePiece>) {
        put("$name.count", list.size)
        list.forEachIndexed { index, piece ->
            val at = "$name.$index"
            val tone = piece.ringTone.name.lowercase()
            put(
                "$at.span",
                "${num(piece.from)} ${num(piece.to)} ${piece.ends.first} ${piece.ends.second} " +
                    "${piece.wide} $tone",
            )
            put("$at.mid", p3(piece.mid))
            put("$at.points", points3(piece.points))
            put("$at.radii", piece.radii.joinToString(" ") { num(it) })
            put("$at.body", piece.body)
            put("$at.shine", piece.shine)
            put("$at.shade", piece.shade)
            put("$at.edges", piece.edges)
            piece.rings.forEachIndexed { ring, (d, alpha) ->
                put("$at.ring$ring", "${fixed(alpha, 6)}\t${d.svg()}")
            }
            put("$at.ringsSvg", ringsSvg(piece, tone))
        }
    }

    /** The original's `ringsSvg`, written from [ringLevels], which is what the renderer strokes. */
    private fun ringsSvg(piece: TubePiece, tone: String): String =
        ringLevels(piece.rings).joinToString("") { (alpha, d) ->
            val opacity =
                if (alpha < 1) " opacity=\"${String.format(Locale.ROOT, "%.2f", alpha)}\"" else ""
            "<path class=\"tb-ring\" data-tone=\"$tone\" d=\"${d.svg()}\"$opacity/>"
        }

    private fun ParityLines.routes(p: Projection, name: String) {
        val straight = listOf(Point3(0.0, 0.0, 0.0), Point3(40.0, 0.0, 0.0))
        pieces("$name.straight", tubePieces(straight, 2.0, p))
        val y = listOf(Point3(0.0, -10.0, 3.0), Point3(0.0, 30.0, 3.0))
        pieces("$name.straight.y", tubePieces(y, 1.2, p, maxLength = 12.0))
        val up = listOf(Point3(4.0, 4.0, 0.0), Point3(4.0, 4.0, 36.0))
        pieces("$name.straight.up", tubePieces(up, 2.5, p, maxLength = 10.0))
        pieces("$name.legs", tubePieces(fillet(LEGS, 4.0), 1.5, p, maxLength = 12.0))
        val upright = fillet(UPRIGHT, 5.0)
        pieces("$name.upright", tubePieces(upright, 3.0, p, breaks = listOf(10.0, 25.0)))
        pieces("$name.duct", tubePieces(DUCT, 3.4, p, maxLength = 18.0, spacing = 1.6))
        pieces("$name.taper", tubePieces(fillet(LEGS, 6.0), ::taper, p, maxLength = 15.0))
        pieces("$name.wobble", tubePieces(DUCT, ::wobble, p, maxLength = 9.0, stripes = false))
        pieces("$name.thin", tubePieces(fillet(LEGS, 3.0), 0.3, p))
        pieces(
            "$name.thin.striped",
            tubePieces(fillet(LEGS, 3.0), 0.3, p, stripes = true, maxLength = 20.0),
        )
    }

    private fun ParityLines.gapsAndCaps(p: Projection, name: String) {
        val route = fillet(LEGS, 4.0)
        val total = pathLength(resample(route, 2.2))
        val gaps = listOf(10.0 to 12.5, 30.0 to 31.0)
        pieces("$name.gaps", tubePieces(route, 1.5, p, gaps = gaps, maxLength = 14.0))
        val ends = listOf(-3.0 to 2.0, total - 4 to total + 5)
        pieces("$name.gaps.ends", tubePieces(route, 1.5, p, gaps = ends, breaks = listOf(20.0)))
        val overlap = listOf(18.0 to 14.0, 15.0 to 22.0, 40.0 to 40.0, 50.0 to 52.0)
        pieces("$name.gaps.overlap", tubePieces(route, 1.5, p, gaps = overlap))
        pieces("$name.caps.none", tubePieces(route, 1.5, p, caps = false to false))
        val branch = tubePieces(route, 1.5, p, caps = false to true, gaps = listOf(25.0 to 27.0))
        pieces("$name.caps.end", branch)
        pieces("$name.caps.start", tubePieces(UPRIGHT, 2.0, p, caps = true to false))
        faces(p, name)
        val crossings = CLAMPS.flatMap { (centre, axis, r) -> crossingsOf(route, centre, axis, r) }
        put("$name.crossingsOf", crossings.joinToString(" ") { num(it) })
        val clamp = Point3(8.0, 0.0, 0.0)
        val axis = Point3(1.0, 0.0, 0.0)
        val miss = crossingsOf(route, clamp, axis, 0.5).joinToString(" ") { num(it) }
        val far = crossingsOf(route, Point3(500.0, 0.0, 0.0), axis, 3.0).size
        put("$name.crossingsOf.miss", "$miss|$far")
        val clamped = crossings.map { it - 0.9 to it + 0.9 }
        pieces("$name.clamped", tubePieces(route, 0.62, p, gaps = clamped, maxLength = 10.0))
    }

    /** Ends whose faces turn toward the viewer, at the end, the start, and a route head-on. */
    private fun ParityLines.faces(p: Projection, name: String) {
        val view = viewOf(p)
        val origin = Point3(0.0, 0.0, 0.0)
        val bend = Point3(0.0, -20.0, 0.0)
        val toward = listOf(origin, bend, add3(bend, mul3(view, 24.0)))
        pieces("$name.face.end", tubePieces(fillet(toward, 6.0), 2.4, p, maxLength = 12.0))
        val away = listOf(add3(origin, mul3(view, 20.0)), origin, Point3(24.0, 0.0, 0.0))
        pieces(
            "$name.face.start",
            tubePieces(fillet(away, 6.0), 2.4, p, maxLength = 12.0, gaps = listOf(10.0 to 13.0)),
        )
        pieces("$name.face.head", tubePieces(listOf(origin, mul3(view, 30.0)), 2.0, p))
    }

    private fun ParityLines.closedRoutes(p: Projection, name: String) {
        pieces("$name.closed", tubePieces(RING, 1.4, p, closed = true, maxLength = 20.0))
        pieces(
            "$name.closed.gaps",
            tubePieces(RING, 1.4, p, closed = true, maxLength = 20.0, gaps = listOf(30.0 to 33.0)),
        )
        pieces(
            "$name.closed.one",
            tubePieces(RING, 1.4, p, closed = true, maxLength = 400.0, rings = Rings(pitch = 4.0)),
        )
    }

    private fun ParityLines.ringsOf(p: Projection, name: String) {
        val route = fillet(LEGS, 5.0)
        pieces("$name.rings", tubePieces(route, 1.8, p, maxLength = 12.0, rings = Rings()))
        val braid = Rings(pitch = 1.6, twist = 1.2, cross = true, tone = Tone.Mid)
        pieces(
            "$name.rings.braid",
            tubePieces(route, 1.8, p, maxLength = 12.0, breaks = listOf(33.0), rings = braid),
        )
        val helix = Rings(pitch = 2.2, twist = -0.8, phase = 0.7, steps = 10)
        pieces(
            "$name.rings.helix",
            tubePieces(route, 1.8, p, maxLength = 16.0, gaps = listOf(20.0 to 23.0), rings = helix),
        )
        val hard = Rings(pitch = 2.5, fade = null, tone = Tone.Faint)
        pieces("$name.rings.hard", tubePieces(route, 1.8, p, maxLength = 16.0, rings = hard))
        val corrugated =
            Rings(pitch = 1.2, fade = 0.05 to 0.4, phase = 0.3, steps = 12, tone = Tone.Hi)
        pieces(
            "$name.rings.corrugated",
            tubePieces(fillet(UPRIGHT, 6.0), 3.2, p, maxLength = 10.0, rings = corrugated),
        )
        val twisted = Rings(pitch = 3.0, twist = 0.6)
        pieces(
            "$name.rings.taper",
            tubePieces(DUCT, ::taper, p, maxLength = 14.0, rings = twisted),
        )
        pieces("$name.volute", tubePieces(VOLUTE, ::volute, p, maxLength = 10.0))
        val wrap = Rings(pitch = 2.5, phase = 1.0)
        pieces(
            "$name.volute.rings",
            tubePieces(VOLUTE, ::volute, p, maxLength = 14.0, rings = wrap),
        )
    }

    private fun ParityLines.runs() {
        fun spans(list: List<Pair<Double, Double>>) =
            list.joinToString(" ") { (a, b) -> "${num(a)},${num(b)}" }
        put("runsOf.none", spans(runsOf(50.0)))
        put("runsOf.gaps", spans(runsOf(50.0, listOf(10.0 to 12.0, 30.0 to 34.5))))
        put("runsOf.ends", spans(runsOf(50.0, listOf(-2.0 to 3.0, 48.0 to 60.0))))
        val overlap =
            listOf(20.0 to 14.0, 15.0 to 25.0, 24.0 to 26.0, 40.0 to 40.0, 44.0 to 44.0000001)
        put("runsOf.overlap", spans(runsOf(50.0, overlap)))
        put("runsOf.all", spans(runsOf(50.0, listOf(0.0 to 50.0))))
    }

    private companion object {
        fun pt(x: Int, y: Int, z: Int) = Point3(x.toDouble(), y.toDouble(), z.toDouble())

        val LEGS = listOf(pt(0, 0, 0), pt(20, 0, 0), pt(20, 15, 5), pt(5, 25, 5), pt(5, 25, 30))
        val UPRIGHT = listOf(pt(-10, 0, 0), pt(-10, 0, 18), pt(6, 0, 18), pt(6, 0, 4))
        val DUCT =
            fillet(
                listOf(
                    pt(-30, -8, 4),
                    pt(-4, -8, 4),
                    pt(-4, 14, 4),
                    pt(-4, 14, 26),
                    pt(18, 14, 26),
                ),
                7.0,
            )
        val VOLUTE =
            spiral(
                frameOf(pt(0, 0, 0), pt(0, 0, 1), pt(1, 0, 0), pt(0, 1, 0)),
                6.0,
                14.0,
                22.0,
                0.3,
                5.5,
                40,
            )
        val RING =
            arcPoints(frameAlong(pt(2, 3, 1), Point3(0.2, 0.1, 1.0)), 0.0, 16.0, 0.0, PI * 2, 36)
        val CLAMPS =
            listOf(
                Triple(pt(8, 0, 0), pt(1, 0, 0), 3.0),
                Triple(pt(20, 8, 2), Point3(0.0, 1.0, 0.3), 4.0),
                Triple(pt(12, 20, 5), Point3(-1.0, 0.6, 0.0), 3.0),
            )

        fun taper(t: Double) = 1.5 + 2 * t

        fun volute(t: Double) = 2 + 3 * t

        fun wobble(t: Double) = 2 + 0.6 * sin(t * 9)
    }
}
