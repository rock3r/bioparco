package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.glCamera
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.shaderLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

/**
 * The shader sees the same camera as the art: `SKSL_ISO`'s helpers, evaluated by Skia on a 1 × 1
 * raster surface, agree with the Kotlin `iso()` and `depthOf()` for several cameras and points.
 * Each check is an SkSL expression for an error, which the shader compares with a tolerance.
 */
class ShaderCameraTest {
    private val cameras =
        listOf(
            Projection(Flat(300.0, 190.0), 1.37),
            Projection(Flat(250.0, 260.0), 0.82, azimuth = 57.0),
            Projection(Flat(320.0, 140.0), 2.1, azimuth = 35.0, elevation = 44.0),
        )

    private val points =
        listOf(
            Point3(0.0, 0.0, 0.0),
            Point3(42.0, -17.5, 9.0),
            Point3(-60.0, 33.0, 120.0),
            Point3(12.25, 80.0, -15.0),
        )

    /** True when the SkSL [error] expression is below [tolerance] for world point [p]. */
    private fun holds(
        expression: String,
        projection: Projection,
        p: Point3,
        tolerance: Double,
    ): Boolean {
        val fragment =
            """
            uniform float3 uP;
            uniform float2 uVb;
            uniform float uExpect;
            uniform float uTol;
            half4 main(float2 c){
              float e=$expression;
              return e<uTol?half4(1.,1.,1.,1.):half4(0.,0.,0.,1.);
            }
            """
        val layer =
            shaderLayer(fragment, 600.0, 380.0, glCamera(projection), onError = { error(it) })
        assertNotNull(layer)
        val vb = iso(p, projection)
        layer.set("uP", listOf(p.x, p.y, p.z))
        layer.set("uVb", listOf(vb.x, vb.y))
        layer.set("uExpect", depthOf(p, projection))
        layer.set("uTol", tolerance)
        val surface = Surface.makeRasterN32Premul(1, 1)
        surface.canvas.drawPaint(Paint().apply { shader = layer.shaderAt(2.0, false, 0.0) })
        val bitmap = Bitmap.makeFromImage(surface.makeImageSnapshot())
        return Color.getR(bitmap.getColor(0, 0)) == 255
    }

    private fun assertEverywhere(name: String, error: String, tolerance: Double) {
        for (projection in cameras) {
            for (p in points) {
                assertEquals(
                    true,
                    holds(error, projection, p, tolerance),
                    "$name at $p, $projection",
                )
            }
        }
    }

    @Test
    fun `project lands where iso does`() =
        assertEverywhere("project", "length(project(uP)-uVb)", 2e-3)

    @Test
    fun `the check is sharp enough to fail a hundredth of a unit off`() {
        val projection = cameras.first()
        val p = points[1]
        assertEquals(false, holds("length(project(uP)-uVb-float2(.01,0.))", projection, p, 2e-3))
    }

    @Test
    fun `onFloor and the walls invert iso`() {
        assertEverywhere("onFloor", "length(onFloor(uVb,uP.z)-uP)", 2e-3)
        assertEverywhere("onWallX", "length(onWallX(uVb,uP.x)-uP)", 2e-3)
        assertEverywhere("onWallY", "length(onWallY(uVb,uP.y)-uP)", 2e-3)
    }

    @Test
    fun `every point along towardViewer lands on the same pixel`() {
        assertEverywhere("ray", "length(project(rayOf(uVb)+37.*towardViewer())-uVb)", 2e-3)
        assertEverywhere("view", "length(project(uP-55.*towardViewer())-uVb)", 2e-3)
    }

    @Test
    fun `towardViewer measures depthOf`() =
        assertEverywhere("depth", "abs(dot(uP,towardViewer())-uExpect)", 2e-3)

    @Test
    fun `pixelOf is world units per device pixel`() =
        // At 2 device px per viewBox unit: half a viewBox unit, over the camera's k.
        assertEverywhere("pixelOf", "abs(pixelOf()*uK*2.-1.)", 1e-5)
}
