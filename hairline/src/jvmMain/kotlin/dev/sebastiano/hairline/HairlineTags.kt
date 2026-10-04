package dev.sebastiano.hairline

/** Test tags for driving the specimen from Spectre. */
object HairlineTags {
    const val GRID = "hairline-grid"
    const val INTENSITY = "hairline-intensity"
    const val THEME = "hairline-theme"

    fun figure(figure: Figure): String = "hairline-figure-${figure.id}"
}
