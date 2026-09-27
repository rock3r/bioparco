package dev.sebastiano.scrolleffects

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import java.util.IdentityHashMap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import org.jetbrains.skia.VertexMode

/**
 * Draws the [CardLayer]s the scene lays out: each mesh as Skia triangles textured with its card's
 * art, then the Stretch rim and the Glitch bars on top. It owns native Skia objects, so [close] it
 * when it leaves composition.
 */
internal class CarouselRenderer(art: List<ImageBitmap>) : AutoCloseable {
    private val images = art.map {
        Image.makeFromBitmap(it.asSkiaBitmap().apply { setImmutable() })
    }
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
    private val shaders: List<Shader> = images.map { it.makeShader(sampling = sampling) }
    private val texturePaints = shaders.map { Paint().apply { shader = it } }
    private val plainPaint = Paint()
    private val barPaint = Paint()
    private val glitchEffect = RuntimeEffect.makeForShader(GLITCH_SKSL)
    private val texCoords = IdentityHashMap<Mesh, FloatArray>()
    private val alphaColors = IdentityHashMap<Mesh, IntArray>()

    fun draw(canvas: Canvas, layers: List<CardLayer>, stage: CarouselStage) {
        for (layer in layers) {
            val mesh = layer.mesh ?: continue
            val image = images[layer.card]
            val tex = textureCoordinates(mesh, image.width.toFloat(), image.height.toFloat())
            val colors = mesh.alphas?.let { vertexColors(mesh, it) }
            if (layer.rgbSplit > 0f) {
                val texelsPerPixel = image.width / stage.cardWidth
                glitchShader(layer, texelsPerPixel, stage.unit).use { shader ->
                    val paint = Paint().apply { this.shader = shader }
                    canvas.drawVertices(
                        VertexMode.TRIANGLES,
                        mesh.positions,
                        colors,
                        tex,
                        null,
                        BlendMode.MODULATE,
                        paint,
                    )
                    paint.close()
                }
            } else {
                canvas.drawVertices(
                    VertexMode.TRIANGLES,
                    mesh.positions,
                    colors,
                    tex,
                    null,
                    BlendMode.MODULATE,
                    texturePaints[layer.card],
                )
            }
            layer.rim?.let {
                canvas.drawVertices(
                    VertexMode.TRIANGLES,
                    it.positions,
                    it.colors,
                    null,
                    null,
                    BlendMode.DST,
                    plainPaint,
                )
            }
            for (bar in layer.bars) {
                barPaint.color = bar.color.toInt()
                canvas.drawRect(Rect.makeXYWH(bar.left, bar.top, bar.width, bar.height), barPaint)
            }
        }
    }

    override fun close() {
        texturePaints.forEach { it.close() }
        shaders.forEach { it.close() }
        images.forEach { it.close() }
        plainPaint.close()
        barPaint.close()
        glitchEffect.close()
    }

    private fun glitchShader(layer: CardLayer, texelsPerPixel: Float, unit: Float): Shader {
        val builder = RuntimeShaderBuilder(glitchEffect)
        builder.uniform("split", layer.rgbSplit * texelsPerPixel)
        builder.uniform("seed", layer.glitchSeed)
        builder.uniform("strength", (layer.rgbSplit / (MAX_SPLIT * unit)).coerceIn(0f, 1f))
        builder.child("image", shaders[layer.card])
        return builder.makeShader().also { builder.close() }
    }

    private fun textureCoordinates(mesh: Mesh, width: Float, height: Float): FloatArray {
        val out = texCoords.getOrPut(mesh) { FloatArray(mesh.uvs.size) }
        var i = 0
        while (i < out.size) {
            out[i] = mesh.uvs[i] * width
            out[i + 1] = mesh.uvs[i + 1] * height
            i += 2
        }
        return out
    }

    private fun vertexColors(mesh: Mesh, alphas: FloatArray): IntArray {
        val out = alphaColors.getOrPut(mesh) { IntArray(mesh.vertexCount) }
        for (i in out.indices) {
            out[i] = ((alphas[i].coerceIn(0f, 1f) * 255f).toInt() shl 24) or 0xFFFFFF
        }
        return out
    }

    private companion object {
        /** The widest Glitch split, per card unit; the shader's strength is the split over this. */
        const val MAX_SPLIT = 5f

        /**
         * Pulls the red and blue channels sideways, darkens every other scanline and flips the
         * colours of a few random blocks. Works in the texture's pixel space.
         */
        val GLITCH_SKSL =
            """
            uniform shader image;
            uniform float split;
            uniform float seed;
            uniform float strength;

            half4 main(float2 p) {
                half4 base = image.eval(p);
                half4 red = image.eval(p + float2(split, 0));
                half4 blue = image.eval(p - float2(split, 0));
                half alpha = max(base.a, max(red.a, blue.a));
                half4 c = half4(red.r, base.g, blue.b, alpha);
                float line = step(0.5, fract(p.y / 3.0));
                c.rgb *= 1.0 - 0.14 * strength * line;
                float2 cell = floor(p / float2(40.0, 4.0));
                float n = fract(sin(dot(cell + seed, float2(12.9898, 78.233))) * 43758.5453);
                if (n > 1.0 - 0.02 * strength) {
                    c.rgb = mix(c.rgb, half3(c.g, c.b * 1.2, c.r) + half3(0.0, 0.25, 0.1) * c.a, 0.8);
                }
                c.rgb = min(c.rgb, half3(c.a));
                return c;
            }
            """
                .trimIndent()
    }
}
