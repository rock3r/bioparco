package dev.sebastiano.peelsticker

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.io.File
import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeTrue

/**
 * Draws peels with Skia's OpenGL backend and in software, and checks they match. A GPU samples
 * textures differently from Skia's CPU backend, which is how a dotted line along the fold showed on
 * Metal and nowhere else; this catches that kind of thing without a Mac.
 *
 * Linux only, and skipped unless `BIOPARCO_GL_PARITY` is set. It needs an X display with GLX, which
 * Xvfb and Mesa's software OpenGL (llvmpipe) provide:
 * ```
 * BIOPARCO_GL_PARITY=1 xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
 *     ./gradlew :peel-sticker:jvmTest --tests '*GpuParity*' --rerun
 * ```
 *
 * The frames land in `peel-sticker/build/reports/gpu-parity/`, `-gl` and `-cpu` side by side.
 */
class GpuParity {
    // Held for the whole test: JNA closes a library it no longer references, which took the
    // current GL context with it, now and then, before Skia could use it.
    private val x11 by lazy { NativeLibrary.getInstance("X11") }
    private val gl by lazy { NativeLibrary.getInstance("GL") }

    @Test
    fun peelsMatchInSoftwareAndOnOpenGl() {
        assumeTrue("Set BIOPARCO_GL_PARITY to run", System.getenv("BIOPARCO_GL_PARITY") != null)
        assumeTrue("Needs GLX", System.getProperty("os.name").startsWith("Linux"))
        makeGlxContextCurrent()
        val context = DirectContext.makeGL()
        val out = File("build/reports/gpu-parity").apply { mkdirs() }
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val worst = mutableListOf<Pair<String, Int>>()
        for ((picture, peels) in PEELS) {
            val texture = printSticker(picture, measurer, FontFamily.Default)
            val renderer = StickerRenderer(texture)
            for ((name, peel) in peels) {
                for (share in SHARES) {
                    val frame = frameOf(texture, peel, share)
                    val label = "$name-${(share * 100).toInt()}"
                    val gl = Surface.makeRenderTarget(context, false, INFO)
                    val cpu = Surface.makeRasterN32Premul(WIDTH, HEIGHT)
                    val onGl = drawn(gl, renderer, frame, gpu = true, context)
                    val inSoftware = drawn(cpu, renderer, frame, gpu = false, null)
                    worst += label to largestDifference(onGl, inSoftware)
                    save(onGl, File(out, "$label-gl.png"))
                    save(inSoftware, File(out, "$label-cpu.png"))
                    gl.close()
                    cpu.close()
                }
            }
            renderer.close()
            texture.close()
        }
        context.close()
        println(worst.joinToString("\n") { (label, diff) -> "$label: $diff" })
        assertTrue(worst.all { it.second <= TOLERANCE }, "OpenGL and software differ: $worst")
    }

    /** The peel [share] of the way along, at the recordings' window size at density 2. */
    private fun frameOf(texture: StickerTexture, peel: FloatArray, share: Float): StickerFrame {
        val fit = minOf(WIDTH * FIT_WIDTH, HEIGHT * FIT_HEIGHT)
        val pixels = round(StickerTexture.SIZE * fit / StickerTexture.CUT)
        val texScale = StickerTexture.SIZE / pixels
        val originX = round((WIDTH - pixels) / 2f)
        val originY = round((HEIGHT - pixels) / 2f)
        fun x(fraction: Float) = WIDTH / 2f + fit * fraction
        fun y(fraction: Float) = HEIGHT / 2f + fit * fraction
        // From (peel[0], peel[1]) to (peel[2], peel[3]).
        val fold =
            peelFold(
                grabX = x(peel[0]),
                grabY = y(peel[1]),
                pointerX = x(peel[0] + (peel[2] - peel[0]) * share),
                pointerY = y(peel[1] + (peel[3] - peel[1]) * share),
                extent = { dx, dy ->
                    originX * dx +
                        originY * dy +
                        texture.silhouette.extent(dx, dy) * StickerTexture.CELL / texScale
                },
                size = StickerTexture.CUT / texScale,
            )
        return StickerFrame(originX, originY, texScale, fold, ShineMode.Prism, 0f, 0f, 0f, 1f, 2f)
    }

    private fun drawn(
        surface: Surface,
        renderer: StickerRenderer,
        frame: StickerFrame,
        gpu: Boolean,
        context: DirectContext?,
    ): Bitmap {
        surface.canvas.clear(PAGE)
        renderer.draw(surface.canvas, Rect.makeWH(WIDTH.toFloat(), HEIGHT.toFloat()), frame, gpu)
        context?.flushAndSubmit(surface, true)
        return Bitmap().apply {
            allocPixels(INFO)
            surface.readPixels(this, 0, 0)
        }
    }

    private fun largestDifference(a: Bitmap, b: Bitmap): Int {
        val first = a.readPixels() ?: return Int.MAX_VALUE
        val second = b.readPixels() ?: return Int.MAX_VALUE
        var worst = 0
        for (i in first.indices) {
            worst = maxOf(worst, abs((first[i].toInt() and BYTE) - (second[i].toInt() and BYTE)))
        }
        return worst
    }

    private fun save(bitmap: Bitmap, file: File) {
        val data = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG) ?: return
        file.writeBytes(data.bytes)
    }

    /** An offscreen GLX context on the default display, current on this thread, for Skia. */
    private fun makeGlxContextCurrent() {
        val display = x11.getFunction("XOpenDisplay").invokePointer(arrayOf<Any?>(null))
        assumeTrue("Needs an X display", display != null)
        val count = IntByReference()
        val configs =
            gl.getFunction("glXChooseFBConfig")
                .invokePointer(arrayOf(display, 0, CONFIG_ATTRIBUTES, count))
        assumeTrue("Needs a GLX pbuffer config", configs != null && count.value > 0)
        val config: Pointer = configs.getPointer(0)
        val pbuffer =
            gl.getFunction("glXCreatePbuffer")
                .invokePointer(arrayOf(display, config, PBUFFER_ATTRIBUTES))
        val context =
            gl.getFunction("glXCreateNewContext")
                .invokePointer(arrayOf(display, config, GLX_RGBA_TYPE, null, 1))
        val current =
            gl.getFunction("glXMakeContextCurrent")
                .invokeInt(arrayOf(display, pbuffer, pbuffer, context))
        assumeTrue("Needs a current GLX context", current != 0)
    }

    private companion object {
        const val WIDTH = 1520
        const val HEIGHT = 1104
        const val FIT_WIDTH = 0.484f
        const val FIT_HEIGHT = 0.708f
        const val PAGE = 0xFFF2F2F4.toInt()
        const val BYTE = 0xFF

        /** In 255ths, per channel. Rounding differs; a missing or wrong pixel is far above it. */
        const val TOLERANCE = 8
        val INFO: ImageInfo = ImageInfo.makeN32Premul(WIDTH, HEIGHT)
        val SHARES = listOf(0.35f, 0.6f, 1f)

        /** The recordings' peels, from and to, as shares of the die-cut around its centre. */
        val PEELS =
            listOf(
                StickerPicture.Ex to
                    listOf(
                        "x-tip" to floatArrayOf(0.37f, -0.43f, -0.25f, 0.3f),
                        "x-arm" to floatArrayOf(-0.42f, 0.44f, 0.45f, -0.1f),
                    ),
                StickerPicture.Gee to listOf("g" to floatArrayOf(0.41f, 0.22f, -0.3f, -0.2f)),
            )

        const val GLX_RGBA_TYPE = 0x8014

        /** Pbuffer-capable, RGBA8888, with a stencil buffer for Skia's clips. */
        val CONFIG_ATTRIBUTES =
            intArrayOf(0x8010, 0x4, 0x8011, 0x1, 8, 8, 9, 8, 10, 8, 11, 8, 13, 8, 0)
        val PBUFFER_ATTRIBUTES = intArrayOf(0x8041, 16, 0x8040, 16, 0)
    }
}
