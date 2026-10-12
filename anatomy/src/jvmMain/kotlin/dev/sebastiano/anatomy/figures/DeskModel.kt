// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/** The computer's input buffer and power, independent of Compose and its frame clock. */
class DeskModel {
    var line: String = ""
        private set

    var power: Boolean = true
        private set

    var lastKey: String = ""
        private set

    var touring: Boolean = true
        private set

    val pressed = mutableSetOf<String>()
    val keys = DeskKeys.keys.map { DeskKeyMotion() }
    var powerClock = 9.0
        private set

    var clock = 0.0
        private set

    private var tour = DeskTour()

    fun press(code: String, hold: Double = 0.09) {
        val index = DeskKeys.keys.indexOfFirst { it.code == code }
        if (index < 0) return
        pressed.add(code)
        keys[index].held = hold
    }

    fun type(char: Char) {
        if (!power) return
        val lower = char.lowercaseChar()
        val code = DeskKeys.shifted[char] ?: lower
        if (DeskKeys.keys.none { it.code == code.toString() }) return
        if (char in DeskKeys.shifted || char != lower) press("Shift", 0.14)
        press(code.toString())
        lastKey = if (char == ' ') "space" else char.toString()
        if (line.length < 20) line += char
    }

    fun erase() {
        if (!power) return
        press("Backspace")
        lastKey = "backspace"
        line = line.dropLast(1)
    }

    fun enter() {
        if (!power) return
        press("Enter")
        lastKey = "return"
        line = ""
    }

    fun setPower(next: Boolean) {
        if (power == next) return
        power = next
        powerClock = 0.0
    }

    fun takeOver(switching: Boolean = false) {
        if (!touring) return
        touring = false
        tour = DeskTour()
        line = ""
        if (!switching) setPower(true)
    }

    fun resume() {
        touring = true
        line = ""
        setPower(true)
    }

    fun readout(): String =
        if (!power) "off"
        else "on · ${line.length} chars" + if (lastKey.isEmpty()) "" else " · key $lastKey"

    fun tick(dt: Double, calm: Boolean): Boolean {
        if (calm) touring = false
        if (touring) tour.tick(this, dt)
        clock += dt
        powerClock = if (calm) 9.0 else powerClock + dt
        keys.forEach { it.tick(dt, calm) }
        return touring || keys.any { it.held > 0 || it.at != 0.0 } || powerClock <= 0.8
    }
}

/** One cap eases down while held, then returns; settled keys cost no drawing-time geometry. */
class DeskKeyMotion {
    var at = 0.0
        private set

    var held = 0.0

    fun tick(dt: Double, calm: Boolean) {
        held = maxOf(0.0, held - dt)
        val want = if (held > 0) 2.2 else 0.0
        at = if (calm) want else at + (want - at) * (1 - exp(-dt / if (want > 0) 0.025 else 0.07))
        if (abs(want - at) < 0.01) at = want
    }
}

/** The original's three phrases, erase, power-off/power-on tour. */
private class DeskTour {
    private var phrase = 0
    private var index = 0
    private var step = "type"
    private var wait = 1.4

    fun tick(pc: DeskModel, dt: Double) {
        wait -= dt
        if (wait > 0) return
        when (step) {
            "type" -> {
                val text = PHRASES[phrase]
                if (index < text.length) {
                    val char = text[index++]
                    pc.type(char)
                    wait = 0.09 + Random.nextDouble() * 0.11 + if (char == ' ') 0.08 else 0.0
                } else {
                    step = "erase"
                    wait = 2.4
                }
            }
            "erase" -> erase(pc)
            "off" -> {
                pc.setPower(false)
                step = "on"
                wait = 1.6
            }
            "on" -> {
                pc.setPower(true)
                phrase = 0
                index = 0
                step = "type"
                wait = 1.4
            }
        }
    }

    private fun erase(pc: DeskModel) {
        if (pc.line.isNotEmpty()) {
            pc.erase()
            wait = 0.055
        } else if (phrase == PHRASES.lastIndex) {
            step = "off"
            wait = 0.9
        } else {
            phrase++
            index = 0
            step = "type"
            wait = 0.7
        }
    }

    private companion object {
        val PHRASES = listOf("hello, claude", "what is a crt?", "draw it isometric")
    }
}
