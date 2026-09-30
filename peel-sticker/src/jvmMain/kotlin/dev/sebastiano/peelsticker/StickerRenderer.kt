package dev.sebastiano.peelsticker

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

/** Where the sticker sits on the stage, how it is peeled, and how it shines, for one frame. */
internal class StickerFrame(
    /** Stage pixel of the texture's top-left corner. */
    val originX: Float,
    val originY: Float,
    /** Texels per stage pixel. */
    val texScale: Float,
    val fold: PeelFold?,
    val mode: ShineMode,
    /** The light, in texels. */
    val pointerX: Float,
    val pointerY: Float,
    /** How much the light shows, 0 to 1. */
    val shine: Float,
    val time: Float,
    /** Stage pixels per dp, to size the shadows. */
    val density: Float,
)

/**
 * Draws a [StickerTexture] in four layers: the soft shadow of the part still on the table, that
 * part, the shadow the lifted part casts, and the lifted part itself. It owns native Skia objects,
 * so [close] it when it leaves composition.
 *
 * Shaders only run where something happens, because Skia's CPU backend (Xvfb, the recordings) pays
 * for every shaded pixel, and runs texture samples that follow an early `return` for all of them
 * (see `docs/TRACING.md`):
 * - the table part is a copy of the face pre-scaled to the stage, except around the light;
 * - its shadow is blurred once per layout and cached;
 * - the lifted part is shaded in three bands, each with the smallest program it needs, inside the
 *   hull of where the die-cut can land;
 * - the lifted part's shadow is shaded and blurred at a quarter of the resolution.
 *
 * That last one runs on the CPU when Compose records the frame, whatever the backend, so on a GPU
 * the shadow is drawn as a blurred layer instead, which the GPU renders when the frame plays back.
 */
internal class StickerRenderer(private val texture: StickerTexture) : AutoCloseable {
    private val programs = HashMap<ProgramKey, RuntimeEffect>()
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
    private val front =
        texture.front.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, sampling, null)
    private val back =
        texture.back.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, sampling, null)
    private val paint = Paint()
    private var tableShadow: TableShadow? = null
    private var scaledFront: Image? = null
    private val scratch = arrayOfNulls<Surface>(2)

    /**
     * Each layer is a trace section. Compose records these draws and rasterises them later, so in
     * an app trace they time the recording; drawn straight onto a raster surface they time the
     * pixels. See `docs/TRACING.md`.
     *
     * [gpu] says the canvas plays back on a GPU, where the lifted shadow is best left to it.
     */
    fun draw(canvas: Canvas, bounds: Rect, frame: StickerFrame, gpu: Boolean = false) =
        Tracing.section("peel-sticker draw") { drawLayers(canvas, bounds, frame, gpu) }

    private fun drawLayers(canvas: Canvas, bounds: Rect, frame: StickerFrame, gpu: Boolean) {
        val side = StickerTexture.SIZE / frame.texScale
        val sticker = Rect.makeXYWH(frame.originX, frame.originY, side, side)
        val fold = frame.fold
        Tracing.section("table shadow") { drawTableShadow(canvas, sticker, frame) }
        canvas.save()
        if (fold != null) clipToTable(canvas, fold, 0f, 0f)
        Tracing.section("table") { drawTable(canvas, bounds, sticker, frame) }
        canvas.restore()
        if (fold == null) return
        val outline = fold.liftedOutline(dieCutHull(frame))
        if (outline.size < 6) return
        val lifted = clip(boundsOf(outline).inflate(2f), bounds)
        if (lifted.isEmpty) return
        Tracing.section("lifted shadow") {
            if (gpu) drawLiftedShadowLayer(canvas, lifted, outline, frame, fold)
            else drawLiftedShadow(canvas, lifted, outline, frame, fold)
        }
        Tracing.section("lifted") { drawLifted(canvas, lifted, outline, frame, fold) }
    }

    override fun close() {
        paint.close()
        front.close()
        back.close()
        programs.values.forEach { it.close() }
        tableShadow?.image?.close()
        scaledFront?.close()
        scratch.forEach { it?.close() }
    }

    /** The die-cut's convex hull on the stage: all of the sticker that can lift, and no margin. */
    private fun dieCutHull(frame: StickerFrame): FloatArray {
        val cells = texture.silhouette.hull
        val scale = StickerTexture.CELL / frame.texScale
        return FloatArray(cells.size) { i ->
            cells[i] * scale + if (i % 2 == 0) frame.originX else frame.originY
        }
    }

    /** The table part: plain texture, with the shine shaded only as far as it reaches. */
    private fun drawTable(canvas: Canvas, bounds: Rect, sticker: Rect, frame: StickerFrame) {
        val shine = shineOf(frame)
        val lit = shine?.let {
            val radius = SHINE_REACH.getValue(it) * StickerTexture.CUT / frame.texScale
            val x = frame.originX + frame.pointerX / frame.texScale
            val y = frame.originY + frame.pointerY / frame.texScale
            pixelAligned(
                clip(clip(Rect(x - radius, y - radius, x + radius, y + radius), sticker), bounds)
            )
        }
        if (lit == null || lit.isEmpty) {
            drawFront(canvas, sticker)
            return
        }
        canvas.save()
        canvas.clipRect(lit, ClipMode.DIFFERENCE, false)
        drawFront(canvas, sticker)
        canvas.restore()
        shade(canvas, lit, frame, ProgramKey(TABLE, shine))
    }

    /**
     * The plain face, from a copy scaled to the stage once per layout. The stage puts the texture
     * on whole pixels, so drawing it is a plain copy, not a filtered resample every frame.
     */
    private fun drawFront(canvas: Canvas, sticker: Rect) {
        val size = sticker.width.roundToInt()
        val scaled =
            scaledFront?.takeIf { it.width == size }
                ?: renderScaledFront(size).also {
                    scaledFront?.close()
                    scaledFront = it
                }
        canvas.drawImage(scaled, sticker.left, sticker.top, paint)
    }

    private fun renderScaledFront(size: Int): Image {
        val surface = Surface.makeRasterN32Premul(size, size)
        val source = texture.front.width.toFloat()
        surface.canvas.drawImageRect(
            texture.front,
            Rect.makeWH(source, source),
            Rect.makeWH(size.toFloat(), size.toFloat()),
            sampling,
            null,
            true,
        )
        return surface.makeImageSnapshot().also { surface.close() }
    }

    /**
     * The table part's drop shadow, blurred once per layout and cached. While peeling it is clipped
     * where the table part ends, shifted with the shadow.
     */
    private fun drawTableShadow(canvas: Canvas, sticker: Rect, frame: StickerFrame) {
        val d = frame.density
        val shadow =
            tableShadow?.takeIf { it.texScale == frame.texScale && it.density == d }
                ?: renderTableShadow(sticker.width, frame.texScale, d).also {
                    tableShadow?.image?.close()
                    tableShadow = it
                }
        // Whole pixels, so the cached shadow is copied, not resampled.
        val dx = (TABLE_SHADOW_X * d).roundToInt().toFloat()
        val dy = (TABLE_SHADOW_Y * d).roundToInt().toFloat()
        canvas.save()
        frame.fold?.let { clipToTable(canvas, it, dx, dy) }
        paint.alpha = (TABLE_SHADOW * 255f).toInt()
        canvas.drawImage(
            shadow.image,
            sticker.left - shadow.margin + dx,
            sticker.top - shadow.margin + dy,
            paint,
        )
        paint.alpha = 255
        canvas.restore()
    }

    private fun renderTableShadow(side: Float, texScale: Float, density: Float): TableShadow {
        val sigma = TABLE_SHADOW_BLUR * density
        val margin = ceil(sigma * 3f)
        val size = (side + margin * 2f).roundToInt()
        val surface = Surface.makeRasterN32Premul(size, size)
        Paint().use { shadowPaint ->
            ColorFilter.makeBlend(0xFF000000.toInt(), BlendMode.SRC_IN).use { black ->
                ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL).use { blur ->
                    shadowPaint.colorFilter = black
                    shadowPaint.imageFilter = blur
                    val source = texture.front.width.toFloat()
                    surface.canvas.drawImageRect(
                        texture.front,
                        Rect.makeWH(source, source),
                        Rect.makeXYWH(margin, margin, side, side),
                        sampling,
                        shadowPaint,
                        true,
                    )
                }
            }
        }
        val image = surface.makeImageSnapshot()
        surface.close()
        return TableShadow(image, margin, texScale, density)
    }

    /**
     * The lifted part's shadow: its coverage shaded at a quarter of the resolution and blurred
     * there, then scaled back up. A soft shadow loses nothing to it.
     */
    private fun drawLiftedShadow(
        canvas: Canvas,
        lifted: Rect,
        outline: FloatArray,
        frame: StickerFrame,
        fold: PeelFold,
    ) {
        val (dx, dy, fullSigma) = liftedShadowOf(frame, fold)
        val sigma = fullSigma / SHADOW_SCALE
        val margin = ceil(sigma * 3f)
        val width = ceil(lifted.width / SHADOW_SCALE + margin * 2f).toInt() + 1
        val height = ceil(lifted.height / SHADOW_SCALE + margin * 2f).toInt() + 1
        val coverage = smallSurface(width, height, 0)
        val blurred = smallSurface(width, height, 1)
        coverage.canvas.clear(0)
        coverage.canvas.save()
        coverage.canvas.translate(margin, margin)
        coverage.canvas.scale(1f / SHADOW_SCALE, 1f / SHADOW_SCALE)
        coverage.canvas.translate(-lifted.left, -lifted.top)
        clipToOutline(coverage.canvas, outline)
        shade(coverage.canvas, lifted, frame, ProgramKey(COVERAGE, null))
        coverage.canvas.restore()
        blurred.canvas.clear(0)
        coverage.makeImageSnapshot().use { sharp ->
            ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL).use { blur ->
                paint.imageFilter = blur
                blurred.canvas.drawImage(sharp, 0f, 0f, paint)
                paint.imageFilter = null
            }
        }
        blurred.makeImageSnapshot().use { soft ->
            val source = Rect.makeWH(width.toFloat(), height.toFloat())
            val target =
                Rect.makeXYWH(
                    lifted.left - margin * SHADOW_SCALE + dx,
                    lifted.top - margin * SHADOW_SCALE + dy,
                    width * SHADOW_SCALE,
                    height * SHADOW_SCALE,
                )
            canvas.save()
            clipToOutline(canvas, grown(outline, dx, dy, margin * SHADOW_SCALE))
            paint.alpha = (LIFTED_SHADOW * 255f).toInt()
            // Nearest is a quarter of the cost of linear on the CPU, and the shadow is too soft to
            // show its blocks.
            canvas.drawImageRect(soft, source, target, SamplingMode.DEFAULT, paint, false)
            paint.alpha = 255
            canvas.restore()
        }
    }

    /**
     * The lifted part's shadow as a layer: its coverage at full resolution, blurred as the layer is
     * put down. Compose only records it, so on a GPU none of it costs the CPU; in software it
     * doubles the cost of a peeling frame. The two shadows differ by at most 3 in 255.
     */
    private fun drawLiftedShadowLayer(
        canvas: Canvas,
        lifted: Rect,
        outline: FloatArray,
        frame: StickerFrame,
        fold: PeelFold,
    ) {
        val (dx, dy, sigma) = liftedShadowOf(frame, fold)
        ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL).use { blur ->
            Paint().use { layer ->
                layer.imageFilter = blur
                layer.alpha = (LIFTED_SHADOW * 255f).toInt()
                canvas.save()
                canvas.translate(dx, dy)
                canvas.saveLayer(lifted, layer)
                clipToOutline(canvas, outline)
                shade(canvas, lifted, frame, ProgramKey(COVERAGE, null))
                canvas.restore()
                canvas.restore()
            }
        }
    }

    /** How far the lifted part's shadow falls, and how soft it is: it grows as the sheet lifts. */
    private fun liftedShadowOf(frame: StickerFrame, fold: PeelFold): Triple<Float, Float, Float> {
        val d = frame.density
        val lift = fold.curve.tight + fold.curve.loose
        return Triple(1.5f * d + lift * 0.06f, 4f * d + lift * 0.16f, 5f * d + lift * 0.12f)
    }

    /** One of the two scratch surfaces for the lifted shadow, grown when it is too small. */
    private fun smallSurface(width: Int, height: Int, index: Int): Surface {
        val current = scratch[index]
        if (current != null && current.width >= width && current.height >= height) return current
        current?.close()
        // Some slack, so a flap that grows by a few pixels does not reallocate every frame.
        return Surface.makeRasterN32Premul(width + SCRATCH_SLACK, height + SCRATCH_SLACK).also {
            scratch[index] = it
        }
    }

    private fun clipToOutline(canvas: Canvas, outline: FloatArray) {
        val path = Path()
        path.moveTo(outline[0], outline[1])
        for (i in 2 until outline.size step 2) path.lineTo(outline[i], outline[i + 1])
        path.close()
        canvas.clipPath(path.asSkiaPath(), ClipMode.INTERSECT, false)
    }

    /**
     * The lifted sheet, in three bands along the fold, each with the least shader it needs: the
     * flat flap is one texture read, the loose roll adds the curve, and only the thin tight curl
     * also shows the face. On the CPU this took the lifted part from about 17 ms to 6 ms.
     *
     * The shaders decide which band a pixel is in, from the same `q` in each, so every pixel is
     * drawn exactly once: split by clips alone, pixels right on a band boundary fell in neither and
     * left a dotted seam. The clips only bound the work, half a pixel wider than their band: both
     * they and the shaders test pixel centres. The tight curl starts [CURL_OVERLAP] before the
     * axis, laying its face over the table part's anti-aliased edge, so the fold has no seam
     * either.
     */
    private fun drawLifted(
        canvas: Canvas,
        lifted: Rect,
        outline: FloatArray,
        frame: StickerFrame,
        fold: PeelFold,
    ) {
        val curlStart = -CURL_OVERLAP
        val rollStart = fold.curve.tight - fold.curve.loose
        val bands =
            listOf(
                Triple(-FAR, minOf(rollStart, curlStart), ProgramKey(LIFTED_FLAT, null)),
                Triple(rollStart, curlStart, ProgramKey(LIFTED_ROLL, null)),
                // To the rim, which fades out over the half pixel past `tight`.
                Triple(curlStart, fold.curve.tight + 0.5f, ProgramKey(LIFTED, shineOf(frame))),
            )
        for ((from, to, key) in bands) {
            if (to <= from) continue
            canvas.save()
            clipToOutline(canvas, outline)
            clipToBand(canvas, fold, from - BAND_MARGIN, to + BAND_MARGIN)
            shade(canvas, lifted, frame, key, from, to)
            canvas.restore()
        }
    }

    /** Clips to where `q`, the distance along the fold from its axis, runs from [from] to [to]. */
    private fun clipToBand(canvas: Canvas, fold: PeelFold, from: Float, to: Float) {
        val degrees = Math.toDegrees(atan2(fold.dirY, fold.dirX).toDouble()).toFloat()
        canvas.translate(fold.axisX, fold.axisY)
        canvas.rotate(degrees)
        canvas.clipRect(Rect(from, -FAR, to, FAR), ClipMode.INTERSECT, false)
        canvas.rotate(-degrees)
        canvas.translate(-fold.axisX, -fold.axisY)
    }

    /** Clips to the table side of the fold's axis, moved by ([dx], [dy]). */
    private fun clipToTable(canvas: Canvas, fold: PeelFold, dx: Float, dy: Float) {
        val degrees = Math.toDegrees(atan2(fold.dirY, fold.dirX).toDouble()).toFloat()
        canvas.translate(fold.axisX + dx, fold.axisY + dy)
        canvas.rotate(degrees)
        canvas.clipRect(Rect(-FAR, -FAR, 0f, FAR), ClipMode.INTERSECT, true)
        canvas.rotate(-degrees)
        canvas.translate(-fold.axisX - dx, -fold.axisY - dy)
    }

    private fun shade(
        canvas: Canvas,
        area: Rect,
        frame: StickerFrame,
        key: ProgramKey,
        bandFrom: Float = -FAR,
        bandTo: Float = FAR,
    ) {
        shaderFor(frame, key, bandFrom, bandTo).use { shader ->
            paint.shader = shader
            canvas.drawRect(area, paint)
            paint.shader = null
        }
    }

    private fun shineOf(frame: StickerFrame): ShineMode? =
        if (frame.shine > SHINE_OFF) frame.mode else null

    /**
     * Each layer and shine gets its own small program, compiled the first time it is drawn: Skia's
     * CPU backend runs texture samples that follow an early `return` for every pixel, so one shader
     * for everything paid for the peel's samples on the flat sticker too.
     */
    private fun shaderFor(
        frame: StickerFrame,
        key: ProgramKey,
        bandFrom: Float,
        bandTo: Float,
    ): Shader {
        val program = programs.getOrPut(key) { RuntimeEffect.makeForShader(peelProgram(key)) }
        val builder = RuntimeShaderBuilder(program)
        val fold = frame.fold
        builder.uniform("origin", frame.originX, frame.originY)
        builder.uniform("texScale", frame.texScale)
        builder.uniform("axis", fold?.axisX ?: 0f, fold?.axisY ?: 0f)
        builder.uniform("dir", fold?.dirX ?: 1f, fold?.dirY ?: 0f)
        builder.uniform("tight", fold?.curve?.tight ?: 0f)
        builder.uniform("loose", fold?.curve?.loose ?: 0f)
        builder.uniform("pointer", frame.pointerX, frame.pointerY)
        builder.uniform("shine", frame.shine)
        builder.uniform("time", frame.time)
        builder.uniform("span", StickerTexture.CUT)
        builder.uniform("band", bandFrom, bandTo)
        builder.child("front", front)
        builder.child("back", back)
        return builder.makeShader().also { builder.close() }
    }

    private class TableShadow(
        val image: Image,
        val margin: Float,
        val texScale: Float,
        val density: Float,
    )

    private companion object {
        const val TABLE_SHADOW = 0.34f
        const val TABLE_SHADOW_X = 1f
        const val TABLE_SHADOW_Y = 2.5f
        const val TABLE_SHADOW_BLUR = 2.5f
        const val LIFTED_SHADOW = 0.22f
        const val SHADOW_SCALE = 4f
        const val SCRATCH_SLACK = 32
        const val SHINE_OFF = 0.001f

        /** How far before the axis the tight curl starts, in stage pixels. */
        const val CURL_OVERLAP = 1.5f

        /** How much wider than its band a band's clip is, in stage pixels. */
        const val BAND_MARGIN = 0.5f
        const val FAR = 1e5f

        /** How far each shine reaches from the light, as a share of the die-cut. */
        val SHINE_REACH =
            mapOf(
                ShineMode.White to 0.64f,
                ShineMode.Sparkle to 0.42f,
                ShineMode.Prism to 0.68f,
                ShineMode.Ripple to 0.5f,
                ShineMode.Halftone to 0.48f,
            )
    }
}

/** The convex [outline] moved by ([dx], [dy]) and grown by at least [by] all round. */
private fun grown(outline: FloatArray, dx: Float, dy: Float, by: Float): FloatArray {
    val points = ArrayList<Pair<Float, Float>>(outline.size * 2)
    for (i in outline.indices step 2) {
        val x = outline[i] + dx
        val y = outline[i + 1] + dy
        points += (x - by) to (y - by)
        points += (x + by) to (y - by)
        points += (x + by) to (y + by)
        points += (x - by) to (y + by)
    }
    return convexHull(points)
}

private fun boundsOf(outline: FloatArray): Rect {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (i in outline.indices step 2) {
        left = minOf(left, outline[i])
        right = maxOf(right, outline[i])
        top = minOf(top, outline[i + 1])
        bottom = maxOf(bottom, outline[i + 1])
    }
    return Rect(left, top, right, bottom)
}

private fun clip(rect: Rect, bounds: Rect) =
    Rect(
        maxOf(rect.left, bounds.left),
        maxOf(rect.top, bounds.top),
        minOf(rect.right, bounds.right),
        minOf(rect.bottom, bounds.bottom),
    )

/** Grows [rect] to whole pixels, so the shaded area and the plain image meet without a seam. */
private fun pixelAligned(rect: Rect) =
    Rect(floor(rect.left), floor(rect.top), ceil(rect.right), ceil(rect.bottom))

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        close()
    }

private const val TABLE = 0
private const val LIFTED = 1
private const val COVERAGE = 2
private const val LIFTED_FLAT = 3
private const val LIFTED_ROLL = 4

/** A layer of the sticker, and the shine its face shows, if any. */
private data class ProgramKey(val layer: Int, val shine: ShineMode?)

/**
 * The shader for one [key], from pieces. Stage pixels map to texels through `origin` and
 * `texScale`.
 *
 * The table layer is the sticker where it still lies flat. With a fold, a pixel `q` stage pixels
 * along `dir` from the axis shows what the lifted sheet puts above the table: first the backing
 * past the top of the tight curl, then the face on its rising side. The depths come from inverting
 * [PeelCurve], which the Kotlin tests cover. The coverage layer is the lifted layer's alpha only,
 * for its shadow.
 *
 * `face` applies the shine to the front, in texels, around `pointer`.
 */
private fun peelProgram(key: ProgramKey): String {
    val shine = key.shine
    val face =
        if (shine == null) {
            "float4 face(float2 t) { return front.eval(t); }"
        } else {
            SHINES.getValue(shine) +
                """
                float4 face(float2 t) {
                    float4 c = front.eval(t);
                    if (c.a <= 0.0) return c;
                    float2 away = t - pointer;
                    float r = length(away);
                    float2 u = r > 0.001 ? away / r : float2(0.0);
                    return ${SHINE_CALLS.getValue(shine)};
                }
                """
                    .trimIndent()
        }
    val main =
        when (key.layer) {
            TABLE -> TABLE_MAIN
            LIFTED -> LIFTED_MAIN
            LIFTED_FLAT -> FLAT_MAIN
            LIFTED_ROLL -> ROLL_MAIN
            else -> COVERAGE_MAIN
        }
    return listOf(PROGRAM_HEAD, face, main).joinToString("\n")
}

private val PROGRAM_HEAD =
    """
    uniform shader front;
    uniform shader back;
    uniform float2 origin;
    uniform float texScale;
    uniform float2 axis;
    uniform float2 dir;
    uniform float tight;
    uniform float loose;
    uniform float2 pointer;
    uniform float shine;
    uniform float time;
    uniform float span;
    // The range of q, the distance along the fold from its axis, a lifted band draws.
    uniform float2 band;

    const float HALF_PI = 1.5707963;
    const float TAU = 6.2831853;

    float2 tex(float2 p) { return (p - origin) * texScale; }

    float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

    float4 onWhite(float4 c) { return float4(c.rgb + (1.0 - c.a), 1.0); }

    // acos to within 7e-5 radians (Abramowitz and Stegun 4.4.45): a fraction of the cost of the
    // built-in one on Skia's CPU backend, and a hundredth of a pixel of depth here.
    float fastAcos(float x) {
        float a = abs(x);
        float r = sqrt(1.0 - a) * (1.5707288 + a * (-0.2121144 + a * (0.0742610 - 0.0187293 * a)));
        return x < 0.0 ? 3.14159265 - r : r;
    }

    // How much of the backing's print shows where the roll has turned to cos(phi) = c. Towards the
    // top of the roll one pixel spans ever more of the backing, up to tens of pixels at the rim;
    // sampled once, the watermark there came and went from pixel to pixel, a dotted line along
    // the fold. Past about 3 to 1 the print blends into the paper, as a mipmap would blend it.
    float legible(float c) { return smoothstep(0.12, 0.35, sqrt(max(1.0 - c * c, 0.0))); }

    float4 backing(float2 t, float printed) {
        float4 b = back.eval(t);
        return float4(mix(float3(b.a), b.rgb, printed), b.a);
    }
    """
        .trimIndent()

private val SHINE_CALLS =
    mapOf(
        ShineMode.White to "white(c, r)",
        ShineMode.Sparkle to "sparkle(t, c, r)",
        ShineMode.Prism to "prism(t, c, u, r)",
        ShineMode.Ripple to "ripple(t, c, u, r)",
        ShineMode.Halftone to "halftone(t, c)",
    )

private val SHINES =
    mapOf(
        ShineMode.White to
            """
            float4 white(float4 c, float r) {
                float reach = 0.2 * span;
                float glare = shine * exp(-r * r / (2.0 * reach * reach));
                return float4(mix(c.rgb, float3(c.a), 0.62 * glare), c.a);
            }
            """
                .trimIndent(),
        ShineMode.Sparkle to
            """
            float4 sparkle(float2 t, float4 c, float r) {
                float w = shine * (1.0 - smoothstep(0.05 * span, 0.42 * span, r));
                float size = 22.0;
                float2 cell = floor(t / size);
                float h = hash(cell);
                float live = step(0.55, h);
                float2 jitter = float2(hash(cell + 3.7), hash(cell + 9.1)) - 0.5;
                float2 d = t - (cell + 0.5 + jitter * 0.5) * size;
                float twinkle = pow(max(0.0, sin(time * (2.5 + 4.0 * h) + h * 60.0)), 6.0);
                float core = exp(-dot(d, d) / 7.0);
                float rays = exp(-d.y * d.y / 1.2 - abs(d.x) / 5.0) + exp(-d.x * d.x / 1.2 - abs(d.y) / 5.0);
                float glint = (core + 0.7 * rays) * twinkle * live * w;
                float3 tint = 0.75 + 0.25 * cos(TAU * (h * 3.0 + float3(0.0, 0.33, 0.67)));
                float3 lit = mix(c.rgb, float3(c.a), 0.12 * w) + c.a * glint * tint * 1.6;
                return float4(min(lit, float3(c.a)), c.a);
            }
            """
                .trimIndent(),
        ShineMode.Prism to
            """
            float4 prism(float2 t, float4 c, float2 u, float r) {
                // Like a lens: the split grows away from the light, then fades out across the sticker.
                float w = shine * smoothstep(0.0, 0.3 * span, r) * (1.0 - smoothstep(0.42 * span, 0.68 * span, r));
                float2 off = u * 13.0 * w;
                float4 base = onWhite(c);
                float red = onWhite(front.eval(t + off)).r;
                float blue = onWhite(front.eval(t - off)).b;
                return float4(float3(red, base.g, blue) * c.a, c.a);
            }
            """
                .trimIndent(),
        ShineMode.Ripple to
            """
            float4 ripple(float2 t, float4 c, float2 u, float r) {
                float envelope = shine * exp(-r / (0.11 * span));
                float wave = sin(r / 30.0 * TAU - time * 6.5);
                float4 moved = front.eval(t - u * wave * envelope * 3.0);
                float3 rgb = moved.a > 0.0 ? moved.rgb / moved.a * c.a : c.rgb;
                rgb *= 1.0 + 0.07 * wave * envelope;
                rgb += c.a * 0.06 * max(wave, 0.0) * envelope;
                return float4(min(rgb, float3(c.a)), c.a);
            }
            """
                .trimIndent(),
        ShineMode.Halftone to
            """
            float4 halftone(float2 t, float4 c) {
                float cellSize = 16.0;
                float2 turned = float2(t.x + t.y, t.y - t.x) * 0.70710678 / cellSize;
                float2 id = floor(turned) + 0.5;
                float2 centre = float2(id.x - id.y, id.x + id.y) * 0.70710678 * cellSize;
                float w = shine * (1.0 - smoothstep(0.3 * span, 0.46 * span, length(centre - pointer)));
                float on = smoothstep(0.04, 0.3, w);
                if (on <= 0.0) return c;
                float d = length(fract(turned) - 0.5) * cellSize;
                float radius = cellSize * (0.34 + 0.1 * w);
                float inDot = 1.0 - smoothstep(radius - 0.9, radius + 0.9, d);
                float3 straight = c.rgb / c.a;
                float luma = dot(straight, float3(0.299, 0.587, 0.114));
                float3 ink = straight * mix(float3(0.72), float3(0.93, 0.93, 0.89), luma);
                float3 paper = mix(straight, float3(1.0), 0.85 * w);
                float3 tone = mix(paper, ink, inDot);
                return float4(mix(straight, tone, on) * c.a, c.a);
            }
            """
                .trimIndent(),
    )

/** The part still on the table; the renderer clips it to the table side of the fold. */
private val TABLE_MAIN =
    """
    half4 main(float2 p) { return half4(face(tex(p))); }
    """
        .trimIndent()

private val LIFTED_MAIN =
    """
    half4 main(float2 p) {
        float q = dot(p - axis, dir);
        if (q < band.x || q >= band.y) return half4(0.0);
        // The curl's rim, where the sheet stands upright at q = tight, fades over its last pixel
        // like any anti-aliased edge; cut off hard, it read as a dotted line along the fold.
        float rim = clamp(tight + 0.5 - q, 0.0, 1.0);
        if (rim <= 0.0) return half4(0.0);
        q = min(q, tight);
        float2 foot = p - dir * q;
        float tightEnd = HALF_PI * tight;
        float looseEnd = tightEnd + HALF_PI * loose;

        float4 top = float4(0.0);
        if (q <= tight) {
            float depth;
            float light;
            float printed = 1.0;
            if (loose > 0.0 && q >= tight - loose) {
                // c = cos(phi), phi being how far round the loose curl the sheet has rolled.
                float c = clamp((q - tight + loose) / loose, -1.0, 1.0);
                depth = tightEnd + loose * fastAcos(c);
                // Shaded from a pixel inside the rim: sin(phi) falls to 0 within the last pixel,
                // and all that shade in one pixel read as a dashed dark line along the fold.
                float lit = clamp((min(q, tight - 1.0) - tight + loose) / loose, -1.0, 1.0);
                // A soft highlight where phi is about half a radian, where cos(phi) is 0.88.
                float bump = max(0.0, 1.0 - (lit - 0.8776) * (lit - 0.8776) / 0.09);
                light = 0.84 + 0.14 * sqrt(1.0 - lit * lit) + 0.06 * bump * bump;
                printed = legible(c);
            } else {
                depth = looseEnd + (tight - loose - q);
                light = 0.98;
            }
            float4 b = backing(tex(foot + dir * depth), printed);
            top = float4(min(b.rgb * light, float3(b.a)), b.a);
        }

        float4 under = float4(0.0);
        // From the band's start, a little before the axis, so the face overlaps the table's edge.
        if (tight > 0.0 && q <= tight) {
            float s = clamp(q / tight, -1.0, 1.0);
            float4 f = face(tex(foot + dir * tight * (HALF_PI - fastAcos(s))));
            under = float4(f.rgb * (0.55 + 0.45 * sqrt(1.0 - s * s)), f.a);
        }

        return half4((top + under * (1.0 - top.a)) * rim);
    }
    """
        .trimIndent()

/** The flat flap past both curls: the backing, mirrored across the fold. */
private val FLAT_MAIN =
    """
    half4 main(float2 p) {
        float q = dot(p - axis, dir);
        if (q >= band.x && q < band.y) {
            float depth = HALF_PI * (tight + loose) + (tight - loose - q);
            float4 b = back.eval(tex(p + dir * (depth - q)));
            return half4(min(b.rgb * 0.98, float3(b.a)), b.a);
        }
        return half4(0.0);
    }
    """
        .trimIndent()

/** The loose roll between the flat flap and the axis: the backing only, shaded by the curve. */
private val ROLL_MAIN =
    """
    half4 main(float2 p) {
        float q = dot(p - axis, dir);
        if (q >= band.x && q < band.y) {
            float c = clamp((q - tight + loose) / loose, -1.0, 1.0);
            float depth = HALF_PI * tight + loose * fastAcos(c);
            float bump = max(0.0, 1.0 - (c - 0.8776) * (c - 0.8776) / 0.09);
            float light = 0.84 + 0.14 * sqrt(1.0 - c * c) + 0.06 * bump * bump;
            float4 b = backing(tex(p + dir * (depth - q)), legible(c));
            return half4(min(b.rgb * light, float3(b.a)), b.a);
        }
        return half4(0.0);
    }
    """
        .trimIndent()

/** Where the lifted sheet is, backing or rising curl, as alpha only. Both share the die-cut. */
private val COVERAGE_MAIN =
    """
    half4 main(float2 p) {
        float q = dot(p - axis, dir);
        float rim = clamp(tight + 0.5 - q, 0.0, 1.0);
        q = min(q, tight);
        float2 foot = p - dir * q;
        float tightEnd = HALF_PI * tight;
        float looseEnd = tightEnd + HALF_PI * loose;
        float a = 0.0;
        if (q <= tight) {
            float depth = (loose > 0.0 && q >= tight - loose)
                ? tightEnd + loose * fastAcos(clamp((q - tight + loose) / loose, -1.0, 1.0))
                : looseEnd + (tight - loose - q);
            a = front.eval(tex(foot + dir * depth)).a;
        }
        if (tight > 0.0 && q >= 0.0 && q <= tight) {
            float under = front.eval(tex(foot + dir * tight * (HALF_PI - fastAcos(q / tight)))).a;
            a = a + under * (1.0 - a);
        }
        return half4(0.0, 0.0, 0.0, a * rim);
    }
    """
        .trimIndent()
