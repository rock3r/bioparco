package dev.sebastiano.wheelpicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The selected duration, one character per slot. When a slot's character changes, the old one
 * rises, blurs and fades while the new one comes up from below and sharpens; the slot's width
 * follows, so the whole readout recentres as it rolls. Slots roll a beat apart, left to right.
 *
 * It is one canvas. A new duration recomposes it once, to start the rolls; the rolls themselves
 * only redraw, from images [ReadoutGlyphs] made up front.
 */
@Composable
internal fun WheelReadout(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val glyphs =
        remember(measurer, style, density) {
            ReadoutGlyphs(measurer, style, density, with(density) { MAX_BLUR.toPx() }).apply {
                warm(READOUT_GLYPHS)
            }
        }
    val scope = rememberCoroutineScope()
    val roll = remember { ReadoutRoll(scope, text) }
    LaunchedEffect(text) { roll.show(text) }
    Canvas(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        Tracing.section("readout") { with(roll) { draw(glyphs, ROLL_TRAVEL.toPx()) } }
    }
}

/** Every character a readout can show. */
private const val READOUT_GLYPHS = "0123456789hm ∞"

internal class ReadoutRoll(private val scope: CoroutineScope, text: String) {
    private val slots = readoutSlots(text, READOUT_SLOTS).map(::Slot)

    private class Slot(initial: Char) {
        var incoming by mutableStateOf(initial)
        var outgoing by mutableStateOf(NO_GLYPH)
        val progress = Animatable(1f)
        var job: Job? = null
    }

    fun show(text: String) {
        readoutSlots(text, READOUT_SLOTS).forEachIndexed { index, glyph ->
            val slot = slots[index]
            if (glyph == slot.incoming) return@forEachIndexed
            slot.outgoing = slot.incoming
            slot.incoming = glyph
            slot.job?.cancel()
            slot.job = scope.launch {
                slot.progress.snapTo(0f)
                delay(index * STAGGER_MILLIS)
                slot.progress.animateTo(
                    1f,
                    spring(dampingRatio = ROLL_DAMPING, stiffness = ROLL_STIFFNESS),
                )
            }
        }
    }

    /** Draws the slots centred on the canvas, glyphs standing on a shared baseline. */
    fun DrawScope.draw(glyphs: ReadoutGlyphs, travel: Float) {
        var total = 0f
        for (slot in slots) total += slotWidth(slot, glyphs)
        var x = (size.width - total) / 2f
        val bottom = size.height / 2f + glyphs[READOUT_GLYPHS[0]].height / 2f
        for (slot in slots) {
            val width = slotWidth(slot, glyphs)
            val t = slot.progress.value
            val shown = t.coerceIn(0f, 1f)
            drawGlyph(
                glyphs,
                slot.outgoing,
                x + width / 2f,
                bottom - travel * shown,
                1f - shown,
                shown,
            )
            drawGlyph(
                glyphs,
                slot.incoming,
                x + width / 2f,
                bottom + travel * (1f - t),
                shown,
                1f - shown,
            )
            x += width
        }
    }

    private fun slotWidth(slot: Slot, glyphs: ReadoutGlyphs): Float {
        val from = widthOf(slot.outgoing, glyphs)
        val to = widthOf(slot.incoming, glyphs)
        return from + (to - from) * slot.progress.value.coerceIn(0f, 1f)
    }

    private fun widthOf(glyph: Char, glyphs: ReadoutGlyphs): Float =
        if (glyph == NO_GLYPH) 0f else glyphs[glyph].width.toFloat()

    /**
     * One glyph centred on [centreX], standing on [bottom], blurred by [blur] (0 sharp, 1 the
     * most). Between two blur steps it cross-fades them. Whole pixels keep each image a copy.
     */
    private fun DrawScope.drawGlyph(
        glyphs: ReadoutGlyphs,
        glyph: Char,
        centreX: Float,
        bottom: Float,
        alpha: Float,
        blur: Float,
    ) {
        if (glyph == NO_GLYPH || alpha < MIN_ALPHA) return
        val image = glyphs[glyph]
        val topLeft =
            Offset(
                ((centreX - image.width / 2f).roundToInt() - glyphs.padding).toFloat(),
                ((bottom - image.height).roundToInt() - glyphs.padding).toFloat(),
            )
        val level = blur.coerceIn(0f, 1f) * ReadoutGlyphs.LEVELS
        val low = floor(level).toInt().coerceAtMost(ReadoutGlyphs.LEVELS - 1)
        val mix = level - low
        drawImage(image.steps[low], topLeft, alpha = alpha * (1f - mix))
        if (mix > MIN_ALPHA) drawImage(image.steps[low + 1], topLeft, alpha = alpha * mix)
    }

    private companion object {
        const val MIN_ALPHA = 0.004f
        const val STAGGER_MILLIS = 28L
        const val ROLL_DAMPING = 0.78f
        const val ROLL_STIFFNESS = 320f
    }
}

private val ROLL_TRAVEL = 22.dp
private val MAX_BLUR = 9.dp
