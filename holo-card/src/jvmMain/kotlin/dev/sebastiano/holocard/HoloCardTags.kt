package dev.sebastiano.holocard

/** Test tags for driving the specimen from Spectre. */
object HoloCardTags {
    const val STAGE = "holo-card-stage"
    const val CARD = "holo-card-card"
    const val AUTOPILOT = "holo-card-autopilot"

    /** The dock's thumbnail for the card at [index]. */
    fun thumbnail(index: Int) = "holo-card-thumbnail-$index"
}
