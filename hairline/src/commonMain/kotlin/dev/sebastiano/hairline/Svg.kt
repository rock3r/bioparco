// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

/*
 * The little of SVG the original figures use, kept as a retained tree so the figures port almost
 * line for line: groups, paths, circles and ellipses, a class list, and the few moves a figure
 * makes (append, after, before). The renderer walks this tree in order; later nodes cover earlier
 * ones, exactly as the original's painter's order does.
 */

/** An element's classes, as `classList`. The styles read these; see [Ink]. */
class ClassList(initial: String = "") {
    private val names = LinkedHashSet<String>()

    init {
        set(initial)
    }

    /** Replaces every class, like setting the `class` attribute. */
    fun set(value: String) {
        names.clear()
        value.split(' ').filter { it.isNotEmpty() }.forEach { names.add(it) }
    }

    fun add(name: String) {
        names.add(name)
    }

    fun remove(name: String) {
        names.remove(name)
    }

    fun toggle(name: String, on: Boolean) {
        if (on) names.add(name) else names.remove(name)
    }

    operator fun contains(name: String): Boolean = name in names

    fun sorted(): List<String> = names.sorted()
}

sealed class Node(cls: String) {
    var parent: Group? = null
        internal set

    val classList = ClassList(cls)

    /** The `opacity` attribute; NaN when unset. */
    var opacity: Double = Double.NaN

    /** The `visibility="hidden"` attribute. */
    var hidden: Boolean = false

    /** Whatever the renderer keeps for this node between frames: its colour transition. */
    internal var renderState: Any? = null

    /** Moves [node] to just after this one, in this one's parent, like `Element.after`. */
    fun after(node: Node) {
        val p = parent ?: return
        node.detach()
        p.children.add(p.children.indexOf(this) + 1, node)
        node.parent = p
    }

    /** Moves [node] to just before this one, in this one's parent, like `Element.before`. */
    fun before(node: Node) {
        val p = parent ?: return
        node.detach()
        p.children.add(p.children.indexOf(this), node)
        node.parent = p
    }

    fun remove() {
        detach()
    }

    internal fun detach() {
        parent?.children?.remove(this)
        parent = null
    }
}

class Group(cls: String = "") : Node(cls) {
    val children: MutableList<Node> = ArrayList()

    /** A vertical fade, as the original's mask, for reflections. */
    var fade: Fade? = null

    /** Appends (or moves to the end) each node in turn, like `Element.append`. */
    fun append(vararg nodes: Node) {
        for (n in nodes) {
            n.detach()
            children.add(n)
            n.parent = this
        }
    }

    /** Removes every child, like `replaceChildren()`. */
    fun clear() {
        children.forEach { it.parent = null }
        children.clear()
    }
}

/** A vertical alpha fade in viewBox units: [a0] at [y0], nothing at [y1]. */
class Fade(val y0: Double, val y1: Double, val a0: Double)

class PathNode(cls: String = "", d: PathData = PathData.EMPTY) : Node(cls) {
    var d: PathData = d
        set(value) {
            field = value
            pathCache = null
        }

    /** The renderer's converted path, dropped whenever [d] changes. */
    internal var pathCache: Any? = null
}

/** A circle; `cx`/`cy` are NaN until placed. */
class CircleNode(var r: Double, cls: String = "") : Node(cls) {
    var cx: Double = Double.NaN
    var cy: Double = Double.NaN
}

/** An ellipse; `cx`/`cy` are NaN until placed. */
class EllipseNode(var rx: Double, var ry: Double, cls: String = "") : Node(cls) {
    var cx: Double = Double.NaN
    var cy: Double = Double.NaN
}

/** `mk("g", {}, parent)`. */
fun Group.g(cls: String = ""): Group = Group(cls).also { append(it) }

/** `mk("path", { class, d }, parent)`. */
fun Group.path(cls: String = "", d: PathData = PathData.EMPTY): PathNode =
    PathNode(cls, d).also { append(it) }

/** `mk("circle", { r, class }, parent)`. */
fun Group.circle(r: Double, cls: String = ""): CircleNode = CircleNode(r, cls).also { append(it) }

/** `mk("ellipse", { rx, ry, class }, parent)`. */
fun Group.ellipse(rx: Double, ry: Double, cls: String = ""): EllipseNode =
    EllipseNode(rx, ry, cls).also { append(it) }
