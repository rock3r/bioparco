package dev.sebastiano.expandingsearch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything that moves. Values are read while drawing and placing, never in composition, so a
 * frame of motion redraws without recomposing. Only [isOpen], the row list and a row's text are
 * composition state, and they change on clicks and keystrokes, not every frame.
 */
internal class SearchMotion(private val scope: CoroutineScope) {
    /** 0 is the round button, 1 the full field. */
    val expansion = Animatable(0f)

    /** 0 is no panel, 1 the panel at its rows' height. Between, it grows out of a thin bar. */
    val reveal = Animatable(0f)

    /** The panel's height in rows, springing as the results change. */
    val panelRows = Animatable(0f)

    /** The highlight's position in rows. */
    val highlightRow = Animatable(0f)

    /** The highlight shows over real results only, not over the "nothing found" line. */
    val highlightAlpha = Animatable(1f)

    var isOpen by mutableStateOf(false)
        private set

    /** The rows on screen, including any still fading out. */
    val rows = mutableStateListOf<RowMotion>()

    /** How many rows can be highlighted and picked. */
    var selectable = 0
        private set

    private var cursor by mutableIntStateOf(0)
    private var hovered by mutableStateOf<Int?>(null)
    private var sequence: Job? = null
    private var opening = false

    /**
     * Whether anything is still moving, so the next frame is due within one frame. Only read for
     * the trace: a frame that draws while this is true marks itself `moving`.
     */
    val isMoving: Boolean
        get() =
            expansion.isRunning ||
                reveal.isRunning ||
                panelRows.isRunning ||
                highlightRow.isRunning ||
                highlightAlpha.isRunning ||
                rows.any { it.presence.isRunning || it.y.isRunning }

    /** The row the highlight heads for: the hovered one, else the keyboard's. */
    val highlighted: Int
        get() = hovered ?: cursor

    fun open() {
        if (isOpen) return
        Tracing.section("open") {
            isOpen = true
            opening = true
            sequence?.cancel()
            sequence = scope.launch {
                highlightRow.snapTo(0f)
                launch { expansion.animateTo(1f, PillOpens) }
                // The panel follows the pill, a few frames behind, as in the video.
                delay(PANEL_DELAY_MS)
                reveal.animateTo(1f, PanelOpens)
            }
        }
    }

    fun close() {
        if (!isOpen) return
        Tracing.section("close") {
            isOpen = false
            hovered = null
            sequence?.cancel()
            sequence = scope.launch {
                launch { reveal.animateTo(0f, PanelCloses) }
                // The pill waits for the panel to fold most of the way up, then shrinks.
                snapshotFlow { reveal.value }.first { it < PILL_FOLLOWS_AT }
                expansion.animateTo(0f, PillCloses)
            }
        }
    }

    fun hover(slot: Int?) {
        val next = slot?.takeIf { it < selectable }
        if (next != hovered) Tracing.section("hover") { hovered = next }
    }

    /** Arrow keys take over from the pointer until it moves again. */
    fun step(by: Int) {
        Tracing.section("step") {
            cursor = stepCursor(highlighted, selectable, by)
            hovered = null
        }
    }

    /**
     * Shows [results] as rows, or none while closed. [selectable] is how many of them are real
     * results; the rest is the "nothing found" line.
     */
    fun showRows(results: List<SearchResult>, selectable: Int) {
        Tracing.section("show rows") {
            this.selectable = selectable
            cursor = 0
            if ((hovered ?: 0) >= selectable) hovered = null
            val shown = rows.filterNot { it.leaving }
            val steps =
                planRows(
                    shown = shown.map { it.key to it.slot },
                    leaving = rows.filter { it.leaving }.map { it.key to it.slot },
                    next = results.map { it.name },
                )
            val byName = results.associateBy { it.name }
            for (step in steps) {
                when (step) {
                    is RowStep.Keep -> stay(shown.first { it.key == step.key }, byName)
                    is RowStep.Revive ->
                        stay(
                            rows.first { it.leaving && it.key == step.key && it.slot == step.slot },
                            byName,
                        )
                    is RowStep.Leave -> leave(shown.first { it.key == step.key })
                    is RowStep.Enter -> enter(byName.getValue(step.key), step.slot, step.from)
                }
            }
            if (results.isNotEmpty()) {
                val height = results.size.toFloat()
                val alpha = if (selectable > 0) 1f else 0f
                // Opening, the panel grows straight to its rows' height; afterwards it springs.
                val fresh = opening
                scope.launch {
                    if (fresh) panelRows.snapTo(height) else panelRows.animateTo(height, RowsResize)
                }
                scope.launch {
                    if (fresh) {
                        // Opening, the highlight comes in with the first row, not before it.
                        highlightAlpha.snapTo(0f)
                        delay(PANEL_DELAY_MS + ROW_LEAD_MS)
                        highlightAlpha.animateTo(alpha, RowFadesIn)
                    } else {
                        highlightAlpha.animateTo(alpha, HighlightFades)
                    }
                }
            }
            opening = false
        }
    }

    /**
     * Runs [results] through the real row changes once while the field is closed: they enter, fade
     * in, then leave. Closed, the panel is a 4dp bar and every row starts 5dp down it, so the
     * panel's clip hides them all. Cold, the first open spent 7.5 ms on its row changes and drew a
     * 22 ms frame; this way the first open finds the code compiled.
     */
    suspend fun warmUp(results: List<SearchResult>) {
        if (isOpen) return
        Tracing.section("warm up") { showRows(results, selectable = results.size) }
        // A spring's first run happens here too, and the rows get its frames to fade in.
        Animatable(0f).animateTo(1f, PillOpens)
        if (!isOpen) showRows(emptyList(), selectable = 0)
    }

    /** Slides the highlight after [highlighted], until the scope ends. */
    suspend fun followHighlight() {
        snapshotFlow { highlighted }
            .collect { target ->
                scope.launch { highlightRow.animateTo(target.toFloat(), HighlightSlides) }
            }
    }

    private fun stay(row: RowMotion, results: Map<String, SearchResult>) {
        row.result = results.getValue(row.key)
        row.leaving = false
        row.job?.cancel()
        row.job = scope.launch {
            launch { row.y.animateTo(row.slot.toFloat(), RowSlides) }
            row.presence.animateTo(1f, RowFadesIn)
        }
    }

    /**
     * Adds a row at [slot]. A row that was at another slot, [from], fades in there instead of
     * sliding across, starting a little towards where it was so it still reads as a move.
     */
    private fun enter(result: SearchResult, slot: Int, from: Int?) {
        val start =
            if (from == null) slot.toFloat() else slot + sign((from - slot).toFloat()) * NUDGE
        val row = RowMotion(result, slot, start)
        rows += row
        // Opening, rows come in one after another once the panel has started to grow.
        val wait = if (opening) PANEL_DELAY_MS + ROW_LEAD_MS + slot * ROW_STAGGER_MS else 0L
        row.job = scope.launch {
            delay(wait)
            launch { row.y.animateTo(slot.toFloat(), RowSlides) }
            row.presence.animateTo(1f, RowFadesIn)
        }
    }

    private fun leave(row: RowMotion) {
        row.leaving = true
        row.job?.cancel()
        row.job = scope.launch {
            row.presence.animateTo(0f, RowFadesOut)
            rows.remove(row)
        }
    }

    private companion object {
        // Each spring is fitted to the 60 fps video, frame by frame, against the motion's real
        // start
        // and end, so its overshoot counts. The pill opens two thirds of the way in 100 ms, runs
        // past its width and eases back over about 200 ms.
        val PillOpens = spring<Float>(dampingRatio = 0.75f, stiffness = 400f)
        val PillCloses = spring<Float>(dampingRatio = 0.88f, stiffness = 660f)
        // The panel grows out of a 4dp bar 60 ms after the pill starts, overshoots by about 4% at
        // 300 ms and settles back.
        val PanelOpens = spring<Float>(dampingRatio = 0.75f, stiffness = 325f)
        // The panel folds at an even speed and is gone in about 120 ms.
        val PanelCloses = tween<Float>(durationMillis = 120, easing = LinearEasing)
        // Typing resizes the panel with the most bounce in the video: about 7% past, then back.
        val RowsResize = spring<Float>(dampingRatio = 0.64f, stiffness = 270f)
        val RowSlides = spring<Float>(dampingRatio = 0.78f, stiffness = 280f)
        // The highlight runs about 2% past each row it moves to.
        val HighlightSlides = spring<Float>(dampingRatio = 0.78f, stiffness = 280f)
        val HighlightFades = tween<Float>(120)
        // In the video the first row shows 85 ms after the pill starts, each next one about 30 ms
        // later, and each is sharp about 160 ms after it shows.
        val RowFadesIn = tween<Float>(durationMillis = 160, easing = LinearOutSlowInEasing)
        val RowFadesOut = tween<Float>(durationMillis = 110, easing = FastOutSlowInEasing)

        const val PANEL_DELAY_MS = 60L
        const val ROW_LEAD_MS = 25L
        const val ROW_STAGGER_MS = 30L
        /** The video's pill starts to shrink 85–100 ms after the click, the panel 70% folded. */
        const val PILL_FOLLOWS_AT = 0.3f

        /** How far towards its old slot, in rows, a moved row starts fading in. */
        const val NUDGE = 0.18f
    }
}

/**
 * One row on screen: the [slot] it belongs in, where it is now in rows, and how present it is, 0
 * gone to 1 sharp.
 */
internal class RowMotion(result: SearchResult, val slot: Int, y: Float) {
    val key = result.name
    var result by mutableStateOf(result)
    val y = Animatable(y)
    val presence = Animatable(0f)
    var leaving = false
    var job: Job? = null
}
