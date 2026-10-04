// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Camera
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.Projector
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fillet
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.ringAt
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.seg
import kotlin.math.cos
import kotlin.math.sin

/*
 * Riffle's drawing, with no tree: the camera, the tray and a card in any pose, as paths. The
 * engine (Riffle.kt) writes these into its tree on every frame.
 */

internal const val RIFFLE_N = 8
internal const val RIFFLE_W = 84.0
internal const val RIFFLE_H = 54.0
internal const val RIFFLE_G = 13.0
private const val TW = 22.0
private const val TH = 7.0
private val TABS = doubleArrayOf(6.0, 31.0, 56.0)
private const val TK = 1.4
internal const val RIFFLE_REST = -12.0
internal const val RIFFLE_BACK = -24.0
internal const val RIFFLE_FWD = 20.0
internal const val RIFFLE_LIFT = 16.0
private const val X0 = -5.0
private const val X1 = RIFFLE_W + 5
private const val Y0 = -9.0
private const val Y1 = (RIFFLE_N - 1) * RIFFLE_G + 9
private const val WH = 20.0
private const val WR = 6.0
private const val WT = 2.4

/** The camera, fitted to the tray with a card lifted, so nothing leaves the frame in any pose. */
private fun riffleCamera(): Camera {
    val c = cam(45.0, 0.5, 1.62)
    fit(
        c,
        listOf(
            Vec3(X0, Y0, 0.0),
            Vec3(X1, Y1, -8.0),
            Vec3(X1, Y0, 0.0),
            Vec3(X0, Y1, 0.0),
            Vec3(X0, Y0, RIFFLE_H + TH),
            Vec3(X1, Y0, RIFFLE_H + TH + RIFFLE_LIFT),
        ),
        200.0,
        166.0,
    )
    return c
}

/** A run of points ordered left to right on screen. */
private fun lr(pts: List<Vec2>): List<Vec2> =
    if (pts.first().x <= pts.last().x) pts else pts.reversed()

/** The tray's paths, which never move: [far] is painted before the cards, [near] after them. */
internal class TrayPaths(
    val far: List<Pair<PathData, String>>,
    val near: List<Pair<PathData, String>>,
)

internal fun tray(p: Projector, front: (Sample) -> Boolean, outer: Ring, inner: Ring): TrayPaths {
    // far half: body, the rim's inner edge, and the floor seam along the far walls
    val far =
        listOf(
            poly(hull(ringAt(p, outer, 0.0) + ringAt(p, outer, WH))) to "sil",
            poly(ringAt(p, inner, WH)) to "nf",
            open(ringAt(p, run(inner) { !front(it) }, 2.5)) to "nf lo",
        )
    // near half: one opaque piece from the rim's inner edge down to the floor
    val iF = lr(ringAt(p, run(inner, front), WH))
    val oT = lr(ringAt(p, run(outer, front), WH))
    val oB = lr(ringAt(p, run(outer, front), 0.0))
    // a finger pull, set into the front
    val hx = (X0 + X1) / 2
    val onFront = { ring: Ring -> ring.map { q -> p(q.u, Y1, q.v) } }
    val near =
        listOf(
            poly(iF + oT.last() + oB.reversed() + oT.first()) to "fo",
            open(oT) to "nf lo",
            open(iF) to "nf",
            open(listOf(oT.first()) + oB + oT.last()) to "nf sil",
            poly(onFront(rrect(hx - 11, 6.5, hx + 11, 12.5, 3.0, 5))) to "nf",
            poly(onFront(rrect(hx - 9.4, 8.0, hx + 9.4, 11.0, 1.5, 5))) to "nf lo",
        )
    return TrayPaths(far, near)
}

/**
 * Card i: its number (8 at the back), which of the three tab positions it takes, and its outline.
 */
internal class CardShape(val n: Int, val t0: Double, val shape: List<Vec2>)

internal fun card(i: Int): CardShape {
    val n = RIFFLE_N - i
    val t0 = TABS[(RIFFLE_N - 1 - i) % 3]
    val w = RIFFLE_W
    val h = RIFFLE_H
    val shape =
        fillet(
            listOf(
                Vec2(0.0, 0.0),
                Vec2(w, 0.0),
                Vec2(w, h),
                Vec2(t0 + TW, h),
                Vec2(t0 + TW, h + TH),
                Vec2(t0, h + TH),
                Vec2(t0, h),
                Vec2(0.0, h),
            ),
            listOf(1.0, 1.0, 3.2, 1.8, 2.4, 2.4, 1.8, 3.2),
        )
    return CardShape(n, t0, shape)
}

internal class CardPose(
    val back: PathData,
    val face: PathData,
    val head: PathData,
    val rules: PathData,
    val punch: List<Vec2>,
)

/**
 * Card i leaning th degrees (negative leans back) and lifted by [lift]: its back edge, face,
 * heading and rules as paths, and where each of the eight punches of its number sits, in a 4 × 2
 * grid on the tab.
 */
internal fun pose(
    p: Projector,
    i: Int,
    t0: Double,
    shape: List<Vec2>,
    th: Double,
    lift: Double,
): CardPose {
    val yb = i * RIFFLE_G
    val s = sin(rad(th))
    val c = cos(rad(th))
    val h = RIFFLE_H
    val w = { u: Double, v: Double -> p(u, yb + v * s, v * c + lift) }
    val wb = { u: Double, v: Double -> p(u, yb + v * s - TK * c, v * c + TK * s + lift) }
    val punch =
        List(8) { k -> w(t0 + TW / 2 + (k % 4 - 1.5) * 3.6, h + TH / 2 + (0.5 - k / 4) * 2.8) }
    return CardPose(
        back = poly(shape.map { wb(it.x, it.y) }),
        face = poly(shape.map { w(it.x, it.y) }),
        head = seg(w(6.0, h - 11), w(RIFFLE_W - 6, h - 11)),
        rules =
            listOf(h - 18, h - 25, h - 32, h - 39)
                .map { v -> seg(w(6.0, v), w(RIFFLE_W - 6, v)) }
                .join(),
        punch = punch,
    )
}

/** The fitted camera, its projector and facing test, and the tray's rings. */
internal class RiffleScene {
    val c = riffleCamera()
    val p = proj(c)
    val front = facing(c)

    /** The tray's outline and the rim's inner edge. */
    val outer = rrect(X0, Y0, X1, Y1, WR, 6)
    val inner = rrect(X0 + WT, Y0 + WT, X1 - WT, Y1 - WT, WR - WT, 6)
}
