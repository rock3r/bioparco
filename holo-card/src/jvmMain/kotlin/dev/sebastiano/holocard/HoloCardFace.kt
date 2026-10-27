package dev.sebastiano.holocard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * The front of a card. The stage turns it in 3D; the face reads the same [tilt] to shift its foil:
 * the mascot, the name, and a faint glare across the black. Nothing on it reaches past its rounded
 * corners, so the face paints its own shape rather than having the turned layer clip it: a clip
 * through a perspective turn costs most of a millisecond a frame in software.
 */
@Composable
internal fun CardFace(
    friend: Friend,
    reliefs: Reliefs?,
    tilt: () -> Tilt,
    modifier: Modifier = Modifier,
) {
    val base = JewelTheme.defaultTextStyle
    Box(modifier.background(CardBlack, RoundedCornerShape(CardCorner))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp)) {
            Text(
                "GLOW\nFRIENDS",
                style =
                    base.copy(
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                    ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "bioparco\ncollection",
                style = base.copy(color = Color(0xFF8E8E96), fontSize = 10.sp, lineHeight = 12.sp),
            )
            Spacer(Modifier.height(4.dp))
            MascotFoil(
                friend = friend,
                reliefs = reliefs,
                tilt = tilt,
                modifier = Modifier.size(MASCOT_SIZE).align(Alignment.CenterHorizontally),
            )
            FoilName(friend, tilt, base, Modifier.fillMaxWidth().height(50.dp))
            Box(
                Modifier.padding(start = 3.dp, top = 2.dp)
                    .size(18.dp, 3.dp)
                    .background(friend.foil[1], RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                friend.tagline,
                style = base.copy(color = Color(0xFFC8C8CE), fontSize = 12.sp, lineHeight = 16.sp),
            )
        }
        Glare(tilt, Modifier.fillMaxSize())
    }
}

/**
 * The mascot, floating a few dp above the card: it slides a little against the tilt, so it reads as
 * raised off the black.
 */
@Composable
internal fun MascotFoil(
    friend: Friend,
    reliefs: Reliefs?,
    tilt: () -> Tilt,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier
            .graphicsLayer {
                val now = tilt()
                translationX = -now.y * PARALLAX * density
                translationY = now.x * PARALLAX * density
            }
            .drawBehind {
                val relief = reliefs?.get(friend.mascot) ?: return@drawBehind
                val now = tilt()
                Tracing.section("holo foil") {
                    drawIntoCanvas {
                        HoloFoil.draw(
                            canvas = it.skiaCanvas,
                            relief = relief,
                            size = size.minDimension,
                            hue = friend.hue,
                            tiltX = now.x / HoloMotion.FULL_DEGREES,
                            tiltY = now.y / HoloMotion.FULL_DEGREES,
                        )
                    }
                    drawFeatures(friend, size.minDimension)
                }
            }
    )
}

/** The friend's name in foil: the colours slide across the letters as the card turns. */
@Composable
private fun FoilName(
    friend: Friend,
    tilt: () -> Tilt,
    base: TextStyle,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style =
        base.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
    Spacer(
        modifier.drawWithCache {
            val layout = measurer.measure(friend.title, style)
            val top = (size.height - layout.size.height) / 2f
            val colors = friend.foil + friend.foil.first()
            onDrawBehind {
                val now = tilt()
                val shift = (now.y * 0.06f - now.x * 0.03f) * size.width
                val brush =
                    Brush.linearGradient(
                        colors = colors,
                        start = Offset(shift, 0f),
                        end = Offset(shift + size.width * 0.9f, size.height * 0.6f),
                        tileMode = TileMode.Mirror,
                    )
                Tracing.section("foil name") {
                    drawText(layout, brush = brush, topLeft = Offset(0f, top))
                }
            }
        }
    )
}

/** A faint band of light across the card's black that sweeps the other way to the tilt. */
@Composable
private fun Glare(tilt: () -> Tilt, modifier: Modifier = Modifier) {
    Spacer(modifier.drawBehind { drawGlare(tilt()) })
}

private fun DrawScope.drawGlare(now: Tilt) {
    val along =
        0.5f - now.y / HoloMotion.FULL_DEGREES * 0.7f + now.x / HoloMotion.FULL_DEGREES * 0.4f
    val centre = Offset(size.width * along, size.height * along)
    val reach = size.minDimension * 0.55f
    Tracing.section("glare") {
        drawRoundRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, GlareWhite, Color.Transparent),
                start = centre - Offset(reach, reach),
                end = centre + Offset(reach, reach),
            ),
            cornerRadius = CornerRadius(CardCorner.toPx()),
        )
    }
}

internal val CardBlack = Color(0xFF09090B)
internal val CardCorner = 22.dp
private val GlareWhite = Color(0x14FFFFFF)
private val MASCOT_SIZE = 196.dp

/** How far the mascot slides per degree of tilt, in dp. */
private const val PARALLAX = 0.22f
