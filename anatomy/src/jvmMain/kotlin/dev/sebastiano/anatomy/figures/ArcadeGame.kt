// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.abs
import kotlin.random.Random

internal class ArcadeShot(val x: Double, var y: Double)

internal class ArcadeBoom(val x: Double, val y: Double, var t: Double = 0.22)

internal class ArcadeGame(private val random: () -> Double = { Random.nextDouble() }) {
    var step = 0.0
    var dir = 1
    var offset = 0.0
    var frame = 0
    val alive = BooleanArray(ArcadeScreen.FORMATION.size) { true }
    var shipX = 112.0
    var shot: ArcadeShot? = null
    var boom: ArcadeBoom? = null
    var score = 0
    var cool = 1.2
    var userAt = -99.0
    var respawn = 0.0

    fun tick(m: ArcadeMachine, dt: Double, mode: String) {
        if (mode != "attract") return
        step += dt
        if (step > 0.32) {
            step = 0.0
            offset += dir * 2
            if (abs(offset) >= 16) dir = -dir
            frame = 1 - frame
        }
        val user = m.clock - userAt < 3
        if (user) shipX += m.stick * 420 * dt
        else {
            val live =
                ArcadeScreen.FORMATION.mapIndexedNotNull { i, a ->
                    if (alive[i]) a.x + 5 + offset else null
                }
            val target = if (live.isEmpty()) 112.0 else live[((m.clock * 0.37) % live.size).toInt()]
            shipX += (target - shipX).coerceIn(-50 * dt, 50 * dt)
        }
        shipX = shipX.coerceIn(14.0, 210.0)
        cool -= dt
        if (shot == null && ((m.fire > 0 && user) || (!user && cool < 0))) {
            shot = ArcadeShot(jsRound(shipX), 244.0)
            cool = 0.9 + random() * 0.8
        }
        shotStep(dt)
        boom?.let {
            it.t -= dt
            if (it.t <= 0) boom = null
        }
        if (alive.none { it }) {
            respawn += dt
            if (respawn > 1.2) {
                respawn = 0.0
                alive.fill(true)
            }
        }
    }

    private fun shotStep(dt: Double) {
        val s = shot ?: return
        s.y -= 220 * dt
        val hit =
            ArcadeScreen.FORMATION.indices.firstOrNull { i ->
                val a = ArcadeScreen.FORMATION[i]
                alive[i] &&
                    s.x >= a.x + offset &&
                    s.x <= a.x + offset + 10 &&
                    s.y <= a.y + 8 &&
                    s.y + 5 >= a.y
            }
        if (hit != null) {
            alive[hit] = false
            val a = ArcadeScreen.FORMATION[hit]
            boom = ArcadeBoom(a.x + offset, a.y.toDouble())
            score += if (a.eye) 30 else 10
            shot = null
        } else if (s.y < 30) shot = null
    }
}
