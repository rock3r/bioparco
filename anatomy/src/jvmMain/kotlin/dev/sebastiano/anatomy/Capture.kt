package dev.sebastiano.anatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.kit.Palette
import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/*
 * The original's `scripts/capture.mjs`, for Compose: renders one figure in its card offscreen and
 * writes PNGs, so a figure can be reviewed the way a demanding reviewer would, at any width, in
 * both themes, at moments of its motion, under the pointer, and in close-up.
 *
 *   ./gradlew :anatomy:capture --args="dial-indicator --out shots/dial.png"
 *   … --light | --dark            the theme (light by default: design for light first)
 *   … --width 390                 the card width in dp (650 by default)
 *   … --scale 2                   device pixels per dp (2 by default)
 *   … --times 0,1.2,2.4           shots at these seconds after the first frame: name-<ms>.png
 *   … --hover 0.7,0.5             the pointer at 70% across and 50% down the card, from the start
 *   … --key Right,Right           keys sent to the figure after it takes focus
 *   … --zoom 300,120,120,80       a close-up of x,y,w,h in card dp, at --zoom-scale (4)
 *   … --reduced                   reduced motion
 */

private const val FRAME_NS = 16_666_667L
private const val TALLEST_DP = 2_400

private class Options(
    val figure: Figure,
    val out: File,
    val dark: Boolean,
    val width: Int,
    val scale: Float,
    val times: List<Double>,
    val hover: Pair<Float, Float>?,
    val keys: List<String>,
    val zoom: List<Float>?,
    val zoomScale: Float,
    val reduced: Boolean,
)

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val options = parse(args) ?: exitProcess(2)
    val density = if (options.zoom != null) options.zoomScale else options.scale
    val palette = if (options.dark) Palette.Dark else Palette.Light
    var bounds = IntSize.Zero
    val widthPx = ((options.width + 48) * density).toInt()
    val scene =
        ImageComposeScene(widthPx, (TALLEST_DP * density).toInt(), Density(density)) {
            IntUiTheme(isDark = options.dark) {
                Box(
                    Modifier.width((options.width + 48).dp)
                        .background(palette.page)
                        .onGloballyPositioned { bounds = it.size }
                        .padding(24.dp)
                ) {
                    FigureContent(options.figure, palette, options.reduced, Modifier.fillMaxWidth())
                }
            }
        }
    try {
        var time = 0L
        scene.render(time)
        options.hover?.let { (fx, fy) ->
            val at = Offset((24 + fx * options.width) * density, bounds.height * fy)
            scene.sendPointerEvent(PointerEventType.Enter, at)
            scene.sendPointerEvent(PointerEventType.Move, at)
        }
        if (options.keys.isNotEmpty()) {
            press(scene, Key.Tab)
            for (name in options.keys) press(scene, keyOf(name))
        }
        val shots = options.times.ifEmpty { listOf(0.0) }
        for (seconds in shots) {
            val until = (seconds * 1e9).toLong()
            while (time < until) {
                time += FRAME_NS
                scene.render(time)
            }
            val image = scene.render(time)
            val file =
                if (shots.size == 1) options.out
                else
                    options.out.resolveSibling(
                        "${options.out.nameWithoutExtension}-${(seconds * 1000).toLong()}.png"
                    )
            save(crop(image, options, bounds, density), file)
            println("wrote ${file.path}")
        }
    } finally {
        scene.close()
    }
    exitProcess(0)
}

private fun crop(image: Image, options: Options, bounds: IntSize, density: Float): Image {
    val zoom = options.zoom
    val rect =
        if (zoom == null) {
            Rect.makeXYWH(0f, 0f, bounds.width.toFloat(), bounds.height.toFloat())
        } else {
            Rect.makeXYWH(
                (zoom[0] + 24) * density,
                (zoom[1] + 24) * density,
                zoom[2] * density,
                zoom[3] * density,
            )
        }
    val surface = Surface.makeRasterN32Premul(rect.width.toInt(), rect.height.toInt())
    surface.canvas.drawImageRect(image, rect, Rect.makeWH(rect.width, rect.height))
    return surface.makeImageSnapshot()
}

private fun save(image: Image, file: File) {
    file.absoluteFile.parentFile.mkdirs()
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode ${file.path}")
    file.writeBytes(data.bytes)
}

// Synthetic key events are internal API: fine for a review tool, never for the figures.
@OptIn(InternalComposeUiApi::class)
private fun press(scene: ImageComposeScene, key: Key) {
    scene.sendKeyEvent(KeyEvent(key = key, type = KeyEventType.KeyDown))
    scene.sendKeyEvent(KeyEvent(key = key, type = KeyEventType.KeyUp))
}

private fun keyOf(name: String): Key =
    when (name) {
        "Right",
        "ArrowRight" -> Key.DirectionRight
        "Left",
        "ArrowLeft" -> Key.DirectionLeft
        "Up",
        "ArrowUp" -> Key.DirectionUp
        "Down",
        "ArrowDown" -> Key.DirectionDown
        "Home" -> Key.MoveHome
        "End" -> Key.MoveEnd
        "Space" -> Key.Spacebar
        "Enter" -> Key.Enter
        "Escape" -> Key.Escape
        "Tab" -> Key.Tab
        else -> error("unknown key $name")
    }

private fun parse(args: Array<String>): Options? {
    val figure = args.firstOrNull()?.let { Figure.of(it) }
    if (figure == null) {
        System.err.println(
            "usage: capture <figure> [options]; figures: " + Figure.entries.joinToString { it.id }
        )
        return null
    }
    val flags = args.drop(1)
    fun value(name: String): String? =
        flags.indexOf(name).takeIf { it >= 0 }?.let { flags.getOrNull(it + 1) }
    fun numbers(name: String): List<Float>? = value(name)?.split(',')?.map { it.trim().toFloat() }
    val dark = "--dark" in flags
    return Options(
        figure = figure,
        out = File(value("--out") ?: "build/anatomy/${figure.id}${if (dark) "-dark" else ""}.png"),
        dark = dark,
        width = value("--width")?.toInt() ?: PAGE_WIDTH,
        scale = value("--scale")?.toFloat() ?: 2f,
        times = numbers("--times")?.map { it.toDouble() }.orEmpty(),
        hover = numbers("--hover")?.let { it[0] to it[1] },
        keys = value("--key")?.split(',')?.map { it.trim() }.orEmpty(),
        zoom = numbers("--zoom"),
        zoomScale = value("--zoom-scale")?.toFloat() ?: 4f,
        reduced = "--reduced" in flags,
    )
}
