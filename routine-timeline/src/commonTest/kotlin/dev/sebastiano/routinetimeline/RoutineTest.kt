package dev.sebastiano.routinetimeline

import kotlin.test.Test
import kotlin.test.assertEquals

class RoutineTest {
    private val light = RoutineWindow(RoutineKind.Light, "Light", hm(7, 0), hm(9, 0), Lane.Inner)
    private val coffee =
        RoutineWindow(RoutineKind.Caffeine, "Coffee", hm(8, 30), hm(14, 0), Lane.Outer)
    private val meal = RoutineWindow(RoutineKind.Meal, "Meal", hm(18, 30), hm(20, 30), Lane.Outer)
    private val sleep = RoutineWindow(RoutineKind.Sleep, "Sleep", hm(23, 0), hm(7, 0), Lane.Outer)

    // Given out of order on purpose: the routine sorts by start.
    private val routine = Routine(listOf(sleep, meal, light, coffee))

    @Test
    fun `windows run in the order they start in the day`() {
        assertEquals(listOf(light, coffee, meal, sleep), routine.windows)
    }

    @Test
    fun `a window is in progress from its start up to its end`() {
        assertEquals(WindowStatus.InWindow, routine.statusOf(light, hm(7, 0).toDouble()))
        assertEquals(WindowStatus.InWindow, routine.statusOf(light, hm(8, 59).toDouble()))
        assertEquals(WindowStatus.Earlier, routine.statusOf(light, hm(9, 0).toDouble()))
    }

    @Test
    fun `a window that crosses midnight is in progress on both sides of it`() {
        assertEquals(WindowStatus.InWindow, routine.statusOf(sleep, hm(23, 30).toDouble()))
        assertEquals(WindowStatus.InWindow, routine.statusOf(sleep, hm(2, 0).toDouble()))
        assertEquals(
            WindowStatus.InWindow,
            routine.statusOf(sleep, hm(2, 0).toDouble() + MINUTES_PER_DAY * 4),
        )
    }

    @Test
    fun `outside a window it is earlier or later, whichever edge is closer`() {
        // The meal ended an hour ago and comes round again in twenty-one hours.
        assertEquals(WindowStatus.Earlier, routine.statusOf(meal, hm(21, 30).toDouble()))
        // Light ended fourteen and a half hours ago and starts in seven and a half.
        assertEquals(WindowStatus.Later, routine.statusOf(light, hm(23, 30).toDouble()))
        assertEquals(WindowStatus.Later, routine.statusOf(sleep, hm(22, 0).toDouble()))
    }

    @Test
    fun `the focus is the window in progress`() {
        assertEquals(1, routine.focusAt(hm(12, 0).toDouble()))
        assertEquals(coffee, routine.windowAt(1))
    }

    @Test
    fun `when two windows overlap the one that started last has the focus`() {
        assertEquals(coffee, routine.windowAt(routine.focusAt(hm(8, 45).toDouble())))
    }

    @Test
    fun `between windows the focus is the next one to start`() {
        assertEquals(meal, routine.windowAt(routine.focusAt(hm(15, 0).toDouble())))
    }

    @Test
    fun `the focus keeps counting through the days, so the list scrolls on`() {
        val noon = hm(12, 0).toDouble()
        assertEquals(1, routine.focusAt(noon))
        assertEquals(1 + 4, routine.focusAt(noon + MINUTES_PER_DAY))
        assertEquals(1 - 4, routine.focusAt(noon - MINUTES_PER_DAY))
        assertEquals(sleep, routine.windowAt(-1))
    }

    @Test
    fun `last night's sleep keeps its place in the order until it ends`() {
        // At 2 AM on day one the sleep in progress began on day zero.
        assertEquals(3, routine.focusAt(hm(2, 0).toDouble() + MINUTES_PER_DAY))
        assertEquals(4, routine.focusAt(hm(7, 0).toDouble() + MINUTES_PER_DAY))
    }

    @Test
    fun `presence ramps in after a window starts and out before it ends`() {
        assertEquals(0f, routine.presenceOf(meal, hm(18, 0).toDouble()))
        assertEquals(0.5f, routine.presenceOf(meal, hm(18, 30).toDouble() + PRESENCE_RAMP / 2))
        assertEquals(1f, routine.presenceOf(meal, hm(19, 30).toDouble()))
        assertEquals(0.5f, routine.presenceOf(meal, hm(20, 30).toDouble() - PRESENCE_RAMP / 2))
        assertEquals(0f, routine.presenceOf(meal, hm(21, 0).toDouble()))
    }

    private fun hm(hours: Int, minutes: Int): Int = hours * 60 + minutes
}
