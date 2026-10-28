package dev.sebastiano.currencyswarm

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.sebastiano.bioparco.tracing.Tracing
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap

/**
 * Draws the dots: one Skia `drawPoints` call per brightness tier, from float arrays filled in
 * place. No `Offset` lists, no per-dot draw calls, nothing allocated per frame.
 *
 * Dots under [ROUND_FROM_PX] across are drawn square. Anti-aliased, a square that small looks the
 * same as a disc, and Skia draws it as a rectangle instead of a little path: in software, 2,600
 * dots cost 0.4 ms instead of 3.5 ms. Bigger dots, on a HiDPI screen, stay round.
 */
@Composable
internal fun SwarmCanvas(state: SwarmStageState, modifier: Modifier = Modifier) {
    val dots = remember(state) { DotBuffers(state.swarm) }
    Canvas(modifier) {
        // The frame is read here, in the draw phase, so each step redraws without recomposing.
        state.frame.longValue
        Tracing.section("swarm draw") { dots.draw(this, state) }
    }
}

private class DotBuffers(private val swarm: Swarm) {
    private val coordinates = Array(Swarm.TIERS) { FloatArray(swarm.tierSizes[it] * 2) }
    private val cursors = IntArray(Swarm.TIERS)
    private val paints =
        Array(Swarm.TIERS) { tier ->
            Paint().apply {
                isAntiAlias = true
                mode = PaintMode.STROKE
                color = SwarmPalette.dotArgb[tier]
            }
        }

    fun draw(scope: DrawScope, state: SwarmStageState) {
        val scale = state.scale
        val cx = state.centre.x
        val cy = state.centre.y
        cursors.fill(0)
        for (i in 0 until swarm.count) {
            val tier = swarm.tier[i]
            val at = cursors[tier]
            val buffer = coordinates[tier]
            buffer[at] = cx + swarm.x[i] * scale
            buffer[at + 1] = cy + swarm.y[i] * scale
            cursors[tier] = at + 2
        }
        scope.drawIntoCanvas { canvas ->
            for (tier in 0 until Swarm.TIERS) {
                val paint = paints[tier]
                val diameter = SwarmPalette.dotDiameterDp[tier] * scope.density
                paint.strokeWidth = diameter
                paint.strokeCap =
                    if (diameter < ROUND_FROM_PX) PaintStrokeCap.SQUARE else PaintStrokeCap.ROUND
                canvas.nativeCanvas.drawPoints(coordinates[tier], paint)
            }
        }
    }
}

private const val ROUND_FROM_PX = 2.5f
