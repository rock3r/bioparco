// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/*
 * The original's `kit/gl.mjs` for Compose: a shader layer drawn with a Skia runtime effect (SkSL)
 * inside the same `IsoCanvas`, between two `drawArt` calls, seen through the figure's own camera.
 * `SKSL_ISO` is `GLSL_ISO` with the same maths; `glCamera` and `shaderLayer` are `glCamera` and
 * `glLayer`, and `IsoScope.drawShader` is `layer.draw`. See references/shaders.md in the skill.
 */

/** The camera of a [Projection] as a shader sees it: the original's `glCamera(P)`. */
class ShaderCamera(
    /** Where the world origin lands, in viewBox units. */
    val origin: Flat,
    /** Screen units per world unit: `uK`. */
    val k: Double,
    /** `(sinA, cosA, sinE, cosE)` of the azimuth and elevation: `uCam`. */
    val cam: List<Double>,
)

/** The uniforms that make a shader see [projection]'s camera. */
fun glCamera(projection: Projection): ShaderCamera {
    val c = cameraOf(projection)
    return ShaderCamera(projection.origin, c.k, listOf(c.sinA, c.cosA, c.sinE, c.cosE))
}

/**
 * Prepended to every fragment by [shaderLayer]. The fragment's `half4 main(float2 vb)` receives the
 * viewBox point directly (the layer is drawn in viewBox units), and returns premultiplied colour.
 */
const val SKSL_ISO: String =
    """
uniform float2 uCanvas;
uniform float2 uView;
uniform float2 uOrigin;
uniform float uK;
uniform float4 uCam;
uniform float uTime;
uniform float uLight;
float2 deviceOf(float2 vb){return vb*(uCanvas.x/uView.x);}
float2 project(float3 p){return uOrigin+uK*float2(p.x*uCam.x-p.y*uCam.y,(p.x*uCam.y+p.y*uCam.x)*uCam.z-p.z*uCam.w);}
float3 towardViewer(){return float3(uCam.y*uCam.w,uCam.x*uCam.w,uCam.z);}
float3 rayOf(float2 vb){float2 q=(vb-uOrigin)/uK;return float3(q.x*uCam.x,-q.x*uCam.y,-q.y/uCam.w);}
float pixelOf(){return (uView.x/uCanvas.x)/uK;}
float3 lightDir(){return normalize(float3(-uCam.x,uCam.y,1.2));}
float3 onFloor(float2 vb,float h){float2 q=(vb-uOrigin)/uK;float b=(q.y+h*uCam.w)/uCam.z;return float3(q.x*uCam.x+b*uCam.y,-q.x*uCam.y+b*uCam.x,h);}
float3 onWallY(float2 vb,float y){float2 q=(vb-uOrigin)/uK;float x=(q.x+y*uCam.y)/uCam.x;return float3(x,y,((x*uCam.y+y*uCam.x)*uCam.z-q.y)/uCam.w);}
float3 onWallX(float2 vb,float x){float2 q=(vb-uOrigin)/uK;float y=(x*uCam.x-q.x)/uCam.y;return float3(x,y,((x*uCam.y+y*uCam.x)*uCam.z-q.y)/uCam.w);}
float hash12(float2 p){float3 q=fract(float3(p.xyx)*0.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
float hash13(float3 p){float3 q=fract(p*0.1031);q+=dot(q,q.zyx+31.32);return fract((q.x+q.y)*q.z);}
float noise2(float2 p){float2 i=floor(p);float2 f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash12(i),hash12(i+float2(1.,0.)),f.x),mix(hash12(i+float2(0.,1.)),hash12(i+float2(1.,1.)),f.x),f.y);}
float noise3(float3 p){float3 i=floor(p);float3 f=fract(p);f=f*f*(3.-2.*f);
  float a=mix(mix(hash13(i),hash13(i+float3(1.,0.,0.)),f.x),mix(hash13(i+float3(0.,1.,0.)),hash13(i+float3(1.,1.,0.)),f.x),f.y);
  float b=mix(mix(hash13(i+float3(0.,0.,1.)),hash13(i+float3(1.,0.,1.)),f.x),mix(hash13(i+float3(0.,1.,1.)),hash13(i+float3(1.,1.,1.)),f.x),f.y);
  return mix(a,b,f.z);}
float fbm2(float2 p){float s=0.;float a=.5;float2 q=p;for(int i=0;i<4;i++){s+=a*noise2(q);q=q*2.07+float2(17.1,9.2);a*=.5;}return s/.9375;}
float fbm3(float3 p){float s=0.;float a=.5;float3 q=p;for(int i=0;i<4;i++){s+=a*noise3(q);q=q*2.07+float3(17.1,9.2,4.7);a*=.5;}return s/.9375;}
float window(float x,float a,float b,float soft){return smoothstep(a-soft,a+soft,x)*(1.-smoothstep(b-soft,b+soft,x));}
float3 tonemap(float3 c,float exposure){return 1.-exp(-c*exposure);}
float4 over(float4 top,float4 under){return top+under*(1.-top.a);}
float4 inkFor(float3 seen,float cover){float3 absorbed=1.-seen;float a=max(max(absorbed.r,max(absorbed.g,absorbed.b)),cover);return float4(float3(a)-absorbed,a);}
half4 finish(float4 c,float2 vb){
  float a=clamp(c.a,0.,1.);
  float3 rgb=clamp(c.rgb,0.,a);
  float2 d=deviceOf(vb);
  float n=(hash12(d+fract(uTime*.61)*91.)+hash12(d.yx*1.37+fract(uTime*.37)*57.)-1.)/255.;
  float g=n*smoothstep(0.,1.5/255.,max(a,max(rgb.r,max(rgb.g,rgb.b))));
  a=clamp(a+g,0.,1.);
  return half4(clamp(rgb+g,0.,a),a);
}
"""

/**
 * The original's `GLSL_TURN`, for a fragment that follows a turning figure: `uGroupX/Y/Z/T` with
 * `toGroup(p)` (a world point in the group's rest-world coordinates), `toWorld`, `dirToGroup` and
 * `dirToWorld`, set from `TurnController.poseUniforms`; and the cover masks `uCoverEdge[60]` and
 * `uCoverCount[6]` with `coverOf(vb, soft)` (1 where a part painted over the target covers the
 * point), set from `TurnController.cover`. Prepend it to the fragment. It defines `toWorld`: don't
 * prepend it to a fragment that declares its own.
 */
const val SKSL_TURN: String =
    """
uniform float3 uGroupX;
uniform float3 uGroupY;
uniform float3 uGroupZ;
uniform float3 uGroupT;
uniform float3 uCoverEdge[60];
uniform float uCoverCount[6];
float3 toGroup(float3 p){float3 q=p-uGroupT;return float3(dot(q,uGroupX),dot(q,uGroupY),dot(q,uGroupZ));}
float3 toWorld(float3 g){return uGroupT+g.x*uGroupX+g.y*uGroupY+g.z*uGroupZ;}
float3 dirToGroup(float3 d){return float3(dot(d,uGroupX),dot(d,uGroupY),dot(d,uGroupZ));}
float3 dirToWorld(float3 d){return d.x*uGroupX+d.y*uGroupY+d.z*uGroupZ;}
float coverOf(float2 vb,float soft){float c=0.;for(int p=0;p<6;p++){float n=uCoverCount[p];float inside=-1e4;for(int e=0;e<10;e++){if(float(e)>=n)break;float3 h=uCoverEdge[p*10+e];inside=max(inside,h.x*vb.x+h.y*vb.y+h.z);}if(n>0.)c=max(c,1.-smoothstep(-soft,soft,inside));}return c;}
"""

/**
 * The original's `glslPose(Name, prefix)`: `to<Name>`, `from<Name>`, `dirTo<Name>` and
 * `dirFrom<Name>` for one group, from the uniforms `<prefix>X/Y/Z/T`, so one fragment can follow
 * several groups.
 */
fun skslPose(name: String, prefix: String = "u$name"): String =
    """
uniform float3 ${prefix}X;uniform float3 ${prefix}Y;uniform float3 ${prefix}Z;uniform float3 ${prefix}T;
float3 to$name(float3 p){float3 q=p-${prefix}T;return float3(dot(q,${prefix}X),dot(q,${prefix}Y),dot(q,${prefix}Z));}
float3 from$name(float3 g){return ${prefix}T+g.x*${prefix}X+g.y*${prefix}Y+g.z*${prefix}Z;}
float3 dirTo$name(float3 d){return float3(dot(d,${prefix}X),dot(d,${prefix}Y),dot(d,${prefix}Z));}
float3 dirFrom$name(float3 d){return d.x*${prefix}X+d.y*${prefix}Y+d.z*${prefix}Z;}
"""

/**
 * A compiled shader layer: the original's `glLayer`. Make one with [shaderLayer], keep it for the
 * figure's lifetime, and draw it each frame with [drawShader] between the back and front art.
 */
class ShaderLayer
internal constructor(
    effect: RuntimeEffect,
    /** The viewBox size the layer covers: the figure's `WIDTH` and `HEIGHT`. */
    val width: Double,
    val height: Double,
    val camera: ShaderCamera,
    fixed: Map<String, List<Double>>,
) {
    private val builder = RuntimeShaderBuilder(effect)
    private val paint = Paint().apply { isAntiAlias = false }

    init {
        for ((name, value) in fixed) set(name, value)
    }

    /** Sets a float uniform. */
    fun set(name: String, value: Double) = builder.uniform(name, value.toFloat())

    /** Sets a `float2`–`float4` uniform, or a `float[N]` array for longer lists. */
    fun set(name: String, value: List<Double>) {
        val v = value.map { it.toFloat() }
        when (v.size) {
            1 -> builder.uniform(name, v[0])
            2 -> builder.uniform(name, v[0], v[1])
            3 -> builder.uniform(name, v[0], v[1], v[2])
            4 -> builder.uniform(name, v[0], v[1], v[2], v[3])
            else -> builder.uniform(name, v.toFloatArray())
        }
    }

    /** Sets a uniform array of any type from its floats: `float3 uCoverEdge[60]` takes 180. */
    fun set(name: String, values: FloatArray) = builder.uniform(name, values)

    /** This frame's shader at [unit] canvas px per viewBox unit, in the light or dark theme. */
    internal fun shaderAt(unit: Double, dark: Boolean, time: Double): Shader {
        val c = camera
        builder.uniform("uCanvas", (width * unit).toFloat(), (height * unit).toFloat())
        builder.uniform("uView", width.toFloat(), height.toFloat())
        builder.uniform("uOrigin", c.origin.x.toFloat(), c.origin.y.toFloat())
        builder.uniform("uK", c.k.toFloat())
        val (sinA, cosA, sinE) = c.cam
        builder.uniform("uCam", sinA.toFloat(), cosA.toFloat(), sinE.toFloat(), c.cam[3].toFloat())
        builder.uniform("uTime", time.toFloat())
        builder.uniform("uLight", if (dark) 0f else 1f)
        return builder.makeShader()
    }

    internal fun paintFor(scope: IsoScope, time: Double): Paint {
        val dark = scope.palette.isDark
        paint.shader?.close()
        paint.shader = shaderAt(scope.unit.toDouble(), dark, time)
        // Dark: the layer only emits light, so it is screen-blended and can only brighten.
        // Light: ink on paper, normal premultiplied blending (see shaders.md, "Light theme").
        paint.blendMode = if (dark) BlendMode.SCREEN else BlendMode.SRC_OVER
        return paint
    }
}

/**
 * Compiles `SKSL_ISO + fragment` for a [width] by [height] viewBox seen through [camera]. [fixed]
 * uniforms are set once (geometry, constants). Returns null, after telling [onError], when the
 * shader does not compile: guard every use, and keep the art readable without it.
 */
fun shaderLayer(
    fragment: String,
    width: Double,
    height: Double,
    camera: ShaderCamera,
    fixed: Map<String, List<Double>> = emptyMap(),
    onError: (String) -> Unit = { System.err.println(it) },
): ShaderLayer? =
// Skia reports a compile error as a plain RuntimeException carrying the SkSL error log.
runCatching {
    ShaderLayer(RuntimeEffect.makeForShader(SKSL_ISO + fragment), width, height, camera, fixed)
}
    .onFailure { onError(it.message ?: "the shader did not compile") }
    .getOrNull()

/**
 * Draws [layer] now, at [time] seconds (`uTime`), over [area] in viewBox units (the whole viewBox
 * by default). [values] sets this frame's uniforms. Keep [area] around everything the effect can
 * light, with its soft tails inside it: [shaderArea] makes one from world points.
 */
fun IsoScope.drawShader(
    layer: ShaderLayer,
    time: Double,
    area: Rect = Rect(0f, 0f, layer.width.toFloat(), layer.height.toFloat()),
    values: ShaderLayer.() -> Unit = {},
) {
    if (ShaderFrames.skip) return
    layer.values()
    val paint = layer.paintFor(this, time)
    draw.drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(
            org.jetbrains.skia.Rect.makeLTRB(area.left, area.top, area.right, area.bottom),
            paint,
        )
    }
}

/**
 * Offscreen captures render every frame up to a shot on the CPU, where a full-canvas shader costs
 * seconds a frame; `capture` sets [skip] on the frames it doesn't keep, so only the shots pay.
 */
object ShaderFrames {
    @Volatile var skip = false
}

/** The screen box, in viewBox units, around [points] seen through [projection], grown by [pad]. */
fun shaderArea(points: List<Point3>, projection: Projection, pad: Double = 2.0): Rect {
    val flat = points.map { iso(it, projection) }
    return Rect(
        (flat.minOf { it.x } - pad).toFloat(),
        (flat.minOf { it.y } - pad).toFloat(),
        (flat.maxOf { it.x } + pad).toFloat(),
        (flat.maxOf { it.y } + pad).toFloat(),
    )
}
