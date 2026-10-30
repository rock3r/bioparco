package dev.sebastiano.tabswitch

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The four icons, each as an outline for outside the pill and a solid shape for inside it. */
internal enum class TabIcon {
    Home,
    Saved,
    Inbox,
    Profile,
}

/**
 * One icon's paths at a given size in pixels. [outline] is stroked; [solid] is filled and then
 * stroked with the same width, which rounds its corners like the outline's.
 */
internal class IconPaths(val outline: Path, val solid: Path)

/** Builds [icon]'s paths at [px] pixels square. The shapes are drawn on a 24-unit grid. */
internal fun iconPaths(icon: TabIcon, px: Float): IconPaths {
    val u = px / GRID
    return when (icon) {
        TabIcon.Home -> IconPaths(outline = house(u).apply { door(u) }, solid = house(u))
        TabIcon.Saved -> IconPaths(outline = bookmark(u), solid = bookmark(u))
        TabIcon.Inbox -> IconPaths(outline = bubble(u), solid = bubble(u))
        TabIcon.Profile ->
            IconPaths(outline = person(u, closed = false), solid = person(u, closed = true))
    }
}

/** A house: a pitched roof over square walls. */
private fun house(u: Float) =
    Path().apply {
        moveTo(4f * u, 10.2f * u)
        lineTo(12f * u, 3.6f * u)
        lineTo(20f * u, 10.2f * u)
        lineTo(20f * u, 20.2f * u)
        lineTo(4f * u, 20.2f * u)
        close()
    }

/** The outline house's door, open at the bottom. */
private fun Path.door(u: Float) {
    moveTo(9.6f * u, 20.2f * u)
    lineTo(9.6f * u, 14.6f * u)
    lineTo(14.4f * u, 14.6f * u)
    lineTo(14.4f * u, 20.2f * u)
}

/** A bookmark ribbon with a notch cut into its foot. */
private fun bookmark(u: Float) =
    Path().apply {
        moveTo(6.4f * u, 3.6f * u)
        lineTo(17.6f * u, 3.6f * u)
        lineTo(17.6f * u, 20.4f * u)
        lineTo(12f * u, 16.4f * u)
        lineTo(6.4f * u, 20.4f * u)
        close()
    }

/** A round speech bubble with its tail at the bottom left. */
private fun bubble(u: Float) =
    Path().apply {
        val cx = 12f * u
        val cy = 11.6f * u
        val r = 8.4f * u
        val start = 150f
        moveTo(cx + r * cos(start.rad()), cy + r * sin(start.rad()))
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), start, 315f, forceMoveTo = false)
        lineTo(3.8f * u, 20.4f * u)
        close()
    }

/** A head over a pair of shoulders: open for the outline, closed along the bottom for the solid. */
private fun person(u: Float, closed: Boolean) =
    Path().apply {
        addOval(Rect(8.2f * u, 3.4f * u, 15.8f * u, 11f * u))
        moveTo(4.8f * u, 20.4f * u)
        cubicTo(4.8f * u, 16.4f * u, 8f * u, 14f * u, 12f * u, 14f * u)
        cubicTo(16f * u, 14f * u, 19.2f * u, 16.4f * u, 19.2f * u, 20.4f * u)
        if (closed) close()
    }

private fun Float.rad(): Float = this * PI.toFloat() / 180f

/** The grid the icons are drawn on, and their stroke on it. */
internal const val GRID = 24f
internal const val STROKE = 1.8f
