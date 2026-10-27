// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.ShaderLayer
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.TubeMark
import dev.sebastiano.anatomy.kit.cameraOf
import dev.sebastiano.anatomy.kit.drawShader
import dev.sebastiano.anatomy.kit.glCamera
import dev.sebastiano.anatomy.kit.shaderLayer
import dev.sebastiano.anatomy.kit.toComposePath
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Paints the live Raptor in the original page's layers: the stand, the plume, the engine's items
 * re-sorted every frame by `key + lift · share` and moved by `move · share`, the rim glow on the
 * nozzle lip, the veil, and the main-stage flash. The selected assembly paints with the lit
 * accent and the rest step down a tone, as raptor.css's `[data-on]` and `[data-focus]` rules do.
 */

/** The figure's own tokens, raptor.css's `--rp-*`, per theme. */
internal class RaptorTokens(
    val rim: Color,
    val rimSoft: Color,
    val rimSoftShare: Float,
    val flash: List<Color>,
    val deck: List<Color>,
    val deckShare: Float,
) {
    companion object {
        val Dark =
            RaptorTokens(
                rim = Color(0xFFFFB46E),
                rimSoft = Color(0xFFFF8A3C),
                rimSoftShare = 0.45f,
                flash =
                    listOf(
                        Color(0xFFFFF4E6),
                        Color(0x66FFD2A0),
                        Color(0x22FF9A5A),
                        Color(0x00FF9A5A),
                    ),
                deck =
                    listOf(
                        Color(0xFFFFCF98),
                        Color(0xBFFF8A3A),
                        Color(0x47B34A12),
                        Color(0x005A2208),
                    ),
                deckShare = 1f,
            )
        val Light =
            RaptorTokens(
                rim = Color(0xFFC2410C),
                rimSoft = Color(0xFFEA580C),
                rimSoftShare = 0.25f,
                flash =
                    listOf(
                        Color(0xFFFFD9B0),
                        Color(0x44FFB07A),
                        Color(0x14FF8A4A),
                        Color(0x00FF8A4A),
                    ),
                deck =
                    listOf(
                        Color(0xFFC2410C),
                        Color(0x8CEA580C),
                        Color(0x33F97316),
                        Color(0x00F97316),
                    ),
                deckShare = 0.45f,
            )

        fun of(palette: Palette) = if (palette.isDark) Dark else Light
    }
}

/** The palettes an item paints with: its own, lit when selected, a tone down when another is. */
internal class RaptorPalettes(base: Palette) {
    val plain = base
    val on =
        base.copy(
            hi = base.lit,
            mid = base.hi,
            top = base.litTop,
            shades = List(4) { base.litShade },
        )
    val dim = base.copy(hi = base.mid, mid = base.lo)
    val standDim = base.copy(mid = base.lo)

    fun of(piece: String, selected: String?): Palette =
        when {
            selected == null -> plain
            piece == selected -> on
            piece == "stand" -> standDim
            else -> dim
        }
}

/** The shader layers, compiled once; null where SkSL is unavailable. */
internal class RaptorLayers(val plume: ShaderLayer?, val veil: ShaderLayer?) {
    companion object {
        fun make(): RaptorLayers {
            val camera = glCamera(Raptor.P)
            return RaptorLayers(
                shaderLayer(RaptorShaders.PLUME, Raptor.W, Raptor.H, camera),
                shaderLayer(RaptorShaders.VEIL, Raptor.W, Raptor.H, camera),
            )
        }
    }
}

/** Every node's fill paths, for hit-testing the pointer the way `elementFromPoint` does. */
internal class RaptorHits(nodes: List<RaptorNode>) {
    private val fills: List<List<Path>> = nodes.map { node ->
        node.item.art?.marks.orEmpty().mapNotNull { mark ->
            when (mark) {
                is SolidMark -> mark.fill.path
                is TubeMark -> mark.body.path
                else -> null
            }
        }
    }
    private val bounds: List<Rect> = fills.map { list ->
        list.map { it.getBounds() }.reduceOrNull { a, b -> a.union(b) } ?: Rect.Zero
    }

    private fun Rect.union(o: Rect) =
        Rect(min(left, o.left), min(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))

    /** The piece of the topmost node under [point] (viewBox units), in paint [order]. */
    fun pieceAt(
        point: Flat,
        order: IntArray,
        nodes: List<RaptorNode>,
        share: (String) -> Double,
    ): String? {
        for (at in order.indices.reversed()) {
            val index = order[at]
            val node = nodes[index]
            val s = share(node.item.ride)
            val x = (point.x - node.shift.x * s).toFloat()
            val y = (point.y - node.shift.y * s).toFloat()
            if (!bounds[index].contains(Offset(x, y))) continue
            if (fills[index].any { it.asSkiaPath().contains(x, y) }) return node.item.piece
        }
        return null
    }
}

/** The paint order this frame: by `key + lift · share`, ties in settled order. */
internal fun RaptorLive.paintOrder(nodes: List<RaptorNode>, order: IntArray) {
    fun at(index: Int): Double {
        val node = nodes[index]
        return node.key + node.lift * springs.getValue(node.item.ride).x
    }
    for (i in 1 until order.size) {
        val moving = order[i]
        val key = at(moving)
        var j = i - 1
        while (j >= 0 && (at(order[j]) > key || (at(order[j]) == key && order[j] > moving))) {
            order[j + 1] = order[j]
            j--
        }
        order[j + 1] = moving
    }
}

private val DECK_CENTRE = Raptor.at(Point3(0.0, 0.0, 0.0))
private val EXIT = Raptor.at(Point3(0.0, 0.0, Raptor.Z_EXIT))
private val LIP_PATH: Path by lazy { RaptorEngine.BUILD.lip.toComposePath() }
private val DECK_PATH: Path by lazy { RaptorEngine.BUILD.deckTop.toComposePath() }
private val SHADOW by lazy {
    dev.sebastiano.anatomy.kit.art { halo(RaptorEngine.BUILD.shadow, opacity = 0.45) }
}

/** Paints one frame of the live figure. */
internal fun IsoScope.drawRaptor(
    live: RaptorLive,
    order: IntArray,
    palettes: RaptorPalettes,
    layers: RaptorLayers,
    time: Double,
) {
    val build = RaptorEngine.BUILD
    val v = live.burn.view
    val dark = palette.isDark
    val shake = v.shake
    val shift =
        if (shake > 0.01)
            Offset(
                (sin(live.clock * 47) * shake * 0.5).toFloat(),
                (cos(live.clock * 59) * shake * 0.5).toFloat(),
            )
        else Offset.Zero
    draw.translate(shift.x, shift.y) {
        drawArt(build.backUnder)
        deckGlow(v.deckGlow, dark)
        drawArt(SHADOW)
        drawArt(build.backOver)
        if (v.active) layers.plume?.let { plume(it, v, time) }
        val nodes = RaptorEngine.NODES
        val jitter = Flat(v.jitterX, v.jitterY)
        for (index in order) {
            val node = nodes[index]
            val share = live.springs.getValue(node.item.ride).x
            val art = node.item.art ?: continue
            withPalette(palettes.of(node.item.piece, live.selected)) {
                drawArt(art, Flat(node.shift.x * share + jitter.x, node.shift.y * share + jitter.y))
            }
        }
        rimGlow(v.rim, jitter, dark)
        if (v.active && v.veiled) layers.veil?.let { veil -> drawShader(veil, time) { shared(v) } }
        flash(v.flash, dark)
    }
}

private fun ShaderLayer.shared(v: BurnView) {
    set("uJit", listOf(v.jitterX, v.jitterY))
    set("uPc", v.pc)
    set("uHue", v.hue)
    set("uSmoke", v.smoke)
    set("uVent", v.vent)
    set("uShockR", v.shockR)
    set("uShockA", v.shockA)
}

private fun IsoScope.plume(layer: ShaderLayer, v: BurnView, time: Double) {
    drawShader(layer, time) {
        shared(v)
        set("uGlow", v.glow)
        set("uPre", v.pre)
        set("uReach", v.reach)
        set("uSplash", v.splash)
        set("uFlash", v.flash)
        set("uRingR", v.ringR)
        set("uRingA", v.ringA)
        set("uPop", v.pop)
        set("uPopR", v.popR)
    }
}

private fun blend(dark: Boolean) = if (dark) BlendMode.Screen else BlendMode.SrcOver

/** The deck's glow under the plume: raptor.css's `rp-deckglow` radial gradient. */
private fun IsoScope.deckGlow(amount: Double, dark: Boolean) {
    val tokens = RaptorTokens.of(palette)
    val alpha = (amount * tokens.deckShare).toFloat()
    if (alpha <= 0.001f) return
    val c = Offset(DECK_CENTRE.x.toFloat(), DECK_CENTRE.y.toFloat())
    val r = (150 * Raptor.KS).toFloat()
    val sinE = cameraOf(Raptor.P).sinE.toFloat()
    val stops = listOf(0f, 0.28f, 0.7f, 1f).zip(tokens.deck).toTypedArray()
    draw.clipPath(DECK_PATH) {
        scale(1f, sinE, pivot = c) {
            drawCircle(
                Brush.radialGradient(*stops, center = c, radius = r),
                r,
                c,
                alpha = alpha,
                blendMode = blend(dark),
            )
        }
    }
}

/** The nozzle lip's glow while it burns: a soft blurred stroke under a crisp one. */
private fun IsoScope.rimGlow(rim: Double, jitter: Flat, dark: Boolean) {
    if (rim <= 0.001) return
    val tokens = RaptorTokens.of(palette)
    draw.translate(jitter.x.toFloat(), jitter.y.toFloat()) {
        drawIntoCanvas { canvas ->
            val paint =
                org.jetbrains.skia.Paint().apply {
                    color =
                        tokens.rimSoft
                            .copy(alpha = (rim * tokens.rimSoftShare).toFloat())
                            .toArgbInt()
                    mode = org.jetbrains.skia.PaintMode.STROKE
                    strokeWidth = 7f
                    strokeCap = org.jetbrains.skia.PaintStrokeCap.ROUND
                    maskFilter =
                        org.jetbrains.skia.MaskFilter.makeBlur(
                            org.jetbrains.skia.FilterBlurMode.NORMAL,
                            4f,
                        )
                    blendMode =
                        if (dark) org.jetbrains.skia.BlendMode.SCREEN
                        else org.jetbrains.skia.BlendMode.SRC_OVER
                }
            canvas.nativeCanvas.drawPath(LIP_PATH.asSkiaPath(), paint)
        }
        drawPath(
            LIP_PATH,
            tokens.rim,
            alpha = rim.toFloat(),
            style = Stroke(px(1.3f), cap = StrokeCap.Round),
            blendMode = blend(dark),
        )
    }
}

/** The main-stage flash: raptor.css's `rp-flash`, a radial gradient at the nozzle exit. */
private fun IsoScope.flash(amount: Double, dark: Boolean) {
    val alpha = (amount * 0.32).toFloat()
    if (alpha <= 0.001f) return
    val tokens = RaptorTokens.of(palette)
    val w = Raptor.W.toFloat()
    val h = Raptor.H.toFloat()
    val c = Offset(EXIT.x.toFloat(), (EXIT.y + 6).toFloat())
    val r = minOf(c.x, w - c.x, c.y, h - c.y)
    val stops = listOf(0f, 0.24f, 0.56f, 1f).zip(tokens.flash).toTypedArray()
    draw.drawRect(
        Brush.radialGradient(*stops, center = c, radius = r),
        Offset.Zero,
        androidx.compose.ui.geometry.Size(w, h),
        alpha = alpha,
        blendMode = blend(dark),
    )
}

private fun Color.toArgbInt(): Int = toArgb()
