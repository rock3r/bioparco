package dev.sebastiano.layoutmorph

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue

/**
 * Renders every arrangement, and frames of each flight between them, to PNGs for review. Skipped
 * unless `LAYOUT_MORPH_CAPTURE` names a directory:
 * ```
 * LAYOUT_MORPH_CAPTURE=/tmp/morph ./gradlew :layout-morph:jvmTest --tests '*ArrangementCapture*' --rerun
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class)
class ArrangementCapture {
    private val directory: String? = System.getenv("LAYOUT_MORPH_CAPTURE")

    @Test
    fun capture() {
        assumeTrue("Set LAYOUT_MORPH_CAPTURE to capture", directory != null)
        val out = File(directory!!).apply { mkdirs() }
        val motion = MorphMotion(ShopCollection.pieces)
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(SCALE)) { App(motion) }
        var nanos = 0L
        fun frames(count: Int) =
            repeat(count) {
                nanos += FRAME_NANOS
                scene.render(nanos).close()
            }
        fun save(name: String) {
            nanos += FRAME_NANOS
            val image = scene.render(nanos)
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("no PNG")
            File(out, "$name.png").writeBytes(png.bytes)
            image.close()
        }
        frames(30)
        save("0-grid")
        for (next in Arrangement.entries.drop(1) + Arrangement.Grid) {
            motion.select(next)
            frames(6)
            save("${next.ordinal}-${next.name.lowercase()}-a")
            frames(8)
            save("${next.ordinal}-${next.name.lowercase()}-b")
            frames(60)
            save("${next.ordinal}-${next.name.lowercase()}")
        }
        // A thrown helix, spinning fast.
        motion.select(Arrangement.Helix)
        frames(120)
        repeat(14) {
            motion.drag(26f)
            frames(1)
        }
        motion.release(1_400f)
        frames(10)
        save("6-thrown-a")
        frames(30)
        save("6-thrown-b")
        scene.close()
    }

    private companion object {
        const val SCALE = 1.5f
        const val WIDTH = (960 * SCALE).toInt()
        const val HEIGHT = (630 * SCALE).toInt()
        const val FRAME_NANOS = 16_666_667L
    }
}
