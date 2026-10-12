package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Drawn
import dev.sebastiano.anatomy.kit.End
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.GroupMark
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.LineEndReport
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.TubePiece
import dev.sebastiano.anatomy.kit.WireMark
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.lineEnds
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.tubePieces
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rules of the original's `scripts/line-ends.mjs`, on small hand-made art. */
class LineEndsTest {
    @Test
    fun `a line from edge to edge of a face lands on its outline`() {
        val report = check {
            face()
            line(segment(0.0, 20.0, 40.0, 20.0))
        }
        assertEquals(0, report.loose.size)
        assertEquals(2, report.ends)
        assertEquals(1, report.lines)
    }

    @Test
    fun `a line that stops in the middle of a face floats`() {
        val report = check {
            face()
            line(segment(0.0, 20.0, 20.0, 20.0))
        }
        assertEquals(1, report.loose.size)
        val loose = report.loose.single()
        assertEquals(End.End, loose.end)
        assertEquals(Flat(20.0, 20.0), loose.at)
        assertEquals(20.0, loose.length, 1e-9)
    }

    @Test
    fun `a free end may stop on a bare face`() {
        val report = check {
            face()
            line(segment(0.0, 20.0, 20.0, 20.0), free = Free.End)
        }
        assertEquals(0, report.loose.size)
        assertEquals(1, report.ends)
    }

    @Test
    fun `a free start does not excuse the end`() {
        val report = check {
            face()
            line(segment(0.0, 20.0, 20.0, 20.0), free = Free.Start)
        }
        assertEquals(End.End, report.loose.single().end)
    }

    @Test
    fun `a line may end on a dot`() {
        val report = check {
            face()
            line(segment(0.0, 20.0, 20.0, 20.0))
            dots(listOf(Flat(20.4, 20.0)), size = 0.5)
        }
        assertEquals(0, report.loose.size)
    }

    @Test
    fun `a line may end on another stroke within a unit`() {
        val near = check {
            face()
            line(segment(20.9, 0.0, 20.9, 40.0))
            line(segment(0.0, 20.0, 20.0, 20.0))
        }
        assertEquals(0, near.loose.size)
        val far = check {
            face()
            line(segment(21.5, 0.0, 21.5, 40.0))
            line(segment(0.0, 20.0, 20.0, 20.0))
        }
        assertEquals(1, far.loose.size)
        assertEquals(1.5, far.loose.single().gap, 1e-9)
    }

    @Test
    fun `a line hidden under a fill painted after it passes`() {
        val report = check {
            line(segment(-10.0, 20.0, 20.0, 20.0))
            face()
        }
        // The start floats in the air; the end is under the face.
        assertEquals(End.Start, report.loose.single().end)
    }

    @Test
    fun `a line on a fill painted before it is not hidden by it`() {
        val report = check {
            face()
            line(segment(10.0, 20.0, 30.0, 20.0))
        }
        assertEquals(2, report.loose.size)
    }

    @Test
    fun `a tick needs one rooted end and may stop on the face with the other`() {
        val rooted = check {
            face()
            line(segment(0.0, 20.0, 5.0, 20.0))
        }
        assertEquals(0, rooted.loose.size)
        val floating = check {
            face()
            line(segment(10.0, 20.0, 15.0, 20.0))
        }
        assertEquals(2, floating.loose.size)
        assertEquals(true, floating.loose.first().mark)
    }

    @Test
    fun `a line may end on its own earlier stretch, but not on the stretch it ends with`() {
        // A loop that comes back to end half a unit from where it started.
        val loop = check {
            face()
            line(
                pathOf(
                    listOf(
                        Flat(0.0, 20.0),
                        Flat(30.0, 20.0),
                        Flat(30.0, 25.0),
                        Flat(10.0, 25.0),
                        Flat(10.0, 20.5),
                    )
                )
            )
        }
        assertEquals(0, loop.loose.size)
    }

    @Test
    fun `faint faded lines are not checked`() {
        val report = check {
            face()
            faded(listOf(segment(10.0, 20.0, 30.0, 20.0) to 0.25))
        }
        assertEquals(0, report.lines)
        assertEquals(0, report.loose.size)
    }

    @Test
    fun `a fade line ramps out and passes`() {
        val report = check {
            face()
            fadeLine(listOf(Flat(0.0, 20.0), Flat(20.0, 20.0)), start = 0.0, end = 4.0)
        }
        assertEquals(0, report.loose.size)
    }

    @Test
    fun `a wire ends in its terminal dots`() {
        val report = check { wire(Flat(-30.0, 0.0), Flat(-10.0, 30.0)) }
        assertEquals(0, report.loose.size)
    }

    @Test
    fun `closed lines have no ends`() {
        val report = check {
            face()
            line(pathOf(listOf(Flat(10.0, 10.0), Flat(20.0, 10.0), Flat(15.0, 20.0)), true))
        }
        assertEquals(0, report.ends)
    }

    @Test
    fun `a group's offset moves its lines before they are checked`() {
        val moved = art { line(segment(-20.0, 20.0, 20.0, 20.0)) }
        val face = art { face() }
        // Moved 20 right, the line runs edge to edge on the face.
        val report = lineEnds(face.drawn(Flat(0.0, 0.0)) + moved.drawn(Flat(20.0, 0.0)))
        assertEquals(0, report.loose.size)
    }

    @Test
    fun `a tube's body hides the ends painted before it, and its edges are not checked`() {
        val report = check {
            face()
            line(segment(0.0, 20.0, 20.0, 20.0))
            tube(pipe())
        }
        assertEquals(0, report.loose.size)
        assertEquals(1, report.lines)
    }

    @Test
    fun `a line may end on a tube's edge but not under a tube painted before it`() {
        val piece = pipe()
        // The pipe runs along screen x at y 20, its top edge 2 units above.
        val edge = check {
            face()
            tube(piece)
            line(segment(20.0, 0.0, 20.0, 17.6))
        }
        assertEquals(0, edge.loose.size)
        val under = check {
            face()
            tube(piece)
            line(segment(20.0, 0.0, 20.0, 20.0))
        }
        assertEquals(End.End, under.loose.single().end)
    }

    private fun check(build: ArtBuilder.() -> Unit): LineEndReport =
        lineEnds(art(build).drawn(Flat(0.0, 0.0)))

    /** A 40 by 40 face at the origin, filled and outlined. */
    private fun ArtBuilder.face() {
        val square =
            pathOf(listOf(Flat(0.0, 0.0), Flat(40.0, 0.0), Flat(40.0, 40.0), Flat(0.0, 40.0)), true)
        solid(SolidPaths(square, square, IsoPath.Empty, square, emptyList()), flat = true)
    }

    /** A pipe of radius 2 along screen x from 5 to 35 at y 20: the floor's y axis, seen head-on. */
    private fun pipe(): TubePiece {
        val front = Projection(Flat(20.0, 20.0), 1.0 / sqrt(1.6), azimuth = 90.0, elevation = 0.0)
        val route = listOf(Point3(-15.0, 0.0, 0.0), Point3(15.0, 0.0, 0.0))
        return tubePieces(route, 2.0, front).single()
    }

    private fun segment(x0: Double, y0: Double, x1: Double, y1: Double): IsoPath =
        pathOf(listOf(Flat(x0, y0), Flat(x1, y1)))

    /** What the renderer would record painting this art at [offset]. */
    private fun Art.drawn(offset: Flat): List<Drawn> = marks.flatMap { mark ->
        when (mark) {
            is GroupMark -> mark.art.drawn(offset)
            is SolidMark -> listOf(Drawn(mark, offset)) + (mark.inner?.drawn(offset).orEmpty())
            is WireMark -> listOf(Drawn(mark.line, offset), Drawn(mark, offset))
            else -> listOf(Drawn(mark, offset))
        }
    }
}
