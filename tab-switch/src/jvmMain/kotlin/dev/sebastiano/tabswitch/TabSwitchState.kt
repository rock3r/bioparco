package dev.sebastiano.tabswitch

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.channels.Channel

/**
 * Runs [TabMotion] on the frame clock and hands its results to Compose in two tiers:
 * - [tick] changes every moving frame. Only the bar's drawing reads it, so a moving frame redraws
 *   the bar without recomposing or laying out anything.
 * - [selected] changes once per switch, and moves the tabs' semantics nodes.
 */
@Stable
internal class TabSwitchState(val motion: TabMotion) {
    val tick = mutableLongStateOf(0L)

    var selected by mutableIntStateOf(motion.selected)
        private set

    private var requested: Int? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Steps the motion every frame while anything moves, then sleeps until poked. */
    suspend fun run() {
        while (true) {
            var moving = true
            while (moving) {
                withFrameNanos { now ->
                    Tracing.section("tab step") {
                        requested?.let {
                            motion.select(it, now)
                            requested = null
                        }
                        moving = motion.step(now)
                    }
                    tick.longValue = now
                }
            }
            wake.receive()
        }
    }

    fun select(tab: Int) {
        if (tab == selected || tab !in 0 until motion.layout.count) return
        selected = tab
        requested = tab
        poke()
    }

    /** Steps the selection by [direction] tabs, stopping at either end. */
    fun step(direction: Int) = select((selected + direction).coerceIn(0, motion.layout.count - 1))

    fun hover(tab: Int?) {
        if (tab == motion.hovered) return
        motion.hover(tab)
        poke()
    }

    /**
     * Runs a throwaway bar through a switch to every tab and back, so the JIT has compiled the
     * springs before the first real click. Call it off the main thread: it takes a few ms.
     */
    fun warmUp() {
        Tracing.section("tab warm-up") {
            val rehearsal = TabMotion(motion.layout, minPillWidth = 1f)
            var now = 0L
            repeat(WARM_UP_ROUNDS) {
                for (tab in (1 until motion.layout.count) + 0) {
                    rehearsal.hover(tab)
                    rehearsal.select(tab, now)
                    repeat(WARM_UP_FRAMES) {
                        now += FRAME_NANOS
                        rehearsal.step(now)
                    }
                }
            }
        }
    }

    private fun poke() {
        wake.trySend(Unit)
    }

    private companion object {
        const val WARM_UP_ROUNDS = 20
        const val WARM_UP_FRAMES = 40
        const val FRAME_NANOS = 16_666_667L
    }
}
