package dev.sebastiano.layoutmorph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** The six ways to browse the collection. */
enum class Arrangement(val label: String, val hint: String) {
    Grid("Grid", "Click a card to open it"),
    Masonry("Masonry", "Every piece keeps its own proportions"),
    List("List", "Each piece with its maker and price"),
    Stack("Stack", "Hover the fan to pull a card up"),
    Carousel("Carousel", "Drag or scroll, it settles on the nearest card"),
    Helix("Helix", "Drag to spin, throw it to keep it turning"),
}

/**
 * Where each arrangement puts every card, for a [Field] of a given size. Every function returns one
 * pose per card, in the collection's order. `lift` is how far (0 to 1) the pointer has raised each
 * card.
 */
object Arrangements {
    /** Grid tiles: a square picture over a caption strip. */
    const val TILE_TALL = 1.36f

    private const val COLUMNS = 8
    private const val GRID_ROWS = 3
    private const val GAP = 10f

    /** How much a hovered flat card grows. */
    private const val HOVER_GROWTH = 0.05f

    /** Eight across, three down, row by row. */
    fun grid(count: Int, field: Field, lift: (Int) -> Float = { 0f }): List<CardPose> {
        val rows = (count + COLUMNS - 1) / COLUMNS
        val width =
            min(
                (field.width - GAP * (COLUMNS - 1)) / COLUMNS,
                (field.height - GAP * (rows - 1)) / rows / TILE_TALL,
            )
        val height = width * TILE_TALL
        val left = (field.width - (width * COLUMNS + GAP * (COLUMNS - 1))) / 2
        val top = (field.height - (height * rows + GAP * (rows - 1))) / 2
        return List(count) { i ->
            CardPose(
                x = left + (i % COLUMNS) * (width + GAP) + width / 2,
                y = top + (i / COLUMNS) * (height + GAP) + height / 2,
                width = width,
                height = height,
                scale = 1f + HOVER_GROWTH * lift(i),
                caption = 1f,
            )
        }
    }

    /**
     * Eight equal columns. Each card, in order, drops into the shortest column at its own [tall]
     * height over width. The columns shrink together until the tallest fits the field.
     */
    fun masonry(tall: List<Float>, field: Field, lift: (Int) -> Float = { 0f }): List<CardPose> {
        val gap = GAP * 0.8f
        var width = (field.width - gap * (COLUMNS - 1)) / COLUMNS
        var placed = stackColumns(tall, width, gap)
        // Gaps don't shrink with the cards, so one rescale can leave a sliver over; two land it.
        repeat(2) {
            val height = placed.maxOf { it.second + it.third }
            if (height > field.height) {
                width *= (field.height - gap * 2) / (height - gap * 2)
                placed = stackColumns(tall, width, gap)
            }
        }
        val blockWidth = width * COLUMNS + gap * (COLUMNS - 1)
        val blockHeight = placed.maxOf { it.second + it.third }
        val left = (field.width - blockWidth) / 2
        val top = (field.height - blockHeight) / 2
        return placed.mapIndexed { i, (column, y, height) ->
            CardPose(
                x = left + column * (width + gap) + width / 2,
                y = top + y + height / 2,
                width = width,
                height = height,
                scale = 1f + HOVER_GROWTH * lift(i),
            )
        }
    }

    /** For each card: its column, its top, and its height. */
    private fun stackColumns(tall: List<Float>, width: Float, gap: Float) = run {
        val bottoms = FloatArray(COLUMNS) { -gap }
        tall.map { ratio ->
            var column = 0
            for (c in 1 until COLUMNS) if (bottoms[c] < bottoms[column] - 0.5f) column = c
            val top = bottoms[column] + gap
            val height = width * ratio
            bottoms[column] = top + height
            Triple(column, top, height)
        }
    }

    private const val LIST_ROWS = 8
    private const val LIST_GAP_X = 14f
    private const val LIST_GAP_Y = 6f
    private const val LIST_ROW_MAX = 36f
    private const val LIST_COLUMN_MAX = 300f

    /** Rows of thumbnail, name, detail and price: three columns, eight down, column by column. */
    fun list(count: Int, field: Field, lift: (Int) -> Float = { 0f }): List<CardPose> {
        val columns = (count + LIST_ROWS - 1) / LIST_ROWS
        val height = min(LIST_ROW_MAX, (field.height - LIST_GAP_Y * (LIST_ROWS - 1)) / LIST_ROWS)
        val width = min(LIST_COLUMN_MAX, (field.width - LIST_GAP_X * (columns - 1)) / columns)
        val left = (field.width - (width * columns + LIST_GAP_X * (columns - 1))) / 2
        val top = (field.height - (height * LIST_ROWS + LIST_GAP_Y * (LIST_ROWS - 1))) / 2
        return List(count) { i ->
            CardPose(
                x = left + (i / LIST_ROWS) * (width + LIST_GAP_X) + width / 2,
                y = top + (i % LIST_ROWS) * (height + LIST_GAP_Y) + height / 2,
                width = width,
                height = height,
                scale = 1f + HOVER_GROWTH * 0.4f * lift(i),
                row = 1f,
            )
        }
    }

    /** Half the fan's spread, in degrees. */
    private const val FAN_HALF_SPREAD = 38f
    private const val FAN_CARD_TALL = 1.3f

    /** How far a hovered fan card comes out, as a share of its height. */
    private const val FAN_PULL = 0.24f

    /**
     * A hand of cards fanned on an arc, each turned along the arc and laid over the one before.
     * Lifting a card slides it out along its own tilt.
     */
    fun stack(count: Int, field: Field, lift: (Int) -> Float = { 0f }): List<CardPose> {
        val width = min(field.width * 0.16f, field.height * 0.56f / FAN_CARD_TALL)
        val height = width * FAN_CARD_TALL
        val half = FAN_HALF_SPREAD.toRadians()
        val radius = (field.width * 0.84f - width * 1.1f) / (2 * sin(half))
        // Centre the fan on the field: from the middle card's top edge down to the lowest corner
        // of the end cards, which lean out by the half spread.
        val top = radius + height / 2
        val bottom = radius * cos(half) - height / 2 * cos(half) - width / 2 * sin(half)
        val pivotY = field.height / 2 + (top + bottom) / 2
        val centreX = field.width / 2
        return List(count) { i ->
            val t = if (count == 1) 0.5f else i / (count - 1f)
            val angle = -FAN_HALF_SPREAD + 2 * FAN_HALF_SPREAD * t
            val a = angle.toRadians()
            val reach = radius + FAN_PULL * height * lift(i)
            CardPose(
                x = centreX + reach * sin(a),
                y = pivotY - reach * cos(a),
                width = width,
                height = height,
                rotation = angle,
                depth = i.toFloat(),
            )
        }
    }

    private const val CAROUSEL_GAP = 20f

    private fun carouselWidth(field: Field) =
        min(field.width * 0.25f, field.height * 0.9f / TILE_TALL)

    /** The distance between neighbouring carousel cards, in dp. */
    fun carouselPitch(field: Field): Float = carouselWidth(field) + CAROUSEL_GAP

    /** Big captioned cards in a row, card [position] (fractional while moving) in the middle. */
    fun carousel(count: Int, field: Field, position: Float): List<CardPose> {
        val width = carouselWidth(field)
        val pitch = width + CAROUSEL_GAP
        return List(count) { i ->
            val from = i - position
            CardPose(
                x = field.width / 2 + from * pitch,
                y = field.height / 2,
                width = width,
                height = width * TILE_TALL,
                caption = 1f,
                depth = -kotlin.math.abs(from),
            )
        }
    }

    /** How many times card [index] has wrapped round the helix's length at [spin]. */
    fun helixLap(index: Int, count: Int, spin: Float): Int =
        floor((index + spin / STEP) / count).toInt()

    /** Cards per turn of the helix. */
    private const val PER_TURN = 7
    private const val STEP = 360f / PER_TURN
    private const val HELIX_CARD_TALL = 1.3f

    /**
     * Cards on a slowly rising spiral round an upright cylinder, each facing out, the middle card
     * in front. [spin] turns the cylinder, in degrees, which also screws the spiral along: cards
     * fade out towards the field's top and bottom edges, and wrap round from one end of the spiral
     * to the other once they are gone. [laps] pins how many times each card has wrapped, so a card
     * still flying in keeps a target that runs on past the end rather than one that jumps back.
     */
    fun helix(count: Int, field: Field, spin: Float, laps: IntArray? = null): List<CardPose> {
        val width = min(field.width * 0.13f, field.height * 0.25f)
        val height = width * HELIX_CARD_TALL
        val radius = width * 1.9f
        // The spiral runs a card's height past the field at each end, where its cards fade out.
        val pitch = (field.height + height) / count
        return List(count) { i ->
            // Where along the spiral the card is, 0 at the top end and count at the bottom.
            val along =
                laps?.let { i + spin / STEP - it[i] * count }
                    ?: wrap(i + spin / STEP, count.toFloat())
            val angle = (along - count / 2f) * STEP
            val rad = angle.toRadians()
            val near = cos(rad)
            val y = field.height / 2 + (along - count / 2f) * pitch
            // Gone by the time a card is mostly over an edge; the spiral's ends lie beyond that.
            val edges =
                smoothstep(-height * EDGE_GONE, height * EDGE_SOLID, y) *
                    smoothstep(-height * EDGE_GONE, height * EDGE_SOLID, field.height - y)
            CardPose(
                x = field.width / 2 + radius * sin(rad),
                y = y,
                width = width,
                height = height,
                turn = wrapDegrees(angle),
                scale = 1f + 0.12f * near,
                alpha = (0.22f + 0.78f * smoothstep(-0.25f, 0.45f, near)) * edges,
                depth = near,
            )
        }
    }

    /** A helix card fades out from this far inside the field's edge, in card heights... */
    private const val EDGE_SOLID = 0.5f

    /** ...to gone this far past it. */
    private const val EDGE_GONE = 0.3f
}

internal fun Float.toRadians(): Float = this * (PI.toFloat() / 180f)

/** [value] wrapped into `[0, length)`. */
internal fun wrap(value: Float, length: Float): Float = value - floor(value / length) * length

/** [degrees] wrapped into `[-180, 180)`. */
internal fun wrapDegrees(degrees: Float): Float = wrap(degrees + 180f, 360f) - 180f

internal fun smoothstep(from: Float, to: Float, value: Float): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}
