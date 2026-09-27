package dev.sebastiano.componentanatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

/** A Jewel button, exploded into the layers it paints, touring its states to the beat. */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) {
        val stage = rememberAnatomyStage()
        // Synthesising the groove takes a moment, so start early and have it ready.
        LaunchedEffect(Unit) { if (GroovePlayer.isAvailable) groove() }
        Column(
            modifier = modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)
        ) {
            Header(stage)
            ComponentAnatomy(stage, modifier = Modifier.fillMaxWidth().weight(1f))
            Controls(stage)
        }
    }
}

@Composable
private fun Header(stage: AnatomyStage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Component anatomy", style = JewelTheme.typography.h1TextStyle)
            Text(
                "A Jewel button, taken apart into the layers it really paints, in the order it paints them.",
                color = JewelTheme.globalColors.text.info,
            )
        }
        BeatDots(stage)
    }
}

/** Four dots, one per beat of the bar. Drawn, not composed, so the beat never recomposes. */
@Composable
private fun BeatDots(stage: AnatomyStage) {
    val lit = JewelTheme.globalColors.outlines.focused
    val dim = Color(0x33FFFFFF)
    Box(
        Modifier.size(width = 64.dp, height = 12.dp).drawBehind {
            val step = size.width / AnatomyTimeline.BEATS_PER_BAR
            val radius = size.height / 2 - 1f
            for (beat in 0 until AnatomyTimeline.BEATS_PER_BAR) {
                val on = beat == stage.beatInBar
                val grow = if (on) 1f + 0.35f * stage.pulse else 1f
                drawCircle(
                    color = if (on) lit else dim,
                    radius = radius * grow * (if (on) 1f else 0.7f),
                    center = Offset(step * beat + step / 2, size.height / 2),
                )
            }
        }
    )
}

/** The controls wrap onto more lines when the host is narrow, as it is in the showcase. */
@Composable
private fun Controls(stage: AnatomyStage) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (state in AnatomyState.entries) {
                RadioButtonChip(
                    selected = stage.state == state,
                    onClick = { stage.pick(state) },
                    modifier = Modifier.testTag(AnatomyTags.state(state)),
                ) {
                    Text(state.name)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButtonChip(
                selected = !stage.outlined,
                onClick = { stage.pickOutlined(false) },
                modifier = Modifier.testTag(AnatomyTags.DEFAULT),
            ) {
                Text("Default")
            }
            RadioButtonChip(
                selected = stage.outlined,
                onClick = { stage.pickOutlined(true) },
                modifier = Modifier.testTag(AnatomyTags.OUTLINED),
            ) {
                Text("Outlined")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckboxRow(
                text = "Exploded",
                checked = stage.looksExploded,
                onCheckedChange = stage::setExploded,
                modifier = Modifier.testTag(AnatomyTags.EXPLODED),
            )
            CheckboxRow(
                text = "Music",
                checked = stage.musicOn,
                onCheckedChange = { on -> stage.setMusic(on) { groove() } },
                enabled = GroovePlayer.isAvailable,
                modifier = Modifier.testTag(AnatomyTags.MUSIC),
            )
            if (!stage.following) {
                Link(
                    "Back to groovin'",
                    onClick = stage::backToGroovin,
                    modifier = Modifier.testTag(AnatomyTags.BACK_TO_GROOVIN),
                )
            }
        }
    }
}

private var cachedGroove: ShortArray? = null

/** The groove takes a moment to synthesise, so it is made once, off the UI thread. */
private suspend fun groove(): ShortArray =
    cachedGroove
        ?: withContext(Dispatchers.Default) { GrooveSynth.render() }.also { cachedGroove = it }
