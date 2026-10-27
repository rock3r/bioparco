package dev.sebastiano.squeezeprogress

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Sampled from the source video. Playing is a blue field with a lighter fill behind the line;
 * paused is orange with a darker fill, and the ink turns from white to near-black.
 *
 * The two looks blend straight through sRGB, as the video does: halfway the field is a dusty mauve.
 */
internal object SqueezeProgressPalette {
    private val playingField = Color(0xFF2E48F8)
    private val playingFill = Color(0xFF3A53F6)
    private val playingInk = Color.White
    private val pausedField = Color(0xFFF59E2E)
    private val pausedFill = Color(0xFFE4932B)
    private val pausedInk = Color(0xFF201600)

    /** The `%` sign is the ink at this alpha. */
    const val SIGN_ALPHA = 0.5f

    fun field(paused: Float): Color = lerp(playingField, pausedField, paused)

    fun fill(paused: Float): Color = lerp(playingFill, pausedFill, paused)

    fun ink(paused: Float): Color = lerp(playingInk, pausedInk, paused)

    /** Both inks, for text that cross-fades in a graphics layer instead of recomposing. */
    val inks = listOf(playingInk, pausedInk)
}
