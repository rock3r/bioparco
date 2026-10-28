package dev.sebastiano.layoutmorph

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.channels.Channel

/**
 * Runs [MorphEngine] on the frame clock and hands its results to Compose in two tiers, so each
 * frame does as little as it can:
 * - [tick] changes every moving frame. Only the card field's drawing reads it.
 * - [arrangement] and [detail] change on a click or a hover, and recompose the text around the
 *   cards.
 */
@Stable
internal class MorphMotion(val pieces: List<Piece>) {
    val engine =
        MorphEngine(
            pieces,
            Field(DEFAULT_WIDTH, DEFAULT_HEIGHT),
            Arrangement.Grid,
            initialFocus = 2,
        )

    val tick = mutableLongStateOf(0L)

    /** The arrangement picked, which the switcher and hint show. */
    var arrangement by mutableStateOf(Arrangement.Grid)
        private set

    /** The card whose details the footer shows: the hovered card, or the carousel's. */
    var detail by mutableStateOf<Int?>(null)
        private set

    private var requested: Arrangement? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Steps the engine every frame while anything moves, then sleeps until poked. */
    suspend fun run() {
        while (true) {
            var moving = true
            engine.resume()
            while (moving) {
                withFrameNanos { now ->
                    Tracing.section("morph step") {
                        requested?.let {
                            engine.select(it, now)
                            requested = null
                        }
                        moving = engine.step(now)
                        publish()
                    }
                    tick.longValue = now
                }
            }
            wake.receive()
        }
    }

    fun select(next: Arrangement) {
        if (next == arrangement) return
        arrangement = next
        requested = next
        poke()
    }

    fun resize(field: Field) {
        if (field == engine.field) return
        engine.field = field
        poke()
    }

    /** The pointer is at ([x], [y]) dp in the field, or has left it. */
    fun pointerAt(x: Float?, y: Float?) {
        val before = engine.hovered
        engine.point(x, y)
        if (engine.hovered != before) poke()
    }

    /** A click at ([x], [y]) dp: opens the card in the carousel, or centres it there. */
    fun click(x: Float, y: Float) {
        val index = hitTest(engine.poses, x, y) ?: return
        engine.focus(index)
        select(Arrangement.Carousel)
        poke()
    }

    fun canDrag(): Boolean = arrangement == Arrangement.Carousel || arrangement == Arrangement.Helix

    fun drag(dx: Float) {
        engine.drag(dx)
        poke()
    }

    fun release(velocity: Float) {
        engine.release(velocity)
        poke()
    }

    /** A wheel notch or arrow key: the carousel steps a card, the helix gets a nudge. */
    fun nudge(direction: Int) {
        when (arrangement) {
            Arrangement.Carousel -> engine.stepFocus(direction)
            Arrangement.Helix -> {
                engine.drag(-direction * NUDGE_DP)
                engine.release(-direction * NUDGE_SPEED)
            }
            else -> return
        }
        poke()
    }

    private fun publish() {
        detail = engine.hovered ?: if (arrangement == Arrangement.Carousel) engine.focused else null
    }

    private fun poke() {
        wake.trySend(Unit)
    }

    private companion object {
        const val DEFAULT_WIDTH = 896f
        const val DEFAULT_HEIGHT = 410f
        const val NUDGE_DP = 12f
        const val NUDGE_SPEED = 600f
    }
}
