package dev.sebastiano.thinkingbloom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every blossom pre-drawn: one small image per look, turn and whole-pixel diameter, made the first
 * time a blossom needs it. A frame then copies pixels instead of filling anti-aliased petals, which
 * in software costs about thirty times less, and on a GPU is a few hundred small textured quads.
 */
internal class FlowerSprites {
    private val sprites = HashMap<Int, ImageBitmap>()

    /** A [diameter]-pixel blossom, drawn centred in a square [cell] pixels wide. */
    fun sprite(look: Int, turn: Int, diameter: Int): ImageBitmap =
        sprites.getOrPut(
            (look * TURNS + turn) * MAX_DIAMETER + diameter.coerceAtMost(MAX_DIAMETER - 1)
        ) {
            draw(BloomPalette.looks[look], turn, diameter)
        }

    private fun draw(look: BloomPalette.Look, turn: Int, diameter: Int): ImageBitmap {
        val side = cell(diameter)
        val image = ImageBitmap(side, side)
        val radius = diameter / 2f
        val centre = Offset(side / 2f, side / 2f)
        CanvasDrawScope().draw(
            ONE,
            LayoutDirection.Ltr,
            Canvas(image),
            Size(side.toFloat(), side.toFloat()),
        ) {
            drawPath(petals(centre, radius, turn * PI.toFloat() / (PETALS * TURNS)), look.petals)
            drawCircle(look.centre, radius * CENTRE, centre)
        }
        return image
    }

    private fun petals(centre: Offset, radius: Float, turn: Float): Path =
        Path().apply {
            for (i in 0 until PETALS) {
                val angle = turn + i * 2f * PI.toFloat() / PETALS
                val petal = centre + Offset(cos(angle), sin(angle)) * (PETAL_REACH * radius)
                addOval(Rect(petal, PETAL_RADIUS * radius))
            }
        }

    companion object {
        /** Two turns: the five petals one way up, and turned by half a petal. */
        const val TURNS = 2

        /**
         * The side of the square image a [diameter]-pixel blossom is drawn in, with a pixel spare.
         */
        fun cell(diameter: Int): Int = diameter + 2

        private const val MAX_DIAMETER = 1024
        private const val PETALS = 5
        private const val PETAL_REACH = 0.55f
        private const val PETAL_RADIUS = 0.45f
        private const val CENTRE = 0.3f
        private val ONE = Density(1f)
    }
}
