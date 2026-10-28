package dev.sebastiano.emailinvite

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.sebastiano.bioparco.tracing.Tracing
import java.util.concurrent.Executor
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * Plays the whole flow once in an offscreen scene: open, type, send, the new guest's arrival, then
 * open and cancel. Cold, Compose's own code for the field, the buttons and the blur layers cost the
 * first real open two frames; afterwards the JIT has compiled it all, so the window runs warm.
 *
 * It runs on the UI thread, a frame at a time with gaps between. On a thread of its own it raced
 * the window's first frames, which then drew some layers without their parameters.
 */
internal suspend fun rehearse() {
    playInvite(EventThread, gapMillis = REHEARSAL_GAP_MILLIS) { _, render ->
        Tracing.section("rehearsal frame") { render() }
    }
}

/**
 * The AWT event thread, where the window composes and draws. Not `Dispatchers.Main`: a standalone
 * specimen has no Swing coroutines module to provide one.
 */
private val EventThread = Executor { SwingUtilities.invokeLater(it) }.asCoroutineDispatcher()

/**
 * Drives the pill through [playInvite]'s scenario a 60 Hz frame at a time. [frame] gets each
 * frame's scenario name and a function that renders it; [gapMillis] of wall-clock time pass between
 * frames.
 *
 * Everything runs on [dispatcher], which must be a single thread: the pill's holds (Sent, the
 * close) wait on the wall clock, and their resumptions must queue behind `render` instead of racing
 * it.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal suspend fun playInvite(
    dispatcher: CoroutineDispatcher,
    width: Int = 800,
    height: Int = 600,
    density: Density = Density(1f),
    gapMillis: Long,
    frame: (scenario: String, render: () -> Unit) -> Unit,
) {
    withContext(dispatcher) {
        var motion: InviteMotion? = null
        val scene =
            ImageComposeScene(width, height, density, coroutineContext = dispatcher) {
                IntUiTheme(isDark = false) {
                    val scope = rememberCoroutineScope()
                    val pill = remember(scope) { InviteMotion(scope) }
                    motion = pill
                    LaunchedEffect(pill) { pill.run() }
                    InviteStage(pill)
                }
            }
        var nanos = 0L
        suspend fun frames(count: Int, scenario: String) =
            repeat(count) {
                nanos += FRAME_NANOS
                frame(scenario) { scene.render(nanos) }
                delay(gapMillis)
            }
        suspend fun framesUntil(scenario: String, done: () -> Boolean) {
            var left = MAX_FRAMES
            while (!done() && left-- > 0) frames(1, scenario)
        }
        try {
            frames(IDLE_FRAMES, "idle")
            val pill = checkNotNull(motion)
            pill.open()
            frames(OPEN_FRAMES, "opening")
            typeInto(pill.address, "new@gmail.com") { frames(2, "typing") }
            pill.submit()
            framesUntil("sending and closing") {
                pill.phase == InvitePhase.Collapsed && pill.shine.value >= 1f
            }
            pill.open()
            frames(OPEN_FRAMES, "opening")
            typeInto(pill.address, "oops") { frames(2, "typing") }
            pill.cancel()
            framesUntil("cancelling") { pill.open.value == 0f && pill.dissolve.value == 0f }
        } finally {
            scene.close()
        }
    }
}

private suspend fun typeInto(field: TextFieldState, text: String, afterKey: suspend () -> Unit) {
    for (end in 1..text.length) {
        field.setTextAndPlaceCursorAtEnd(text.substring(0, end))
        afterKey()
    }
}

private const val FRAME_NANOS = 16_666_667L
private const val IDLE_FRAMES = 10
private const val OPEN_FRAMES = 36
private const val MAX_FRAMES = 2_000
private const val REHEARSAL_GAP_MILLIS = 12L
