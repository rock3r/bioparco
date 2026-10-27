package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.Uniform
import dev.sebastiano.anatomy.kit.audit.ball
import dev.sebastiano.anatomy.kit.audit.box
import dev.sebastiano.anatomy.kit.audit.cylinder as cylinderShape
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.audit.tube
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.sphereOf
import dev.sebastiano.anatomy.kit.sub3
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A figure recorded for the audit the way a real one is: items put with their art and shapes, a
 * pipe recorded as a route and drawn as legs with elbow balls. Settled, it paints back to front and
 * the audit passes; with the pipe dropped through the tank and a bracket left in the air, it fails.
 */
class RecorderAuditTest {
    private val p = Projection(Flat(300.0, 200.0), 3.0)

    private fun pt(x: Number, y: Number, z: Number) =
        Point3(x.toDouble(), y.toDouble(), z.toDouble())

    /** A pipe drawn as straight legs between elbow balls, its rims on the balls. */
    private fun Recorder.pipe(name: String, points: List<Point3>, r: Double) {
        route(name, points, r)
        val elbow = r * 1.3
        val trim = sqrt(elbow * elbow - r * r)
        var chunk = 0
        for (i in 0 until points.size - 1) {
            val axis = sub3(points[i + 1], points[i])
            val s0 = if (i > 0) trim else 0.0
            val s1 = if (i < points.size - 2) len3(axis) - trim else len3(axis)
            val unit = mul3(axis, 1 / len3(axis))
            val ends = listOf(add3(points[i], mul3(unit, s0)), add3(points[i], mul3(unit, s1)))
            put(
                art { solid(disc(s0, s1, r, frameAlong(points[i], axis), p), tone = Tone.Mid) },
                listOf(tube(ends, r)),
                at = mul3(add3(ends[0], ends[1]), 0.5),
                name = "$name:$chunk",
                route = name,
                chunk = chunk++,
            )
            if (i < points.size - 2) {
                val at = points[i + 1]
                put(
                    art { solid(sphereOf(at, elbow, p), tone = Tone.Mid) },
                    listOf(solid(ball(at, elbow, ShapeMeta(name = "$name.elbow$i", owner = name)))),
                    at = at,
                    name = "$name:$chunk",
                    route = name,
                    chunk = chunk++,
                )
            }
        }
    }

    /** Put in a deliberately wrong order: pipes first, the base last. */
    private fun plant(broken: Boolean): Recorder {
        val r = Recorder(p)
        val z = if (broken) 18 else 28
        r.pipe(
            "feed",
            listOf(
                pt(-14, -4, 22),
                pt(-14, -4, z),
                pt(-14, -16, z),
                pt(16, -16, z),
                pt(16, -16, 20),
            ),
            1.5,
        )
        val tank = Plan(8.0, -22.0, 16.0, 16.0, 2.0)
        r.put(
            art { solid(slabOf(tank, 4.0, 16.0, p)) },
            listOf(r.solid(slab(tank, 4.0, 16.0, ShapeMeta(name = "tank")))),
            at = pt(16, -14, 20),
            name = "tank",
        )
        r.put(
            art { solid(cylinder(-14.0, -4.0, 7.0, 4.0, 18.0, p)) },
            listOf(r.solid(cylinderShape(-14.0, -4.0, 7.0, 4.0, 18.0, ShapeMeta(name = "pump")))),
            at = pt(-14, -4, 22),
            name = "pump",
        )
        val lift = if (broken) 30 else 14
        r.put(
            art { solid(slabOf(Plan(-4.0, 10.0, 8.0, 6.0, 1.0), lift.toDouble(), 3.0, p)) },
            listOf(
                r.solid(box(pt(-4, 10, lift), pt(4, 16, lift + 3), ShapeMeta(name = "bracket")))
            ),
            at = pt(0, 13, lift + 3),
            name = "bracket",
        )
        r.put(
            art { solid(cylinder(0.0, 13.0, 1.2, 4.0, 10.0, p)) },
            listOf(r.solid(cylinderShape(0.0, 13.0, 1.2, 4.0, 10.0, ShapeMeta(name = "post")))),
            at = pt(0, 13, 14),
            name = "post",
        )
        val base = Plan(-30.0, -30.0, 60.0, 50.0, 4.0)
        r.put(
            art { solid(slabOf(base, 0.0, 4.0, p), tone = Tone.Lo) },
            listOf(r.solid(slab(base, 0.0, 4.0, ShapeMeta(name = "stand.base")))),
            at = pt(0, 0, 4),
            name = "base",
        )
        return r
    }

    private fun audited(broken: Boolean) =
        object : AuditedFigure {
            override fun record() = plant(broken).also { it.settle() }
        }

    @Test
    fun `settling paints the base first and things on it later`() {
        val r = plant(broken = false)
        r.settle()
        val order = r.painted().map { it.name }
        val pairs =
            listOf("base" to "pump", "base" to "tank", "base" to "post") +
                listOf("pump" to "bracket", "post" to "bracket", "tank" to "feed:6")
        for (pair in pairs) {
            assertTrue(order.indexOf(pair.first) < order.indexOf(pair.second), "$pair in $order")
        }
        assertEquals(order, r.painted(Uniform(0.0)).map { it.name })
        assertEquals(r.painted().flatMap { it.art!!.marks }, r.paintedArt().marks)
    }

    @Test
    fun `the audit passes a sound figure and writes its report`() {
        val log = File(createTempDirectory("audit").toFile(), "plant-audit.txt")
        assertEquals(0, auditFigure(audited(broken = false), log))
        assertTrue(log.readText().trimEnd().endsWith("audit passed"), log.readText())
    }

    @Test
    fun `the audit fails a pipe through the tank and a bracket in the air`() {
        val log = File(createTempDirectory("audit").toFile(), "plant-audit.txt")
        val failures = auditFigure(audited(broken = true), log)
        val text = log.readText()
        assertEquals(5, failures, text)
        assertTrue("through feed  ×  tank" in text, text)
        assertTrue("loose   bracket" in text, text)
        assertTrue(text.trimEnd().endsWith("audit failed: 5 problems"), text)
    }
}
