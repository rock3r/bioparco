package dev.sebastiano.anatomy.figures

import androidx.compose.ui.geometry.Rect
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.ShaderLayer
import dev.sebastiano.anatomy.kit.drawShader
import dev.sebastiano.anatomy.kit.glCamera
import dev.sebastiano.anatomy.kit.shaderArea
import dev.sebastiano.anatomy.kit.shaderLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The flame, as a volume in the burner's own camera. The burner's axis is the world z axis, so
 * `rayOf(vb)` is where each pixel's ray passes closest to it: from there the ray runs along
 * `towardViewer()`, its distance from the axis grows as `t·cosE` and its height as `t·sinE`. Twenty
 * dithered samples along that chord gather three kinds of light, each with its own ink:
 *
 * - soot glowing yellow in the upper body of a diffusion flame (`uSoot`);
 * - the faint blue-violet mantle of the outer flame, and the blue root of a yellow one;
 * - the inner premixed cone, a thin bright blue sheet from the bore's edge to its tip (`uBlue`).
 *
 * Everything reaches zero inside `uReach` of the axis and `uTop` above the rim, so the layer's
 * bounding box is only an optimisation.
 */
object BunsenFlame {
    /** How far from the axis, and how high above the rim, any light can reach. */
    private const val REACH = 22.0
    private const val TOP = 96.0

    private const val FRAGMENT =
        """
uniform float uH;
uniform float uCone;
uniform float uSoot;
uniform float uBlue;
uniform float uRoar;
uniform float uLive;
uniform float uRim;
uniform float uBore;
uniform float uReach;
uniform float uTop;

float3 paperSoot(float e){
  float3 c=mix(float3(1.),float3(1.,.80,.38),smoothstep(0.,.3,e));
  c=mix(c,float3(1.,.70,.12),smoothstep(.22,.6,e));
  return mix(c,float3(1.,.93,.66),smoothstep(.74,.97,e));
}

float3 paperCone(float e){
  float3 c=mix(float3(1.),float3(.20,.44,.98),smoothstep(0.,.55,e));
  return mix(c,float3(.78,.92,1.),smoothstep(.75,1.,e));
}

float coneLight(float3 p0,float3 V,float u,float jitter){
  if(uBlue<.001||u>uBore+1.){return 0.;}
  float reach=(uBore+1.)/uCam.w;
  float dt=2.*reach/24.;
  float sum=0.;
  for(int i=0;i<24;i++){
    float t=-reach+(float(i)+jitter)*dt;
    float3 p=p0+t*V;
    float h=p.z-uRim;
    float r=length(p.xy);
    float rc=uBore*max(1.-h/uCone,0.);
    float sheet=exp(-pow((r-rc)/.5,2.));
    float inside=.12*(1.-smoothstep(rc-.5,rc+.5,r));
    sum+=(sheet+inside)*smoothstep(.1,1.,h)*(1.-smoothstep(uCone*.92,uCone*1.03,h));
  }
  return uBlue*sum*dt*uCam.w;
}

half4 main(float2 vb){
  float3 p0=rayOf(vb);
  float u=length(p0.xy);
  float sE=uCam.z;
  float cE=uCam.w;
  float bulge=mix(.35,1.1,uSoot);
  float swing=mix(4.5,1.25,uRoar);
  // Nothing reaches past 1.8 body radii (the mantle's tail over the edge noise) plus the sway.
  float rmax=(uBore*1.06*(1.+bulge)+.5)*1.8;
  float wide=min(uReach,rmax+1.8*swing+.5);
  float reach=wide/cE;
  float zLow=p0.z-reach*sE-uRim;
  float zHigh=p0.z+reach*sE-uRim;
  if(u>wide||zHigh<0.||zLow>min(uTop,uH*1.16+2.)){return half4(0.);}
  float3 V=towardViewer();
  float lazy=1.-uRoar;
  float H=uH*(1.+.05*lazy*(noise2(float2(uTime*2.3,7.1))-.5));
  float onset=mix(.5,.05,uSoot);
  float jitter=hash12(deviceOf(vb)+fract(uTime*.37)*41.);
  float dt=2.*reach/20.;
  float soot=0.;
  float mantle=0.;
  for(int i=0;i<20;i++){
    float t=-reach+(float(i)+jitter)*dt;
    float3 p=p0+t*V;
    float h=p.z-uRim;
    float zeta=h/H;
    float2 sway=float2(noise2(float2(h*.045-uTime*1.7,1.3)),noise2(float2(h*.045-uTime*1.7,8.9)))-.5;
    float2 xy=p.xy-sway*2.*swing*zeta*zeta;
    float r=length(xy);
    if(h<0.||zeta>1.12||r>rmax){continue;}
    float edge=(noise3(float3(xy*.22,h*.09-uTime*mix(2.2,5.5,uRoar)))-.5)*mix(.5,.35,uRoar)*smoothstep(.1,.6,zeta);
    float z1=clamp(zeta,0.,1.);
    float rf=uBore*1.06*pow(max(1.-z1,0.),.55)*(1.+bulge*smoothstep(0.,.35,z1))+.5;
    float rr=r/rf*(1.+edge);
    float root=smoothstep(.2,1.4,h);
    float tip=1.-smoothstep(.9,1.04+edge*.3,zeta);
    float bound=(1.-smoothstep(uReach-4.,uReach-1.,r))*(1.-smoothstep(uTop-10.,uTop-2.,h));
    float core=(1.-smoothstep(.55,1.,rr))*root*tip*bound;
    float axis=1.3-.6*smoothstep(0.,1.,rr);
    soot+=uSoot*core*axis*smoothstep(onset,onset+.25,zeta);
    float shell=exp(-pow((rr-.82)/.2,2.))*root*tip*bound;
    float foot=(1.-smoothstep(onset+.03,onset+.25,zeta))*core;
    mantle+=shell*mix(.35,1.,1.-uSoot)+foot*uSoot*1.4;
  }
  float ds=dt*cE;
  soot*=ds;
  mantle*=ds;
  float cone=coneLight(p0,V,u,jitter);
  float3 glow=float3(1.,.55,.14)*soot*.17+float3(.30,.36,1.)*mantle*.05+float3(.30,.62,1.)*cone*.55;
  float3 lit=tonemap(glow,1.15);
  float4 dark=float4(lit,max(lit.r,max(lit.g,lit.b)));
  float eS=1.-exp(-soot*.17);
  float eM=1.-exp(-mantle*.06);
  float eC=1.-exp(-cone*.9);
  float4 sootInk=inkFor(paperSoot(eS),.7*smoothstep(.72,.97,eS));
  float4 mantleInk=inkFor(mix(float3(1.),float3(.62,.70,1.),eM*.75),0.);
  float4 coneInk=inkFor(paperCone(eC),0.);
  float4 light=over(coneInk,over(sootInk,mantleInk));
  return finish(mix(dark,light,uLight)*uLive,vb);
}
"""

    fun layer(): ShaderLayer? {
        val d = BunsenBench
        return shaderLayer(
            FRAGMENT,
            d.WIDTH,
            d.HEIGHT,
            glCamera(d.P),
            fixed =
                mapOf(
                    "uRim" to listOf(d.RIM),
                    "uBore" to listOf(d.BORE_R),
                    "uReach" to listOf(REACH),
                    "uTop" to listOf(TOP),
                ),
        )
    }

    /** The screen box the flame can light: a cylinder [REACH] round the axis, [TOP] tall. */
    val AREA: Rect =
        shaderArea(
            (0 until 16).flatMap { index ->
                val angle = index * PI / 8
                listOf(0.0, TOP).map { h ->
                    Point3(REACH * cos(angle), REACH * sin(angle), BunsenBench.RIM + h)
                }
            },
            BunsenBench.P,
        )

    /** Draws the flame for a collar [opening] (0 to 1), at [time] seconds, faded in by [live]. */
    fun IsoScope.drawFlame(layer: ShaderLayer, opening: Double, time: Double, live: Double = 1.0) {
        drawShader(layer, time, AREA) {
            set("uH", Bunsen.flameHeight(opening))
            set("uCone", Bunsen.coneHeight(opening))
            set("uSoot", Bunsen.sootiness(opening))
            set("uBlue", Bunsen.coneStrength(opening))
            set("uRoar", Bunsen.aeration(opening) / Bunsen.OPEN_AERATION)
            set("uLive", live)
        }
    }
}
