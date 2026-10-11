# Kit API

Everything is in `dev.sebastiano.anatomy.kit`. The geometry (`Iso.kt`, `IsoPath.kt`) and motion
(`Motion.kt`) are plain Kotlin in `anatomy/src/commonMain`; the art, renderer, card and frame loop
are Compose, in `anatomy/src/jvmMain`. World coordinates are `Point3(x, y, z)`, with x and y on the
floor and z up, in units of about 1 px. Screen coordinates are `Flat(x, y)` in the viewBox.

The functions keep the original's names and argument order, so the original's `references/kit.md`
and examples read across directly. The differences:

| Original (JS) | Kotlin |
|---|---|
| `[x, y, z]`, `[x, y]` | `Point3(x, y, z)`, `Floor(x, y)` (floor), `Flat(x, y)` (screen) |
| `{ x, y, w, d, r }` | `Plan(x, y, w, d, r)` |
| paths as SVG `d` strings, joined with `+` | `IsoPath`, joined with `+` or `IsoPath.join(list)`; `.svg()` writes the original's string |
| `"x"`/`"y"`, `"left"`/`"right"` | `Axis.X`/`Axis.Y`, `Face.Left`/`Face.Right`, `Along.X`/`Along.Y` |
| `[minor, major]` lengths | `minor to major` |
| `{ pad, azimuth }` options | named arguments: `pad = 30.0, azimuth = 42.0` |
| `translateAlong` returns `"translate(x y)"` | returns the `Flat` offset |
| `topMatrix`/`sideMatrix` return `"matrix(…)"` | return a `FaceMatrix(u, v, origin)` |
| `solidSvg`, `lineSvg`, `dotsSvg`, `wireSvg`, `faceTextSvg`, `groupSvg` | `ArtBuilder.solid`, `line`, `dots`, `wire`, `faceText`, `group` |
| `figureSvg` + `plateHtml` + `pageHtml` | `IsoCanvas` inside a `Plate` |
| `ISO_CSS` themes | `Palette.Light`, `Palette.Dark` |

## 1. Types

```kotlin
data class Flat(val x: Double, val y: Double)            // screen point, with + - *
data class Floor(val x: Double, val y: Double)           // floor point
data class Point3(val x: Double, val y: Double, val z: Double)
data class Plan(val x: Double, val y: Double, val w: Double, val d: Double, val r: Double)
data class Projection(val origin: Flat, val scale: Double, val azimuth: Double = 45.0, val elevation: Double = 30.0)
class IsoPath(val runs: List<Run>)                        // polylines, open or closed
class SolidPaths(fill, outline, crease, top, shades: List<IsoPath>, bevel: IsoPath?, seamless)
enum class Tone { Hi, Mid, Lo, Faint, Lit, Red, Green, Blue }
```

## 2. Camera

| Function | What it does |
|---|---|
| `fitProjection(points, width, height, pad = 24.0, headroom = 0.0, azimuth = 45.0, elevation = 30.0)` | The projection that centres `points` in the viewBox. Pass `boxCorners(...)` of the base plus the tallest points. |
| `iso(point, P)` | A world point on screen. |
| `depthOf(point, P)` | How near a point is. Larger is nearer: sort ascending to paint far before near. |
| `towardViewer(P)` | The floor direction that points at the viewer. |
| `axisVector(P, Point3(dx, dy, dz))` | The screen vector of a world offset. |
| `screenRise(height, P)` | How many screen units a world height rises. |

## 3. Solids

| Function | What it does |
|---|---|
| `slabOf(plan, z, h, P, steps = 8, bevel = 0.0)` | A rounded box. `steps` is points per corner (2–4 small, 8–10 large). `bevel` (0.3–1.6) adds a highlight on the top along the visible edges. |
| `cylinder(cx, cy, r, z, h, P, steps = 32, bevel = 0.0)` | A post, puck, foot, knob or pad. 16–20 steps small, 56–72 for big discs. |
| `extrude(ring, z, h, P, convex = true, bevel = 0.0)` | Any floor outline. Pass `convex = false` for a concave one. |
| `ringOf(plan, steps)`, `roundedPlan(plan, steps)` | A rounded-rectangle outline as points. |
| `circleRing(cx, cy, r, steps)` | A circle outline as points. |
| `insetPlan(plan, by)` | Shrinks a plan (negative `by` grows it). |
| `corners(plan, inset)` | Four floor points inset from the corners, for feet and screws. |
| `boxCorners(plan, z0, z1)` | The eight corners, for `fitProjection`. |
| `haloOf(plan, z, h, P)` | A fill path for a soft shadow, drawn with `halo`. |

A `SolidPaths` paints in order: `fill` (paper colour, hides what is behind), `shades[0..3]` (visible
sides by how they face the light, darkest first), `top`, `crease`, `bevel`, then `outline`.

## 4. Lines on faces

| Function | What it draws |
|---|---|
| `onTop(points, z, P, closed = false)` | A polyline on a horizontal face. |
| `lineOnTop(from, to, z, P)` | One segment on a horizontal face. |
| `planOutline(plan, z, P, steps)` | A rounded-rectangle outline on a face: grooves and inlays. |
| `ring(cx, cy, r, z, P, steps)` | A circle on a face: screw rings, bezels, seats. |
| `segment(from, to, P)`, `rise(at, z0, z1, P)` | Any 3D segment; a vertical one. |
| `pointsAt(points, P)`, `dotGrid(x, y, cols, rows, gap, z, P)` | Projected points for screws, rivets, perforations. |
| `topMatrix(at, z, P, along = Along.X)` | A `FaceMatrix` that lays text flat on a top face. |
| `sideMatrix(at, z, P, Face.Left \| Face.Right)` | The same, on a vertical face, local y running down. |

## 5. Parts library

| Function | Returns |
|---|---|
| `knurl(cx, cy, r, z0, z1, count, P, inset = 0.4)` | Vertical lines on the visible half of a cylinder. |
| `sideArc(cx, cy, r, z, P, steps)` | The visible front half of a horizontal circle on a cylinder side: seams and bands. For the far half (the floor of a hole), sweep the other way with `onTop` over `circleRing` points. |
| `sideSeam(plan, z, P, steps)` | The same for a box, wrapping its corners and ending on its silhouette. Pass the slab's own `steps` and draw it as the slab's `inner`. |
| `ringSeam(ring, z, P)` | `sideSeam` for any floor outline. |
| `sideRing(center, r, Axis.X \| Axis.Y, P)` | A circle on a vertical face (the axis is the face normal): bores, rod ends, lenses. |
| `coil(from, to, center, r, turns, axis, P)` | A `Coil(back, front)`: paint back, then the rod, then front. |
| `topTicks(from, to, step, every, edge, z, minor to major, P, axis)` | `Ticks(minor, major)` on a top face, growing from `edge`. |
| `sideTicks(from, to, step, every, face, top, minor to major, P, axis)` | Ticks hanging down a vertical face from its top edge. |
| `radialTicks(cx, cy, r, count, every, z, minor to major, P, from = 0.0, span = 2π)` | Dial and turntable ticks. Angle 0 points to −y, clockwise from above. |

## 6. Moving things

| Function | What it does |
|---|---|
| `translateAlong(P, Point3(dx, dy, dz))` | The screen offset that moves a pre-built group by a world offset: carriages, drawers, plungers, lifts. |

Rotations about z can't be done with an offset: rebuild the rotating paths each frame (a needle, a
pointer, a small part). One solid costs well under 0.1 ms.

## 7. Art

Build static art once, in painter's order, with `art { … }`:

```kotlin
val ART: Art = art {
    halo(haloOf(PLATE, -17.0, 12.0, P))
    solid(slabOf(PLATE, -12.0, 12.0, P, 8, 1.6), tone = Tone.Mid)
    line(planOutline(insetPlan(PLATE, 5.0), 0.0, P), tone = Tone.Lo)
    dots(screws.map { iso(Point3(it.x, it.y, 0.0), P) }, size = 0.5)
    group("carriage") { solid(slabOf(CARRIAGE, 3.0, 5.0, P, 5, 0.6), tone = Tone.Mid) }
}
```

| `ArtBuilder` | Notes |
|---|---|
| `solid(paths, tone = Hi, crease = Faint, halo = false, lit = false, flat = false, inner = null)` | `crease = null` draws no crease or bevel. `lit` is the one accent: a lit edge and lighter top and sides. `inner` is art drawn under the outline: seams, welds, bands. |
| `line(path, tone = Lo, stroke = Solid, free = None, width = null)` | `Stroke.Dotted`, `Dashed`, `Flow` (dashes that march, for things moving along a path). `free` marks an end meant to stop on a bare face. `width` overrides the 0.6 px hairline. |
| `faded(lines: List<Pair<IsoPath, Double>>, tone)` | Lines that each carry an opacity, grouped in steps of 0.05. |
| `fadeLine(points, start, end, tone)` | One line that ramps out over its first and last screen units instead of stopping. |
| `dots(points, size = 0.5, tone = Mid, alphas = null)` | Screws, rivets, perforations. |
| `wire(from, to, via = [], tone = Lo, stroke, ends = Ends.Both, size = 1.2)` | A connector with terminal dots. |
| `faceText(matrix, text, size = 3.0, tone = Mid, anchor = Anchor.Start)` | Engraved text on a face, in the monospace face. |
| `halo(path, blur = 9.0)` | A soft shadow, blurred, dropped 3 units. |
| `group(part) { … }` | Art that live code moves by a screen offset. |
| `add(art)` | Art built elsewhere. |

## 8. Drawing, the card and the page

```kotlin
IsoCanvas(WIDTH, HEIGHT, palette, Modifier.fillMaxWidth()) {        // an IsoScope
    val x = carriageX                                               // snapshot state, read here only
    drawArt(ART) { part -> if (part == "carriage") translateAlong(P, Point3(x, 0.0, 0.0)) else null }
    line(needleAt(turn), tone = Tone.Lit, width = 1.1)              // rebuilt each frame
}
```

- `IsoCanvas(width, height, palette, modifier, clock = { 0.0 }) { … }` scales the viewBox to the
  available width. Inside, `IsoScope` has `drawArt(art, offsetOf)` and `drawArt(art, offset)` (one
  copy of a repeated part), `solid`, `line`, `dots` and `faceText` for per-frame parts, `palette`,
  and `draw` (the Compose `DrawScope`, in viewBox units) for anything else. `clock` drives
  `Stroke.Flow`.
- `Plate(fig, title, hint, readout = { text }, palette, modifier, keys, caption) { … }` is the card.
  `readout` is read only by the readout's own text. `keys` are `LegendKey(KeyMark.Lit, "…")`; marks
  are `Raised`, `Flat`, `Lit`, `Edge`, `Dim`, `Dotted`, `Dashed`, `Red`, `Green`, `Blue`.
- `Palette.Light` and `Palette.Dark` are the original's tokens. Light is the theme to design for
  first. `palette.ink(tone)` gives a tone's colour if you draw something yourself.
- A figure is a composable `XxxFigure(palette, reducedMotion, modifier)`, registered in `Figure` and
  `FigureContent` (`App.kt`).

## 9. Motion

`follow`, `spring` (`Spring(at, rate)`), `settle` (`Settling(x, v)`), `smooth`, `burst` and
`rememberFrameLoop`. See `motion.md`.

## 10. Shader layer

The original's `gl.mjs`, as a Skia runtime shader (SkSL) drawn inside the same `IsoCanvas` between
two `drawArt` calls (`Shader.kt`). Read `shaders.md` before using it: the camera contract, the layer
stack, the inks for light and dark, and the craft rules.

| Function | What it does |
|---|---|
| `glCamera(P)` | The `ShaderCamera` (origin, `k`, `sinA cosA sinE cosE`) the shader sees. |
| `SKSL_ISO` | The prelude: `project`, `rayOf`, `onFloor`, `onWallX`, `onWallY`, `towardViewer`, `pixelOf`, noise, `tonemap`, `inkFor`, `over`, `finish`. |
| `shaderLayer(fragment, width, height, camera, fixed, onError)` | Compiles `SKSL_ISO + fragment`; null if it doesn't compile. Make it once. |
| `IsoScope.drawShader(layer, time, area) { set(name, value) }` | Draws it now over `area` (viewBox units): screen-blended in dark, ink on paper in light. |
| `shaderArea(points, P, pad)` | The screen box around world points, for `area`. |

## Not in the Compose kit yet

`lathe.mjs` (`frameAlong`, `solidOf`, `disc`, `bandsOf`, `sphereOf`, `arcOf`, `ribsOf`, `dotsOf`,
`rodOut`, `fillet`), `tube.mjs` (`tubePieces`), `audit.mjs` (`recorder`, `settle`, `auditOrExit`),
the 3D runtime (and so `gl.mjs`'s `GLSL_TURN` and `glslPose` for shaders on turning groups), and
`holdStill`. When one of them is the only honest way to draw a part,
say so to the user instead of faking it.
