package dev.sebastiano.stampbook

import androidx.compose.ui.graphics.Color

/** One colourway of the book: its frosted cover, the back board behind it, and the cover's text. */
internal data class FolderInk(
    val name: String,
    val swatch: Color,
    val cover: Color,
    val back: Color,
    val label: Color,
    val note: Color,
)

internal object StampBookPalette {
    val Table = Color(0xFFF2F2F2)

    val inks =
        listOf(
            FolderInk(
                name = "gold",
                swatch = Color(0xFFD9B23C),
                cover = Color(0xFFE4BA48),
                back = Color(0xFFF1CB5E),
                label = Color(0xFF3B2D0A),
                note = Color(0x993B2D0A),
            ),
            FolderInk(
                name = "magenta",
                swatch = Color(0xFFDA37A7),
                cover = Color(0xFFDE36A9),
                back = Color(0xFFF052BF),
                label = Color(0xFFFFFFFF),
                note = Color(0xA6FFFFFF),
            ),
            FolderInk(
                name = "black",
                swatch = Color(0xFF1F1F1F),
                cover = Color(0xFF282828),
                back = Color(0xFF363636),
                label = Color(0xFFF0F0F0),
                note = Color(0x8CF0F0F0),
            ),
        )

    /** The ring around the chosen swatch. */
    val Ring = Color(0x59000000)
}
