package dev.sebastiano.peelsticker

import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeTrue

/**
 * What a pixel costs Skia on the CPU, rung by rung: fills, copies, filtered draws, runtime shaders
 * with samples, maths and branches. The results in `PERFORMANCE.md` and `docs/TRACING.md` come from
 * here. Skipped unless `BIOPARCO_BENCH` is set:
 * ```
 * BIOPARCO_BENCH=1 ./gradlew :peel-sticker:jvmTest --tests '*CpuCostLadder*' --rerun
 * ```
 *
 * The table lands in `peel-sticker/build/reports/cpu-cost-ladder.md`. It is single-threaded CPU
 * raster, the Xvfb case; a GPU draws these differently.
 */
class CpuCostLadder {
    private val surface = Surface.makeRasterN32Premul(SIDE, SIDE)
    private val canvas = surface.canvas
    private val rect = Rect.makeWH(SIDE.toFloat(), SIDE.toFloat())
    private val paint = Paint()
    private val rows = mutableListOf<Pair<String, Double>>()

    @Test
    fun ladder() {
        assumeTrue("Set BIOPARCO_BENCH to run the ladder", System.getenv("BIOPARCO_BENCH") != null)
        val image = solidImage(SIDE)
        val big = solidImage(BIG)
        drawRungs(image)
        shaderRungs(image, big)
        branchRungs(big)
        jvmRung()
        report()
    }

    private fun drawRungs(image: Image) {
        rung("Solid fill, opaque") { canvas.drawRect(rect, paint.apply { color = OPAQUE }) }
        rung("Solid fill, half transparent") { canvas.drawRect(rect, paint.apply { color = HALF }) }
        paint.color = OPAQUE
        rung("Image copied onto whole pixels") { canvas.drawImage(image, 0f, 0f, paint) }
        val shifted = Rect.makeXYWH(0.3f, 0.6f, SIDE.toFloat(), SIDE.toFloat())
        rung("Image drawn bilinear, fractional offset") {
            canvas.drawImageRect(image, rect, shifted, SamplingMode.LINEAR, paint, true)
        }
    }

    private fun shaderRungs(image: Image, big: Image) {
        val oneToOne = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, LINEAR, null)
        val minified = big.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, LINEAR, null)
        val mipmapped = big.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, TRILINEAR, null)
        shaderRung("Shader: constant colour", "half4 main(float2 p) { return half4(0.5); }")
        shaderRung(
            "Shader: one sample, 1:1",
            "half4 main(float2 p) { return img.eval(p); }",
            oneToOne,
        )
        shaderRung(
            "Shader: three samples, 1:1",
            "half4 main(float2 p) { return (img.eval(p) + img.eval(p + 3.0) + img.eval(p - 3.0)) / 3.0; }",
            oneToOne,
        )
        shaderRung(
            "Shader: one sample, 2.7x smaller, bilinear",
            "half4 main(float2 p) { return img.eval(p * 2.7); }",
            minified,
        )
        shaderRung(
            "Shader: one sample, 2.7x smaller, mipmapped",
            "half4 main(float2 p) { return img.eval(p * 2.7); }",
            mipmapped,
        )
        shaderRung(
            "Shader: 8 × sin, cos, sqrt",
            "half4 main(float2 p) { $HEAVY return half4(fract(a)); }",
        )
    }

    private fun branchRungs(big: Image) {
        val img = big.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, LINEAR, null)
        val samples =
            "(img.eval(p * 2.7) + img.eval(p * 2.6) + img.eval(p * 2.5) + img.eval(p * 2.4)) / 4.0"
        shaderRung("Shader: 4 samples, always", "half4 main(float2 p) { return $samples; }", img)
        shaderRung(
            "Shader: 4 samples in an untaken if",
            "half4 main(float2 p) { if (u > 0.5) { return $samples; } return half4(0.5); }",
            img,
        )
        shaderRung(
            "Shader: 4 samples after an early return",
            "half4 main(float2 p) { if (u < 0.5) return half4(0.5); return $samples; }",
            img,
        )
        shaderRung(
            "Shader: maths after an early return",
            "half4 main(float2 p) { if (u < 0.5) return half4(0.5); $HEAVY return half4(fract(a)); }",
        )
        shaderRung(
            "Shader: maths in an if taken by half the rows",
            "half4 main(float2 p) { if (p.y < 256.0) { $HEAVY return half4(fract(a)); } return half4(0.5); }",
        )
        shaderRung(
            "Shader: maths in an if taken by every other pixel",
            "half4 main(float2 p) { if (fract(p.x * 0.5) < 0.5) { $HEAVY return half4(fract(a)); } " +
                "return half4(0.5); }",
        )
    }

    /** The same maths as the shader rung, as a plain single-threaded Kotlin loop. */
    private fun jvmRung() {
        val out = FloatArray(SIDE * SIDE)
        rung("JVM loop: 8 × sin, cos, sqrt") {
            for (y in 0 until SIDE) {
                for (x in 0 until SIDE) {
                    var a = 0f
                    for (i in 1..8) a += sin(x * 0.01f * i) * cos(y * 0.013f) + sqrt(abs(a) + 1f)
                    out[y * SIDE + x] = a
                }
            }
        }
    }

    private fun shaderRung(label: String, main: String, img: Shader? = null) {
        val effect = RuntimeEffect.makeForShader("uniform float u; uniform shader img; $main")
        val fallback = if (img == null) solidImage(1).makeShader() else null
        rung(label) {
            val builder = RuntimeShaderBuilder(effect)
            builder.uniform("u", 0f)
            builder.child("img", img ?: fallback!!)
            val shader = builder.makeShader()
            paint.shader = shader
            canvas.drawRect(rect, paint)
            paint.shader = null
            shader.close()
            builder.close()
        }
        effect.close()
    }

    private fun rung(label: String, draw: () -> Unit) {
        repeat(WARM_UP) { draw() }
        val start = System.nanoTime()
        repeat(RUNS) { draw() }
        rows += label to (System.nanoTime() - start).toDouble() / RUNS / (SIDE * SIDE)
    }

    private fun report() {
        val table = buildString {
            appendLine("| Rung | ns per pixel |")
            appendLine("|---|---|")
            for ((label, nanos) in rows) appendLine("| $label | ${"%.2f".format(nanos)} |")
        }
        println(table)
        File("build/reports").apply { mkdirs() }.resolve("cpu-cost-ladder.md").writeText(table)
    }

    private fun solidImage(side: Int): Image {
        val source = Surface.makeRasterN32Premul(side, side)
        source.canvas.clear(BLUE)
        return source.makeImageSnapshot().also { source.close() }
    }

    private companion object {
        const val SIDE = 512
        const val BIG = 1400
        const val WARM_UP = 3
        const val RUNS = 20
        const val OPAQUE = 0xFF808080.toInt()
        const val HALF = 0x80808080.toInt()
        const val BLUE = 0xFF3366CC.toInt()
        val LINEAR = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        val TRILINEAR = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
        const val HEAVY =
            "float a = 0.0; for (int i = 1; i <= 8; i++) " +
                "{ a += sin(p.x * 0.01 * float(i)) * cos(p.y * 0.013) + sqrt(abs(a) + 1.0); }"
    }
}
