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
import dev.sebastiano.achievementbadge.App as AchievementBadgeApp
import dev.sebastiano.borderbeam.App as BorderBeamApp
import dev.sebastiano.componentanatomy.App as ComponentAnatomyApp
import dev.sebastiano.departureboard.App as DepartureBoardApp
import dev.sebastiano.dotmatrixrecorder.App as DotMatrixRecorderApp
import dev.sebastiano.grabbystepper.App
import dev.sebastiano.hairline.App as HairlineApp
import dev.sebastiano.holocard.App as HoloCardApp
import dev.sebastiano.honeycomb.App as HoneycombApp
import dev.sebastiano.peelsticker.App as PeelStickerApp
import dev.sebastiano.processingfield.App as ProcessingFieldApp
import dev.sebastiano.scrolleffects.App as ScrollEffectsApp
import dev.sebastiano.thinkingorbs.App as ThinkingOrbsApp
import dev.sebastiano.wheelpicker.App as WheelPickerApp
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
    AchievementBadge,
    ComponentAnatomy,
    ScrollEffects,
    PeelSticker,
    Honeycomb,
    Hairline,
    DepartureBoard,
    HoloCard,
    WheelPicker,
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
                    onOpenAchievementBadge = { route = SpecimenRoute.AchievementBadge },
                    onOpenComponentAnatomy = { route = SpecimenRoute.ComponentAnatomy },
                    onOpenScrollEffects = { route = SpecimenRoute.ScrollEffects },
                    onOpenPeelSticker = { route = SpecimenRoute.PeelSticker },
                    onOpenHoneycomb = { route = SpecimenRoute.Honeycomb },
                    onOpenHairline = { route = SpecimenRoute.Hairline },
                    onOpenDepartureBoard = { route = SpecimenRoute.DepartureBoard },
                    onOpenHoloCard = { route = SpecimenRoute.HoloCard },
                    onOpenWheelPicker = { route = SpecimenRoute.WheelPicker },
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
            SpecimenRoute.AchievementBadge ->
                SpecimenHost(
                    title = "Achievement badge",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AchievementBadgeApp()
                }
            SpecimenRoute.ComponentAnatomy ->
                SpecimenHost(
                    title = "Component anatomy",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ComponentAnatomyApp()
                }
            SpecimenRoute.ScrollEffects ->
                SpecimenHost(
                    title = "Scroll effects",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ScrollEffectsApp()
                }
            SpecimenRoute.PeelSticker ->
                SpecimenHost(
                    title = "Peel sticker",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PeelStickerApp()
                }
            SpecimenRoute.Honeycomb ->
                SpecimenHost(
                    title = "Honeycomb",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HoneycombApp()
                }
            SpecimenRoute.Hairline ->
                SpecimenHost(
                    title = "Hairline",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HairlineApp()
                }
            SpecimenRoute.DepartureBoard ->
                SpecimenHost(
                    title = "Departure board",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DepartureBoardApp()
                }
            SpecimenRoute.HoloCard ->
                SpecimenHost(
                    title = "Holo card",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HoloCardApp()
                }
            SpecimenRoute.WheelPicker ->
                SpecimenHost(
                    title = "Wheel picker",
                    onBack = { route = SpecimenRoute.Catalog },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    WheelPickerApp()
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
    onOpenAchievementBadge: () -> Unit,
    onOpenComponentAnatomy: () -> Unit,
    onOpenScrollEffects: () -> Unit,
    onOpenPeelSticker: () -> Unit,
    onOpenHoneycomb: () -> Unit,
    onOpenHairline: () -> Unit,
    onOpenDepartureBoard: () -> Unit,
    onOpenHoloCard: () -> Unit,
    onOpenWheelPicker: () -> Unit,
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
            "A collection of Compose Desktop experiments. Open a specimen to try it.",
            style = JewelTheme.typography.regular,
        )
        SpecimenCard(
            name = "Grabby stepper",
            summary =
                "Drag the number to change the count. The dark track stretches toward the pointer.",
            testTag = "open-grabby-stepper",
            onOpen = onOpenGrabby,
        )
        SpecimenCard(
            name = "Chat bubble transition",
            summary = "The message input moves into the conversation and becomes a sent bubble.",
            testTag = "open-chat-bubble-transition",
            onOpen = onOpenChat,
        )
        SpecimenCard(
            name = "Processing field",
            summary = "A grid forms an animated shape as each mark changes size and opacity.",
            testTag = "open-processing-field",
            onOpen = onOpenProcessingField,
        )
        SpecimenCard(
            name = "Thinking Orbs",
            summary = "Nine dotted 3D indicators show AI or agent activity.",
            testTag = "open-thinking-orbs",
            onOpen = onOpenThinkingOrbs,
        )
        SpecimenCard(
            name = "Dot-matrix recorder",
            summary =
                "A recording control uses 5×5 dot icons, counts down 3, 2, 1 and becomes a " +
                    "timer. Based on Sasha Birukoff’s Halogen.",
            testTag = "open-dot-matrix-recorder",
            onOpen = onOpenDotMatrixRecorder,
        )
        SpecimenCard(
            name = "Border beam",
            summary = "A glow moves along a card border or pulses in place.",
            testTag = "open-border-beam",
            onOpen = onOpenBorderBeam,
        )
        SpecimenCard(
            name = "Achievement badge",
            summary =
                "A trophy badge spins into its slot with confetti and light effects. " +
                    "Based on Adrian’s animation (@adriankuleszo).",
            testTag = "open-achievement-badge",
            onOpen = onOpenAchievementBadge,
        )
        SpecimenCard(
            name = "Component anatomy",
            summary =
                "A Jewel button shows its drawing layers and cycles through its states " +
                    "in time with music. Based on Jae’s design (@Jaenam97).",
            testTag = "open-component-anatomy",
            onOpen = onOpenComponentAnatomy,
        )
        SpecimenCard(
            name = "Scroll effects",
            summary =
                "A card carousel has six scroll effects: stretch, bulge, drum, " +
                    "shatter, Thanos and glitch. Based on sucodee’s design (@sucodeee).",
            testTag = "open-scroll-effects",
            onOpen = onOpenScrollEffects,
        )
        SpecimenCard(
            name = "Peel sticker",
            summary =
                "Peel a die-cut sticker by dragging an edge. Choose a pointer-controlled light effect: " +
                    "white, sparkle, prism, ripple or halftone. Based on sucodee’s design (@sucodeee).",
            testTag = "open-peel-sticker",
            onOpen = onOpenPeelSticker,
        )
        SpecimenCard(
            name = "Honeycomb",
            summary =
                "A 2D pan of circles packed in a honeycomb. They shrink and fade as they leave " +
                    "the centre. After Shubham Singh (@Shubham_iosdev).",
            testTag = "open-honeycomb",
            onOpen = onOpenHoneycomb,
        )
        SpecimenCard(
            name = "Hairline",
            summary =
                "Nineteen isometric line figures respond to pointer movement: cards, pillars, a " +
                    "turntable, a vault and more. This ports Lucas Marques's hairline (MIT).",
            testTag = "open-hairline",
            onOpen = onOpenHairline,
        )
        SpecimenCard(
            name = "Departure board",
            summary =
                "World clocks on a dot-matrix board. Letters scramble into place, and a search " +
                    "field puts another city on the board. After HASSCO (@itshassco).",
            testTag = "open-departure-board",
            onOpen = onOpenDepartureBoard,
        )
        SpecimenCard(
            name = "Holo card",
            summary =
                "A holographic trading card tilts towards the pointer in 3D. Its puffy foil " +
                    "mascot shifts colour as it turns. After Ann Nguyen (@ann_nnng).",
            testTag = "open-holo-card",
            onOpen = onOpenHoloCard,
        )
        SpecimenCard(
            name = "Wheel picker",
            summary =
                "A duration ruler wrapped around a drum: ticks squeeze, fade and split into " +
                    "colour at the edges, and the readout rolls. After Alex (@a_cucos).",
            testTag = "open-wheel-picker",
            onOpen = onOpenWheelPicker,
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
