package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.SHADES
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.radialOf
import dev.sebastiano.anatomy.kit.tangentOf
import dev.sebastiano.anatomy.kit.toneOf
import dev.sebastiano.anatomy.kit.viewOf
import kotlin.math.ceil
import kotlin.math.max

/*
 * The boiler is drawn in section: its near half is cut away along a plane through its axis, so the
 * heat exchanger and the element show inside. The kit turns closed solids; a shell seen from
 * inside is drawn here as patches of a cylinder, each face shaded by the way it faces, painted
 * inner surface first, then the outer surface over the top, then the cut faces, so the shell
 * hides itself where it would.
 */
internal object EspressoShell {
    /**
     * The part of the cylinder of radius [r] about [frame], from [s0] to [s1] and from angle [a0]
     * to [a1], that faces the viewer: from inside when [inward]. [darker] drops every facet that
     * many tones, for the wet wall below a waterline.
     */
    fun patch(
        frame: Frame,
        s0: Double,
        s1: Double,
        r: Double,
        a0: Double,
        a1: Double,
        inward: Boolean,
        projection: Projection,
        darker: Int = 0,
    ): SolidPaths {
        val view = viewOf(projection)
        val n = max(4, ceil((a1 - a0) / (2 * Math.PI) * 180).toInt())
        fun angle(j: Int) = a0 + (a1 - a0) * j / n
        fun normal(t: Double) = mul3(radialOf(frame, t), if (inward) -1.0 else 1.0)
        val seen = List(n) { dot3(normal((angle(it) + angle(it + 1)) / 2), view) > 1e-6 }
        val buckets = List(SHADES + 1) { ArrayList<IsoPath>() }
        val outlines = ArrayList<IsoPath>()
        var j = 0
        while (j < n) {
            if (!seen[j]) {
                j++
                continue
            }
            val start = j
            while (j < n && seen[j]) j++
            val near = (start..j).map { iso(pointOf(frame, s0, r, angle(it)), projection) }
            val far = (j downTo start).map { iso(pointOf(frame, s1, r, angle(it)), projection) }
            outlines += pathOf(near + far, closed = true)
            for (k in start until j) {
                val tone = toneOf(normal((angle(k) + angle(k + 1)) / 2), projection)
                val quad =
                    listOf(
                        iso(pointOf(frame, s0, r, angle(k)), projection),
                        iso(pointOf(frame, s0, r, angle(k + 1)), projection),
                        iso(pointOf(frame, s1, r, angle(k + 1)), projection),
                        iso(pointOf(frame, s1, r, angle(k)), projection),
                    )
                buckets[bucketOf(tone, darker)].add(pathOf(quad, closed = true))
            }
        }
        val outline = IsoPath.join(outlines)
        return SolidPaths(
            fill = outline,
            outline = outline,
            crease = IsoPath.Empty,
            top = IsoPath.join(buckets[SHADES]),
            shades = (0 until SHADES).map { IsoPath.join(buckets[it]) },
        )
    }

    /** The flat cut face of the shell at [angle], from [rIn] to [rOut], along [s0]..[s1]. */
    fun cut(
        frame: Frame,
        s0: Double,
        s1: Double,
        rIn: Double,
        rOut: Double,
        angle: Double,
        facing: Double,
        projection: Projection,
    ): SolidPaths {
        val corners =
            listOf(
                pointOf(frame, s0, rIn, angle),
                pointOf(frame, s1, rIn, angle),
                pointOf(frame, s1, rOut, angle),
                pointOf(frame, s0, rOut, angle),
            )
        return face(corners, mul3(tangentOf(frame, angle), facing), projection)
    }

    /** One flat polygon facing [normal], shaded like a facet, [darker] tones down. */
    fun face(
        corners: List<Point3>,
        normal: Point3,
        projection: Projection,
        darker: Int = 0,
    ): SolidPaths {
        val path = pathOf(corners.map { iso(it, projection) }, closed = true)
        val bucket = bucketOf(toneOf(normal, projection), darker)
        return SolidPaths(
            fill = path,
            outline = path,
            crease = IsoPath.Empty,
            top = if (bucket == SHADES) path else IsoPath.Empty,
            shades = (0 until SHADES).map { if (it == bucket) path else IsoPath.Empty },
        )
    }

    /** The water's section on the cut plane, from the waterline down to the shell's floor. */
    fun waterFace(projection: Projection): IsoPath {
        val m = EspressoMachine
        val floor = m.BOILER_Z - (m.BOILER_R - m.WALL)
        val corners =
            listOf(
                Point3(m.SHELL_S0, m.BOILER_Y, m.WATER_Z),
                Point3(m.SHELL_S1, m.BOILER_Y, m.WATER_Z),
                Point3(m.SHELL_S1, m.BOILER_Y, floor),
                Point3(m.SHELL_S0, m.BOILER_Y, floor),
            )
        return pathOf(corners.map { iso(it, projection) }, closed = true)
    }

    /** A facet's tone bucket, [darker] tones down: wet facets lose the top tone too. */
    private fun bucketOf(tone: Int, darker: Int): Int =
        if (darker == 0) tone else max(0, minOf(tone, SHADES - 1) - darker)
}
