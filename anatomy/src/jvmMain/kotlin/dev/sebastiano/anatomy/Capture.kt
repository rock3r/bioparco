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
import dev.sebastiano.anatomy.kit.Drawing
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.MarkRecorder
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.ShaderFrames
import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.lineEnds
import dev.sebastiano.anatomy.kit.turn.TurnHook
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
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
 *   … --click 0.5,0.4             a press and release there, after the first frame
 *   … --key Right,Right           keys sent to the figure after it takes focus
 *   … --zoom 300,120,120,80       a close-up of x,y,w,h in card dp, at --zoom-scale (4)
 *   … --reduced                   reduced motion
 *   … --lines                     check that every line ends on something (`LineEnds.kt`): print
 *                                 `line ends: N`, ring each loose end in red on the PNG, write the
 *                                 report to a .txt beside it, and exit 1 if any end floats
 *   … --turn dial=37,lift=1,pod=0.5
 *                                 hold a turning figure at these values (`TurnHook`, the original's
 *                                 `__isoTurn.set`): its groups, and figure values such as a pod's time
 *   … --audit                     run the geometry audit (`kit/audit`) on a figure that records
 *                                 itself (`AUDITED_FIGURES`): print the report, write it to
 *                                 <name>-audit.txt beside the PNG, and exit 1 on any failure;
 *                                 a turning figure (`TurningFigure`) then runs the orbit audit
 *                                 (`kit/turn/Orbit.kt`) and ends `turn audit passed` or failed
 *   … --audit --quick             the orbit audit at about a tenth of the states, for iteration
 *   … --audit --verbose           the orbit audit's progress, check by check
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
    val click: Pair<Float, Float>?,
    val keys: List<String>,
    val zoom: List<Float>?,
    val zoomScale: Float,
    val reduced: Boolean,
    val lines: Boolean,
    val audit: Boolean,
    val turn: Map<String, Double>?,
    val quick: Boolean,
    val verbose: Boolean,
)

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val options = parse(args) ?: exitProcess(2)
    val audited = auditedFigure(options)
    val density = if (options.zoom != null) options.zoomScale else options.scale
    val palette = if (options.dark) Palette.Dark else Palette.Light
    var bounds = IntSize.Zero
    val drawing = if (options.lines) Drawing() else null
    MarkRecorder.active = drawing
    TurnHook.values = options.turn
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
    var floating = 0
    try {
        var time = 0L
        scene.render(time)
        fun at(fraction: Pair<Float, Float>) =
            Offset((24 + fraction.first * options.width) * density, bounds.height * fraction.second)
        options.hover?.let {
            scene.sendPointerEvent(PointerEventType.Enter, at(it))
            scene.sendPointerEvent(PointerEventType.Move, at(it))
        }
        options.click?.let {
            scene.sendPointerEvent(PointerEventType.Move, at(it))
            scene.sendPointerEvent(PointerEventType.Press, at(it))
            scene.sendPointerEvent(PointerEventType.Release, at(it))
        }
        sendKeys(scene, options.keys)
        val shots = options.times.ifEmpty { listOf(0.0) }
        for (seconds in shots) {
            val until = (seconds * 1e9).toLong()
            ShaderFrames.skip = true
            while (time < until) {
                time += FRAME_NS
                scene.render(time)
            }
            ShaderFrames.skip = false
            val image = scene.render(time)
            val file =
                if (shots.size == 1) options.out
                else
                    options.out.resolveSibling(
                        "${options.out.nameWithoutExtension}-${(seconds * 1000).toLong()}.png"
                    )
            val rect = cropRect(options, bounds, density)
            val loose =
                if (drawing == null) emptyList()
                else checkLines(drawing, palette, density, rect, file).also { floating += it.size }
            save(crop(image, rect, loose), file)
            println("wrote ${file.path}")
        }
    } finally {
        scene.close()
    }
    val failing = auditBeside(audited, options)
    exitProcess(if (floating + failing > 0) 1 else 0)
}

/** Audits [figure], if any, writing the report beside the PNG; returns how many problems fail. */
private fun auditBeside(figure: AuditedFigure?, options: Options): Int {
    if (figure == null) return 0
    val png = options.out
    val log = png.resolveSibling("${png.nameWithoutExtension}-audit.txt")
    return auditFigure(figure, log, options.quick, options.verbose)
}

/** The figure's recorder, with `--audit`; exits if the figure doesn't record itself. */
private fun auditedFigure(options: Options): AuditedFigure? {
    if (!options.audit) return null
    return AUDITED_FIGURES[options.figure]
        ?: run {
            System.err.println(
                "${options.figure.id} records no shapes for the audit (see AUDITED_FIGURES)"
            )
            exitProcess(2)
        }
}

/**
 * Runs the line-end check on the frame [drawing] holds, prints the report and writes it beside
 * [file] as a .txt, and returns where to ring each loose end, in device px within [rect].
 */
private fun checkLines(
    drawing: Drawing,
    palette: Palette,
    density: Float,
    rect: Rect,
    file: File,
): List<Offset> {
    val report = lineEnds(drawing.marks, palette)
    fun device(at: Flat) = drawing.origin + at * drawing.scale
    val text = buildString {
        appendLine(
            "line ends: ${report.loose.size} of ${report.ends} ends on ${report.lines} lines float"
        )
        for (loose in report.loose) {
            val card = device(loose.at) * (1.0 / density) - Flat(24.0, 24.0)
            val gap =
                if (loose.gap.isInfinite()) "nothing nearby"
                else "${String.format(Locale.ROOT, "%.2f", loose.gap)} from anything"
            append("  ${loose.what}  ${loose.end.name.lowercase()}")
            if (loose.mark) append(" (a mark: neither end rooted)")
            append("  $gap   at card ${oneDecimal(card.x)},${oneDecimal(card.y)}")
            append("   view ${oneDecimal(loose.at.x)},${oneDecimal(loose.at.y)}")
            append("   length ${oneDecimal(loose.length)}   d ${loose.d}…")
            if (loose.alpha < 1)
                append("   opacity ${String.format(Locale.ROOT, "%.2f", loose.alpha)}")
            appendLine()
        }
    }
    print(text)
    val log = file.resolveSibling("${file.nameWithoutExtension}.txt")
    log.absoluteFile.parentFile.mkdirs()
    log.writeText(text)
    println("wrote ${log.path}")
    return report.loose.map { loose ->
        val at = device(loose.at)
        Offset(at.x.toFloat() - rect.left, at.y.toFloat() - rect.top)
    }
}

private fun cropRect(options: Options, bounds: IntSize, density: Float): Rect {
    val zoom = options.zoom
    return if (zoom == null) {
        Rect.makeXYWH(0f, 0f, bounds.width.toFloat(), bounds.height.toFloat())
    } else {
        Rect.makeXYWH(
            (zoom[0] + 24) * density,
            (zoom[1] + 24) * density,
            zoom[2] * density,
            zoom[3] * density,
        )
    }
}

/** The shot, cut to [rect], with a red ring round each of [rings], as the original's `--out`. */
private fun crop(image: Image, rect: Rect, rings: List<Offset>): Image {
    val surface = Surface.makeRasterN32Premul(rect.width.toInt(), rect.height.toInt())
    surface.canvas.drawImageRect(image, rect, Rect.makeWH(rect.width, rect.height))
    if (rings.isNotEmpty()) {
        // The original's rings: 5 CSS px with a 1.5 px stroke, on a 2× shot.
        val paint =
            Paint().apply {
                color = 0xFFFF0050.toInt()
                mode = PaintMode.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }
        for (ring in rings) surface.canvas.drawCircle(ring.x, ring.y, 10f, paint)
    }
    return surface.makeImageSnapshot()
}

private fun save(image: Image, file: File) {
    file.absoluteFile.parentFile.mkdirs()
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode ${file.path}")
    file.writeBytes(data.bytes)
}

// Synthetic key events are internal API: fine for a review tool, never for the figures.
@OptIn(InternalComposeUiApi::class)
private fun press(scene: ImageComposeScene, key: Key, codePoint: Int = 0) {
    scene.sendKeyEvent(KeyEvent(key = key, type = KeyEventType.KeyDown, codePoint = codePoint))
    scene.sendKeyEvent(KeyEvent(key = key, type = KeyEventType.KeyUp, codePoint = codePoint))
}

private fun sendKeys(scene: ImageComposeScene, names: List<String>) {
    if (names.isEmpty()) return
    press(scene, Key.Tab)
    for (name in names) {
        val char =
            when (name) {
                "Space" -> ' '
                "Comma" -> ','
                else -> name.singleOrNull()
            }
        press(scene, keyOf(name), char?.code ?: 0)
    }
}

/** The keys `--key` understands, by the names `drive.mjs` uses. */
private val KEYS: Map<String, Key> =
    mapOf(
        "Right" to Key.DirectionRight,
        "ArrowRight" to Key.DirectionRight,
        "Left" to Key.DirectionLeft,
        "ArrowLeft" to Key.DirectionLeft,
        "Up" to Key.DirectionUp,
        "ArrowUp" to Key.DirectionUp,
        "Down" to Key.DirectionDown,
        "ArrowDown" to Key.DirectionDown,
        "Home" to Key.MoveHome,
        "End" to Key.MoveEnd,
        "Space" to Key.Spacebar,
        "Enter" to Key.Enter,
        "Escape" to Key.Escape,
        "Tab" to Key.Tab,
        "Backspace" to Key.Backspace,
        "Shift" to Key.ShiftLeft,
        "ShiftRight" to Key.ShiftRight,
        "Comma" to Key.Comma,
        "c" to Key.C,
        "C" to Key.C,
        "5" to Key.Five,
        "i" to Key.I,
        "I" to Key.I,
    )

private fun keyOf(name: String): Key =
    KEYS[name] ?: if (name.length == 1) Key.Unknown else error("unknown key $name")

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
        click = numbers("--click")?.let { it[0] to it[1] },
        keys = value("--key")?.split(',')?.map { it.trim() }.orEmpty(),
        zoom = numbers("--zoom"),
        zoomScale = value("--zoom-scale")?.toFloat() ?: 4f,
        reduced = "--reduced" in flags,
        lines = "--lines" in flags,
        audit = "--audit" in flags,
        quick = "--quick" in flags,
        verbose = "--verbose" in flags,
        turn =
            value("--turn")?.split(',')?.associate { pair ->
                val (name, number) = pair.split('=')
                name.trim() to number.trim().toDouble()
            },
    )
}
