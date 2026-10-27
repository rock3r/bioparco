// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import dev.sebastiano.hairline.figures.mountBranches
import dev.sebastiano.hairline.figures.mountCabinet
import dev.sebastiano.hairline.figures.mountDish
import dev.sebastiano.hairline.figures.mountElevator
import dev.sebastiano.hairline.figures.mountExploded
import dev.sebastiano.hairline.figures.mountKeyboard
import dev.sebastiano.hairline.figures.mountLaptop
import dev.sebastiano.hairline.figures.mountLockers
import dev.sebastiano.hairline.figures.mountPadlock
import dev.sebastiano.hairline.figures.mountPatch
import dev.sebastiano.hairline.figures.mountPhone
import dev.sebastiano.hairline.figures.mountPhosphor
import dev.sebastiano.hairline.figures.mountRiffle
import dev.sebastiano.hairline.figures.mountRouter
import dev.sebastiano.hairline.figures.mountSlow
import dev.sebastiano.hairline.figures.mountTerminal
import dev.sebastiano.hairline.figures.mountTerrain
import dev.sebastiano.hairline.figures.mountTurntable
import dev.sebastiano.hairline.figures.mountVault

/**
 * The nineteen figures: each one's id, its name, its accessible description, the caption it rests
 * on, and its engine. [focusable] figures take the arrow keys.
 */
enum class Figure(
    val id: String,
    val title: String,
    val label: String,
    val rest: String,
    internal val mount: FigureMount,
    val focusable: Boolean = false,
) {
    Riffle(
        "riffle",
        "Riffle",
        "A tray of eight cards. Hover or use the arrow keys to pull a card.",
        "rest",
        ::mountRiffle,
        focusable = true,
    ),
    Terrain(
        "terrain",
        "Terrain",
        "Eighty-one pillars rise around the pointer. At rest, they form two raised areas on a base.",
        "rest",
        ::mountTerrain,
    ),
    Exploded(
        "exploded",
        "Exploded",
        "An app window taken apart into four layers. Moving across opens the gap; moving down picks a layer.",
        "",
        ::mountExploded,
    ),
    Phosphor(
        "phosphor",
        "Phosphor",
        "A seven by seven dot matrix plays a loop on a floating tile. Move the pointer to draw " +
            "dots that fade over time.",
        "loop",
        ::mountPhosphor,
    ),
    Slow(
        "slow",
        "Slow",
        "Crates move on a belt through a gate. Hover over the belt to slow the animation.",
        "rate 1.00×",
        ::mountSlow,
    ),
    Turntable(
        "turntable",
        "Turntable",
        "Blocks on a turntable. Flick across it to spin it; it settles on the nearest quarter turn.",
        "az 045° · el 30°",
        ::mountTurntable,
    ),
    Keyboard(
        "keyboard",
        "Keyboard",
        "A sixty-key board responds to the pointer. The nearest key moves down most; " +
            "keys further away move less.",
        "rest",
        ::mountKeyboard,
    ),
    Elevator(
        "elevator",
        "Elevator",
        "Four floors sit beside an open shaft. The pointer's height selects a floor. The car " +
            "passes the other floors on its way there.",
        "rest",
        ::mountElevator,
    ),
    Phone(
        "phone",
        "Phone",
        "A phone in layers: glass, board, battery, shell. Moving across opens the gap; moving down " +
            "picks a layer.",
        "rest",
        ::mountPhone,
    ),
    Laptop(
        "laptop",
        "Laptop",
        "A thin laptop: the pointer's height sets how far the lid stands open, and the lid follows " +
            "it on a spring.",
        "rest",
        ::mountLaptop,
    ),
    Terminal(
        "terminal",
        "Terminal",
        "A terminal window: the pointer's height scrolls back through its history, and the line " +
            "under it lifts off the screen.",
        "rest",
        ::mountTerminal,
    ),
    Cabinet(
        "cabinet",
        "Cabinet",
        "A rack holds twelve blade servers. The pointer's height pulls nearby servers out on " +
            "their rails. Servers further away move less.",
        "rest",
        ::mountCabinet,
    ),
    Branches(
        "branches",
        "Branches",
        "A board shows a commit graph. The commit under the pointer rises. Earlier commits in " +
            "its history rise less as their distance increases.",
        "rest",
        ::mountBranches,
    ),
    Vault(
        "vault",
        "Vault",
        "Move the pointer in a circle to turn the vault dial. The dial slows to ten-unit steps. " +
            "At forty, the three bolts retract.",
        "rest",
        ::mountVault,
    ),
    Lockers(
        "lockers",
        "Lockers",
        "A bank of twelve lockers, one ajar at rest: the locker under the pointer opens, and the one " +
            "open before it swings shut.",
        "rest",
        ::mountLockers,
    ),
    Padlock(
        "padlock",
        "Padlock",
        "A padlock: as the pointer nears, the shackle springs up out of the body and swings open " +
            "about its long leg.",
        "rest",
        ::mountPadlock,
    ),
    Patch(
        "patch",
        "Patch",
        "A patch panel has twenty-four ports. The cable under the pointer lifts. Nearby cables " +
            "lean away; cables further away move less.",
        "rest",
        ::mountPatch,
    ),
    Dish(
        "dish",
        "Dish",
        "A parabolic dish on a two-axis gimbal: the pointer aims it, and it follows on a spring.",
        "rest",
        ::mountDish,
    ),
    Router(
        "router",
        "Router",
        "A Wi-Fi router's antennas lean toward the pointer. The nearest antenna moves most; " +
            "antennas further away move less.",
        "rest",
        ::mountRouter,
    ),
}

/**
 * A figure on a stage: the original's `create`. It makes the drawing's root and the read-out,
 * starts the engine at the intensity's number, and writes the rest caption if the engine wrote
 * none.
 */
class MountedFigure(
    val figure: Figure,
    stage: Stage,
    intensity: Double?,
    onRead: (String) -> Unit,
) {
    val svg = Group()
    private val read = Readout(onRead)
    private var value = parameter(figure.id, intensity)
    private val engine = figure.mount(FigureEls(stage, svg, read), value)
    private var dead = false

    init {
        if (read.textContent == null) read.textContent = figure.rest
    }

    fun update(intensity: Double?) {
        if (dead) return
        val v = parameter(figure.id, intensity)
        if (v != value) {
            value = v
            engine.set(v)
        }
    }

    fun destroy() {
        if (dead) return
        dead = true
        engine.destroy()
    }
}
