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
    private var highlightJob: Job? = null
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
                rows.any { it.presence.isRunning || it.y.isRunning || it.scale.isRunning }

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
                    is RowStep.Keep -> stay(shown.first { it.key == step.key }, step.slot, byName)
                    is RowStep.Revive ->
                        stay(rows.first { it.leaving && it.key == step.key }, step.slot, byName)
                    is RowStep.Leave -> leave(shown.first { it.key == step.key })
                    is RowStep.Enter -> enter(byName.getValue(step.key), step.slot)
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
                // The opening fade-in waits for the first row; results that change meanwhile
                // (say, a query with no matches) must not have it fade back in after them.
                highlightJob?.cancel()
                highlightJob = scope.launch {
                    if (fresh) {
                        // Opening, the highlight comes in with the first row, not before it.
                        highlightAlpha.snapTo(0f)
                        delay(PANEL_DELAY_MS + ROW_SHARPENS_AFTER_MS)
                        highlightAlpha.animateTo(alpha, RowSharpens)
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

    /** Keeps [row], or brings it back, at [slot]: it slides there and grows back to full size. */
    private fun stay(row: RowMotion, slot: Int, results: Map<String, SearchResult>) {
        row.result = results.getValue(row.key)
        row.slot = slot
        row.leaving = false
        row.job?.cancel()
        row.job = scope.launch {
            launch { row.y.animateTo(slot.toFloat(), RowSlides) }
            launch { row.scale.animateTo(1f, RowGrows) }
            row.presence.animateTo(1f, RowSharpens)
        }
    }

    /**
     * Adds a row at [slot]. It grows from [ENTER_SCALE] on a bouncy spring, past full size and
     * back, and sharpens out of a blur a little behind the growth, as in the video.
     */
    private fun enter(result: SearchResult, slot: Int) {
        val row = RowMotion(result, slot, slot.toFloat())
        rows += row
        // Opening, rows come in one after another as the panel starts to grow.
        val wait = if (opening) PANEL_DELAY_MS + slot * ROW_STAGGER_MS else 0L
        row.job = scope.launch {
            row.scale.snapTo(ENTER_SCALE)
            delay(wait)
            launch { row.scale.animateTo(1f, RowGrows) }
            delay(ROW_SHARPENS_AFTER_MS)
            row.presence.animateTo(1f, RowSharpens)
        }
    }

    /** Blurs [row] out where it is, shrinking a little, then drops it. */
    private fun leave(row: RowMotion) {
        row.leaving = true
        row.job?.cancel()
        row.job = scope.launch {
            launch { row.scale.animateTo(LEAVE_SCALE, RowFadesOut) }
            row.presence.animateTo(0f, RowFadesOut)
            rows.remove(row)
        }
    }

    private companion object {
        // Each spring is fitted to the 60 fps video, frame by frame, against the motion's real
        // start and end, so its overshoot counts. The pill opens two thirds of the way in 100 ms,
        // runs
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
        // Rows that stay slide to their new slot quickly, with next to no overshoot.
        val RowSlides = spring<Float>(dampingRatio = 0.8f, stiffness = 340f)
        // The bounciest spring in the video. A new row's text, measured frame by frame, grows from
        // about 62% to 107% of its size, dips to 99% and settles about 700 ms in.
        val RowGrows = spring<Float>(dampingRatio = 0.45f, stiffness = 165f)
        // The highlight runs about 2% past each row it moves to.
        val HighlightSlides = spring<Float>(dampingRatio = 0.78f, stiffness = 280f)
        val HighlightFades = tween<Float>(120)
        // A new row starts to show 65 ms after it starts growing and is sharp 100 ms later, by
        // when it has nearly reached full size.
        val RowSharpens = tween<Float>(durationMillis = 100, easing = FastOutSlowInEasing)
        // A leaving row is mostly gone after a frame or two: fast first, then easing.
        val RowFadesOut = tween<Float>(durationMillis = 110, easing = LinearOutSlowInEasing)

        const val PANEL_DELAY_MS = 60L
        const val ROW_SHARPENS_AFTER_MS = 65L
        const val ROW_STAGGER_MS = 30L
        /** The video's pill starts to shrink 85–100 ms after the click, the panel 70% folded. */
        const val PILL_FOLLOWS_AT = 0.3f

        /** A new row grows from this size; a leaving one shrinks to [LEAVE_SCALE]. */
        const val ENTER_SCALE = 0.62f
        const val LEAVE_SCALE = 0.85f
    }
}

/**
 * One row on screen: the [slot] it belongs in, where it is now in rows, and how present it is, 0
 * gone to 1 sharp.
 */
internal class RowMotion(result: SearchResult, var slot: Int, y: Float) {
    val key = result.name
    var result by mutableStateOf(result)
    val y = Animatable(y)
    val presence = Animatable(0f)

    /** The row's size, about the panel's middle: 1 is full size. */
    val scale = Animatable(1f)
    var leaving = false
    var job: Job? = null
}
