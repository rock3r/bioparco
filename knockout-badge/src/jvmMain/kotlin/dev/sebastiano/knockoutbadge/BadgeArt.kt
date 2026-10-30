package dev.sebastiano.knockoutbadge

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.jetbrains.skia.Path as SkiaPath

/**
 * The badge, drawn in its own frame: [width] × [height] px with the origin at its top left. It
 * holds the die-cut shape and the print, built once for a size and then drawn every frame, so a
 * frame only replays them. The word is laid out on its own, by [ink], so typing does not rebuild
 * the rest.
 */
internal class BadgeArt(
    density: Density,
    private val measurer: TextMeasurer,
    private val fontFamily: FontFamily,
) : Density by density {
    val width = BadgeSpec.WIDTH.toPx()
    val height = BadgeSpec.HEIGHT.toPx()
    private val margin = BadgeSpec.MARGIN.toPx()
    private val corner = BadgeSpec.CORNER.toPx()
    private val notchY = height * BadgeSpec.STUB_AT
    private val notchRadius = BadgeSpec.NOTCH.toPx()
    private val slot =
        Rect(
            offset = Offset((width - BadgeSpec.SLOT_WIDTH.toPx()) / 2f, BadgeSpec.SLOT_TOP.toPx()),
            size = Size(BadgeSpec.SLOT_WIDTH.toPx(), BadgeSpec.SLOT_HEIGHT.toPx()),
        )

    /** The die-cut card: rounded, with a lanyard slot and the two notches of a tear-off stub. */
    val shape: Path =
        Path.combine(
            PathOperation.Difference,
            Path().apply { addRoundRect(RoundRect(0f, 0f, width, height, CornerRadius(corner))) },
            Path().apply {
                addRoundRect(RoundRect(slot, CornerRadius(slot.height / 2f)))
                addOval(Rect(Offset(0f, notchY), notchRadius))
                addOval(Rect(Offset(width, notchY), notchRadius))
            },
        )

    val skiaShape: SkiaPath = shape.asSkiaPath()

    private val grid = globeGrid()
    private val bars = barcodePath()
    private val perforation = perforationPath()

    private fun style(
        size: TextUnit,
        weight: FontWeight,
        color: Color = Color.White,
        spacing: TextUnit = 0.sp,
    ) =
        TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = fontFamily,
            letterSpacing = spacing,
        )

    private val kicker = style(8.sp, FontWeight.Bold, spacing = 1.2.sp)
    private val conference = measurer.measure("CONFERENCE PASS", kicker)
    private val dates = measurer.measure("14–15 MAY 2027", kicker)
    private val name = measurer.measure(BadgeSpec.HOLDER, style(20.sp, FontWeight.ExtraBold))
    private val chip = measurer.measure("SPEAKER", style(6.5.sp, FontWeight.Black, Black, 0.8.sp))
    private val role =
        measurer.measure("Motion & Interaction", style(9.sp, FontWeight.Normal, Color(0xCCFFFFFF)))
    private val facts =
        listOf("ACCESS" to "All areas", "DAYS" to "Both", "TRACK" to "Main stage").map { (k, v) ->
            measurer.measure(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontSize = 6.5.sp,
                            color = Color(0x99FFFFFF),
                            letterSpacing = 1.sp,
                        )
                    ) {
                        append(k)
                    }
                    append('\n')
                    withStyle(SpanStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
                        append(v)
                    }
                },
                style(10.sp, FontWeight.Bold),
            )
        }
    private val number = measurer.measure("№ 0427", kicker)
    private val admit = measurer.measure("ADMIT ONE", kicker)

    private val globeCentre =
        Offset(width - margin - BadgeSpec.GLOBE.toPx(), BadgeSpec.NAME_TOP.toPx() + 16.dp())
    private val globeRadius = BadgeSpec.GLOBE.toPx()
    private val globeRing =
        Brush.sweepGradient(
            0f to Color(0xFF8B5CF6),
            0.25f to Color(0xFF22D3EE),
            0.5f to Color(0xFFA3E635),
            0.75f to Color(0xFFF59E0B),
            1f to Color(0xFF8B5CF6),
            center = globeCentre,
        )

    /** The sticker's black: the whole card, all the way to black. */
    fun drawBlack(scope: DrawScope) = scope.drawPath(shape, Black)

    /**
     * The white [ink]. [sheet] shows the ink's layer as a faint pane when the badge comes apart.
     */
    fun drawInk(scope: DrawScope, ink: WordInk, sheet: Float) =
        with(scope) {
            if (sheet > 0f) {
                drawPath(shape, Color.White.copy(alpha = 0.07f * sheet))
                drawPath(shape, Color.White.copy(alpha = 0.2f * sheet), style = Stroke(1.dp()))
            }
            drawText(ink.layout, topLeft = ink.at)
        }

    /** Everything else printed on the badge, over the sticker and not blended. */
    fun drawPrint(scope: DrawScope, seconds: Float) =
        with(scope) {
            drawStill()
            drawGlobe(seconds)
        }

    /** The print that never moves. */
    private fun DrawScope.drawStill() {
        drawPath(grid, Color.White.copy(alpha = 0.13f), style = Stroke(0.7.dp()))
        val top = BadgeSpec.KICKER_TOP.toPx()
        drawText(conference, topLeft = Offset(margin, top))
        drawText(dates, topLeft = Offset(width * 0.5f, top))

        val nameTop = BadgeSpec.NAME_TOP.toPx()
        drawText(name, topLeft = Offset(margin, nameTop))
        val chipTop = nameTop + name.size.height + 3.dp()
        val pad = 4.dp()
        drawRoundRect(
            Color.White,
            topLeft = Offset(margin, chipTop),
            size = Size(chip.size.width + pad * 2f, chip.size.height + 2.dp()),
            cornerRadius = CornerRadius(3.dp()),
        )
        drawText(chip, topLeft = Offset(margin + pad, chipTop + 1.dp()))
        drawText(role, topLeft = Offset(margin + chip.size.width + pad * 3f, chipTop - 1.dp()))

        val factsTop = height * BadgeSpec.FACTS_AT
        val columns = floatArrayOf(0f, 0.3f, 0.52f)
        facts.forEachIndexed { i, fact ->
            drawText(fact, topLeft = Offset(margin + (width - margin * 2) * columns[i], factsTop))
        }
        drawPath(perforation, Color.White.copy(alpha = 0.3f))
        drawPath(bars, Color.White)
        val bottom = height - margin - number.size.height + 2.dp()
        drawText(number, topLeft = Offset(margin, bottom))
        drawText(admit, topLeft = Offset(width - margin - admit.size.width, bottom))
    }

    /** A wireframe globe in a rainbow ring, its meridians turning slowly. */
    private fun DrawScope.drawGlobe(seconds: Float) {
        val r = globeRadius
        drawCircle(Black, r, globeCentre)
        drawCircle(globeRing, r - 1.5.dp(), globeCentre, style = Stroke(3.dp()))
        val inner = r - 5.dp()
        val line = Stroke(0.8.dp())
        val ink = Color(0xCCD9D2FF)
        drawCircle(ink, inner, globeCentre, style = line)
        for (k in -2..2) {
            val y = globeCentre.y + inner * k / 3f
            val half = inner * kotlin.math.sqrt(1f - (k / 3f) * (k / 3f))
            drawLine(
                ink,
                Offset(globeCentre.x - half, y),
                Offset(globeCentre.x + half, y),
                0.8.dp(),
            )
        }
        for (j in 0 until MERIDIANS) {
            val angle = seconds * GLOBE_SPIN + j * PI.toFloat() / MERIDIANS
            val rx = inner * abs(cos(angle))
            drawOval(
                ink,
                topLeft = Offset(globeCentre.x - rx, globeCentre.y - inner),
                size = Size(rx * 2f, inner * 2f),
                style = line,
            )
        }
    }

    /**
     * [word], cut out of the sticker as big as fits the badge's width. Its baseline is fixed, so
     * the badge keeps its layout as the word changes.
     */
    fun ink(word: String): WordInk {
        val layout = fitWord(word)
        val at = Offset(margin - 2.dp(), BadgeSpec.WORD_BASELINE.toPx() - layout.firstBaseline)
        val box = Rect(at, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
        return WordInk(word, layout, at, box.inflate(2f))
    }

    private fun fitWord(word: String): TextLayoutResult {
        val ink =
            TextStyle(
                color = Color.White,
                fontSize = WORD_SIZE,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily,
                letterSpacing = (-3).sp,
            )
        val room = width - margin * 2f + 4.dp()
        val first = measurer.measure(word, ink, softWrap = false)
        if (first.size.width <= room) return first
        val scale = floor(WORD_SIZE.value * room / first.size.width)
        return measurer.measure(
            word,
            ink.copy(fontSize = scale.sp, letterSpacing = (-3 * scale / WORD_SIZE.value).sp),
            softWrap = false,
        )
    }

    /**
     * The lines of a big wireframe globe printed faintly behind the type, as polylines sampled from
     * its parallels and meridians, broken wherever they leave the card.
     */
    private fun globeGrid(): Path {
        val path = Path()
        val cx = width * 0.82f
        val cy = height * 0.36f
        val radius = width * 1.05f
        val step = 2.5.dp()
        fun trace(points: Int, at: (Float) -> Offset) {
            var drawing = false
            for (i in 0..points) {
                val p = at(i.toFloat() / points)
                if (isOnCard(p, 1.5.dp())) {
                    if (drawing) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                    drawing = true
                } else {
                    drawing = false
                }
            }
        }
        val samples = (2f * PI.toFloat() * radius / step).toInt()
        // Parallels, seen from a little above, so they are flat ellipses.
        for (k in -5..5) {
            val latitude = k * PI.toFloat() / 12f
            val rx = radius * cos(latitude)
            val y = cy + radius * sin(latitude) * 0.95f
            trace(samples) { t ->
                val a = t * 2f * PI.toFloat()
                Offset(cx + rx * cos(a), y + rx * 0.3f * sin(a))
            }
        }
        // Meridians: ellipses as tall as the globe, narrowing towards its edge.
        for (m in 0 until 8) {
            val longitude = m * PI.toFloat() / 8f
            val rx = radius * sin(longitude)
            trace(samples) { t ->
                val a = t * 2f * PI.toFloat()
                Offset(cx + rx * cos(a), cy + radius * sin(a))
            }
        }
        return path
    }

    /** Whether [p] is on the card, at least [inset] inside its edges, slot and notches. */
    private fun isOnCard(p: Offset, inset: Float): Boolean {
        val r = corner
        val x = p.x.coerceIn(r, width - r)
        val y = p.y.coerceIn(r, height - r)
        val inCorner = (p - Offset(x, y)).getDistance() <= r - inset
        if (!inCorner) return false
        if (p.x < inset || p.x > width - inset || p.y < inset || p.y > height - inset) return false
        if (slot.inflate(inset).contains(p)) return false
        val notch = notchRadius + inset
        if ((p - Offset(0f, notchY)).getDistanceSquared() < notch * notch) return false
        if ((p - Offset(width, notchY)).getDistanceSquared() < notch * notch) return false
        return true
    }

    private fun barcodePath(): Path {
        val left = margin
        val top = notchY + BadgeSpec.BARS_GAP.toPx()
        val span = width - margin * 2f
        val tall = BadgeSpec.BARS_HEIGHT.toPx()
        val code = barcode(seed = 427, count = 52)
        return Path().apply {
            for (i in 0 until code.size / 2) {
                addRect(
                    Rect(
                        left + code[i * 2] * span,
                        top,
                        left + (code[i * 2] + code[i * 2 + 1]) * span,
                        top + tall,
                    )
                )
            }
        }
    }

    private fun perforationPath(): Path {
        val dash = 4.dp()
        val gap = 3.dp()
        val from = notchRadius + 5.dp()
        val to = width - notchRadius - 5.dp()
        val thick = 1.dp()
        return Path().apply {
            var x = from
            while (x < to) {
                addRect(Rect(x, notchY - thick / 2f, minOf(x + dash, to), notchY + thick / 2f))
                x += dash + gap
            }
        }
    }

    private fun Double.dp(): Float = (this * density).toFloat()

    private fun Int.dp(): Float = this * density

    private companion object {
        val WORD_SIZE = 136.sp
        const val MERIDIANS = 4
        const val GLOBE_SPIN = 0.6f
    }
}

/**
 * The word cut out of the sticker: its [layout], drawn at [at]. [box] is all of the footage that
 * shows while the badge is whole.
 */
internal class WordInk(
    val text: String,
    val layout: TextLayoutResult,
    val at: Offset,
    val box: Rect,
)

internal val Black = Color(0xFF000000)
