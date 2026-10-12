// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Tone

/*
 * The roles of a part, `t3Roles`: the elements it paints, in painting order. A path role is a fill
 * or a line, a face is a flat face with a crossfaded tone, a slot holds faded lines at one opacity,
 * `top` is an upright prism's top placed by a matrix, a plane is markup placed by a matrix, a dot is
 * a screw head, and `move` is a fixed picture's translation.
 */

/** What a role paints, the original's `cls` and `type`. */
enum class RoleKind {
    Fill,
    Shade,
    Top,
    Face,
    TopXform,
    Crease,
    Bevel,
    Inner,
    RibSlot,
    Plane,
    Edge,
    Dot,
    Body,
    Shine,
    TubeShade,
    Ring,
    TubeEdge,
    Move,
}

/** One role: its [name] (`fill`, `s2`, `f3`, `v0`, `plane1`, `c4`…), what it paints, its tone. */
class Role(
    val name: String,
    val kind: RoleKind,
    val tone: Tone? = null,
    val shade: Int = -1,
    val size: Double = 0.5,
)

/** True for the roles the runtime writes path data to. */
val Role.isPath: Boolean
    get() =
        when (kind) {
            RoleKind.TopXform,
            RoleKind.Plane,
            RoleKind.Dot,
            RoleKind.Move -> false
            else -> true
        }

private class RoleList(val part: PartData) {
    val roles = ArrayList<Role>()

    fun add(name: String, kind: RoleKind, tone: Tone? = null, shade: Int = -1, size: Double = 0.5) {
        roles.add(Role(name, kind, tone, shade, size))
    }

    fun slots(prefix: String, count: Int, kind: RoleKind, tone: Tone?) {
        for (j in 0 until count) add("$prefix$j", kind, tone)
    }

    fun planes() = part.planes.indices.forEach { add("plane$it", RoleKind.Plane) }

    fun dots() =
        part.dots.forEachIndexed { j, dot -> add("c$j", RoleKind.Dot, dot.tone, size = dot.size) }

    fun sheets(top: Boolean) {
        for (t in 0 until 4) add("s$t", RoleKind.Shade, shade = t)
        if (top) add("s4", RoleKind.Top)
    }

    fun inner() {
        if (
            !part.seams.isNullOrEmpty() || (part.ribs != null && part.ribs.seams != RibSeams.None)
        ) {
            add("inner", RoleKind.Inner, Tone.Faint)
        }
    }

    fun ribs() {
        val ribs = part.ribs ?: return
        slots("r", part.spec.slots?.ribs ?: 6, RoleKind.RibSlot, ribs.tone)
    }
}

/** The roles of [part], in painting order. */
internal fun t3Roles(part: PartData): List<Role> {
    val list = RoleList(part)
    when (val spec = part.spec) {
        is PrismSpec -> prismRoles(list, part, spec)
        is RoundSpec -> roundRoles(list, part, spec.ends)
        is LatheSpec ->
            roundRoles(
                list,
                part,
                listOf(spec.caps.first, spec.caps.second).map {
                    if (it) RoundEnd.Flat else RoundEnd.Open
                },
            )
        is BallSpec -> {
            list.add("fill", RoleKind.Fill)
            list.sheets(false)
            spec.flats.indices.forEach { list.add("f$it", RoleKind.Face) }
            part.crease?.let { list.add("crease", RoleKind.Crease, it) }
            list.planes()
            list.add("edge", RoleKind.Edge, part.tone)
            list.dots()
        }
        is TubeSpec -> {
            list.add("body", RoleKind.Body)
            if (spec.wide) {
                list.add("shine", RoleKind.Shine)
                list.add("shade", RoleKind.TubeShade)
            }
            if (spec.ringTone != null) {
                list.slots("r", spec.slots?.ribs ?: 6, RoleKind.Ring, spec.ringTone)
            }
            list.add("edges", RoleKind.TubeEdge, part.tone)
        }
        is FixedSpec -> {
            list.add("move", RoleKind.Move)
            list.ribs()
            list.planes()
            list.dots()
        }
    }
    return list.roles
}

private fun prismRoles(list: RoleList, part: PartData, spec: PrismSpec) {
    list.add("fill", RoleKind.Fill)
    val faces = spec.ring.size / 2 + if (spec.up) 0 else 2
    for (j in 0 until faces) list.add("f$j", RoleKind.Face)
    if (spec.up) list.add("top", RoleKind.TopXform)
    val crease = part.crease
    if (crease != null) list.add("crease", RoleKind.Crease, crease)
    if (spec.bevel != 0.0 && crease != null) {
        list.slots("v", spec.slots?.bevel ?: 6, RoleKind.Bevel, crease)
    }
    if (!part.seams.isNullOrEmpty()) list.add("inner", RoleKind.Inner, Tone.Faint)
    list.planes()
    list.add("edge", RoleKind.Edge, part.tone)
    list.dots()
}

private fun roundRoles(list: RoleList, part: PartData, ends: List<RoundEnd>) {
    list.add("fill", RoleKind.Fill)
    list.sheets(true)
    ends.forEachIndexed { j, end -> if (end == RoundEnd.Flat) list.add("k$j", RoleKind.Face) }
    part.crease?.let { list.add("crease", RoleKind.Crease, it) }
    list.inner()
    list.ribs()
    list.planes()
    list.add("edge", RoleKind.Edge, part.tone)
    list.dots()
}
