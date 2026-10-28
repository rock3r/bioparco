package dev.sebastiano.emailinvite

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun EmailInviteStage(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { InviteMotion(scope) }
    LaunchedEffect(motion) { motion.run() }
    // Plays the whole flow once off screen, so the first real open does not run cold code.
    LaunchedEffect(Unit) { rehearse() }
    InviteStage(motion, modifier)
}

/** The stage around [motion]'s pill: keys, focus and click-away. The rehearsal drives one too. */
@Composable
internal fun InviteStage(motion: InviteMotion, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val geometry =
        remember(density) {
            with(density) { PillGeometry(density, BOX_WIDTH.toPx() / 2f, BOX_HEIGHT.toPx() / 2f) }
        }
    val stageFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keys = remember { HeldKeys() }
    val phase = motion.phase
    LaunchedEffect(phase) {
        // The field takes the keyboard while editing; otherwise the stage does, for Enter and Esc.
        if (phase == InvitePhase.Editing) fieldFocus.requestFocus() else stageFocus.requestFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(EmailInvitePalette.stage)
            .testTag(EmailInviteTags.STAGE)
            .onPreviewKeyEvent { event -> handleKey(event, motion, keys) }
            .onFocusChanged { if (!it.hasFocus) keys.reset() }
            .focusRequester(stageFocus)
            .focusable()
            // A click anywhere off the pill closes it, as clicking away from a popover does.
            .pointerInput(motion, geometry) {
                detectTapGestures { tap ->
                    val open = motion.open.value
                    val tuck = motion.tuck.value
                    val slots = motion.slots.value
                    val outside =
                        abs(tap.x - size.width / 2f) > geometry.pillWidth(open, tuck, slots) / 2f ||
                            abs(tap.y - size.height / 2f) > geometry.pillHeight / 2f
                    if (outside) motion.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        InvitePill(motion, geometry, fieldFocus)
    }
}

private fun handleKey(event: KeyEvent, motion: InviteMotion, keys: HeldKeys): Boolean {
    val code = event.key.keyCode
    val fresh = event.type == KeyEventType.KeyDown && keys.press(code)
    if (event.type == KeyEventType.KeyUp) keys.release(code)
    return when (event.key) {
        Key.Enter,
        Key.NumPadEnter -> {
            if (fresh) {
                when (motion.phase) {
                    InvitePhase.Collapsed -> motion.open()
                    InvitePhase.Editing -> motion.submit()
                    InvitePhase.Sent -> Unit
                }
            }
            true
        }
        Key.Escape -> {
            if (fresh) motion.cancel()
            true
        }
        // Space opens the button, but is a character while typing.
        Key.Spacebar -> {
            val collapsed = motion.phase == InvitePhase.Collapsed
            if (fresh && collapsed) motion.open()
            collapsed
        }
        else -> false
    }
}

/** The whole pill in a fixed box; everything inside moves in draw and graphics layers only. */
@Composable
private fun InvitePill(
    motion: InviteMotion,
    geometry: PillGeometry,
    fieldFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shadow = remember(density, geometry) { PillShadow(density, geometry.pillHeight) }
    Box(
        modifier.size(BOX_WIDTH, BOX_HEIGHT).drawBehind {
            Tracing.section("invite pill") { drawPill(motion, geometry, shadow) }
        }
    ) {
        InviteLabel(motion, geometry)
        Placeholder(motion, geometry)
        AddressField(motion, geometry, fieldFocus)
        SendButton(motion, geometry)
        InviteButton(motion, geometry)
        val guests = motion.guests
        guests.forEachIndexed { index, guest ->
            androidx.compose.runtime.key(guest.id) {
                Avatar(guest, motion, geometry, Modifier.zIndex(-index.toFloat()))
            }
        }
    }
}

/** The click target over the collapsed "+ Invite" button; the pill draws the button itself. */
@Composable
private fun InviteButton(motion: InviteMotion, geometry: PillGeometry) {
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            when (it) {
                is HoverInteraction.Enter -> motion.hovering(true)
                is HoverInteraction.Exit -> motion.hovering(false)
            }
        }
    }
    val collapsed = motion.phase == InvitePhase.Collapsed
    Box(
        Modifier.size(BUTTON_WIDTH, FIELD_HEIGHT)
            .graphicsLayer {
                translationX =
                    geometry.fieldLeft(motion.open.value, motion.tuck.value, motion.slots.value)
                translationY = geometry.centreY - size.height / 2f
            }
            // Only a live target hit-tests: open, this spot is the field's, for placing the caret.
            .then(
                if (collapsed) {
                    Modifier.hoverable(interactions)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactions,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Invite",
                            onClick = motion::open,
                        )
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = "Invite" }
            .testTag(EmailInviteTags.INVITE)
    )
}

/** Where the button's label, the placeholder and the typed address all start. */
private fun textLeft(motion: InviteMotion, geometry: PillGeometry): Float =
    geometry.fieldLeft(motion.open.value, motion.tuck.value, motion.slots.value) +
        geometry.textStart

/** "Invite" on the button, which greys into the placeholder's first word as the field opens. */
@Composable
private fun InviteLabel(motion: InviteMotion, geometry: PillGeometry) {
    Text(
        "Invite",
        color = EmailInvitePalette.ink,
        style =
            JewelTheme.defaultTextStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        modifier =
            Modifier.graphicsLayer {
                translationX = textLeft(motion, geometry)
                translationY = geometry.centreY - size.height / 2f
                alpha = 1f - motion.label.value
            },
    )
}

/** The field's "Invite by email", whose last two words sharpen in after the first. */
@Composable
private fun Placeholder(motion: InviteMotion, geometry: PillGeometry) {
    val style = JewelTheme.defaultTextStyle
    Row(
        Modifier.graphicsLayer {
            translationX = textLeft(motion, geometry)
            translationY = geometry.centreY - size.height / 2f
            // The placeholder only shows while the field is empty.
            alpha = if (motion.address.text.isEmpty()) motion.label.value else 0f
        }
    ) {
        val placeholder = style.copy(fontSize = 15.sp, color = EmailInvitePalette.placeholder)
        Text("Invite", style = placeholder)
        Text(
            " by email",
            style = placeholder,
            modifier =
                Modifier.bleed(BLUR_ROOM)
                    .graphicsLayer {
                        val tail = motion.tail.value
                        alpha = tail
                        translationX = -(1f - tail) * 4.dp.toPx()
                        blur(this, (1f - tail) * 5.dp.toPx())
                    }
                    .padding(BLUR_ROOM),
        )
    }
}

@Composable
private fun AddressField(motion: InviteMotion, geometry: PillGeometry, focus: FocusRequester) {
    val editing = motion.phase == InvitePhase.Editing
    BasicTextField(
        state = motion.address,
        enabled = editing,
        inputTransformation = InputTransformation.maxLength(MAX_ADDRESS),
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle =
            JewelTheme.defaultTextStyle.copy(fontSize = 15.sp, color = EmailInvitePalette.ink),
        cursorBrush = SolidColor(EmailInvitePalette.caret),
        // Padding inside the layer gives the blur room to spread.
        decorator = { field -> Box(Modifier.padding(BLUR_ROOM)) { field() } },
        modifier =
            Modifier.width(FIELD_TEXT_WIDTH + BLUR_ROOM * 2)
                .graphicsLayer {
                    translationX = textLeft(motion, geometry) - BLUR_ROOM.toPx()
                    translationY = geometry.centreY - size.height / 2f
                    val dissolve = motion.dissolve.value
                    alpha = 1f - dissolve
                    blur(this, dissolve * 8.dp.toPx())
                }
                .focusRequester(focus)
                .pointerHoverIcon(PointerIcon.Text)
                .testTag(EmailInviteTags.FIELD),
    )
}

@Composable
private fun SendButton(motion: InviteMotion, geometry: PillGeometry) {
    val editing = motion.phase == InvitePhase.Editing
    val interactions = remember { MutableInteractionSource() }
    // The layer is wider than the button so its blur has room to spread.
    Box(
        Modifier.size(SEND_WIDTH + BLUR_ROOM * 2, SEND_HEIGHT + BLUR_ROOM * 2).graphicsLayer {
            val open = motion.open.value
            translationX =
                geometry.sendLeft(open, motion.tuck.value, motion.slots.value) - BLUR_ROOM.toPx()
            translationY = geometry.centreY - size.height / 2f
            val reveal = motion.send.value
            alpha = reveal
            val scale = (0.92f + 0.08f * reveal) * (1f - 0.06f * motion.press.value)
            scaleX = scale
            scaleY = scale
            blur(this, (1f - reveal) * 8.dp.toPx())
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(SEND_WIDTH, SEND_HEIGHT)
                .then(
                    if (editing) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = interactions,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Send",
                                onClick = motion::submit,
                            )
                    } else {
                        Modifier
                    }
                )
                .testTag(EmailInviteTags.SEND)
                .drawBehind {
                    val idleToArmed =
                        lerp(
                            EmailInvitePalette.sendIdle,
                            EmailInvitePalette.sendArmed,
                            motion.armed.value,
                        )
                    drawRoundRect(
                        lerp(idleToArmed, EmailInvitePalette.accent, motion.sent.value),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            SendLabel(motion)
        }
    }
}

/** "Sen" stays; its last letter blurs from d to t. */
@Composable
private fun SendLabel(motion: InviteMotion) {
    val style =
        JewelTheme.defaultTextStyle.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = EmailInvitePalette.onAccent,
        )
    Row {
        Text("Sen", style = style)
        Box {
            Text(
                "d",
                style = style,
                modifier =
                    Modifier.bleed(LETTER_ROOM)
                        .graphicsLayer {
                            val sent = motion.sent.value
                            alpha = 1f - sent
                            translationY = -sent * 3.dp.toPx()
                            blur(this, sent * 3.dp.toPx())
                        }
                        .padding(LETTER_ROOM),
            )
            Text(
                "t",
                style = style,
                modifier =
                    Modifier.bleed(LETTER_ROOM)
                        .graphicsLayer {
                            val sent = motion.sent.value
                            alpha = sent
                            translationY = (1f - sent) * 3.dp.toPx()
                            blur(this, (1f - sent) * 3.dp.toPx())
                        }
                        .padding(LETTER_ROOM),
            )
        }
    }
}

@Composable
private fun Avatar(
    guest: Guest,
    motion: InviteMotion,
    geometry: PillGeometry,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val letterStyle =
        JewelTheme.defaultTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val letter =
        remember(guest.initial, letterStyle) {
            measurer.measure(AnnotatedString(guest.initial.toString()), letterStyle)
        }
    Box(
        modifier
            .size(AVATAR_BOX)
            .graphicsLayer { placeAvatar(guest, motion, geometry) }
            .drawBehind {
                Tracing.section("avatar") {
                    val (fill, ink) = tintOf(guest, motion)
                    val radius = geometry.avatar / 2f
                    drawCircle(EmailInvitePalette.pill, radius + geometry.ring)
                    drawCircle(fill, radius)
                    // Only the front avatar keeps its letter when the stack tucks.
                    val slot = motion.slot(guest).coerceIn(0f, 1f)
                    val letterAlpha = 1f - motion.tuck.value * slot
                    drawText(
                        letter,
                        color = ink.copy(alpha = letterAlpha),
                        topLeft = center - Offset(letter.size.width / 2f, letter.size.height / 2f),
                    )
                    if (guest.id == motion.newestId) drawShine(radius, motion.shine.value)
                }
            }
    )
}

private fun GraphicsLayerScope.placeAvatar(
    guest: Guest,
    motion: InviteMotion,
    geometry: PillGeometry,
) {
    val open = motion.open.value
    val tuck = motion.tuck.value
    val slots = motion.slots.value
    val halo = (size.width - geometry.avatar) / 2f
    var x = geometry.avatarLeft(motion.slot(guest), open, tuck, slots) - halo
    var y = geometry.centreY - size.height / 2f
    var scale = 1f
    var fade = 1f
    var blurRadius = 0f
    val hop = motion.hop.value
    if (guest.id == motion.newestId && hop < 1f) {
        // It rises out of the field, arcs over the stack and lands at the front.
        val travel = FastOutSlowInEasing.transform(hop)
        x += lerp(HOP_FROM_X.toPx(), 0f, travel)
        y += lerp(HOP_FROM_Y.toPx(), 0f, travel) - sin(PI.toFloat() * travel) * HOP_ARC.toPx()
        scale = lerp(0.55f, 1f, easeOutBack(hop))
        fade = (hop * 4f).coerceAtMost(1f)
        blurRadius = (1f - travel) * 6.dp.toPx()
    }
    if (guest.id == motion.leavingId) {
        val leave = motion.leave.value
        fade = 1f - leave
        scale = 1f - 0.3f * leave
    }
    translationX = x
    translationY = y
    scaleX = scale
    scaleY = scale
    alpha = fade
    blur(this, blurRadius)
}

private fun tintOf(guest: Guest, motion: InviteMotion): Pair<Color, Color> {
    val accent = EmailInvitePalette.accent to EmailInvitePalette.onAccent
    val invited = EmailInvitePalette.invited
    return when {
        guest.id == motion.newestId -> accent
        guest.invited && guest.id == motion.calmingId -> {
            val calm = motion.calm.value
            lerp(accent.first, invited.first, calm) to lerp(accent.second, invited.second, calm)
        }
        guest.invited -> invited
        else -> EmailInvitePalette.founders[guest.id % EmailInvitePalette.founders.size]
    }
}

/**
 * Grows the layer that follows by [room] on every side without moving or resizing anything, so its
 * blur has space to spread.
 */
private fun Modifier.bleed(room: Dp): Modifier = layout { measurable, constraints ->
    val px = room.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * px, 2 * px))
    layout(placeable.width - 2 * px, placeable.height - 2 * px) { placeable.place(-px, -px) }
}

/**
 * Blurs a layer, or turns the blur off entirely once it is too small to see. A blurred layer clips
 * to its bounds: unclipped, Skia blurred an offscreen the size of the whole window, which tripled
 * the cost of a software frame. Each blurred layer leaves room around its content for the spread.
 */
private fun blur(layer: GraphicsLayerScope, radius: Float) {
    val blurring = radius >= MIN_BLUR
    layer.renderEffect = if (blurring) BlurEffect(radius, radius, TileMode.Decal) else null
    layer.clip = blurring
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

private val BOX_WIDTH = 440.dp
private val BOX_HEIGHT = 150.dp
private val BUTTON_WIDTH = 100.dp
private val FIELD_HEIGHT = 47.dp
private val SEND_WIDTH = 64.dp
private val SEND_HEIGHT = 35.dp
private val FIELD_TEXT_WIDTH = 140.dp
private val AVATAR_BOX = 35.dp + 2.5.dp * 2 + 16.dp
private val BLUR_ROOM: Dp = 8.dp
private val LETTER_ROOM: Dp = 4.dp
private val HOP_FROM_X = 72.dp
private val HOP_FROM_Y = (-20).dp
private val HOP_ARC = 12.dp
private const val MIN_BLUR = 0.5f
private const val MAX_ADDRESS = 64
