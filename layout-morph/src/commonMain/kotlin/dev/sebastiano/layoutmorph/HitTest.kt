package dev.sebastiano.layoutmorph

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Below this a card is too faded to pick: the far side of the helix. */
private const val PICKABLE_ALPHA = 0.5f

/** Below this a turned card is too close to edge-on to pick. */
private const val PICKABLE_FACING = 0.2f

/**
 * The card under ([x], [y]), in dp: the top one by depth, then by order, as they are drawn. Cards
 * mostly faded out or seen edge-on cannot be picked. A turned card is taken as its face's
 * silhouette, narrowed by the turn, without the slight perspective.
 */
fun hitTest(poses: List<CardPose>, x: Float, y: Float): Int? {
    var best: Int? = null
    for (i in poses.indices) {
        val pose = poses[i]
        val facing = abs(cos(pose.turn.toRadians()))
        if (pose.alpha < PICKABLE_ALPHA || facing < PICKABLE_FACING) continue
        // Into the card's own axes: undo the position, then the rotation, then the scale.
        val dx = x - pose.x
        val dy = y - pose.y
        val a = -pose.rotation.toRadians()
        val localX = (dx * cos(a) - dy * sin(a)) / pose.scale
        val localY = (dx * sin(a) + dy * cos(a)) / pose.scale
        val inside = abs(localX) <= pose.width * facing / 2 && abs(localY) <= pose.height / 2
        if (inside && (best == null || pose.depth >= poses[best].depth)) best = i
    }
    return best
}
