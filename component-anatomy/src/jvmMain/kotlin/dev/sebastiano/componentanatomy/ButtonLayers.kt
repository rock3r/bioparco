package dev.sebastiano.componentanatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.ButtonState
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.focusOutline

/**
 * The drawing layers of a Jewel button, bottom to top, in the order Jewel really paints them.
 *
 * `ButtonImpl` chains `.background().focusOutline().border()` on one box. A background draws before
 * the box's content, but both borders draw *after* it. So the label sits under the border, and the
 * focus outline goes on top of everything. `ButtonParityTest` fails if this order is wrong.
 */
enum class ButtonPart(val title: String) {
    Background("Background"),
    Label("Label"),
    Border("Border"),
    FocusOutline("Focus outline"),
}

/** A style value, with the [ButtonColors] property it came from. */
@Immutable data class Token<T>(val property: String, val value: T)

/** Everything a button draws in one state, and where each value comes from. */
@Immutable
data class ButtonLook(
    val background: Token<Brush>,
    val border: Token<Brush>,
    val content: Token<Color>,
    val focused: Boolean,
)

fun AnatomyState.toButtonState(): ButtonState =
    when (this) {
        AnatomyState.Normal -> ButtonState.of()
        AnatomyState.Hovered -> ButtonState.of(hovered = true)
        AnatomyState.Pressed -> ButtonState.of(hovered = true, pressed = true)
        AnatomyState.Focused -> ButtonState.of(focused = true)
        AnatomyState.Disabled -> ButtonState.of(enabled = false)
    }

/**
 * Picks the same values as `ButtonColors.backgroundFor`, `borderFor` and `contentFor`, but also
 * says which property each value came from. The rules are Jewel 0.41's, outside Swing compatibility
 * mode (the standalone theme never uses it). Note that the border prefers focus over press and
 * hover, while the background and content prefer press and hover over focus.
 *
 * `ButtonParityTest` checks that a button drawn from this look matches the real one.
 */
fun ButtonColors.lookFor(state: ButtonState): ButtonLook {
    val fillKey =
        when {
            !state.isEnabled -> "Disabled"
            state.isPressed -> "Pressed"
            state.isHovered -> "Hovered"
            state.isFocused -> "Focused"
            else -> ""
        }
    val borderKey =
        when {
            !state.isEnabled -> "Disabled"
            state.isFocused -> "Focused"
            state.isPressed -> "Pressed"
            state.isHovered -> "Hovered"
            else -> ""
        }
    return ButtonLook(
        background = Token("background$fillKey", backgroundFor(fillKey)),
        border = Token("border$borderKey", borderFor(borderKey)),
        content = Token("content$fillKey", contentFor(fillKey)),
        focused = state.isFocused,
    )
}

private fun ButtonColors.backgroundFor(key: String): Brush =
    when (key) {
        "Disabled" -> backgroundDisabled
        "Pressed" -> backgroundPressed
        "Hovered" -> backgroundHovered
        "Focused" -> backgroundFocused
        else -> background
    }

private fun ButtonColors.borderFor(key: String): Brush =
    when (key) {
        "Disabled" -> borderDisabled
        "Pressed" -> borderPressed
        "Hovered" -> borderHovered
        "Focused" -> borderFocused
        else -> border
    }

private fun ButtonColors.contentFor(key: String): Color =
    when (key) {
        "Disabled" -> contentDisabled
        "Pressed" -> contentPressed
        "Hovered" -> contentHovered
        "Focused" -> contentFocused
        else -> content
    }

/** The paint for one frame. The stage animates these; the parity test uses them as they are. */
@Immutable
data class ButtonPaint(
    val background: Brush,
    val border: Brush,
    val content: Color,
    val focusOutlineAlpha: Float,
)

fun ButtonLook.toPaint(): ButtonPaint =
    ButtonPaint(
        background = background.value,
        border = border.value,
        content = content.value,
        focusOutlineAlpha = if (focused) 1f else 0f,
    )

/**
 * One layer of the button: the same box and row as Jewel's `ButtonImpl`, with only [part]'s step of
 * the modifier chain. Every layer lays out the label, so all layers have the button's exact size;
 * only the [ButtonPart.Label] layer lets you see it.
 */
@Composable
fun ButtonLayer(
    part: ButtonPart,
    paint: ButtonPaint,
    style: ButtonStyle,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val step =
        when (part) {
            ButtonPart.Background -> Modifier.background(paint.background, shape)
            ButtonPart.FocusOutline ->
                // No alpha here: a graphics layer would clip the halo, which draws outside the
                // button. The stage fades this layer as a whole instead.
                Modifier.focusOutline(
                    showOutline = paint.focusOutlineAlpha > 0f,
                    outlineShape = shape,
                    alignment = style.focusOutlineAlignment,
                    expand = style.metrics.focusOutlineExpand,
                )
            ButtonPart.Border ->
                Modifier.border(
                    Stroke.Alignment.Inside,
                    style.metrics.borderWidth,
                    paint.border,
                    shape,
                )
            ButtonPart.Label -> Modifier
        }
    Box(modifier = modifier.then(step), propagateMinConstraints = true) {
        val contentColor = paint.content.takeOrElse { textStyle.color }
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides textStyle.copy(color = contentColor),
        ) {
            Row(
                Modifier.defaultMinSize(style.metrics.minSize.width)
                    .height(style.metrics.minSize.height),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val visibility = if (part == ButtonPart.Label) Modifier else Modifier.alpha(0f)
                Box(Modifier.padding(style.metrics.padding).then(visibility)) { content() }
            }
        }
    }
}

/** All layers stacked flat: what the real button looks like. */
@Composable
fun CollapsedButton(
    paint: ButtonPaint,
    style: ButtonStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        ButtonPart.entries.forEach { part -> ButtonLayer(part, paint, style, content = content) }
    }
}
