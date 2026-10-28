package dev.sebastiano.layoutmorph

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement as Spacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sign
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun LayoutMorphStage(
    modifier: Modifier = Modifier,
    motion: MorphMotion = remember { MorphMotion(ShopCollection.pieces) },
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(motion) { motion.run() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .background(MorphPalette.background)
            .testTag(LayoutMorphTags.STAGE)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = DIGITS.indexOf(event.key)
                when {
                    digit >= 0 -> motion.select(Arrangement.entries[digit])
                    event.key == Key.DirectionRight -> motion.nudge(1)
                    event.key == Key.DirectionLeft -> motion.nudge(-1)
                    else -> return@onKeyEvent false
                }
                true
            }
            .padding(horizontal = 32.dp, vertical = 22.dp)
    ) {
        Header()
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switcher(motion.arrangement, motion::select)
            Spacer(Modifier.weight(1f))
            Text(motion.arrangement.hint, color = MorphPalette.faint, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        CardField(motion, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Footer(motion.detail?.let { motion.pieces[it] }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Header(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "One collection, six\nways to browse",
            color = MorphPalette.ink,
            fontSize = 27.sp,
            lineHeight = 30.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The ${ShopCollection.NAME} autumn edit: ${ShopCollection.pieceCount} pieces from " +
                "${ShopCollection.makerCount} makers. Switch the view and every card travels to its new place.",
            color = MorphPalette.muted,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

/**
 * Every card is a recorded graphics layer, its face drawn once at the card's size. One draw pass
 * lays the layers down, deepest first, so the order is right on every frame even while the helix
 * spins. Moving, turning and fading a card only changes its layer's properties; its face is
 * recorded again only when its [CardLook] changes. Nothing here recomposes: [MorphMotion.tick] is
 * read while drawing.
 *
 * Layers keep the default compositing: a fading card is drawn offscreen at its own size and blended
 * in. `ModulateAlpha` would skip that, but on desktop it let one layer's alpha leak into the layers
 * drawn after it, and the helix came out washed out.
 */
@Composable
private fun CardField(motion: MorphMotion, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = JewelTheme.defaultTextStyle
    val texts =
        remember(measurer, style, density) {
            motion.pieces.map { CardText.measure(it, measurer, style, density.density) }
        }
    val layers = List(motion.pieces.size) { rememberGraphicsLayer() }
    val recorded = remember(texts) { arrayOfNulls<CardLook>(motion.pieces.size) }
    Spacer(
        modifier
            .testTag(LayoutMorphTags.FIELD)
            .clipToBounds()
            .onSizeChanged {
                motion.resize(Field(it.width / density.density, it.height / density.density))
            }
            .pointerInput(motion) { trackPointer(motion) }
            .drawBehind {
                motion.tick.longValue
                Tracing.section("cards") {
                    val poses = motion.engine.poses
                    // Faces first, then every layer, deepest first.
                    for (i in poses.indices) {
                        val look = CardLook.of(poses[i])
                        if (look == recorded[i]) continue
                        recorded[i] = look
                        // Room around the card for its shadow.
                        val margin = (CARD_MARGIN * 2).dp.toPx()
                        val size =
                            IntSize(
                                ceil(look.width.dp.toPx() + margin).toInt(),
                                ceil(look.height.dp.toPx() + margin).toInt(),
                            )
                        Tracing.section("card face") {
                            layers[i].record(this, layoutDirection, size) {
                                drawCard(motion.pieces[i], texts[i], look)
                            }
                        }
                    }
                    for (i in motion.engine.drawOrder) {
                        val pose = poses[i]
                        val layer = layers[i]
                        layer.translationX = pose.x.dp.toPx() - layer.size.width / 2f
                        layer.translationY = pose.y.dp.toPx() - layer.size.height / 2f
                        layer.rotationZ = pose.rotation
                        // A turn narrows the card, without perspective: see [CardField].
                        layer.scaleX = pose.scale * abs(cos(pose.turn.toRadians()))
                        layer.scaleY = pose.scale
                        layer.alpha = pose.alpha
                        drawLayer(layer)
                    }
                }
            }
    )
}

/**
 * Hover picks the card under the pointer. In the carousel and the helix a horizontal drag slides or
 * spins them and a release flings them; anywhere, a press that doesn't drag is a click.
 */
private suspend fun PointerInputScope.trackPointer(motion: MorphMotion) {
    val slop = viewConfiguration.touchSlop
    awaitPointerEventScope {
        var down: Offset? = null
        var last = Offset.Zero
        var dragging = false
        val tracker = VelocityTracker()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.first()
            val at = change.position
            when (event.type) {
                PointerEventType.Press -> {
                    down = at
                    last = at
                    dragging = false
                    tracker.resetTracking()
                    tracker.addPosition(change.uptimeMillis, at)
                }
                PointerEventType.Move -> {
                    val start = down
                    motion.pointerAt(at.x / density, at.y / density)
                    if (start != null) {
                        tracker.addPosition(change.uptimeMillis, at)
                        if (!dragging && motion.canDrag() && abs(at.x - start.x) > slop) {
                            dragging = true
                        }
                        if (dragging) motion.drag((at.x - last.x) / density)
                        last = at
                    }
                }
                PointerEventType.Release -> {
                    val start = down
                    down = null
                    if (dragging) {
                        motion.release(tracker.calculateVelocity().x / density)
                    } else if (start != null) {
                        motion.click(at.x / density, at.y / density)
                    }
                    dragging = false
                }
                PointerEventType.Exit -> if (down == null) motion.pointerAt(null, null)
                PointerEventType.Scroll -> {
                    val delta = change.scrollDelta
                    val step = if (abs(delta.x) > abs(delta.y)) delta.x else delta.y
                    if (step != 0f) motion.nudge(sign(step).toInt())
                    change.consume()
                }
            }
        }
    }
}

@Composable
private fun Footer(piece: Piece?, modifier: Modifier = Modifier) {
    val stats: List<Pair<String, String>>
    val title: String
    val sub: String
    when {
        piece == null -> {
            title = "Autumn edit"
            sub = "Hover a card for its details"
            stats =
                listOf(
                    "Pieces" to ShopCollection.pieceCount.toString(),
                    "Makers" to ShopCollection.makerCount.toString(),
                    "Avg rating" to ShopCollection.averageRating.toString(),
                )
        }
        piece.isMaker -> {
            title = piece.title
            sub = piece.detail
            stats =
                listOf(
                    "Craft" to piece.detail.substringBefore(" · "),
                    "Based in" to piece.detail.substringAfter(" · "),
                    "In this edit" to piece.trailing,
                )
        }
        else -> {
            title = piece.title
            sub = "By " + piece.detail.substringBefore(" · ")
            stats =
                listOf(
                    "Price" to piece.trailing,
                    "In stock" to piece.stock.toString(),
                    "Rating" to piece.rating.toString(),
                )
        }
    }
    Row(modifier.height(44.dp), horizontalArrangement = Spacing.spacedBy(12.dp)) {
        Column(Modifier.weight(1.6f)) {
            Text(
                title,
                color = MorphPalette.ink,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(LayoutMorphTags.DETAIL),
            )
            Text(sub, color = MorphPalette.muted, fontSize = 10.sp)
        }
        for ((label, value) in stats) {
            Column(
                Modifier.weight(1f)
                    .drawBehind {
                        drawLine(
                            MorphPalette.hairline,
                            Offset.Zero,
                            Offset(0f, size.height),
                            1.dp.toPx(),
                        )
                    }
                    .padding(start = 10.dp)
            ) {
                Text(label, color = MorphPalette.muted, fontSize = 9.5.sp)
                Text(
                    value,
                    color = MorphPalette.ink,
                    style =
                        JewelTheme.defaultTextStyle.copy(
                            fontSize = 15.sp,
                            fontFeatureSettings = "tnum",
                        ),
                )
            }
        }
    }
}

private val DIGITS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)

/** Room around a card's layer for its shadow, in dp on each side. */
private const val CARD_MARGIN = 4f
