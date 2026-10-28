package dev.sebastiano.stampbook

/** A rectangle in stage pixels. */
data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * Whether the book is open. Hovering the closed book ([opener]) opens it; once open it stays open
 * anywhere over the wider spread ([keeper]) the stamps fan out into, so following a stamp with the
 * pointer doesn't snap the book shut. A click pins it open for touch and keyboards, or shuts a
 * hovered-open book, which then stays shut until the pointer has left and come back.
 */
class HoverLatch(val opener: Area, val keeper: Area) {
    private var hovering = false
    private var pinned = false

    /**
     * Set when a click shuts the book under the pointer, so the hover doesn't reopen it at once.
     */
    private var suppressed = false

    val isOpen: Boolean
        get() = pinned || hovering

    fun move(x: Float, y: Float) {
        if (!keeper.contains(x, y)) suppressed = false
        hovering = if (hovering) keeper.contains(x, y) else !suppressed && opener.contains(x, y)
    }

    fun leave() {
        hovering = false
        suppressed = false
    }

    fun toggle() {
        if (isOpen) {
            pinned = false
            if (hovering) suppressed = true
            hovering = false
        } else {
            pinned = true
        }
    }
}
