package dev.sebastiano.thinkingorbs

import kotlin.math.max
import kotlin.math.sqrt

internal enum class OrbMode {
    Orbits,
    Globe,
    Rubik,
    Wave,
    Web,
    Braid,
    Ribbon,
    Ring,
    Morph,
}

private val OrbDesign.mode: OrbMode
    get() =
        when (this) {
            OrbDesign.Working -> OrbMode.Orbits
            OrbDesign.Searching -> OrbMode.Globe
            OrbDesign.Solving -> OrbMode.Rubik
            OrbDesign.Listening -> OrbMode.Wave
            OrbDesign.Connecting -> OrbMode.Web
            OrbDesign.Weaving -> OrbMode.Braid
            OrbDesign.Composing -> OrbMode.Ribbon
            OrbDesign.Breathing -> OrbMode.Ring
            OrbDesign.Shaping -> OrbMode.Morph
        }

internal data class OrbResolved(
    val mode: OrbMode,
    val speed: Double,
    val options: Map<String, Double>,
) {
    fun frame(size: Double, time: Double): OrbFrame =
        OrbEngine.frame(mode, size, if (time.isFinite()) time else 0.0, options)
}

internal object OrbPresets {
    private data class Preset(
        val speed: Double,
        val count: Double,
        val size: Double,
        val extra: Map<String, Double> = emptyMap(),
    )

    private fun preset(mode: OrbMode, size: OrbSize): Preset =
        when (size) {
            OrbSize.Regular -> regularPreset(mode)
            OrbSize.Small -> smallPreset(mode)
        }

    private fun regularPreset(mode: OrbMode): Preset =
        when (mode) {
            OrbMode.Orbits -> Preset(1.885, 1.0, 1.0)
            OrbMode.Globe -> Preset(2.015, 0.42, 1.15, mapOf("scanMul" to 4.08, "dimBase" to 0.45))
            OrbMode.Rubik -> Preset(1.82, 0.35, 1.05)
            OrbMode.Wave -> Preset(4.388, 0.341, 1.0)
            OrbMode.Web -> Preset(3.315, 1.35, 0.95)
            OrbMode.Braid -> Preset(1.625, 0.5, 1.0)
            OrbMode.Ribbon ->
                Preset(2.34, 0.25, 0.85, mapOf("spin" to 0.0, "bandMul" to 3.9, "wobMul" to 1.0))
            OrbMode.Ring ->
                Preset(
                    3.24,
                    0.25,
                    0.956,
                    mapOf("spin" to 0.0, "bandMul" to 3.627, "wobMul" to 0.368),
                )
            OrbMode.Morph -> Preset(2.405, 0.702, 0.395, mapOf("spread" to 1.45))
        }

    private fun smallPreset(mode: OrbMode): Preset =
        when (mode) {
            OrbMode.Orbits -> Preset(3.9, 0.238, 2.4)
            OrbMode.Globe ->
                Preset(2.665, 0.105, 1.75, mapOf("scanMul" to 4.335, "dimBase" to 0.45))
            OrbMode.Rubik -> Preset(1.95, 0.088, 1.9)
            OrbMode.Wave -> Preset(3.998, 0.105, 1.6)
            OrbMode.Web -> Preset(6.63, 0.25, 1.52)
            OrbMode.Braid -> Preset(2.75, 0.1125, 1.36)
            OrbMode.Ribbon ->
                Preset(3.12, 0.051, 1.073, mapOf("spin" to 0.0, "bandMul" to 4.94, "wobMul" to 1.0))
            OrbMode.Ring ->
                Preset(
                    3.78,
                    0.028,
                    1.622,
                    mapOf("spin" to 0.0, "bandMul" to 3.968, "wobMul" to 0.565),
                )
            OrbMode.Morph -> Preset(2.08, 0.53, 1.011, mapOf("spread" to 1.45))
        }

    private fun base(mode: OrbMode): Map<String, Double> =
        when (mode) {
            OrbMode.Globe ->
                opts(
                    "latRings" to 17.0,
                    "lonDensity" to 44.0,
                    "rBase" to 0.6,
                    "rDepth" to 1.7,
                    "rBoost" to 1.0,
                    "inkFar" to 0.62,
                    "inkSpan" to 0.54,
                    "rsPow" to 0.6,
                    "rMin" to 0.3,
                )
            OrbMode.Orbits ->
                opts(
                    "orbitN" to 12.0,
                    "ghostN" to 40.0,
                    "ghostR" to 0.9,
                    "ghostA" to 0.5,
                    "particles" to 3.0,
                    "partR" to 1.2,
                    "partRDepth" to 1.6,
                    "rsPow" to 0.6,
                    "rMin" to 0.3,
                )
            OrbMode.Rubik ->
                opts(
                    "latRings" to 15.0,
                    "lonDensity" to 40.0,
                    "moveCount" to 14.0,
                    "rBase" to 0.6,
                    "rDepth" to 1.7,
                    "rActive" to 0.3,
                    "inkFar" to 0.62,
                    "inkSpan" to 0.54,
                    "rsPow" to 0.6,
                    "rMin" to 0.3,
                )
            OrbMode.Wave ->
                opts(
                    "rings" to 15.0,
                    "lonDensity" to 40.0,
                    "rBase" to 0.6,
                    "rDepth" to 1.7,
                    "rsPow" to 0.6,
                    "rMin" to 0.3,
                )
            OrbMode.Web ->
                opts(
                    "nodeN" to 30.0,
                    "thr" to 0.72,
                    "signals" to 5.0,
                    "nodeR" to 1.4,
                    "nodeRDepth" to 1.8,
                    "lineW" to 0.8,
                    "rsPow" to 0.6,
                    "rMin" to 0.3,
                )
            OrbMode.Braid ->
                opts(
                    "strandN" to 52.0,
                    "turns" to 3.0,
                    "ghostN" to 150.0,
                    "rBase" to 1.2,
                    "rDepth" to 1.8,
                    "rsPow" to 0.6,
                    "rMin" to 0.3,
                )
            OrbMode.Ribbon -> ribbonBase(ghostCount = 150.0)
            OrbMode.Ring -> ribbonBase(ghostCount = 0.0) + ("faceOn" to 1.0)
            OrbMode.Morph -> opts("rDot" to 0.021, "iconD" to 1.0, "rMin" to 0.25)
        }

    private fun ribbonBase(ghostCount: Double) =
        opts(
            "lanes" to 5.0,
            "segs" to 88.0,
            "ghostN" to ghostCount,
            "rBase" to 1.1,
            "rDepth" to 1.7,
            "rsPow" to 0.6,
            "rMin" to 0.3,
        )

    private fun opts(vararg pairs: Pair<String, Double>) = mapOf(*pairs)

    private val countPairs =
        listOf("latRings" to "lonDensity", "rings" to "lonDensity", "lanes" to "segs")
    private val countKeys = listOf("orbitN", "ghostN", "nodeN", "strandN", "signals")
    private val radiusKeys =
        listOf(
            "rBase",
            "rDepth",
            "rActive",
            "rDot",
            "ghostR",
            "partR",
            "partRDepth",
            "nodeR",
            "nodeRDepth",
        )

    private fun scaleCounts(options: Map<String, Double>, scale: Double): Map<String, Double> {
        val out = options.toMutableMap()
        val done = mutableSetOf<String>()
        val root = sqrt(scale)
        for ((first, second) in countPairs) {
            val a = out[first]
            val b = out[second]
            if (a != null && b != null && first !in done && second !in done) {
                out[first] = max(2.0, OrbEngine.jsRound(a * root))
                out[second] = max(2.0, OrbEngine.jsRound(b * root))
                done += first
                done += second
            }
        }
        for (key in countKeys) {
            val value = out[key]
            if (value != null && value != 0.0 && key !in done) {
                out[key] = max(1.0, OrbEngine.jsRound(value * scale))
            }
        }
        out["iconD"]?.let { out["iconD"] = max(0.02, it * scale) }
        return out
    }

    private fun scaleRadii(options: Map<String, Double>, scale: Double): Map<String, Double> {
        val out = options.toMutableMap()
        radiusKeys.forEach { key -> out[key]?.let { out[key] = it * scale } }
        out["rSizeMul"] = (out["rSizeMul"] ?: 1.0) * scale
        return out
    }

    private val cache =
        OrbDesign.entries.associateWith { design ->
            OrbSize.entries.associateWith { size -> compute(design, size) }
        }

    fun resolve(design: OrbDesign, size: OrbSize): OrbResolved =
        cache.getValue(design).getValue(size)

    private fun compute(design: OrbDesign, size: OrbSize): OrbResolved {
        val mode = design.mode
        val preset = preset(mode, size)
        var options = base(mode)
        if (preset.count != 1.0) options = scaleCounts(options, preset.count)
        if (preset.size != 1.0) options = scaleRadii(options, preset.size)
        return OrbResolved(mode, preset.speed, options + preset.extra)
    }
}
