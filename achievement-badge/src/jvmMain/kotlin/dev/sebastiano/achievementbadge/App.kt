package dev.sebastiano.achievementbadge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

/** The stage: the badge on a dark floor, with a button to celebrate again. */
@Composable
fun App(modifier: Modifier = Modifier) {
    var plays by remember { mutableIntStateOf(0) }
    IntUiTheme(isDark = true) {
        Column(modifier = modifier.fillMaxSize().background(BadgeColors.stage)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Achievement badge", style = JewelTheme.typography.h1TextStyle)
                    Text(
                        "A trophy badge that pops in, bursts, and lights itself up.",
                        color = JewelTheme.globalColors.text.info,
                    )
                }
                OutlinedButton(
                    onClick = { plays++ },
                    modifier = Modifier.testTag(BadgeTags.REPLAY),
                ) {
                    Text("Replay")
                }
            }
            AchievementBadge(modifier = Modifier.fillMaxWidth().weight(1f), playCount = plays)
        }
    }
}
