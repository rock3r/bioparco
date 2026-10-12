// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3

/*
 * The painter's order, `t3Order`. Every pair of parts that can overlap on screen has one plane: in
 * their shared group (planar), in their common ancestor's frame (framed), between two groups
 * (group pairs), or found by GJK each frame (dynamic, free). B is in front of A when the plane's
 * normal faces the viewer. The order is a stable Kahn sort keyed by the previous frame's order; on
 * a stall, edges between parts whose hulls are apart on screen are dropped, and a remaining stall
 * is a forced pick.
 */

/** What an order counted: edges, forced picks, dropped edges, interpenetrating pairs, GJK runs. */
class OrderStats {
    var edges = 0
    var forced = 0
    var refined = 0
    var unseparated = 0
    var solved = 0
}

/** A layer's reusable buffers for ordering. */
internal class OrderWork(val count: Int) {
    val rank = IntArray(count)
    val head = IntArray(count)
    val indeg = IntArray(count)
    val heap = IntArray(count)
    val done = BooleanArray(count)
    val mark = IntArray(count)
    val via = IntArray(count)
    val iter = IntArray(count)
    val stack = IntArray(count)
    var next = IntArray(16)
    var to = IntArray(16)
    var live = BooleanArray(16)
    var checked = BooleanArray(16)
    var edges = 0

    fun reserve(most: Int) {
        if (next.size < most) {
            next = IntArray(most)
            to = IntArray(most)
            live = BooleanArray(most)
            checked = BooleanArray(most)
        }
    }

    fun addEdge(back: Int, front: Int) {
        to[edges] = front
        next[edges] = head[back]
        live[edges] = true
        head[back] = edges
        indeg[front]++
        edges++
    }
}

/**
 * The parts of [layer] in painting order for this frame: [frames] are this frame's, [cams] its
 * cameras, [previous] the last order (or null for the layer's rest order). [stats] accumulates.
 */
fun turnOrder(
    layer: TurnLayer,
    frames: List<TurnFrame>,
    cams: TurnCams,
    previous: IntArray?,
    stats: OrderStats?,
    scene: TurnScene,
): IntArray {
    val count = layer.parts.size
    val work = layer.work
    val local = layer.local
    val src = previous ?: layer.rest
    for (at in 0 until count) work.rank[local.getValue(src[at])] = at
    var groupEdges = 0
    for (gp in layer.groupPairs) groupEdges += gp.a.size * gp.b.size
    work.reserve(
        layer.planar.size / 5 +
            layer.framed.size / 6 +
            groupEdges +
            layer.dynamic.size / 2 +
            layer.free.size * count +
            1
    )
    work.head.fill(-1)
    work.indeg.fill(0)
    work.edges = 0
    val edges = EdgeCollector(layer, frames, cams, scene)
    edges.planar()
    edges.framed()
    edges.groupPairs()
    edges.dynamic()
    edges.loose()
    val out = KahnSort(layer, frames, work).sort()
    if (stats != null) {
        stats.edges += work.edges
        stats.forced += out.second
        stats.refined += out.third
        stats.unseparated += edges.unseparated
        stats.solved += edges.solved
    }
    return out.first
}

/** Adds this frame's edges: each pair whose boxes overlap, by the side of its plane. */
private class EdgeCollector(
    val layer: TurnLayer,
    val frames: List<TurnFrame>,
    val cams: TurnCams,
    val scene: TurnScene,
) {
    val work = layer.work
    val local = layer.local
    val runs = scene.runs
    var unseparated = 0
    var solved = 0

    fun add(i: Int, j: Int, s: Double) {
        if (s < 1e-7 && s > -1e-7) return
        val li = local.getValue(i)
        val lj = local.getValue(j)
        if (s > 0) work.addEdge(li, lj) else work.addEdge(lj, li)
    }

    fun planar() {
        val planar = layer.planar
        for (p in planar.indices step 5) {
            val i = planar[p].toInt()
            val j = planar[p + 1].toInt()
            if (!t3Overlap(frames[i].box, frames[j].box, -0.05)) continue
            val v = cams.of(runs[i].group).v
            add(i, j, planar[p + 2] * v.x + planar[p + 3] * v.y + planar[p + 4] * v.z)
        }
    }

    fun framed() {
        val framed = layer.framed
        for (p in framed.indices step 6) {
            val i = framed[p].toInt()
            val j = framed[p + 1].toInt()
            if (!t3Overlap(frames[i].box, frames[j].box, -0.05)) continue
            val v = cams.of(framed[p + 2].toInt()).v
            add(i, j, framed[p + 3] * v.x + framed[p + 4] * v.y + framed[p + 5] * v.z)
        }
    }

    fun union(list: IntArray, box: DoubleArray) {
        box[0] = Double.POSITIVE_INFINITY
        box[1] = Double.POSITIVE_INFINITY
        box[2] = Double.NEGATIVE_INFINITY
        box[3] = Double.NEGATIVE_INFINITY
        for (q in list) {
            val b = frames[q].box
            if (b[0] < box[0]) box[0] = b[0]
            if (b[1] < box[1]) box[1] = b[1]
            if (b[2] > box[2]) box[2] = b[2]
            if (b[3] > box[3]) box[3] = b[3]
        }
    }

    fun groupPairs() {
        for (gp in layer.groupPairs) {
            union(gp.a, gp.boxA)
            union(gp.b, gp.boxB)
            if (!t3Overlap(gp.boxA, gp.boxB, -0.05)) continue
            val v = cams.of(gp.frame).v
            val s = gp.n.x * v.x + gp.n.y * v.y + gp.n.z * v.z
            if (s < 1e-7 && s > -1e-7) continue
            for (i in gp.a) {
                val bi = frames[i].box
                if (!t3Overlap(bi, gp.boxB, -0.05)) continue
                for (j in gp.b) {
                    if (!t3Overlap(bi, frames[j].box, -0.05)) continue
                    add(i, j, s)
                }
            }
        }
    }

    fun dynamic() {
        val dynamic = layer.dynamic
        for (p in dynamic.indices step 2) {
            val i = dynamic[p]
            val j = dynamic[p + 1]
            if (!t3Overlap(frames[i].box, frames[j].box, -0.05)) continue
            add(i, j, cross(i, j))
        }
    }

    fun loose() {
        val parts = layer.parts
        for (i in layer.free) {
            val gi = runs[i].group
            val bi = frames[i].box
            for (b in parts.indices) {
                val j = parts[b]
                if (j == i || runs[j].group == gi) continue
                if (layer.looseAt[b] && j < i) continue
                if (!t3Overlap(bi, frames[j].box, -0.05)) continue
                val s = cross(i, j)
                if (s < 1e-7 && s > -1e-7) continue
                val li = local.getValue(i)
                if (s > 0) work.addEdge(li, b) else work.addEdge(b, li)
            }
        }
    }

    /** The plane between two parts of different groups, cached while their relative pose holds. */
    fun cross(i: Int, j: Int): Double {
        val ri = runs[i]
        val rj = runs[j]
        val ci = cams.of(ri.group)
        val cj = cams.of(rj.group)
        val a = ci.r
        val b = cj.r
        val dx = cj.t.x - ci.t.x
        val dy = cj.t.y - ci.t.y
        val dz = cj.t.z - ci.t.z
        val memo = layer.rel.getOrPut(i * 65536 + j) { PairMemo() }
        val pose = memo.pose
        var same = memo.fresh
        var at = 0
        for (c in 0 until 3) {
            for (r in 0 until 3) {
                val v = a[c] * b[r] + a[3 + c] * b[3 + r] + a[6 + c] * b[6 + r]
                if (same && kotlin.math.abs(pose[at] - v) > 1e-12) same = false
                pose[at++] = v
            }
        }
        val d = doubleArrayOf(dx, dy, dz)
        for (c in 0 until 3) {
            val v = a[c] * d[0] + a[3 + c] * d[1] + a[6 + c] * d[2]
            if (same && kotlin.math.abs(pose[at] - v) > 1e-9) same = false
            pose[at++] = v
        }
        val n = memo.n
        val nx: Double
        val ny: Double
        val nz: Double
        if (same) {
            nx = a[0] * n[0] + a[1] * n[1] + a[2] * n[2]
            ny = a[3] * n[0] + a[4] * n[1] + a[5] * n[2]
            nz = a[6] * n[0] + a[7] * n[1] + a[8] * n[2]
        } else {
            val warm =
                if (memo.fresh) {
                    Point3(
                        a[0] * n[0] + a[1] * n[1] + a[2] * n[2],
                        a[3] * n[0] + a[4] * n[1] + a[5] * n[2],
                        a[6] * n[0] + a[7] * n[1] + a[8] * n[2],
                    )
                } else null
            val hit =
                turnGjk(ri.cloud, ci, rj.cloud, cj, warm, 8, hintsOf(ri, ci, rj, cj, cams.world))
            solved++
            nx = hit.n.x
            ny = hit.n.y
            nz = hit.n.z
            n[0] = a[0] * nx + a[3] * ny + a[6] * nz
            n[1] = a[1] * nx + a[4] * ny + a[7] * nz
            n[2] = a[2] * nx + a[5] * ny + a[8] * nz
            memo.margin = hit.margin
            memo.fresh = true
        }
        if (memo.margin < -0.08) unseparated++
        val v = cams.world.v
        return nx * v.x + ny * v.y + nz * v.z
    }
}

/** The stable Kahn sort with cycle breaking: returns the order, forced picks, dropped edges. */
private class KahnSort(val layer: TurnLayer, val frames: List<TurnFrame>, val w: OrderWork) {
    val count = layer.parts.size
    val parts = layer.parts
    var size = 0
    var forced = 0
    var refined = 0

    fun less(a: Int, b: Int) = w.rank[a] < w.rank[b]

    fun push(v: Int) {
        val heap = w.heap
        var i = size++
        heap[i] = v
        while (i > 0) {
            val p = (i - 1) shr 1
            if (!less(heap[i], heap[p])) break
            val tmp = heap[i]
            heap[i] = heap[p]
            heap[p] = tmp
            i = p
        }
    }

    fun pop(): Int {
        val heap = w.heap
        val top = heap[0]
        heap[0] = heap[--size]
        var i = 0
        while (true) {
            val l = 2 * i + 1
            val r = l + 1
            var s = i
            if (l < size && less(heap[l], heap[s])) s = l
            if (r < size && less(heap[r], heap[s])) s = r
            if (s == i) break
            val tmp = heap[i]
            heap[i] = heap[s]
            heap[s] = tmp
            i = s
        }
        return top
    }

    fun sort(): Triple<IntArray, Int, Int> {
        w.done.fill(false)
        for (v in 0 until count) if (w.indeg[v] == 0) push(v)
        val out = IntArray(count)
        var placed = 0
        w.checked.fill(false, 0, w.edges)
        while (placed < count) {
            if (size == 0) {
                var tries = 0
                while (tries < 64 && size == 0) {
                    if (breakCycle() <= 0) break
                    tries++
                }
                if (size != 0) continue
                forcePick()
            }
            val v = pop()
            if (w.done[v]) continue
            w.done[v] = true
            out[placed++] = parts[v]
            var e = w.head[v]
            while (e >= 0) {
                if (w.live[e]) {
                    val t = w.to[e]
                    if (!w.done[t] && --w.indeg[t] == 0) push(t)
                }
                e = w.next[e]
            }
        }
        return Triple(out, forced, refined)
    }

    fun forcePick() {
        var pick = -1
        for (v in 0 until count) {
            if (w.done[v]) continue
            if (
                pick < 0 ||
                    w.indeg[v] < w.indeg[pick] ||
                    (w.indeg[v] == w.indeg[pick] && w.rank[v] < w.rank[pick])
            ) {
                pick = v
            }
        }
        forced++
        w.indeg[pick] = 0
        push(pick)
    }

    /** Finds a cycle by DFS and drops its edges between parts apart on screen: 1, 0 or -1. */
    fun breakCycle(): Int {
        w.mark.fill(0)
        for (root in 0 until count) {
            if (w.done[root] || w.mark[root] != 0) continue
            var top = 0
            w.stack[top++] = root
            w.mark[root] = 1
            w.iter[root] = w.head[root]
            while (top > 0) {
                val v = w.stack[top - 1]
                var e = w.iter[v]
                while (e >= 0 && (!w.live[e] || w.done[w.to[e]] || w.mark[w.to[e]] == 2)) e =
                    w.next[e]
                if (e < 0) {
                    w.mark[v] = 2
                    top--
                    continue
                }
                w.iter[v] = w.next[e]
                val t = w.to[e]
                if (w.mark[t] == 1) return if (dropCycle(v, e, t, top)) 1 else 0
                w.via[t] = e
                w.mark[t] = 1
                w.iter[t] = w.head[t]
                w.stack[top++] = t
            }
        }
        return -1
    }

    /** Walks the cycle closed by edge [edge0] from [v0] back to [target], dropping apart edges. */
    fun dropCycle(v0: Int, edge0: Int, target: Int, top: Int): Boolean {
        var dropped = false
        var x = v0
        var edge = edge0
        while (true) {
            if (!w.checked[edge]) {
                w.checked[edge] = true
                val b = w.to[edge]
                if (turnDisjoint(frames[parts[x]], frames[parts[b]], 0.1)) {
                    w.live[edge] = false
                    w.indeg[b]--
                    refined++
                    dropped = true
                    if (w.indeg[b] == 0) push(b)
                }
            }
            if (x == target) break
            edge = w.via[x]
            var from = -1
            for (s in 0 until top) if (w.stack[s] == x) from = if (s > 0) w.stack[s - 1] else -1
            if (from < 0) break
            x = from
        }
        return dropped
    }
}
