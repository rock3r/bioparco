// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.graphics.Color
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.FillInk
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.fill

/** `spot-plate.css` materials and `live.tsx` painter's order. */
internal class SpotPaint(private val palette: Palette) {
    private fun color(light: Long, dark: Long) = Color(if (palette.isDark) dark else light)

    private val trayPalette = palette.copy(top = color(0xFFFBFBFA, 0xFF1C1C1C))
    private val wellPalette =
        palette.copy(
            shades =
                listOf(color(0xFFE4E4E3, 0xFF0B0B0B), color(0xFFF2F2F1, 0xFF121212)) +
                    palette.shades.drop(2)
        )
    private val glassPalette = palette.copy(top = color(0xFFFDFDFC, 0xFF202020))
    private val joinedPalette = palette.copy(top = color(0xFFFEFEFD, 0xFF262626))
    private val bladePalette = palette.copy(top = color(0xFFFBFBF9, 0xFF1B1B1B))
    private val outPalette = palette.copy(top = color(0xFFFEFEFC, 0xFF232323))
    private val leadPalette = bladePalette.copy(litTop = bladePalette.top)
    private val rowCache = LinkedHashMap<SpotRow, List<Art>>()

    private fun glassArt(shape: SpotGlass, joined: Boolean): Art = art {
        fill(shape.fill, FillInk.Paper)
        for (shade in shape.shades) fill(
            shade.d,
            listOf(FillInk.Shade0, FillInk.Shade1, FillInk.Shade2, FillInk.Shade3)[shade.shade],
        )
        // Keep strip order from the concave field; a kit solid's four shade buckets would reorder
        // it.
        solid(
            SolidPaths(
                IsoPath.Empty,
                shape.outline,
                shape.crease,
                shape.top,
                emptyList(),
                shape.bevel,
            ),
            if (joined) Tone.Hi else Tone.Mid,
            if (joined) Tone.Lo else Tone.Faint,
        )
        optical(shape.shine, Tone.Mid)
    }

    private fun rowArt(row: SpotRow): List<Art> =
        rowCache.getOrPut(row) {
            if (rowCache.size >= 420) rowCache.remove(rowCache.keys.first())
            row.glass.map { glassArt(it, row.joined) } +
                art { for (glint in row.neck) optical(glint.d, Tone.Lit, glint.alpha) }
        }

    fun draw(scope: IsoScope, merge: Double) =
        with(scope) {
            drawArt(SpotArt.base)
            withPalette(trayPalette) { drawArt(SpotArt.tray) }
            withPalette(wellPalette) { drawArt(SpotArt.wells) }
            for (row in SpotDrops.rowsOf(SpotDrops.keyOf(merge) / 4.0)) {
                withPalette(if (row.joined) joinedPalette else glassPalette) {
                    for (art in rowArt(row)) drawArt(art)
                }
            }
            drawArt(SpotArt.board)
            withPalette(glassPalette) { drawArt(SpotArt.bottleBody) }
            drawArt(SpotArt.bottleTop)
            drawArt(SpotArt.fanBack)
            fan(scope, SpotFan.fanOf(merge))
            drawArt(SpotArt.front)
        }

    private fun fan(scope: IsoScope, pose: SpotFanPose) =
        with(scope) {
            // CSS blur(4px), opacity .8, translateY(1.5px); the kit halo already drops 3 units.
            drawArt(art { halo(pose.halo, 4.0, 0.8) }, Flat(0.0, -1.5))
            pose.blades.forEachIndexed { index, blade ->
                val lead = index == SpotRig.ROWS
                val out = !lead && pose.out[index]
                withPalette(if (lead) leadPalette else if (out) outPalette else bladePalette) {
                    solid(
                        blade.paths,
                        if (lead || out) Tone.Hi else Tone.Mid,
                        Tone.Faint,
                        lit = lead,
                    )
                    // An engraved rule along each blade, intentionally short of both ends.
                    line(blade.line, if (lead) Tone.Lit else Tone.Lo, free = Free.Both)
                }
            }
        }
}
