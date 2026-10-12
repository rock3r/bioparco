// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

internal class ArcadeCoin(val from: Point3, val landAt: Point3) {
    var t = 0.0
    var switched = false
    var landed = false
}

internal class ArcadeCoinPose(
    val layer: String,
    val p: Point3,
    val axis: Point3,
    val spin: Double = 0.0,
    val clip: String? = null,
    val stage: String? = null,
    val settled: Boolean = false,
)

internal object ArcadeCoinMotion {
    private const val GRAV = 1962.0
    private const val FLOOR = 8.0 + 0.7 + Arcade.COIN_T / 2
    private val CS = sqrt(3.0) / 2
    private const val ACCEL = 2.0 / 3 * GRAV * 0.5
    private val length = (141 - 104) / CS
    private val rollTime = sqrt(2 * length / ACCEL)
    private val speed = ACCEL * rollTime
    private val end =
        Point3(-42.0, 104 - Arcade.COIN_R * 0.5, Arcade.chuteZ(104.0) + Arcade.COIN_R * CS)
    private val dropTime =
        (-speed * 0.5 + sqrt(speed * speed * 0.25 + 2 * GRAV * (end.z - FLOOR - Arcade.COIN_R))) /
            GRAV
    private val landY = end.y - speed * CS * dropTime
    private val slotOut = Point3(-42.0, 168.8, 104.0)
    private val slotIn = Point3(-42.0, 147.8, 104.0)

    fun newCoin(stack: Int, pile: Int) = ArcadeCoin(stackSpot(stack - 1), pileSpot(pile))

    fun stackSpot(i: Int) = Point3(-74.0, 188.0, 1.6 + i * 0.42 + 0.2)

    fun pileSpot(i: Int): Point3 {
        val a = i * 2.39996
        val r = minOf(5.0, 1.1 * sqrt(i.toDouble()))
        return Point3(
            -42 + cos(a) * r * 1.2,
            (landY + sin(a) * r).coerceIn(
                62 + 0.7 + Arcade.COIN_R + 0.3,
                104 - 0.7 - Arcade.COIN_R - 0.3,
            ),
            FLOOR + floor(i / 12.0) * Arcade.COIN_T,
        )
    }

    fun pose(coin: ArcadeCoin): ArcadeCoinPose {
        val t = coin.t
        if (t < 0.9) return flying(coin)
        if (t < 1.24)
            return ArcadeCoinPose(
                "fly",
                lerp(slotOut, slotIn, smooth(0.0, 1.0, (t - 0.9) / 0.34)),
                Arcade.X,
                7.0,
                "door",
            )
        if (t < 1.66) return ArcadeCoinPose("none", slotIn, Arcade.X, stage = "mech")
        val roll = t - 1.66
        if (roll < rollTime) {
            val s = 0.5 * ACCEL * roll * roll
            val y = 141 - s * CS
            val p = Point3(-42.0, y - Arcade.COIN_R * 0.5, Arcade.chuteZ(y) + Arcade.COIN_R * CS)
            return ArcadeCoinPose(
                "chute",
                p,
                Arcade.X,
                7 + s / Arcade.COIN_R,
                if (p.y + Arcade.COIN_R > 141) "mech" else null,
                "roll",
            )
        }
        return falling(coin, roll - rollTime)
    }

    private fun flying(c: ArcadeCoin): ArcadeCoinPose {
        val s = smooth(0.0, 1.0, c.t / 0.9)
        val mid =
            Point3(
                (c.from.x + slotOut.x) / 2,
                (c.from.y + slotOut.y) / 2,
                maxOf(c.from.z, slotOut.z) + 26,
            )
        val axis = unit3(lerp(Arcade.Z, Arcade.X, smooth(0.15, 0.85, c.t / 0.9)))
        return ArcadeCoinPose(
            "fly",
            lerp(lerp(c.from, mid, s), lerp(mid, slotOut, s), s),
            axis,
            s * 7,
        )
    }

    private fun falling(c: ArcadeCoin, t: Double): ArcadeCoinPose {
        val land = c.landAt
        if (t < dropTime)
            return ArcadeCoinPose(
                "fall",
                Point3(
                    end.x + (land.x - end.x) * t / dropTime,
                    end.y + (land.y - end.y) * t / dropTime,
                    end.z - speed * 0.5 * t - 0.5 * GRAV * t * t,
                ),
                Arcade.X,
                7 + length / Arcade.COIN_R + t * 20,
                stage = "drop",
            )
        val tl = t - dropTime
        val tip = smooth(0.0, 0.16, tl)
        return ArcadeCoinPose(
            "fall",
            Point3(land.x, land.y, land.z + Arcade.COIN_R * (1 - tip)),
            unit3(lerp(Arcade.X, Arcade.Z, tip)),
            7.0,
            stage = "drop",
            settled = tl > 0.22,
        )
    }

    private fun lerp(a: Point3, b: Point3, t: Double) = add3(a, mul3(sub3(b, a), t))
}

internal fun smooth(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
