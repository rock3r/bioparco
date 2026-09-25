package dev.sebastiano.borderbeam

/** Traveling or breathing preset. Names match the border-beam `size` prop. */
enum class BeamSize {
    Sm,
    Md,
    Line,
    PulseInner,
    PulseOutside,
}

/** Public palettes from border-beam. `mono` pins the hue shift. */
enum class BeamColorVariant {
    Colorful,
    Mono,
    Ocean,
    Sunset,
}

enum class BeamTheme {
    Dark,
    Light,
}

internal enum class BeamCorner {
    Tl,
    Tr,
    Bl,
    Br,
}

internal data class PulseParams(
    val sp: Float,
    val dr: Float,
    val op: Float,
    val gh: Float,
    val bs: Float,
    val ss: Float,
    val ghs: Float,
    val huePeriod: Float,
)
