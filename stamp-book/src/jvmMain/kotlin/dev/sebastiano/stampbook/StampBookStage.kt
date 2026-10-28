package dev.sebastiano.stampbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The table, the book and the swatches. The book opens while the pointer is over it (or after a
 * click or Space, for touch and keyboards) and recolours from the dots underneath or with the arrow
 * keys.
 */
@Composable
internal fun StampBookStage(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val art by
        produceState<BookArt?>(null, density.density) {
            value =
                withContext(Dispatchers.Default) {
                    Tracing.section("bake art") { bakeBookArt(density.density) }
                }
        }
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { StampBookMotion(scope) }
    LaunchedEffect(motion) { motion.warmUp() }
    val latch = remember(density) { HoverLatch(Book.opener(density), Book.keeper(density)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val keys = remember { KeyRepeatFilter() }

    fun toggle() {
        latch.toggle()
        motion.show(latch.isOpen)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(StampBookPalette.Table)
            .testTag(StampBookTags.STAGE)
            .onPreviewKeyEvent { event ->
                val code = event.key.keyCode
                when (event.key) {
                    Key.Spacebar,
                    Key.Enter -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> if (keys.press(code)) toggle()
                            KeyEventType.KeyUp -> keys.release(code)
                        }
                        true
                    }
                    Key.DirectionLeft,
                    Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown) {
                            val step = if (event.key == Key.DirectionLeft) -1 else 1
                            val count = StampBookPalette.inks.size
                            motion.pick(cycled(motion.swatch, step, count))
                        }
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { if (!it.hasFocus) keys.reset() }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(Book.Content).pointerInput(latch, motion) {
                awaitPointerEventScope {
                    var pressedOnBook = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val (x, y) = change.position
                        when (event.type) {
                            PointerEventType.Enter,
                            PointerEventType.Move -> latch.move(x, y)
                            PointerEventType.Exit -> latch.leave()
                            PointerEventType.Press ->
                                pressedOnBook = !change.isConsumed && latch.opener.contains(x, y)
                            PointerEventType.Release -> {
                                if (pressedOnBook && latch.keeper.contains(x, y)) latch.toggle()
                                pressedOnBook = false
                            }
                        }
                        motion.show(latch.isOpen)
                    }
                }
            }
        ) {
            art?.let {
                BookLayers(
                    it,
                    motion,
                    density,
                    onToggle = ::toggle,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Swatches(motion)
        }
    }
}

/** Back board, the two stamps, then the cover on top. */
@Composable
private fun BookLayers(
    art: BookArt,
    motion: StampBookMotion,
    density: Density,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val origin = Book.FolderOrigin
    val label = if (motion.open) "Close the stamp book" else "Open the stamp book"
    val name = StampBookPalette.inks[motion.swatch].name
    Box(modifier) {
        Box(
            Modifier.offset(origin.x + Book.BackShift.x, origin.y + Book.BackShift.y)
                .size(Book.Folder)
                .drawBehind { Tracing.section("back board") { drawBoard(art, motion) } }
        )
        Box(
            Modifier.stampPlacement(Book.StampAClosed)
                .size(Book.StampA)
                .testTag(StampBookTags.STAMP_A)
                .graphicsLayer { slide(Book.StampAClosed, Book.StampAOpen, motion.slideA.value) }
                .drawBehind {
                    Tracing.section("quiet stamp") { drawStampA(art, motion.cover.value) }
                }
        )
        // The bird stamp's shadow stays square to the table, outside the stamp's turning layer, so
        // copying it costs no filtering. It is soft enough that the few degrees never show.
        Box(
            Modifier.stampPlacement(Book.StampBClosed)
                .size(Book.StampB)
                .graphicsLayer { slide(Book.StampBClosed, Book.StampBOpen, motion.slideB.value) }
                .drawBehind {
                    Tracing.section("bird shadow") {
                        drawStampBShadow(art, lift(motion.slideB.value))
                    }
                }
        )
        Box(
            Modifier.stampPlacement(Book.StampBClosed)
                .size(Book.StampB)
                .testTag(StampBookTags.STAMP_B)
                .graphicsLayer {
                    val slide = motion.slideB.value
                    slide(Book.StampBClosed, Book.StampBOpen, slide)
                    // Tucked in and still, the stamp is drawn already turned (below), so the layer
                    // stays square and the copy needs no filtering.
                    if (!motion.birdTucked) {
                        rotationZ = stampBTurn(slide, motion.slideB.velocity)
                        scaleX = 1f + LIFT_SCALE * lift(slide)
                        scaleY = scaleX
                    }
                }
                .drawBehind {
                    Tracing.section("bird stamp") {
                        if (motion.birdTucked)
                            drawPlaced(art.stampBTucked.image, art.stampBTucked.at)
                        else drawPlaced(art.stampB, AtOrigin)
                    }
                }
        )
        Box(
            Modifier.offset(origin.x, origin.y)
                .size(Book.Folder)
                .pointerHoverIcon(PointerIcon.Hand)
                .testTag(StampBookTags.FOLDER)
                .semantics {
                    role = Role.Button
                    contentDescription = "Stamp book, $name"
                    stateDescription = if (motion.open) "Open" else "Closed"
                    onClick(label) {
                        onToggle()
                        true
                    }
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = coverTurn(motion.cover.value)
                    cameraDistance = CAMERA_DISTANCE * density.density
                }
                .drawBehind { Tracing.section("cover") { drawCover(art, motion) } }
        )
    }
}

/**
 * Moves a stamp [fraction] of the way from [closed] to [open], in whole pixels: an image on a pixel
 * boundary is a plain copy, while a fractional one is resampled, which cost about a millisecond a
 * frame for the pale stamp in software.
 */
private fun GraphicsLayerScope.slide(closed: DpOffset, open: DpOffset, fraction: Float) {
    translationX = ((open.x - closed.x).toPx() * fraction).roundToInt().toFloat()
    translationY = ((open.y - closed.y).toPx() * fraction).roundToInt().toFloat()
}

private val AtOrigin = DpOffset(0.dp, 0.dp)

@Composable
private fun Swatches(motion: StampBookMotion, modifier: Modifier = Modifier) {
    val inks = StampBookPalette.inks
    val step = Book.SwatchSize + Book.SwatchGap
    val width = step * inks.size - Book.SwatchGap
    // Centred under the book's board, as in the video.
    val centreX = Book.FolderOrigin.x + (Book.Folder.width + Book.BackShift.x) / 2
    Box(
        modifier
            .offset(centreX - width / 2, Book.SwatchCentreY - Book.SwatchSize / 2)
            .size(width, Book.SwatchSize)
    ) {
        inks.forEachIndexed { index, ink ->
            val selected = motion.swatch == index
            Box(
                Modifier.offset(x = step * index)
                    .size(Book.SwatchSize)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClickLabel = "Make the book ${ink.name}",
                    ) {
                        motion.pick(index)
                    }
                    .semantics {
                        contentDescription = ink.name
                        this.selected = selected
                    }
                    .testTag(StampBookTags.swatch(index))
                    .drawBehind { drawSwatch(ink.swatch, selected) }
            )
        }
    }
}

private fun Modifier.stampPlacement(closed: DpOffset): Modifier =
    offset(Book.FolderOrigin.x + closed.x, Book.FolderOrigin.y + closed.y)

/** How far the bird stamp has lifted off the board: most of the way through its slide. */
internal fun lift(slide: Float): Float = (4f * slide * (1f - slide)).coerceIn(0f, 1f)

/** The bird stamp straightens as it slides out, and leans into its own speed. */
internal fun stampBTurn(slide: Float, velocity: Float): Float =
    Book.B_CLOSED_TURN + (Book.B_OPEN_TURN - Book.B_CLOSED_TURN) * slide -
        (velocity * LEAN_PER_SPEED).coerceIn(-MAX_LEAN, MAX_LEAN)

internal fun coverTurn(open: Float): Float =
    Book.COVER_CLOSED_TURN + (Book.COVER_OPEN_TURN - Book.COVER_CLOSED_TURN) * open

/** A lifted stamp grows a little, as if nearer. */
private const val LIFT_SCALE = 0.035f

/** Degrees of lean per unit of slide per second, and the most it ever leans. */
private const val LEAN_PER_SPEED = 2.2f

private const val MAX_LEAN = 5f

/**
 * Well back from the cover. Its free edge swings towards the viewer, and a close camera blows that
 * edge up; the video shows only a mild perspective.
 */
private const val CAMERA_DISTANCE = 24f
