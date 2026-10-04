// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/*
 * The two clocks. A discrete change (which card) gets a long ease-out on a tween; a continuous
 * input (where the pointer is) gets a spring, because its target moves every frame and a timed
 * ease would always be chasing it.
 *
 * Reduced motion is one flag, as in the original: the host writes it and figures read it. With it
 * on, springs and tweens land on their target in one step.
 */

/**
 * The reduced-motion flag every figure reads. The original's is page-wide; here each figure has its
 * own, so a host sets it around every call into its figure with [withReducedMotion].
 */
object ReducedMotion {
    var enabled: Boolean = false
}

/** Runs [block] with reduced motion [on], then puts the flag back as it was. */
inline fun <T> withReducedMotion(on: Boolean, block: () -> T): T {
    val before = ReducedMotion.enabled
    ReducedMotion.enabled = on
    try {
        return block()
    } finally {
        ReducedMotion.enabled = before
    }
}

fun reducedMotion(): Boolean = ReducedMotion.enabled

/**
 * A spring at [x] heading for [t]. k 100 · c 18 · m 1 unless told otherwise; [eps] is when it
 * counts as settled.
 */
class Spring(
    var x: Double,
    var k: Double = 100.0,
    var c: Double = 18.0,
    var m: Double = 1.0,
    var eps: Double = 0.01,
) {
    var v: Double = 0.0
    var t: Double = x
}

/** A spring at rest on [x]. */
fun spring(
    x: Double,
    k: Double = 100.0,
    c: Double = 18.0,
    m: Double = 1.0,
    eps: Double = 0.01,
): Spring = Spring(x, k, c, m, eps)

/**
 * Advances a spring by dt seconds toward its target, substepped at 240 Hz so a long frame can't
 * make it overshoot. Returns whether it is still moving.
 */
fun stepS(sp: Spring, dt: Double): Boolean {
    if (ReducedMotion.enabled) {
        sp.x = sp.t
        sp.v = 0.0
        return false
    }
    val n = max(1.0, ceil(dt * 240)).toInt()
    val h = dt / n
    repeat(n) {
        val a = (-sp.k * (sp.x - sp.t) - sp.c * sp.v) / sp.m
        sp.v += a * h
        sp.x += sp.v * h
    }
    if (abs(sp.x - sp.t) < sp.eps && abs(sp.v) < sp.eps * 10) {
        sp.x = sp.t
        sp.v = 0.0
        return false
    }
    return true
}

/** A CSS cubic-bezier as a function of progress: Newton first, bisection if it strays. */
fun bezier(x1: Double, y1: Double, x2: Double, y2: Double): (Double) -> Double {
    val cx = 3 * x1
    val bx = 3 * (x2 - x1) - cx
    val ax = 1 - cx - bx
    val cy = 3 * y1
    val by = 3 * (y2 - y1) - cy
    val ay = 1 - cy - by
    val fx = { u: Double -> ((ax * u + bx) * u + cx) * u }
    val fy = { u: Double -> ((ay * u + by) * u + cy) * u }
    val dx = { u: Double -> (3 * ax * u + 2 * bx) * u + cx }
    return { t ->
        when {
            t <= 0 -> 0.0
            t >= 1 -> 1.0
            else -> fy(solve(t, fx, dx))
        }
    }
}

private fun solve(t: Double, fx: (Double) -> Double, dx: (Double) -> Double): Double {
    var u = t
    var i = 0
    while (i < 8) {
        val e = fx(u) - t
        val d = dx(u)
        if (abs(e) < 1e-5 || abs(d) < 1e-6) break
        u -= e / d
        i++
    }
    if (!(u in 0.0..1.0) || abs(fx(u) - t) > 1e-4) {
        var lo = 0.0
        var hi = 1.0
        u = t
        repeat(24) {
            if (fx(u) < t) lo = u else hi = u
            u = (lo + hi) / 2
        }
    }
    return u
}

/** Linear's lift curve, 700ms (.32, .72, 0, 1): the discrete clock. */
val EASE_LIFT: (Double) -> Double = bezier(0.32, 0.72, 0.0, 1.0)

/**
 * A tween on [EASE_LIFT]. [t0] is when it starts (it may be in the future: that is how a stagger
 * delays it), [dur] how long it runs, in ms of `now`.
 */
class Tween(var from: Double, var to: Double, var t0: Double, var dur: Double)

fun tween(v: Double, dur: Double = 700.0): Tween = Tween(v, v, -1e9, dur)

/** Where the tween is at [now]. */
fun tval(tw: Tween, now: Double): Double {
    val p = clamp((now - tw.t0) / tw.dur, 0.0, 1.0)
    return tw.from + (tw.to - tw.from) * (if (ReducedMotion.enabled) 1.0 else EASE_LIFT(p))
}

/**
 * Retargets from wherever it is now, starting after [delay] ms. A no-op if the target is unchanged.
 */
fun tset(tw: Tween, to: Double, now: Double, delay: Double) {
    if (tw.to == to) return
    tw.from = tval(tw, now)
    tw.to = to
    tw.t0 = now + delay
}

fun tdone(tw: Tween, now: Double): Boolean = ReducedMotion.enabled || now >= tw.t0 + tw.dur
