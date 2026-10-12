// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.animation.core.CubicBezierEasing

/** CSS transitions restart from the displayed value when the target reverses. */
internal class ArcadeFade(private val seconds: Double) {
    private var at = 0.0
    private var from = 0.0
    private var to = 0.0
    private val ease = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    fun value(clock: Double, on: Boolean, calm: Boolean): Float {
        val value =
            from +
                (to - from) * ease.transform(((clock - at) / seconds).coerceIn(0.0, 1.0).toFloat())
        val next = if (on) 1.0 else 0.0
        if (next != to) {
            from = value
            to = next
            at = clock
        }
        return (if (calm) to else value).toFloat()
    }
}
