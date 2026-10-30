package dev.sebastiano.knockoutbadge

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.Tracing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * The table: the badge in the middle, a line of help under it. The badge's layers are drawn in
 * their own frames, so taking it apart only changes where each frame sits. While it is together the
 * sticker (black plus white ink) multiplies the footage beneath; apart, it fades to lying over it,
 * so the ink still shows where nothing is under it.
 */
@Composable
internal fun KnockoutBadgeStage(modifier: Modifier = Modifier) {
    val motion = remember { BadgeMotion() }
    val footage = remember { ClubFootage() }
    DisposableEffect(footage) { onDispose { footage.close() } }
    val editor = remember { WordEditor(BadgeSpec.WORD) }
    var word by remember { mutableStateOf(editor.word) }
    val measurer = rememberTextMeasurer()
    val fontFamily = JewelTheme.defaultTextStyle.fontFamily ?: FontFamily.Default
    val codeFamily = JewelTheme.editorTextStyle.fontFamily ?: FontFamily.Monospace
    val focus = remember { FocusRequester() }
    LaunchedEffect(motion) { motion.run() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BoxWithConstraints(
        modifier
            .background(Table)
            .testTag(BadgeTags.STAGE)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val now = System.currentTimeMillis()
                when (event.key) {
                    Key.Spacebar,
                    Key.Enter -> motion.toggleApart()
                    Key.Backspace -> if (editor.erase(now)) word = editor.word
                    else -> {
                        val code = event.utf16CodePoint
                        if (code <= 0 || code >= Char.MAX_VALUE.code) return@onPreviewKeyEvent false
                        if (!editor.type(code.toChar(), now)) return@onPreviewKeyEvent false
                        word = editor.word
                    }
                }
                true
            }
            .pointerInput(motion) { handlePointer(motion) }
    ) {
        val stageHeight = maxHeight - CAPTION_ROOM
        val badgeLeft = (maxWidth - BadgeSpec.WIDTH) / 2
        val badgeTop = (stageHeight - BadgeSpec.HEIGHT) / 2

        Box(
            Modifier.fillMaxSize().drawWithCache {
                val grid = tableGrid(size.width, size.height, GRID.toPx())
                val shadow = BadgeShadow(this)
                onDrawBehind {
                    drawPath(grid, GridLine)
                    shadow.draw(
                        this,
                        stageCentre(this, 0f),
                        1f - (motion.explode * SHADOW_FADE).coerceIn(0f, 1f),
                    )
                }
            }
        )

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val centre = stageCentre(this, 0f)
                    rotationX = motion.tiltX
                    rotationY = motion.tiltY
                    cameraDistance = CAMERA_DISTANCE * density
                    transformOrigin = TransformOrigin(centre.x / size.width, centre.y / size.height)
                }
                .drawWithCache {
                    val art = BadgeArt(this, measurer, fontFamily)
                    var ink: WordInk? = null
                    var primed = 0
                    onDrawBehind {
                        // Typing lays out the word alone, here, rather than the whole badge.
                        val shown = word
                        val current =
                            ink?.takeIf { it.text == shown } ?: art.ink(shown).also { ink = it }
                        val prime = primed < PRIME_FRAMES
                        if (prime) primed++
                        drawBadge(art, current, motion, footage, prime)
                    }
                }
        )

        Box(
            Modifier.fillMaxSize().drawWithCache {
                val notes = Annotations(this, measurer, fontFamily, codeFamily)
                onDrawBehind {
                    val shown = ((motion.explode - NOTES_FROM) / (1f - NOTES_FROM)).coerceIn(0f, 1f)
                    // Before they first show, the notes draw at 1/255 alpha, so their glyphs are
                    // rasterised before the badge comes apart rather than on its first frame.
                    if (shown > 0f || notes.needsPriming()) {
                        Tracing.section("notes") {
                            notes.draw(this, shown) { layer, local ->
                                layerMatrix(this, layer, motion.explode).map(local)
                            }
                        }
                    }
                }
            }
        )

        // Nodes for Spectre: where the badge and its corner are, and what state it is in.
        Box(
            Modifier.offset(badgeLeft, badgeTop)
                .size(BadgeSpec.WIDTH, BadgeSpec.HEIGHT)
                .testTag(BadgeTags.BADGE)
        )
        Box(
            Modifier.offset(badgeLeft + BadgeSpec.WIDTH - CORNER_NODE, badgeTop)
                .size(CORNER_NODE)
                .testTag(BadgeTags.CORNER)
        )
        val state = if (motion.isApart) BadgeTags.apart(word) else BadgeTags.together(word)
        Box(
            Modifier.size(1.dp).testTag(BadgeTags.STATE).clearAndSetSemantics {
                text = AnnotatedString(state)
            }
        )

        Text(
            "Hover to tilt. Drag the corner to peel. Click or Space to take it apart. Type a new word",
            color = Caption,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        )
    }
}

/** Hover tilts the badge, the corner peels, and a click anywhere else takes it apart. */
private suspend fun PointerInputScope.handlePointer(motion: BadgeMotion) {
    awaitPointerEventScope {
        var pressedAt: Offset? = null
        var grabOffset = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            val centre = stageCentre(this, 0f)
            val halfWidth = BadgeSpec.WIDTH.toPx() / 2f
            val halfHeight = BadgeSpec.HEIGHT.toPx() / 2f
            // In dp from the badge's top right corner.
            val fromCorner =
                (change.position - Offset(centre.x + halfWidth, centre.y - halfHeight)) / density
            when (event.type) {
                PointerEventType.Press -> {
                    pressedAt = change.position
                    val grip = Offset(motion.gripX, motion.gripY)
                    if (
                        motion.canPeel &&
                            (fromCorner - grip).getDistance() < BadgeSpec.CORNER_GRAB.value
                    ) {
                        grabOffset = fromCorner - grip
                        motion.peelTo(grip.x, grip.y, PEEL_REACH)
                    }
                }
                PointerEventType.Enter,
                PointerEventType.Move -> {
                    if (motion.isPeeling) {
                        val at = fromCorner - grabOffset
                        motion.peelTo(at.x, at.y, PEEL_REACH)
                    } else if (pressedAt == null) {
                        val offset = change.position - centre
                        motion.hover(offset.x, offset.y, halfWidth, halfHeight)
                    }
                }
                PointerEventType.Release -> {
                    val from = pressedAt
                    pressedAt = null
                    if (motion.isPeeling) {
                        motion.letGo()
                    } else if (
                        from != null && (change.position - from).getDistance() < CLICK_SLOP.toPx()
                    ) {
                        motion.toggleApart()
                    }
                }
                PointerEventType.Exit -> motion.leave()
            }
        }
    }
}

/**
 * Draws the badge's four layers, each in its own frame, then the peeled corner. Nothing is clipped
 * to a path: complex clips cost a lot on Skia's GPU backends. The sticker and the print are drawn
 * whole, and the lifted corner is painted over them afterwards: the footage that the peel uncovers,
 * then the flap.
 */
private fun DrawScope.drawBadge(
    art: BadgeArt,
    ink: WordInk,
    motion: BadgeMotion,
    footage: ClubFootage,
    prime: Boolean,
) {
    val apart = motion.explode
    val seconds = motion.seconds
    // The grip's spring can swing a hair past the corner on its way home; past it, the crease would
    // flip and lift the whole card.
    val gripX = minOf(motion.gripX, 0f).dp.toPx()
    val gripY = maxOf(motion.gripY, 0f).dp.toPx()
    val fold = Fold(Point(art.width, 0f), Point(art.width + gripX, gripY))
    val lifted =
        if (fold.depth > FOLD_SHOWS)
            Path.combine(PathOperation.Intersect, art.shape, liftedSide(fold, art))
        else null

    Tracing.section("footage") {
        inLayer(Layers.VIDEO, apart) {
            if (apart > TOGETHER) {
                footage.draw(
                    drawContext.canvas.skiaCanvas,
                    art.skiaShape,
                    seconds,
                    art.width,
                    art.height,
                )
            } else {
                // Together, the sticker hides all of the footage but the word.
                val box = ink.box
                clipRect(box.left, box.top, box.right, box.bottom) {
                    footage.draw(
                        drawContext.canvas.skiaCanvas,
                        art.skiaShape,
                        seconds,
                        art.width,
                        art.height,
                    )
                }
            }
        }
    }

    Tracing.section("sticker") {
        val share = multiplyShare(apart)
        val bounds = stickerBounds(art, apart)
        if (share > 0f) sticker(art, ink, apart, bounds, BlendMode.Multiply, share)
        if (share < 1f) sticker(art, ink, apart, bounds, BlendMode.SrcOver, 1f - share)
        // The plain composite first runs as the badge starts coming apart; drawn unseen over the
        // first frames, its pipeline is ready by then.
        else if (prime) sticker(art, ink, apart, bounds, BlendMode.SrcOver, PRIME_ALPHA)
    }

    Tracing.section("print") { inLayer(Layers.PRINT, apart) { art.drawPrint(this, seconds) } }

    if (lifted != null) {
        Tracing.section("peel") {
            inLayer(Layers.PRINT, apart) {
                footage.draw(
                    drawContext.canvas.skiaCanvas,
                    lifted.asSkiaPath(),
                    seconds,
                    art.width,
                    art.height,
                )
                drawFlap(fold, lifted)
            }
        }
    }
}

/**
 * The sticker, composited onto what is under it with [mode] at [alpha]: its black and its ink, each
 * in its own frame, in an offscreen layer no bigger than [bounds].
 */
private fun DrawScope.sticker(
    art: BadgeArt,
    ink: WordInk,
    apart: Float,
    bounds: Rect,
    mode: BlendMode,
    alpha: Float,
) {
    val paint =
        Paint().apply {
            blendMode = mode
            this.alpha = alpha
        }
    drawContext.canvas.saveLayer(bounds, paint)
    inLayer(Layers.BLACK, apart) { art.drawBlack(this) }
    inLayer(Layers.INK, apart) { art.drawInk(this, ink, apart.coerceIn(0f, 1f)) }
    drawContext.canvas.restore()
}

/** Where the sticker's two layers land on the stage, so its offscreen layer covers only them. */
private fun DrawScope.stickerBounds(art: BadgeArt, apart: Float): Rect {
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (layer in intArrayOf(Layers.BLACK, Layers.INK)) {
        val matrix = layerMatrix(this, layer, apart)
        for (corner in
            arrayOf(
                Offset.Zero,
                Offset(art.width, 0f),
                Offset(0f, art.height),
                Offset(art.width, art.height),
            )) {
            val p = matrix.map(corner)
            left = minOf(left, p.x)
            top = minOf(top, p.y)
            right = maxOf(right, p.x)
            bottom = maxOf(bottom, p.y)
        }
    }
    return Rect(left, top, right, bottom).inflate(2f)
}

/** The [lifted] corner, folded back over the crease to show the sticker's grey backing. */
private fun DrawScope.drawFlap(fold: Fold, lifted: Path) {
    val flap = Path().apply { addPath(lifted) }
    val r = fold.reflection()
    flap.transform(
        Matrix().apply {
            values[Matrix.ScaleX] = r[0]
            values[Matrix.SkewY] = r[1]
            values[Matrix.SkewX] = r[2]
            values[Matrix.ScaleY] = r[3]
            values[Matrix.TranslateX] = r[4]
            values[Matrix.TranslateY] = r[5]
        }
    )
    val dropX = 1.5.dp.toPx()
    val dropY = 3.dp.toPx()
    withTransform({ translate(dropX, dropY) }) { drawPath(flap, FlapShadow) }
    val crease = Offset((fold.corner.x + fold.grip.x) / 2f, (fold.corner.y + fold.grip.y) / 2f)
    drawPath(
        flap,
        Brush.linearGradient(
            0f to BackingLight,
            1f to BackingDark,
            start = crease,
            end = Offset(fold.grip.x, fold.grip.y),
        ),
    )
}

/** Everything on the corner's side of the crease, as a quad well past the badge's edges. */
private fun liftedSide(fold: Fold, art: BadgeArt): Path {
    val quad = fold.liftedSide(reach = (art.width + art.height) * 2f)
    return Path().apply {
        moveTo(quad[0].x, quad[0].y)
        for (i in 1 until quad.size) lineTo(quad[i].x, quad[i].y)
        close()
    }
}

private inline fun DrawScope.inLayer(layer: Int, apart: Float, block: DrawScope.() -> Unit) {
    val matrix = layerMatrix(this, layer, apart)
    withTransform({ transform(matrix) }, block)
}

/**
 * From [layer]'s own frame, the badge's, to the stage's: turned, laid back and lifted as the badge
 * comes [apart], around the middle of the stack.
 */
internal fun layerMatrix(scope: DrawScope, layer: Int, apart: Float): Matrix {
    val pose = layerPose(layer, apart)
    val centre = stageCentre(scope, apart)
    val width = BadgeSpec.WIDTH.value * scope.density
    val height = BadgeSpec.HEIGHT.value * scope.density
    return Matrix().apply {
        translate(centre.x, centre.y + pose.lift * height)
        scale(1f, pose.squash)
        rotateZ(pose.degrees)
        scale(pose.scale, pose.scale)
        translate(-width / 2f, -height / 2f)
    }
}

/** The middle of the badge, or of the stack as it comes [apart]. */
private fun stageCentre(scope: Density, width: Float, height: Float, apart: Float): Offset =
    with(scope) {
        Offset(
            width / 2f + BadgeSpec.APART_SHIFT.toPx() * apart,
            (height - CAPTION_ROOM.toPx()) / 2f,
        )
    }

private fun stageCentre(scope: DrawScope, apart: Float) =
    stageCentre(scope, scope.size.width, scope.size.height, apart)

private fun stageCentre(scope: GraphicsLayerScope, apart: Float) =
    stageCentre(scope, scope.size.width, scope.size.height, apart)

private fun stageCentre(
    scope: AwaitPointerEventScope,
    apart: Float,
) = stageCentre(scope, scope.size.width.toFloat(), scope.size.height.toFloat(), apart)

private fun tableGrid(width: Float, height: Float, step: Float): Path =
    Path().apply {
        var x = step
        while (x < width) {
            addRect(Rect(x, 0f, x + 1f, height))
            x += step
        }
        var y = step
        while (y < height) {
            addRect(Rect(0f, y, width, y + 1f))
            y += step
        }
    }

private val Table = Color(0xFF2A2B2F)
private val GridLine = Color(0x0CFFFFFF)
private val Caption = Color(0xFF8E8F96)
private val FlapShadow = Color(0x5C000000)
private val BackingLight = Color(0xFF6A6B71)
private val BackingDark = Color(0xFF3A3B40)
private val CAPTION_ROOM = 56.dp
private val GRID = 32.dp
private val CORNER_NODE = 24.dp
private val CLICK_SLOP = 6.dp
private const val CAMERA_DISTANCE = 14f
private const val PEEL_REACH = 330f
private const val NOTES_FROM = 0.72f

/** The shadow is gone by the time the badge is this share of the way apart, inverted. */
private const val SHADOW_FADE = 3f

/** How many frames draw what has not shown yet, unseen, so its first real frame costs no more. */
private const val PRIME_FRAMES = 2

/** Unseen: one step of eight-bit alpha. */
internal const val PRIME_ALPHA = 1f / 255f

/** Below this the badge counts as together. */
private const val TOGETHER = 0.002f

/** Below this many pixels, the corner is flat. */
private const val FOLD_SHOWS = 0.5f
