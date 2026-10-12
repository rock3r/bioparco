// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.geometry.Rect
import dev.sebastiano.anatomy.figures.TurningDial.FACE_R
import dev.sebastiano.anatomy.figures.TurningDial.FACE_Z
import dev.sebastiano.anatomy.figures.TurningDial.HUB_R
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.SKSL_TURN
import dev.sebastiano.anatomy.kit.ShaderLayer
import dev.sebastiano.anatomy.kit.drawShader
import dev.sebastiano.anatomy.kit.glCamera
import dev.sebastiano.anatomy.kit.shaderArea
import dev.sebastiano.anatomy.kit.shaderLayer
import dev.sebastiano.anatomy.kit.skslPose
import dev.sebastiano.anatomy.kit.turn.TurnController
import dev.sebastiano.anatomy.kit.turn.cover
import dev.sebastiano.anatomy.kit.turn.poseUniforms
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The turning dial's light, `live.js` of the original: an LED on the lifting fingertip, the pool it
 * casts on the dial face (a back layer between the dial and the hand, following the dial with
 * `toDial`), and sparks when the pod breaks off (born at the socket, then integrated in the world).
 * The front layer is masked by the parts painted over the fingertip with `coverOf`.
 */

/** The LED's pool on the dial face, in the dial's rest-world coordinates. */
private val BACK =
    skslPose("Dial") +
        """
uniform float3 uLed;
uniform float3 uLedDir;
uniform float uOn;
const float FACE_Z=$FACE_Z;
const float FACE_R=$FACE_R;
const float HUB_R=$HUB_R;
half4 main(float2 vb){
  float3 p=onFloor(vb,FACE_Z);
  float3 q=toDial(p);
  float r=length(q.xy);
  float face=(1.-smoothstep(FACE_R-3.,FACE_R-1.,r))*smoothstep(HUB_R+.2,HUB_R+1.6,r);
  float3 v=p-uLed;
  float d2=dot(v,v);
  float d=sqrt(d2);
  float lens=.3+.7*pow(max(0.,dot(v/d,uLedDir)),1.5);
  float lambert=max(0.,(uLed.z-FACE_Z+.6)/d);
  float grain=.84+.16*noise2(float2(atan(q.y,q.x)*180.,r*.6));
  float e=420.*lens*lambert/(d2+10.)*grain*face;
  float lit=1.-exp(-e);
  float3 tint=float3(.40,.84,.60);
  float4 light=inkFor(mix(float3(1.),tint,.7*lit),0.);
  float3 glow=float3(.25,1.,.58)*lit*.55;
  float4 dark=float4(glow,max(glow.r,max(glow.g,glow.b)));
  return finish(mix(dark,light,uLight)*uOn,vb);
}
"""

/** The LED itself and the sparks, over the hand, under the parts that cover them. */
private val FRONT =
    SKSL_TURN +
        """
uniform float2 uLedVb;
uniform float uLedR;
uniform float uLedFace;
uniform float uOn;
uniform float4 uSpark[24];
uniform float uSparkHeat[24];
float4 sparkInk(float heat,float along,float young){
  float3 tail=mix(float3(.78,.2,.04),float3(1.,.55,.08),smoothstep(.35,.8,heat));
  float3 head=mix(tail,float3(1.,.74,.2),smoothstep(.55,.9,heat)*smoothstep(.5,1.,along));
  float3 seen=mix(head,float3(1.,.95,.82),young*smoothstep(.8,1.,along));
  float a=.88*smoothstep(0.,.3,along)*smoothstep(.12,.45,heat);
  float4 light=float4(seen*a,a);
  float3 hot=mix(float3(1.,.35,.05),float3(1.,.9,.6),young*along)*(.4+.6*heat);
  float4 dark=float4(hot*a,a);
  return mix(dark,light,uLight);
}
half4 main(float2 vb){
  float px=uView.x/uCanvas.x;
  float soft=.7*px;
  float d=length(vb-uLedVb);
  float body=1.-smoothstep(uLedR-.6*px,uLedR+.6*px,d);
  float core=1.-smoothstep(uLedR*.32,uLedR*.55+.5*px,d);
  float rim=smoothstep(uLedR*.55,uLedR,d);
  float3 seen=mix(mix(float3(.18,.70,.43),float3(.11,.52,.31),rim),float3(.90,1.,.94),core);
  float4 lightLed=inkFor(seen,.92)*body;
  float3 hot=mix(float3(.2,1.,.55),float3(.92,1.,.95),core)*body;
  float4 darkLed=float4(hot,body);
  float4 led=mix(darkLed,lightLed,uLight)*smoothstep(.02,.3,uLedFace)*uOn;
  float4 sparks=float4(0.);
  float w=max(.35+.6*px,1.25*px);
  for(int i=0;i<24;i++){
    float heat=uSparkHeat[i];
    if(heat<=0.)continue;
    float4 s=uSpark[i];
    float2 a=s.zw;
    float2 b=s.xy;
    float2 ab=b-a;
    float l2=max(dot(ab,ab),1e-6);
    float t=clamp(dot(vb-a,ab)/l2,0.,1.);
    float dist=length(vb-a-ab*t);
    float cover=1.-smoothstep(w*.5-.5*px,w*.5+.5*px,dist);
    if(cover<=0.)continue;
    sparks=over(sparkInk(heat,t,step(.65,heat))*cover,sparks);
  }
  float4 ink=over(sparks,led)*(1.-coverOf(vb,soft));
  return finish(ink,vb);
}
"""

private const val SPARKS = 24

/** One spark: position and velocity in the world, age and life in seconds. */
private class Spark(val p: DoubleArray, val v: DoubleArray, var age: Double, val life: Double)

/** The LED, its pool and the sparks: shader layers and the sparks' state. */
internal class TurningDialLight(private val scene: TurningDial.Scene) {
    private val back: ShaderLayer? = shaderLayer(BACK, TurningDial.WIDTH, TurningDial.HEIGHT, glCamera(TurningDial.P))
    private val front: ShaderLayer? =
        back?.let { shaderLayer(FRONT, TurningDial.WIDTH, TurningDial.HEIGHT, glCamera(TurningDial.P)) }

    /** True when both layers compiled: the original's `data-gl`. */
    val available: Boolean = back != null && front != null
    private val sparks = arrayOfNulls<Spark>(SPARKS)
    private val sparkData = FloatArray(SPARKS * 4)
    private val sparkHeat = FloatArray(SPARKS)
    private var seed = 7L

    private fun random(): Double {
        seed = (seed * 16807) % 2147483647
        return seed.toDouble() / 2147483647
    }

    /** Throws the sparks from the pod's socket, now. */
    fun ignite(controller: TurnController) {
        val at = controller.anchor("dial", scene.podAt).world
        for (i in 0 until SPARKS) {
            val a = random() * PI * 2
            val tilt = 0.35 + random() * 0.9
            val speed = 16 + random() * 22
            val v = doubleArrayOf(cos(a) * sin(tilt) * speed, sin(a) * sin(tilt) * speed, cos(tilt) * speed)
            sparks[i] = Spark(doubleArrayOf(at.x, at.y, at.z), v, -random() * 0.08, 0.3 + random() * 0.45)
        }
    }

    /** Moves the sparks on by [dt]; true while any is alive. */
    fun step(dt: Double): Boolean {
        var live = false
        for (i in 0 until SPARKS) {
            val s = sparks[i] ?: continue
            s.age += dt
            if (s.age < 0) {
                live = true
                continue
            }
            s.v[2] -= 98 * dt
            for (c in 0 until 3) s.p[c] += s.v[c] * dt
            if (s.age > s.life || s.p[2] < FACE_Z) sparks[i] = null else live = true
        }
        return live
    }

    /** Draws the pool on the dial face: call between the dial and hand layers. */
    fun IsoScope.drawPool(controller: TurnController, on: Double, time: Double) {
        val layer = back ?: return
        val led = scene.led
        val anchor = controller.anchor(led.group, led.at, led.normal)
        val pose = controller.poseUniforms("dial")
        drawShader(layer, time, POOL) {
            set("uLed", listOf(anchor.world.x, anchor.world.y, anchor.world.z))
            set("uLedDir", anchor.direction!!.let { listOf(it.x, it.y, it.z) })
            set("uOn", on)
            set("uDialX", pose.x)
            set("uDialY", pose.y)
            set("uDialZ", pose.z)
            set("uDialT", pose.t)
        }
    }

    /** Draws the LED and the sparks over the hand, masked by the parts in front of them. */
    fun IsoScope.drawLed(controller: TurnController, on: Double, time: Double) {
        val layer = front ?: return
        val led = scene.led
        val anchor = controller.anchor(led.group, led.at, led.normal)
        val world = controller.cams.world
        val d = anchor.direction!!
        val facing = d.x * world.v.x + d.y * world.v.y + d.z * world.v.z
        val sx = anchor.screen.x.toFloat()
        val sy = anchor.screen.y.toFloat()
        val box = floatArrayOf(sx - 4, sy - 4, sx + 4, sy + 4)
        val live = writeSparks(controller)
        for (i in 0 until SPARKS) {
            if (sparkHeat[i] <= 0f) continue
            for (k in 0 until 2) {
                val x = sparkData[4 * i + 2 * k]
                val y = sparkData[4 * i + 2 * k + 1]
                box[0] = min(box[0], x - 2)
                box[1] = min(box[1], y - 2)
                box[2] = max(box[2], x + 2)
                box[3] = max(box[3], y + 2)
            }
        }
        val area = Rect(box[0], box[1], box[2], box[3])
        val cover = controller.cover(if (live) listOf(listOf(led.part), listOf("hand.pod.socket")) else listOf(listOf(led.part)))
        drawShader(layer, time, area) {
            set("uLedVb", listOf(anchor.screen.x, anchor.screen.y))
            set("uLedR", 0.52 * world.k)
            set("uLedFace", facing)
            set("uOn", on)
            set("uSpark", sparkData)
            set("uSparkHeat", sparkHeat)
            set("uCoverEdge", cover.edge)
            set("uCoverCount", cover.count)
        }
    }

    /** The sparks on screen, head and tail, and their heat; true while any shows. */
    private fun writeSparks(controller: TurnController): Boolean {
        val w = controller.cams.world
        fun x(p: DoubleArray) = w.ox + w.m[0] * p[0] + w.m[1] * p[1] + w.m[2] * p[2]
        fun y(p: DoubleArray) = w.oy + w.m[3] * p[0] + w.m[4] * p[1] + w.m[5] * p[2]
        var live = false
        for (i in 0 until SPARKS) {
            val s = sparks[i]
            live = live || s != null
            if (s == null || s.age < 0) {
                sparkHeat[i] = 0f
                continue
            }
            val tail = DoubleArray(3) { s.p[it] - s.v[it] * 0.05 }
            sparkData[4 * i] = x(s.p).toFloat()
            sparkData[4 * i + 1] = y(s.p).toFloat()
            sparkData[4 * i + 2] = x(tail).toFloat()
            sparkData[4 * i + 3] = y(tail).toFloat()
            sparkHeat[i] = (1 - s.age / s.life).toFloat()
        }
        return live
    }

    private companion object {
        /** The dial face's screen box: everything the pool can light. */
        val POOL: Rect =
            shaderArea(
                (0 until 32).map { i ->
                    val a = i * PI / 16
                    Point3(FACE_R * cos(a), FACE_R * sin(a), FACE_Z)
                },
                TurningDial.P,
            )
    }
}
