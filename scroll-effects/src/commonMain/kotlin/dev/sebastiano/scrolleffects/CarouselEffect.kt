package dev.sebastiano.scrolleffects

/** The six ways a card can leave the centre of the carousel, in the order the original lists. */
enum class CarouselEffect(val label: String) {
    Stretch("Stretch"),
    Bulge("Bulge"),
    Drum("Drum"),
    Shatter("Shatter"),
    Thanos("Thanos"),
    Glitch("Glitch");

    /** The 1-based number the tab bar and the title show next to the name. */
    val number: Int
        get() = ordinal + 1
}
