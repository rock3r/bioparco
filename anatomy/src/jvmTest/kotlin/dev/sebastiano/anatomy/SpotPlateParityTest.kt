// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.SpotBase
import dev.sebastiano.anatomy.figures.SpotBottle
import dev.sebastiano.anatomy.figures.SpotDeck
import dev.sebastiano.anatomy.figures.SpotDrops
import dev.sebastiano.anatomy.figures.SpotFan
import dev.sebastiano.anatomy.figures.SpotModel
import dev.sebastiano.anatomy.figures.SpotRig
import dev.sebastiano.anatomy.figures.SpotTray
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.SolidPaths
import java.math.BigDecimal
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every output path, topology, shade, glint and word is compared with original TS run by Node. */
class SpotPlateParityTest {
    @Test
    fun staticArtFanAndGlassMatchOriginal() {
        val actual = LinkedHashMap<String, String>()
        actual.put("base", SpotBase)
        actual.put("tray", SpotTray)
        actual.put("deck", SpotDeck)
        actual.put("bottle", SpotBottle)
        for (merge in listOf(0.0, 3.75, 4.0, 4.25, 10.0, 16.25, 20.0, 24.0, 32.25, 44.0, 48.0)) {
            val fan = SpotFan.fanOf(merge)
            actual.put(
                "fan.${label(merge)}",
                mapOf(
                    "halo" to fan.halo,
                    "out" to fan.out,
                    "blades" to
                        fan.blades.map { blade ->
                            mapOf(
                                "fill" to blade.paths.fill,
                                "shades" to blade.paths.shades,
                                "top" to blade.paths.top,
                                "crease" to blade.paths.crease,
                                "outline" to blade.paths.outline,
                                "line" to blade.line,
                            )
                        },
                ),
            )
        }
        val poses =
            ((0..24).map { it * 2.0 } +
                    SpotRig.GAPS.flatMap { gap ->
                        listOf(-0.375, -0.25, -0.125, -0.124, 0.0, 0.124, 0.125, 0.25, 0.375, 0.5)
                            .map { gap + it }
                    } +
                    SpotRig.GAPS.flatMap { listOf(it / 2 - 0.125, it / 2, it / 2 + 0.125) })
                .distinct()
                .sorted()
        for (merge in poses) {
            actual.put("key.${label(merge)}", SpotDrops.keyOf(merge))
            actual.put("rows.${label(merge)}", SpotDrops.rowsOf(merge))
        }
        modelCases(actual)
        val expected =
            javaClass
                .getResource("/goldens/spot-plate.txt")!!
                .readText()
                .lineSequence()
                .filter { it.isNotEmpty() }
                .associate { line ->
                    val split = line.indexOf('\t')
                    line.substring(0, split) to line.substring(split + 1)
                }
        assertEquals(emptySet(), expected.keys - actual.keys, "missing fields")
        assertEquals(emptySet(), actual.keys - expected.keys, "extra fields")
        for ((key, want) in expected) {
            val got = actual.getValue(key)
            val number = want.toDoubleOrNull()
            if (number != null) assertEquals(number, got.toDouble(), key)
            else assertEquals(want, got, key)
        }
    }

    private fun modelCases(actual: MutableMap<String, String>) {
        for (merge in listOf(0.0, 4.0, 4.5, 10.0, 16.0, 20.0, 24.0, 32.0, 44.0, 44.5, 48.0)) {
            actual.put(
                "model.${label(merge)}",
                mapOf(
                    "readout" to SpotModel.readoutOf(merge),
                    "spoken" to SpotModel.spokenOf(merge),
                    "joinedList" to SpotModel.joinedListOf(merge),
                    "bladesOut" to SpotModel.bladesOutOf(merge),
                ),
            )
            for (gap in SpotRig.GAPS) {
                val field = SpotModel.fieldOf(gap, merge)
                actual.put(
                    "field.${label(merge)}.${label(gap)}",
                    mapOf(
                        "neck" to SpotModel.neckOf(gap, merge),
                        "seenGap" to SpotModel.seenGapOf(gap, merge),
                        "samples" to
                            listOf(
                                    0.0 to 0.0,
                                    17.25 to -6.75,
                                    (SpotRig.RA + gap / 2) to 0.0,
                                    (SpotRig.RA + gap / 2) to 0.75,
                                    SpotModel.spanOf(gap) to 0.0,
                                    (SpotModel.spanOf(gap) + 10) to 15.0,
                                )
                                .map { (u, v) -> field(u, v) },
                    ),
                )
            }
        }
    }

    private fun label(number: Double) =
        if (number == number.toInt().toDouble()) number.toInt().toString() else number.toString()

    private fun MutableMap<String, String>.put(key: String, value: Any) {
        when (value) {
            is IsoPath -> this[key] = value.svg()
            is Flat -> put(key, listOf(value.x, value.y))
            is FaceMatrix -> {
                // The original stores a rounded SVG matrix; compare its exact printed digits.
                this[key] =
                    listOf(
                            value.u.x,
                            value.u.y,
                            value.v.x,
                            value.v.y,
                            value.origin.x,
                            value.origin.y,
                        )
                        .joinToString(" ", "matrix(", ")") {
                            BigDecimal((floor(it * 1000 + 0.5) / 1000).toString())
                                .stripTrailingZeros()
                                .toPlainString()
                        }
            }
            is SolidPaths ->
                put(
                    key,
                    mapOf(
                        "fill" to value.fill,
                        "top" to value.top,
                        "outline" to value.outline,
                        "crease" to value.crease,
                        "bevel" to (value.bevel ?: IsoPath.Empty),
                        "shades" to value.shades,
                    ),
                )
            is List<*> -> {
                this["$key.length"] = value.size.toString()
                value.forEachIndexed { index, v -> put("$key.$index", v!!) }
            }
            is Map<*, *> ->
                value.keys.map { it as String }.sorted().forEach { put("$key.$it", value[it]!!) }
            is String,
            is Number,
            is Boolean -> this[key] = value.toString()
            else -> {
                val getters =
                    value.javaClass.declaredMethods.filter {
                        it.name.startsWith("get") &&
                            it.parameterCount == 0 &&
                            java.lang.reflect.Modifier.isPublic(it.modifiers)
                    }
                put(
                    key,
                    getters.associate {
                        it.name.removePrefix("get").replaceFirstChar(Char::lowercase) to
                            it.invoke(value)
                    },
                )
            }
        }
    }
}
