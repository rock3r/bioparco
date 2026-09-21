package dev.sebastiano.thinkingorbs

internal class OrbPlaybackClock(initialSeconds: Double = DEFAULT_ORB_CLOCK_SECONDS) {
    var seconds: Double = initialSeconds
        private set

    private var lastFrameNanos: Long? = null

    fun onFrame(frameNanos: Long) {
        val previous = lastFrameNanos
        lastFrameNanos = frameNanos
        if (previous != null) {
            seconds += (frameNanos - previous) / NANOS_PER_SECOND
        }
    }

    fun pause() {
        lastFrameNanos = null
    }

    private companion object {
        const val DEFAULT_ORB_CLOCK_SECONDS = 0.6
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
