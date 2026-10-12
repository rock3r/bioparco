// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Calm, time-based motion: the original's `live.tsx` helpers and the envelopes from `gl.mjs`.
 * Every function takes `dt` in seconds and a `calm` flag for reduced motion, which jumps to the
 * target. Frame loops clamp `dt` to [LONGEST_FRAME].
 */

/** The longest step a frame loop takes, so a stalled window doesn't jump. */
const val LONGEST_FRAME: Double = 1.0 / 30

/** Exponential follow with time constant [seconds]. Snaps once within 1e-3. */
fun follow(
    current: Double,
    target: Double,
    dt: Double,
    seconds: Double,
    calm: Boolean = false,
): Double {
    if (calm) return target
    val next = current + (target - current) * (1 - exp(-dt / seconds))
    return if (abs(target - next) < 1e-3) target else next
}

/** A spring's position and velocity. */
class Spring(var at: Double, var rate: Double = 0.0)

/**
 * A spring sub-stepped about 240 times a second. Under [calm] it is critically damped. Returns true
 * once it has settled on [target].
 */
fun spring(
    state: Spring,
    target: Double,
    dt: Double,
    stiffness: Double,
    damping: Double,
    calm: Boolean = false,
): Boolean {
    val drag = if (calm) 2 * sqrt(stiffness) else damping
    val steps = max(ceil(dt * 240).toInt(), 1)
    val slice = dt / steps
    repeat(steps) {
        state.rate += (target - state.at) * stiffness * slice - state.rate * drag * slice
        state.at += state.rate * slice
    }
    if (abs(target - state.at) < 5e-4 && abs(state.rate) < 5e-4) {
        state.at = target
        state.rate = 0.0
    }
    return state.at == target && state.rate == 0.0
}

/** A critically damped value: position and velocity. */
class Settling(var x: Double, var v: Double = 0.0)

/**
 * A critically damped spring with time constant [seconds], for a value that should arrive without
 * overshoot: a hue, a brightness, a reach. Returns true while it is still moving.
 */
fun settle(
    state: Settling,
    target: Double,
    dt: Double,
    seconds: Double,
    calm: Boolean = false,
): Boolean {
    if (calm) {
        state.x = target
        state.v = 0.0
        return false
    }
    val w = 1 / seconds
    repeat(4) {
        val h = dt / 4
        state.v += (w * w * (target - state.x) - 2 * w * state.v) * h
        state.x += state.v * h
    }
    if (abs(target - state.x) < 1e-4 && abs(state.v) < 1e-4) {
        state.x = target
        state.v = 0.0
        return false
    }
    return true
}

/** Smoothstep of [x] from [a] to [b]. */
fun smooth(a: Double, b: Double, x: Double): Double {
    val t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)
}

/**
 * The envelope of a one-shot event [age] seconds old: a smoothstep attack over [rise], then an
 * exponential decay with time constant [fall]. Zero before the event.
 */
fun burst(age: Double, rise: Double, fall: Double): Double =
    if (age > 0) smooth(0.0, rise, age) * exp(-max(0.0, age - rise) / fall) else 0.0
