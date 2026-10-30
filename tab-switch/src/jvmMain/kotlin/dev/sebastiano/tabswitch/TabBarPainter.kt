package dev.sebastiano.tabswitch

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.roundToInt

/** A tab: its [label], its [icon], and the count in its [badge], if it has one. */
internal class Tab(val label: String, val icon: TabIcon, val badge: String? = null)

internal val Tabs =
    listOf(
        Tab("Home", TabIcon.Home),
        Tab("Saved", TabIcon.Saved),
        Tab("Inbox", TabIcon.Inbox, badge = "2"),
        Tab("Profile", TabIcon.Profile),
    )

/**
 * Draws the bar from a [TabMotion], in two layers. Underneath, every icon is a light outline on the
 * bar. On top, clipped to the pill, the pill itself and every icon again, dark and solid, with the
 * labels. Where the pill covers part of an icon, that part turns solid and the rest stays an
 * outline, so the pill seems to develop the icons as it slides over them.
 *
 * Everything that can be built ahead of a frame is built here: the icon paths at their pixel size,
 * the measured labels, and the layout, which works in dp.
 */
internal class TabBarPainter(
    val tabs: List<Tab>,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    badgeStyle: TextStyle,
    private val density: Float,
) {
    private val labels: List<TextLayoutResult> = tabs.map { measurer.measure(it.label, labelStyle) }
    private val badges: List<TextLayoutResult?> = tabs.map { tab ->
        tab.badge?.let { measurer.measure(it, badgeStyle) }
    }

    val layout =
        TabBarLayout(
            expandedWidths =
                FloatArray(tabs.size) {
                    PAD_START + ICON + GAP + labels[it].size.width / density + PAD_END
                },
            slot = SLOT,
            iconInset = PAD_START + ICON / 2f,
        )

    private val iconPx = ICON * density
    private val unit = iconPx / GRID
    private val icons = tabs.map { iconPaths(it.icon, iconPx) }
    private val stroke = Stroke(STROKE * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val pill = Path()
    private var primeFrames = PRIME_FRAMES

    /** The tab under [x] pixels from the bar's left end, as the bar is drawn now. */
    fun tabAt(x: Float, motion: TabMotion): Int? = layout.tabAt(x / density - BAR_PAD, motion.spans)

    fun draw(scope: DrawScope, motion: TabMotion) =
        with(scope) {
            val inset = BAR_PAD * density
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(TabSwitchPalette.bar, cornerRadius = radius)

            Tracing.section("outlines") {
                for (tab in tabs.indices) {
                    val colour =
                        lerp(
                            TabSwitchPalette.icon,
                            TabSwitchPalette.iconHover,
                            motion.highlight[tab],
                        )
                    val x = iconX(tab, motion)
                    drawIcon(icons[tab].outline, x, colour, solid = false)
                    drawBadge(
                        tab,
                        x,
                        fill = TabSwitchPalette.iconHover,
                        ink = TabSwitchPalette.ink,
                        ring = TabSwitchPalette.bar,
                    )
                }
            }

            val left = inset + motion.pillLeft * density
            val right = inset + motion.pillRight * density
            pill.reset()
            pill.addRoundRect(
                RoundRect(
                    left,
                    inset,
                    right,
                    size.height - inset,
                    CornerRadius((size.height - 2 * inset) / 2f),
                )
            )
            if (primeFrames > 0) {
                primeFrames--
                Tracing.section("primer") { prime(left) }
            }
            Tracing.section("pill") {
                clipPath(pill) {
                    drawRect(
                        TabSwitchPalette.pill,
                        Offset(left, inset),
                        Size(right - left, size.height - 2 * inset),
                    )
                    for (tab in tabs.indices) {
                        val x = iconX(tab, motion)
                        // Skip tabs the pill does not reach: the badge and label stick out right.
                        val reach =
                            iconPx +
                                if (motion.labelAlpha[tab] > 0f) labels[tab].size.width.toFloat()
                                else 0f
                        if (x + reach < left || x - iconPx > right) continue
                        drawIcon(icons[tab].solid, x, TabSwitchPalette.ink, solid = true)
                        drawBadge(
                            tab,
                            x,
                            fill = TabSwitchPalette.ink,
                            ink = TabSwitchPalette.pill,
                            ring = TabSwitchPalette.pill,
                        )
                        drawLabel(tab, x, motion.labelAlpha[tab])
                    }
                }
            }
        }

    /**
     * Draws everything the pill can show, inside the pill's area, just before the pill is painted
     * over it, which leaves every pixel as the pill alone would. Cold, the first switch drew a 16
     * ms frame in software: it drew the other labels' glyphs, the solid icons and the dark badge
     * for the first time. Now the first frames do that. Each tab's icon, badge and label fit in the
     * resting pill, which is as wide as an open tab.
     */
    private fun DrawScope.prime(pillLeft: Float) {
        val x = pillLeft + PAD_START * density + iconPx / 2f
        for (tab in tabs.indices) {
            drawIcon(icons[tab].solid, x, TabSwitchPalette.ink, solid = true)
            drawBadge(
                tab,
                x,
                fill = TabSwitchPalette.ink,
                ink = TabSwitchPalette.pill,
                ring = TabSwitchPalette.pill,
            )
            drawLabel(tab, x, alpha = 1f)
        }
    }

    /** Where [tab]'s icon centre is now, in pixels from the bar's left end. */
    private fun iconX(tab: Int, motion: TabMotion): Float {
        val spans = motion.spans
        val centre = layout.iconCentre(spans[2 * tab], spans[2 * tab + 1], motion.expansion[tab])
        return (BAR_PAD + centre) * density
    }

    private fun DrawScope.drawIcon(path: Path, x: Float, colour: Color, solid: Boolean) {
        translate(x - iconPx / 2f, (size.height - iconPx) / 2f) {
            if (solid) drawPath(path, colour)
            drawPath(path, colour, style = stroke)
        }
    }

    /**
     * The count at the icon's top right, ringed in whatever is behind it to part it from the icon.
     * The count sits on its circle to a fraction of a pixel, which its style's sub-pixel glyph
     * positioning allows. Snapped to whole pixels, it hopped a pixel against the circle as the tab
     * slid to rest. Only its height is rounded, which never changes.
     */
    private fun DrawScope.drawBadge(tab: Int, x: Float, fill: Color, ink: Color, ring: Color) {
        val text = badges[tab] ?: return
        val centre =
            Offset(x - iconPx / 2f + BADGE_X * unit, (size.height - iconPx) / 2f + BADGE_Y * unit)
        drawCircle(ring, radius = (BADGE_RADIUS + BADGE_RING) * unit, center = centre)
        drawCircle(fill, radius = BADGE_RADIUS * unit, center = centre)
        drawText(
            text,
            color = ink,
            topLeft =
                Offset(
                    centre.x - text.size.width / 2f,
                    (centre.y - text.size.height / 2f).roundToInt().toFloat(),
                ),
        )
    }

    private fun DrawScope.drawLabel(
        tab: Int,
        x: Float,
        alpha: Float,
    ) {
        if (alpha <= 0f) return
        val text = labels[tab]
        drawText(
            text,
            color = TabSwitchPalette.ink,
            alpha = alpha,
            topLeft =
                Offset(
                    (x + iconPx / 2f + GAP * density).roundToInt().toFloat(),
                    ((size.height - text.size.height) / 2f).roundToInt().toFloat(),
                ),
        )
    }

    companion object {
        /** The bar's height and the gap round the pill, in dp. */
        const val BAR_HEIGHT = 62f
        const val BAR_PAD = 5f
        const val PILL_HEIGHT = BAR_HEIGHT - 2 * BAR_PAD

        /** An open tab, left to right: padding, icon, gap, label, padding. In dp. */
        const val PAD_START = 18f
        const val ICON = 24f
        const val GAP = 9f
        const val PAD_END = 22f

        /** A closed tab next to the widest open one, in dp. */
        const val SLOT = 62f

        private const val PRIME_FRAMES = 2

        /** The badge, on the icon's 24-unit grid. */
        private const val BADGE_X = 19.6f
        private const val BADGE_Y = 4.6f
        private const val BADGE_RADIUS = 7.2f
        private const val BADGE_RING = 2.2f
    }
}
