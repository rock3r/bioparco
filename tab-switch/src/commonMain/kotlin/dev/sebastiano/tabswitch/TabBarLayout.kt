package dev.sebastiano.tabswitch

/**
 * Where each tab sits along the bar, measured from the inside of its padding.
 *
 * Each tab has an [expansion] between 0 (closed: just its icon) and 1 (open: icon and label). The
 * bar never changes width: an open tab takes its own [expandedWidths] entry, and the closed tabs
 * share whatever is left equally. In between, a tab's width blends the two, so tabs opening and
 * closing at the same pace always fill the bar exactly.
 *
 * @param slot how wide a closed tab is next to the widest open one.
 * @param iconInset how far an open tab's icon centre sits from its left end.
 */
class TabBarLayout(
    private val expandedWidths: FloatArray,
    private val slot: Float,
    private val iconInset: Float,
) {
    val count: Int = expandedWidths.size

    /** The widest tab open, and every other tab closed. */
    val width: Float = (expandedWidths.maxOrNull() ?: 0f) + (count - 1).coerceAtLeast(0) * slot

    private val settled = FloatArray(2 * count)

    init {
        val expansion = FloatArray(count)
        val spans = FloatArray(2 * count)
        for (tab in 0 until count) {
            expansion.fill(0f)
            expansion[tab] = 1f
            spans(expansion, spans)
            settled[2 * tab] = spans[2 * tab]
            settled[2 * tab + 1] = spans[2 * tab + 1]
        }
    }

    /**
     * Writes each tab's left and right ends into [out], as `left0, right0, left1, right1, …`, for
     * the given [expansion]s, which should add up to one.
     */
    fun spans(expansion: FloatArray, out: FloatArray) {
        var open = 0f
        var closed = 0f
        for (tab in 0 until count) {
            open += expansion[tab] * expandedWidths[tab]
            closed += 1f - expansion[tab]
        }
        val share = if (closed > EPSILON) (width - open) / closed else 0f
        var x = 0f
        for (tab in 0 until count) {
            val e = expansion[tab]
            out[2 * tab] = x
            x += e * expandedWidths[tab] + (1f - e) * share
            out[2 * tab + 1] = x
        }
    }

    /** The left end of [tab] once it is open and the others are closed. */
    fun settledLeft(tab: Int): Float = settled[2 * tab]

    /** The right end of [tab] once it is open and the others are closed. */
    fun settledRight(tab: Int): Float = settled[2 * tab + 1]

    /**
     * Where a tab's icon centre goes: the middle of a closed tab, [iconInset] from the left of an
     * open one, and in between while it opens.
     */
    fun iconCentre(left: Float, right: Float, expansion: Float): Float {
        val middle = (left + right) / 2f
        return middle + (left + iconInset - middle) * expansion
    }

    /** The tab under [x] in the given [spans], or `null` off the ends of the bar. */
    fun tabAt(x: Float, spans: FloatArray): Int? {
        if (count == 0 || x < spans[0] || x >= spans[2 * count - 1]) return null
        for (tab in 0 until count) if (x < spans[2 * tab + 1]) return tab
        return null
    }

    private companion object {
        const val EPSILON = 1e-6f
    }
}
