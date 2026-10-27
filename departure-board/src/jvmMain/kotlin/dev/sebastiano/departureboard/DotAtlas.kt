package dev.sebastiano.departureboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.SamplingMode

/** A cell is five dots wide with one dot of space after it, and seven tall with three below. */
private const val CELL_ADVANCE = DotFont.COLUMNS + 1
private const val ROW_ADVANCE = DotFont.ROWS + 3
private const val BOARD_WIDTH_DOTS = BOARD_COLUMNS * CELL_ADVANCE - 1
private const val BOARD_HEIGHT_DOTS = BOARD_ROWS * ROW_ADVANCE - 3

/** The board takes this much of the stage at most, and sits a little above the middle. */
private const val BOARD_MAX_WIDTH = 0.84f
private const val BOARD_MAX_HEIGHT = 0.6f
private const val BOARD_CENTRE_Y = 0.45f

private const val LIT_RADIUS = 0.42f
private const val DIM_RADIUS = 0.39f
private const val MIN_PITCH = 2
private const val ATLAS_COLUMNS = 8

/**
 * Every glyph drawn once, at one dot pitch, into one image: each tile is a whole cell with its
 * background, its unlit dots and its lit ones. A frame then only copies tiles onto whole pixels at
 * their own size, which is close to the cheapest thing Skia does, on the GPU or in software.
 *
 * The atlas is an immutable Skia [Image], drawn through the Skia canvas. Compose's `drawImage` on a
 * mutable [ImageBitmap] wraps it in a new image, copying its pixels, on every call: a hundred calls
 * a frame made the board section cost 3.4 ms; with the image it costs 0.03 ms (median, Xvfb).
 */
internal class DotAtlas(val pitch: Int) {
    val tile = IntSize(DotFont.COLUMNS * pitch, DotFont.ROWS * pitch)
    private val tiles = HashMap<Char, SkiaRect>()
    val image: Image

    init {
        val characters = DotFont.CHARACTERS
        val rows = (characters.length + ATLAS_COLUMNS - 1) / ATLAS_COLUMNS
        val bitmap = ImageBitmap(ATLAS_COLUMNS * tile.width, rows * tile.height)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }
        characters.forEachIndexed { index, char ->
            val origin =
                IntOffset(index % ATLAS_COLUMNS * tile.width, index / ATLAS_COLUMNS * tile.height)
            tiles[char] =
                SkiaRect.makeXYWH(
                    origin.x.toFloat(),
                    origin.y.toFloat(),
                    tile.width.toFloat(),
                    tile.height.toFloat(),
                )
            paint.color = DepartureBoardPalette.Background
            canvas.drawRect(
                Rect(
                    origin.x.toFloat(),
                    origin.y.toFloat(),
                    (origin.x + tile.width).toFloat(),
                    (origin.y + tile.height).toFloat(),
                ),
                paint,
            )
            drawGlyph(canvas, paint, char, origin)
        }
        image = Image.makeFromBitmap(bitmap.asSkiaBitmap().apply { setImmutable() })
    }

    private fun drawGlyph(canvas: Canvas, paint: Paint, char: Char, origin: IntOffset) {
        for (row in 0 until DotFont.ROWS) {
            for (column in 0 until DotFont.COLUMNS) {
                val lit = DotFont.lit(char, row, column)
                paint.color = if (lit) DepartureBoardPalette.Lit else DepartureBoardPalette.Dim
                canvas.drawCircle(
                    Offset(origin.x + (column + 0.5f) * pitch, origin.y + (row + 0.5f) * pitch),
                    (if (lit) LIT_RADIUS else DIM_RADIUS) * pitch,
                    paint,
                )
            }
        }
    }

    /** Where [char]'s tile sits in [image]. Characters without a glyph get the blank tile. */
    fun tileOf(char: Char): SkiaRect = tiles[char] ?: tiles.getValue(' ')
}

/** Keeps the last atlas, and draws a new one only when the dot pitch changes. */
internal class AtlasCache {
    private var atlas: DotAtlas? = null

    fun atlasFor(pitch: Int): DotAtlas =
        atlas?.takeIf { it.pitch == pitch }
            ?: Tracing.section("board atlas") { DotAtlas(pitch) }.also { atlas = it }
}

/** The largest whole-pixel dot pitch at which the board fits the stage. */
internal fun pitchFor(width: Float, height: Float): Int =
    floor(
            min(
                width * BOARD_MAX_WIDTH / BOARD_WIDTH_DOTS,
                height * BOARD_MAX_HEIGHT / BOARD_HEIGHT_DOTS,
            )
        )
        .toInt()
        .coerceAtLeast(MIN_PITCH)

/** Draws [model] as it is at [nowMs]: one tile copy per cell. The gutter column has no dots. */
internal fun DrawScope.drawBoard(model: BoardModel, nowMs: Long, cache: AtlasCache) {
    val pitch = pitchFor(size.width, size.height)
    val atlas = cache.atlasFor(pitch)
    val left = ((size.width - BOARD_WIDTH_DOTS * pitch) / 2f).roundToInt()
    val top = (size.height * BOARD_CENTRE_Y - BOARD_HEIGHT_DOTS * pitch / 2f).roundToInt()
    val canvas = drawContext.canvas.skiaCanvas
    for (row in 0 until model.rows) {
        val y = (top + row * ROW_ADVANCE * pitch).toFloat()
        for (column in 0 until model.columns) {
            if (column != GUTTER_COLUMN) {
                val x = (left + column * CELL_ADVANCE * pitch).toFloat()
                val target =
                    SkiaRect.makeXYWH(x, y, atlas.tile.width.toFloat(), atlas.tile.height.toFloat())
                canvas.drawImageRect(
                    atlas.image,
                    atlas.tileOf(model.charAt(row, column, nowMs)),
                    target,
                    SamplingMode.DEFAULT,
                    null,
                    true,
                )
            }
        }
    }
}
