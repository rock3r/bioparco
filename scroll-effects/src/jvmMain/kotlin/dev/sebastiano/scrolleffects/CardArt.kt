package dev.sebastiano.scrolleffects

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

/**
 * The card as the design has it, before any effect. The art is drawn once and reused as a texture.
 */
internal val CardWidth = 272.dp
internal val CardHeight = 344.dp
private val CardRadius = 16.dp
private val Inset = 20.dp

/**
 * Transparent pixels around the card in its texture. Mesh edges then fall on transparent pixels,
 * and the card keeps the anti-aliased rounded edge it was painted with.
 */
internal const val TEXTURE_PADDING = 3

/** Paints [spec] into a bitmap `CardWidth × CardHeight` at [density], plus the padding. */
internal fun renderCardArt(
    spec: CardSpec,
    density: Density,
    measurer: TextMeasurer,
    fontFamily: FontFamily,
    grain: ImageBitmap,
): ImageBitmap {
    val cardWidth = with(density) { CardWidth.toPx() }
    val cardHeight = with(density) { CardHeight.toPx() }
    val bitmap =
        ImageBitmap(
            (cardWidth + TEXTURE_PADDING * 2).toInt(),
            (cardHeight + TEXTURE_PADDING * 2).toInt(),
        )
    val size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
        translate(TEXTURE_PADDING.toFloat(), TEXTURE_PADDING.toFloat()) {
            CardPainter(this, spec, Size(cardWidth, cardHeight), measurer, fontFamily).paint(grain)
        }
    }
    return bitmap
}

/** A small tile of monochrome noise, for the grainy gradient look. */
internal fun grainTile(size: Int = 128, seed: Int = 5): ImageBitmap {
    val random = Random(seed)
    val bytes = ByteArray(size * size * 4)
    for (i in 0 until size * size) {
        val value = random.nextInt(256).toByte()
        bytes[i * 4] = value
        bytes[i * 4 + 1] = value
        bytes[i * 4 + 2] = value
        bytes[i * 4 + 3] = 0xFF.toByte()
    }
    val bitmap = Bitmap()
    val info = ImageInfo.makeN32(size, size, ColorAlphaType.OPAQUE)
    bitmap.allocPixels(info)
    bitmap.installPixels(info, bytes, size * 4)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

private class CardPainter(
    private val scope: DrawScope,
    private val spec: CardSpec,
    private val card: Size,
    private val measurer: TextMeasurer,
    private val fontFamily: FontFamily,
) {
    private val inset = with(scope) { Inset.toPx() }
    private val white = Color.White

    fun paint(grain: ImageBitmap) =
        with(scope) {
            val radius = CardRadius.toPx()
            val shape =
                Path().apply {
                    addRoundRect(RoundRect(0f, 0f, card.width, card.height, CornerRadius(radius)))
                }
            clipPath(shape) {
                drawRect(spec.base, size = card)
                for (blob in spec.blobs) {
                    val r = blob.radius * card.width
                    drawCircle(
                        Brush.radialGradient(
                            0f to blob.color,
                            0.45f to blob.color.copy(alpha = 0.55f),
                            1f to blob.color.copy(alpha = 0f),
                            center = blob.center(card.width, card.height),
                            radius = r,
                        ),
                        radius = r,
                        center = blob.center(card.width, card.height),
                    )
                }
                drawRect(
                    ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)),
                    size = card,
                    alpha = 0.16f,
                    blendMode = BlendMode.Overlay,
                )
                drawRect(
                    Brush.verticalGradient(
                        0f to white.copy(alpha = 0.1f),
                        0.35f to Color.Transparent,
                        endY = card.height,
                    ),
                    size = card,
                )
                chart()
                number()
                labels()
            }
            drawRoundRect(
                white.copy(alpha = 0.14f),
                size = card,
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

    private fun text(
        value: String,
        sizeSp: Float,
        color: Color,
        topLeft: Offset,
        weight: FontWeight = FontWeight.Normal,
    ) =
        with(scope) {
            drawText(
                measurer,
                value,
                topLeft = topLeft,
                style =
                    TextStyle(
                        color = color,
                        fontSize = sizeSp.sp,
                        fontFamily = fontFamily,
                        fontWeight = weight,
                    ),
            )
        }

    private fun labels() =
        with(scope) {
            val titleStyle =
                TextStyle(
                    color = white.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                )
            drawText(
                measurer,
                "${spec.title}\n${spec.subtitle}",
                topLeft = Offset(inset, inset),
                style = titleStyle,
            )
            spec.chip?.let { chip(it) }
            if (spec.live) {
                val y = inset + 50.dp.toPx()
                drawCircle(white, radius = 3.dp.toPx(), center = Offset(inset + 3.dp.toPx(), y))
                text(
                    "Live",
                    10.5f,
                    white.copy(alpha = 0.8f),
                    Offset(inset + 11.dp.toPx(), y - 7.dp.toPx()),
                )
            }
            val footer =
                measurer.measure(
                    spec.footer,
                    TextStyle(
                        color = white.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            drawText(footer, topLeft = Offset(inset, card.height - inset - footer.size.height))
        }

    private fun chip(label: String) =
        with(scope) {
            val layout =
                measurer.measure(
                    label,
                    TextStyle(
                        color = white.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            val padX = 8.dp.toPx()
            val padY = 3.dp.toPx()
            val width = layout.size.width + padX * 2
            val height = layout.size.height + padY * 2
            val left = card.width - inset - width
            val top = inset
            drawRoundRect(
                white.copy(alpha = 0.16f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(height / 2f),
            )
            drawText(layout, topLeft = Offset(left + padX, top + padY))
        }

    /** The big value with its unit raised beside it, lit from above and glowing underneath. */
    private fun number() =
        with(scope) {
            val numberSize = if (spec.chart == CardChart.Moon) 58f else 78f
            val text = buildAnnotatedString {
                append(spec.value)
                withStyle(
                    SpanStyle(
                        fontSize = (numberSize * 0.3f).sp,
                        fontWeight = FontWeight.Normal,
                        baselineShift = BaselineShift(1.6f),
                    )
                ) {
                    append(" ${spec.unit}")
                }
            }
            val layout =
                measurer.measure(
                    text,
                    TextStyle(
                        fontSize = numberSize.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp,
                    ),
                )
            val centerY =
                if (spec.chart == CardChart.Moon) card.height * 0.7f else card.height * 0.47f
            val topLeft =
                Offset(
                    (card.width - layout.size.width) / 2f,
                    centerY - layout.size.height / 2f,
                )
            val brush =
                Brush.verticalGradient(
                    listOf(spec.numberTop, spec.numberBottom),
                    startY = topLeft.y + layout.size.height * 0.2f,
                    endY = topLeft.y + layout.size.height * 0.85f,
                )
            // A blurred copy underneath is the glow.
            drawText(
                layout,
                brush = SolidColor(Color.Transparent),
                topLeft = topLeft,
                shadow =
                    Shadow(spec.glow.copy(alpha = 0.8f), Offset(0f, 8.dp.toPx()), 28.dp.toPx()),
            )
            drawText(layout, brush = brush, topLeft = topLeft)
        }

    private fun chart() =
        when (spec.chart) {
            CardChart.Solar -> solar()
            CardChart.Surf -> surf()
            CardChart.Roast -> roast()
            CardChart.Heart -> heart()
            CardChart.Focus -> focus()
            CardChart.Moon -> moon()
        }

    /** A day of output as thin bars, brightest at the 13:10 peak. */
    private fun solar() =
        with(scope) {
            val bars = 29
            val left = inset
            val right = card.width - inset
            val base = card.height * 0.79f
            val step = (right - left) / (bars - 1)
            val barWidth = 2.dp.toPx()
            for (i in 0 until bars) {
                val t = i / (bars - 1f)
                val bell = exp(-((t - 0.52f) * (t - 0.52f)) / 0.035f)
                val height = 3.dp.toPx() + bell * 38.dp.toPx()
                val peak = i == 15
                drawRoundRect(
                    white.copy(alpha = if (peak) 1f else 0.3f + 0.35f * bell),
                    topLeft =
                        Offset(
                            left + i * step - barWidth / 2f,
                            base - height - (if (peak) 6.dp.toPx() else 0f),
                        ),
                    size = Size(barWidth, height + (if (peak) 6.dp.toPx() else 0f)),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
            axis(listOf("06", "12", "18"), base + 6.dp.toPx())
        }

    /** Three swell lines, the front one brightest. */
    private fun surf() =
        with(scope) {
            val alphas = floatArrayOf(0.85f, 0.45f, 0.25f)
            for (line in 0..2) {
                val path = Path()
                val baseY = card.height * (0.7f + line * 0.035f)
                val amplitude = (9f - line * 2f) * density
                var x = inset
                while (x <= card.width - inset) {
                    val t = (x - inset) / (card.width - inset * 2)
                    val y =
                        baseY +
                            amplitude * sin(t * 2f * PI.toFloat() * 2.2f + line * 0.9f) +
                            amplitude * 0.4f * sin(t * 2f * PI.toFloat() * 5.3f + line)
                    if (x == inset) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 2f
                }
                drawPath(
                    path,
                    white.copy(alpha = alphas[line]),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
            }
        }

    /** The roast's progress as a dashed track, lit up to the Maillard stage. */
    private fun roast() =
        with(scope) {
            val segments = 26
            val lit = 16
            val gap = 2.5.dp.toPx()
            val y = card.height * 0.66f
            val width = (card.width - inset * 2 - gap * (segments - 1)) / segments
            val height = 3.dp.toPx()
            for (i in 0 until segments) {
                val color =
                    if (i < lit) Color(0xFFFF9447).copy(alpha = 0.55f + 0.45f * i / lit)
                    else white.copy(alpha = 0.18f)
                drawRoundRect(
                    color,
                    topLeft = Offset(inset + i * (width + gap), y),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2f),
                )
            }
            val labelY = y + 10.dp.toPx()
            val muted = white.copy(alpha = 0.55f)
            text("Drying", 9.5f, muted, Offset(inset, labelY))
            val maillard =
                measurer.measure("Maillard", TextStyle(fontSize = 9.5.sp, fontFamily = fontFamily))
            text(
                "Maillard",
                9.5f,
                white.copy(alpha = 0.9f),
                Offset((card.width - maillard.size.width) / 2f, labelY),
            )
            val develop =
                measurer.measure("Develop", TextStyle(fontSize = 9.5.sp, fontFamily = fontFamily))
            text("Develop", 9.5f, muted, Offset(card.width - inset - develop.size.width, labelY))
        }

    /** An ECG trace: a flat line with a beat every so often. */
    private fun heart() =
        with(scope) {
            val path = Path()
            val baseY = card.height * 0.72f
            val left = inset
            val right = card.width - inset
            val beat = (right - left) / 5.2f
            path.moveTo(left, baseY)
            var x = left
            while (x <= right) {
                val phase = ((x - left) / beat) % 1f
                val y =
                    when {
                        phase in 0.30f..0.34f -> baseY - (phase - 0.30f) / 0.04f * 26.dp.toPx()
                        phase in 0.34f..0.38f ->
                            baseY - 26.dp.toPx() + (phase - 0.34f) / 0.04f * 34.dp.toPx()
                        phase in 0.38f..0.42f ->
                            baseY + 8.dp.toPx() - (phase - 0.38f) / 0.04f * 8.dp.toPx()
                        phase in 0.55f..0.7f ->
                            baseY - sin((phase - 0.55f) / 0.15f * PI.toFloat()) * 7.dp.toPx()
                        else -> baseY
                    }
                path.lineTo(x, y)
                x += 0.75f
            }
            val fade =
                Brush.horizontalGradient(
                    0f to white.copy(alpha = 0.25f),
                    0.5f to white.copy(alpha = 0.95f),
                    1f to white.copy(alpha = 0.6f),
                    startX = left,
                    endX = right,
                )
            drawPath(path, fade, style = Stroke(width = 1.3.dp.toPx()))
        }

    /** Hours per day, Thursday lit. */
    private fun focus() =
        with(scope) {
            val days = listOf("M", "T", "W", "T", "F", "S")
            val heights = floatArrayOf(0.35f, 0.62f, 0.4f, 1f, 0.7f, 0.28f)
            val base = card.height * 0.79f
            val slot = (card.width - inset * 2) / days.size
            val barWidth = slot * 0.46f
            val tallest = 52.dp.toPx()
            days.forEachIndexed { i, day ->
                val best = i == 3
                val height = heights[i] * tallest
                val left = inset + slot * i + (slot - barWidth) / 2f
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFBFE4FF).copy(alpha = if (best) 0.95f else 0.45f),
                            Color(0xFF5FA8FF).copy(alpha = if (best) 0.6f else 0.2f),
                        ),
                        startY = base - height,
                        endY = base,
                    ),
                    topLeft = Offset(left, base - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                val label =
                    measurer.measure(day, TextStyle(fontSize = 9.sp, fontFamily = fontFamily))
                text(
                    day,
                    9f,
                    white.copy(alpha = if (best) 0.9f else 0.5f),
                    Offset(left + (barWidth - label.size.width) / 2f, base + 6.dp.toPx()),
                )
            }
        }

    /** A glowing moon, 84 % lit, among a few stars. */
    private fun moon() =
        with(scope) {
            val random = Random(84)
            repeat(26) {
                drawCircle(
                    white.copy(alpha = 0.2f + random.nextFloat() * 0.4f),
                    radius = (0.5f + random.nextFloat()) * density,
                    center =
                        Offset(
                            random.nextFloat() * card.width,
                            random.nextFloat() * card.height * 0.9f,
                        ),
                )
            }
            val center = Offset(card.width / 2f, card.height * 0.42f)
            val radius = 44.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x99E7C9FF), Color(0x33B57BFF), Color.Transparent),
                    center = center,
                    radius = radius * 2.2f,
                ),
                radius = radius * 2.2f,
                center = center,
            )
            drawCircle(Color(0xFF3A1686), radius = radius, center = center)
            val lit =
                Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center.x - radius * 0.72f,
                            center.y - radius,
                            center.x + radius,
                            center.y + radius,
                        )
                    )
                }
            clipPath(lit) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFBF3FF), Color(0xFFE2C8FF), Color(0xFFB88AF5)),
                        center = center + Offset(radius * 0.25f, -radius * 0.3f),
                        radius = radius * 1.3f,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }

    private fun axis(labels: List<String>, y: Float) =
        with(scope) {
            val style = TextStyle(fontSize = 9.sp, fontFamily = fontFamily)
            labels.forEachIndexed { i, label ->
                val width = measurer.measure(label, style).size.width
                val x =
                    inset + (card.width - inset * 2) * i / (labels.size - 1) -
                        width * i / (labels.size - 1)
                text(label, 9f, white.copy(alpha = 0.6f), Offset(x, y))
            }
        }
}
