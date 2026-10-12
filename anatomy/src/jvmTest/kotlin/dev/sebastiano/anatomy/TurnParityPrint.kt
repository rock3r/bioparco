package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.ParityLines.Companion.fixed
import dev.sebastiano.anatomy.ParityLines.Companion.num
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.jsString
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.turn.BallSpec
import dev.sebastiano.anatomy.kit.turn.Cut
import dev.sebastiano.anatomy.kit.turn.FixedSpec
import dev.sebastiano.anatomy.kit.turn.GroupKind
import dev.sebastiano.anatomy.kit.turn.LatheSpec
import dev.sebastiano.anatomy.kit.turn.OrderStats
import dev.sebastiano.anatomy.kit.turn.PartData
import dev.sebastiano.anatomy.kit.turn.PrismSpec
import dev.sebastiano.anatomy.kit.turn.RibSeams
import dev.sebastiano.anatomy.kit.turn.RoleKind
import dev.sebastiano.anatomy.kit.turn.RoundSpec
import dev.sebastiano.anatomy.kit.turn.Slots
import dev.sebastiano.anatomy.kit.turn.TubeSpec
import dev.sebastiano.anatomy.kit.turn.TurnBuild
import dev.sebastiano.anatomy.kit.turn.TurnData
import dev.sebastiano.anatomy.kit.turn.TurnFrame
import dev.sebastiano.anatomy.kit.turn.TurnRun
import dev.sebastiano.anatomy.kit.turn.TurnScene
import dev.sebastiano.anatomy.kit.turn.TurnState
import dev.sebastiano.anatomy.kit.turn.TurnValues
import dev.sebastiano.anatomy.kit.turn.coverOf
import dev.sebastiano.anatomy.kit.turn.poseUniformsOf
import dev.sebastiano.anatomy.kit.turn.prepareTurn
import dev.sebastiano.anatomy.kit.turn.turnAnchor
import dev.sebastiano.anatomy.kit.turn.turnOrder
import dev.sebastiano.anatomy.kit.turn.turnSvg

/*
 * The lines `parity/turn.mjs` writes, from the Kotlin side: the build's report and packed data, the
 * runtime's frames and orders over a list of states, and covers, anchors and pose uniforms.
 */

private fun f4(v: Double) = fixed(v, 4)

private fun list(values: DoubleArray, format: (Double) -> String = ::num) =
    values.joinToString(" ") { format(it) }

private fun vec(v: Point3?) = if (v == null) "-" else "${num(v.x)} ${num(v.y)} ${num(v.z)}"

private fun pair(v: Pair<Double, Double>?) =
    if (v == null) "-" else "${num(v.first)} ${num(v.second)}"

private fun frameText(f: Frame?) =
    if (f == null) "-" else "${vec(f.o)} ${vec(f.a)} ${vec(f.u)} ${vec(f.v)}"

private fun tone(t: Tone?) = t?.name?.lowercase() ?: "none"

private fun cuts(list: List<Cut>) = list.joinToString(",") { "${vec(it.n)}/${num(it.d)}" }

private fun slots(s: Slots?) = if (s == null) "-" else "${s.bevel} ${s.ribs}"

internal fun ParityLines.report(name: String, built: TurnBuild) {
    val r = built.report
    put("$name.report", "${r.parts} ${r.groups}")
    for (l in r.layers) put(
        "$name.report.${l.name}",
        "${l.parts} ${l.planar} ${l.framed} ${l.groupPairs} ${l.dynamic} ${l.skip}",
    )
    put("$name.margins", r.margins.joinToString(" ") { "${it.a}|${it.b}|${fixed(it.margin, 6)}" })
    put("$name.tight", r.tight.joinToString(" ") { "${it.a}|${it.b}" })
    put(
        "$name.proofs",
        r.proofs.joinToString(" ") { "${it.item}|${it.layer}|${it.group}|${it.rule}|${it.side}" },
    )
    for ((key, plane) in built.planesByName) put(
        "$name.plane.$key",
        "${vec(plane.n)} ${fixed(plane.d, 6)}",
    )
}

private fun ParityLines.spec(at: String, p: PartData) {
    when (val s = p.spec) {
        is PrismSpec -> {
            val place =
                if (s.up) "${num(s.z0)} ${num(s.z1)}"
                else "${frameText(s.frame)} ${num(s.s0)} ${num(s.s1)}"
            put(
                "$at.spec",
                "${if (s.up) 1 else 0} ${list(s.ring)} $place ${num(s.bevel)} ${cuts(s.cuts)} ${slots(s.slots)}",
            )
        }
        is RoundSpec ->
            put(
                "$at.spec",
                "${frameText(s.frame)} ${num(s.s0)} ${num(s.s1)} ${num(s.r0)} ${num(s.r1)} " +
                    "${s.ends.joinToString(",") { it.name.lowercase() }} ${cuts(s.cuts)} ${slots(s.slots)}",
            )
        is BallSpec ->
            put(
                "$at.spec",
                "${vec(s.c)} ${num(s.r)} ${s.flats.joinToString(",") { "${vec(it.n)}/${num(it.d)}" }}",
            )
        is LatheSpec ->
            put(
                "$at.spec",
                "${frameText(s.frame)} ${list(s.levels)} ${s.caps.first},${s.caps.second} ${slots(s.slots)}",
            )
        is TubeSpec ->
            put(
                "$at.spec",
                "${s.route} ${num(s.from)} ${num(s.to)} ${s.open0} ${s.open1} ${s.gap0} ${s.gap1} " +
                    "${s.caps.first},${s.caps.second} ${s.wide} " +
                    "${s.ringTone?.let { tone(it) } ?: "-"} ${slots(s.slots)}",
            )
        is FixedSpec ->
            put(
                "$at.spec",
                "${vec(s.anchor)} ${num(s.home.x)} ${num(s.home.y)} ${list(s.box)} ${list(s.hull)} " +
                    "${frameText(s.frame)} ${slots(s.slots)}",
            )
    }
}

internal fun ParityLines.data(name: String, d: TurnData) {
    val p = d.projection
    put(
        "$name.P",
        "${num(p.origin.x)} ${num(p.origin.y)} ${num(p.scale)} ${num(p.azimuth)} ${num(p.elevation)}",
    )
    for (g in d.groups) {
        val pivot = g.pivot?.let { "${num(it.x)} ${num(it.y)}" } ?: "-"
        put(
            "$name.group.${g.name}",
            "${g.parent} ${g.kind.name.lowercase()} ${vec(g.origin)} $pivot ${vec(g.direction)} " +
                "${g.travel?.let { jsString(it) } ?: "-"} ${vec(g.point)} ${vec(g.axis)} " +
                "${pair(g.range)} ${g.upright} ${g.loose}",
        )
    }
    for (l in d.layers) put(
        "$name.layer.${l.name}",
        "${l.parts.joinToString(",")} rest ${l.rest.joinToString(",")}",
    )
    d.parts.forEachIndexed { index, p0 -> part("$name.part.$index", p0) }
    d.routes.forEachIndexed { index, r ->
        put(
            "$name.route.$index",
            "${list(r.points)} ${num(r.r)} ${num(r.spacing)} ${r.wide} ${r.hue?.let { tone(it) } ?: "-"}",
        )
    }
    sceneData(name, d)
}

private fun ParityLines.part(at: String, p0: PartData) {
    put(
        at,
        "${p0.name} ${p0.kind.name.lowercase()} ${p0.group} ${p0.layer} ${tone(p0.tone)} " +
            "${tone(p0.crease)} ${p0.lit} ${p0.material?.id ?: "-"}",
    )
    spec(at, p0)
    p0.seams?.let { put("$at.seams", list(it.toDoubleArray())) }
    p0.ribs?.let { r ->
        val seams =
            when (r.seams) {
                RibSeams.None -> "false"
                RibSeams.Both -> "true"
                RibSeams.First -> "first"
                RibSeams.Last -> "last"
            }
        put(
            "$at.ribs",
            "${num(r.s0)} ${num(r.s1)} ${r.count} ${num(r.phase)} ${num(r.twist)} " +
                "${pair(r.fade)} $seams ${tone(r.tone)} " +
                "${r.r0?.let { num(it) } ?: "-"} ${r.r1?.let { num(it) } ?: "-"}",
        )
    }
    p0.planes.forEachIndexed { j, pl ->
        put(
            "$at.plane$j",
            "${vec(pl.o)} ${vec(pl.u)} ${vec(pl.v)} ${vec(pl.n)} ${pair(pl.fade)} ${pl.billboard}",
        )
    }
    p0.dots.forEachIndexed { j, dot ->
        put(
            "$at.dot$j",
            "${vec(dot.p)} ${vec(dot.n)} ${num(dot.size)} ${tone(dot.tone)} ${pair(dot.fade)}",
        )
    }
    p0.cloud?.let { put("$at.cloud", list(it)) }
}

private fun ParityLines.sceneData(name: String, d: TurnData) {
    val scene = prepareTurn(d, 1.0)
    for (l in scene.layers) {
        put("$name.planar.${l.name}", list(l.planar))
        put("$name.framed.${l.name}", list(l.framed))
        put(
            "$name.groupPairs.${l.name}",
            l.groupPairs.joinToString(" ") {
                "${it.a.joinToString(",")}/${it.b.joinToString(",")}/${it.frame}/${vec(it.n)}"
            },
        )
        put("$name.dynamic.${l.name}", l.dynamic.joinToString(","))
        put("$name.free.${l.name}", l.free.joinToString(","))
    }
    for (run in scene.runs) {
        put(
            "$name.run.${run.index}",
            "${run.cloud.size / 3} ${list(run.cloud.copyOfRange(0, minOf(12, run.cloud.size)))} | " +
                "${run.axes.joinToString(",") { vec(it) }} | ${vec(run.centroid)}",
        )
    }
}

private fun ParityLines.frame(at: String, run: TurnRun, f: TurnFrame) {
    run.roles.forEachIndexed { r, role ->
        val key = "$at.${role.name}"
        when (role.kind) {
            RoleKind.Face -> {
                val d = f.d[r].turnSvg()
                put(key, d)
                if (d.isNotEmpty()) put("$key.q", "${f.q[r]} ${jsString(f.w[r])}")
            }
            RoleKind.Bevel,
            RoleKind.RibSlot,
            RoleKind.Ring -> {
                put(key, f.d[r].turnSvg())
                put("$key.o", jsString(jsRound(f.o[r] * 1000) / 1000))
            }
            RoleKind.TopXform,
            RoleKind.Move -> put(key, f.t[r]?.svg() ?: "")
            RoleKind.Plane -> put(key, "${f.t[r]?.svg() ?: ""} ${jsString(f.o[r])}")
            RoleKind.Dot ->
                put(
                    key,
                    "${jsString(jsRound(f.cx[r] * 100) / 100)} " +
                        "${jsString(jsRound(f.cy[r] * 100) / 100)} ${jsString(f.o[r])}",
                )
            else -> put(key, f.d[r].turnSvg())
        }
    }
    put("$at.box", list(f.box, ::f4))
    val quads = f.quadList?.takeIf { f.quadN > 0 }
    put(
        "$at.hull",
        quads?.take(f.quadN)?.joinToString(" | ") { list(it.buf.copyOfRange(0, 2 * it.n), ::f4) }
            ?: list(f.hull.copyOfRange(0, 2 * f.hullN), ::f4),
    )
    put(
        "$at.levels",
        "${f.levels} ${f.fade.entries.joinToString(" ") { (p, s) -> "$p:${s.keys.joinToString(",")}" }}",
    )
}

/** The result of [drive]: the scene, its frames and the last orders. */
internal class Driven(val scene: TurnScene, val frames: List<TurnFrame>, val orders: List<IntArray>)

internal fun ParityLines.drive(
    name: String,
    d: TurnData,
    states: List<TurnValues>,
    full: (Int) -> Boolean,
    exactAt: Int,
): Driven {
    val scene = prepareTurn(d, 1.0)
    val frames = scene.frames()
    var orders = scene.layers.map { it.rest.copyOf() }
    states.forEachIndexed { i, values ->
        TurnState.exact = i == exactAt
        val cams = scene.cams(values)
        for (run in scene.runs) run.emit(cams.of(run.group), frames[run.index])
        val stats = OrderStats()
        orders =
            scene.layers.mapIndexed { index, layer ->
                turnOrder(layer, frames, cams, orders[index], stats, scene)
            }
        val at = "$name.state.$i"
        scene.layers.forEachIndexed { index, layer ->
            put("$at.order.${layer.name}", orders[index].joinToString(","))
        }
        put(
            "$at.stats",
            "${stats.edges} ${stats.forced} ${stats.refined} ${stats.unseparated} ${stats.solved}",
        )
        if (full(i)) for (run in scene.runs) frame("$at.${run.name}", run, frames[run.index])
        else for (run in scene.runs) put("$at.${run.name}.box", list(frames[run.index].box, ::f4))
        TurnState.exact = false
    }
    return Driven(scene, frames, orders)
}

internal fun ParityLines.extras(
    name: String,
    driven: Driven,
    values: TurnValues,
    covers: List<List<List<String>>>,
    anchors: List<Triple<String?, Point3, Point3?>>,
    poses: List<String>,
) {
    val scene = driven.scene
    val cams = scene.cams(values)
    val orderIndex = { layer: String ->
        driven.orders[scene.layers.indexOfFirst { it.name == layer }]
    }
    covers.forEachIndexed { j, sets ->
        val c = coverOf(scene, driven.frames, orderIndex, sets)
        put(
            "$name.cover.$j",
            "${c.parts.joinToString(",")} ${c.dropped} ${c.count.joinToString(",") { it.toInt().toString() }} " +
                c.edge.joinToString(" ") { fixed(it.toDouble(), 5) },
        )
    }
    anchors.forEachIndexed { j, (group, point, direction) ->
        val a = turnAnchor(scene, cams, group, point, direction)
        put(
            "$name.anchor.$j",
            "${vec(a.world)} ${num(a.screen.x)} ${num(a.screen.y)} ${vec(a.direction)}",
        )
    }
    for (group in poses) {
        val u = poseUniformsOf(scene, cams, group)
        fun v(l: List<Double>) = l.joinToString(" ") { num(it) }
        put("$name.uniforms.$group", "${v(u.x)} ${v(u.y)} ${v(u.z)} ${v(u.t)}")
    }
}

internal val GroupKind.id: String
    get() = name.lowercase()
