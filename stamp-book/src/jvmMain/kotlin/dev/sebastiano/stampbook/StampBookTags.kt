package dev.sebastiano.stampbook

/** Test tags for driving the specimen from Spectre. */
object StampBookTags {
    const val STAGE = "stamp-book-stage"
    const val FOLDER = "stamp-book-folder"
    const val STAMP_A = "stamp-book-stamp-quiet"
    const val STAMP_B = "stamp-book-stamp-bird"

    fun swatch(index: Int): String = "stamp-book-swatch-$index"
}
