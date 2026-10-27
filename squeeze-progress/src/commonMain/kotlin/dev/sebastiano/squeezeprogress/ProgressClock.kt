package dev.sebastiano.squeezeprogress

/**
 * The bar's own clock: it fills over [runMillis], holds full for [holdMillis], then rewinds to
 * empty over [rewindMillis] and runs again. While [playing] is false every phase waits.
 *
 * Time is kept in whole milliseconds, so the bar reads exactly 100% only when it is full.
 */
class ProgressClock(
    private val runMillis: Long,
    private val holdMillis: Long,
    private val rewindMillis: Long,
) {
    var playing = true

    private var phase = Phase.Run
    private var elapsed = 0L

    val progress: Float
        get() =
            when (phase) {
                Phase.Run -> elapsed.toFloat() / runMillis
                Phase.Hold -> 1f
                Phase.Rewind -> 1f - elapsed.toFloat() / rewindMillis
            }

    /** Whole percent, rounded down. */
    val percent: Int
        get() =
            when (phase) {
                Phase.Run -> (elapsed * PERCENT / runMillis).toInt()
                Phase.Hold -> PERCENT.toInt()
                Phase.Rewind -> ((rewindMillis - elapsed) * PERCENT / rewindMillis).toInt()
            }

    val isFull: Boolean
        get() = phase == Phase.Hold

    fun advance(millis: Long) {
        if (!playing) return
        elapsed += millis
        when (phase) {
            // Whatever is left of the frame that fills the bar is dropped: a long frame (a hitch,
            // a window drag) must not eat into the hold.
            Phase.Run ->
                if (elapsed >= runMillis) {
                    phase = Phase.Hold
                    elapsed = 0L
                }
            Phase.Hold ->
                if (elapsed >= holdMillis) {
                    phase = Phase.Rewind
                    elapsed -= holdMillis
                    if (elapsed >= rewindMillis) restart(elapsed - rewindMillis)
                }
            Phase.Rewind -> if (elapsed >= rewindMillis) restart(elapsed - rewindMillis)
        }
    }

    private fun restart(carry: Long) {
        phase = Phase.Run
        elapsed = carry.coerceAtMost(runMillis - 1)
    }

    private enum class Phase {
        Run,
        Hold,
        Rewind,
    }
}

private const val PERCENT = 100L
