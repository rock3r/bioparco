// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.pathOf
import kotlin.math.floor
import kotlin.math.hypot

data class SpotShade(val d: IsoPath, val shade: Int)

/** A contour's lit strips and silhouette, from `drops.ts/solidOf`. */
class SpotGlass
internal constructor(
    private val ring: List<Floor>,
    private val z: Double,
    private val height: Double,
    occlude: Boolean,
) {
    private val count = ring.size
    private val normals = SpotContours.normalsOf(ring)
    private val facing = normals.map { it.x * SpotRig.VIEW.x + it.y * SpotRig.VIEW.y > 1e-9 }
    private val upper = ring.map { SpotRig.at(Point3(it.x, it.y, z + height)) }
    private val bottom = ring.map { SpotRig.at(Point3(it.x, it.y, z)) }
    private val hidden = if (occlude) SpotOcclusion(ring, z + height) else null
    val fill = pathOf(oriented(upper), true) + pathOf(oriented(bottom), true)
    val shades = strips()
    val top = pathOf(oriented(upper), true)
    val crease =
        IsoPath.join(SpotContours.runsOf(facing, true).map { run -> pathOf(run.map { upper[it] }) })
    val bevel = IsoPath.join(SpotContours.runsOf(facing, true).map(::bevelOf))
    val outline = outlineOf()
    val shine = shineOf()
    val depth =
        ring.sumOf { it.x } / count * SpotRig.VIEW.x + ring.sumOf { it.y } / count * SpotRig.VIEW.y

    private fun oriented(points: List<Flat>): List<Flat> =
        if (SpotContours.signedArea(points.map { Floor(it.x, it.y) }) < 0) points.reversed()
        else points

    private class Strip(val depth: Double, val shade: Int, val d: IsoPath)

    private class Open(val from: Int, var to: Int, val shade: Int)

    private fun strips(): List<SpotShade> {
        val strips = ArrayList<Strip>()
        var open: Open? = null
        fun close() {
            val part = open ?: return
            val upper = ArrayList<Flat>()
            val lower = ArrayList<Flat>()
            var depth = 0.0
            for (index in part.from..part.to + 1) {
                val point = index % count
                upper.add(this.upper[point])
                lower.add(bottom[point])
                depth += ring[point].x * SpotRig.VIEW.x + ring[point].y * SpotRig.VIEW.y
            }
            strips.add(
                Strip(
                    depth / upper.size,
                    part.shade,
                    pathOf(oriented(upper + lower.reversed()), true),
                )
            )
            open = null
        }
        for (index in ring.indices) {
            if (!facing[index]) {
                close()
                continue
            }
            val n = normals[index]
            val lit = (n.x * SpotRig.LIGHT.x + n.y * SpotRig.LIGHT.y + 1) / 2
            val shade = floor(lit * 4).toInt().coerceIn(0, 3)
            val part = open
            if (part != null && part.shade == shade && index - part.from < 8) part.to = index
            else {
                close()
                open = Open(index, index, shade)
            }
        }
        close()
        val shades = ArrayList<SpotShade>()
        for (strip in strips.sortedBy { it.depth }) {
            val last = shades.lastOrNull()
            if (last != null && last.shade == strip.shade)
                shades[shades.lastIndex] = SpotShade(last.d + strip.d, last.shade)
            else shades.add(SpotShade(strip.d, strip.shade))
        }
        return shades
    }

    private fun inward(index: Int, by: Double): Floor {
        val a = normals[(index + count - 1) % count]
        val b = normals[index]
        val mx = a.x + b.x
        val my = a.y + b.y
        val length = hypot(mx, my).takeIf { it != 0.0 } ?: 1.0
        return Floor(ring[index].x - mx / length * by, ring[index].y - my / length * by)
    }

    private fun bevelOf(run: List<Int>): IsoPath {
        val along = arrayListOf(0.0)
        for (step in 1 until run.size) {
            val a = ring[run[step - 1]]
            val b = ring[run[step]]
            along.add(along[step - 1] + hypot(b.x - a.x, b.y - a.y))
        }
        val total = along.last()
        return pathOf(
            run.mapIndexed { step, index ->
                val near = minOf(along[step], total - along[step])
                val point = inward(index, 2.2 * SpotDrops.ease(minOf(1.0, near / 10)))
                SpotRig.at(Point3(point.x, point.y, z + height))
            }
        )
    }

    private fun outlineOf(): IsoPath {
        val outline = ArrayList<IsoPath>()
        for (run in SpotContours.runsOf(facing, false)) outline.add(pathOf(run.map { upper[it] }))
        for (run in SpotContours.runsOf(facing, true)) {
            if (hidden != null) for (seen in hidden.visibleRuns(run, z)) outline.add(pathOf(seen))
            else outline.add(pathOf(run.map { bottom[it] }))
        }
        for (index in ring.indices) {
            if (facing[(index + count - 1) % count] == facing[index]) continue
            val point = ring[index]
            val from = hidden?.from(index, z, height) ?: z
            if (z + height - from > 0.05)
                outline.add(
                    pathOf(listOf(upper[index], SpotRig.at(Point3(point.x, point.y, from))))
                )
        }
        return IsoPath.join(outline)
    }

    private fun shineOf(): IsoPath {
        val toward = normals.mapIndexed { index, n ->
            val p = normals[(index + count - 1) % count]
            ((n.x + p.x) * SpotRig.LIGHT.x + (n.y + p.y) * SpotRig.LIGHT.y) / 2 > 0.82
        }
        return IsoPath.join(
            SpotContours.runsOf(toward, true)
                .filter { it.size > 3 }
                .map { run ->
                    pathOf(
                        run.drop(1).dropLast(1).map { index ->
                            val p = inward(index, 3.4)
                            SpotRig.at(Point3(p.x, p.y, z + height))
                        }
                    )
                }
        )
    }
}
