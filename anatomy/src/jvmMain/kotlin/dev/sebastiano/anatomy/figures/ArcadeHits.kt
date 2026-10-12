// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.graphics.asSkiaPath
import dev.sebastiano.anatomy.kit.SolidMark
import kotlin.math.hypot

/** Reverse paint order gives the visible SVG target, rather than clicking through the cabinet. */
internal fun arcadeCoinHit(x: Double, y: Double): Boolean {
    for (item in ArcadeCabinet.ITEMS.asReversed()) {
        val fills = item.art?.marks?.filterIsInstance<SolidMark>() ?: continue
        if (fills.any { it.fill.path.asSkiaPath().contains(x.toFloat(), y.toFloat()) })
            return item.name.startsWith("door")
    }
    val tray = Arcade.at(Arcade.TRAY)
    return hypot(x - tray.x, y - tray.y) < 9 * Arcade.KS
}
