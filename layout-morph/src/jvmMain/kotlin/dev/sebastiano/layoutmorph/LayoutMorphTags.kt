package dev.sebastiano.layoutmorph

/** Test tags for driving the specimen from Spectre. */
object LayoutMorphTags {
    const val STAGE = "layout-morph-stage"
    const val FIELD = "layout-morph-field"
    const val DETAIL = "layout-morph-detail"

    fun arrangement(arrangement: Arrangement): String =
        "layout-morph-${arrangement.name.lowercase()}"
}
