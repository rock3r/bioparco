# Shaders inside the drawing

Adapted from the original's `references/webgl.md` (MIT) for the Compose kit, where the effect is a
Skia runtime shader (SkSL) drawn inside the figure's own `IsoCanvas`. Read it before you put fire,
water, light or glow into a figure. `figures/BunsenFlame.kt` (Fig 3, the Bunsen burner) is the
worked example: a luminous yellow flame, a blue premixed cone and its outer mantle, ray-marched in
the burner's own camera between the back and front art.

The original's first shader figure was loved, but its colour changed instantly between phases, as if
the effect had been cut. So the bar is: **nothing pops, nothing is cut, and everything sits in the
drawing's perspective.** Every rule below came from a bug that broke that bar.

## Contents
1. When a shader earns its place
2. The layer stack
3. The camera contract
4. Ray-marching in the iso camera
5. Registering shader content to the art
6. API and a minimal figure
7. Craft rules, each with the bug that taught it
8. Light theme
9. Verify
10. Performance

## 1. When a shader earns its place

Use a shader for things lines can't draw: emitted light and glow, fire and exhaust, water surfaces,
caustics, soft shadows from a real lamp, smoke and steam. These are continuous fields, and they're
the subject or what the subject does.

Don't use one for:
- **Solids.** Parts, edges, ticks and screws stay kit art. The shader never draws a part the kit
  could draw.
- **Decoration.** Shimmer, background gradients, bloom on every edge, or noise added to make the
  figure look "rich". If you could delete the effect and the explanation still holds, delete it.
- **Something the art already shows well.** A glowing LED is a `lit` dot, not a shader.

The art must still read on its own. `shaderLayer` returns null when the SkSL doesn't compile; the
figure then draws without the effect, so make sure its mechanism and readout still explain it. The
Bunsen burner without its flame still shows the collar's slots over the ports and a ruler cursor at
the flame's true height.

## 2. The layer stack

A shader can't be depth-tested against the art, so you order layers instead. The basic stack is
three steps inside one `IsoCanvas`, all in the same viewBox units:

```
drawArt(BACK)        what the effect covers
drawShader(layer)    the effect
drawArt(FRONT)       what covers the effect
```

For every part, ask one question: **is it ever covered by the effect, or does it cover the effect?**
Parts that are covered go in the back art, and parts that cover go in the front art. If a part does
both, you have three choices:

- **Split the art.** Cut the part into pieces and send each piece to the layer, and the slot in that
  layer, where it belongs. The Bunsen burner's tube is back art, but the near halves of its rim
  (outer edge and bore) are drawn again as front art, so the strokes stay crisp over the flame's
  root.
- **Model it in the shader.** If the part is a simple shape (box, cylinder, torus) and stays in the
  back art, give the shader that shape as an occluder, so every integral starts at the occluder's
  hit along the ray.
- **Split the effect.** When the effect passes in front of front-art parts, draw a second layer
  after them. An analytic proxy of the occluding part decides, for each sample, whether it goes to
  the back layer (weight `1 − f`) or the front layer (weight `f`). Each sample is drawn exactly once,
  so there is no seam. Make the proxy generous: outside the real silhouette both layers look the
  same.

The kit's art has no pointer targets, so nothing changes for input: the figure's `pointerInput`
stays on the `IsoCanvas`.

## 3. The camera contract

`shaderLayer` prepends `SKSL_ISO` to your fragment. It declares the uniforms and functions that make
the shader see the same camera as `iso()`. The maths is the original's `GLSL_ISO`, and
`ShaderCameraTest` proves it: it evaluates the helpers with Skia on a raster surface for three
cameras and four points each, and checks them against the Kotlin `iso()` and `depthOf()` to 2e-3
viewBox units.

| Name | What it is |
|---|---|
| `uCanvas` | The canvas size in device pixels (viewBox × the scope's `unit`). |
| `uView` | The viewBox size `(W, H)`. |
| `uOrigin`, `uK` | The projection origin and the screen units per world unit (`glCamera(P).k`). |
| `uCam` | `(sinA, cosA, sinE, cosE)` of the azimuth and elevation. |
| `uTime` | The time you pass to `drawShader`. |
| `uLight` | 1 in the light theme, 0 in dark: set by `drawShader` from the palette. |
| `half4 main(float2 vb)` | Your entry point. The layer is drawn in viewBox units, so `vb` is already the viewBox point: there is no `viewOf`. |
| `deviceOf(vb)` | The device pixel under `vb`, for hashes that must not scale with zoom (dither). |
| `project(float3 p)` | World to viewBox, identical to the kit's `iso()`. |
| `towardViewer()` | The unit 3D view direction, `(cosA·cosE, sinA·cosE, sinE)`. Every `p + t·towardViewer()` lands on the same pixel, and `dot(p, towardViewer())` is `depthOf(p)`. Not the same as the kit's `towardViewer(P)`, which returns the floor direction. |
| `rayOf(vb)` | The world point under `vb` on the vertical plane through the world origin that faces the camera, so `rayOf(vb) + t·towardViewer()` is the pixel's ray. Put a vertical-axis effect (a flame, a plume) on the world z axis and `rayOf` is where the ray passes closest to it. |
| `pixelOf()` | World units per device pixel across the screen. |
| `lightDir()` | The kit's light, so shader shading agrees with the art's tones. |
| `onFloor(vb, z)` | The world point on the horizontal plane at height `z` under this pixel. |
| `onWallX(vb, x)`, `onWallY(vb, y)` | The world point on the vertical plane `x = const` or `y = const` under this pixel. |
| `hash12`, `hash13`, `noise2`, `noise3`, `fbm2`, `fbm3` | Hashes and value noise, plus 4-octave fbm. |
| `window(x, a, b, soft)` | 1 inside `[a + soft, b − soft]` and 0 outside `[a − soft, b + soft]`. |
| `tonemap(c, exposure)` | `1 − exp(−c·exposure)`. Use it instead of `clamp`. |
| `over(top, under)` | Premultiplied over: stack inks back to front. |
| `inkFor(seen, cover)` | The ink that shows `seen` over white paper (section 8.1). |
| `finish(c, vb)` | Clamps alpha and `rgb ≤ a`, then dithers ±1 LSB, gated so empty pixels stay exactly 0. Return it from `main`. |

`onFloor` and the wall functions round-trip exactly with `iso()`: `project(onFloor(vb, z)) == vb`.
That is what lets shader content register with the art's lines.

**Why world space.** The figure's perspective is the projection. If you draw an effect in screen
space (an ellipse sized by eye, a glow column in pixels), it can't agree with the drawing. It floats
above the floor, runs through the deck, or keeps the same shape when the camera changes. Build
everything from world points and world sizes. Screen sizes come only from world units times `uK`.

Antialiasing in world units: `pixelOf()` is world units per device pixel across the screen. On the
floor in the depth direction, divide it by `uCam.z` (sinE), and up a wall by `uCam.w` (cosE). Soften
registered edges by about `0.7 * px`, so they come out one pixel soft: crisp like the art, but not
aliased.

**SkSL, not GLSL.** Types are `float2`/`float3`/`float4` (and `half4` for the return), there is no
`precision` line, loops need constant bounds (`for (int i=0;i<16;i++)`), and there are no
`gl_FragCoord`, derivatives or textures through this API. A child shader or a uniform array is the
way to pass more data.

## 4. Ray-marching in the iso camera

The camera is orthographic with uniform scale: both rows of the projection are orthonormal. Three
things follow.

- **Every pixel is a straight ray** `p(t) = p0 + t·V`, where `V = towardViewer()` and `p0` is any
  world point under the pixel: `onFloor(vb, h)`, or `rayOf(vb)`. Larger `t` is nearer the viewer.
- **Distances to points are screen distances.** Screen distance divided by `uK` is the exact
  perpendicular distance from the pixel's ray to a world point. For a vertical axis through the
  origin, `length(rayOf(vb).xy)` is the ray's closest distance `u` to the axis, and along the ray the
  radius is `sqrt(u² + (t·cosE)²)` and the height `z0 + t·sinE`.
- **Visible fraction behind an occluder.** If the first opaque surface along the ray is at `tLow`, a
  Gaussian centred at `tc` with width `sigma` is visible by `1/(1 + exp(2.41·(tLow − tc)/sigma))`.

To march a volume:
1. **Find the first opaque hit** along the ray: the floor, plus anything the art draws there. Only
   the part of the ray in front of the hit is air. The Bunsen flame needs none: everything opaque
   (the tube) is below the rim, and the flame is above it.
2. **Bound the march** with a simple shape (the flame uses a cylinder `uReach` round the axis and
   `uTop` above the rim), return 0 early outside it, and window every term to exactly zero before the
   bound, so the bound is only an optimisation.
3. **Sample with dither.** `t = ta + (i + hash12(deviceOf(vb)))·dt`, so 20 samples don't band.
4. **Normalise each term by its chord**, so the centre pixel gets the intended brightness whatever
   the step count: multiply the sum by `dt·cosE` for a vertical-axis volume.
5. **Give thin features their own loop.** A 0.5 mm sheet can't be found by 16–20 samples over a
   45 mm chord: the Bunsen's first inner cone came out beaded. Its cone now has a second loop of 24 cheap
   samples over just the bore's width (`coneLight`), and only runs when the cone is showing.
6. **Expect a uniform cylinder to look softer.** Its projected profile is sqrt-shaped, so it loses
   the defined edge a flat drawing had. In dark, a limb shell brings the edge back; in light, the
   same shell inks an outline round a hollow flame, so weight the body toward the axis instead.

## 5. Registering shader content to the art

Anything that lies on a plane must line up exactly with that plane's lines.
- **Same numbers.** Pass the art's sizes and heights to the shader as `fixed` uniforms (the flame
  gets `uRim` and `uBore` from `BunsenBench`), never retyped constants.
- **Same plane.** Use `onFloor(vb, z)` and the wall functions at the art's heights. Never use screen
  offsets.
- **Seen through an opening, masked twice.** Content behind an opening in a nearer plane is masked by
  its own plane and by the opening tested in the opening's plane, the two soft masks multiplied
  together. Draw the opening's inner walls in the front art, so the offset between the planes reads
  as wall.
- **Only the deviation moves.** If an effect displaces what you see (refraction), apply only the
  part that differs from the rest state, so at rest it lines up with the art.

Check registration at 4× (section 9).

## 6. API and a minimal figure

Everything is in `dev.sebastiano.anatomy.kit` (`Shader.kt`), and the envelopes are in `Motion.kt`.

| Function | What it does |
|---|---|
| `glCamera(P)` | A `ShaderCamera(origin, k, cam)` for a projection. |
| `shaderLayer(fragment, width, height, camera, fixed = emptyMap(), onError)` | Compiles `SKSL_ISO + fragment` as a Skia runtime effect for a `width` by `height` viewBox seen through `camera`. `fixed` is `name to listOf(values)` for uniforms set once. Returns a `ShaderLayer`, or null after calling `onError` (default: stderr) when it doesn't compile. Make it once, in `remember`. |
| `layer.set(name, value)` | Sets a `float`, or a `float2`–`float4` from a list of 2–4, or a `float[N]` from a longer list. |
| `IsoScope.drawShader(layer, time, area = whole viewBox) { set(…) }` | Sets the camera uniforms, `uTime` and `uLight`, runs the block to set this frame's uniforms, and fills `area` (viewBox units) with the shader: screen-blended in dark, normal premultiplied in light. |
| `shaderArea(points, P, pad = 2.0)` | The screen box around world points: pass the corners of the effect's world bound. |
| `smooth`, `burst`, `settle`, `follow`, `spring` | The envelopes every uniform rides on (section 7 and `motion.md`). |

Your fragment declares its own uniforms and `half4 main(float2 vb)`, which returns **premultiplied**
colour, normally through `finish(ink, vb)`.

A minimal figure: a hot plate glowing on a square deck, registered to a ring of radius 40.

```kotlin
private const val FRAGMENT = """
uniform float uHeat;
const float R=40.;
half4 main(float2 vb){
  float px=pixelOf()/uCam.z;
  float3 p=onFloor(vb,0.);
  float r=length(p.xy);
  float disc=1.-smoothstep(R-.7*px,R+.7*px,r);
  float halo=exp(-pow(r/(R*1.6),2.))*(1.-smoothstep(80.,88.,max(abs(p.x),abs(p.y))));
  float3 col=tonemap(float3(1.,.42,.12)*(disc*.9+halo*.35)*uHeat,1.2);
  float a=max(col.r,max(col.g,col.b));
  float4 dark=float4(col,a);
  float4 light=inkFor(mix(float3(1.),float3(.98,.55,.16),a),0.);
  return finish(mix(dark,light,uLight),vb);
}"""

@Composable
fun HotPlateFigure(palette: Palette, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val layer = remember { shaderLayer(FRAGMENT, WIDTH, HEIGHT, glCamera(P)) }
    val heat = remember { Settling(0.0) }
    var shown by remember { mutableDoubleStateOf(0.0) }
    rememberFrameLoop(reducedMotion) { dt, calm ->
        val moving = settle(heat, 1.0, dt, 0.35, calm)
        shown = heat.x
        moving                                             // stops once settled: no flicker here
    }
    IsoCanvas(WIDTH, HEIGHT, palette, modifier) {
        drawArt(BACK)
        val h = shown
        if (layer != null && h > 1e-3) drawShader(layer, 0.0, AREA) { set("uHeat", h) }
        drawArt(FRONT)
    }
}
```

Without time in the field this loop stops once the heat has settled. With an animated field
(flicker, flow) keep running while the effect is alive and on screen, and stop otherwise: the Bunsen
burner's loop runs while the figure is composed, because a flame always flickers, and stops under
reduced motion once the collar has settled.

## 7. Craft rules, each with the bug that taught it

### Nothing pops
Every uniform moves along a smooth envelope in time, with an attack and a decay. Never set a value
to 1 in a single frame.

- **One-shot events use `burst`.** The original's first ignition flash went from 0 to full in one
  frame and whited out the card. Every event (flash, shock, pop, shake) uses
  `burst(age, rise, fall)`, a smoothstep attack then an exponential decay, with a 50–90 ms attack.
  Keep whole-card flashes low.
- **Phase changes are crossfades driven by the physical quantity.** A separate term that died while
  another grew read as an instant colour switch. Drive colour, brightness, raggedness and length
  from one physical quantity that moves on a spring. The Bunsen flame's soot, cone strength,
  turbulence, length and cone height are all functions of the collar opening, which `follow`s its
  target, so yellow crossfades into blue as the collar turns.
- **Colour gets its own slow envelope** when the driving quantity moves fast: give hue its own
  `settle` (0.3–0.5 s), and brightness a separate uniform that only falls as hue rises.
- **Brightness during a fade-out only falls.** `mix(a, b, hue)` with `b < a` rises again as hue
  decays. Freeze the level when a fade-out starts and scale it only by quantities that fall.
- **Recheck neighbours when you change a spring.** A critically damped main spring can starve a term
  that relied on its overshoot.
- **One-shot flags belong to the event**, not to a quiet-period clock.
- **Changes travel through the medium.** A new wavelength spreads outward from its source rather than
  re-laying the whole field at once; keep a short history of the source and read it by age.
- **Keep the phase continuous through a change**, so far crests don't race and alias.
- **First light fades in, and in light it only brightens** (section 8.2).

### No hard spatial cut-offs
Every term reaches zero before any boundary: an `if`, a bounding shape, a plane, or the edge of the
drawn `area`. Use `if` only as an optimisation where the term is provably about zero.
- An `if` box, a `continue` on a small step and a `step()` halo mask all showed as seams in the
  original's plume.
- A screen-space glow column ran to the bottom of the canvas and ended in a straight line. Use world
  space, bounded by the ground, and make the last pixels of the `area` read exactly 0.
- Cell noise looks only inside its own cell: keep `offset + wobble + radius + tail` under half a cell,
  or test the 3 × 3 neighbouring cells.
- Radial noise that used `atan` had a seam along one angle. Use `noise3(float3(dir, r·k − t))` of the
  direction vector instead.

### World space, in the camera
- A shock drawn as a 2D circle around the projected exit ran over and under the deck. Make it a real
  sphere: each pixel ray hits it at two roots, hidden softly below the ground.
- A splash laid flat over a ring lost the ring's volume. Model the ring as an occluder and light it
  by facing.
- Haze behind a column painted over its front face. Make the column an opaque box in the shader.

### Layering and blending
- In the dark theme a layer that only emits light is screen-blended and writes premultiplied colour
  with alpha = the largest channel; `drawShader` does this from the palette. A layer that also
  darkens what it covers (water tinting the paper) needs normal blending in dark too: draw it with
  your own `Paint` if you need that.
- In light every layer is normal-blended and its ink is solved from the colour the paper should show
  (section 8), not darkened from the dark-theme colour.
- Glow rims over a body go in the front art or the ink itself, never a blurred normal-blended rim
  over a hot core (it reads as a dim brown collar).

### Tonemap and dither
- Use `tonemap()`, never `clamp`, so bright cores roll off instead of flattening.
- Without dither, large soft terms become 8-bit staircases. `finish` dithers after tonemap with
  about ±1 LSB of animated triangular noise, gated so empty pixels stay exactly 0. Under reduced
  motion pass a fixed time, and the grain is still.
- Band-limit fine detail: fade out any pattern whose projected wavelength drops below 2.5–5 device
  pixels, or it turns into moiré.

### Render only while it matters
- Draw only while the effect is active, and give `drawShader` an `area` around the effect, not the
  whole viewBox: the Bunsen flame's box is 5% of the card.
- An effect that never rests (a pilot flame, bubbles in a flow) keeps the loop running at idle. Keep
  the per-frame Kotlin work small: build static art once, rebuild only moving parts, write snapshot
  state only the canvas reads, and write the readout only when its text changes.
- Gate costly terms on uniforms (`if(uBlue<.001){return 0.;}`): they are the same for the whole
  draw, so they cost nothing when off.

### Reduced motion
Jump straight to the steady state, freeze `uTime` (the Bunsen uses 0.8 s), zero every event
envelope, and let the loop stop once settled. The figure must still respond to input.

### Fallback
`shaderLayer` returns null, so guard every use. The art must still explain the figure without the
effect.

## 8. Light theme

Light is the shipping theme. A page that is light gets shaders designed for paper, not dark-plate
glows converted at the end. Seven figures on the original's site were built on a dark plate and then
converted (a pool reactor, a foundry pour, an afterburner, a glory hole, a tokamak, a plasma cutter
and a forge). Each conversion took three review rounds, and every rule in this section is one of
their findings.

Don't multiply every hue by a fixed dark red and set alpha to brightness: the hottest gas then comes
out darkest and most opaque, and reviewers called it "red ink smoke".

### 8.1 Ink on paper
Over white the layer is normal-blended, premultiplied, and never screen, because screen over white is
white. No pixel can be brighter than the paper, so light has to read through **saturation and local
contrast**:
- **A glow is a saturated body, a pale near-white core and a coloured rim.** The pale core reads as
  hot only because the body around it is saturated and the ground around that is darker.
- **What the light falls on takes a tint** (multiply-like, with capped alpha). It doesn't get
  brighter.
- **The volume that holds the light is darker, for a physical reason:** soot, refractory, deep water
  or shadow. It is never a black box.

**Design the colour the paper should show, then solve for the ink.** Write a ramp from light energy
to the colour the reader should see over white, then invert normal compositing over white with
`inkFor(seen, cover)` from `SKSL_ISO`:
```
float4 inkFor(float3 seen,float cover){
  float3 absorbed=1.-seen;
  float a=max(max(absorbed.r,max(absorbed.g,absorbed.b)),cover);
  return float4(float3(a)-absorbed,a);
}
```
Over white this composites to exactly `seen`. `cover` is an alpha floor at the hot end: without it a
near-white core has alpha near 0, so the lines behind it show straight through and it reads as a
hole, not as light. The Bunsen flame's soot ramp (`paperSoot`) runs white → pale yellow → saturated
orange-yellow `(1,.70,.12)` → cream `(1,.93,.66)`, with `cover = .7·smoothstep(.72,.97,e)`.

**One ink per kind of light, composited in the shader.** Keep separate accumulators, give each its
own ink, and stack them back to front with `over`. If you sum the energies first and ink the sum,
everything gets one hue and the pale core is lost.

| Term | Ink | Example |
|---|---|---|
| Emission, the hot thing itself | ramp body plus a pale core, alpha up to .96 | the flame's soot, the inner cone |
| Light falling on a surface | hue pulled warm or cool, alpha capped at .5–.8 | |
| Haze: steam, smoke, fume, a faint mantle | grey or lightly tinted, alpha `1 − exp(−density·k)` | the flame's blue mantle |
| Sparks and streaks | on top of everything | |

The Bunsen stacks `over(coneInk, over(sootInk, mantleInk))`.

**Finish.** `finish` clamps `rgb ≤ a`, dithers rgb and alpha both, and keeps empty pixels at 0.
Tonemap energy before it reaches a ramp (`tonemap`, or `1 − exp(−k·e)`), never `clamp`.

### 8.2 Ink grows with light
In dark more light only adds. On paper more light means more ink, and a careless formula can add ink
while the light falls or take ink away while it rises.
- **Fade the finished ink once.** Scaling a premultiplied ink by `u` is an exact linear crossfade
  from bare paper to the full picture (the flame's `uLive`). Fading each term before inking it makes
  the pale core arrive before its body, so first light goes pale and then dark.
- **Fade a glow by energy and alpha together.** Fading only alpha turns a pale core milky grey;
  fading only energy runs the glow back down its ramp. Scale energy down to a floor and alpha the
  rest of the way.
- **Take the hue at steady brightness.** As energy falls the tonemap reddens and darkens the hue, and
  with alpha near its cap the ink rises. Divide by the local lit strength before taking the hue, and
  tie alpha to energy.
- **Hand-offs are plain premultiplied mixes**, and each copy is gated so it can't pick up the
  other's terms.
- **Envelopes that multiply can rise during a fade.** Freeze first-light growth while the effect is
  falling.

### 8.3 A ground for the light
A glow on white with nothing around it reads as a stain. Give it the ground it really has:
- **Cavities take darker tones.** Draw the inside of a bore, a furnace or a tank with the palette's
  darker tokens (`palette.lo`, `palette.shades[0]`, `palette.page` in dark), with all four shades
  kept so it still has form. The Bunsen's bore is a dark disc under the flame's root.
- **The shader's ground equals the art's.** Where the shader paints an opaque cavity, pass the
  palette colour as a uniform rather than retyping it.
- **Light around the source is a tint with a long tail**, or the vessel reads grey.
- **No bloom in empty air.** In light a halo around a stream or flame hanging in the air is a smudge
  on the paper. Keep the rim inside the body, and the tint on surfaces the light reaches.
- **Light only lands where the source can see**, tested against the same geometry the art draws.

### 8.4 Recipes by phenomenon
Hot bodies run dull red → orange → yellow → near-white, in that order and no other. Keep one stop
table in the model and use it for every ink.
- **Molten metal, forged steel.** Index the table by brightness temperature; alpha opens at visible
  glow (about 560–820 °C); the rim is cooler and darker; a cream core appears only in the hottest
  centre; a dark skin grows as the metal cools. Reviewers' words for a good stream: "a cream core
  with an orange skin".
- **Glass.** Alpha is a luminance gate; deep cherry → red → orange; the pale core sits where the
  pixel's own emission peaks. A red mixed toward white is salmon, and no temperature looks like that.
- **Sparks.** A saturated head and a darker, cooler tail, at least about 1.25 device px wide; over a
  hotter emitter a spark vanishes.
- **Flame.**
  - A saturated orange-yellow body with translucent edges, weighted toward the axis in light
    (the flame's `axis = 1.3 − .6·smoothstep(0, 1, rr)`). Cut the limb mantle in light, or the
    flame is an outline round a hollow.
  - Keep raggedness to the outer part and the upper flame; the root at the rim stays steady.
  - A luminous (diffusion) flame has a blue root and yellow above it; as primary air rises the soot
    recedes to yellow tips, then goes. A premixed flame's inner cone is a thin, steady, saturated
    blue sheet with a paler interior; its outer mantle is a faint blue-violet haze. Real numbers
    drive all of it (`Bunsen.kt`).
  - Check a cross-section: ink should peak toward the axis.
- **Smoke and steam.** Light grey, never with a dark glow ink laid over it; alpha
  `1 − exp(−density·k)`; near a hot source warm the cloud toward pale amber.
- **Plasma and Cherenkov light.** Saturated violet or cyan-blue bodies whose core goes pale above a
  threshold, with the paper ramp of 8.1.

### 8.5 The art and the fallback in light
- **Read the theme once.** `drawShader` sets `uLight` from the palette. Gate every light-only change
  on it (`mix(dark, light, uLight)`), so dark output stays as it was.
- **No colour literals in the art.** Art colours come from the `Palette`; shader constants that must
  match a token come in as uniforms.
- **Nothing with gaps goes under a translucent ink.** Whatever the art draws shows through an ink
  that isn't fully opaque: rows of strokes print as grey streaks. Fill the face instead.
- **Every body the shader draws has a meaning without it**: if the effect is missing, the readout and
  mechanism still explain the figure.

### 8.6 Where the ink meets a line
The layer lies over the back art's 0.6 px strokes. In light a misregistered edge shows as a pale gap
or a coloured line over the stroke, rather than as a dark seam.
- **Stop at the stroke's inner edge,** half the line weight in (0.3 screen px for the kit's
  hairlines), or redraw the stroke in the front art (the Bunsen's rim).
- **Don't clear the ink where nothing is stroked.** Draw such a crease in the ink instead.
- **A body that enters from out of frame** fades in from the `area`'s edge in world or screen space,
  so the edge still reads 0.

### 8.7 What the reviewers saw, and the fix

| You see in light | Cause | Fix |
|---|---|---|
| A red-brown "ink smoke" plume | Hue × a dark multiplier, alpha set to brightness | A paper ramp: saturated body, pale core, alpha floor (8.1) |
| A flame drawn as an outline, hollow inside | The dark limb mantle | Weight the body toward the axis, cut the mantle (8.4) |
| A cream sticker with an orange outline | Soot energy saturating the ramp | A smaller ink coefficient and cover, a pale core only at the top of the ramp |
| First light goes pale, then dark | Terms faded one by one before inking | Fade the finished premultiplied ink once (8.2) |
| A glow fades out through milky grey | Alpha-only fade of a pale ink | Dim energy and alpha together (8.2) |
| A salmon stage as glass or steel cools | Red mixed toward white | A blackbody-ordered ramp, pale only past yellow (8.4) |
| A halo stains the paper around a flame | Dark-theme bloom in empty air | No halos in air in light (8.3) |
| A beaded or dotted thin sheet | Too few samples across it | A dedicated loop over the sheet's short chord (4) |
| A pale fringe or white seam along an edge | Ink stops short of the stroke | End the ink at the stroke's inner edge, or redraw the stroke in front (8.6) |

## 9. Verify

`./gradlew :anatomy:capture` renders shaders: `ImageComposeScene` draws on Skia's CPU raster, which
runs runtime effects, so a capture is the real shader output, frame by frame on the simulated
clock. On top of `verify.md`'s checklist:

- **A contact sheet across every transition:** each phase and the steady states, with `--times` and
  `--hover`/`--key` to get there. For a flame, several moments of the flicker at each setting.
- **Never time anything on the wall clock.** A capture renders a CPU shader at tens of milliseconds a
  frame, so a `delay()` in a `LaunchedEffect` fires long before its simulated time: the Bunsen's idle
  tour first came back mid-capture. Time idle resumes on the frame loop's `dt`.
- **Numbers, not just pictures.** Read pixels from the PNG: hue and ink should each move
  monotonically through a phase change, apart from deliberate bursts.
- **Edges read 0.** Check the rows and columns at the edge of the `area` in every phase: anything
  above 0 is a straight cut.
- **In light, probe ink along the physical ramp** (yellow → blue for the Bunsen) and a cross-section
  of the body from edge to edge: it should peak on the axis.
- **In light, check the dark path too.** Every light change sits behind `uLight`.
- **4× crops** of every place the effect meets the art: rims, lips, deck edges, openings. Look for a
  gap between the effect and the part, ink over a stroke, a hard edge, banding, a double line or a
  sudden colour switch.
- **Reduced motion** (`--reduced`): one still, representative frame, the same at every time.

## 10. Performance

- Cost is roughly pixels × samples. Give `drawShader` a tight `area`, return early outside the
  effect's world bound, gate whole terms on uniforms, and give thin features their own short loop.
- A real window draws through Skia's GPU backend (Metal on macOS), where the Bunsen figure holds
  60 fps with its flame live. `ImageComposeScene` captures run the same SkSL on the CPU raster:
  exact, but slow (60–130 ms a frame for the flame's 45,000 pixels at 2×). Software rendering
  (`SKIKO_RENDER_API=SOFTWARE`) pays that cost every frame, so bound the work tightly.
- Return early per pixel from the effect's *current* extent (the flame's width and length this
  frame), not just its largest: it nearly halved the Bunsen flame's CPU cost. Keep those bounds
  generous enough to cover every soft tail and sway, or they become hard edges.
- Don't animate `uTime` if nothing in the field needs it, and pass a fixed time under reduced
  motion so the frame is still.
- When the effect is idle, stop the loop. A shader figure that stays at 60 fps with nothing happening
  is a bug; a flame is never idle.
