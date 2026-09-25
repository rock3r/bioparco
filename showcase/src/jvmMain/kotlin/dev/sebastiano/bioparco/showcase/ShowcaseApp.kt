package dev.sebastiano.bioparco.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import chatbubble.ChatApp
import dev.sebastiano.borderbeam.App as BorderBeamApp
import dev.sebastiano.dotmatrixrecorder.App as DotMatrixRecorderApp
import dev.sebastiano.grabbystepper.App
import dev.sebastiano.processingfield.App as ProcessingFieldApp
import dev.sebastiano.thinkingorbs.App as ThinkingOrbsApp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

private enum class SpecimenRoute {
    Catalog,
    Grabby,
    ChatBubble,
    ProcessingField,
    ThinkingOrbs,
    DotMatrixRecorder,
    BorderBeam,
}

@Composable
fun ShowcaseApp(modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf(SpecimenRoute.Catalog) }
    // Jewel themes the text but not the window. Without this, the dark theme's light text sits
    // on the platform's light window background.
    Column(modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        when (route) {
            SpecimenRoute.Catalog ->
                Catalog(
                    onOpenGrabby = { route = SpecimenRoute.Grabby },
                    onOpenChat = { route = SpecimenRoute.ChatBubble },
                    onOpenProcessingField = { route = SpecimenRoute.ProcessingField },
                    onOpenThinkingOrbs = { route = SpecimenRoute.ThinkingOrbs },
                    onOpenDotMatrixRecorder = { route = SpecimenRoute.DotMatrixRecorder },
                    onOpenBorderBeam = { route = SpecimenRoute.BorderBeam },
                    modifier = Modifier.fillMaxSize(),
                )
            SpecimenRoute.Grabby ->
                SpecimenHost(
                    title = "Grabby stepper",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    App()
                }
            SpecimenRoute.ChatBubble ->
                SpecimenHost(
                    title = "Chat bubble transition",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ChatApp()
                }
            SpecimenRoute.ProcessingField ->
                SpecimenHost(
                    title = "Processing field",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ProcessingFieldApp()
                }
            SpecimenRoute.ThinkingOrbs ->
                SpecimenHost(
                    title = "Thinking Orbs",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ThinkingOrbsApp()
                }
            SpecimenRoute.DotMatrixRecorder ->
                SpecimenHost(
                    title = "Dot-matrix recorder",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DotMatrixRecorderApp()
                }
            SpecimenRoute.BorderBeam ->
                SpecimenHost(
                    title = "Border beam",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    BorderBeamApp()
                }
        }
    }
}

@Composable
private fun Catalog(
    onOpenGrabby: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProcessingField: () -> Unit,
    onOpenThinkingOrbs: () -> Unit,
    onOpenDotMatrixRecorder: () -> Unit,
    onOpenBorderBeam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("bioparco", style = JewelTheme.typography.h1TextStyle)
        Text(
            "Specimens gathered like a bioparco: small Compose Desktop animals, each in its own enclosure.",
            style = JewelTheme.typography.regular,
        )
        SpecimenCard(
            name = "Grabby stepper",
            summary =
                "A thumb you can actually grab. The dark track stretches after it like rubber.",
            testTag = "open-grabby-stepper",
            onOpen = onOpenGrabby,
        )
        SpecimenCard(
            name = "Chat bubble transition",
            summary = "The composer chrome flies into the transcript and becomes a sent bubble.",
            testTag = "open-chat-bubble-transition",
            onOpen = onOpenChat,
        )
        SpecimenCard(
            name = "Processing field",
            summary =
                "A grid of marks whose sizes describe one soft mass that drifts, folds and breathes.",
            testTag = "open-processing-field",
            onOpen = onOpenProcessingField,
        )
        SpecimenCard(
            name = "Thinking Orbs",
            summary = "Nine dotted 3D signals for what an AI or agent is doing.",
            testTag = "open-thinking-orbs",
            onOpen = onOpenThinkingOrbs,
        )
        SpecimenCard(
            name = "Dot-matrix recorder",
            summary =
                "A record pill with 5×5 dot icons that counts down 3, 2, 1 and folds into a " +
                    "timer. After Sasha Birukoff’s Halogen.",
            testTag = "open-dot-matrix-recorder",
            onOpen = onOpenDotMatrixRecorder,
        )
        SpecimenCard(
            name = "Border beam",
            summary = "A traveling or breathing glow that rides the border of a card.",
            testTag = "open-border-beam",
            onOpen = onOpenBorderBeam,
        )
    }
}

@Composable
private fun SpecimenCard(
    name: String,
    summary: String,
    testTag: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, style = JewelTheme.typography.h2TextStyle)
        Text(summary, style = JewelTheme.typography.regular)
        DefaultButton(onClick = onOpen, modifier = Modifier.testTag(testTag)) {
            Text("Open enclosure")
        }
    }
}

@Composable
private fun SpecimenHost(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PaddingValues(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("back-to-catalog")) {
                Text("Back to catalog")
            }
            Text(title, style = JewelTheme.typography.h3TextStyle)
        }
        content()
    }
}
