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
data class Frame(val o: Point3, val a: Point3, val u: Point3, val v: Point3)  // section 10
data class Knot(val s: Double, val r: Double, val smooth: Boolean = false)  // a meridian point
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
| `group(part, clip = null) { … }` | Art that live code moves by a screen offset. A `clip` path holds still while the group moves (the original's `data-hold` and `holdStill`): a rod sliding out of a gland (`rodOut`, section 10). |
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

## 10. Round parts on any axis

The Compose port of the original's `kit/lathe.mjs`, in `Vectors.kt`, `Frames.kt`, `Profiles.kt`,
`SmoothShading.kt`, `SmoothRows.kt`, `Lathe.kt`, `LatheOutline.kt`, `RoundSolids.kt`,
`RoundShapes.kt`, `Bands.kt`, `RoundLines.kt` and `Routes.kt`. Section 3 builds prisms that stand on
the floor; these turn a profile around **any** axis, for bells, chambers, flanges, valve bodies,
fittings, actuators lying at an angle and connectors on a vertical face. They return the same
`SolidPaths` as `slabOf`, so `solid(paths)` paints them. `LatheParityTest` holds every function to
the original's output, path for path.

Beyond the table at the top: `[s, r]` and `[s, r, 1]` are `Knot(s, r)` and `Knot(s, r, smooth = true)`;
the `fill`, `rims`, `seams` and `ends` strings are `LatheFill`, `Rims` and `Seams`; `[lo, hi]` fades are
`lo to hi`; option objects are named arguments; results the original returns as objects are classes
(`Bands`, `Band`, `Sides`, `Rod`, `ProfileCurve`, `LatheOutline`, `FadedDots`).

**Frames.** A frame is `Frame(o, a, u, v)`: an origin, the unit axis, and two unit radial directions.
A point on the part is `(s, r, angle)`: `s` along the axis from `o`, `r` from the axis, and `angle`
from `u` toward `v`.

| Function | What it does |
|---|---|
| `frameAlong(o, a)` | A frame on axis `a` through `o`, with `u` and `v` chosen for you. Use it for pipes, bosses and flanges at any angle. |
| `frameOf(o, a, u, v)` | A frame with explicit radial directions, so angles mean something: `frameOf(Point3(0.0, 0.0, z), Point3(0.0, 0.0, 1.0), Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0))` makes angle 0 point along +x. |
| `pointOf(F, s, r, angle)` | The world point. |
| `radialOf(F, angle)`, `tangentOf(F, angle)`, `angleToward(F, direction)` | Directions in the frame, and the angle that faces a direction. |

**Solids.** A meridian is a `List<Knot>` along the axis. Mark a knot `smooth = true` for a smooth
ring, so no crease line is drawn there: a bell, a dome, a fillet into a flange. A meridian with any
smooth knot is shaded smoothly on its own: `smooth` defaults to `Smooth.auto(meridian)`, which is
`Smooth()` when `softOf(meridian)` and null otherwise. Pass `smooth = null` to opt out, `Smooth()` or
`Smooth(rows = 4)` to force it on a profile without smooth knots, and `Smooth(slope = …)` or
`Smooth(slope = …, radius = …)` for an analytic curve.

```kotlin
class Smooth(rows = 6, slope: ((Double) -> Double)? = null, radius: ((Double) -> Double)? = null,
             steps: Int? = null, error = 0.35, depth = 8, fineness = 0.1)
```

**Smooth shading.** Without it every facet row gets one flat tone, and a curved profile shows a
staircase of tone blocks. With `smooth` the side of each meridian segment is shaded from an
interpolated normal: the tone boundaries become the true iso-light curves, sampled at `rows` levels
and emitted as one polygon per tone region, so they run as clean curves across the whole part.
- `Smooth()` interpolates each run of smooth knots with a monotone cubic Hermite curve
  (Fritsch–Carlson slopes: zero where the profile turns, a weighted harmonic mean elsewhere), and
  uses that one curve for the normals, the tone sheets, the silhouette and the hull, resampled until
  it is within 0.2 px of the chord. Shading and outline then describe the same shape.
- `Smooth(slope = …)` takes the normal from an analytic `dr/ds`, so a body cut into bands shares one
  normal field. With a `slope` alone the profile points are joined straight, so sample the analytic
  `r(s)` densely (20–30 knots), and mark the inner knots smooth, or each one draws a crease ring.
- `Smooth(slope = …, radius = …)` also takes the surface from an analytic `r(s)`: tone sheets, hull
  and silhouette all follow `radius` between the knots, so two knots are enough for a band. This is
  what `bandsOf` passes to every band.
- **Hand-typed knots are not a curve.** The monotone Hermite is only C1: its `dr/ds` plateaus at
  every knot and overshoots between them, so tone bands stall and then lurch, a staircase in
  scallops (the original draws the same). Any curved body larger than a fitting gets an analytic
  profile and `Smooth(slope = …, radius = …)`.
- `rows` is the starting sampling along each segment. Rows are split until every tone boundary is
  within `error` px of a chord, and wherever the number of bands changes down to `depth` levels or
  `fineness` px on screen, so a band that appears or disappears is born at zero width. Each tone is
  one polygon for as long as its own number of regions holds. `steps` overrides the facet count the
  tone arcs are sampled from.
- The result is marked `seamless`, which the renderer paints by stroking each tone region 0.4 px
  wide in its own colour, so the abutting sheets show no hairline seams. Flat caps (rings where `s`
  doesn't change) keep their facet shading.

All the solids below take the lathe options as named arguments, `steps`, `fill`, `hole`, `bevel`,
`rims`, `smooth` and `waist`, with the defaults of `lathe`.

| Function | What it does |
|---|---|
| `lathe(meridian, F, P, steps = stepsFor(rMax, 32, 160, scaleOf(P)), fill = LatheFill.Auto, hole: Knot? = null, bevel = 0.0, rims = Rims.All, smooth = Smooth.auto(meridian), waist = 0.5)` | The general solid of revolution. Facets are shaded in four tones by the kit's light and bucketed like `extrude`. `Auto` fills with the hull of the rings unless the true silhouette lies more than `waist` px inside it (a throat, a spool), and then fills and outlines the body with the silhouette on both sides joined by the visible end arcs (only when `s` rises along the whole side: a step falls back to the hull); `Surface` forces that, `Hull` forces the hull, `Facets` fills only the visible facets and draws no outline, for a concave or open shape seen from inside. `hole = Knot(s, r)` cuts a round hole into the fill and outline, for the end of a tube. `bevel` adds a highlight inset from a closed cap (a meridian end at `r = 0`). `rims` keeps the outline on the end rings: `All`, `None`, `First`, `Last` or `Ends`; only hull edges along the first or last ring are dropped. |
| `solidOf(profile, F, P, …)` | Closes a profile to the axis at both ends and picks `steps` from the largest radius. The usual way to make a turned part: `solidOf(listOf(Knot(0.0, 12.0), Knot(6.0, 12.0), Knot(8.0, 9.0, true), Knot(20.0, 9.0)), F, P)`. |
| `bandOfProfile(profile, F, P, …)` | An open band with no caps. Prefer `bandsOf`, which cuts the whole body at once. |
| `bandsOf(profile, cuts, F, P, slope = null, radius = null, steps: Int? = null, rows = 6, rims = Rims.None, fill = LatheFill.Auto, samples = 72)` | One curved body (`s` rising along `profile`) cut at the `s` values in `cuts` into depth-sortable bands that share one normal field, one surface and one facet count. Pass the analytic `slope` and `radius`, or neither: then `profileCurve(profile)` makes them. Returns `Bands(bands, radius, slope, steps, s0, s1)`. Each `Band(s0, s1, profile, paths, outline, seam)`: `paths` is ready for `solid` with its outline cleared, `outline` is the band's own chord outline, `seam` the visible arc where it meets the next band (put it in `inner`). `bands.sidesOf(from = s0, to = s1, ends = Seams.None, count = samples)` returns `Sides(d, points)`: the body's two analytic side edges over that span as one path, with the visible rim arcs at `ends` (`Both`, `First`, `Last`), and their 3D points. Draw `d` as one line over the bands. |
| `profileCurve(profile, slope = null, radius = null)` | `ProfileCurve(s0, s1, radius, slope, analytic)` for a profile whose `s` rises: the curve `smooth` draws through it, clamped to its ends. Throws if `s` doesn't rise: a step in a profile is a crease and belongs to a separate solid. |
| `outlineOf(meridian, F, P, steps, fill, hole, rims, smooth, waist)` | Only the `LatheOutline(fill, outline)` of `lathe`, for a body whose outline you draw separately. |
| `softOf(meridian)` | True when any knot is smooth. |
| `curveRuns(meridian)` | The Hermite `Curve` (`radius(s)`, `slope(s)`) of each segment inside a smooth run, null elsewhere. |
| `disc(s0, s1, r, F, P, …)` | A short cylinder on the axis: pucks, flanges, bosses, pins, plugs. |
| `ringBand(s0, s1, rIn, rOut, F, P, …)` | An annulus with thickness: a flange or collar around something. |
| `stepsFor(radius, least = 16, most = 112, scale = 0.0)` | A facet count. With `scale` (`scaleOf(P)`) it keeps every facet about 3 px long on screen; the solids use that (32–160) when you pass no `steps`. Facets only set the tone fills: outlines, rims, creases and bevels are sampled from the true circle at about 1.5 px and end exactly where the faces turn away. |
| `saddleOf(F, a, start: (Double) -> Double, s1, P, steps = stepsFor(a, 24))` | A saddle pad: a cylinder of radius `a` along `F.a` from a curved host surface to a flat face at `s1`. `start(angle)` is where each generator leaves the host. The overload `saddleOf(F, a, host: Double, s1, P)` is for a host cylinder of radius `host` perpendicular to `F.a` (`saddleStart(host, a, angle)` gives each generator's start). It meets the host along the true intersection: seat flanges on it wherever a flat flange on a curved body would leave a rim gap. |
| `prismOf(F, polygon: List<Floor>, s0, s1, P)` and `stadium(r0, r1, offset, steps = 32)` | A prism of any convex polygon in the frame's `u`, `v` plane (`Floor(x, y)` is `x·u + y·v`), extruded along `F.a`, with facet tones, the hull as fill and outline, and creases only at sharp corners. `stadium` is the hull of two circles, for a plate joining a motor to an actuator. |
| `sphereOf(centre, r, P, steps = 48)` | A ball with exact smooth tones: each tone region is the projected hull of a spherical cap, four small polygons instead of a turned profile. Use it for rod-end bearings, tee fittings and knobs. It is `seamless`. |
| `silhouettePoints(profile, F, P, samples = 72, slope = null, radius = null)` | The two side edges of `silhouetteOf` as 3D point lists. |
| `silhouetteOf(profile, F, P, samples = 72, slope = null, radius = null)` | The two analytic side edges of the surface of revolution through `profile` (linearly interpolated), as one path of two polylines: where the surface turns away from the viewer. Where it faces the viewer head-on a sample is skipped. Pass the analytic `slope` so the edge doesn't kink at every profile point. |

**Lines and dots on round surfaces.** These draw only what faces the viewer. `slope` is `dr/ds` of
the surface at that ring, so a line on a cone or bell hides where the real surface turns away.

| Function | What it does |
|---|---|
| `arcOf(F, s, r, P, slope = 0.0, steps = 0, least = 0.02, inward = false, from = 0.0, to = 2π, trim = 0.3)` | The visible arc of a ring on the surface: seams, bands, weld lines, a lip. The limb angles are solved exactly, and each end that stops at the limb is shortened by `trim` px so its round cap stays inside the outline stroke. `inward = true` draws the arc you see on an inner surface, such as the inside of a nozzle. `least = -1.0` gives the whole ring. Draw seams under the outline: `solid(paths, inner = art { line(arcOf(…), tone = Tone.Faint) })`. |
| `circleOf(F, s, r, P, steps = 0)` | A full closed circle: a face ring, a bore, a void you fill. |
| `ribsOf(F, s0, s1, r, count, P, phase = 0.0, twist = 0.0, least = 0.12, r1 = r, seams = Seams.None)` | Visible axial lines: a knurl, ribs or fins along any axis. `twist` makes them helical and `r1` runs them along a cone. `seams` (`Both`, `First`, `Last`) adds the seam arcs at `s0` and `s1`, so a rib row and the seams it ends on share one `s`. |
| `ribsOf(F, s0, s1, r, count, P, fade = lo to hi, phase, twist, r1, seams)` | The same, fading toward the turn: returns `List<Pair<IsoPath, Double>>`, each rib's opacity a smoothstep of how much it faces you, seams at full opacity. Draw them with `faded(…)`. |
| `rodOut(F, from, to, r, P, stroke = 0.0, …)` | A rod that slides out of a gland at `s = from` (`F.a` points out of it), its tip at `to` at rest, travelling up to `stroke` further out, or the same rod retracting into it. Returns `Rod(paths, clip, from, to, stroke, reach)`: `paths` is one rod from `from − stroke` to `to`, `clip` covers it from the gland face to `to + stroke`. Draw it as `group("rod", clip = rod.clip) { solid(rod.paths, lit = true) }` and move the group with `translateAlong(P, mul3(F.a, x))`: the clip holds still while the rod slides. |
| `spokesOf(F, s, r0, r1, count, P, phase = 0.0, sweep = 0.0, from = 0.0, to = 2π)` | Radial lines on a face, for vanes and turbine blades. |
| `dotsOf(F, s, r, count, P, phase = 0.0, slope = 0.0, least = 0.08, all = false)` | Screen points of a bolt circle on the visible side, for `dots(…)`. Use `all = true` for a face you see head-on. |
| `dotsOf(F, s, r, count, P, fade = lo to hi, phase, slope, all)` | The same, fading toward the turn: `FadedDots(points, alphas)`, drawn with `dots(d.points, alphas = d.alphas)`. A `least` well above 0 stops a circle in the middle of the surface; fade it instead (`fade = 0.04 to 0.42`). |
| `fadeOf(value, lo to hi)` | The smoothstep both fades use. |
| `facingOf(F, angle, P, slope = 0.0)` | How much a surface at that angle faces the viewer. Positive means visible. |

**Paths in 3D**, for pipes and cables.

| Function | What it does |
|---|---|
| `fillet(points, radius, perArc = 7, projection = null, tube = 0.0, margin = 1.3)` | Rounds every corner of a polyline with a bend of `radius`, clamped to 0.48 of the shorter leg. With `projection` and `tube` (the pipe radius) each bend grows to `margin · tube / (0.7 · f²)`, `f` being how squarely the bend's plane faces the viewer, so the outline never folds back on itself. |
| `resample(points, spacing)`, `pathLength(points)`, `cut(points, from, to)` | Even spacing, total length, and a sub-path by arc length. |
| `pointAlong(points, at)`, `tangentAlong(points, at)` | Position and direction at an arc length. |
| `spiral(F, s, r0, r1, a0, a1, steps = 28)` | 3D points of a widening spiral, such as a pump volute. |
| `arcPoints(F, s, r, a0, a1, steps = 24)` | 3D points around a ring: a manifold, a deluge ring, a harness lane. |

**Camera, vectors and helpers.**
- `viewOf(P)` is the unit direction to the viewer, so `dot3(p, viewOf(P))` equals `depthOf(p, P)`.
- `lightOf(P)` is the kit's light; `scaleOf(P)` the screen units per world unit.
- `toneOf(normal, P)` and `bandOf(score)` give the tone bucket: 0–3 index `shades`, 4 is `top`.
- `hullOf(points)` and `runsOf(flags)` are the hull and run helpers the module uses itself.
- The vector helpers on `Point3` are `dot3`, `add3`, `sub3`, `mul3`, `len3`, `unit3`, `cross3` and
  `lerp3`. `len3` reproduces V8's `Math.hypot`, so lengths match the original to the bit.

**A long curved body in bands.** Cut it with `bandsOf` so each band depth-sorts against what wraps
it. Every band shares the body's `steps`, normal field and surface, so tone curves cross every joint
without a step, and the outline comes from `sidesOf` as one curve:

```kotlin
val nozzle = bandsOf(listOf(Knot(3.2, bell(3.2)), Knot(116.0, bell(116.0))), listOf(14.0, 28.0, 44.0, 60.0),
    ENGINE, P, radius = ::bell, slope = ::bellSlope, steps = 96, rows = 8)
nozzle.bands.forEach { band -> solid(band.paths, inner = art { line(band.seam, tone = Tone.Faint) }) }
line(nozzle.sidesOf(3.2, 106.0, ends = Seams.First).d, tone = Tone.Hi)
```

Don't build bands by hand from separate `bandOfProfile` calls on two-knot chords with their own
Hermite fits: each band then has its own end slopes, its tones step at every joint, and its chord
outline kinks.

**Known traps.**
- **Four-tone quantisation follows the slope of each facet row** when a body isn't smoothed. Mark
  every curved knot smooth and the shading follows on its own; a profile of hard knots that still
  describes a curve needs `smooth = Smooth()`. Use `bandsOf` on a body cut into bands.
- **Smooth shading is heavier.** Use `sphereOf` for balls and keep `rows` low on small parts. Build
  round parts once into static art; rebuild one per frame only if it really turns.

## Not in the Compose kit yet

`tube.mjs` (`tubePieces`), `audit.mjs` (`recorder`, `settle`, `auditOrExit`), `gl.mjs` and the 3D
runtime. When one of them is the only honest way to draw a part, say so to the user instead of
faking it.
