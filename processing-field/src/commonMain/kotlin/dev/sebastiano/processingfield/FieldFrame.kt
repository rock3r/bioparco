package dev.sebastiano.processingfield

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The field at one moment: the grid the marks sit on, where the mass is, how its outline happens to
 * be folded just now, and how big and how dark a mark is at either end of the ramp from outside the
 * shape to inside it.
 *
 * Faithful port of Haplo's Swift `FieldFrame`. None of this is part of the specimen's public look.
 */
internal class FieldFrame(
    width: Double,
    height: Double,
    time: Double?,
    pitch: Double,
    reach: Double,
    softness: Double,
    minRadiusRatio: Double,
    maxRadiusRatio: Double,
    minInk: Double,
    maxInk: Double,
    driftSpeed: Double,
    foldSpeed: Double,
) {
    val columns: Int
    val rows: Int
    val step: Double
    val originY: Double

    private val centreX: Double
    private val centreY: Double
    private val invReachX: Double
    private val invReachY: Double
    private val swell: Double
    private val fold1: Double
    private val fold2: Double
    private val fold3: Double
    private val depth1: Double
    private val depth2: Double
    private val depth3: Double
    private val shoulder: Double
    private val shoulderSpan: Double
    private val radiusFloor: Double
    private val radiusSpan: Double
    private val inkFloor: Double
    private val inkSpan: Double

    init {
        val wanted = max(1.0, pitch)
        val columnCount = max(1, round(width / wanted).toInt())
        val step = width / columnCount.toDouble()
        this.columns = columnCount
        this.step = step
        this.rows = max(1, round(height / step).toInt())
        this.originY = (height - step * this.rows.toDouble()) / 2.0

        val t = time ?: 0.0
        val driftT = t * max(0.0, driftSpeed)
        val foldT = t * max(0.0, foldSpeed)

        this.swell =
            if (time == null) {
                SwellMid
            } else {
                SwellMid + SwellRange * sin(2.0 * PI * foldT / 7.1)
            }
        this.centreX =
            if (time == null) {
                width / 2.0
            } else {
                width * (0.5 + 0.17 * sin(2.0 * PI * driftT / 8.3))
            }
        this.centreY =
            if (time == null) {
                height / 2.0
            } else {
                height * (0.5 + 0.19 * sin(2.0 * PI * driftT / 6.7 + 0.9))
            }

        val breath = 1.0 + 0.10 * sin(2.0 * PI * foldT / 6.1)
        val span = max(0.01, reach) * breath
        val reachX = max(0.5, width * span)
        val reachY = max(0.5, height * span)
        this.invReachX = 1.0 / reachX
        this.invReachY = 1.0 / reachY

        val radiusFloor = step * max(0.0, minRadiusRatio)
        this.radiusFloor = radiusFloor
        this.radiusSpan = max(0.0, step * maxRadiusRatio - radiusFloor)
        val inkFloor = min(max(minInk, 0.0), 1.0)
        this.inkFloor = inkFloor
        this.inkSpan = min(max(maxInk, 0.0), 1.0) - inkFloor

        this.fold1 = 0.34 * foldT
        this.fold2 = -0.22 * foldT
        this.fold3 = 0.16 * foldT
        this.depth1 = 0.10 + 0.08 * sin(2.0 * PI * foldT / 5.9)
        this.depth2 = 0.06 + 0.05 * sin(2.0 * PI * foldT / 4.3 + 1.7)
        this.depth3 = 0.05 + 0.04 * sin(2.0 * PI * foldT / 7.7 + 0.4)

        this.shoulder = max(0.0001, softness)
        this.shoulderSpan = 2.0 * this.shoulder
    }

    fun xOfColumn(column: Int): Double = step * (column.toDouble() + 0.5)

    fun yOfRow(row: Int): Double = originY + step * (row.toDouble() + 0.5)

    fun normalisedX(x: Double): Double = (x - centreX) * invReachX

    fun normalisedY(y: Double): Double = (y - centreY) * invReachY

    fun level(nx: Double, ny: Double): Double {
        val distance = sqrt(nx * nx + ny * ny)
        val angle = atan2(ny, nx)
        val outline =
            1.0 +
                depth1 * sin(3.0 * angle + fold1) +
                depth2 * sin(5.0 * angle + fold2) +
                depth3 * sin(2.0 * angle + fold3)
        val ramp = (outline + shoulder - distance) / shoulderSpan
        val edge = min(max(ramp, 0.0), 1.0)
        return edge * edge * (3.0 - 2.0 * edge) * swell
    }

    fun radiusAtLevel(level: Double): Double = radiusFloor + radiusSpan * level

    fun inkAtLevel(level: Double): Double = inkFloor + inkSpan * level

    fun tangent(nx: Double, ny: Double): Pair<Double, Double> {
        val gx = nx * invReachX
        val gy = ny * invReachY
        val length = sqrt(gx * gx + gy * gy)
        if (length <= 1e-9) return 1.0 to 0.0
        return (-gy / length) to (gx / length)
    }

    private companion object {
        const val SwellMid = 0.82
        const val SwellRange = 0.18
    }
}
