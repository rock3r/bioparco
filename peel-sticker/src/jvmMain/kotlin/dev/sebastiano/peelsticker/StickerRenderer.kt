package dev.sebastiano.peelsticker

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

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
 * Draws a [StickerTexture] with one SkSL shader, in four passes: the soft shadow of the part still
 * on the table, that part, the shadow the lifted part casts, and the lifted part itself. It owns
 * native Skia objects, so [close] it when it leaves composition.
 */
internal class StickerRenderer(private val texture: StickerTexture) : AutoCloseable {
    private val effect = RuntimeEffect.makeForShader(PEEL_SKSL)
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
    private val front =
        texture.front.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, sampling, null)
    private val back =
        texture.back.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, sampling, null)
    private val paint = Paint()

    /**
     * Each pass only shades where it can draw: the table passes the sticker's own box, the lifted
     * passes the box the peel can reach, clipped to the stage [bounds]. Blurred shadows spread past
     * their box by themselves.
     */
    fun draw(canvas: Canvas, bounds: Rect, frame: StickerFrame) {
        val d = frame.density
        val side = StickerTexture.SIZE / frame.texScale
        val sticker = Rect.makeXYWH(frame.originX, frame.originY, side, side)
        shadow(canvas, sticker, frame, TABLE_COVERAGE, 1f * d, 2.5f * d, 2.5f * d, TABLE_SHADOW)
        pass(canvas, clip(sticker, bounds), frame, TABLE)
        val fold = frame.fold ?: return
        val reach = fold.reach(sticker.left, sticker.top, sticker.right, sticker.bottom)
        val lifted = clip(Rect(reach[0] - 2f, reach[1] - 2f, reach[2] + 2f, reach[3] + 2f), bounds)
        val lift = fold.curve.tight + fold.curve.loose
        shadow(
            canvas,
            lifted,
            frame,
            LIFTED_COVERAGE,
            1.5f * d + lift * 0.06f,
            4f * d + lift * 0.16f,
            5f * d + lift * 0.12f,
            LIFTED_SHADOW,
        )
        pass(canvas, lifted, frame, LIFTED)
    }

    override fun close() {
        paint.close()
        front.close()
        back.close()
        effect.close()
    }

    private fun shadow(
        canvas: Canvas,
        area: Rect,
        frame: StickerFrame,
        layer: Float,
        dx: Float,
        dy: Float,
        sigma: Float,
        alpha: Float,
    ) {
        ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL).use { blur ->
            canvas.save()
            canvas.translate(dx, dy)
            paint.imageFilter = blur
            paint.alpha = (alpha * 255f).toInt()
            pass(canvas, area, frame, layer)
            paint.imageFilter = null
            paint.alpha = 255
            canvas.restore()
        }
    }

    private fun pass(canvas: Canvas, bounds: Rect, frame: StickerFrame, layer: Float) {
        shaderFor(frame, layer).use { shader ->
            paint.shader = shader
            canvas.drawRect(bounds, paint)
            paint.shader = null
        }
    }

    private fun shaderFor(frame: StickerFrame, layer: Float): Shader {
        val builder = RuntimeShaderBuilder(effect)
        val fold = frame.fold
        builder.uniform("origin", frame.originX, frame.originY)
        builder.uniform("texScale", frame.texScale)
        builder.uniform("peeling", if (fold != null) 1f else 0f)
        builder.uniform("axis", fold?.axisX ?: 0f, fold?.axisY ?: 0f)
        builder.uniform("dir", fold?.dirX ?: 1f, fold?.dirY ?: 0f)
        builder.uniform("tight", fold?.curve?.tight ?: 0f)
        builder.uniform("loose", fold?.curve?.loose ?: 0f)
        builder.uniform("layer", layer)
        builder.uniform("mode", frame.mode.ordinal.toFloat())
        builder.uniform("pointer", frame.pointerX, frame.pointerY)
        builder.uniform("shine", frame.shine)
        builder.uniform("time", frame.time)
        builder.uniform("span", StickerTexture.CUT)
        builder.child("front", front)
        builder.child("back", back)
        return builder.makeShader().also { builder.close() }
    }

    private companion object {
        const val TABLE = 0f
        const val LIFTED = 1f
        const val TABLE_COVERAGE = 2f
        const val LIFTED_COVERAGE = 3f
        const val TABLE_SHADOW = 0.34f
        const val LIFTED_SHADOW = 0.22f
    }
}

private fun clip(rect: Rect, bounds: Rect) =
    Rect(
        maxOf(rect.left, bounds.left),
        maxOf(rect.top, bounds.top),
        minOf(rect.right, bounds.right),
        minOf(rect.bottom, bounds.bottom),
    )

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        close()
    }

/**
 * The whole sticker in one shader. Stage pixels map to texels through `origin` and `texScale`.
 *
 * With no fold, layer 0 is the whole sticker. With a fold, a pixel `q` stage pixels along `dir`
 * from the axis shows the table (q < 0, layer 0), or what the lifted sheet puts above it (layer 1):
 * first the backing past the top of the tight curl, then the face on its rising side. The depths
 * come from inverting [PeelCurve], which the Kotlin tests cover. Layers 2 and 3 are the coverage of
 * layers 0 and 1, for their shadows.
 *
 * `face` applies the shine to the front, in texels, around `pointer`.
 */
private val PEEL_SKSL =
    """
    uniform shader front;
    uniform shader back;
    uniform float2 origin;
    uniform float texScale;
    uniform float peeling;
    uniform float2 axis;
    uniform float2 dir;
    uniform float tight;
    uniform float loose;
    uniform float layer;
    uniform float mode;
    uniform float2 pointer;
    uniform float shine;
    uniform float time;
    uniform float span;

    const float HALF_PI = 1.5707963;
    const float TAU = 6.2831853;

    float2 tex(float2 p) { return (p - origin) * texScale; }

    float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

    float4 onWhite(float4 c) { return float4(c.rgb + (1.0 - c.a), 1.0); }

    float4 white(float4 c, float r) {
        float reach = 0.2 * span;
        float glare = shine * exp(-r * r / (2.0 * reach * reach));
        return float4(mix(c.rgb, float3(c.a), 0.62 * glare), c.a);
    }

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

    float4 prism(float2 t, float4 c, float2 u, float r) {
        // Like a lens: the split grows away from the light, then fades out across the sticker.
        float w = shine * smoothstep(0.0, 0.3 * span, r) * (1.0 - smoothstep(0.42 * span, 0.68 * span, r));
        float2 off = u * 13.0 * w;
        float4 base = onWhite(c);
        float red = onWhite(front.eval(t + off)).r;
        float blue = onWhite(front.eval(t - off)).b;
        return float4(float3(red, base.g, blue) * c.a, c.a);
    }

    float4 ripple(float2 t, float4 c, float2 u, float r) {
        float envelope = shine * exp(-r / (0.11 * span));
        float wave = sin(r / 30.0 * TAU - time * 6.5);
        float4 moved = front.eval(t - u * wave * envelope * 3.0);
        float3 rgb = moved.a > 0.0 ? moved.rgb / moved.a * c.a : c.rgb;
        rgb *= 1.0 + 0.07 * wave * envelope;
        rgb += c.a * 0.06 * max(wave, 0.0) * envelope;
        return float4(min(rgb, float3(c.a)), c.a);
    }

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

    float4 face(float2 t) {
        float4 c = front.eval(t);
        if (c.a <= 0.0 || shine <= 0.001) return c;
        float2 away = t - pointer;
        float r = length(away);
        float2 u = r > 0.001 ? away / r : float2(0.0);
        if (mode < 0.5) return white(c, r);
        if (mode < 1.5) return sparkle(t, c, r);
        if (mode < 2.5) return prism(t, c, u, r);
        if (mode < 3.5) return ripple(t, c, u, r);
        return halftone(t, c);
    }

    half4 main(float2 p) {
        bool coverage = layer > 1.5;
        bool table = layer < 0.5 || (layer > 1.5 && layer < 2.5);
        float q = peeling > 0.5 ? dot(p - axis, dir) : -1.0;
        if (table) {
            if (q >= 0.0) return half4(0.0);
            float4 c = coverage ? front.eval(tex(p)) : face(tex(p));
            return coverage ? half4(0.0, 0.0, 0.0, c.a) : half4(c);
        }
        if (peeling < 0.5) return half4(0.0);

        float2 foot = p - dir * q;
        float tightEnd = HALF_PI * tight;
        float looseEnd = tightEnd + HALF_PI * loose;

        float4 top = float4(0.0);
        if (q <= tight) {
            float depth;
            float light;
            if (loose > 0.0 && q >= tight - loose) {
                float phi = acos(clamp((q - tight + loose) / loose, -1.0, 1.0));
                depth = tightEnd + loose * phi;
                float sheen = exp(-(phi - 0.5) * (phi - 0.5) / 0.08);
                light = 0.84 + 0.14 * sin(phi) + 0.06 * sheen;
            } else {
                depth = looseEnd + (tight - loose - q);
                light = 0.98;
            }
            float4 b = back.eval(tex(foot + dir * depth));
            top = float4(min(b.rgb * light, float3(b.a)), b.a);
        }

        float4 under = float4(0.0);
        if (tight > 0.0 && q >= 0.0 && q <= tight) {
            float theta = asin(q / tight);
            float4 f = face(tex(foot + dir * tight * theta));
            under = float4(f.rgb * (0.55 + 0.45 * cos(theta)), f.a);
        }

        float4 c = top + under * (1.0 - top.a);
        return coverage ? half4(0.0, 0.0, 0.0, c.a) : half4(c);
    }
    """
        .trimIndent()
