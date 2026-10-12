package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.BallFlat
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.RouteOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.ball
import dev.sebastiano.anatomy.kit.audit.body
import dev.sebastiano.anatomy.kit.audit.box
import dev.sebastiano.anatomy.kit.audit.cylinder
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.audit.extruded
import dev.sebastiano.anatomy.kit.audit.lathe
import dev.sebastiano.anatomy.kit.audit.orientedBox
import dev.sebastiano.anatomy.kit.audit.prism
import dev.sebastiano.anatomy.kit.audit.ring
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.audit.slide
import dev.sebastiano.anatomy.kit.audit.solid
import dev.sebastiano.anatomy.kit.audit.tube
import dev.sebastiano.anatomy.kit.cut
import dev.sebastiano.anatomy.kit.cylinder as cylinderPaths
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.slabOf
import kotlin.math.sqrt

/**
 * The scenes `anatomy/parity/audit.mjs` builds, built the same way with the Kotlin kit, in the same
 * order. Where the original records an item's SVG markup, these record the equivalent [Art].
 */
object AuditScenes {
    val P = Projection(Flat(300.0, 220.0), 2.0)
    val P57 = Projection(Flat(310.0, 200.0), 2.4, 57.0, 36.0)
    private val X = p(1, 0, 0)
    private val Y = p(0, 1, 0)
    private val Z = p(0, 0, 1)
    val UP = frameOf(p(0, 0, 0), Z, X, Y)
    val TILT = frameAlong(p(-6, 4, 12), Point3(0.4, -0.7, 0.6))
    val SIDE = frameAlong(p(10, -5, 8), X)

    val BELL =
        listOf(
            Knot(0.0, 12.0),
            Knot(3.0, 11.6, true),
            Knot(8.0, 9.8, true),
            Knot(14.0, 7.2, true),
            Knot(20.0, 5.4, true),
            Knot(26.0, 4.8),
        )
    val WOBBLY =
        listOf(
            Knot(0.0, 10.0),
            Knot(3.0, 9.7, true),
            Knot(6.0, 8.2, true),
            Knot(9.0, 8.0, true),
            Knot(12.0, 6.2, true),
            Knot(15.0, 6.0),
        )

    fun throat(s: Double): Double = sqrt(36 + (0.5 * (s - 10)) * (0.5 * (s - 10)))

    val THROAT = listOf(0, 4, 8, 12, 16, 20).map { Knot(it.toDouble(), throat(it.toDouble())) }
    val LEGS = listOf(p(-8, -4, 2), p(6, -4, 2), p(6, 7, 9), p(-3, 9, 14))

    val PROBES =
        List(40) { i ->
            Point3(
                ((i * 7) % 13 - 6) * 1.7 + 0.3,
                ((i * 5) % 11 - 5) * 1.9 - 0.4,
                ((i * 3) % 9) * 1.6 - 1.1 + (i % 4) * 0.7,
            )
        }

    fun p(x: Number, y: Number, z: Number) = Point3(x.toDouble(), y.toDouble(), z.toDouble())

    private fun named(name: String) = ShapeMeta(name = name)

    fun shapeCases(): List<Pair<String, AuditShape>> {
        val cutter = ball(p(0, 0, 1), 3.5, named("cutter"))
        val plug = cylinder(2.0, 2.0, 1.5, -2.0, 10.0, named("plug"))
        fun knots(vararg values: Pair<Number, Number>) = values.map {
            Knot(it.first.toDouble(), it.second.toDouble())
        }
        val smooth =
            listOf(
                Knot(0.0, 0.0),
                Knot(0.0, 4.0),
                Knot(3.0, 6.0, true),
                Knot(6.0, 7.0, true),
                Knot(9.0, 6.0, true),
                Knot(12.0, 4.0),
                Knot(12.0, 0.0),
            )
        return listOf(
            "body" to body(TILT, knots(0 to 0, 0 to 5, 6 to 7, 10 to 3, 10 to -1), named("body")),
            "lathe.ring" to lathe(SIDE, knots(0 to 6, 0 to 10, 12 to 10, 12 to 6)),
            "lathe.smooth" to lathe(UP, smooth),
            "solid.bell" to solid(TILT, BELL),
            "solid.radius" to solid(SIDE, THROAT, ::throat),
            "prism" to prism(TILT, floors(-5 to -3, 5 to -3, 5 to 3, -5 to 3), 3.0, -2.0),
            "extruded" to
                extruded(floors(0 to 0, 8 to 0, 8 to 3, 3 to 3, 3 to 9, 0 to 9), 2.0, 5.0),
            "slab" to slab(Plan(-10.0, -6.0, 20.0, 12.0, 3.0), 0.0, 4.0),
            "slab.steps" to slab(Plan(-4.0, -3.0, 9.0, 6.0, 2.0), 1.0, 2.0, ShapeMeta(), 3),
            "cylinder" to cylinder(3.0, -2.0, 4.0, 1.0, 8.0),
            "disc" to disc(TILT, -1.0, 1.0, 6.0),
            "ring" to ring(TILT, 0.0, 3.0, 4.0, 7.0),
            "box" to box(p(-3, -2, 0), p(5, 4, 6)),
            "orientedBox" to
                orientedBox(
                    p(1, 2, 3),
                    listOf(p(1, 1, 0), p(-1, 1, 0), p(0, 0, 1)),
                    listOf(6.0, 1.5, 2.0),
                ),
            "ball" to ball(p(2, -1, 5), 4.0),
            "ball.flats" to
                ball(p(2, -1, 5), 4.0, flats = listOf(BallFlat(Z, 2.0), BallFlat(X, 3.0))),
            "tube" to tube(LEGS, 1.2),
            "tube.function" to tube(LEGS, { t: Double -> 1 + t }),
            "tube.list" to tube(LEGS, listOf(0.8, 1.4, 1.4, 0.6)),
            "tube.round" to tube(LEGS, 1.0, round = true to true),
            "slide" to slide(SIDE, 0.0, 8.0, 1.5),
            "cut.one" to disc(UP, 0.0, 3.0, 5.0, ShapeMeta(cut = listOf(cutter))),
            "cut.list" to box(p(-4, -4, 0), p(4, 4, 6), ShapeMeta(cut = listOf(cutter, plug))),
        )
    }

    private fun floors(vararg values: Pair<Number, Number>) = values.map {
        Floor(it.first.toDouble(), it.second.toDouble())
    }

    /** The overlap scene. Keys are put in deliberately wrong. */
    fun overlapScene(): Recorder {
        val r = Recorder(P)
        r.put(
            null,
            listOf(slab(Plan(-14.0, -10.0, 28.0, 20.0, 3.0), 0.0, 3.0)),
            key = 5.0,
            name = "deck",
        )
        r.put(null, listOf(cylinder(0.0, 0.0, 3.0, 3.0, 12.0)), key = -3.0, name = "post")
        val pipe = listOf(p(-12, 6, 8), p(0, 6, 8), p(10, 6, 8))
        r.put(
            null,
            listOf(tube(cut(pipe, 0.0, 12.0), 1.2)),
            key = -4.0,
            name = "pipe:0",
            route = "pipe",
            chunk = 0,
        )
        r.put(
            null,
            listOf(tube(cut(pipe, 12.0, 22.0), 1.2)),
            key = -4.0,
            name = "pipe:1",
            route = "pipe",
            chunk = 1,
        )
        val lift = p(0, 0, 10)
        r.put(
            null,
            listOf(box(p(-4, -4, 15), p(4, 4, 17))),
            key = 1.0,
            name = "lid",
            piece = "lid",
            move = lift,
        )
        val down = frameOf(p(0, 0, 15), p(0, 0, -1), X, p(0, -1, 0))
        r.put(
            null,
            listOf(slide(down, 0.0, 6.0, 1.0)),
            key = 0.5,
            name = "rod",
            piece = "lid",
            move = lift,
        )
        r.put(null, listOf(box(p(4, -12, 4), p(12, -10, 6))), key = 2.0, name = "cross.a")
        r.put(null, listOf(box(p(7, -14, 3), p(9, -8, 7))), key = 2.0, name = "cross.b")
        r.put(null, key = 0.0, name = "bare")
        return r
    }

    val KNOT = fillet(listOf(p(20, -8, 6), p(20, -8, 12), p(21.5, -6.5, 12.6), p(26, -12, 13)), 0.6)

    private class RouteCase(
        val name: String,
        val points: List<Point3>,
        val r: Double,
        val options: RouteOptions,
    )

    private fun routeCases() =
        listOf(
            RouteCase(
                "feed",
                listOf(p(0, 0, 15), p(0, 0, 18), p(24, 0, 18), p(24, 0, 4)),
                1.0,
                RouteOptions(),
            ),
            RouteCase(
                "cable",
                listOf(p(5, -14, 4), p(5, -14, 18), p(5, 14, 18), p(5, 14, 4)),
                0.4,
                RouteOptions(limp = true),
            ),
            RouteCase("sense", listOf(p(6.7, -10, 8), p(6.7, 10, 8)), 0.5, RouteOptions()),
            RouteCase(
                "hose",
                listOf(p(-26, -4, 4), p(-26, -4, 12), p(-20, -4, 16)),
                0.8,
                RouteOptions(free = false to true, piece = "pipes", move = p(0, -6, 0)),
            ),
            RouteCase(
                "drop",
                listOf(p(26, 18, 24), p(16, 8, 24), p(16, 8, 4)),
                0.9,
                RouteOptions(),
            ),
            RouteCase(
                "tap",
                listOf(p(-23, 18, 4), p(-23, 18, 14), p(-23, 12.5, 14)),
                0.6,
                RouteOptions(),
            ),
            RouteCase(
                "gapped",
                listOf(p(-8, 16, 4), p(-8, 16, 12), p(8, 16, 12), p(8, 16, 4)),
                0.7,
                RouteOptions(gaps = listOf(10.0 to 12.0), rims = listOf(1.2, 1.2)),
            ),
            RouteCase("upper", listOf(p(-10, -32, 20), p(10, -32, 20)), 0.8, RouteOptions()),
            RouteCase(
                "lower",
                listOf(p(0, -29, 40), p(0, -29, 24.2)),
                0.6,
                RouteOptions(owner = "low", bundle = "harness"),
            ),
        )

    private fun Recorder.putShape(
        name: String,
        shape: AuditShape,
        piece: String = "",
        move: Point3 = p(0, 0, 0),
    ) {
        put(null, listOf(shape), at = shape.bounds.max, name = name, piece = piece, move = move)
    }

    /** The check scene: every check fires somewhere in it, and stays quiet somewhere else. */
    fun checkScene(arts: Map<String, Art>): Recorder {
        val r = Recorder(P, order = listOf("lid", "pipes"))
        r.putShape(
            "stand.deck",
            r.solid(slab(Plan(-30.0, -20.0, 60.0, 40.0, 4.0), 0.0, 4.0, named("stand.deck"))),
        )
        r.putShape("block", r.solid(box(p(-6, -6, 4), p(6, 6, 14), named("block"))))
        val portFrame = frameOf(p(0, 0, 14), Z, X, Y)
        r.putShape("block.port", r.solid(disc(portFrame, 0.0, 1.0, 2.0, named("block.port"))))
        r.putShape("wall", r.solid(box(p(10, -8, 4), p(12, 8, 20), named("wall"))))
        r.solid(box(p(14, 8, 20), p(18, 12, 24), named("floater")))
        r.solid(box(p(-20, -15, 2), p(-14, -9, 8), named("sunk")))
        r.putShape(
            "lid",
            r.solid(box(p(-5, -5, 22), p(5, 5, 24), named("lid"))),
            "lid",
            p(0, 0, -12),
        )
        r.putShape("rail", r.solid(box(p(-24, 10, 4), p(-22, 12, 22), named("rail"))))
        val host = r.solid(cylinder(-16.0, 0.0, 4.0, 4.0, 10.0, named("drum")))
        r.putShape("drum", host)
        val padFrame = frameOf(p(-11.5, 0, 9), X, Y, Z)
        val pad =
            disc(
                padFrame,
                -1.0,
                0.5,
                1.5,
                ShapeMeta(name = "drum.pad", seated = true, cut = listOf(host)),
            )
        r.putShape("drum.pad", r.solid(pad), "pipes", p(6, 0, 0))
        val capFrame = frameOf(p(-16, 0, 13.5), Z, X, Y)
        val cap =
            disc(
                capFrame,
                -1.0,
                1.0,
                1.2,
                ShapeMeta(name = "drum.cap", seated = true, cut = listOf(host)),
            )
        r.putShape("drum.cap", r.solid(cap))
        profiles(r)
        lines(r, arts)
        return r
    }

    private fun profiles(r: Recorder) {
        r.solid(
            solid(
                frameOf(p(22, -14, 4), Z, X, Y),
                WOBBLY,
                meta = ShapeMeta(name = "vase", curve = WOBBLY),
            )
        )
        val small = BELL.map { Knot(it.s / 3, it.r / 3, it.smooth) }
        r.solid(
            solid(
                frameOf(p(-24, -14, 4), Z, X, Y),
                small,
                meta = ShapeMeta(name = "bell", curve = small),
            )
        )
    }

    private fun lines(r: Recorder, arts: Map<String, Art>) {
        for (case in routeCases()) {
            r.route(case.name, case.points, case.r, case.options)
            r.put(
                null,
                listOf(tube(case.points, case.r)),
                at = case.points[0],
                name = "${case.name}:0",
                route = case.name,
                chunk = 0,
                piece = case.options.piece,
                move = case.options.move,
            )
        }
        r.putShape("post", r.solid(box(p(-1, -40, 0), p(1, -38, 20), named("post"))))
        val clampMeta = ShapeMeta(name = "clamp", owner = "upper", fitting = true, anchor = false)
        r.putShape("clamp", r.solid(disc(frameAlong(p(-3, -32, 20), X), -0.9, 0.9, 1.4, clampMeta)))
        r.put(
            null,
            listOf(tube(KNOT, 2.2)),
            at = p(20, -8, 12),
            name = "knot:0",
            route = "knot",
            chunk = 0,
        )
        r.put(
            arts.getValue("standin"),
            listOf(ball(p(-2, -16, 8), 0.5)),
            at = p(-2, -16, 8),
            name = "standin",
        )
        r.put(
            arts.getValue("good"),
            listOf(cylinder(-12.0, 14.0, 2.0, 4.0, 6.0)),
            at = p(2, -16, 8),
            name = "good",
        )
    }

    /** The art the original's scene records as SVG markup, drawn with the Kotlin kit. */
    fun arts(): Map<String, Art> {
        val standin = art {
            solid(cylinderPaths(-2.4, -16.0, 3.0, 4.0, 8.0, P))
            line(pathOf(listOf(iso(p(-2, -16, 12), P), iso(p(-2, -16, 16), P))))
        }
        val good = art {
            solid(cylinderPaths(-12.0, 14.0, 2.0, 4.0, 6.0, P))
            dots(listOf(iso(p(-12, 14, 10), P)), size = 0.8)
            halo(haloOf(Plan(-20.0, 6.0, 16.0, 16.0, 2.0), 4.0, 6.0, P))
        }
        val clip =
            pathOf(
                listOf(iso(p(-4, -4, 0), P), iso(p(4, -4, 0), P), iso(p(4, 4, 0), P)),
                closed = true,
            )
        val clipped = art {
            group("c", clip = clip) {
                solid(slabOf(Plan(-6.0, -6.0, 12.0, 12.0, 2.0), 0.0, 6.0, P))
            }
            wire(iso(p(0, 0, 0), P), iso(p(8, 0, 0), P))
        }
        return mapOf("standin" to standin, "good" to good, "clipped" to clipped)
    }
}
