package dev.sebastiano.anatomy.kit

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * Takes keyboard focus when the figure is pressed, so its keys work after a click, as a web page's
 * focusable SVG does. Put it before `focusable()`, which it focuses.
 */
fun Modifier.focusOnPress(): Modifier = this then FocusOnPressElement

private data object FocusOnPressElement : ModifierNodeElement<FocusOnPressNode>() {
    override fun create() = FocusOnPressNode()

    override fun update(node: FocusOnPressNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "focusOnPress"
    }
}

private class FocusOnPressNode :
    Modifier.Node(), PointerInputModifierNode, FocusRequesterModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press) {
            requestFocus()
        }
    }

    override fun onCancelPointerInput() = Unit
}
