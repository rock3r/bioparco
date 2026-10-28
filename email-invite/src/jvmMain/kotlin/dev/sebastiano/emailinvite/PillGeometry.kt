package dev.sebastiano.emailinvite

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Where everything sits, in pixels, for a given moment of the motion. Measured off the video at
 * about 0.4 dp per source pixel. The pill stays centred on ([centreX], [centreY]) while it grows
 * and shrinks; everything else hangs off its left edge, the avatar stack, or the grey field.
 *
 * - [open]: 0 is the "+ Invite" button, 1 the email field. The spring overshoots a touch past 1.
 * - [tuck]: 0 fans the avatars out, 1 tucks them behind the front one.
 * - [slots]: how many avatars the stack makes room for, fractional while one arrives.
 */
internal class PillGeometry(density: Density, val centreX: Float, val centreY: Float) {
    val pillHeight = with(density) { 56.dp.toPx() }
    val avatar = with(density) { 35.dp.toPx() }
    val ring = with(density) { 2.5.dp.toPx() }
    val fieldHeight = with(density) { 47.dp.toPx() }
    val sendWidth = with(density) { 64.dp.toPx() }
    val sendHeight = with(density) { 35.dp.toPx() }
    val iconCentre = with(density) { 24.dp.toPx() }
    val textStart = with(density) { 42.dp.toPx() }

    private val padStart = with(density) { 10.5.dp.toPx() }
    private val padEnd = with(density) { 4.5.dp.toPx() }
    private val gap = with(density) { 10.dp.toPx() }
    private val spread = with(density) { 24.dp.toPx() }
    private val tucked = with(density) { 8.dp.toPx() }
    private val buttonWidth = with(density) { 100.dp.toPx() }
    private val openWidth = with(density) { 260.dp.toPx() }
    private val sendInset = with(density) { 6.dp.toPx() }

    fun step(tuck: Float): Float = lerp(spread, tucked, tuck)

    fun stackWidth(slots: Float, tuck: Float): Float = avatar + (slots - 1f) * step(tuck)

    fun fieldWidth(open: Float): Float = lerp(buttonWidth, openWidth, open)

    fun pillWidth(open: Float, tuck: Float, slots: Float): Float =
        padStart + stackWidth(slots, tuck) + gap + fieldWidth(open) + padEnd

    fun pillLeft(open: Float, tuck: Float, slots: Float): Float =
        centreX - pillWidth(open, tuck, slots) / 2f

    /** The front avatar's left edge. */
    fun stackLeft(open: Float, tuck: Float, slots: Float): Float =
        pillLeft(open, tuck, slots) + padStart

    fun fieldLeft(open: Float, tuck: Float, slots: Float): Float =
        stackLeft(open, tuck, slots) + stackWidth(slots, tuck) + gap

    /** The Send button's left edge: it rides the field's right edge as the field grows. */
    fun sendLeft(open: Float, tuck: Float, slots: Float): Float =
        fieldLeft(open, tuck, slots) + fieldWidth(open) - sendInset - sendWidth

    /** The left edge of an avatar sitting at [slot] (0 is the front). */
    fun avatarLeft(slot: Float, open: Float, tuck: Float, slots: Float): Float =
        stackLeft(open, tuck, slots) + slot * step(tuck)
}
