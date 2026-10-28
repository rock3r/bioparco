package dev.sebastiano.stampbook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/*
 * The two stamps, drawn in dp units onto a baked bitmap (see BookArt). Both are after the video's
 * pair: a pale line-drawn sparrow, and a loud teal one with a chubby bird over waves. The video's
 * Japanese lettering becomes Italian here, for a bioparco.
 */

/** The pale stamp: a sparrow on a twig, the word running down its right-hand side. */
internal fun DrawScope.drawQuietStamp(text: TextMeasurer) {
    val w = Book.StampA.width.value
    val h = Book.StampA.height.value
    drawPerforatedPaper(w, h, Quiet.Paper)
    val field = Rect(10f, 10f, w - 10f, h - 10f)
    drawRect(Quiet.Field, field.topLeft, field.size)
    clipRect(field.left, field.top, field.right, field.bottom) { drawFibres(field) }

    val title = TextStyle(color = Quiet.Ink, fontSize = 16.sp, fontFamily = FontFamily.Serif)
    "QUIETE"
        .forEachIndexed { index, letter ->
            val layout = text.measure(letter.toString(), title)
            drawText(layout, topLeft = Offset(122f - layout.size.width / 2f, 20f + index * 19f))
        }
    val value = text.measure("63", title.copy(fontSize = 24.sp))
    drawText(value, topLeft = Offset(126f - value.size.width, 178f))
    drawText(text.measure("c", title.copy(fontSize = 11.sp)), topLeft = Offset(127f, 190f))
    val country = text.measure("ITALIA", title.copy(fontSize = 8.sp, letterSpacing = 1.2.sp))
    drawText(country, topLeft = Offset(136f - country.size.width, 207f))

    // The twig and sparrow sit low on the left, clear of the value.
    translate(-6f, -8f) {
        drawTwig()
        drawSparrow()
    }
}

private fun DrawScope.drawFibres(field: Rect) {
    val random = Random(7)
    repeat(70) {
        val start =
            Offset(
                field.left + random.nextFloat() * field.width,
                field.top + random.nextFloat() * field.height,
            )
        val end = start + Offset(random.nextFloat() * 8f - 4f, random.nextFloat() * 3f - 1.5f)
        drawLine(Quiet.Fibre, start, end, strokeWidth = 0.6f)
    }
}

private fun DrawScope.drawTwig() {
    val line = Stroke(width = 1f, cap = StrokeCap.Round)
    val branch =
        Path().apply {
            moveTo(18f, 204f)
            cubicTo(52f, 196f, 84f, 174f, 108f, 158f)
        }
    drawPath(branch, Quiet.Line, style = line)
    leaf(Offset(30f, 200f), angle = 200f, length = 15f, fill = null)
    leaf(Offset(44f, 194f), angle = 115f, length = 16f, fill = Quiet.Leaf)
    leaf(Offset(86f, 176f), angle = 65f, length = 17f, fill = null)
    leaf(Offset(98f, 166f), angle = 210f, length = 14f, fill = null)
    drawLine(Quiet.Line, Offset(94f, 168f), Offset(93f, 176f), strokeWidth = 0.8f)
    drawCircle(Quiet.Berry, radius = 2.6f, center = Offset(93f, 178.5f))
}

/** An almond leaf on a twig at [base], pointing [angle] degrees clockwise from the right. */
private fun DrawScope.leaf(base: Offset, angle: Float, length: Float, fill: Color?) {
    val shape =
        Path().apply {
            moveTo(0f, 0f)
            quadraticTo(length * 0.5f, -length * 0.32f, length, 0f)
            quadraticTo(length * 0.5f, length * 0.32f, 0f, 0f)
            close()
        }
    shape.translate(base)
    rotate(angle, pivot = base) {
        if (fill != null) drawPath(shape, fill)
        drawPath(shape, Quiet.Line, style = Stroke(width = 0.8f))
        drawLine(Quiet.Line, base, base + Offset(length * 0.8f, 0f), strokeWidth = 0.5f)
    }
}

private fun DrawScope.drawSparrow() {
    val outline = Stroke(width = 0.9f, cap = StrokeCap.Round)
    val tail =
        Path().apply {
            moveTo(50f, 166f)
            lineTo(30f, 180f)
            lineTo(36f, 183f)
            lineTo(55f, 170f)
            close()
        }
    drawPath(tail, Quiet.Cap)
    drawPath(tail, Quiet.Line, style = outline)
    drawLine(Quiet.Line, Offset(61f, 174f), Offset(62f, 183f), strokeWidth = 0.9f)
    drawLine(Quiet.Line, Offset(67f, 174f), Offset(69f, 182f), strokeWidth = 0.9f)

    rotate(-14f, pivot = Offset(64f, 164f)) {
        drawOval(Quiet.Body, Offset(46f, 153f), Size(38f, 22f))
        drawOval(Quiet.Line, Offset(46f, 153f), Size(38f, 22f), style = outline)
        val wing =
            Path().apply {
                moveTo(52f, 160f)
                quadraticTo(64f, 152f, 76f, 160f)
                quadraticTo(64f, 170f, 50f, 168f)
                close()
            }
        drawPath(wing, Quiet.Wing)
        drawPath(wing, Quiet.Line, style = outline)
        drawLine(Quiet.Line, Offset(57f, 163f), Offset(70f, 162f), strokeWidth = 0.5f)
    }

    val head = Offset(82f, 150f)
    drawCircle(Quiet.Body, radius = 9.5f, center = head)
    val skull = Path().apply { addOval(Rect(head, 9.5f)) }
    clipPath(skull) { drawRect(Quiet.Cap, Offset(70f, 136f), Size(26f, 11f)) }
    drawCircle(Quiet.Line, radius = 9.5f, center = head, style = outline)
    drawCircle(Color.Black, radius = 1.4f, center = Offset(85f, 149f))
    val beak =
        Path().apply {
            moveTo(90.5f, 148.5f)
            lineTo(96f, 151f)
            lineTo(90.5f, 153f)
            close()
        }
    drawPath(beak, Quiet.Beak)
}

/** The loud stamp: a chubby bird on a branch over waves, under a sun and a cloud. */
internal fun DrawScope.drawBirdStamp(text: TextMeasurer) {
    val w = Book.StampB.width.value
    val h = Book.StampB.height.value
    drawPerforatedPaper(w, h, Loud.Paper)
    val field = Rect(9f, 9f, w - 9f, h - 9f)
    clipRect(field.left, field.top, field.right, field.bottom) {
        drawRect(Loud.Sky, field.topLeft, field.size)
        drawWaves(field)
        drawCircle(Loud.Sun, radius = 9f, center = Offset(130f, 58f))
        cloud(Offset(118f, 66f), 1f)
        cloud(Offset(30f, 78f), 0.75f)
        drawBranch()
        drawChubbyBird()
        drawRect(Loud.Paper, Offset(field.left, 188f), Size(field.width, field.bottom - 188f))
    }

    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = Loud.Navy)
    drawText(
        text.measure("Uccellino", title.copy(color = Loud.Sun)),
        topLeft = Offset(16.2f, 15.2f),
    )
    drawText(text.measure("Uccellino", title), topLeft = Offset(15f, 14f))
    val subtitle = TextStyle(fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Loud.Navy)
    drawText(text.measure("le quattro stagioni", subtitle), topLeft = Offset(17f, 42f))

    val band = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = Loud.Navy)
    drawText(text.measure("ITALIA", band), topLeft = Offset(15f, 193f))
    val value = text.measure("84", band.copy(fontSize = 22.sp, color = Loud.Sun))
    drawText(value, topLeft = Offset(136f - value.size.width, 184f))
    drawText(text.measure("c", band.copy(color = Loud.Sun)), topLeft = Offset(137f, 196f))
}

/** Seigaiha: rows of overlapping fans of rings, each row tucked under the one below it. */
private fun DrawScope.drawWaves(field: Rect) {
    val radius = 13f
    val ring = Stroke(width = 1.1f)
    var row = 0
    var y = 92f
    while (y < field.bottom + radius) {
        var x = field.left + if (row % 2 == 0) 0f else radius
        while (x < field.right + radius) {
            val centre = Offset(x, y)
            drawArc(
                Loud.Sea,
                180f,
                180f,
                true,
                centre - Offset(radius, radius),
                Size(radius * 2f, radius * 2f),
            )
            for (r in listOf(radius - 1f, radius * 0.66f, radius * 0.33f)) {
                drawArc(
                    Loud.Foam,
                    180f,
                    180f,
                    false,
                    centre - Offset(r, r),
                    Size(r * 2f, r * 2f),
                    style = ring,
                )
            }
            x += radius * 2f
        }
        y += radius * 0.62f
        row++
    }
}

private fun DrawScope.cloud(at: Offset, scale: Float) {
    for ((dx, dy, r) in listOf(Triple(-10f, 2f, 6f), Triple(0f, -2f, 8f), Triple(10f, 2f, 6f))) {
        drawCircle(Loud.Cloud, radius = r * scale, center = at + Offset(dx * scale, dy * scale))
    }
    drawRect(Loud.Cloud, at + Offset(-10f * scale, 2f * scale), Size(20f * scale, 6f * scale))
}

private fun DrawScope.drawBranch() {
    val branch =
        Path().apply {
            moveTo(0f, 178f)
            cubicTo(40f, 170f, 100f, 168f, 160f, 158f)
        }
    drawPath(branch, Loud.Bark, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
    for ((x, y, angle) in
        listOf(
            Triple(22f, 174f, 120f),
            Triple(38f, 171f, 220f),
            Triple(112f, 166f, 300f),
            Triple(128f, 163f, 40f),
            Triple(140f, 160f, 290f),
        )) {
        val leaf =
            Path().apply {
                moveTo(0f, 0f)
                quadraticTo(8f, -6f, 17f, 0f)
                quadraticTo(8f, 6f, 0f, 0f)
                close()
            }
        leaf.translate(Offset(x, y))
        rotate(angle, pivot = Offset(x, y)) {
            drawPath(leaf, Loud.Leaf)
            drawPath(leaf, Loud.Navy, style = Stroke(width = 1f))
        }
    }
}

private fun DrawScope.drawChubbyBird() {
    val navy = Stroke(width = 2f, cap = StrokeCap.Round)
    val centre = Offset(82f, 128f)
    val radius = 31f
    drawLine(
        Loud.Beak,
        Offset(76f, 155f),
        Offset(74f, 166f),
        strokeWidth = 2.4f,
        cap = StrokeCap.Round,
    )
    drawLine(
        Loud.Beak,
        Offset(90f, 155f),
        Offset(91f, 165f),
        strokeWidth = 2.4f,
        cap = StrokeCap.Round,
    )

    drawCircle(Loud.Belly, radius, centre)
    val body = Path().apply { addOval(Rect(centre, radius)) }
    clipPath(body) {
        val cap =
            Path().apply {
                moveTo(40f, 92f)
                lineTo(124f, 92f)
                lineTo(124f, 116f)
                quadraticTo(104f, 108f, 90f, 114f)
                quadraticTo(74f, 120f, 40f, 110f)
                close()
            }
        drawPath(cap, Loud.Feather)
    }
    drawCircle(Loud.Navy, radius, centre, style = navy)

    val wing =
        Path().apply {
            moveTo(92f, 116f)
            cubicTo(112f, 116f, 124f, 140f, 122f, 156f)
            cubicTo(110f, 152f, 94f, 146f, 90f, 132f)
            close()
        }
    drawPath(wing, Loud.Feather)
    drawPath(wing, Loud.Navy, style = Stroke(width = 1.8f))
    drawLine(Loud.Foam, Offset(97f, 128f), Offset(114f, 146f), strokeWidth = 1.2f)
    drawLine(Loud.Foam, Offset(95f, 136f), Offset(108f, 148f), strokeWidth = 1.2f)

    drawOval(Loud.Blush, Offset(66f, 130f), Size(12f, 7f))
    drawCircle(Loud.Navy, radius = 3.6f, center = Offset(68f, 121f))
    drawCircle(Color.White, radius = 1.1f, center = Offset(69.2f, 119.8f))

    val beak =
        Path().apply {
            moveTo(53f, 128f)
            lineTo(61f, 123f)
            lineTo(61f, 132f)
            close()
        }
    drawPath(beak, Loud.Beak)
    drawPath(beak, Loud.Navy, style = Stroke(width = 1.2f))
    drawLine(Loud.Navy, Offset(56f, 131f), Offset(55f, 137f), strokeWidth = 1f)
    drawCircle(Loud.Sun, radius = 3.6f, center = Offset(55f, 140f))
    drawCircle(Loud.Navy, radius = 3.6f, center = Offset(55f, 140f), style = Stroke(width = 1f))

    for (x in listOf(78f, 86f, 94f)) {
        drawLine(
            Loud.Navy.copy(alpha = 0.5f),
            Offset(x - 2f, 146f),
            Offset(x, 149f),
            strokeWidth = 1f,
        )
        drawLine(
            Loud.Navy.copy(alpha = 0.5f),
            Offset(x, 149f),
            Offset(x + 2f, 146f),
            strokeWidth = 1f,
        )
    }
}

private object Quiet {
    val Paper = Color(0xFFF8F5EC)
    val Field = Color(0xFFEEE8D9)
    val Fibre = Color(0x0A000000)
    val Ink = Color(0xFF3B4152)
    val Line = Color(0xFF5E5852)
    val Leaf = Color(0xFFD8744A)
    val Berry = Color(0xFFC8473B)
    val Body = Color(0xFFECE6D8)
    val Wing = Color(0xFFA39C92)
    val Cap = Color(0xFF8C8794)
    val Beak = Color(0xFFE07A45)
}

private object Loud {
    val Paper = Color(0xFFF3E6C8)
    val Sky = Color(0xFF3AA7A2)
    val Sea = Color(0xFF2E9993)
    val Foam = Color(0xFF9AD8CF)
    val Sun = Color(0xFFE4563B)
    val Cloud = Color(0xFFF6EBD3)
    val Navy = Color(0xFF1E2448)
    val Bark = Color(0xFF6B4A35)
    val Leaf = Color(0xFF2F7A55)
    val Belly = Color(0xFFF7EFDD)
    val Feather = Color(0xFF3F8DB3)
    val Beak = Color(0xFFEE8B3A)
    val Blush = Color(0xCCF2A3A0)
}
