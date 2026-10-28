package dev.sebastiano.layoutmorph

/**
 * Where a card sits and how it looks, in dp around its centre ([x], [y]). [rotation] turns it in
 * the screen's plane and [turn] about its own upright axis, both in degrees. [row] blends its face
 * from a tile (0) to a list row (1); [caption] shows the caption strip under a tile. Cards with a
 * greater [depth] draw on top.
 */
data class CardPose(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val turn: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val row: Float = 0f,
    val caption: Float = 0f,
    val depth: Float = 0f,
)

/** The card field the arrangements fill, in dp. */
data class Field(val width: Float, val height: Float)
