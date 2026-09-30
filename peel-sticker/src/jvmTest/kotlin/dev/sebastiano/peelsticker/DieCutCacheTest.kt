package dev.sebastiano.peelsticker

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * The built-in pictures' die-cuts ship baked (see [BAKED_DIE_CUTS]); this checks they still match a
 * fresh cut, so changing a picture, the border or the fillet can't leave them stale. To rewrite
 * them:
 * ```
 * BIOPARCO_UPDATE_DIE_CUTS=1 ./gradlew :peel-sticker:jvmTest --tests '*DieCutCacheTest*' --rerun
 * ```
 */
class DieCutCacheTest {
    @Test
    fun `the baked die-cuts match fresh ones`() {
        val update = System.getenv(UPDATE) != null
        for ((picture, path) in BAKED_DIE_CUTS) {
            val art = drawArt(picture)
            val fresh = cutAround(art)
            art.close()
            if (update) {
                val png = maskImage(fresh, StickerTexture.SIZE).encodeToData(EncodedImageFormat.PNG)
                File("src/jvmMain/resources/$path")
                    .apply { parentFile.mkdirs() }
                    .writeBytes(png!!.bytes)
                continue
            }
            val baked = assertNotNull(bakedDieCut(picture), "$path is missing. $HOW")
            // Anti-aliasing may differ by a hair between platforms; a stale cut differs everywhere
            // along its edge.
            val differing =
                fresh.indices.count {
                    abs((fresh[it].toInt() and BYTE) - (baked[it].toInt() and BYTE)) > 2
                }
            assertTrue(
                differing <= fresh.size / 1000,
                "$path is out of date: $differing texels differ. $HOW",
            )
        }
    }

    private companion object {
        const val UPDATE = "BIOPARCO_UPDATE_DIE_CUTS"
        const val BYTE = 0xFF
        const val HOW =
            "Rewrite it with $UPDATE=1 ./gradlew :peel-sticker:jvmTest --tests '*DieCutCacheTest*' --rerun"
    }
}
