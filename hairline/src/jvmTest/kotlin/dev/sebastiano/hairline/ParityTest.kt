package dev.sebastiano.hairline

import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every figure against the original. The goldens were captured by running the original TypeScript
 * figures in jsdom through one pointer script (see `hairline/parity/`): each records the script's
 * steps and, at each checkpoint, the caption and the whole SVG tree. Here the same steps drive the
 * port on a fake clock, and its tree must match the original's, node for node, class for class, and
 * every number within [TOLERANCE].
 */
class ParityTest {
    @AfterTest
    fun resetMotion() {
        ReducedMotion.enabled = false
    }

    @Test fun riffle() = parity(Figure.Riffle)

    @Test fun terrain() = parity(Figure.Terrain)

    @Test fun exploded() = parity(Figure.Exploded)

    @Test fun phosphor() = parity(Figure.Phosphor)

    @Test fun slow() = parity(Figure.Slow)

    @Test fun turntable() = parity(Figure.Turntable)

    @Test fun keyboard() = parity(Figure.Keyboard)

    @Test fun elevator() = parity(Figure.Elevator)

    @Test fun phone() = parity(Figure.Phone)

    @Test fun laptop() = parity(Figure.Laptop)

    @Test fun terminal() = parity(Figure.Terminal)

    @Test fun cabinet() = parity(Figure.Cabinet)

    @Test fun branches() = parity(Figure.Branches)

    @Test fun vault() = parity(Figure.Vault)

    @Test fun lockers() = parity(Figure.Lockers)

    @Test fun padlock() = parity(Figure.Padlock)

    @Test fun patch() = parity(Figure.Patch)

    @Test fun dish() = parity(Figure.Dish)

    @Test fun router() = parity(Figure.Router)

    private fun parity(figure: Figure) {
        val golden = golden(figure.id)
        val stage = FakeStage()
        var read = ""
        val mounted = MountedFigure(figure, stage, null) { read = it }
        var i = 0
        while (i < golden.size) {
            val line = golden[i]
            when {
                line.startsWith("> ") -> stage.step(line.removePrefix("> "), mounted)
                line.startsWith("@ ") -> {
                    val at = line.removePrefix("@ ")
                    val expected = ArrayList<String>()
                    i++
                    while (
                        i < golden.size && !golden[i].startsWith(">") && !golden[i].startsWith("@")
                    ) {
                        expected.add(golden[i])
                        i++
                    }
                    compare(figure, at, expected, listOf("read $read") + dump(mounted.svg))
                    continue
                }
            }
            i++
        }
        mounted.destroy()
    }

    private fun compare(figure: Figure, at: String, expected: List<String>, actual: List<String>) {
        val bad =
            (0 until maxOf(expected.size, actual.size)).filter { k ->
                val e = expected.getOrNull(k)
                val a = actual.getOrNull(k)
                e == null || a == null || !same(e, a)
            }
        bad.firstOrNull()?.let { k ->
            val e = expected.getOrNull(k)
            val a = actual.getOrNull(k)
            run {
                fail(
                    "${figure.id} @ \"$at\", line ${k + 1} of ${expected.size} (port has ${actual.size}), " +
                        "${bad.size} lines differ:\n" +
                        "  original: ${e?.take(MAX_SHOWN)}\n" +
                        "  port:     ${a?.take(MAX_SHOWN)}\n" +
                        "  near:\n" +
                        expected.subList(maxOf(0, k - 3), k).joinToString("\n") {
                            "    $it".take(MAX_SHOWN)
                        }
                )
            }
        }
    }

    private fun same(e: String, a: String): Boolean {
        if (e.startsWith("read ")) return e == a
        if (NUMBER.replace(e, "#") != NUMBER.replace(a, "#")) return false
        val en = NUMBER.findAll(e).map { it.value.toDouble() }.toList()
        val an = NUMBER.findAll(a).map { it.value.toDouble() }.toList()
        return en.zip(an).all { (x, y) -> abs(x - y) <= TOLERANCE }
    }

    private fun golden(id: String): List<String> {
        val res = javaClass.getResourceAsStream("/goldens/$id.txt.gz") ?: error("no golden for $id")
        return GZIPInputStream(res).bufferedReader().readLines().filter { it.isNotEmpty() }
    }

    private class FakeStage : Stage {
        private var clock = 1000.0
        private val loop = FrameLoop({ clock }, {})
        private val pointers = ArrayList<PointerHandlers>()
        private val keys = ArrayList<(String) -> Boolean>()
        private val blurs = ArrayList<() -> Unit>()

        override fun now(): Double = clock

        override fun register(tick: Tick): Loop = loop.register(tick)

        override fun pointer(handlers: PointerHandlers): () -> Unit {
            pointers.add(handlers)
            return { pointers.remove(handlers) }
        }

        override fun onKey(handler: (String) -> Boolean): () -> Unit {
            keys.add(handler)
            return { keys.remove(handler) }
        }

        override fun onBlur(handler: () -> Unit): () -> Unit {
            blurs.add(handler)
            return { blurs.remove(handler) }
        }

        fun step(step: String, mounted: MountedFigure) {
            val parts = step.split(' ')
            when (parts[0]) {
                "adv" ->
                    repeat(parts[1].toInt()) {
                        clock += 1000.0 / 60
                        if (loop.running) loop.frame(clock)
                    }
                "move" ->
                    pointers.toList().forEach {
                        it.move(Vec2(parts[1].toDouble(), parts[2].toDouble()))
                    }
                "out" -> pointers.toList().forEach { it.leave() }
                "key" -> keys.toList().forEach { it(parts[1]) }
                "blur" -> blurs.toList().forEach { it() }
                "set" -> mounted.update(parts[1].toDouble())
                else -> error("unknown step $step")
            }
        }
    }

    private companion object {
        const val TOLERANCE = 0.02
        const val MAX_SHOWN = 400
        val NUMBER = Regex("-?\\d+(?:\\.\\d+)?(?:e-?\\d+)?")
    }
}

/**
 * The tree as the capture writes it: one line per node, classes sorted, then the attributes it set.
 */
internal fun dump(root: Group): List<String> {
    val out = ArrayList<String>()
    fun walk(g: Group, depth: Int) {
        for (n in g.children) {
            val (tag, attrs) = describe(n)
            val cls = n.classList.sorted()
            out.add(
                "  ".repeat(depth) +
                    tag +
                    (if (cls.isEmpty()) "" else "." + cls.joinToString(".")) +
                    (if (attrs.isEmpty()) "" else "|" + attrs.joinToString("|"))
            )
            if (n is Group) walk(n, depth + 1)
        }
    }
    walk(root, 0)
    return out
}

private fun describe(n: Node): Pair<String, List<String>> {
    val attrs = ArrayList<String>()
    fun placed(cx: Double, cy: Double) {
        if (!cx.isNaN()) attrs.add("cx=${num(cx)}")
        if (!cy.isNaN()) attrs.add("cy=${num(cy)}")
    }
    val tag =
        when (n) {
            is Group -> "g"
            is PathNode -> {
                if (!n.d.isEmpty()) attrs.add("d=${n.d.toSvg()}")
                "path"
            }
            is CircleNode -> {
                placed(n.cx, n.cy)
                attrs.add("r=${num(n.r)}")
                "circle"
            }
            is EllipseNode -> {
                placed(n.cx, n.cy)
                attrs.add("rx=${num(n.rx)}")
                attrs.add("ry=${num(n.ry)}")
                "ellipse"
            }
        }
    if (!n.opacity.isNaN()) attrs.add("opacity=${toFixed(n.opacity, 3)}")
    if (n.hidden) attrs.add("visibility=hidden")
    if (n is Group) n.fade?.let { attrs.add("mask=${num(it.y0)},${num(it.y1)},${it.a0}") }
    return tag to attrs
}
