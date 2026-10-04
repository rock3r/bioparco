// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

/*
 * One option, intensity, for every figure. Each figure turns it into the one number its engine
 * takes: two straight lines that meet at 0.5, where the number is the figure's default, with the
 * figure's limits at 0 and 1.
 */

/**
 * Each figure's number at intensity 0, 0.5 and 1. Slow's falls: a slower clock is a stronger
 * answer.
 */
internal val TABLE: Map<String, DoubleArray> =
    mapOf(
        "riffle" to doubleArrayOf(0.0, 40.0, 90.0), // stagger, ms
        "terrain" to doubleArrayOf(1.5, 3.0, 5.0), // radius, cells
        "exploded" to doubleArrayOf(12.0, 28.0, 40.0), // gap, viewBox units
        "phosphor" to doubleArrayOf(150.0, 520.0, 1500.0), // afterglow, ms
        "slow" to doubleArrayOf(0.6, 0.2, 0.05), // rate, × normal speed
        "turntable" to doubleArrayOf(200.0, 650.0, 1500.0), // coast, ms
        "keyboard" to doubleArrayOf(1.0, 2.0, 3.5), // radius, keys
        "elevator" to doubleArrayOf(40.0, 100.0, 220.0), // stiffness, spring units
        "phone" to doubleArrayOf(16.0, 28.0, 40.0), // gap, viewBox units
        "laptop" to doubleArrayOf(100.0, 125.0, 150.0), // lid, degrees
        "terminal" to doubleArrayOf(1.0, 2.0, 3.5), // spread, lines
        "cabinet" to doubleArrayOf(1.5, 3.0, 5.0), // reach, blades
        "branches" to doubleArrayOf(1.0, 3.0, 6.0), // reach, commits
        "vault" to doubleArrayOf(250.0, 600.0, 1500.0), // coast, ms
        "lockers" to doubleArrayOf(55.0, 90.0, 120.0), // opening, degrees
        "padlock" to doubleArrayOf(45.0, 90.0, 100.0), // swing, degrees
        "patch" to doubleArrayOf(1.0, 2.5, 5.0), // radius, ports
        "dish" to doubleArrayOf(30.0, 50.0, 70.0), // reach, degrees
        "router" to doubleArrayOf(0.5, 1.5, 3.0), // spread, antennas
    )

const val DEFAULT_INTENSITY = 0.5

/** An intensity: what is not a finite number is the default, the rest is clamped to 0…1. */
fun intensity(value: Double?): Double =
    if (value == null || !value.isFinite()) DEFAULT_INTENSITY else clamp(value, 0.0, 1.0)

/** The figure's own number for an intensity, rounded to three decimals so 0.7 gives Riffle 60. */
fun parameter(figure: String, value: Double?): Double {
    val (lo, mid, hi) = TABLE.getValue(figure)
    val i = intensity(value)
    val v = if (i <= 0.5) lo + i / 0.5 * (mid - lo) else mid + (i - 0.5) / 0.5 * (hi - mid)
    return jsRound(v * 1000) / 1000
}
