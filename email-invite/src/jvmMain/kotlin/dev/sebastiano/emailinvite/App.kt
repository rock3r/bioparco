package dev.sebastiano.emailinvite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A pill with three avatars and an Invite button. Click Invite (or press Enter) and it opens into
 * an email field; type an address and Send turns black; send it and the new guest hops into the
 * front of the stack as the pill closes. Escape, or a click outside, closes it without sending.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = false) { EmailInviteStage(modifier.fillMaxSize()) }
}
