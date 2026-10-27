// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.slabOf

class DeskKey(val code: String, val plan: Plan, val height: Double) {
    val paths by lazy { slabOf(plan, DeskKeys.TOP, height, DeskComputer.P, 3, 0.4) }
    val art: Art by lazy {
        art {
            solid(paths, tone = Tone.Mid)
            line(
                planOutline(
                    insetPlan(plan, if (code == " ") 1.8 else 1.6),
                    DeskKeys.TOP + height,
                    DeskComputer.P,
                    3,
                ),
                tone = Tone.Faint,
            )
        }
    }
}

object DeskKeys {
    const val TOP = 8.6
    const val U = 9.2
    const val X = -U * 7.5
    const val Y = 88.5
    private val heights = listOf(5.6, 5.2, 4.8, 4.4, 4.0)
    private val rows =
        listOf(
            listOf("`" to 1.0) + "1234567890-=".map { it.toString() to 1.0 } + ("Backspace" to 2.0),
            listOf("Tab" to 1.5) + "qwertyuiop[]".map { it.toString() to 1.0 } + ("\\" to 1.5),
            listOf("CapsLock" to 1.75) +
                "asdfghjkl;'".map { it.toString() to 1.0 } +
                ("Enter" to 2.25),
            listOf("Shift" to 2.25) +
                "zxcvbnm,./".map { it.toString() to 1.0 } +
                ("ShiftRight" to 2.75),
            listOf(
                "Alt" to 1.5,
                "Meta" to 1.5,
                " " to 9.0,
                "NumpadEnter" to 1.5,
                "AltRight" to 1.5,
            ),
        )
    val shifted = "~!@#$%^&*()_+{}|:\"<>?".zip("`1234567890-=[]\\;',./").toMap()
    val keys: List<DeskKey> = rows.flatMapIndexed { row, entries ->
        var x = X
        entries.map { (code, width) ->
            val plan = Plan(x + 0.7, Y + row * U + 0.7, width * U - 1.4, U - 1.4, 1.4)
            x += width * U
            DeskKey(code, plan, heights[row])
        }
    }
}
