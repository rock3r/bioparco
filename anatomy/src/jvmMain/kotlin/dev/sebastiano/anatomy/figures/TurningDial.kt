// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.turn.FreePose
import dev.sebastiano.anatomy.kit.turn.OrbitFamily
import dev.sebastiano.anatomy.kit.turn.OrbitOptions
import dev.sebastiano.anatomy.kit.turn.OrbitResult
import dev.sebastiano.anatomy.kit.turn.StaticItem
import dev.sebastiano.anatomy.kit.turn.TurnBuild
import dev.sebastiano.anatomy.kit.turn.TurnValues
import dev.sebastiano.anatomy.kit.turn.Turning
import dev.sebastiano.anatomy.kit.turn.TurningFigure
import dev.sebastiano.anatomy.kit.turn.jsCos
import dev.sebastiano.anatomy.kit.turn.jsSin
import dev.sebastiano.anatomy.kit.turn.orbit
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/*
 * `examples/turning-dial/build.mjs` of the original: a combination dial on a raised door plate,
 * with a small robot hand lying on its hub. The dial and everything on it is one turn group; an
 * index wheel under a window turns geared to it, a pawl rides its knurl on a hinge, one finger of
 * three hinges lifts and taps, and a hex pod breaks off as a free body. The live parts are in
 * TurningDialParts.kt, the static plate and stand in TurningDialStatic.kt.
 */
object TurningDial : TurningFigure {
    const val WIDTH = 640.0
    const val HEIGHT = 470.0
    const val AZIMUTH = 40.0
    const val ELEVATION = 31.0

    val BASE = Plan(-82.0, -78.0, 164.0, 156.0, 8.0)
    const val BASE_H = 12.0
    const val FOOT_H = 5.0
    val PLATE = Plan(-72.0, -68.0, 144.0, 136.0, 6.0)
    const val PLATE_Z0 = 8.0
    const val PLATE_Z1 = 10.0
    const val DIAL_R = 50.0
    const val SKIRT_TOP = 14.4
    const val FACE_Z = 17.6
    const val FACE_R = 46.0
    const val HUB_R = 12.0
    const val HUB_TOP = 19.4
    val PALM = Plan(-18.5, -16.5, 30.0, 33.0, 3.2)
    const val PALM_TOP = HUB_TOP + 8
    val COVER = Plan(-15.5, -13.2, 26.6, 26.4, 2.6)
    const val COVER_TOP = PALM_TOP + 1.2
    val INDEX_AT = ((AZIMUTH + 180) * PI) / 180

    val WHEEL_AT = Floor(40 * jsCos((AZIMUTH * PI) / 180), 40 * jsSin((AZIMUTH * PI) / 180))
    const val WHEEL_R = 32.0
    const val PINION_R = 8.0
    const val LOCK_Z0 = 3.2
    const val LOCK_Z1 = 7.2
    val WINDOW = Plan(42.4, 35.6, 12.6, 10.4, 2.2)
    val PAWL_AT = Floor(0.0, DIAL_R + 8.3)

    val P =
        fitProjection(
            boxCorners(BASE, -BASE_H - FOOT_H, 0.0) + Point3(0.0, 0.0, 40.0),
            WIDTH,
            HEIGHT,
            pad = 22.0,
            azimuth = AZIMUTH,
            elevation = ELEVATION,
        )

    const val BREAK_END = 2.2

    /** The figure's values mapped onto the coupled groups: the wheel, the pawl, the finger. */
    fun follow(values: TurnValues): TurnValues {
        val dial = values["dial"] ?: 0.0
        val lift = values["lift"] ?: 0.0
        return values +
            TurnValues(
                mapOf(
                    "wheel" to -dial / 4,
                    "pawl" to 4 * jsSin((dial * PI) / 18),
                    "f2.mcp" to -22 * lift,
                    "f2.pip" to -14 * lift,
                    "f2.dip" to -9 * lift,
                )
            )
    }

    /** The finger's tap, 0 to 1, repeating every 4 s. */
    fun tapAt(time: Double): Double {
        val phase = ((time % 4) + 4) % 4
        val up = min(1.0, max(0.0, (phase - 0.4) / 0.9))
        val down = min(1.0, max(0.0, (phase - 2.2) / 1.1))
        fun ease(x: Double) = x * x * (3 - 2 * x)
        return ease(up) * (1 - ease(down))
    }

    /**
     * The pod's flight as a pure function of time: a somersault about its centroid on a ballistic
     * arc, a flush landing on the dial face, then a decaying slide and yaw. Null before it breaks.
     */
    fun breakAt(time: Double): FreePose? {
        if (!(time > 0)) return null
        val flight = 0.79888
        val c = 1.0
        var phi: Double
        var psi = 0.0
        val y: Double
        val z: Double
        if (time < flight) {
            val s = time / flight
            phi = 2 * PI * s * s * (3 - 2 * s)
            y = 16 * time
            z = 25 * time - 49 * time * time
        } else {
            val k = 1 - exp(-(time - flight) / 0.25)
            phi = 2 * PI
            y = 16 * flight + 1.5 * k
            z = -11.3
            psi = (40 * PI * k) / 180
        }
        val cp = jsCos(phi)
        val sp = jsSin(phi)
        val cz = jsCos(psi)
        val sz = jsSin(psi)
        val r = doubleArrayOf(cz, -sz * cp, sz * sp, sz, cz * cp, -cz * sp, 0.0, sp, cp)
        return FreePose(r = r, t = Point3(-r[2] * c, y - r[5] * c, z + c - r[8] * c))
    }

    /** Every reachable state, sampled: turns with the tap, and the break at eight angles. */
    fun poses(): List<TurnValues> {
        val out = ArrayList<TurnValues>()
        for (deg in 0 until 1440) out.add(
            follow(TurnValues(mapOf("dial" to deg.toDouble(), "lift" to tapAt(deg / 30.0))))
        )
        for (start in 0 until 360 step 45) {
            for (f in 0..66) {
                val values =
                    TurnValues(
                        mapOf("dial" to start + f * 0.6, "lift" to tapAt(f / 30.0)),
                        mapOf("pod" to breakAt(f / 30.0)),
                    )
                out.add(follow(values))
            }
        }
        return out
    }

    /** The original's `frameOf(o, a, u)`: axis [a], `u` made square to it (or chosen). */
    fun frameOf(o: Point3, a: Point3, u: Point3? = null): Frame {
        val axis = unit3(a)
        val uu = if (u != null) unit3(sub3(u, mul3(axis, dot3(u, axis)))) else frameAlong(o, axis).u
        return Frame(o, axis, uu, cross3(axis, uu))
    }

    /** A vertical frame through `(x, y)`. */
    fun vert(x: Double, y: Double) =
        Frame(
            Point3(x, y, 0.0),
            Point3(0.0, 0.0, 1.0),
            Point3(1.0, 0.0, 0.0),
            Point3(0.0, 1.0, 0.0),
        )

    /** A finger's LED: where it sits at rest, its facing, its part and its group. */
    class Led(val at: Point3, val normal: Point3, val part: String, val group: String)

    /** Everything the figure is built from, once. */
    class Scene(
        val turning: Turning,
        val recorder: Recorder,
        val built: TurnBuild,
        val statics: List<StaticItem>,
        /** Static art per stack layer, in settled painter's order. */
        val art: Map<String, List<Pair<String, Art>>>,
        val led: Led,
        val podAt: Point3,
    )

    /** Builds the figure: static art and items, live parts, `T.build`, and settles the statics. */
    fun build(): Scene {
        val r = Recorder(P)
        val t = Turning(P, r)
        val live = TurningDialParts(t)
        live.build()
        val statics = TurningDialStatic(P, r)
        statics.build()
        val built = t.build(statics.items, poses())
        r.settle()
        val painted = r.painted()
        val layers = statics.items.associate { it.name to it.layer }
        val art =
            listOf("back", "mid", "front")
                .associateWith { layer ->
                    painted
                        .filter { layers[it.name] == layer }
                        .map { it.name to (it.art ?: Art.Empty) }
                }
                .toMutableMap()
        art["back"] =
            listOf("stand.halo" to art { halo(statics.baseHalo()) }) + art.getValue("back")
        return Scene(t, r, built, statics.items, art, live.led!!, live.podAt)
    }

    /** The built figure, made on first use. */
    val scene: Scene by lazy { build() }

    override fun record(): Recorder = scene.recorder

    /**
     * The orbit audit as the original's `--audit` runs it: four turns of the dial at 0.3°, the tap
     * and the break-apart at 60 fps, and the cable/finger crossings looked at and excused.
     */
    override fun orbitAudit(quick: Boolean, log: ((String) -> Unit)?): OrbitResult {
        val s = scene
        val options =
            OrbitOptions(
                sweep =
                    OrbitFamily(
                        "sweep",
                        { x -> follow(TurnValues(mapOf("dial" to x))) },
                        0.0,
                        1440.0,
                        0.3,
                        periodic = true,
                    ),
                clips = listOf(tapClip(), breakClip()),
                crowd = CROWD,
                quick = quick,
                log = log,
            )
        return orbit(s.turning, s.recorder, P, s.statics, options)
    }

    private fun tapClip() =
        OrbitFamily(
            "tap",
            { t -> follow(TurnValues(mapOf("dial" to 40 + 12 * t, "lift" to tapAt(t)))) },
            0.0,
            4.0,
            1 / 60.0,
        )

    private fun breakClip() =
        OrbitFamily(
            "break",
            { t ->
                follow(
                    TurnValues(
                        mapOf("dial" to 100 + 12 * t, "lift" to 0.3),
                        mapOf("pod" to breakAt(t)),
                    )
                )
            },
            0.0,
            BREAK_END,
            1 / 60.0,
        )

    /**
     * Cable × rod crossings excused from `crowding`: the six the original lists, then four more its
     * own full audit fails on at commit 240a9bf (each port prints the same four). Each is a cable
     * end running into its gland beside a parallel finger link or the piston rod, which crosses the
     * rod's outline at some angle whatever its offset; shot at 8× at the angle the audit names
     * (165°, 200°, 210°, 285°), each reads as two separate parts.
     */
    private val CROWD =
        listOf(
            "cable.red" to "hand.f1.p2",
            "cable.red" to "hand.f2.p1",
            "cable.red" to "hand.f2.p2",
            "cable.red" to "hand.thumb.link",
            "cable.blue" to "hand.f2.p1",
            "cable.blue" to "hand.f3.p2",
            "cable.green" to "hand.f3.p1",
            "cable.blue" to "hand.f3.p1",
            "cable.red" to "hand.f1.p1",
            "cable.green" to "hand.piston.rod",
        )

    internal fun add(a: Point3, b: Point3) = add3(a, b)
}
