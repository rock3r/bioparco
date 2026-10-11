# Craft

How to make a figure look crafted rather than diagrammed. Adapted from the original's
`references/craft.md` (MIT), with its recipes in Kotlin. Its sections on turned parts, pipes,
harnesses, the audit and shaders are left out because the Compose kit has none of them yet; read
them in the original when you port those pieces.

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
