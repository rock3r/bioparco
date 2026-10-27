package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.LatheParityTest.Companion.FRAMES
import dev.sebastiano.anatomy.LatheParityTest.Companion.bell
import dev.sebastiano.anatomy.LatheParityTest.Companion.bellSlope
import dev.sebastiano.anatomy.ParityLines.Companion.num
import dev.sebastiano.anatomy.ParityLines.Companion.points3
import dev.sebastiano.anatomy.kit.Bands
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Rims
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.bandsOf
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.spokesOf

/** The bands and the lines and dots on round surfaces of [LatheParityTest], in the same order. */
internal object LatheLineCases {
    fun bandsOf(out: ParityLines, projection: Projection, name: String) {
        val f = FRAMES[2].second
        val x = FRAMES[1].second
        val analytic =
            bandsOf(
                listOf(Knot(0.0, bell(0.0)), Knot(30.0, bell(30.0))),
                listOf(8.0, 16.0, 16.0, 24.0, 40.0, -3.0),
                f,
                projection,
                slope = ::bellSlope,
                radius = ::bell,
                rows = 4,
            )
        val profile =
            listOf(
                Knot(0.0, 12.0),
                Knot(6.0, 11.0, true),
                Knot(12.0, 9.0, true),
                Knot(20.0, 8.0, true),
                Knot(26.0, 8.0),
            )
        val fitted =
            bandsOf(profile, listOf(6.0, 14.0), x, projection, rims = Rims.First, samples = 40)
        bands(out, "$name.bands.analytic", analytic)
        bands(out, "$name.bands.fitted", fitted)
    }

    private fun bands(out: ParityLines, at: String, bands: Bands) {
        val b = bands
        out.put(
            at,
            "${b.steps} ${num(b.s0)} ${num(b.s1)} ${num(b.radius(5.0))} ${num(b.slope(5.0))} " +
                "${b.bands.size}",
        )
        b.bands.forEachIndexed { index, band ->
            val key = "$at.$index"
            val knots =
                band.profile.joinToString(" ") {
                    "${num(it.s)}/${num(it.r)}${if (it.smooth) "/1" else ""}"
                }
            out.put("$key.span", "${num(band.s0)} ${num(band.s1)} $knots")
            out.solid(key, band.paths)
            out.put("$key.ownOutline", band.outline)
            out.put("$key.seam", band.seam)
        }
        val sides = b.sidesOf()
        out.put("$at.sides", sides.d)
        out.put("$at.sides.points", sides.points.joinToString(" | ") { points3(it) })
        out.put("$at.sides.ends", b.sidesOf(2.0, 20.0, ends = Seams.Both, count = 24).d)
        out.put("$at.sides.first", b.sidesOf(4.0, 50.0, ends = Seams.First).d)
        out.put("$at.sides.last", b.sidesOf(to = 22.0, ends = Seams.Last, count = 30).d)
    }

    fun linesOf(out: ParityLines, projection: Projection, name: String) {
        val p = projection
        for ((frameName, f) in FRAMES) {
            val at = "$name.$frameName"
            out.put("$at.arc", arcOf(f, 4.0, 9.0, p))
            out.put("$at.arc.slope", arcOf(f, 4.0, 9.0, p, slope = 0.4, steps = 20))
            out.put("$at.arc.inward", arcOf(f, 4.0, 9.0, p, inward = true, least = -1.0))
            out.put("$at.arc.inwardSlope", arcOf(f, 4.0, 9.0, p, inward = true, slope = -0.5))
            out.put("$at.arc.whole", arcOf(f, 4.0, 9.0, p, least = -1.0))
            out.put("$at.arc.limbs", arcOf(f, 4.0, 9.0, p, from = 0.5, to = 4.0))
            out.put(
                "$at.arc.limbsWide",
                arcOf(f, 4.0, 9.0, p, from = -2.0, to = 5.0, trim = 0.6, least = 0.1),
            )
            out.put("$at.arc.noTrim", arcOf(f, 4.0, 9.0, p, from = 1.0, to = 6.0, trim = 0.0))
            out.put("$at.circle", circleOf(f, 3.0, 8.0, p))
            out.put("$at.circle.steps", circleOf(f, 3.0, 8.0, p, 20))
            out.put("$at.spokes", spokesOf(f, 2.0, 3.0, 9.0, 8, p, phase = 0.1, sweep = 0.3))
            out.put("$at.spokes.range", spokesOf(f, 2.0, 3.0, 9.0, 5, p, from = 1.0, to = 3.0))
            ribsAndDots(out, at, f, p)
        }
    }

    private fun ribsAndDots(
        out: ParityLines,
        at: String,
        f: Frame,
        p: Projection,
    ) {
        out.put("$at.ribs", ribsOf(f, 0.0, 12.0, 8.0, 24, p))
        out.put(
            "$at.ribs.cone",
            ribsOf(f, 0.0, 12.0, 8.0, 30, p, 0.05, 0.4, least = 0.05, r1 = 5.0, seams = Seams.Both),
        )
        out.faded(
            "$at.ribs.fade",
            ribsOf(f, 0.0, 12.0, 8.0, 24, p, fade = 0.1 to 0.55, seams = Seams.First),
        )
        out.faded(
            "$at.ribs.fadeLast",
            ribsOf(
                f,
                2.0,
                10.0,
                6.0,
                18,
                p,
                fade = 0.1 to 0.55,
                seams = Seams.Last,
                r1 = 7.0,
                twist = -0.3,
            ),
        )
        out.dots("$at.dots", dotsOf(f, 6.0, 9.0, 12, p))
        out.dots(
            "$at.dots.slope",
            dotsOf(f, 6.0, 9.0, 16, p, slope = 0.5, least = 0.2, phase = 0.2),
        )
        out.dots("$at.dots.all", dotsOf(f, 6.0, 9.0, 8, p, all = true))
        val fade = dotsOf(f, 6.0, 9.0, 20, p, fade = 0.04 to 0.42)
        out.dots("$at.dots.fade", fade.points, fade.alphas)
        val fadeAll = dotsOf(f, 6.0, 9.0, 10, p, fade = 0.04 to 0.42, all = true)
        out.dots("$at.dots.fadeAll", fadeAll.points, fadeAll.alphas)
    }
}
