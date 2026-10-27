// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke

/*
 * Paints a tube chunk the way the original's `ISO_CSS` styles `tubeSvg`: `.tb-body` in the middle
 * side tone (`--anatomy-shade-2`), `.tb-shine` in the top tone and `.tb-shade` in the darkest side
 * tone, `.tb-ring` 0.45 px with butt caps in its tone at its opacity, and `.tb-edge` as an ordinary
 * 0.6 px line in the piece's tone.
 */

/** The ring stroke weight in screen px. */
private const val RING_WEIGHT = 0.45f

/** Paints a tube chunk now: a cable or hose that is rebuilt each frame. */
fun IsoScope.tube(piece: TubePiece, tone: Tone = Tone.Hi) = drawTube(TubeMark(piece, tone))

internal fun IsoScope.drawTube(mark: TubeMark) {
    record(mark)
    val p = palette
    draw.drawPath(mark.body.path, p.shades[2], style = Fill)
    if (mark.piece.wide) {
        draw.drawPath(mark.shine.path, p.top, style = Fill)
        draw.drawPath(mark.shade.path, p.shades[0], style = Fill)
    }
    if (mark.rings.isNotEmpty()) {
        val ring = DrawStroke(px(RING_WEIGHT), cap = StrokeCap.Butt, join = StrokeJoin.Round)
        val ink = p.ink(mark.piece.ringTone)
        for ((alpha, shape) in mark.rings) {
            draw.drawPath(shape.path, ink, alpha = alpha.toFloat(), style = ring)
        }
    }
    draw.drawPath(mark.edges.path, p.ink(mark.tone), style = hairline())
}
