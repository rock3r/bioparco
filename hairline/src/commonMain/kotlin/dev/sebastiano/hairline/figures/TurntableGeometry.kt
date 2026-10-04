// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.jsRound
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Turntable block geometry and painter's ordering, independent of the retained tree. */
internal data class TurntableBlock(
    val x0: Double,
    val y0: Double,
    val z0: Double,
    val x1: Double,
    val y1: Double,
    val z1: Double,
    val column: Int,
)

internal val TURNTABLE_BLOCKS =
    listOf(
        TurntableBlock(-42.0, -42.0, 0.0, -12.0, -12.0, 14.0, 0),
        TurntableBlock(-36.0, -36.0, 14.0, -18.0, -18.0, 34.0, 0),
        TurntableBlock(0.0, -46.0, 0.0, 16.0, -6.0, 10.0, 1),
        TurntableBlock(24.0, -32.0, 0.0, 40.0, -16.0, 44.0, 2),
        TurntableBlock(-44.0, 4.0, 0.0, -4.0, 18.0, 22.0, 3),
        TurntableBlock(8.0, 6.0, 0.0, 40.0, 38.0, 8.0, 4),
        TurntableBlock(16.0, 14.0, 8.0, 28.0, 26.0, 20.0, 4),
        TurntableBlock(-30.0, 28.0, 0.0, -16.0, 42.0, 12.0, 5),
    )
internal const val TURNTABLE_HOME = 45.0
private const val DETENT = 90.0

internal fun turntableDetent(a: Double) =
    TURNTABLE_HOME + DETENT * jsRound((a - TURNTABLE_HOME) / DETENT)

private fun depth(b: TurntableBlock, s: Double, c: Double) =
    (b.x0 + b.x1) * s + (b.y0 + b.y1) * c + (b.z0 + b.z1) * 0.01

private fun behind(a: TurntableBlock, b: TurntableBlock, s: Double, c: Double): Boolean =
    when {
        a.column == b.column -> a.z0 < b.z0
        a.x1 <= b.x0 -> s > 0
        b.x1 <= a.x0 -> s < 0
        a.y1 <= b.y0 -> c > 0
        b.y1 <= a.y0 -> c < 0
        else -> depth(a, s, c) < depth(b, s, c)
    }

internal data class TurntableScreenBox(
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
)

private fun screenBox(b: TurntableBlock, s: Double, c: Double, k: Double): TurntableScreenBox {
    val zf = sqrt(1 - k * k)
    var x0 = 1e9
    var x1 = -1e9
    var y0 = 1e9
    var y1 = -1e9
    for (x in listOf(b.x0, b.x1)) for (y in listOf(b.y0, b.y1)) for (z in listOf(b.z0, b.z1)) {
        val xx = x * c - y * s
        val yy = (x * s + y * c) * k - z * zf
        x0 = min(x0, xx)
        x1 = max(x1, xx)
        y0 = min(y0, yy)
        y1 = max(y1, yy)
    }
    return TurntableScreenBox(x0, y0, x1, y1)
}

/** Indices into [blocks], back to front. */
internal fun turntableOrder(
    blocks: List<TurntableBlock>,
    s: Double,
    c: Double,
    k: Double,
): List<Int> {
    val boxes = blocks.map { screenBox(it, s, c, k) }
    val next = List(blocks.size) { ArrayList<Int>() }
    val wait = IntArray(blocks.size)
    for (i in blocks.indices) for (j in blocks.indices) {
        val a = boxes[i]
        val b = boxes[j]
        val separate = i == j || a.x1 <= b.x0 || b.x1 <= a.x0 || a.y1 <= b.y0 || b.y1 <= a.y0
        if (!separate && behind(blocks[i], blocks[j], s, c)) {
            next[i].add(j)
            wait[j]++
        }
    }
    return topologicalOrder(blocks, next, wait, s, c)
}

private fun topologicalOrder(
    blocks: List<TurntableBlock>,
    next: List<List<Int>>,
    wait: IntArray,
    s: Double,
    c: Double,
): List<Int> {
    val out = ArrayList<Int>()
    val done = BooleanArray(blocks.size)
    while (out.size < blocks.size) {
        var pick = -1
        for (pass in 0..1) {
            if (pick >= 0) break
            for (i in blocks.indices) {
                if (
                    !done[i] &&
                        (pass != 0 || wait[i] == 0) &&
                        (pick < 0 || depth(blocks[i], s, c) < depth(blocks[pick], s, c))
                )
                    pick = i
            }
        }
        done[pick] = true
        out.add(pick)
        next[pick].forEach { wait[it]-- }
    }
    return out
}
