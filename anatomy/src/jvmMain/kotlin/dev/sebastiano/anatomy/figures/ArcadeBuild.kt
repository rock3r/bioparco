// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.PartRecorder
import dev.sebastiano.anatomy.kit.audit.Piece
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.prism
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.audit.solid
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.solidOf

/** Draw and record together. Cabinet-specific concave boards and cable sampling stay here. */
internal class ArcadeBuild {
    val recorder = Recorder(Arcade.P)
    val parts = PartRecorder(recorder, mapOf("cab" to Piece("Cabinet", "", Point3(0.0, 0.0, 0.0))))
    var back = Art.Empty
    val surfaces = linkedMapOf<String, ArcadeSurface>()

    init {
        stand()
        cabinet()
        monitor()
        controls()
        coinDoor()
        chute()
        cashBox()
        board()
        power()
        harness()
        bezel()
    }

    fun put(
        name: String,
        ref: Point3,
        shapes: List<AuditShape>,
        bias: Double = 0.0,
        route: String? = null,
        chunk: Int = 0,
        draw: ArtBuilder.() -> Unit,
    ) = parts.put("cab", ref, shapes, bias, name = name, route = route, chunk = chunk, draw = draw)

    fun prism(
        name: String,
        f: Frame,
        poly: List<Floor>,
        s0: Double,
        s1: Double,
        tone: Tone = Tone.Mid,
        bias: Double = 0.0,
        ref: Point3? = null,
        inner: Art? = null,
        extra: ArtBuilder.() -> Unit = {},
    ): AuditShape {
        val shape = recorder.solid(prism(f, poly, s0, s1, ShapeMeta(name = name)))
        val centre =
            add3(
                add3(
                    add3(f.o, mul3(f.a, (s0 + s1) / 2)),
                    mul3(f.u, poly.sumOf { it.x } / poly.size),
                ),
                mul3(f.v, poly.sumOf { it.y } / poly.size),
            )
        put(name, ref ?: centre, listOf(shape), bias) {
            solid(arcadePrism(f, poly, s0, s1), tone, inner = inner)
            extra()
        }
        return shape
    }

    fun board(
        name: String,
        poly: List<Floor>,
        x0: Double = -60.0,
        x1: Double = 60.0,
        tone: Tone = Tone.Mid,
        bias: Double = 0.0,
        holes: List<Floor> = emptyList(),
        extra: ArtBuilder.() -> Unit = {},
    ) =
        prism(name, Arcade.XF, poly, x0, x1, tone, bias) {
            dots(holes.map { Arcade.at(Point3(x0, it.x, it.y)) }, 0.5, Tone.Lo)
            extra()
        }

    fun box(
        name: String,
        lo: Point3,
        hi: Point3,
        tone: Tone = Tone.Mid,
        r: Double = 0.4,
        steps: Int = 2,
        bevel: Double = 0.0,
        bias: Double = 0.0,
        inner: Art? = null,
        extra: ArtBuilder.() -> Unit = {},
    ): AuditShape {
        val plan = Plan(lo.x, lo.y, hi.x - lo.x, hi.y - lo.y, r)
        val shape = recorder.solid(slab(plan, lo.z, hi.z - lo.z, ShapeMeta(name = name), steps))
        put(
            name,
            Point3((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, (lo.z + hi.z) / 2),
            listOf(shape),
            bias,
        ) {
            solid(slabOf(plan, lo.z, hi.z - lo.z, Arcade.P, steps, bevel), tone, inner = inner)
            extra()
        }
        return shape
    }

    fun turned(
        name: String,
        f: Frame,
        profile: List<Knot>,
        tone: Tone = Tone.Mid,
        steps: Int? = null,
        bevel: Double = 0.0,
        owner: String? = null,
        inner: Art? = null,
        extra: ArtBuilder.() -> Unit = {},
    ): AuditShape {
        val shape = recorder.solid(solid(f, profile, meta = ShapeMeta(name = name, owner = owner)))
        val smooth = Smooth.auto(profile)
        val paths =
            if (steps == null) solidOf(profile, f, Arcade.P, bevel = bevel, smooth = smooth)
            else solidOf(profile, f, Arcade.P, steps, bevel = bevel, smooth = smooth)
        put(name, add3(f.o, mul3(f.a, (profile.first().s + profile.last().s) / 2)), listOf(shape)) {
            solid(paths, tone, inner = inner)
            extra()
        }
        return shape
    }

    fun disc(
        name: String,
        f: Frame,
        s0: Double,
        s1: Double,
        r: Double,
        tone: Tone = Tone.Mid,
        steps: Int? = null,
        bevel: Double = 0.0,
        owner: String? = if (name.startsWith("crt.")) "crt" else null,
        inner: Art? = null,
        extra: ArtBuilder.() -> Unit = {},
    ) = turned(name, f, listOf(Knot(s0, r), Knot(s1, r)), tone, steps, bevel, owner, inner, extra)
}
