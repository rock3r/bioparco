// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.ball
import dev.sebastiano.anatomy.kit.audit.disc as recordedDisc
import dev.sebastiano.anatomy.kit.audit.prism as recordedPrism
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.prismOf
import dev.sebastiano.anatomy.kit.sphereOf
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal object ArcadeParts {
    fun rotate(v: Point3, axis: Point3, angle: Double): Point3 {
        val c = cos(angle)
        val s = sin(angle)
        val d = dot3(axis, v)
        val x = cross3(axis, v)
        return Point3(
            v.x * c + x.x * s + axis.x * d * (1 - c),
            v.y * c + x.y * s + axis.y * d * (1 - c),
            v.z * c + x.z * s + axis.z * d * (1 - c),
        )
    }

    fun axis(tx: Double, tv: Double) =
        unit3(rotate(rotate(Arcade.CPN, Arcade.CPD, tx), Arcade.X, -tv))

    fun closed(tx: Double, tv: Double): List<String> = buildList {
        if (tx > 0.12) add("left")
        if (tx < -0.12) add("right")
        if (tv > 0.12) add("up")
        if (tv < -0.12) add("down")
    }

    fun upper(tx: Double = 0.0, tv: Double = 0.0): Art = art {
        val a = axis(tx, tv)
        val f = frameAlong(Arcade.PIVOT, a)
        val t = 4 / dot3(a, Arcade.CPN)
        val cross = add3(Arcade.PIVOT, mul3(a, t))
        val w = frameOf(cross, Arcade.CPN, Arcade.X, Arcade.CPD)
        solid(disc(0.0, 0.4, 4.4, w, Arcade.P, steps = 40), Tone.Mid)
        line(circleOf(w, 0.4, 1.3, Arcade.P, 20), Tone.Faint)
        solid(disc(t + 0.4, t + 8.95, 0.85, f, Arcade.P, steps = 16), Tone.Mid)
        solid(
            sphereOf(add3(Arcade.PIVOT, mul3(a, t + 12.4)), 3.5, Arcade.P, steps = 40),
            Tone.Hi,
            lit = true,
        )
    }

    fun lower(tx: Double = 0.0, tv: Double = 0.0): Art {
        val a = axis(tx, tv)
        val f = frameAlong(Arcade.PIVOT, a)
        val p = Arcade.P
        val items = arrayListOf<Pair<Double, Art>>()
        fun depth(s: Double) = dot3(add3(Arcade.PIVOT, mul3(a, s)), Arcade.V)
        items.add(depth(-5.0) to art { solid(disc(-9.4, -0.6, 0.85, f, p, steps = 16), Tone.Mid) })
        items.add(
            depth(-6.6) + 0.01 to
                art {
                    solid(disc(-8.6, -4.6, 2.4, f, p, steps = 32, bevel = 0.3), Tone.Hi)
                    line(arcOf(f, -6.6, 2.4, p), Tone.Faint)
                }
        )
        items.add(depth(-9.6) to art { solid(disc(-10.0, -9.4, 1.5, f, p, steps = 20), Tone.Lo) })
        val on = closed(tx, tv)
        for (sw in Arcade.SWITCHES) items.add(dot3(sw.c, Arcade.V) to switchArt(sw, sw.id in on))
        return art { items.sortedBy { it.first }.forEach { add(it.second) } }
    }

    private fun switchArt(sw: ArcadeSwitch, on: Boolean) = art {
        for (o in listOf(-1.1, 0.0, 1.1)) {
            val at = add3(sw.c, add3(mul3(Arcade.CPN, -1.0), mul3(sw.side, o)))
            solid(
                prismOf(
                    frameOf(at, Arcade.CPN, sw.out, sw.side),
                    Arcade.rect(-0.3, 0.3, -0.35, 0.35),
                    -1.0,
                    0.0,
                    Arcade.P,
                ),
                Tone.Lo,
            )
        }
        solid(
            prismOf(
                frameOf(sw.c, Arcade.CPN, sw.out, sw.side),
                Arcade.rect(-0.65, 0.65, -2.0, 2.0),
                -1.0,
                1.0,
                Arcade.P,
            ),
            Tone.Mid,
        )
        val start = add3(sw.c, add3(mul3(sw.out, -0.65), mul3(sw.side, 1.6)))
        val tip = add3(sw.c, add3(mul3(sw.out, if (on) -0.85 else -1.45), mul3(sw.side, -1.2)))
        line(pathOf(listOf(start, tip).map(Arcade::at)), if (on) Tone.Lit else Tone.Hi)
    }

    fun coin(c: Point3, axis: Point3, spin: Double = 0.0, lit: Boolean = true): Art = art {
        val f0 = frameAlong(c, axis)
        val f = frameOf(f0.o, f0.a, rotate(f0.u, f0.a, spin), rotate(f0.v, f0.a, spin))
        val face = if (dot3(f.a, Arcade.V) >= 0) Arcade.COIN_T / 2 else -Arcade.COIN_T / 2
        solid(
            disc(-Arcade.COIN_T / 2, Arcade.COIN_T / 2, Arcade.COIN_R, f, Arcade.P, steps = 36),
            Tone.Hi,
            lit = lit,
        )
        if (abs(dot3(f.a, Arcade.V)) > 0.2) {
            line(circleOf(f, face, Arcade.COIN_R - 0.35, Arcade.P, 28), Tone.Lo)
            line(
                pathOf(
                    listOf(pointOf(f, face, 0.3, 0.0), pointOf(f, face, 1.5, 0.0)).map(Arcade::at)
                ),
                Tone.Lo,
                free = Free.Both,
            )
        }
    }
}

internal fun ArcadeBuild.controls() {
    val j = Arcade.JO
    val f = Arcade.JF
    val up = Arcade.CPN
    prism("stick.plate", f, Arcade.rect(-9.0, 9.0, -9.0, 9.0), -3.6, -3.0)
    val corners =
        listOf(-7.0 to -7.0, 7.0 to -7.0, -7.0 to 7.0, 7.0 to 7.0).map { (u, v) ->
            add3(add3(j, mul3(Arcade.X, u)), mul3(Arcade.CPD, v))
        }
    for (sw in Arcade.SWITCHES) {
        val frame = frameOf(add3(sw.c, mul3(sw.out, 0.65)), up, sw.out, sw.side)
        prism("stick.bracket", frame, Arcade.rect(0.0, 0.5, -2.2, 2.2), -1.0, 6.8, Tone.Lo)
    }
    disc("stick.bushing", f, -5.2, -3.6, 2.2, Tone.Lo, owner = "stick")
    val pivot = frameAlong(Arcade.PIVOT, up)
    val shapes =
        arrayListOf<dev.sebastiano.anatomy.kit.audit.AuditShape>(
            recorder.solid(
                recordedDisc(
                    pivot,
                    -8.6,
                    -4.6,
                    2.4,
                    ShapeMeta(name = "stick.actuator", owner = "stick"),
                )
            ),
            recorder.solid(
                recordedDisc(
                    pivot,
                    -9.4,
                    -0.6,
                    0.85,
                    ShapeMeta(name = "stick.shaft.lower", owner = "stick"),
                )
            ),
            recorder.solid(
                recordedDisc(
                    pivot,
                    -10.0,
                    -9.4,
                    1.5,
                    ShapeMeta(name = "stick.clip", owner = "stick"),
                )
            ),
        )
    for (sw in Arcade.SWITCHES) shapes.add(
        recorder.solid(
            recordedPrism(
                frameOf(sw.c, up, sw.out, sw.side),
                Arcade.rect(-0.65, 0.65, -2.0, 2.0),
                -1.0,
                1.0,
                ShapeMeta(name = "stick.switch.${sw.id}"),
            )
        )
    )
    for (sw in Arcade.SWITCHES) for (o in listOf(-1.1, 0.0, 1.1)) {
        val at = add3(add3(sw.c, mul3(up, -1.0)), mul3(sw.side, o))
        shapes.add(
            recorder.solid(
                recordedPrism(
                    frameOf(at, up, sw.out, sw.side),
                    Arcade.rect(-0.3, 0.3, -0.35, 0.35),
                    -1.0,
                    0.0,
                    ShapeMeta(name = "stick.tab.${sw.id}", owner = "stick.switch.${sw.id}"),
                )
            )
        )
    }
    put("stick.lower", add3(j, mul3(up, -9.0)), shapes) { add(ArcadeParts.lower()) }
    put(
        "stick.bolts",
        j,
        corners.map { recorder.solid(ball(it, 0.4, ShapeMeta(name = "stick.bolt"))) },
        0.1,
    ) {
        dots(corners.map(Arcade::at), 0.55, Tone.Mid)
    }
    put(
        "stick.upper",
        add3(j, mul3(up, 8.0)),
        listOf(
            recorder.solid(
                ball(add3(j, mul3(up, 12.4)), 3.5, ShapeMeta(name = "stick.ball", owner = "stick"))
            ),
            recorder.solid(
                recordedDisc(
                    frameAlong(j, up),
                    0.4,
                    8.95,
                    0.85,
                    ShapeMeta(name = "stick.shaft", owner = "stick"),
                )
            ),
            recorder.solid(
                recordedDisc(
                    frameAlong(j, up),
                    0.0,
                    0.4,
                    4.4,
                    ShapeMeta(name = "stick.washer", owner = "stick"),
                )
            ),
        ),
        2.0,
    ) {
        add(ArcadeParts.upper())
    }
    buttons()
}

private fun ArcadeBuild.buttons() {
    val buttons =
        listOf(
            Triple("fire", -18.0, 158.0),
            Triple("jump", -6.0, 162.0),
            Triple("p1", 18.0, 146.0),
            Triple("p2", 32.0, 146.0),
        )
    for ((id, x, y) in buttons) {
        val o = Arcade.cpPoint(x, y)
        val f = Arcade.cpf(o)
        val r = if (id.startsWith("p")) 2.2 else 2.6
        val bez =
            recorder.solid(recordedDisc(f, 0.0, 0.7, r + 1, ShapeMeta(name = "button.$id.bezel")))
        put("button.$id.bezel", o, listOf(bez), 0.5) {
            solid(disc(0.0, 0.7, r + 1, f, Arcade.P, steps = 36), Tone.Mid)
        }
        val cap = recorder.solid(recordedDisc(f, 0.7, 2.2, r, ShapeMeta(name = "button.$id")))
        put("button.$id", add3(o, mul3(Arcade.CPN, 1.5)), listOf(cap), 0.8) {
            solid(disc(0.7, 2.2, r, f, Arcade.P, steps = 32), Tone.Hi)
            line(circleOf(f, 2.2, r * 0.68, Arcade.P, 24), Tone.Lo)
        }
        disc("button.$id.housing", f, -8.0, -4.6, 2.3)
        disc("button.$id.nut", f, -4.6, -3.0, 3.6, Tone.Lo, steps = 6)
        prism(
            "button.$id.switch",
            Arcade.cpf(add3(o, mul3(Arcade.CPN, -8.0))),
            Arcade.rect(-2.0, 2.0, -1.3, 1.3),
            -4.2,
            0.0,
        )
    }
}
