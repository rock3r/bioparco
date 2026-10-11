package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.SolidPaths
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor

/**
 * Golden lines as the parity scripts write them: `name<TAB>value`, numbers the way JavaScript's
 * `String(Math.round(value * 10^digits) / 10^digits)` prints them.
 */
class ParityLines {
    val lines = ArrayList<String>()

    fun put(name: String, value: Any) {
        lines.add("$name\t${if (value is IsoPath) value.svg() else value}")
    }

    fun solid(name: String, paths: SolidPaths) {
        put("$name.fill", paths.fill)
        put("$name.outline", paths.outline)
        put("$name.crease", paths.crease)
        put("$name.top", paths.top)
        paths.shades.forEachIndexed { index, shade -> put("$name.shade$index", shade) }
        put("$name.bevel", paths.bevel ?: "")
        put("$name.seamless", paths.seamless)
    }

    fun faded(name: String, list: List<Pair<IsoPath, Double>>) {
        list.forEachIndexed { index, (d, alpha) ->
            put("$name.$index", "${fixed(alpha, 6)}\t${d.svg()}")
        }
    }

    fun dots(name: String, points: List<Flat>, alphas: List<Double>? = null) {
        put(
            name,
            points.withIndex().joinToString(" ") { (index, p) ->
                val alpha = alphas?.get(index) ?: 1.0
                if (alpha < 1) "${p2(p)}@${fixed(alpha, 6)}" else p2(p)
            },
        )
    }

    companion object {
        fun num(value: Double): String = fixed(value, 9)

        fun p2(p: Flat): String = "${fixed(p.x, 4)},${fixed(p.y, 4)}"

        fun p3(p: Point3): String = "${num(p.x)},${num(p.y)},${num(p.z)}"

        fun points3(list: List<Point3>): String = list.joinToString(" ") { p3(it) }

        /** JavaScript's `String(Math.round(value * 10^digits) / 10^digits)`. */
        fun fixed(value: Double, digits: Int): String {
            var scale = 1.0
            repeat(digits) { scale *= 10 }
            val rounded = floor(value * scale + 0.5) / scale
            if (rounded == 0.0) return "0"
            val exact = BigDecimal(rounded.toString()).stripTrailingZeros()
            if (abs(rounded) >= 1e-6) return exact.toPlainString()
            // JavaScript writes 0.00000032 as 3.2e-7.
            val digits = exact.unscaledValue().abs().toString()
            val exponent = digits.length - 1 - exact.scale()
            val mantissa = if (digits.length == 1) digits else "${digits[0]}.${digits.drop(1)}"
            return "${if (rounded < 0) "-" else ""}${mantissa}e$exponent"
        }
    }
}
