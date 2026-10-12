// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Face-local paths, parsed once; all inks resolve from the figure's palette when painted. */
internal class ArcadeSurface(val matrix: FaceMatrix, val paths: List<String>) {
    val painted: List<Path> = paths.map { PathParser().parsePathString(it).toPath() }

    companion object {
        fun marquee(): ArcadeSurface {
            val sc = 1.7
            val w = (7 * 8 - 3) * sc
            return ArcadeSurface(
                arcadeMatrix(Point3(0.0, 125.02, 338.0), Arcade.X, Point3(0.0, 0.0, -1.0)),
                listOf(ArcadeScreen.text("ANATOMY", -w / 2, -6.0, sc)),
            )
        }

        fun meter() =
            ArcadeSurface(
                arcadeMatrix(Point3(-58.02, 109.6, 18.4), Arcade.Y, Point3(0.0, 0.0, -1.0)),
                emptyList(),
            )

        fun fan(): ArcadeSurface {
            val blades =
                (0..6).joinToString("") { i ->
                    val a = i / 7.0 * PI * 2
                    fun pt(r: Double, da: Double) =
                        "${ArcadeScreen.n(r * cos(a + da))} ${ArcadeScreen.n(r * sin(a + da))}"
                    "M${pt(3.6, 0.0)}Q${pt(8.0, 0.25)} ${pt(11.0, 0.12)}L${pt(11.0, 0.62)}Q${pt(7.6, 0.72)} ${pt(3.6, 0.7)}Z"
                }
            val guard =
                listOf(11.6, 8.0, 4.6).joinToString("") { r ->
                    val n = ArcadeScreen.n(r)
                    "M$n 0A$n $n 0 1 1 ${ArcadeScreen.n(-r)} 0A$n $n 0 1 1 $n 0Z"
                }
            val spokes =
                (0..3).joinToString("") { i ->
                    val a = i * PI / 2 + 0.4
                    "M${ArcadeScreen.n(3 * cos(a))} ${ArcadeScreen.n(3 * sin(a))}L${ArcadeScreen.n(11.6 * cos(a))} ${ArcadeScreen.n(11.6 * sin(a))}"
                }
            return ArcadeSurface(
                arcadeMatrix(Point3(-18.0, 7.42, 262.0), Arcade.X, Point3(0.0, 0.0, -1.0)),
                listOf(blades, guard + spokes),
            )
        }
    }
}

internal fun arcadeMatrix(o: Point3, ex: Point3, ey: Point3): FaceMatrix {
    val p = Arcade.at(o)
    val x = Arcade.at(add3(o, ex))
    val y = Arcade.at(add3(o, ey))
    fun fixed(v: Double, places: Int) = String.format(Locale.ROOT, "%.${places}f", v).toDouble()
    return FaceMatrix(
        Flat(fixed(x.x - p.x, 4), fixed(x.y - p.y, 4)),
        Flat(fixed(y.x - p.x, 4), fixed(y.y - p.y, 4)),
        Flat(fixed(p.x, 2), fixed(p.y, 2)),
    )
}

internal fun FaceMatrix.composeMatrix(): Matrix =
    Matrix().apply {
        this[0, 0] = u.x.toFloat()
        this[0, 1] = u.y.toFloat()
        this[1, 0] = v.x.toFloat()
        this[1, 1] = v.y.toFloat()
        this[3, 0] = origin.x.toFloat()
        this[3, 1] = origin.y.toFloat()
    }
