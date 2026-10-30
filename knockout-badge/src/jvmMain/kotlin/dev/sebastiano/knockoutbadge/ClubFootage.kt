package dev.sebastiano.knockoutbadge

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/**
 * The video under the sticker: club footage shot out of focus, rows of warm bulbs drifting past on
 * an orange haze that pumps on the beat. It is a small SkSL program rather than a file, so the
 * specimen ships no media and plays the same on every machine.
 *
 * The footage is nothing but soft light, so each frame is rendered at a sixth of the badge's
 * resolution, on the CPU, and drawn scaled up with bilinear filtering. Running the program for
 * every pixel on screen was the specimen's whole cost: without a GPU, Skia's OpenGL path (Mesa's
 * llvmpipe) and its software path both run it on the CPU. Everything that depends only on time
 * (where each bulb is, how bright, the beat) is worked out once a frame here and handed in as
 * uniforms, so the per-pixel work is a gradient and twelve squared distances.
 */
internal class ClubFootage : AutoCloseable {
    private val program by lazy { RuntimeEffect.makeForShader(SKSL) }
    private val render = Paint()
    private val paint = Paint().apply { isAntiAlias = true }
    private val bulbs = FloatArray(BULBS * 4)
    private var surface: Surface? = null
    private var frame: Image? = null
    private var frameAt = Float.NaN

    /** Fills [shape] with the footage at [seconds], for a badge [width] × [height] px. */
    fun draw(canvas: Canvas, shape: Path, seconds: Float, width: Float, height: Float) {
        val image = frame(seconds, width, height)
        val shader =
            image.makeShader(
                FilterTileMode.CLAMP,
                FilterTileMode.CLAMP,
                SamplingMode.LINEAR,
                Matrix33.makeScale(width / image.width, height / image.height),
            )
        paint.shader = shader
        canvas.drawPath(shape, paint)
        paint.shader = null
        shader.close()
    }

    /**
     * The frame at [seconds], rendered small. The peel draws the same frame twice; it renders once.
     */
    private fun frame(seconds: Float, width: Float, height: Float): Image {
        val w = max(1, ceil(width / DOWNSCALE).toInt())
        val h = max(1, ceil(height / DOWNSCALE).toInt())
        val cached = frame
        if (cached != null && frameAt == seconds && cached.width == w && cached.height == h) {
            return cached
        }
        val target =
            surface?.takeIf { it.width == w && it.height == h }
                ?: Surface.makeRasterN32Premul(w, h).also {
                    surface?.close()
                    surface = it
                }
        val builder = RuntimeShaderBuilder(program)
        builder.uniform("size", width, height)
        // Four on the floor at 124 bpm: the haze jumps on each beat and dies away before the next.
        val beat = (seconds * BEATS_PER_SECOND) % 1f
        builder.uniform("gain", 0.78f + 0.32f * exp(-beat * BEAT_DECAY))
        builder.uniform("sway", 0.35f * sin(seconds * 0.43f))
        fillBulbs(seconds, width, height)
        builder.uniform("bulbs", bulbs)
        // The program works in the badge's pixels; the surface is smaller.
        val shader = builder.makeShader(Matrix33.makeScale(w / width, h / height))
        builder.close()
        render.shader = shader
        target.canvas.drawPaint(render)
        render.shader = null
        shader.close()
        cached?.close()
        frameAt = seconds
        return target.makeImageSnapshot().also { frame = it }
    }

    override fun close() {
        frame?.close()
        frame = null
        surface?.close()
        surface = null
    }

    /**
     * Three rows of bulbs, the nearer rows bigger, softer and faster, as a camera panning past
     * string lights would see them. Each bulb flickers at its own rate.
     */
    private fun fillBulbs(seconds: Float, width: Float, height: Float) {
        val span = width * 1.5f
        val handheld = sin(seconds * 0.9f) * height * 0.015f
        for (i in 0 until BULBS) {
            val row = i / PER_ROW
            val column = i % PER_ROW
            val speed = width * (0.05f + 0.035f * row)
            val start = column * span / PER_ROW + row * width * 0.21f
            val x = (start + seconds * speed) % span - width * 0.25f
            val y = height * ROWS[row] + handheld + sin(column * 2.3f + row) * height * 0.04f
            val radius = width * (0.12f + 0.05f * row)
            val flicker = 0.72f + 0.28f * sin(seconds * (1.7f + 0.37f * i) + i * 1.9f)
            bulbs[i * 4] = x
            bulbs[i * 4 + 1] = y
            bulbs[i * 4 + 2] = 1f / (radius * radius)
            bulbs[i * 4 + 3] = (0.75f + 0.15f * row) * flicker
        }
    }

    private companion object {
        const val BULBS = 12

        /** The rows' heights, as shares of the badge's: the first runs behind the word. */
        val ROWS = floatArrayOf(0.15f, 0.3f, 0.6f)
        const val PER_ROW = 4
        const val BEATS_PER_SECOND = 124f / 60f
        const val BEAT_DECAY = 5f

        /** The footage renders with one pixel for this many of the badge's, each way. */
        const val DOWNSCALE = 6f

        val SKSL =
            """
            uniform float2 size;
            uniform float gain;
            uniform float sway;
            uniform float4 bulbs[12];

            half4 main(float2 p) {
                float2 uv = p / size;
                // A haze that is hottest at the top, swaying side to side.
                float g = saturate(1.1 - uv.y * 1.05 + sway * (uv.x - 0.5));
                half3 c = mix(half3(0.32, 0.03, 0.01), half3(1.0, 0.46, 0.05), half(g));
                float glow = 0.0;
                for (int i = 0; i < 12; i++) {
                    float2 d = p - bulbs[i].xy;
                    float f = saturate(1.0 - dot(d, d) * bulbs[i].z);
                    glow += f * f * bulbs[i].w;
                }
                glow *= gain;
                c *= half(gain);
                c += half3(1.0, 0.52, 0.1) * half(glow) + half3(1.0, 0.9, 0.62) * half(glow * glow);
                return half4(saturate(c), 1.0);
            }
            """
                .trimIndent()
    }
}
