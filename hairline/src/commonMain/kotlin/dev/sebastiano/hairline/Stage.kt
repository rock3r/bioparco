// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import kotlin.math.max
import kotlin.math.min

/*
 * The side every figure shares: the helpers that write a solid or a dot into the tree, one frame
 * loop, the pointer, and tear-down. The host (Compose on screen, a fake clock in the tests) sits
 * behind [Stage].
 */

/* ---------- the contract ---------- */

/** Where a figure writes its caption. Only a change goes any further. */
class Readout(private val onChange: (String) -> Unit = {}) {
    var textContent: String? = null
        set(value) {
            val next = value ?: ""
            if (next == field) return
            field = next
            onChange(next)
        }
}

/**
 * A figure's frame: dt in seconds (capped at 50ms), now in ms. Returns whether it wants another
 * frame.
 */
fun interface Tick {
    fun tick(dt: Double, now: Double): Boolean
}

interface Loop {
    /** Ask for frames again after input; the loop runs until the tick returns false. */
    fun wake()

    /** Leave the loop. Idempotent. */
    fun unregister()
}

/** Pointer input in viewBox units (400 × 320). */
interface PointerHandlers {
    fun move(p: Vec2)

    /** A press; [move] unless a figure says otherwise. */
    fun down(p: Vec2) {
        move(p)
    }

    fun leave()
}

/** What hosts a figure: its clock, its frames, and its input. */
interface Stage {
    /** Milliseconds on the host's clock, as `performance.now()`. */
    fun now(): Double

    /**
     * Joins the frame loop. The tick runs once now, so the figure is drawn before it is ever on
     * screen, then on every frame while it keeps returning true.
     */
    fun register(tick: Tick): Loop

    /**
     * Pointer input; returns the disposer. A mouse leaving acts at once, a finger lifting after
     * 1.4s.
     */
    fun pointer(handlers: PointerHandlers): () -> Unit

    /** Key presses, by DOM key name (`ArrowLeft`, `Escape`…). Return true to claim the key. */
    fun onKey(handler: (String) -> Boolean): () -> Unit

    /** Focus leaving the figure. */
    fun onBlur(handler: () -> Unit): () -> Unit
}

/** What a figure is handed: its host, the root of its drawing, and its caption. */
class FigureEls(val stage: Stage, val svg: Group, val read: Readout)

interface FigureHandle {
    /** The figure's own number, from [parameter]. */
    fun set(value: Double)

    fun destroy()
}

typealias FigureMount = (FigureEls, Double) -> FigureHandle

/* ---------- drawing helpers ---------- */

/** A solid's two paths in one group: the silhouette (`.sil`) and its crease (`.nf.lo`). */
class Solid(val g: Group, val sil: PathNode, val cr: PathNode)

fun solid(parent: Group): Solid {
    val g = parent.g()
    return Solid(g, g.path("sil"), g.path("nf lo"))
}

/** Writes a prism's paths into a solid. */
fun put(el: Solid, s: PrismPaths) {
    el.sil.d = s.sil
    el.cr.d = s.crease
}

/**
 * A dot lying flat on a horizontal plane: an ellipse squashed by the camera. Position it with
 * [place].
 */
fun flatDot(parent: Group, c: Camera, r: Double, cls: String): EllipseNode =
    parent.ellipse(r2(r * c.s), r2(r * c.s * c.k), cls)

fun place(el: EllipseNode, q: Vec2) {
    el.cx = r2(q.x)
    el.cy = r2(q.y)
}

fun place(el: CircleNode, q: Vec2) {
    el.cx = r2(q.x)
    el.cy = r2(q.y)
}

/**
 * A prism's mirror under its floor: the far edge of the reflection and its two sides, fading out.
 */
fun reflect(
    parent: Group,
    p: Projector,
    front: (Sample) -> Boolean,
    ring: List<Sample>,
    z0: Double,
    depth: Double,
) {
    val r = ghost(p, front, ring, z0, depth)
    val gh = parent.g("ghost")
    gh.fade = Fade(r2(r.y0), r2(r.y1), 0.7)
    gh.path(d = r.d)
}

/* ---------- one loop ---------- */

/**
 * The original's shared frame loop, for one host. Frames run while any registered tick asks for
 * them; [wake] after input restarts it, and the first frame after a sleep measures dt from the
 * wake.
 */
class FrameLoop(private val clock: () -> Double, private val requestFrame: () -> Unit) {
    private class Board(val tick: Tick) {
        var awake = true
    }

    private var boards: List<Board> = emptyList()
    private var last = 0.0

    /** Whether a frame is wanted. */
    var running: Boolean = false
        private set

    fun register(tick: Tick): Loop {
        val b = Board(tick)
        boards = boards + b
        wake(b)
        tick.tick(0.0, clock())
        var gone = false
        return object : Loop {
            override fun wake() {
                if (!gone) wake(b)
            }

            override fun unregister() {
                if (gone) return
                gone = true
                boards = boards - b
                if (boards.isEmpty()) running = false
            }
        }
    }

    /** Wakes every figure, as the original does when reduced motion changes. */
    fun wakeAll() {
        boards.forEach { wake(it) }
    }

    private fun wake(b: Board) {
        b.awake = true
        if (!running) {
            last = clock()
            running = true
            requestFrame()
        }
    }

    /** Runs one frame at [now] ms. Returns whether another is wanted. */
    fun frame(now: Double): Boolean {
        val dt = min(0.05, max(0.0, (now - last) / 1000))
        last = now
        var any = false
        for (b in boards) {
            if (b.awake) {
                b.awake = b.tick.tick(dt, now)
                any = any || b.awake
            }
        }
        running = any
        return any
    }
}

/* ---------- tear-down ---------- */

/**
 * Collects a figure's tear-down, so its `destroy` is one call. Runs everything added, last first,
 * once.
 */
class Disposer {
    private var fns: List<() -> Unit> = emptyList()

    fun add(fn: () -> Unit) {
        fns = fns + fn
    }

    fun dispose() {
        val run = fns
        fns = emptyList()
        for (i in run.indices.reversed()) run[i]()
    }
}
