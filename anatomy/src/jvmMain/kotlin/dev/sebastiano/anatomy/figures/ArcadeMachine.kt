// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

internal class ArcadeMachine {
    var touring = true
    var calm = false
    var clock = 0.0
    var powerAt = -99.0
    var offAt = -99.0
    var lastMode = "none"
    var status = ""
    var meter = 4127
    var credits = 0
    var pile = 0
    var stack = 7
    var power = false
        private set

    var game: ArcadeGame? = null
    var stick = 0.0
    var target = 0.0
    var releaseAt = Double.POSITIVE_INFINITY
    var fire = 0.0
    var fireHeld = 0.0
    var wire = 0.0
    var wireVelocity = 0.0
    private val relay = mutableListOf<Double>()
    private val credit = mutableListOf<Double>()
    val tour = ArcadeTour(this)
    val coins = mutableListOf<ArcadeCoin>()
    val pulses = mutableListOf<ArcadePulse>()

    fun insertCoin() {
        if (stack <= 0) stack = 7
        coins.add(ArcadeCoinMotion.newCoin(stack, pile + coins.size))
        stack--
    }

    fun setPower(on: Boolean) {
        if (power == on) return
        power = on
        if (on) {
            powerAt = clock
            game = ArcadeGame()
        } else {
            offAt = clock
            credits = 0
        }
        status =
            if (on) "Coin accepted. The cabinet wakes and the CRT warms up." else "Switched off."
    }

    fun picture(): ArcadePicture = arcadePicture(this)

    fun pulse(route: String, delay: Double = 0.0, seconds: Double = 0.5) {
        if (!calm) pulses.add(ArcadePulse(route, -delay, seconds))
    }

    fun steer(to: Double, seconds: Double = Double.POSITIVE_INFINITY) {
        target = to
        game?.userAt = clock
        releaseAt = clock + seconds
    }

    fun shoot(seconds: Double = 0.16) {
        fireHeld = seconds
        game?.userAt = clock
    }

    fun tick(dt: Double, calm: Boolean): Boolean {
        this.calm = calm
        clock += dt
        if (calm) {
            touring = false
            pulses.clear()
        }
        if (touring) tour.tick(dt)
        coinStep(dt)
        timers()
        wireStep(dt)
        pulses.forEach { it.t += dt }
        pulses.removeAll { it.t >= it.seconds * 1.25 }
        val picture = picture()
        if (power) lastMode = picture.mode
        game?.tick(this, dt, picture.mode)
        controlsStep(dt)
        arcadeSay(this, picture)
        return touring ||
            coins.isNotEmpty() ||
            wire != 0.0 ||
            pulses.isNotEmpty() ||
            picture.busy ||
            controlsBusy() ||
            (power && !calm)
    }

    private fun controlsStep(dt: Double) {
        if (clock >= releaseAt) target = 0.0
        stick = if (calm) target else stick + (target - stick) * (1 - exp(-dt / 0.05))
        if (abs(stick - target) < 1e-3) stick = target
        fireHeld = maxOf(0.0, fireHeld - dt)
        val want = if (fireHeld > 0) 1.0 else 0.0
        fire += (want - fire) * (1 - exp(-dt / 0.03))
        if (abs(want - fire) < 0.01) fire = want
    }

    private fun controlsBusy() = stick != target || fire != 0.0 || fireHeld > 0

    private fun coinStep(dt: Double) {
        for (c in coins) {
            c.t += if (calm) 10.0 else dt
            val p = ArcadeCoinMotion.pose(c)
            if (!c.switched && (calm || (p.stage == "roll" && p.p.y < Arcade.WIRE_FROM.y))) {
                c.switched = true
                meter++
                pulse("wire.coin", seconds = 0.45)
                pulse("wire.meter", seconds = 0.25)
                if (!power) relay.add(clock + if (calm) 0.0 else 0.42)
                credit.add(clock + if (calm) 0.0 else 0.45)
            }
            if (!c.landed && (p.settled || calm)) {
                c.landed = true
                pile++
            }
        }
        coins.removeAll { it.landed }
    }

    private fun timers() {
        relay.removeAll { at ->
            if (clock + 1e-12 < at) false
            else {
                setPower(true)
                pulse("cable.power")
                pulse("cable.video", 0.5)
                true
            }
        }
        credit.removeAll { at ->
            if (clock + 1e-12 < at) false
            else {
                credits++
                true
            }
        }
    }

    private fun wirePush(): Double {
        var push = 0.0
        for (c in coins) {
            val pose = ArcadeCoinMotion.pose(c)
            val dy = Arcade.WIRE_FROM.y - pose.p.y
            if (pose.stage != "roll" || abs(dy) >= Arcade.COIN_R) continue
            val low = pose.p.z - sqrt(Arcade.COIN_R * Arcade.COIN_R - dy * dy)
            push = maxOf(push, minOf(1.2, Arcade.WIRE_FROM.z - low))
        }
        return maxOf(0.0, push) * Arcade.WIRE_LENGTH / (-42 - Arcade.WIRE_FROM.x)
    }

    private fun wireStep(dt: Double) {
        val push = wirePush()
        if (push > wire || calm) {
            wire = push
            wireVelocity = 0.0
        } else
            repeat(4) {
                val h = dt / 4
                wireVelocity += (-900 * wire - 9 * wireVelocity) * h
                wire += wireVelocity * h
            }
        if (abs(wire) < 1e-3 && abs(wireVelocity) < 1e-2) {
            wire = 0.0
            wireVelocity = 0.0
        }
    }
}

internal class ArcadePulse(val route: String, var t: Double, val seconds: Double) {
    fun hot(chunk: Int, count: Int) = t >= 0 && abs(chunk - t / seconds * (count + 1) + 0.5) < 1.1
}
