// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

/*
 * The original's stylesheet, as a function of an element's classes. Every rule there sits inside
 * `:where()`, so they all have zero specificity and only their order decides: a later rule wins.
 * [paintOf] applies them in that order.
 */

/** The five colours of a palette: the plate fill and four strokes from bright to faint. */
enum class Ink {
    Plate,
    Hi,
    Edge,
    Mid,
    Lo,
}

/**
 * How one element is painted. `null` is `none`. [fillTransitions] is true for dots, whose fill
 * eases over 260ms when their class changes; everything else eases its stroke instead.
 */
data class Paint(
    val fill: Ink?,
    val stroke: Ink?,
    val dashed: Boolean,
    val fillTransitions: Boolean,
)

/**
 * The cascade for one element. [circle] is for `<circle>`, which the drawing rule does not cover
 * (every circle a figure draws is a `.dot`, so the dot rule paints it); [inGhost] is for a path
 * inside a reflection's `.ghost` group.
 */
fun paintOf(classes: ClassList, circle: Boolean, inGhost: Boolean): Paint {
    // path, polygon, ellipse, line: plates filled with the plate colour, mid stroke
    var fill: Ink? = if (circle) Ink.Hi else Ink.Plate
    var stroke: Ink? = if (circle) null else Ink.Mid
    var fillTransitions = false
    if ("nf" in classes) fill = null
    if ("fo" in classes) stroke = null
    if ("sil" in classes) stroke = Ink.Edge
    if ("hi" in classes) stroke = Ink.Hi
    if ("lo" in classes) stroke = Ink.Lo
    val dashed = "dash" in classes
    if ("dot" in classes) {
        stroke = null
        fill =
            when {
                "off" in classes -> Ink.Lo
                "m" in classes -> Ink.Edge
                else -> Ink.Hi
            }
        fillTransitions = true
    }
    if (inGhost && !circle) {
        fill = null
        stroke = Ink.Mid
    }
    return Paint(fill, stroke, dashed, fillTransitions)
}
