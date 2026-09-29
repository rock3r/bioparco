package dev.sebastiano.peelsticker

/** How the sticker's face catches the light under the pointer. */
enum class ShineMode(val number: Int, val label: String) {
    /** A soft white glare. */
    White(1, "White"),

    /** Glitter that twinkles in the light. */
    Sparkle(2, "Sparkle"),

    /** The colour channels split apart at every edge. */
    Prism(3, "Prism"),

    /** Rings spread out from the pointer, as on water. */
    Ripple(4, "Ripple"),

    /** A printed dot screen. */
    Halftone(5, "Halftone"),
}
