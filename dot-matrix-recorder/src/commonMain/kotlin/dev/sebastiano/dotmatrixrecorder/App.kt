package dev.sebastiano.dotmatrixrecorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            RecorderPill(Modifier.align(Alignment.Center))
            Text(
                text =
                    "Hover the pill. Record counts down; hover again to stop, restart, or delete.",
                style =
                    TextStyle(
                        color = Color(0xFF4A4A4A),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}
