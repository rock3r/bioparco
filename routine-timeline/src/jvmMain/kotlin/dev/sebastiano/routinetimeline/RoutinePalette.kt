package dev.sebastiano.routinetimeline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Sampled from the source video: a near-black card, amber days and violet nights. */
internal object RoutinePalette {
    val window = Color(0xFF060608)
    val card = Color(0xFF141416)
    val cardEdge = Color(0xFF26262B)
    val raised = Color(0xFF1D1D21)
    val track = Color(0xFF1F1F23)
    val header = Color(0xFF9D9DA4)
    val hourMark = Color(0xFF8B8B93)
    val title = Color(0xFFF3F3F5)
    val subtitle = Color(0xFF85858D)
    val clock = Color(0xFF7D7AFF)
    val meridiem = Color(0xFFB4B4BC)
    val nowFill = Color(0xFF1A1A2A)
    val nowInk = Color(0xFF7D7AFF)
    val star = Color(0xFFB9B9D2)
    val sun = Color(0xFFF7A21B)
    val moon = Color(0xFF6F6BFF)
    private val dayArc = Color(0xFFF29A1F)
    private val nightArc = Color(0xFF5C57EE)

    fun kind(kind: RoutineKind): Color =
        when (kind) {
            RoutineKind.Light -> Color(0xFFF7A21B)
            RoutineKind.Caffeine -> Color(0xFFC9825C)
            RoutineKind.Meal -> Color(0xFFB9A8E6)
            RoutineKind.WindDown -> Color(0xFF7A76F6)
            RoutineKind.Sleep -> Color(0xFF605CF7)
        }

    /** A window that is not in progress: its colour sunk most of the way into the track. */
    fun muted(kind: RoutineKind): Color = lerp(kind(kind), track, MUTED)

    /** The outer arc's colour at a time of day. */
    fun sky(minuteOfDay: Double): Color = lerp(nightArc, dayArc, daylight(minuteOfDay))

    private const val MUTED = 0.6f
}

private const val DAWN_START = 5.5 * 60
private const val DAWN_END = 7.5 * 60
private const val DUSK_START = 17.5 * 60
private const val DUSK_END = 19.5 * 60

/** 1 in the day, 0 at night, with two-hour smooth ramps at dawn and dusk. */
internal fun daylight(minuteOfDay: Double): Float {
    val m = minuteOfDay.mod(MINUTES_PER_DAY.toDouble())
    val t =
        when {
            m < DAWN_START || m >= DUSK_END -> 0.0
            m < DAWN_END -> (m - DAWN_START) / (DAWN_END - DAWN_START)
            m < DUSK_START -> 1.0
            else -> 1.0 - (m - DUSK_START) / (DUSK_END - DUSK_START)
        }
    return (t * t * (3 - 2 * t)).toFloat()
}
