package dev.sebastiano.emailinvite

import androidx.compose.ui.graphics.Color

/** Sampled from the source video. */
internal object EmailInvitePalette {
    val stage = Color(0xFFF6F4F0)
    val pill = Color.White
    val hairline = Color(0xFFE6E4E0)
    val shadow = Color(0xFF3A3020)

    /** The grey of the Invite button and the field it grows into, and the button on hover. */
    val field = Color(0xFFEAEAE4)
    val fieldHover = Color(0xFFE4E1DA)

    val ink = Color(0xFF18150F)
    val placeholder = Color(0xFF7E7E75)
    val caret = Color(0xFF303FF6)

    val sendIdle = Color(0xFFC6C3B7)
    val sendArmed = Color(0xFF18150F)

    /** Sent, and the newest guest's avatar. */
    val accent = Color(0xFF303CF6)
    val onAccent = Color.White

    /** The video's three people, A, M and L: fill and letter. */
    val founders =
        listOf(
            Color(0xFFE4DBCF) to Color(0xFF544B3F),
            Color(0xFFD8E1D8) to Color(0xFF4A564C),
            Color(0xFFDED8E7) to Color(0xFF585070),
        )

    /** An earlier invite, once a newer one has taken the accent. */
    val invited = Color(0xFFD9DCF6) to Color(0xFF3E4590)
}
