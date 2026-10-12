// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

/*
 * The Raptor's two shader layers, the original live.js's GLSL in SkSL on the kit's `SKSL_ISO`
 * camera: the plume layer between the stand and the engine (exhaust, Mach diamonds, the main-stage
 * flash and shock, the splash on the deck, the steam and the shutdown pop) and the veil over the
 * engine (the steam and the shock where they pass in front of it). Both work in world space: each
 * pixel's ray is marched against the cone, the deck, the deluge ring, the columns and the engine's
 * silhouette, so the light falls behind and in front of the right parts. `inkOf` turns light into
 * ink on paper in the light theme.
 */
internal object RaptorShaders {
    /** Shared by both layers: the uniforms both set, the scene's ray tests, steam and shock. */
    private const val COMMON =
        """
uniform float2 uJit;
uniform float uPc;
uniform float uHue;
uniform float uSmoke;
uniform float uVent;
uniform float uShockR;
uniform float uShockA;
const float ZE=172.0;
const float R0=64.6;
const float PL=140.0;
const float RB=132.;
const float2 COL0=float2(-124.0,124.0);
const float2 COL1=float2(124.0,-124.0);
const float COLH=12.4;
const float COLTOP=533.0;
float ground(float r){
  if(r>56.)return 0.;
  if(r>48.)return 4.;
  if(r>43.)return mix(4.,8.,(48.-r)/5.);
  if(r>35.)return mix(8.,13.,(43.-r)/8.);
  if(r>25.)return mix(13.,19.,(35.-r)/10.);
  if(r>14.5)return mix(19.,25.,(25.-r)/10.5);
  if(r>6.)return mix(25.,29.5,(14.5-r)/8.5);
  if(r>2.)return mix(29.5,32.,(6.-r)/4.);
  return 32.;
}
bool solid(float3 p){return p.z<ground(length(p.xy));}
float ringField(float3 p){return length(float2(length(p.xy)-68.,p.z-6.4))-2.1;}
float2 groundHit(float3 o,float3 V){
  float tf=-o.z/V.z;
  float best=tf;
  if(length(o.xy)<56.5){
    float ta=(36.-o.z)/V.z;
    float prev=ta;
    for(int i=1;i<=28;i++){
      float t=mix(ta,tf,float(i)/28.);
      if(solid(o+t*V)){
        float lo=t;float hi=prev;
        for(int j=0;j<7;j++){float m=(lo+hi)*.5;if(solid(o+m*V))lo=m;else hi=m;}
        best=hi;
        break;
      }
      prev=t;
    }
  }
  float t6=(6.4-o.z)/V.z;
  if(abs(length((o+t6*V).xy)-68.)<12.){
    float t=(9.-o.z)/V.z;
    for(int i=0;i<14;i++){
      float d=ringField(o+t*V);
      if(d<.02)break;
      t-=d;
    }
    if(ringField(o+t*V)<.06&&t>best)return float2(t,1.);
  }
  return float2(best,0.);
}
float columnNear(float3 o,float3 V,float2 c){
  float3 lo=float3(c-COLH,0.);
  float3 hi=float3(c+COLH,COLTOP);
  float3 a=(lo-o)/V;
  float3 b=(hi-o)/V;
  float3 mn=min(a,b);
  float3 mx=max(a,b);
  float tIn=max(mn.x,max(mn.y,mn.z));
  float tOut=min(mx.x,min(mx.y,mx.z));
  return tOut>tIn?tOut:-1e9;
}
float columnsNear(float3 o,float3 V){return max(columnNear(o,V,COL0),columnNear(o,V,COL1));}
float engineR(float z){
  float s=z-ZE;
  float b=36.+29.*(1.-pow(clamp(s,0.,120.)/120.,1.6))+3.;
  return mix(b,48.,smoothstep(108.,124.,s))*smoothstep(-6.,0.,s)*(1.-smoothstep(318.,330.,s));
}
float inFront(float3 p,float3 V){
  float2 h=V.xy;
  float hl=length(h);
  float tau=-dot(p.xy,h)/(hl*hl);
  float d=length(p.xy+tau*h);
  float re=engineR(p.z+tau*V.z);
  float inside=1.-smoothstep(re-3.,re+3.,d);
  float surface=tau*hl+sqrt(max(re*re-d*d,0.));
  return inside*smoothstep(0.,5.,-surface);
}
float side(float front,float veil){return mix(1.-front,front,veil);}
float beyond(float t0,float tc,float sigma){return 1./(1.+exp(clamp(2.41*(t0-tc)/sigma,-30.,30.)));}
float3 steamOf(float3 g0,float3 V,float tA,float u,float z0,float pf,float T,float veil){
  if(uSmoke<.0004&&uVent<.0004)return float3(0.);
  float sE=uCam.z;
  float Hs=70.+60.*uSmoke;
  float3 start=g0+tA*V;
  float steam=0.;
  float vapour=0.;
  for(int i=0;i<6;i++){
    float w=(float(i)+.5)/6.;
    float3 p=start+(-Hs*log(w)/sE)*V;
    float r=length(p.xy);
    float lay=side(inFront(p,V),veil);
    float box=1.-smoothstep(120.,178.,max(abs(p.x),abs(p.y)));
    steam+=box*(.4+.6*exp(-pow((r-90.)/80.,2.)))*(1.-smoothstep(170.,330.,p.z))*lay;
    vapour+=exp(-pow(r/96.,2.))*(1.-smoothstep(40.,140.,p.z))*lay;
  }
  float layer=exp(-max(start.z,0.)/Hs)/6.;
  float b1=fbm2(float2(u*.018+T*.03,z0*.016-T*.26+7.));
  float b2=fbm2(float2(u*.045-T*.05,z0*.04-T*.5+2.));
  float3 lit=mix(float3(.3,.3,.33),float3(.72,.4,.2),pf*exp(-max(z0,0.)/150.));
  return lit*(b1*.75+b2*.4)*smoothstep(.05,.6,b1)*steam*layer*uSmoke*1.05+float3(.42,.47,.54)*(b2*.6+.4)*vapour*layer*uVent*.5;
}
float shellOf(float dist,float tc,float tAir,float rs,float w,float3 e0,float3 V,float veil){
  if(rs<.5)return 0.;
  float m=sqrt(max(rs*rs-dist*dist,0.));
  float edge=exp(-pow(max(dist-rs,0.)/w,2.));
  float limb=rs*w/sqrt(m*m+2.*rs*w)/sqrt(rs*w*.5);
  limb*=limb;
  float soft=w*2.+2.;
  float far=tc-m;
  float near=tc+m;
  float aFar=smoothstep(tAir-soft*.5,tAir+soft,far)*side(inFront(e0+far*V,V),veil);
  float aNear=smoothstep(tAir-soft*.5,tAir+soft,near)*side(inFront(e0+near*V,V),veil);
  return edge*limb*(aFar+aNear)*.5;
}
float3 shockOf(float dist,float tc,float tAir,float3 e0,float3 V,float veil){
  if(uShockA<.0004)return float3(0.);
  float rs=uShockR;
  float sw=1.8+max(rs-26.,0.)*.01;
  float lead=shellOf(dist,tc,tAir,rs,sw,e0,V,veil);
  float trail=shellOf(dist,tc,tAir,max(rs-sw*2.6,0.),sw*2.2,e0,V,veil)*.3;
  return float3(.84,.88,1.)*(lead+trail)*.42*uShockA*(1.-smoothstep(170.,270.,rs));
}
half4 inkOf(float3 col,float2 vb){
  col=tonemap(col,1.15);
  float m=max(col.r,max(col.g,col.b));
  float n=(hash12(deviceOf(vb)+fract(uTime*.61)*91.)+hash12(deviceOf(vb).yx*1.37+fract(uTime*.37)*57.)-1.)/255.;
  col=max(col+n*smoothstep(0.,1.5/255.,m),0.);
  m=max(col.r,max(col.g,col.b));
  float3 ink=col/max(m,1e-5)*float3(.62,.34,.3);
  float4 light=float4(ink*m*.92,m*.92);
  return half4(mix(float4(col,m),light,uLight));
}
struct Scene{float3 V;float3 g0;float3 e0;float3 hit;float tLow;float ring;float tCol;float tAir;float tAirE;float tLowE;float u;float z0;float s0;float dist;float tc;};
Scene sceneOf(float2 vb){
  Scene S;
  S.V=towardViewer();
  float sE=uCam.z;
  S.g0=rayOf(vb);
  float2 gh=groundHit(S.g0,S.V);
  S.tLow=gh.x;
  S.ring=gh.y;
  S.tCol=columnsNear(S.g0,S.V);
  S.tAir=max(S.tLow,S.tCol);
  S.hit=S.g0+S.tLow*S.V;
  S.e0=rayOf(vb-uJit);
  S.u=(vb.x-uJit.x-uOrigin.x)/uK;
  S.z0=S.e0.z;
  S.s0=ZE-S.z0;
  S.tLowE=S.tLow+(S.g0.z-S.e0.z)/sE;
  S.tAirE=S.tAir+(S.g0.z-S.e0.z)/sE;
  float3 C=float3(0.,0.,ZE-6.);
  S.tc=dot(C-S.e0,S.V);
  S.dist=length(S.e0+S.tc*S.V-C);
  return S;
}
"""

    /** The plume layer, under the engine. */
    const val PLUME: String =
        COMMON +
            """
uniform float uGlow;
uniform float uPre;
uniform float uReach;
uniform float uSplash;
uniform float uFlash;
uniform float uRingR;
uniform float uRingA;
uniform float uPop;
uniform float uPopR;
float onDeck(float2 p){return 1.-smoothstep(134.,150.,max(abs(p.x),abs(p.y)));}
float radiusAt(float s,float pf,float warm){
  float spread=.045+.12*(1.-pf);
  return R0*(1.-.07*pf*smoothstep(0.,40.,s)-.42*warm*smoothstep(0.,80.,s))+spread*max(s,0.);
}
half4 main(float2 vb){
  Scene S=sceneOf(vb);
  float3 V=S.V;
  float sE=uCam.z;
  float cE=uCam.w;
  float tanE=sE/cE;
  float T=uTime;
  float pc=clamp(uPc,0.,1.);
  float pf=pc;
  float warm=1.-clamp(uHue,0.,1.);
  float glow=uGlow;
  float dith=hash12(deviceOf(vb));
  float3 col=float3(0.);
  float3 g0=S.g0;
  float3 e0=S.e0;
  float u=S.u;
  float z0=S.z0;
  float s0=S.s0;
  float3 hit=S.hit;
  float rho=length(hit.xy);
  float2 dirH=hit.xy/max(rho,1e-3);

  float tongue=fbm2(float2(u*.07,s0*.06-T*8.));
  float reach=uReach*(1.+(tongue-.5)*.55*warm);
  float rag=1.+1.6*warm;
  float lam=29.+7.*pf;
  float v0=19.+6.*pf;
  float cellOn=smoothstep(.4,.95,pf);

  if((glow>.0004||uVent>.0004)&&abs(u)<RB){
    float e=fbm2(float2(u*.06+7.,s0*.03-T*4.6));
    float e2=fbm2(float2(u*.16-3.,s0*.08-T*7.3));
    float streak=.86+.3*fbm2(float2(u*.22,s0*.012-T*9.));
    float flick=(.94+.06*sin(T*97.+s0*.23))*(1.+uPre*warm*(tongue-.45)*.9);
    float mist=fbm2(float2(u*.035+.4*T,s0*.022-T*.7));
    float halfChord=sqrt(max(RB*RB-u*u,0.))/cE;
    float ta=max(S.tAirE,-halfChord);
    float tb=min((ZE+2.-z0)/sE,halfChord);
    float dt=max(tb-ta,0.)/28.;
    float3 sum=float3(0.);
    float3 cold=float3(0.);
    for(int i=0;i<28;i++){
      float t=ta+(float(i)+dith)*dt;
      float3 p=e0+t*V;
      float s=ZE-p.z;
      float r=length(p.xy);
      float keep=(1.-smoothstep(RB*.8,RB,r))*smoothstep(-.5,4.,s);
      float Rv=radiusAt(s,pf,warm);
      float Re=Rv*(1.+(e-.5)*.42*rag*smoothstep(4.,120.,s)+(e2-.5)*.1*rag*smoothstep(0.,40.,s));
      float rn=r/Re;
      float fade=(1.-smoothstep(reach-46.,reach,s))*keep;
      float3 violet=mix(float3(.86,.44,1.),float3(1.,.5,.62),smoothstep(0.,150.,s)*.7);
      float3 ember=mix(float3(1.,.36,.07),float3(1.,.7,.32),tongue);
      float3 bodyCol=mix(violet,ember,warm);
      float fill=smoothstep(1.06,.72,rn);
      float wBody=dt*cE/(2.*Re);
      sum+=bodyCol*fill*wBody*(.26+.3*exp(-max(s,0.)/110.))*streak*flick*fade*2.;
      sum+=bodyCol*exp(-rn*rn*2.)*(dt*cE/(Re*1.25))*.22*fade;
      float rc=Rv*.34;
      sum+=mix(float3(1.,.74,.9),float3(1.,.8,.45),warm)*exp(-(r*r)/(rc*rc))*(dt*cE/(rc*1.772))*.34*(1.-.5*smoothstep(PL-30.,PL+10.,s))*fade;
      float rb=Rv*.62;
      sum+=mix(float3(1.,.9,.76),float3(1.,.78,.46),warm)*exp(-(r*r)/(rb*rb))*exp(-max(s,0.)/24.)*(dt*cE/(rb*1.772))*1.6*keep;
      float sw=Re*.24;
      float shell=exp(-pow((r-Re*.9)/sw,2.))*smoothstep(8.,150.,s)*(dt*cE/(sw*3.545));
      sum+=float3(1.,.42,.12)*shell*.8*(.35+1.1*e)*fade;
      sum+=float3(1.,.8,.52)*smoothstep(PL-30.,PL+4.,s)*fill*wBody*.8*fade;
      for(int c=0;c<4;c++){
        float fc=float(c);
        float vc=v0+fc*lam+sin(T*47.+fc*2.3)*.6;
        float Rc=R0*(1.-.07*pf)+(.045+.12*(1.-pf))*vc;
        float amp=exp(-fc*.28)*cellOn*(1.-smoothstep(reach-70.,reach-10.,vc))*(1.-smoothstep(PL-24.,PL-6.,vc))*keep;
        float rm=Rc*.33;
        float ph=(s-vc)/(lam*.62);
        float taper=rm*(1.06-clamp(ph,0.,1.)*.8)+.6;
        float bead=exp(-pow(r/taper,2.)*2.)*exp(-max(ph,0.)*2.2)*smoothstep(-.16,.02,ph)*(1.-smoothstep(.7,1.,ph));
        sum+=float3(1.,.82,.46)*bead*(dt*cE/(taper*1.25))*2.1*amp;
        float d=r/(Rc*.9)+abs(s-vc+lam*.08)/(lam*.5);
        sum+=float3(1.,.5,.64)*pow(clamp(1.-d,0.,1.),2.2)*(dt*cE/(Rc*.9*.62))*1.05*amp;
      }
      float sigma=R0*(.85+max(s,0.)*.012);
      cold+=float3(.5,.56,.64)*exp(-(r*r)/(sigma*sigma))*(dt*cE/(sigma*1.772))*smoothstep(0.,16.,s)*(1.-smoothstep(50.,170.,s))*keep;
    }
    col+=sum*glow;
    col+=cold*mist*uVent*.62;
  }

  if(glow>.0004){
    float sh=radiusAt(clamp(s0,0.,PL),pf,warm)*2.2;
    float span=2.6*sh/cE;
    float h0=max(S.tAirE,-span);
    float dh=max(span-h0,0.)/8.;
    float halo=0.;
    for(int i=0;i<8;i++){
      float t=h0+(float(i)+dith)*dh;
      float z=z0+t*sE;
      float rr=u*u+t*t*cE*cE;
      halo+=exp(-rr/(sh*sh))*exp(-max(ZE-z,0.)/240.)*smoothstep(0.,30.,z)*smoothstep(ZE+25.,ZE-15.,z);
    }
    col+=mix(float3(.9,.42,.62),float3(1.,.45,.16),warm)*halo*(dh*cE/(sh*1.772))*.16*glow;
  }
  for(int c=0;c<4;c++){
    float fc=float(c);
    float vc=v0+fc*lam+sin(T*47.+fc*2.3)*.6;
    float Rc=R0*(1.-.07*pf)+(.045+.12*(1.-pf))*vc;
    float amp=exp(-fc*.28)*cellOn*(1.-smoothstep(reach-70.,reach-10.,vc))*(1.-smoothstep(PL-24.,PL-6.,vc));
    float rm=Rc*.33;
    float q=length(float2(u/rm,(s0-vc)/(rm*tanE+1.2)));
    col+=float3(1.,.98,.9)*exp(-q*q*2.4)*2.6*amp*glow;
  }

  float dist=S.dist;
  col+=float3(1.,.84,.64)*uFlash*(exp(-pow(dist/120.,2.))*.36*beyond(S.tAirE,S.tc,120.)+exp(-dist/30.)*1.5*beyond(S.tAirE,S.tc,30.));
  col+=shockOf(dist,S.tc,S.tAirE,e0,V,0.);

  float floorSeen=S.tCol>S.tLow?0.:1.;
  float deck=onDeck(hit.xy)*floorSeen;
  float3 tube=hit-float3(dirH*68.,6.4);
  float tubeLit=max(dot(normalize(tube),normalize(float3(-dirH*68.,26.))),0.);
  float facing=mix(1.,.15+1.3*tubeLit,S.ring);
  float splash=uSplash*pf;
  if(splash>.0004||uRingA>.0004){
    float flow=fbm3(float3(dirH*2.4,rho*.035-T*3.8));
    float flow2=noise3(float3(dirH*5.1+9.,rho*.07-T*6.1));
    float reachS=56.+80.*splash;
    float sheet=exp(-pow(rho/reachS,1.5))*deck*facing;
    float3 hot=mix(float3(1.,.38,.1),float3(1.,.82,.55),exp(-rho/26.));
    col+=hot*sheet*(.26+.56*flow+.22*flow2)*splash*.62;
    col+=float3(1.,.88,.7)*exp(-pow(rho/40.,2.))*splash*.9*floorSeen;
    float dust=noise3(float3(dirH*7.,rho*.05));
    float gw=4.+uRingR*.034;
    col+=float3(1.,.8,.6)*exp(-pow((rho-uRingR)/gw,2.))*(.45+dust)*deck*facing*uRingA*.5;
    if(splash>.0004){
      float tS=(70.-g0.z)/sE;
      float dS=max(tS-S.tAir,0.)/10.;
      float curtain=0.;
      for(int i=0;i<10;i++){
        float3 p=g0+(S.tAir+(float(i)+dith)*dS)*V;
        float r=length(p.xy);
        float h=max(p.z-ground(r),0.);
        curtain+=exp(-pow((r-reachS*.8)/(reachS*.34),2.))*exp(-h/26.)*onDeck(p.xy)*dS*sE/26.;
      }
      float lick=fbm2(float2(u*.05+T*.3,z0*.06-T*1.4));
      col+=float3(1.,.46,.16)*curtain*lick*.55*splash;
    }
  }

  col+=steamOf(g0,V,S.tAir,u,z0,pf,T,0.);

  if(uPop>.0004){
    float3 Cp=float3(0.,0.,ZE-uPopR*.55);
    float tp=dot(Cp-e0,V);
    float pd=length(e0+tp*V-Cp);
    float pn=fbm2(float2(u*.045,s0*.045-T*2.4));
    col+=float3(1.,.46,.15)*exp(-pow(pd/uPopR,2.))*(.55+.7*pn)*smoothstep(ZE+24.,ZE-12.,z0)*beyond(S.tAirE,tp,uPopR)*uPop*.95;
  }

  return inkOf(col,vb);
}
"""

    /** The veil layer, over the engine. */
    const val VEIL: String =
        COMMON +
            """
half4 main(float2 vb){
  Scene S=sceneOf(vb);
  float pf=clamp(uPc,0.,1.);
  float3 col=steamOf(S.g0,S.V,S.tAir,S.u,S.z0,pf,uTime,1.);
  col+=shockOf(S.dist,S.tc,S.tAirE,S.e0,S.V,1.);
  return inkOf(col,vb);
}
"""

    /** The uniforms both layers take each frame. */
    val SHARED = listOf("uJit", "uPc", "uHue", "uSmoke", "uVent", "uShockR", "uShockA")
}
