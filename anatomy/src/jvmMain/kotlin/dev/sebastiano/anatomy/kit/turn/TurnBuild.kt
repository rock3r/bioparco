// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.viewOf

/*
 * `T.build`: sizes every part's faded-line slots over its reachable states, proves every pair
 * (TurnPairs.kt) and every static item's layer (TurnLayers.kt), packs the runtime's data, and
 * solves each layer's painting order at rest. It throws a readable error on any pair with no
 * separating plane (unless not [strict]), and on any static that can't be proved.
 */

/** One live layer's counts in the report. */
class LayerReport(
    val name: String,
    val parts: Int,
    val planar: Int,
    val framed: Int,
    val groupPairs: Int,
    val dynamic: Int,
    val skip: Int,
)

/** What the build proved and how long it took. */
class TurnReport(
    val parts: Int,
    val groups: Int,
    val layers: List<LayerReport>,
    val tight: List<PairMargin>,
    val margins: List<PairMargin>,
    val proofs: List<LayerProof>,
    val seconds: Double,
)

/** What `T.build` returns: the runtime's [data], the report, and the rest frames. */
class TurnBuild(
    val data: TurnData,
    val report: TurnReport,
    val scene: TurnScene,
    val frames: List<TurnFrame>,
    /** Each same-group pair's plane in world coordinates, by `"a|b"`. */
    val planesByName: Map<String, Plane>,
)

/** A run of [part] in [data], `t3Prepare`'s per-part step. */
internal fun runOf(index: Int, part: PartData, data: TurnData, unit: Double): TurnRun {
    val p = data.projection
    val run: TurnRun =
        when (val spec = part.spec) {
            is PrismSpec -> PrismRun(index, part, unit, spec)
            is RoundSpec -> RoundRun(index, part, unit, spec, p)
            is BallSpec -> BallRun(index, part, unit, spec, p)
            is LatheSpec -> LatheRun(index, part, unit, spec, p)
            is TubeSpec -> TubeRun(index, part, unit, spec, data.routes[spec.route])
            is FixedSpec -> FixedRun(index, part, unit, spec)
        }
    return run.also { it.finishPrepare() }
}

/** How many faded-line slots each part needs over the states its group reaches. */
private fun slotsOf(t: Turning, scratch: TurnData, solver: PairSolver): List<Slots?> =
    t.parts.mapIndexed { index, part ->
        val data = scratch.parts[index]
        val needs =
            when (part.kind) {
                PartKind.Prism -> (part.spec as PrismSpec).bevel != 0.0
                PartKind.Tube -> (part.spec as TubeSpec).ringTone != null
                else -> part.ribs != null
            }
        if (!needs) return@mapIndexed null
        val wide =
            PartData(
                data.name,
                data.kind,
                data.group,
                data.layer,
                data.tone,
                data.crease,
                data.lit,
                data.material,
                data.spec.withSlots(Slots(64, 64)),
                data.seams,
                data.ribs,
                data.planes,
                data.dots,
                data.cloud,
                data.hue,
                data.art,
            )
        val run = runOf(index, wide, scratch, t.scale)
        val f = TurnFrame(run.roles.size)
        var most = 0
        for (cams in solver.camsFor(part.group)) {
            run.emit(cams.of(part.group?.index ?: -1), f)
            most = maxOf(most, f.levels)
        }
        val need = maxOf(1, most)
        if (part.kind == PartKind.Prism) Slots(need, 0) else Slots(0, need)
    }

internal fun buildTurning(
    t: Turning,
    statics: List<StaticItem>,
    poses: List<TurnValues>,
    strict: Boolean,
): TurnBuild {
    val started = System.nanoTime()
    val p = t.projection
    val states = statesOf(t, poses)
    val groups = groupData(t)
    val routes = routeData(t)
    val emptyLayers = t.layers.map { LayerData(it.name, IntArray(0), IntArray(0)) }
    var parts = t.parts.map(::partData)
    var scratch = TurnData(p, groups, emptyLayers, parts, routes)
    var scene = prepareTurn(scratch, t.scale)
    val solver = PairSolver(t, { scene }, states, strict)
    val slots = slotsOf(t, scratch, solver)
    parts = parts.mapIndexed { i, data ->
        slots[i]?.let { withSpec(data, data.spec.withSlots(it)) } ?: data
    }
    scratch = TurnData(p, groups, emptyLayers, parts, routes)
    scene = prepareTurn(scratch, t.scale)
    solver.spheresOf()
    for (layer in t.layers) solver.layer(layer)
    val found = solver.out
    if (found.problems.isNotEmpty() && strict)
        fail("pair", "with no separating plane", found.problems)
    val (layerProblems, proofs) = t.proveLayers(statics, states, scene)
    if (layerProblems.isNotEmpty()) fail("layer problem", "", layerProblems)
    val packed = packData(t, groups, parts, routes, found)
    val packedScene = prepareTurn(packed, t.scale)
    val rest = TurnValues(groups.filter { it.kind != GroupKind.Free }.associate { it.name to 0.0 })
    val cams = packedScene.cams(rest)
    val frames =
        packedScene.runs.map { run ->
            TurnFrame(run.roles.size).also { run.emit(cams.of(run.group), it) }
        }
    val restLayers = restLayersOf(packedScene, frames, cams, solver, viewOf(p))
    val data =
        TurnData(
            packed.projection,
            packed.groups,
            restLayers,
            packed.parts,
            packed.routes,
            packed.planes,
            packed.framed,
            packed.groupPairs,
            packed.dynamic,
            packed.free,
            packed.skip,
        )
    val finalScene =
        TurnScene(
            packedScene.projection,
            data,
            packedScene.unit,
            packedScene.groups,
            packedScene.runs,
            packedScene.layers,
        )
    val report = reportOf(t, found, proofs, started)
    return TurnBuild(data, report, finalScene, frames, found.planesByName)
}

private fun routeData(t: Turning) =
    t.routes.map { route ->
        RouteData(
            route.points.flatMap { listOf(it.x, it.y, it.z) }.map(::r4).toDoubleArray(),
            route.r,
            route.spacing,
            route.wide,
            route.hue,
            route.rings,
        )
    }

/** Throws the build's error: [count] problems of [what] [after], one a line. */
private fun fail(what: String, after: String, problems: List<String>): Nothing {
    val count = problems.size
    val head =
        "turning: $count $what${if (count == 1) "" else "s"}${if (after.isEmpty()) "" else " $after"}"
    error("$head\n  ${problems.joinToString("\n  ")}")
}

/** Each layer's rest order: by sphere depth along [v], then the runtime's sort. */
private fun restLayersOf(
    scene: TurnScene,
    frames: List<TurnFrame>,
    cams: TurnCams,
    solver: PairSolver,
    v: Point3,
) =
    scene.layers.map { layer ->
        val provisional =
            layer.parts
                .sortedWith { i, j ->
                    (dot3(solver.spheres[i].c, v) - dot3(solver.spheres[j].c, v)).let {
                        if (it < 0) -1 else if (it > 0) 1 else 0
                    }
                }
                .toIntArray()
        val order = turnOrder(layer, frames, cams, provisional, OrderStats(), scene)
        layer.rest = order
        LayerData(layer.name, layer.parts, order)
    }

private fun reportOf(t: Turning, found: PairResults, proofs: List<LayerProof>, started: Long) =
    TurnReport(
        t.parts.size,
        t.groups.size,
        t.layers.map { layer ->
            LayerReport(
                layer.name,
                found.layers[layer.index].parts.size,
                found.planes.getValue(layer.name).size / 5,
                found.framed.getValue(layer.name).size / 6,
                found.groupPairs.getValue(layer.name).size / 6,
                found.dynamic.getValue(layer.name).size / 2,
                found.skip.getValue(layer.name).size / 2,
            )
        },
        found.margins
            .filter { it.margin < 0 }
            .sortedWith { a, b ->
                (a.margin - b.margin).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
            },
        found.margins.toList(),
        proofs,
        (System.nanoTime() - started) / 1e9,
    )

private fun withSpec(d: PartData, spec: PartSpec) =
    PartData(
        d.name,
        d.kind,
        d.group,
        d.layer,
        d.tone,
        d.crease,
        d.lit,
        d.material,
        spec,
        d.seams,
        d.ribs,
        d.planes,
        d.dots,
        d.cloud,
        d.hue,
        d.art,
    )

/** The runtime's data, packed as the original's JSON round trip packs it. */
private fun packData(
    t: Turning,
    groups: List<GroupData>,
    parts: List<PartData>,
    routes: List<RouteData>,
    found: PairResults,
): TurnData {
    val clouds = t.parts.map { part -> thinCloud(part.samples, 64) }
    val packedParts = parts.mapIndexed { index, data ->
        val cloud =
            if (data.kind == PartKind.Fixed)
                clouds[index].flatMap { listOf(it.x, it.y, it.z) }.toDoubleArray()
            else null
        packPart(data, cloud)
    }
    return TurnData(
        packProjection(t.projection),
        groups.map(::packGroup),
        found.layers.toList(),
        packedParts,
        routes.map(::packRoute),
        found.planes.mapValues { packPairs(it.value, 5, false) },
        found.framed.mapValues { packPairs(it.value, 6, true) },
        found.groupPairs.mapValues { (_, list) -> list.map(::r4).toDoubleArray() },
        found.dynamic,
        found.free,
        found.skip,
    )
}
