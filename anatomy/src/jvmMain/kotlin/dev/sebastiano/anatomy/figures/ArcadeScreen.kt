// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.jsRound

/** The original's 5×7 ROM font and sprites, on its 224×288 portrait raster. */
internal object ArcadeScreen {
    const val W = 224
    const val H = 288
    val FONT =
        mapOf(
            'A' to "01110/10001/10001/11111/10001/10001/10001",
            'C' to "01110/10001/10000/10000/10000/10001/01110",
            'D' to "11110/10001/10001/10001/10001/10001/11110",
            'E' to "11111/10000/10000/11110/10000/10000/11111",
            'F' to "11111/10000/10000/11110/10000/10000/10000",
            'H' to "10001/10001/10001/11111/10001/10001/10001",
            'I' to "01110/00100/00100/00100/00100/00100/01110",
            'K' to "10001/10010/10100/11000/10100/10010/10001",
            'L' to "10000/10000/10000/10000/10000/10000/11111",
            'M' to "10001/11011/10101/10101/10001/10001/10001",
            'N' to "10001/11001/10101/10011/10001/10001/10001",
            'O' to "01110/10001/10001/10001/10001/10001/01110",
            'P' to "11110/10001/10001/11110/10000/10000/10000",
            'R' to "11110/10001/10001/11110/10100/10010/10001",
            'S' to "01111/10000/10000/01110/00001/00001/11110",
            'T' to "11111/00100/00100/00100/00100/00100/00100",
            'U' to "10001/10001/10001/10001/10001/10001/01110",
            'Y' to "10001/10001/01010/00100/00100/00100/00100",
            'Z' to "11111/00001/00010/00100/01000/10000/11111",
            '0' to "01110/10011/10101/10101/10101/11001/01110",
            '1' to "00100/01100/00100/00100/00100/00100/01110",
            '2' to "01110/10001/00001/00110/01000/10000/11111",
            '3' to "11111/00010/00100/00010/00001/10001/01110",
            '4' to "00010/00110/01010/10010/11111/00010/00010",
            '5' to "11111/10000/11110/00001/00001/10001/01110",
            '6' to "00110/01000/10000/11110/10001/10001/01110",
            '7' to "11111/00001/00010/00100/01000/01000/01000",
            '8' to "01110/10001/10001/01110/10001/10001/01110",
            '9' to "01110/10001/10001/01111/00001/00010/01100",
            '-' to "00000/00000/00000/11111/00000/00000/00000",
            '.' to "00000/00000/00000/00000/00000/01100/01100",
            ' ' to "00000/00000/00000/00000/00000/00000/00000",
        )
    val SPRITES =
        mapOf(
            "jellyA" to
                "0001111000/0011111100/0110110110/1111111111/1111111111/0010110100/0100000010/0010000100",
            "jellyB" to
                "0001111000/0011111100/0110110110/1111111111/1111111111/0010110100/0001001000/0010000100",
            "eyeA" to "00111100/01111110/11011011/11111111/01100110/11000011",
            "eyeB" to "00111100/01111110/11011011/11111111/01100110/00111100",
            "ship" to
                "00000100000/00001110000/00001110000/01111111110/11111111111/11011111011/10000100001",
            "boom" to
                "1000100010/0100100100/0010001000/1100000011/0010001000/0100100100/1000100010",
        )
    val FORMATION =
        (0..2).flatMap { row ->
            (0..5).map { col -> Alien(34 + col * 26, 92 + row * 18, row == 0) }
        }

    class Alien(val x: Int, val y: Int, val eye: Boolean)

    fun n(v: Double): String =
        (jsRound(v * 100) / 100).let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }

    fun bitmap(rows: String, x: Double, y: Double, s: Double = 1.0): String = buildString {
        rows.split('/').forEachIndexed { r, row ->
            var c = 0
            while (c < row.length) {
                if (row[c] == '1') {
                    var e = c
                    while (e < row.length && row[e] == '1') e++
                    append(
                        "M${n(x + c * s)} ${n(y + r * s)}h${n((e - c) * s)}v${n(s)}h${n(-(e - c) * s)}z"
                    )
                    c = e
                } else c++
            }
        }
    }

    fun text(str: String, x: Double, y: Double, s: Double = 1.0, adv: Int = 8) =
        str.mapIndexed { i, ch -> bitmap(FONT[ch] ?: FONT.getValue(' '), x + i * adv * s, y, s) }
            .joinToString("")

    fun centred(str: String, s: Double = 1.0, adv: Int = 8) =
        n((W - (str.length * adv - (adv - 5)) * s) / 2).toDouble()

    fun sprite(name: String, x: Double, y: Double) = bitmap(SPRITES.getValue(name), x, y)

    val TEST =
        listOf(
            text("SELF TEST", centred("SELF TEST"), 56.0),
            text("RAM OK", 56.0, 104.0),
            text("ROM OK", 56.0, 120.0),
            text("Z80 3.072 MHZ", 56.0, 152.0),
        )
    val HATCH = buildString {
        for (x in 0..W step 16) append("M${minOf(x, W - 1)} 0h1v${H}h-1z")
        for (y in 0..H step 16) append("M0 ${minOf(y, H - 1)}h${W}v1h${-W}z")
    }
    val SCAN = (0..112).joinToString("") { "M${n(-29 + it * 0.518)} -37.3V37.3" }

    /** Every path in SCREEN.svg, including hidden states: parity also checks dormant art. */
    fun paths(): List<String> = buildList {
        add(HATCH)
        addAll(TEST)
        add(text("1UP", 24.0, 8.0) + text("HI-SCORE", 80.0, 8.0) + text("2UP", 176.0, 8.0))
        add(text("00000", 16.0, 20.0))
        add(text("12500", 92.0, 20.0))
        add(text("ANATOMY", centred("ANATOMY", 2.0), 44.0, 2.0))
        for (a in FORMATION) for (f in listOf("A", "B")) add(
            sprite(
                (if (a.eye) "eye" else "jelly") + f,
                a.x + if (a.eye) 1.0 else 0.0,
                a.y.toDouble(),
            )
        )
        add(sprite("boom", 0.0, 0.0))
        add("M0 0h1v5h-1z")
        add(sprite("ship", -5.5, 0.0))
        add("M8 262h208v1h-208z")
        add(text("INSERT COIN", centred("INSERT COIN"), 200.0))
        add(text("PUSH START", centred("PUSH START"), 200.0))
        add(text("CREDIT 0", 120.0, 274.0))
        add(SCAN)
    }
}
