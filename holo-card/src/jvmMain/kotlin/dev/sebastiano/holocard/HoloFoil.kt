package dev.sebastiano.holocard

import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.sqrt
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

/** A mascot's baked normal map, and the part of it the mascot covers. */
internal class Relief(val image: Image, val bounds: PixelBounds)

/** The mascots' baked normal maps. Bake them off the UI thread. */
internal class Reliefs private constructor(private val reliefs: Map<Mascot, Relief>) {
    operator fun get(mascot: Mascot): Relief = reliefs.getValue(mascot)

    companion object {
        /** Big enough for a 196dp mascot at density 2 to sample at about one texel a pixel. */
        const val SIZE = 448

        fun bake(): Reliefs =
            Tracing.section("bake reliefs") {
                Reliefs(
                    Mascot.entries.associateWith { mascot ->
                        val pixels =
                            Tracing.section("bake ${mascot.name}") {
                                bakeRelief(mascot.sdf, SIZE, Mascot.PUFF)
                            }
                        val info = ImageInfo(SIZE, SIZE, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
                        Relief(
                            Image.makeRaster(info, pixels, SIZE * 4),
                            coverageBounds(pixels, SIZE),
                        )
                    }
                )
            }
    }
}

/**
 * Paints a mascot's foil: a small SkSL program per pixel that reads the baked normal and turns it
 * into thin-film colour. The rim runs through the rainbow, the body drifts through its friend's hue
 * as the card turns, a glow inside slides against the tilt, and a specular highlight follows the
 * light.
 *
 * Software rendering runs the program on the CPU for every pixel, through the card's perspective,
 * so it is kept lean: everything that depends only on the tilt is worked out once here and handed
 * in as uniforms, the rainbow is a smoothed triangle wave rather than three cosines, the glow and
 * rim use squared distances rather than square roots, and only the box the mascot covers is drawn.
 */
internal object HoloFoil {
    private val program by lazy { RuntimeEffect.makeForShader(SKSL) }
    private val paint = Paint()

    /**
     * Draws [relief]'s foil into the [size]² box at the canvas origin. [tiltX] and [tiltY] are the
     * card's turn as a share of the most it turns, roughly -1..1.
     */
    fun draw(canvas: Canvas, relief: Relief, size: Float, hue: Float, tiltX: Float, tiltY: Float) {
        val texels = relief.image.width
        val scale = size / texels
        val normals =
            relief.image.makeShader(
                FilterTileMode.DECAL,
                FilterTileMode.DECAL,
                SamplingMode.LINEAR,
                Matrix33.makeScale(scale, scale),
            )
        val builder = RuntimeShaderBuilder(program)
        builder.uniform("invSize", 1f / size)
        // Turning the card swings the viewer across its surface...
        val view = normalized(tiltY * VIEW_SWING, -tiltX * VIEW_SWING, 1f)
        builder.uniform("view", view[0], view[1], view[2])
        // ...and the light from the top left with it. Blinn's halfway vector, for the highlight.
        val light = normalized(-0.45f + 0.6f * tiltY, -0.55f - 0.6f * tiltX, 0.75f)
        val halfway = normalized(light[0] + view[0], light[1] + view[1], light[2] + view[2])
        builder.uniform("halfway", halfway[0], halfway[1], halfway[2])
        builder.uniform("glowAt", 0.5f - 0.2f * tiltY, 0.46f + 0.2f * tiltX)
        builder.uniform("base", hue + 0.24f * tiltY - 0.18f * tiltX)
        builder.child("relief", normals)
        val shader = builder.makeShader()
        builder.close()
        paint.shader = shader
        val b = relief.bounds
        canvas.drawRect(
            Rect(b.left * scale, b.top * scale, b.right * scale, b.bottom * scale),
            paint,
        )
        paint.shader = null
        shader.close()
        normals.close()
    }

    private fun normalized(x: Float, y: Float, z: Float): FloatArray {
        val length = sqrt(x * x + y * y + z * z)
        return floatArrayOf(x / length, y / length, z / length)
    }

    private const val VIEW_SWING = 0.7f

    private val SKSL =
        """
        uniform shader relief;
        uniform float invSize;
        uniform float3 view;
        uniform float3 halfway;
        uniform float2 glowAt;
        uniform float base;

        half4 main(float2 p) {
            half4 r = relief.eval(p);
            if (r.b < 0.004) return half4(0);
            float2 uv = p * invSize;
            float2 lean = float2(r.rg) * 2.0 - 1.0;
            float rim = dot(lean, lean);
            // A gentle dome across the body, so the flat middle still catches the light.
            float2 nxy = lean + (uv - 0.5) * (0.35 - 0.35 * rim);
            float3 n = float3(nxy, sqrt(max(1.0 - dot(nxy, nxy), 0.0)));
            float facing = saturate(dot(n, view));

            // Thin film: the body drifts through its hue, the rim runs through the rainbow. A
            // triangle wave smoothed by a cubic stands in for a cosine, within 2%.
            float phase = base + 0.12 * (uv.x - uv.y) + 0.12 * (1.0 - facing) + 0.5 * rim;
            float3 w = abs(fract(phase + float3(0.0, 0.33, 0.67)) - 0.5) * 4.0 - 1.0;
            float3 film = 0.5 + 0.5 * w * (1.5 - 0.5 * w * w);
            float3 body = film * (0.65 + 0.35 * film);

            // Light caught inside the jelly, sliding against the tilt.
            float2 g = uv - glowAt;
            float glow = saturate(1.0 - dot(g, g) * 6.9);
            glow *= glow;

            // The highlight: a power of 32 by squaring.
            float s = max(dot(n, halfway), 0.0);
            s *= s; s *= s; s *= s; s *= s; s *= s;

            float sheen = saturate((rim - 0.3) * 1.43);
            float3 color = body * (0.55 + 0.45 * facing)
                + glow * 0.5 * (0.4 * film + 0.6)
                + sheen * 0.4 * film.gbr
                + s * 0.8;
            half a = r.b;
            return half4(half3(saturate(color)) * a, a);
        }
        """
            .trimIndent()
}
