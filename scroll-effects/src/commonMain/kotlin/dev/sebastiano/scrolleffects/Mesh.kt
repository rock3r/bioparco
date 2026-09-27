package dev.sebastiano.scrolleffects

/**
 * A fixed-size list of triangles textured with one card's art. Every effect bends, breaks or
 * scatters the card by moving these vertices; the renderer draws them with the card as texture.
 *
 * The buffers keep their size for the life of the mesh, so a frame rewrites them in place and
 * allocates nothing.
 */
class Mesh(val vertexCount: Int, withAlpha: Boolean = false) {
    /** Screen positions, `x, y` per vertex, in pixels. */
    val positions = FloatArray(vertexCount * 2)

    /** Texture coordinates in card space: `0..1` across the card on both axes. */
    val uvs = FloatArray(vertexCount * 2)

    /** Per-vertex opacity, or `null` when the mesh is always opaque. */
    val alphas: FloatArray? = if (withAlpha) FloatArray(vertexCount) { 1f } else null
}

/** Untextured triangles with a colour per vertex, used for the Stretch effect's rainbow rim. */
class ColorMesh(val vertexCount: Int) {
    val positions = FloatArray(vertexCount * 2)

    /** ARGB colours, not premultiplied. */
    val colors = IntArray(vertexCount)
}

/**
 * A card-shaped grid of `cols × rows` quads. Effects move the shared grid [points]; [commit] then
 * copies them into the triangle list, so each point is computed once rather than six times.
 */
class GridMesh(val cols: Int, val rows: Int) {
    val mesh = Mesh(cols * rows * VERTICES_PER_QUAD)

    /** `x, y` per grid point, row by row, `(cols + 1) × (rows + 1)` points. */
    val points = FloatArray((cols + 1) * (rows + 1) * 2)

    init {
        forEachCorner { vertex, col, row ->
            mesh.uvs[vertex * 2] = col / cols.toFloat()
            mesh.uvs[vertex * 2 + 1] = row / rows.toFloat()
        }
    }

    fun u(col: Int): Float = col / cols.toFloat()

    fun v(row: Int): Float = row / rows.toFloat()

    fun setPoint(col: Int, row: Int, x: Float, y: Float) {
        val i = (row * (cols + 1) + col) * 2
        points[i] = x
        points[i + 1] = y
    }

    fun x(col: Int, row: Int): Float = points[(row * (cols + 1) + col) * 2]

    fun y(col: Int, row: Int): Float = points[(row * (cols + 1) + col) * 2 + 1]

    fun commit() {
        forEachCorner { vertex, col, row ->
            val i = (row * (cols + 1) + col) * 2
            mesh.positions[vertex * 2] = points[i]
            mesh.positions[vertex * 2 + 1] = points[i + 1]
        }
    }

    private inline fun forEachCorner(action: (vertex: Int, col: Int, row: Int) -> Unit) {
        var vertex = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                // Two triangles per quad: top-left, top-right, bottom-left; then top-right,
                // bottom-right, bottom-left.
                action(vertex++, col, row)
                action(vertex++, col + 1, row)
                action(vertex++, col, row + 1)
                action(vertex++, col + 1, row)
                action(vertex++, col + 1, row + 1)
                action(vertex++, col, row + 1)
            }
        }
    }
}

internal const val VERTICES_PER_QUAD = 6
