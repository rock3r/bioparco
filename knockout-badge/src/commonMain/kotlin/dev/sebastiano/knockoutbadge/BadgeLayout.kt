package dev.sebastiano.knockoutbadge

/** The badge's layers, bottom to top. */
object Layers {
    /** The footage that plays underneath everything. */
    const val VIDEO = 0

    /** The sticker's black, which multiplies the footage all the way down to black. */
    const val BLACK = 1

    /** The sticker's white ink, which has no darkness to add, so the footage shows untouched. */
    const val INK = 2

    /** Everything else printed on top, not blended. */
    const val PRINT = 3

    const val COUNT = 4
}

/**
 * Where one layer sits as the badge comes apart, in the badge's frame: turned by [degrees] in its
 * plane, squashed vertically by [squash] to lie back like a page on a table, scaled by [scale], and
 * lifted by [lift] badge heights up the screen (negative is up).
 */
data class LayerPose(val degrees: Float, val squash: Float, val scale: Float, val lift: Float) {
    companion object {
        val Flat = LayerPose(0f, 1f, 1f, 0f)
    }
}

/**
 * The pose of [layer] when the badge is [explode] apart: 0 assembled, 1 apart. The spring that
 * drives [explode] may overshoot either end a little; the poses follow it.
 */
fun layerPose(layer: Int, explode: Float): LayerPose {
    if (explode == 0f) return LayerPose.Flat
    val middle = (Layers.COUNT - 1) / 2f
    return LayerPose(
        degrees = APART_DEGREES * explode,
        squash = 1f - (1f - APART_SQUASH) * explode,
        scale = 1f - (1f - APART_SCALE) * explode,
        lift = (middle - layer) * APART_SPACING * explode,
    )
}

/**
 * How much of the sticker multiplies the footage, rather than lying over it like paint: all of it
 * when assembled, none when apart, so the white ink still shows when nothing is under it.
 */
fun multiplyShare(explode: Float): Float {
    val t = ((explode - BLEND_FROM) / (BLEND_TO - BLEND_FROM)).coerceIn(0f, 1f)
    return 1f - t * t * (3f - 2f * t)
}

/**
 * A barcode's bars as `[start, width]` pairs across a box 0..1 wide, the same for the same [seed]:
 * [count] bars of one to three units, with gaps of one or two, stretched to fill the box.
 */
fun barcode(seed: Int, count: Int): FloatArray {
    var state = seed.toLong() * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
    fun next(bound: Int): Int {
        state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        return ((state ushr 33) % bound).toInt()
    }
    val units = IntArray(count * 2)
    var total = 0
    for (i in 0 until count) {
        units[i * 2] = if (i == 0) 0 else 1 + next(2)
        units[i * 2 + 1] = 1 + next(3)
        total += units[i * 2] + units[i * 2 + 1]
    }
    val bars = FloatArray(count * 2)
    var at = 0
    for (i in 0 until count) {
        at += units[i * 2]
        bars[i * 2] = at.toFloat() / total
        bars[i * 2 + 1] = units[i * 2 + 1].toFloat() / total
        at += units[i * 2 + 1]
    }
    return bars
}

/**
 * A badge's turn in degrees, as Compose's `graphicsLayer` takes it: [x] about the horizontal axis,
 * [y] about the vertical one.
 */
data class Tilt(val x: Float, val y: Float) {
    companion object {
        val Flat = Tilt(0f, 0f)
    }
}

/**
 * The tilt that sends the badge's side under the pointer away, as if pressed: [dx] and [dy] are the
 * pointer's offset from the badge's centre. Past the badge's edges it tilts no further than [most].
 */
fun tiltToward(dx: Float, dy: Float, halfWidth: Float, halfHeight: Float, most: Float): Tilt {
    val across = (dx / halfWidth).coerceIn(-1f, 1f)
    val down = (dy / halfHeight).coerceIn(-1f, 1f)
    return Tilt(x = -down * most, y = across * most)
}

private const val APART_DEGREES = -30f
private const val APART_SQUASH = 0.56f
private const val APART_SCALE = 0.78f
private const val APART_SPACING = 0.36f
private const val BLEND_FROM = 0.1f
private const val BLEND_TO = 0.75f
