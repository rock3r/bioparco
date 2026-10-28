package dev.sebastiano.routinetimeline

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the card, the dial and the clock. The dial is one day round, so everything on it is fixed
 * to a time of day and the whole face turns by a quarter of a degree a minute under the now marker
 * at three o'clock. Text is drawn from images made once, and the outer arc's colours are one sweep
 * gradient over the day, turned with the face.
 */
internal class DialPainter(
    private val density: Density,
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    private val routine: Routine,
) {
    private fun px(dp: Dp): Float = with(density) { dp.toPx() }

    private val centre = Offset(px(TimelineMetrics.dialX), px(TimelineMetrics.dialY))
    private val arcRadius = px(TimelineMetrics.arcRadius)
    private val arcStroke = Stroke(px(TimelineMetrics.arcWidth), cap = StrokeCap.Round)
    private val markRadius = px(TimelineMetrics.markRadius)
    private val veil = px(TimelineMetrics.veil)
    private val skyCentre = centre + Offset(0f, px(TimelineMetrics.skyIconY))

    private val lanes =
        Lane.entries.associateWith { lane ->
            when (lane) {
                Lane.Outer ->
                    LaneShape(
                        px(TimelineMetrics.outerLaneRadius),
                        px(TimelineMetrics.outerLaneWidth),
                    )
                Lane.Inner ->
                    LaneShape(
                        px(TimelineMetrics.innerLaneRadius),
                        px(TimelineMetrics.innerLaneWidth),
                    )
            }
        }

    /** The day's colours round the dial, 0° being midnight. */
    private val skyBrush: Brush =
        Brush.sweepGradient(
            *Array(SKY_STOPS + 1) { i ->
                val f = i.toFloat() / SKY_STOPS
                f to RoutinePalette.sky(f * MINUTES_PER_DAY.toDouble())
            },
            center = centre,
        )

    private val veilBrush =
        Brush.horizontalGradient(
            0f to RoutinePalette.card,
            1f to RoutinePalette.card.copy(alpha = 0f),
            startX = centre.x,
            endX = centre.x + veil,
        )

    private val sunGlow = glow(RoutinePalette.sun)
    private val moonGlow = glow(RoutinePalette.moon)

    private val dash =
        PathEffect.dashPathEffect(
            floatArrayOf(px(TimelineMetrics.dashGap), px(TimelineMetrics.dashGap))
        )

    private val stars: List<Star> =
        Random(STAR_SEED).let { random ->
            List(STAR_COUNT) {
                Star(
                    minute = random.nextDouble() * MINUTES_PER_DAY,
                    radius =
                        px(TimelineMetrics.starInner) +
                            random.nextFloat() *
                                (px(TimelineMetrics.starOuter) - px(TimelineMetrics.starInner)),
                    size = px(TimelineMetrics.starSize) * (0.6f + random.nextFloat() * 0.6f),
                    alpha = 0.25f + random.nextFloat() * 0.45f,
                )
            }
        }

    private val hourMarks: List<ImageBitmap> =
        (0 until 24 step 3).map { hour ->
            val style =
                baseStyle.copy(
                    color = RoutinePalette.hourMark,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            textImage(measurer.measure(hourMark(hour), style), density)
        }

    private val digitStyle =
        baseStyle.copy(
            color = RoutinePalette.clock,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum",
        )
    private val digits: Map<Char, ImageBitmap> =
        "0123456789:"
            .associateWith { textImage(measurer.measure(it.toString(), digitStyle), density) }

    private val meridiems: Map<String, ImageBitmap> =
        listOf("AM", "PM").associateWith {
            val style =
                baseStyle.copy(
                    color = RoutinePalette.meridiem,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                )
            textImage(measurer.measure(it, style), density)
        }

    fun draw(scope: DrawScope, time: Double) =
        with(scope) {
            val day = minuteOfDay(time)
            drawCard()
            Tracing.section("lanes") {
                clipRect(left = centre.x) {
                    drawLanes(time)
                    drawStars(day)
                    // Only as tall as the lanes, so it never touches the card's edge.
                    val reach = lanes.getValue(Lane.Outer).let { it.radius + it.width }
                    drawRect(
                        veilBrush,
                        topLeft = Offset(centre.x, centre.y - reach),
                        size = Size(veil, reach * 2),
                    )
                }
            }
            drawLine(
                RoutinePalette.clock.copy(alpha = DASH_ALPHA),
                centre + Offset(px(TimelineMetrics.dashFrom), 0f),
                centre + Offset(arcRadius - px(TimelineMetrics.markerRadius), 0f),
                strokeWidth = px(TimelineMetrics.dashWidth),
                pathEffect = dash,
            )
            drawSkyArc(day)
            Tracing.section("marks") {
                drawHourMarks(time)
                drawLaneIcons(time)
                drawBadges(time)
            }
            drawNowMarker(day)
            Tracing.section("clock") {
                drawSky(day)
                drawClock(day)
            }
        }

    private fun DrawScope.drawCard() {
        val corner = CornerRadius(px(TimelineMetrics.cardCorner))
        drawRoundRect(RoutinePalette.card, cornerRadius = corner)
        drawRoundRect(
            RoutinePalette.cardEdge,
            cornerRadius = corner,
            style = Stroke(px(TimelineMetrics.hairline)),
        )
    }

    private fun DrawScope.drawLanes(time: Double) {
        for (shape in lanes.values) {
            ring(shape.radius, RoutinePalette.track, -90f, 180f, Stroke(shape.width))
        }
        for (window in routine.windows) {
            val shape = lanes.getValue(window.lane)
            val presence = routine.presenceOf(window, time)
            val start = screenAngle(window.startMinute.toDouble(), time).toFloat()
            val sweep = (window.durationMinutes * DEGREES_PER_MINUTE).toFloat()
            // Round caps reach half the width past each end: pull the ends in by that much.
            val cap = (shape.width / 2 / shape.radius * DEGREES_PER_RADIAN).toFloat()
            val bright = RoutinePalette.kind(window.kind)
            if (presence > 0f) {
                // A soft glow: two wider, fainter strokes rather than a blur, which software Skia
                // would pay for on every pixel.
                for ((spread, strength) in GLOW_LAYERS) {
                    val glow = Stroke(shape.width * spread, cap = StrokeCap.Round)
                    ring(
                        shape.radius,
                        bright.copy(alpha = strength * presence),
                        start + cap,
                        sweep - 2 * cap,
                        glow,
                    )
                }
            }
            val color = lerp(RoutinePalette.muted(window.kind), bright, presence)
            ring(
                shape.radius,
                color,
                start + cap,
                sweep - 2 * cap,
                Stroke(shape.width, cap = StrokeCap.Round),
            )
        }
    }

    private fun DrawScope.drawStars(day: Double) {
        for (star in stars) {
            val angle = screenAngle(star.minute, day)
            val alpha = edgeFade(angle) * star.alpha
            if (alpha <= 0f) continue
            drawCircle(
                RoutinePalette.star.copy(alpha = alpha),
                star.size,
                polar(star.radius, angle),
            )
        }
    }

    private fun DrawScope.drawSkyArc(day: Double) {
        // In the turned frame, an angle is a time of day (0° is midnight); the visible half runs
        // from six hours before now to six hours after.
        val turn = (day * DEGREES_PER_MINUTE).toFloat()
        rotate(-turn, pivot = centre) {
            drawArc(
                skyBrush,
                startAngle = turn - 90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = centre - Offset(arcRadius, arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = arcStroke,
            )
        }
    }

    private fun DrawScope.drawHourMarks(time: Double) {
        hourMarks.forEachIndexed { index, image ->
            val angle = screenAngle(index * 3 * 60.0, time)
            val alpha = edgeFade(angle)
            if (alpha <= 0f) return@forEachIndexed
            val at = polar(markRadius, angle)
            drawImage(
                image,
                Offset(at.x - image.width / 2f, at.y - image.height / 2f),
                alpha = alpha,
            )
        }
    }

    /** Each window's icon rides its capsule, kept on the visible part of it. */
    private fun DrawScope.drawLaneIcons(time: Double) {
        val iconSize = px(TimelineMetrics.laneIcon)
        for (window in routine.windows) {
            val shape = lanes.getValue(window.lane)
            val start = screenAngle(window.startMinute.toDouble(), time)
            val end = start + window.durationMinutes * DEGREES_PER_MINUTE
            val inset = shape.width / shape.radius * DEGREES_PER_RADIAN * 0.55
            val low = maxOf(start + inset, -ICON_LIMIT)
            val high = minOf(end - inset, ICON_LIMIT)
            if (low > high) continue
            val angle = ((start + end) / 2).coerceIn(low, high)
            val presence = routine.presenceOf(window, time)
            val alpha = edgeFade(angle) * (ICON_REST + (1f - ICON_REST) * presence)
            RoutineIcons.of(window.kind)
                .draw(this, polar(shape.radius, angle), iconSize, Color.White, alpha)
        }
    }

    /** A small disc on the outer arc where each window starts. */
    private fun DrawScope.drawBadges(time: Double) {
        val radius = px(TimelineMetrics.badgeRadius)
        val iconSize = px(TimelineMetrics.badgeIcon)
        for (window in routine.windows) {
            val angle = screenAngle(window.startMinute.toDouble(), time)
            val alpha = edgeFade(angle) * (1f - nearMarker(angle))
            if (alpha <= 0f) continue
            val at = polar(arcRadius, angle)
            drawCircle(
                RoutinePalette.card.copy(alpha = alpha),
                radius + px(TimelineMetrics.hairline) * 2,
                at,
            )
            drawCircle(RoutinePalette.kind(window.kind).copy(alpha = alpha), radius, at)
            RoutineIcons.of(window.kind).draw(this, at, iconSize, RoutinePalette.card, alpha)
        }
    }

    private fun DrawScope.drawNowMarker(day: Double) {
        val at = centre + Offset(arcRadius, 0f)
        val sky = RoutinePalette.sky(day)
        val radius = px(TimelineMetrics.markerRadius)
        drawCircle(sky.copy(alpha = MARKER_HALO), radius * 1.9f, at)
        drawCircle(RoutinePalette.card, radius, at)
        drawCircle(sky, radius, at, style = Stroke(px(TimelineMetrics.markerStroke)))
    }

    /** The sun by day, the moon by night, each with its own glow, cross-fading at dawn and dusk. */
    private fun DrawScope.drawSky(day: Double) {
        val light = daylight(day)
        val iconSize = px(TimelineMetrics.skyIcon)
        val travel = px(TimelineMetrics.skyTravel)
        drawCircle(sunGlow, px(TimelineMetrics.skyGlow), skyCentre, alpha = light)
        drawCircle(moonGlow, px(TimelineMetrics.skyGlow), skyCentre, alpha = 1f - light)
        // The two swap over the middle of dawn and dusk rather than the whole of it, so they are
        // never both half there: the sun sinks and shrinks, the moon drops in from above.
        val sun = ((light - SWAP_FROM) / (1f - 2 * SWAP_FROM)).coerceIn(0f, 1f)
        val moon = 1f - sun
        if (sun > 0f) {
            val at = skyCentre + Offset(0f, travel * (1f - sun))
            // The rays turn slowly with the time, so scrubbing spins them.
            rotate((day * SUN_TURN).toFloat(), pivot = at) {
                RoutineIcons.sun.draw(
                    this,
                    at,
                    iconSize * (0.5f + 0.5f * sun),
                    RoutinePalette.sun,
                    sun,
                )
            }
        }
        if (moon > 0f) {
            val at = skyCentre - Offset(0f, travel * (1f - moon))
            RoutineIcons.moon.draw(
                this,
                at,
                iconSize * (0.5f + 0.5f * moon),
                RoutinePalette.moon,
                moon,
            )
        }
    }

    private fun DrawScope.drawClock(day: Double) {
        val text = clockText(floor(day).toInt())
        var width = 0f
        for (c in text.digits) width += digits.getValue(c).width
        var x = centre.x - width / 2
        val y = centre.y + px(TimelineMetrics.clockY)
        for (c in text.digits) {
            val image = digits.getValue(c)
            drawImage(image, Offset(x, y - image.height / 2f))
            x += image.width
        }
        val meridiem = meridiems.getValue(text.meridiem)
        drawImage(
            meridiem,
            Offset(
                centre.x - meridiem.width / 2f,
                centre.y + px(TimelineMetrics.meridiemY) - meridiem.height / 2f,
            ),
        )
    }

    private fun DrawScope.ring(
        radius: Float,
        color: Color,
        start: Float,
        sweep: Float,
        stroke: Stroke,
    ) {
        if (sweep <= 0f) return
        drawArc(
            color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = centre - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
            style = stroke,
        )
    }

    private fun polar(radius: Float, degrees: Double): Offset {
        val a = degrees * PI / 180
        return centre + Offset((radius * cos(a)).toFloat(), (radius * sin(a)).toFloat())
    }

    private fun glow(color: Color): Brush =
        Brush.radialGradient(
            0f to color.copy(alpha = 0.34f),
            0.45f to color.copy(alpha = 0.12f),
            1f to color.copy(alpha = 0f),
            center = skyCentre,
            radius = px(TimelineMetrics.skyGlow),
        )

    private class LaneShape(val radius: Float, val width: Float)

    private class Star(val minute: Double, val radius: Float, val size: Float, val alpha: Float)

    private companion object {
        const val SKY_STOPS = 48
        const val STAR_COUNT = 22
        const val STAR_SEED = 50
        const val DEGREES_PER_RADIAN = 180 / PI
        /** Each glow stroke: its width as a multiple of the lane's, and its alpha. */
        val GLOW_LAYERS = listOf(1.55f to 0.06f, 1.3f to 0.08f, 1.12f to 0.12f)
        const val SWAP_FROM = 0.3f
        const val ICON_LIMIT = 76.0
        const val ICON_REST = 0.5f
        const val DASH_ALPHA = 0.5f
        const val MARKER_HALO = 0.16f
        const val SUN_TURN = 0.5
    }
}

/** 1 across most of the visible half, falling to 0 as an angle nears the top or bottom. */
private fun edgeFade(degrees: Double): Float {
    val t = ((abs(degrees) - 70.0) / 20.0).coerceIn(0.0, 1.0)
    return (1 - t * t * (3 - 2 * t)).toFloat()
}

/** 1 right at the marker, 0 from 7° away, so a badge slides under it instead of clashing. */
private fun nearMarker(degrees: Double): Float =
    (1.0 - abs(degrees) / 7.0).coerceIn(0.0, 1.0).toFloat()
