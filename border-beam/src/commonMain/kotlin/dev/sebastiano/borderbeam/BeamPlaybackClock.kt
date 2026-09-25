package dev.sebastiano.borderbeam

/**
 * Seconds and fade for one beam. The composable publishes these into draw state; nothing in
 * composition should read [seconds] or [opacity].
 */
internal class BeamPlaybackClock {
    var seconds: Float = 0f
        private set

    var opacity: Float = 0f
        private set

    private var lastFrameNanos: Long? = null

    fun onFrame(frameNanos: Long, active: Boolean) {
        val previous = lastFrameNanos
        lastFrameNanos = frameNanos
        if (previous == null) return
        val dt = (frameNanos - previous) / NANOS_PER_SECOND
        if (dt <= 0f) return
        if (!active && opacity <= 0f) return
        seconds += dt
        val target = if (active) 1f else 0f
        val fade = if (active) FADE_IN_SECONDS else FADE_OUT_SECONDS
        val step = dt / fade
        opacity =
            if (target >= opacity) {
                minOf(target, opacity + step)
            } else {
                maxOf(target, opacity - step)
            }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val FADE_IN_SECONDS = 0.6f
        const val FADE_OUT_SECONDS = 0.5f
    }
}
