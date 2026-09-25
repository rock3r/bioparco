package dev.sebastiano.dotmatrixrecorder

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

@OptIn(ExperimentalTestApi::class)
class RecorderKeyboardTest {
    @Test
    fun focusSurvivesTheDockOpeningIntoTheMenu() = runComposeUiTest {
        // The lens never stops animating (it shimmers at rest), so idling must not chase frames.
        mainClock.autoAdvance = false
        setContent { IntUiTheme(isDark = true) { RecorderPill() } }

        onNodeWithTag(RecorderTags.DOCK_RECORD)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        // Longer than the collapse grace, so a lost focus would fold the menu back into the dock.
        mainClock.advanceTimeBy(1_000)

        onNodeWithTag(RecorderTags.RECORD).assertIsFocused()
    }

    @Test
    fun focusFollowsTheControlsFromCountdownToRecording() = runComposeUiTest {
        // The lens never stops animating, so idling must not chase the frame clock.
        mainClock.autoAdvance = false
        setContent { IntUiTheme(isDark = true) { RecorderPill() } }
        onNodeWithTag(RecorderTags.DOCK_RECORD)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        mainClock.advanceTimeBy(1_000)

        onNodeWithTag(RecorderTags.RECORD).performClick()
        // The countdown runs on wall time. Step the frame clock alongside it until it ends.
        val deadline = System.currentTimeMillis() + COUNTDOWN_MS + 500
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            mainClock.advanceTimeBy(50)
        }

        onNodeWithTag(RecorderTags.STOP).assertIsFocused()
    }
}
