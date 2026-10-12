// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import androidx.compose.ui.graphics.Color
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Tone

/*
 * The materials of `kit/turn.mjs` (`T3_MATERIALS`): each remaps the kit's ramp (paper, face, top,
 * the four side tones, the lit tokens, the line tones, dots) and a tube's body, shine, shade and
 * edge, with a light and a dark set. Tones still come from facing exactly as the kit's do.
 */

/** A part's finish. */
enum class Material(light: List<Long>, dark: List<Long>) {
    /** Champagne-gold anodised aluminium: warm pale top, bronze shadows, dark bronze outline. */
    Gold(
        listOf(
            0xd3bb88,
            0xe4d2a8,
            0xf1e5c6,
            0xa1844f,
            0xbc9f69,
            0xd3bb88,
            0xe4d2a8,
            0xf8efd8,
            0xdfcb9c,
            0x4b3920,
            0x86693f,
            0xa98c5b,
            0xc1a676,
            0x4a381f,
            0x2a1d0c,
            0xcdb07a,
            0xf3e8cc,
            0x8e7044,
            0x4b3920,
        ),
        listOf(
            0x3f3521,
            0x4b3f28,
            0x5a4c31,
            0x2b2416,
            0x352c1b,
            0x3f3521,
            0x4b3f28,
            0x6a5a3b,
            0x4f4329,
            0xe6d2a3,
            0x8a7550,
            0x5e4e33,
            0x463a26,
            0xe6d2a3,
            0xfff3d6,
            0x4a3e27,
            0x8c7853,
            0x251f13,
            0xe6d2a3,
        ),
    ),
    /**
     * Bright polished steel, non-monotonic on purpose: a dark reflection band beside a bright one.
     */
    Chrome(
        listOf(
            0x8e959c,
            0xc3c8cd,
            0xf7f8f9,
            0xa2a9b0,
            0x6f767d,
            0x454b52,
            0xcacfd4,
            0xffffff,
            0xbcc2c8,
            0x202428,
            0x585e65,
            0x878d94,
            0xa9afb5,
            0x2a2e33,
            0x000000,
            0x8b9299,
            0xfbfcfd,
            0x3c4248,
            0x202428,
        ),
        listOf(
            0x2a2e32,
            0x4a5056,
            0x6e757c,
            0x2e3236,
            0x24272b,
            0x1a1d20,
            0x4a5056,
            0x848b92,
            0x3a3f44,
            0xdfe3e7,
            0x7c838a,
            0x4d5359,
            0x363b40,
            0xe4e7ea,
            0xffffff,
            0x34393e,
            0x9aa1a8,
            0x16191b,
            0xdfe3e7,
        ),
    ),
    /** Satin steel, monotonic and cool. */
    Steel(
        listOf(
            0xb8bdc1,
            0xcdd1d4,
            0xe5e8ea,
            0x8f959b,
            0xa4aaaf,
            0xb8bdc1,
            0xcdd1d4,
            0xf1f3f4,
            0xc6cacd,
            0x2b2f33,
            0x6b7177,
            0x959ba1,
            0xaeb3b8,
            0x33373b,
            0x000000,
            0xb3b8bd,
            0xeef0f2,
            0x7d8389,
            0x2b2f33,
        ),
        listOf(
            0x2c3034,
            0x34393d,
            0x3e4348,
            0x1f2225,
            0x25292c,
            0x2c3034,
            0x34393d,
            0x4a5056,
            0x33383c,
            0xd0d4d8,
            0x727980,
            0x4a5056,
            0x33383d,
            0xd8dce0,
            0xffffff,
            0x30353a,
            0x5c6369,
            0x1a1d20,
            0xd0d4d8,
        ),
    ),
    /** Dark blue-grey metal with light dots. */
    Gunmetal(
        listOf(
            0x444a51,
            0x50575e,
            0x5f666e,
            0x2f3439,
            0x393f45,
            0x444a51,
            0x50575e,
            0x6d757d,
            0x4b5258,
            0x0f1113,
            0x1d2125,
            0x272b30,
            0x30353a,
            0xb0b5ba,
            0x000000,
            0x3d4349,
            0x6e757c,
            0x24282c,
            0x0f1113,
        ),
        listOf(
            0x1d2125,
            0x22272b,
            0x2a2f34,
            0x15181b,
            0x191c20,
            0x1d2125,
            0x22272b,
            0x323840,
            0x20252a,
            0xa9b0b7,
            0x5c636a,
            0x3a4046,
            0x2a2f34,
            0xb8bec4,
            0xffffff,
            0x1f2327,
            0x3a4046,
            0x111316,
            0xa9b0b7,
        ),
    ),
    /** Black rubber and cable sheath with a soft sheen; with a hue, a coloured sheath. */
    Rubber(
        listOf(
            0x2a2b2e,
            0x34363a,
            0x46484d,
            0x1b1c1e,
            0x222326,
            0x2a2b2e,
            0x34363a,
            0x55575c,
            0x2f3134,
            0x070708,
            0x111214,
            0x18191b,
            0x1f2023,
            0x6a6c70,
            0x000000,
            0x27282b,
            0x5b5d62,
            0x121314,
            0x050506,
        ),
        listOf(
            0x121213,
            0x161618,
            0x1c1c1f,
            0x0c0c0d,
            0x0f0f10,
            0x121213,
            0x161618,
            0x232327,
            0x141416,
            0x8c8e92,
            0x4a4c50,
            0x2c2d30,
            0x1f2023,
            0x9a9ca0,
            0xffffff,
            0x131315,
            0x2b2c30,
            0x0a0a0b,
            0x8c8e92,
        ),
    ),
    /** A warm metal for fittings, ports, valves. */
    Brass(
        listOf(
            0xbd9646,
            0xd2ad5b,
            0xe6c87e,
            0x8a6a2c,
            0xa47f38,
            0xbd9646,
            0xd2ad5b,
            0xf0d796,
            0xc8a252,
            0x3f2e0e,
            0x765a24,
            0x9a7a3c,
            0xb08c46,
            0x3a2a0c,
            0x1f1606,
            0xb8913f,
            0xefd795,
            0x7d5f26,
            0x3f2e0e,
        ),
        listOf(
            0x3d3017,
            0x49391c,
            0x574523,
            0x2a210f,
            0x332813,
            0x3d3017,
            0x49391c,
            0x66522b,
            0x45371b,
            0xe9cf8a,
            0x8e7438,
            0x5e4c25,
            0x44371c,
            0xe9cf8a,
            0xfff0c8,
            0x433419,
            0x8a6f36,
            0x21190b,
            0xe9cf8a,
        ),
    ),
    /** A warm metal for fittings. */
    Copper(
        listOf(
            0xb3633e,
            0xc9784f,
            0xdf9670,
            0x7c4128,
            0x9a5132,
            0xb3633e,
            0xc9784f,
            0xeaa985,
            0xbe6d46,
            0x3a1a0c,
            0x6e3820,
            0x925037,
            0xa65d3f,
            0x3a1a0c,
            0x1c0b04,
            0xad5f3b,
            0xf0b896,
            0x6e3720,
            0x3a1a0c,
        ),
        listOf(
            0x3d1f13,
            0x492617,
            0x582e1c,
            0x2a160d,
            0x331a10,
            0x3d1f13,
            0x492617,
            0x673722,
            0x432215,
            0xeaa985,
            0x8f5236,
            0x5f3624,
            0x45271a,
            0xeaa985,
            0xffe0cc,
            0x42221a,
            0x8a4c31,
            0x1f0f08,
            0xeaa985,
        ),
    );

    private val lightSet = light
    private val darkSet = dark

    /** The material's 19 colours in the original's order (`T3_MAT_KEYS`, then the tube's). */
    fun colours(dark: Boolean): List<Color> =
        (if (dark) darkSet else lightSet).map { Color(0xFF000000 or it) }

    /** The original's name, as `data-mat` writes it. */
    val id: String = name.lowercase()

    companion object {
        fun of(id: String): Material? = entries.firstOrNull { it.id == id }
    }
}

/** A tube's four colours: body, shine, shade and edge. */
class TubeInk(val body: Color, val shine: Color, val shade: Color, val edge: Color)

/** [palette] with [material]'s ramp in its theme: what `[data-mat]` does to the CSS tokens. */
fun materialPalette(palette: Palette, material: Material?): Palette {
    if (material == null) return palette
    val c = material.colours(palette.isDark)
    return palette.copy(
        paper = c[0],
        face = c[1],
        top = c[2],
        shades = listOf(c[3], c[4], c[5], c[6]),
        litTop = c[7],
        litShade = c[8],
        hi = c[9],
        mid = c[10],
        lo = c[11],
        faint = c[12],
        dot = c[13],
        lit = c[14],
    )
}

/** CSS `color-mix(in srgb, a share, b)`. */
internal fun mixSrgb(a: Color, share: Float, b: Color): Color =
    Color(
        red = a.red * share + b.red * (1 - share),
        green = a.green * share + b.green * (1 - share),
        blue = a.blue * share + b.blue * (1 - share),
        alpha = a.alpha * share + b.alpha * (1 - share),
    )

/**
 * A tube's colours on [palette] (already the material's, if any): the kit's, the material's, or a
 * [hue] mixed into either, as `TURN_CSS` writes them.
 */
fun tubeInk(palette: Palette, material: Material?, hue: Tone?, edgeTone: Tone): TubeInk {
    val own = material?.colours(palette.isDark)
    val base =
        if (own != null) TubeInk(own[15], own[16], own[17], own[18])
        else TubeInk(palette.shades[2], palette.top, palette.shades[0], palette.ink(edgeTone))
    if (hue == null) return base
    val ink = palette.ink(hue)
    return if (own != null) {
        TubeInk(
            mixSrgb(ink, 0.62f, base.body),
            mixSrgb(ink, 0.22f, base.shine),
            mixSrgb(ink, 0.46f, base.shade),
            mixSrgb(ink, 0.30f, base.edge),
        )
    } else {
        TubeInk(
            mixSrgb(ink, 0.54f, palette.shades[2]),
            mixSrgb(ink, 0.16f, palette.top),
            mixSrgb(ink, 0.30f, palette.hi),
            mixSrgb(ink, 0.38f, palette.hi),
        )
    }
}

private val MATERIAL_KEYS =
    listOf(
        "paper",
        "face",
        "top",
        "shade-0",
        "shade-1",
        "shade-2",
        "shade-3",
        "lit-top",
        "lit-shade",
        "hi",
        "mid",
        "lo",
        "faint",
        "dot",
        "lit",
    )

private val TUBE_KEYS = listOf("body", "shine", "shade", "edge")

/** The materials as the original's CSS writes them (`TURN.materialCss()`), for checks. */
fun materialCss(): String {
    fun hex(c: Color) =
        "#%02x%02x%02x"
            .format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    fun block(colours: List<Color>) =
        (MATERIAL_KEYS.mapIndexed { i, key -> "--anatomy-$key:${hex(colours[i])}" } +
                TUBE_KEYS.mapIndexed { i, key ->
                    "--mat-$key:${hex(colours[MATERIAL_KEYS.size + i])}"
                })
            .joinToString(";")
    val rules = ArrayList<String>()
    for (material in Material.entries) {
        rules.add("[data-mat=\"${material.id}\"]{${block(material.colours(true))}}")
        rules.add(
            ".iso[data-theme=\"light\"] [data-mat=\"${material.id}\"]{${block(material.colours(false))}}"
        )
        rules.add(
            ".iso[data-theme=\"dark\"] [data-mat=\"${material.id}\"]{${block(material.colours(true))}}"
        )
    }
    return rules.joinToString("\n")
}
