package dev.sebastiano.routinetimeline

import kotlin.math.floor

/** Minutes a window takes to light up after it starts, and to dim before it ends. */
const val PRESENCE_RAMP = 20.0

enum class RoutineKind {
    Light,
    Caffeine,
    Meal,
    WindDown,
    Sleep,
}

/** Which ring of the dial a window sits on. */
enum class Lane {
    Outer,
    Inner,
}

enum class WindowStatus {
    Earlier,
    InWindow,
    Later,
}

/**
 * Something to do between [startMinute] and [endMinute] every day. A window whose end comes before
 * its start crosses midnight.
 */
data class RoutineWindow(
    val kind: RoutineKind,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val lane: Lane,
    val detail: String? = null,
) {
    val durationMinutes: Int = (endMinute - startMinute).mod(MINUTES_PER_DAY)

    /** The list's second line: the window's own note, or its times. */
    val subtitle: String
        get() = detail ?: rangeLabel(startMinute, endMinute)
}

/**
 * A day of windows that repeats forever. The list counts windows through the days with an
 * _ordinal_: window `i` of day `d` is `d * size + i`, so ordinals rise in the order windows start.
 */
class Routine(windows: List<RoutineWindow>) {
    val windows: List<RoutineWindow> = windows.sortedBy { it.startMinute }

    val size: Int
        get() = windows.size

    fun statusOf(window: RoutineWindow, time: Double): WindowStatus {
        val sinceStart = minutesSinceStart(window, time)
        if (sinceStart < window.durationMinutes) return WindowStatus.InWindow
        val sinceEnd = sinceStart - window.durationMinutes
        val untilStart = MINUTES_PER_DAY - sinceStart
        return if (sinceEnd <= untilStart) WindowStatus.Earlier else WindowStatus.Later
    }

    /**
     * How lit [window] is at [time]: 0 outside it, rising to 1 over the first [PRESENCE_RAMP]
     * minutes and falling back over the last.
     */
    fun presenceOf(window: RoutineWindow, time: Double): Float {
        val sinceStart = minutesSinceStart(window, time)
        val duration = window.durationMinutes
        if (sinceStart >= duration) return 0f
        val edge = minOf(sinceStart, duration - sinceStart)
        return (edge / PRESENCE_RAMP).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * The ordinal the list centres on at [time]: the window in progress that started last, or else
     * the next one to start.
     */
    fun focusAt(time: Double): Int {
        val day = floor(time / MINUTES_PER_DAY).toInt()
        var active = Int.MIN_VALUE
        var activeStart = Double.NEGATIVE_INFINITY
        var next = Int.MIN_VALUE
        var nextStart = Double.POSITIVE_INFINITY
        for (d in day - 1..day + 1) {
            windows.forEachIndexed { index, window ->
                val start = d.toDouble() * MINUTES_PER_DAY + window.startMinute
                val ordinal = d * size + index
                if (time >= start && time < start + window.durationMinutes && start > activeStart) {
                    active = ordinal
                    activeStart = start
                } else if (start > time && start < nextStart) {
                    next = ordinal
                    nextStart = start
                }
            }
        }
        return if (active != Int.MIN_VALUE) active else next
    }

    fun windowAt(ordinal: Int): RoutineWindow = windows[ordinal.mod(size)]

    private fun minutesSinceStart(window: RoutineWindow, time: Double): Double =
        (time - window.startMinute).mod(MINUTES_PER_DAY.toDouble())

    companion object {
        /**
         * After the source: morning light, a caffeine cut-off, a last meal, winding down, sleep.
         */
        val Default =
            Routine(
                listOf(
                    RoutineWindow(
                        RoutineKind.Light,
                        "Get morning light",
                        7 * 60,
                        9 * 60,
                        Lane.Inner,
                    ),
                    RoutineWindow(
                        RoutineKind.Caffeine,
                        "Caffeine window",
                        8 * 60 + 30,
                        14 * 60,
                        Lane.Outer,
                        detail = "Last caffeine by 2:00 PM",
                    ),
                    RoutineWindow(
                        RoutineKind.Meal,
                        "Finish final meal",
                        18 * 60 + 30,
                        20 * 60 + 30,
                        Lane.Outer,
                    ),
                    RoutineWindow(RoutineKind.WindDown, "Wind down", 22 * 60, 23 * 60, Lane.Inner),
                    RoutineWindow(RoutineKind.Sleep, "Sleep window", 23 * 60, 7 * 60, Lane.Outer),
                )
            )
    }
}
