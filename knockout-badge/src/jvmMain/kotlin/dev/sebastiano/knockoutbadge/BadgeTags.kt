package dev.sebastiano.knockoutbadge

/** Test tags for driving the specimen from Spectre. */
object BadgeTags {
    /** The stage. Hover tilts the badge, a click takes it apart or puts it back together. */
    const val STAGE = "knockout-badge-stage"

    /** Where the badge sits when it is together. */
    const val BADGE = "knockout-badge-badge"

    /** The corner that peels. */
    const val CORNER = "knockout-badge-corner"

    /** Its semantics text says whether the badge is together or apart, and the word on it. */
    const val STATE = "knockout-badge-state"

    /** The state text for a badge that is together, showing [word]. */
    fun together(word: String) = "together · $word"

    /** The state text for a badge that is apart, showing [word]. */
    fun apart(word: String) = "apart · $word"
}
