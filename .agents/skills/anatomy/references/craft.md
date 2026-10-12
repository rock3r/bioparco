# Craft

How to make a figure look crafted rather than diagrammed. Adapted from the original's
`references/craft.md` (MIT), with its recipes in Kotlin. Recording a figure for the audit is in
`audit.md`, and shaders are in `shaders.md`.

## Contents
1. What "crafted" means
2. Anti-patterns
3. Parts vocabulary, with recipes
4. Tones
5. Painter's order
6. Camera and composition
7. Themes
8. Exploded views
9. Things on faces
10. Round parts and pipes

## 1. What "crafted" means

The approved figures share five qualities.

1. **A real object you could build.** The reader names it at a glance: a test rig, a dial indicator,
   a chest of drawers, a gantry plotter, a lock. It is invented for the subject, never traced from a
   reference.
2. **Many small, precise parts.** A good inline figure has 30–60 solids and 300–500 paths. Every part
   rests on another part, aligns to its neighbours and has a job. Density comes from structure (feet,
   rails, stops, bushings, knobs), not from texture.
3. **Volume.** Parts have real thickness, sides shaded in four tones, lighter tops, bevels that catch
   light, and a soft halo underneath.
4. **Calm.** Thin 0.6 px lines and a restrained palette. One lit accent tells the eye where to look.
   Motion is eased and slow enough to follow.
5. **Truth.** The readout shows live, correct numbers, and the motion follows the real model: springs
   with real stiffness, true sizes and thresholds.

## 2. Anti-patterns

Reviewers rejected each of these, so don't do them:

- **Abstract stacks.** Floating plates, slabs or tiles standing for "layers" are too abstract to read
  as anything. If you need layers, make them drawers, trays in a rack, plates on guide pillars, or
  cards in a holder.
- **One big plain slab.** A large surface with a few lines is too flat and bland. Break it into a
  base, a carriage and parts; add a groove, screws, a ruler and a label plate.
- **Blueprint furniture.** Leader lines, balloons with numbers, dimension arrows, hatching, dashed
  hidden edges and floating labels belong in the legend and caption, not the drawing.
- **Lines that stop in mid-air.** Guide fans, connectors that end short of a part, or a crease that
  stops before the silhouette corner. Every line must land on something.
- **Wireframes.** See-through boxes where the back edges show.
- **Colour everywhere.** Keep it monochrome unless colour is the subject: one lit accent plus true
  RGB only for colour channels. A fluid is not colour by default: oil in a sight glass is a tint of
  the figure's own inks.
- **Text in the drawing.** At most one tiny engraved label (`faceText` on a face). Everything else goes
  in the card corners, legend or caption.
- **Copying a reference.** Take mood and finish from a reference, then invent the object.
- **Raw pointer jumps.** Parts that snap to the cursor. Ease everything.
- **Lines through solids.** On the first Raptor the owner zoomed in and found cables passing through
  the main ducts and through each other, "which makes no sense". At 1× it looked fine. Every wire keeps
  clear of everything but its own two ends, and every crossing is drawn in true depth order.
- **Textures that stop.** Rows of bolt dots that stop dead in the middle of a surface, a weld seam
  inset 6 units from each rounded corner, a row of faint ticks floating on a bare plate with no ruler
  under them. If a texture has to end, "we'd rather fade them out at the end than an instant stop". A
  seam on a box runs edge to edge round the visible corners (`sideSeam`), ticks hang from a ruler
  strip's edge, and a line that must stop on a face ramps out (`fadeLine`).
- **Stand-in shapes.** Several overlapping copies of a rod to fake its extension: each copy's lit edge
  stops where the next one's fill starts, and the outline is notched while it moves. Draw one part,
  built once at full length, and move it.
- **Fixing one close-up by eye.** Nudging a part until one screenshot looks right breaks another pose
  or another theme. Check every pose in both themes.

## 3. Parts vocabulary, with recipes

`P` is the projection, the kit is imported from `dev.sebastiano.anatomy.kit`, and the recipes run
inside `art { … }`. Heights are in world units, with z up. Draw each recipe's solids in the order
listed. `at(x, y, z)` is `iso(Point3(x, y, z), P)`.

### Base plate on feet
```kotlin
val PLATE = Plan(-128.0, -64.0, 262.0, 128.0, 8.0)
halo(haloOf(PLATE, -17.0, 12.0, P))
for ((x, y) in corners(PLATE, 22.0).sortedBy { depthOf(Point3(it.x, it.y, 0.0), P) }) {
    solid(cylinder(x, y, 7.0, -17.0, 1.6, P, 28), tone = Tone.Lo)      // flange
    solid(cylinder(x, y, 5.4, -15.4, 3.4, P, 28), tone = Tone.Mid)     // foot
}
solid(slabOf(PLATE, -12.0, 12.0, P, 8, 1.6), tone = Tone.Mid)
line(planOutline(insetPlan(PLATE, 5.0), 0.0, P), tone = Tone.Lo)       // groove
val screws = corners(PLATE, 10.0)
line(IsoPath.join(screws.map { ring(it.x, it.y, 2.2, 0.0, P, 16) }), tone = Tone.Lo)
dots(screws.map { at(it.x, it.y, 0.0) }, size = 0.5)
```

### Ruler strip and label plate
```kotlin
val strip = Plan(-110.0, PLATE.y + PLATE.d - 15, 230.0, 7.0, 1.0)
solid(slabOf(strip, 0.0, 0.6, P, 3), tone = Tone.Lo, crease = null)
val t = topTicks(strip.x + 4, strip.x + strip.w - 4, 4.0, 5, strip.y + 0.8, 0.6, 2.0 to 3.6, P)
line(t.minor, tone = Tone.Lo); line(t.major, tone = Tone.Mid)
// label plate: slabOf(plan, 0.0, 0.8, P, 4) + planOutline(insetPlan(plan, 1.6), 0.8, P) (Faint)
//   + three lineOnTop rule lines of different lengths (Lo, free = Free.Both) + two screw dots
```
Ticks only live on a ruler strip or a dial face, rooted on its edge: `topTicks` grows each tick from
`edge`, so put `edge` on (or within 1 unit of) the strip's edge, and `sideTicks` hangs them from
`top`, so pass the face's top edge. The label plate's rule lines stand for printed text, so they are
the one place a line may stop on a bare face: mark them `free`.

### Seams, welds and trim lines on a box
```kotlin
val seam = sideSeam(TANK, 14.0, P, 6)
solid(slabOf(TANK, 6.0, 98.0, P, 6, 1.2), tone = Tone.Mid, inner = art { line(seam, tone = Tone.Faint) })
```
A seam, weld bead, band or lid line on a box wraps the visible corners and ends exactly on the
vertical silhouette edges, like `sideArc` on a cylinder. Pass the slab's own `steps`, and pass it as
`inner` so its ends sit under the outline. A line that belongs to one face runs the full face, edge to
edge: `segment(Point3(x, y, z0), Point3(x, y, z1), P)` with `z0` and `z1` the face's bottom and top,
never `z0 + 3`. Slots and vents are closed outlines, so they have no ends at all.

### Rails, end stops and a carriage
```kotlin
for (y in listOf(-20.0, 15.0)) solid(slabOf(Plan(2.0, y, 122.0, 5.0, 1.0), 0.0, 3.0, P, 3, 0.4), tone = Tone.Mid)
// stops: slabOf(Plan(x, -23.0, 5.0, 46.0, 1.2), 0.0, 7.0, P, 3, 0.5) + 2 screw dots on top
group("carriage") {                       // built at x = 0, moved with translateAlong
    solid(slabOf(Plan(-28.0, -24.0, 56.0, 48.0, 3.0), 3.0, 5.0, P, 5, 0.6), tone = Tone.Mid)
    // bushings: small slabs at the four corners, taller than the plate, with screw dots
    // rivets: a row of 0.4 dots along the front edge
    // handle: a small neck slab plus a knurled cylinder on the front face
}
```

### Post, collar, clamp and thumb screw
```kotlin
solid(cylinder(px, py, 5.0, z0, 4.0, P, 28), tone = Tone.Mid)                       // collar
solid(cylinder(px, py, 3.2, z0 + 4, h, P, 28), tone = Tone.Mid, crease = null)      // post
line(sideArc(px, py, 3.2, z, P, 16), tone = Tone.Faint)                             // seam rings
solid(slabOf(Plan(px - 7, py - 7, 14.0, 14.0, 2.5), zc, 12.0, P, 4, 0.6), tone = Tone.Mid)  // clamp
// thumb screw: a 2.8 × 3 neck slab from the clamp face + cylinder(r 2.6, h 6) + knurl(…, 18)
```
A post that passes through a clamp is drawn in two segments, below and above, around the clamp.

### Knurled knob with a pointer
```kotlin
solid(cylinder(cx, cy, 6.0, z, 4.0, P, 32), tone = Tone.Mid)
line(knurl(cx, cy, 6.0, z, z + 4, 36, P), tone = Tone.Lo)
line(lineOnTop(Floor(cx - 3, cy), Floor(cx + 4.6, cy), z + 4, P), tone = Tone.Hi)   // pointer
```

### Coil spring around a rod (split for painter's order)
```kotlin
val c = coil(xFrom, xTo, Floor(rodY, rodZ), 4.2, 6.0, Axis.X, P)
line(c.back, tone = Tone.Lo)                                                         // BEFORE the rod
solid(slabOf(Plan(x, rodY - 2, w, 4.0, 1.8), rodZ - 2, 4.0, P, 3), tone = Tone.Mid) // rod
line(c.front, tone = Tone.Mid)                                                       // AFTER the rod
```
Rebuild the coil each frame when it compresses, by changing `xFrom`/`xTo`. It is cheap.

### Dial gauge (face up)
```kotlin
solid(cylinder(x, y, R, z, 10.0, P, 64, 0.8), tone = Tone.Hi, crease = Tone.Lo)   // puck
line(knurl(x, y, R, top - 3.4, top, 84, P, 0.3), tone = Tone.Lo)                  // knurled bezel band
line(sideArc(x, y, R, top - 3.4, P, 40), tone = Tone.Faint)                       // band seam
line(ring(x, y, R - 2.2, top, P, 64), tone = Tone.Lo)                             // bezel ring
val t = radialTicks(x, y, R - 3, 50, 5, top, 1.3 to 2.8, P)                       // minor Lo, major Mid
// crown: two small slabs on the +x side; lug: a slab joining the arm; hub: ring(x, y, 1.6, top, P, 16)
// needle: lineOnTop(tail, tip, top + 0.2, P), Tone.Lit, width 1.1, rebuilt per frame
```

### Drawer with a pull and label holder
```kotlin
// case: slabOf(casePlan, z, h, P, 4, 0.8)
// front: a thin slab on the near face; pull: two posts plus a bar (small slabs)
// holder: an inset outline on the front with a card slab inside; rivets as 0.45 dots
// open: a group moved along its axis with translateAlong; its box (floor plus 3 walls) is
//       drawn before the front, and its contents are engraved on the floor
```

### Turntable with degree ticks
```kotlin
solid(cylinder(cx, cy, R, z, 4.0, P, 72, 0.8), tone = Tone.Mid)
val t = radialTicks(cx, cy, R - 1, 72, 6, z + 4, 1.2 to 2.6, P)
// rotate: rebuild only what turns (or the object on top) at the new angle each frame
```

### Perforations and vents
```kotlin
dots(dotGrid(x, y, cols, rows, gap, zTop, P), size = 0.45, tone = Tone.Lo)
```

### Lamp, probe and finger
These are stacks of cylinders: housing, cap ring, collar, stem, pad. Seam rings come from `sideArc`,
and the lock screw is a small slab plus a cylinder head on the side.

## 4. Tones

| Element | Tone |
|---|---|
| Base, feet, floors | `Lo` |
| Structure: posts, rails, frame, carriage, case | `Mid` |
| The subject: the thing being explained | `Hi` |
| The part the reader should watch now | `lit = true` (one only) |
| Grooves, inset outlines, floor grid | `Faint` |
| Minor ticks | `Lo` |
| Major ticks | `Mid` |
| Zero mark, pointer line | `Hi` |
| Screws and rivets | dots `Mid` at 0.4–0.55 |
| Crease (top/side join) | `Faint` (default), `Lo` on `Hi` parts |

A `solid` with `lit = true` gets a lit edge and a lighter top. Use it for the moving subject, the
plunger or a picked drawer. "One accent" means one subject: several solids that together are the
thing to watch (a stack of tokens, a set of pins) may all be lit. A line in `Tone.Lit` is the needle or pointer of that subject.

## 5. Painter's order

- Draw the base first, then everything resting on it.
- Within a level, sort by `depthOf(centre, P)`, ascending: far before near.
- **Split wrapping parts.** For a spring or a frame around a carriage, draw the back half, then the
  inner part, then the front half. In the test rig: back frame bars → rods with the back coil halves
  → carriage → glass → front coil halves → front bars → dial.
- **Moving parts** are drawn at their slot in the order, inside a `group` that moves them. If a moving
  part can pass in front of and behind the same neighbour, split the neighbour or bound the motion so
  the order never changes. If it truly must change, draw the moving part in more than one slot and
  show only the one that is right for this frame.
- **Stacked parts.** Where one part sits above another at the same floor position (a stack of
  coins, a lid on a box), the upper one is nearer the viewer wherever they overlap on screen: paint the
  lower one first, also when the upper one is moving.
- **Things standing on a stepped surface.** Order the steps so the taller ones are further back. A
  probe resting on a lower step then can't be hidden by a taller one in front of it.
- Vertical parts that pass through a horizontal one (a post through a clamp) are drawn in two
  segments, below and above, around the horizontal part.
- **A clip cut is an edge.** Where something is cut against another part, the cut is flush with the
  joint plane and drawn with a stroke.

## 6. Camera and composition

- Default camera: azimuth 45°, elevation 30°. Rotate the azimuth (35–60°) so the face that carries the
  mechanism points at the viewer. The test rig uses 57° to show the long side and the dial indicator
  42°. Raise the elevation to look into something open: the ripple tank uses 44°.
- Fit with `fitProjection(boxCorners(...) + tallest points, width, height, pad = 24–34, azimuth = …)`.
  Typical viewBoxes are 600 × 340–380.
- The object fills about 55–65% of the card's width. A tall object is fitted by its height and comes
  out narrow: lower its tallest parts or turn its long side toward the viewer. Leave air around it, don't let it touch the corner
  labels, and keep sizes consistent across a set of figures.
- At 390 wide (`capture --width 390`), check it still reads.

## 7. Themes

`Palette.Light` and `Palette.Dark` are token sets: every fill and stroke reads one.

- **Paper** equals the card colour, so fills hide what is behind without showing as patches.
- **Shades.** Four steps from darkest (0, facing away from the light) to lightest (3), about 3% apart
  in luminance.
- **Lines.** In dark, `hi` is near-white and `faint` barely above the paper. Light inverts this.
- **Halo.** A blurred dark fill under the base: strong in dark, about 30% in light.
- **Light ships.** Design light first and keep dark working.
- **Every colour is a token.** A colour typed into a figure is a theme bug waiting to happen. If a
  figure needs a colour the palette lacks, add it to `Palette` with a light and a dark value.

## 8. Exploded views

An exploded view earns its place when the parts are the explanation (what feeds what, in what order):

- Choose the assemblies the real thing comes apart into, not single solids. Each is a `group`.
- Move each assembly along its real disassembly direction, far enough that its silhouette clears its
  neighbours: down along the axis for stacked stages, out sideways for side units.
- Every part keeps a home: parts that bridge two assemblies go with the one they stay attached to,
  and when apart they still end on something of their own.
- One spring per assembly, staggered by about 35 ms, in an order that lets nothing pass through
  anything: whoever is outside leaves first, and closing reverses the order. `motion.md` has the
  loop.
- List the assemblies in the legend, and let hover and the keyboard select one: the selected parts
  become the one `lit` accent.

## 9. Things on faces

- **Text the machine displays** (a counter's digits, a typed line) is part of the object, not a
  label, so the one-label rule doesn't apply. Keep it in the face's plane (`faceText` with a
  `topMatrix` or `sideMatrix`) and fit it: the cap height is about 0.55 of the window's height and the
  widest string you will ever show must fit its width with a margin.
- **Art in a face plane.** For more than text, draw inside the face's transform yourself: build a
  Compose `Matrix` from the `FaceMatrix` (`u`, `v`, `origin`) and draw with
  `draw.withTransform({ transform(matrix) }) { … }` in local face coordinates, clipped to the window.
  Add it to the kit if a second figure needs it.
- **Content seen through an opening** sits behind the face: draw the opening's inner walls between the
  face and the back plane, shaded by facing, and clip the content to the opening.

## 10. Round parts and pipes

Engines, pumps, valves, lamps and instruments are mostly turned parts joined by pipes. The
round-parts kit builds them along any axis (`kit.md`, section 10), and the tube kit runs pipes,
hoses and cables between them (`kit.md`, section 11).

### Turned parts: think in meridians
Write a part as its half-section: a list of `Knot(s, r)` from one end to the other, and turn it with
`solidOf(profile, F, P)`.

```kotlin
val F = frameAlong(Point3(0.0, 0.0, 12.0), Point3(1.0, 0.0, 0.2))      // any axis
val body = listOf(Knot(0.0, 12.0), Knot(6.0, 12.0), Knot(6.0, 9.0), Knot(8.0, 9.0, true), Knot(20.0, 9.0))
solid(solidOf(body, F, P), tone = Tone.Mid, inner = art { line(arcOf(F, 14.0, 9.0, P), tone = Tone.Faint) })
```
- **Steps and shoulders** are where the radius jumps at one `s`: `Knot(6.0, 12.0), Knot(6.0, 9.0)`.
  They make the crease lines that read as machining.
- **Curves** are many knots marked smooth (`Knot(s, r, true)`), so no crease is drawn across them: a
  bell, a dome, a fillet into a flange. The kit shades any profile with a smooth knot smoothly on its
  own (`Smooth.auto`), so the shading follows the curve instead of stepping facet by facet; pass
  `smooth = null` only to opt out. A knot that is not marked smooth draws a crease ring, even on an
  analytic profile, so mark every inner knot of a curve.
- **A waisted body is never hulled.** A profile that narrows in the middle (a throat, a spool) has a
  concave outline. `lathe` notices when its true silhouette lies more than half a pixel inside the
  hull of its rings and then fills and outlines it with the silhouette and the visible end arcs
  (`LatheFill.Auto`, or force `LatheFill.Surface`). That needs `s` to rise along the whole side: a
  shoulder in the profile falls back to the hull, so a flared bell with a stem on top is two solids,
  the bell and a `disc`. Drawn as a hull, a throat is a straight cone with a pale unshaded strip and
  a seam that stops short of the outline.
- **Seams and weld lines** on a curved surface are `arcOf(F, s, r, P, slope = …)`, with the profile's
  slope at that ring, drawn `Faint` and passed as the solid's `inner`, so they sit under the outline.
  They end exactly at the limb, a little short of it. Added after the solid instead, each seam's
  round cap lands on the edge stroke and the dark-theme outline looks dashed at 4×.
- **A long body** (a nozzle, a barrel) is cut into bands with `bandsOf(profile, cuts, F, P, radius =
  …, slope = …)`, so each band depth-sorts against what wraps it. The bands share one `steps`, one
  normal field and one surface, their own outlines come cleared, and `sidesOf` draws one continuous
  outline for the whole body, so the tone curves run through every joint without a step and the
  sides don't kink. Without an analytic `radius` and `slope` it fits one curve through the whole
  profile, never one per band.
- **A ring around a band** (a stiffening ring on a chamber, a collar) splits the band: band, ring,
  band. Drawn as one long band with the ring on top, the ring's back half paints over the band (or
  the band hides the ring's front); split, each piece orders itself.
- **An open end** is a `ringBand` lip, with a filled void (`circleOf`) for the dark inside and the
  inside arc from `arcOf(…, inward = true, least = -1.0)`. Bolt dots go around the lip.

### Parts on a round body
- **Flange:** its back face sits on the surface it mounts to (on a pipe, on the route end, so the
  route ends exactly on the mount's surface), and the disc runs from there back along its axis
  (`centre = end − tangent · t/2`): `disc(-t / 2, t / 2, r, frameAlong(centre, axis), P)`, with a bolt circle
  `dotsOf(F, faceS, 0.74 * r, 8, P, all = true)` on the face that points at the viewer. Skip the bolts
  when the face is edge-on (`abs(dot3(F.a, viewOf(P))) < 0.12`).
- **Boss or port** on a curved wall: a short `disc` along the outward normal, with a `Faint` face ring
  (`circleOf`) when it faces you. Make it at least as wide as the flange that lands on it; a flange
  that overhangs its boss floats at the rim.
- **Saddle pad.** A flat flange on a curved body leaves a gap at its rim (the sagitta,
  `R − √(R² − a²)`): an 11.6 flange on an 18.4 body stands 4 units off at the sides. Wherever that gap
  would be more than about 0.15, seat the flange on a pad: a short cylinder whose base follows the
  host surface, `saddleOf(F, a, hostRadius, h, P)` on a cylinder, or `saddleOf(F, a, { angle -> … },
  h, P)` with the start of each generator found from the host's shape. Keep the pad inside one band
  of the host and off the host's silhouette: a pad that straddles the silhouette is half in front of
  and half behind its host, and no painter's order draws it. **A saddle belongs to its host's
  group**: its base is cut to the host's curve, so if it flies out with what lands on it, the cut
  base is on show apart.
- **Rod out of a gland.** A piston rod, a plunger or a slide whose visible length changes as it moves
  is one rod, built once at full length (visible plus stroke), with the part inside the gland clipped
  away by a clip that holds still:
  ```kotlin
  val ROD = rodOut(F, gland, tip, r, P, stroke = stroke)          // F.a points out of the gland
  group("rod", clip = ROD.clip) { solid(ROD.paths, lit = true) }  // in the static art
  drawArt(ART) { part -> if (part == "rod") translateAlong(P, mul3(F.a, out)) else null }
  ```
  The clip covers the rod from the gland face to `tip + stroke` and stays put while the group moves,
  so the gland face stays put while the rod slides through it. The same rod retracts into a gland
  when its offset goes the other way. Draw a faint seal ring (`circleOf` at the gland face, radius
  `r + 0.4`) on the gland. Never fake the extension with overlapping copies: each copy's lit edge
  stops where the next one's fill starts, and the outline is notched every few units while it moves.
- **Valve on a pipe:** a bulged `lathe` profile on `frameAlong(point, tangent)` turned smooth, with
  bolt dots on both end faces, a round neck and an actuator box with a lid outline and two screws.
  It sits on a straight leg (body length plus both fillet cuts), the pipe is gapped under its body,
  and the actuator stands perpendicular to the pipe: up on a level pipe, sideways (a round can) on a
  vertical one, never along the pipe.
- **Volute:** `spiral(F, s, r0, r1, a0, a1)` fed to `tubePieces` with a radius that grows along it.
  Keep its centreline at housing radius plus tube radius plus 0.3, so it wraps the housing
  tangentially; a volute sunk into the housing is a pipe through a solid:
  ```kotlin
  val housing = frameOf(Point3(110.0, 40.0, 0.0), Point3(0.0, 0.0, 1.0), Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0))
  val volute = spiral(housing, 7.0, 16 + 2 + 0.3, 16 + 5 + 0.3, 0.3, 5.5, 40)
  tubePieces(volute, { t -> 2 + 3 * t }, P, maxLength = 10.0).forEach { piece -> put(piece.mid) { tube(piece) } }
  ```
- **Ball joints and tees** are spheres (`sphereOf`): a rod-end bearing on an actuator, a knob, a cast
  tee where a branch meets a manifold.
- **Knurls, ribs and fins on a tilted axis:** `ribsOf`. Vanes on a face: `spokesOf` with a `sweep`.

### Pipes
- Lay out a route as a few straight legs between real ports, then `fillet` it with a bend of about
  2.6 × the pipe radius (`fillet(legs, 2.6 * r, projection = P, tube = r)`). Pipes bend; they don't
  kink.
- End every pipe on a port with a flange, or on a block with a terminal dot. A pipe that stops in the
  air is a line that stops in the air.
- Add the parts a real line has: bands where it is clamped or welded, a bellows where it must flex (a
  run of thin `disc`s), and a valve where flow is controlled. Each one wraps the pipe, so gap the pipe
  under it (`kit.md`, section 11).
- Use few sizes, all big enough to tell apart: main ducts (r 5–8), feed lines (r 2–4), sense lines
  (r 0.9–1.5), cables (r 0.5–0.65).

```kotlin
val r = 5.0
val t = 3.0                                                          // flange thickness
val duct = fillet(listOf(Point3(-140.0, -60.0, 12.0), Point3(-80.0, -60.0, 12.0),
    Point3(-80.0, -60.0, 56.0), Point3(-40.0, -60.0, 56.0)), 2.6 * r, projection = P, tube = r)
val dense = resample(duct, 2.2)                                      // what tubePieces measures
val total = pathLength(dense)
tubePieces(duct, r, P, maxLength = 14.0, gaps = listOf(0.0 to t, total - t to total))
    .forEach { piece -> put(piece.mid) { tube(piece) } }
fun flange(end: Point3, tangent: Point3) {
    val centre = sub3(end, mul3(tangent, t / 2))
    put(centre) { solid(disc(-t / 2, t / 2, r * 1.6, frameAlong(centre, tangent), P), tone = Tone.Mid) }
}
flange(duct.first(), mul3(tangentAlong(dense, 0.0), -1.0))
flange(duct.last(), tangentAlong(dense, total))
```
`put` is the depth-sorting helper from `kit.md`, section 11: every piece and every collar goes into
one list keyed by `dot3(at, viewOf(P))`, and the art is built in key order.

### Choosing waypoints
Route the big lines first (ducts, downcomers, manifolds), then feed lines, then sense lines, then
cables, so each smaller line routes round what is already there. For each route:
- **Start on the port's normal.** The first leg leaves the port straight out along its surface
  normal, long enough for the flange, then the bend: `fillet` takes at most 0.48 of the shorter leg,
  so a leg shorter than about twice the bend radius gives a tight bend, and a turn right at a port
  facing the viewer pinches ("Clearance and crossings").
- **Follow structure.** Legs run parallel to the body they follow (along a chamber, up a downcomer)
  or round it on a ring at a fixed radius (`arcPoints`), offset from its skin by the body's own
  clearance: skin radius plus about 1.5 × the line's radius. Lines that fan across the subject's
  face to reach their block read as a scribble.
- **Stay on your side of the subject.** A line that serves a port on the far side goes round the back
  or round the side at a ring, never diagonally across the front of the thing being explained.
- **Prefer legs across the screen.** A leg that runs straight toward or away from the viewer projects
  short and makes every turn at its ends a cusp or a V.
- **Leave room for fittings.** A valve, bellows or clamp needs a straight leg at least its body plus
  both bend allowances (`rBend · tan(turn / 2)` each). On a bend the straight body cuts into the pipe.
- **Don't land on a rod.** A pipe's last leg must not run along a rod, tie rod, post or rail on
  screen, and its end fitting must not sit on one's outline: a hose whose last leg runs parallel to a
  tie rod, 2 px above it, reads as the rod plugged into the hose fitting. Approach the port across
  the rods, or turn the elbow so the leg lies across the screen. Check every pose too.
- **Cross deliberately.** If two lines must cross on screen, they cross once, on straights, at a clear
  angle, and far enough apart in depth that no painter's order can get them wrong. A crossing near a
  bend or an end reads as a joint.
- **Split, don't overlap.** Where something wraps a pipe (flange, band, bellows disc, clamp, valve
  body), the pipe is gapped there and a piece ends on each face (`gaps`, `crossingsOf` for clamps).
  Run through it as one chunk, the pipe is both behind and in front of the collar and no key can
  order it.

### Contacts and junctions
Every place two parts meet is either a flat face on a flat face, a tangent contact, or a rim landing
on a round body. Nothing is sunk into anything: a sunk part is both in front of and behind its host,
and no painter's order can draw it.
- **Flush.** A rod ends on the face of its clevis; a boss starts on the surface it stands on; a lug is
  a short pin from the housing to the ball.
- **Rim on a round body.** A cylinder of radius `a` meeting a sphere or tube of radius `R` ends where
  its rim lies on the surface, `sqrt(R² − a²)` from the centre. Its end face is then hidden inside the
  host and nothing shows through. This is how actuator rods meet their ball joints and a manifold
  meets its tee.
- **Tees.** A branch into a pipe is a sphere fitting a little larger than both: the run is gapped
  where its rim meets the sphere and the branch ends on the sphere (`caps = false to true` when its
  start hides inside). Size the sphere so neither pipe's rim pokes into the other: for a branch of
  radius `b` into a run of radius `a`, `R ≥ sqrt(a² + b²)`.
- **Stand-offs.** A ring that would have to sink into a curved body to touch it (a manifold round a
  bell) stands clear by at least half its tube radius on small radial pads.
- **Valves and bellows on straights.** A straight lathe on a curved pipe cuts into it at both ends.
  Put them where the route is straight for their whole length.
- **Two flanges, one joint.** Where a duct bolts to a pump outlet flange, the duct's own flange meets
  the outlet flange face to face; two flanges in the same place interlock. Each part then keeps a
  flange when the figure comes apart.
- **Branches have fittings.** A branch leaves its run from a tee (a ball the run is gapped under, the
  branch starting where its rim meets the ball) or from a saddle pad on the run, never from a bare
  spot on the run's skin.
- **Struts end in ball joints.** A strut's eye disc laid along an oblique axis sinks into whatever it
  meets. Give each end a short stem along the host's normal, a ball on the stem, and the rod between
  the balls (rim on sphere at all four joints).
- **Everything is held.** Every solid is connected through contacts to the stand: brackets carry
  boxes, studs carry harness clamps, a gearbox plate joins a motor to its actuator. A clamp that
  touches only the cable it holds floats.

### Clearance and crossings
A pipe keeps about 1.5 radii between its centreline and anything but its own connection, so a 0.6
cable passes 0.3 clear of a duct's skin and a duct keeps half its radius off its neighbours. Where
two lines must cross on screen, they cross in depth, one well in front of the other, and the chunk
keys order them; where a pipe passes through a collar, clamp or valve it is gapped there.
- **Lines don't double back on screen.** A horizontal leg that runs away from the viewer reads as
  going up, so a pipe that runs away and then drops reads as a cusp, a pipe ending in mid-air. Route
  the leg sideways on screen first.
- **No outline folds back on itself, and no bend pinches to a point.** A leg that points at the
  viewer and then turns projects its fillet edge-on, and the two legs meet in a V with no visible
  radius; below the tube's own screen radius the inner edge of a chunk runs backwards and the
  outline crosses itself in a little knot. A bend in a plane whose normal makes `f = |n·V|` with the
  view projects to a screen radius of about `R·f²`. Fixes, in order of preference: let the turn
  happen inside a fitting (a ball on the boss, the line leaving it toward its next point); bend in a
  plane that faces the viewer (`sideOn(d) = unit3(cross3(unit3(sub3(V, mul3(d, dot3(V, d)))), d))`,
  with `V = viewOf(P)`, is the direction across the screen from a leg along `d`, so a bend in the
  plane of `d` and `sideOn(d)` faces the viewer); move the port to where its normal lies across the
  screen. `fillet(…, projection = P, tube = r)` raises each corner's radius to clear the knot, but
  only as far as the legs allow, so give a turn next to a port at least `R + 3r` of straight leg.
- **Crossings happen on straights, clearly.** Where one line's bend or end lands on another's outline
  on screen (touching, or overlapping by any amount), the eye reads a joint or a line threaded
  through another, whatever the true depth. A fitting counts too: a clamp whose outline lands on
  another line reads as that line growing out of the clamp. Reroute one line so the meeting is a
  clear crossing of two straights or a visible gap of at least a pixel (two for a clamp), and look at
  every crossing at 4× in both themes.

Prove it rather than eyeballing it: record every item's true shape and every route into a
`Recorder`, `settle` the painter's order from measured depth, and run `capture --audit` until every
failing line prints zero (`audit.md`). An interlocked pair is a geometry bug, fixed with a split, a
gap or a tangent contact, never a key bias.

Lessons from the espresso machine, the first Compose figure with dense pipework:
- **Rim-on-sphere and rim-on-cylinder joints need a shared `owner`** in their `ShapeMeta`, or the audit
  reports them as sunk.
- **A hose can't be recorded pushed onto a barb**: the barb inside the hose reads as a pipe through a
  solid. End hoses on a boss or a union nut instead.
- **Near-side-on cameras pinch bends.** Every turn between a vertical leg and a leg toward the viewer
  folds on screen, and `fillet` quietly grows the bend to clear it, which can sweep it into a
  neighbour. Give every such turn a sideways component.
- **Flush overlays aren't ordered.** Faces within 0.3 of each other are never ordered by `settle` and
  nothing is reported, so draw a window's contents behind the window on purpose and check it at 4×.

### Shading and textures on curved surfaces
- **Smooth shading** (automatic on any profile with a smooth knot; `Smooth(slope = …)` or
  `Smooth(slope = …, radius = …)` for an analytic curve; `bandsOf` for a body in bands; `sphereOf` for
  balls) on every curved surface: bells, throats, domes, cones, bulges, balls. A flat facet tone per
  row is only right on a cylinder. Each tone is one polygon over the whole body for as long as its
  region count holds, so no row seams show.
- **A texture ends on an edge or fades, along the axis too.** Each rib's `s0` and `s1` land exactly
  on a drawn step, crease or seam arc at the same `s`: pass `seams = Seams.Both` and `ribsOf` returns
  the two seam arcs with the ribs, so they can't drift apart. Where a rib row has no seam, let it run
  onto the face that ends the band or fade it.
- **Textures end on an edge or fade.** Ribs and bolt circles limited to the side facing you stop
  wherever the cut-off falls, in the middle of the surface. Fade them toward the turn instead:
  ```kotlin
  faded(ribsOf(F, 26.0, 88.0, r, 48, P, fade = 0.1 to 0.55, seams = Seams.Both), tone = Tone.Lo)
  val bolts = dotsOf(F, s, r, 16, P, fade = 0.04 to 0.42)
  dots(bolts.points, alphas = bolts.alphas)
  ```
  Seam arcs and band lines already end on the silhouette. A single texture line that has to stop in
  the middle of a face (a groove that runs out, a weld bead short of a corner) is drawn with
  `fadeLine(points, start, end)`; never let it end on a bare cut. Choose the cut-off from the way the
  feature faces, not from the ring it sits on: holes on top of a ring face up and are seen all the
  way round, so they take `all = true`. Never cut a row on an up-facing feature with a radial
  `least`; if part of it really is hidden, fade toward the turn.
- **A texture on a pipe follows its cross-section.** Braid, corrugation, tape wrap and armour come
  from `tubePieces(route, r, P, rings = Rings(pitch, twist, cross, fade))`: rings (or with `twist`,
  helices, and with `cross = true`, a braid of both hands) placed by 3D arc length along the whole
  route, each drawn as the visible front arc of its cross-section, fading toward both limbs and
  ending on the end and gap faces. Never a dashed stroke along a chunk's centreline: it ends in
  straight hard edges along the hose, doesn't wrap the round section, piles up on the inside of
  bends, and restarts at every chunk joint.
  ```kotlin
  val hose = fillet(listOf(Point3(-130.0, 20.0, 4.0), Point3(-70.0, 20.0, 4.0), Point3(-40.0, 60.0, 4.0)), 9.0)
  tubePieces(hose, 2.4, P, maxLength = 12.0, rings = Rings(pitch = 1.4, twist = 1.2, cross = true))  // braid
  tubePieces(bellows, 4.5, P, rings = Rings(pitch = 1.6, fade = 0.05 to 0.5, tone = Tone.Mid))         // corrugation
  ```
- **Tube stripes.** Shine and shade bands run continuously through chunk joints and on to the very
  end: at every capped end they follow the outward half of the end ellipse, and at an end face turned
  toward you they stop on its inner rim, which is drawn. At a joint the stripes overhang the body by a
  further 0.5 (bodies overlap by 0.5, stripes by 1), and every frame is computed from the whole route,
  so the later chunk's stripe covers its own body's anti-aliased cut and ends on an identical stripe.
  With body and stripe cut on one line, a hairline crosses a volute's shade band at every joint, light
  in light theme and dark in dark. Check them at 4× at every bend, tee, bellows and flange, and on a
  strongly curved tapered tube. Anything you draw on a chunk yourself (a texture, flow marks) follows
  the same rule: compute it from the whole route, phase it by 3D arc length from the route's start,
  and extend it over the chunk's stripe overhang (1 unit past each joint), or the joint shows as a
  half-dash, a doubled mark or a notch. `Rings` does this for you.
- **Smooth tones taper.** Where a tone band appears or disappears along a curved profile, it is born
  at zero width and grows. A band that ends on a straight cut across the surface, or a pale wedge of
  bare fill, is the bug a reviewer finds first.
- **Shape and shading describe one curve.** Smooth runs of a profile are interpolated with a monotone
  cubic Hermite curve, and the kit uses that same curve for the normals, the tone sheets, the
  silhouette and the hull. A monotone curve can only turn where the profile turns.
- **The monotone Hermite is only C1.** Its `dr/ds` plateaus at each hand-typed knot and overshoots
  between them, and tone bands, which follow the slope, stall and then lurch: a staircase in
  scallops. Any curved body larger than a fitting needs an analytic profile `r(s)` and its
  derivative, sampled at 20–30 smooth knots:
  ```kotlin
  fun flare(s: Double): Double { val y = 1 - s / 38; return 7 + 15 * (y * y) }
  fun flareSlope(s: Double): Double = -30.0 / 38 * (1 - s / 38)
  val profile = (0..19).map { Knot(it * 2.0, flare(it * 2.0), smooth = it in 1..18) }
  solid(solidOf(profile, F, P, smooth = Smooth(slope = ::flareSlope, radius = ::flare)))
  ```
  A cone that closes to `r = 0` at its tip gets a vertical tangent there, so the outline rounds over
  the tip instead of peaking above a flat tip disc.
- **Outlines are ellipses, not facet polygons.** Facet counts are for tone fills only (about 3 px per
  facet on screen, `stepsFor(r, 32, 160, scaleOf(P))`); every silhouette, rim and crease is sampled
  from the analytic circle at about 1.5 px, and creases and bevels end exactly where the faces turn
  away.

### Harness routing
Cables are the easiest way to ruin a figure: a cable drawn across the subject's face reads as a
scribble. Route them like a real harness:
- **Lanes.** Run cables in parallel lanes on a ring around the body (`arcPoints` at a fixed `s`, each
  lane offset 1.4 units out and 0.2 along for r 0.62 cables). They leave the lane only to branch to
  their plug.
- **Enter in order.** Cables come down from the connectors in parallel drops and join the ring where
  their drop meets their lane (at `acos(x / laneR)`), so the innermost lane is fed by the connector
  nearest the front and no drop crosses another.
- **Leave in order.** The outermost lane leaves the ring first; an inner cable that turns outward
  while outer lanes still run beside it has to cross them.
- **Follow structure.** A cable that has to climb runs up beside a pipe instead of crossing open space
  in front of the subject.
- **Short branches.** Go from the lane straight to the port. Don't run diagonally across the
  subject's face.
- **Clamps** (`disc(-0.9, 0.9, r, frameAlong(point, tangent), P)`, tone `Mid`) hold the lanes to the
  body at regular angles. Gap each cable where it passes through a clamp, or the clamp and the cable
  interlock:
  ```kotlin
  val clamp = frameAlong(clampAt, tangent)
  put(clampAt) { solid(disc(-0.9, 0.9, clampR, clamp, P), tone = Tone.Mid) }
  for (lane in lanes) {
      val gaps = crossingsOf(lane, clampAt, tangent, clampR).map { it - 0.9 to it + 0.9 }
      tubePieces(lane, 0.62, P, maxLength = 8.0, gaps = gaps).forEach { piece -> put(piece.mid) { tube(piece, Tone.Mid) } }
  }
  ```
  `crossingsOf` measures along the points you pass it, and `gaps` along the resampled route: on a
  route of straight legs they agree; on a filleted lane pass it `resample(lane, 2.2)` to be exact.
- **Ends land.** Each cable starts at a connector on the controller or a junction box and ends on a
  plug, sensor tip, valve lid or bulkhead connector, never on a clamp or in the air beside the bundle.
  An end that stays with its own assembly gets a terminal dot; an end on another assembly gets a
  connector shell that moves with the cable.
- **Count what you draw.** Push every route into a list and build the readout from its length: a
  readout that claims 8 cables while 11 are drawn is the kind of lie a reviewer finds.
