// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.EllipseNode
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.extremes
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.ringAt
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import dev.sebastiano.hairline.toFixed

private const val EXPLODED_REST = .18
private const val EXPLODED_THICKNESS = 2.4

private data class ExplodedRect(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

private data class ExplodedLayer(
    val name: String,
    val rect: ExplodedRect,
    val radius: Double,
    val segments: List<Pair<Vec2, Vec2>>,
    val dots: List<Vec2>? = null,
    val selection: ExplodedRect? = null,
)

private data class ExplodedEls(
    val ring: Ring,
    val inner: Ring,
    val extremes: List<Sample>,
    val guide: PathNode?,
    val solid: Solid,
    val marks: PathNode,
    val selectionRing: Ring?,
    val selection: PathNode?,
    val dots: List<EllipseNode>?,
)

private fun segment(x0: Double, y0: Double, x1: Double, y1: Double) = Vec2(x0, y0) to Vec2(x1, y1)

private val explodedLayers =
    listOf(
        ExplodedLayer(
            "surface",
            ExplodedRect(0.0, 0.0, 132.0, 96.0),
            7.0,
            listOf(segment(1.5, 12.0, 130.5, 12.0)),
            listOf(Vec2(7.0, 6.0), Vec2(13.0, 6.0), Vec2(19.0, 6.0)),
        ),
        ExplodedLayer(
            "sidebar",
            ExplodedRect(5.0, 17.0, 38.0, 91.0),
            4.0,
            listOf(
                segment(10.0, 25.0, 28.0, 25.0),
                segment(10.0, 33.0, 32.0, 33.0),
                segment(10.0, 41.0, 24.0, 41.0),
                segment(10.0, 49.0, 30.0, 49.0),
                segment(10.0, 83.0, 22.0, 83.0),
            ),
        ),
        ExplodedLayer(
            "card",
            ExplodedRect(48.0, 22.0, 120.0, 62.0),
            5.0,
            listOf(
                segment(54.0, 30.0, 92.0, 30.0),
                segment(54.0, 38.0, 112.0, 38.0),
                segment(54.0, 46.0, 104.0, 46.0),
                segment(54.0, 54.0, 80.0, 54.0),
            ),
        ),
        ExplodedLayer(
            "popover",
            ExplodedRect(80.0, 50.0, 126.0, 86.0),
            4.0,
            listOf(
                segment(86.0, 58.0, 118.0, 58.0),
                segment(86.0, 74.0, 116.0, 74.0),
                segment(86.0, 80.0, 108.0, 80.0),
            ),
            selection = ExplodedRect(83.0, 62.0, 123.0, 70.0),
        ),
    )

internal fun mountExploded(els: FigureEls, value: Double): FigureHandle = ExplodedFigure(els, value)

/**
 * Exploded — an app window taken apart into surface, sidebar, card and popover. Pointer x scrubs
 * the gap through a spring; y picks a layer. Picking tests the target gap, not the gap on screen.
 */
private class ExplodedFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var gap = value
    private var active = -1
    private var lastPointer: Vec2? = null
    private val expansion = spring(EXPLODED_REST, eps = .002)
    private val c =
        cam(45.0, .5, 1.42).also {
            fit(
                it,
                listOf(
                    Vec3(0.0, 0.0, 0.0),
                    Vec3(132.0, 96.0, 0.0),
                    Vec3(132.0, 0.0, 0.0),
                    Vec3(0.0, 96.0, 0.0),
                    Vec3(0.0, 0.0, 3 * 34.0 + EXPLODED_THICKNESS),
                    Vec3(132.0, 0.0, 3 * 34.0 + EXPLODED_THICKNESS),
                ),
                180.0,
                166.0,
            )
        }
    private val p = proj(c)
    private val front = facing(c)
    private val g = els.svg.g()
    private val layerEls = explodedLayers.mapIndexed(::buildLayer)
    private val loop = els.stage.register { dt, _ -> draw(dt) }

    init {
        bag.add(loop::unregister)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        lastPointer = p
                        expansion.t =
                            EXPLODED_REST + (1 - EXPLODED_REST) * clamp((p.x - 60) / 280, 0.0, 1.0)
                        setActive(pick(p))
                        loop.wake()
                    }

                    override fun leave() {
                        lastPointer = null
                        expansion.t = EXPLODED_REST
                        setActive(-1)
                        loop.wake()
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun buildLayer(index: Int, layer: ExplodedLayer): ExplodedEls {
        val r = layer.rect
        val (ring, inner) = rings(r.x0, r.y0, r.x1, r.y1, layer.radius, 1.3)
        val ext = extremes(p, ring).toList()
        val guide = if (index > 0) g.path("nf dash") else null
        val solid = solid(g)
        val marks = solid.g.path("nf")
        val sr = layer.selection?.let { rrect(it.x0, it.y0, it.x1, it.y1, 2.0) }
        val se = layer.selection?.let { solid.g.path("nf sil") }
        val dots = layer.dots?.map { flatDot(solid.g, c, 1.6, "nf") }
        return ExplodedEls(ring, inner, ext, guide, solid, marks, sr, se, dots)
    }

    private fun draw(dt: Double): Boolean {
        val moving = stepS(expansion, dt)
        explodedLayers.forEachIndexed { i, layer -> drawLayer(i, layer) }
        val z = active * gap * expansion.x
        els.read.textContent =
            if (active >= 0) "0${active + 1} · ${explodedLayers[active].name} · z ${toFixed(z, 1)}"
            else "gap ${toFixed(gap * expansion.x, 1)}"
        return moving
    }

    private fun drawLayer(index: Int, layer: ExplodedLayer) {
        val e = layerEls[index]
        val z = index * gap * expansion.x
        val top = z + EXPLODED_THICKNESS
        put(e.solid, prism(p, front, e.ring, e.inner, z, top))
        e.marks.d = layer.segments.map { (a, b) -> seg(p(a.x, a.y, top), p(b.x, b.y, top)) }.join()
        if (e.selection != null && e.selectionRing != null)
            e.selection.d = poly(ringAt(p, e.selectionRing, top))
        e.dots?.forEachIndexed { k, dot -> place(dot, p(layer.dots!![k].x, layer.dots[k].y, top)) }
        if (e.guide != null) {
            val below = (index - 1) * gap * expansion.x + EXPLODED_THICKNESS
            e.guide.d = e.extremes.map { seg(p(it.u, it.v, z), p(it.u, it.v, below)) }.join()
        }
    }

    private fun corners(rect: ExplodedRect, z: Double) =
        listOf(
            p(rect.x0, rect.y0, z),
            p(rect.x1, rect.y0, z),
            p(rect.x1, rect.y1, z),
            p(rect.x0, rect.y1, z),
        )

    private fun pick(point: Vec2?): Int {
        if (point == null) return -1
        for (i in explodedLayers.indices.reversed()) if (
            insideExploded(
                point,
                corners(explodedLayers[i].rect, i * gap * expansion.t + EXPLODED_THICKNESS),
            )
        )
            return i
        return -1
    }

    private fun setActive(value: Int) {
        if (value == active) return
        active = value
        layerEls.forEachIndexed { i, e -> e.solid.sil.classList.toggle("hi", i == value) }
        loop.wake()
    }

    override fun set(value: Double) {
        gap = value
        if (lastPointer != null) setActive(pick(lastPointer))
        loop.wake()
    }

    override fun destroy() = bag.dispose()
}

private fun insideExploded(point: Vec2, polygon: List<Vec2>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if (
            (a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
        )
            inside = !inside
        j = i
    }
    return inside
}
