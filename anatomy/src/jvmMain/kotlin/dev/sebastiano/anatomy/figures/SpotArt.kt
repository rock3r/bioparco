// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.FillInk
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Shape
import dev.sebastiano.anatomy.kit.Stroke
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.fill

/** Retained layers from `art.tsx`, split only where CSS changes the material's top colour. */
internal object SpotArt {
    val base = art {
        val b = SpotBase
        halo(b.halo)
        for (parts in b.feet) parts.forEachIndexed { piece, paths ->
            solid(paths, tone = if (piece != 0) Tone.Mid else Tone.Lo)
        }
        solid(b.slab, tone = Tone.Mid)
        line(b.groove, Tone.Lo)
        line(b.inlay, Tone.Faint)
        line(b.screwRings, Tone.Lo)
        dots(b.screws, 0.5, Tone.Mid)
    }
    val tray = art { solid(SpotTray.slab, Tone.Hi, Tone.Lo) }
    val wells = art {
        val t = SpotTray
        line(t.groove, Tone.Lo)
        line(t.inlay, Tone.Faint)
        for (well in t.wells) {
            fill(well.opening, FillInk.Shade1)
            fill(well.wall, FillInk.Shade0)
            line(well.foot, Tone.Faint)
            line(well.opening, Tone.Mid)
        }
        dots(t.counts, 0.55, Tone.Mid)
        line(t.screwRings, Tone.Lo)
        dots(t.screws, 0.5, Tone.Mid)
        line(t.minor, Tone.Lo)
        line(t.major, Tone.Mid)
        line(t.datum, Tone.Mid)
        dots(t.gapMarks, 0.6, Tone.Hi)
    }
    val board = art {
        val d = SpotDeck
        val b = SpotBottle
        solid(d.slab, Tone.Mid)
        line(d.inlay, Tone.Faint)
        line(d.screwRings, Tone.Lo)
        dots(d.screws, 0.45, Tone.Mid)
        solid(b.coaster, Tone.Mid)
        line(b.coasterRing, Tone.Faint)
        dots(b.coasterDots, 0.45, Tone.Mid)
    }
    val bottleBody = art {
        val b = SpotBottle
        solid(b.body, Tone.Hi)
        line(b.foot, Tone.Faint)
        line(b.level, Tone.Lo)
        optical(b.shine, Tone.Mid)
        solid(b.shoulder, Tone.Hi)
    }
    val bottleTop = art {
        val b = SpotBottle
        solid(b.neck, Tone.Mid)
        solid(b.collar, Tone.Mid)
        solid(b.cap, Tone.Hi, Tone.Lo)
        line(b.capKnurl, Tone.Lo)
        line(b.capRing, Tone.Faint)
        solid(b.bulb, Tone.Mid)
        optical(b.bulbShine, Tone.Lo)
        line(b.bulbRing, Tone.Faint)
        dots(listOf(b.tip), 0.45, Tone.Mid)
    }
    val fanBack = art {
        val d = SpotDeck
        // The guide is an engraved protractor rule, not a seam between parts.
        line(d.guide, Tone.Faint, Stroke.Dashed, Free.Both)
        for (paths in d.stops) solid(paths, Tone.Mid)
        dots(d.stopTops, 0.45, Tone.Hi)
        solid(d.bandBack, Tone.Mid)
        faceText(d.label, "merge", 4.2, Tone.Lo)
        solid(d.washer, Tone.Lo)
    }
    val front = art {
        val d = SpotDeck
        solid(d.bandFront, Tone.Mid)
        line(d.bandLine, Tone.Faint)
        line(d.minor, Tone.Lo)
        line(d.major, Tone.Mid)
        dots(d.gapDots, 0.55, Tone.Hi)
        dots(d.ends + d.seam, 0.45, Tone.Mid)
        solid(d.cap, Tone.Mid)
        solid(d.nut, Tone.Hi, Tone.Lo)
        line(d.nutKnurl, Tone.Lo)
        line(d.nutRing, Tone.Faint)
        dots(listOf(d.pin), 0.6, Tone.Hi)
    }
}

/** Optical glints aren't physical texture lines: they can terminate on a smooth glass face. */
internal fun ArtBuilder.optical(path: IsoPath, tone: Tone, alpha: Double = 1.0) {
    if (!path.isEmpty)
        add(
            dev.sebastiano.anatomy.kit.Art(
                listOf(
                    LineMark(
                        Shape(path),
                        tone,
                        Stroke.Solid,
                        Free.None,
                        null,
                        alpha,
                        checkEnds = false,
                    )
                )
            )
        )
}
