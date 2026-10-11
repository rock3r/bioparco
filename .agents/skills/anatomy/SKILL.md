---
name: anatomy
description: >
  Build crafted, highly detailed, interactive isometric figures in Compose that explain a technical
  idea as a real physical object (a test rig, a dial gauge, a cabinet of drawers, a plotter, a lock),
  with many small precise solid parts, four-tone shading, correct painter's order, a framed card with
  title, hint and live readout, and calm eased motion. Use whenever someone in this repository asks
  for an isometric illustration, an explanatory figure or diagram, an interactive explainer, or a
  drawing that shows how a system, algorithm or component works, even if they never say isometric,
  and for any change to the `:anatomy` module's kit or figures.
---

# Anatomy

Adapted from Ryan's [Anatomy](https://github.com/wheresryan22/anatomy) skill (MIT, see
`anatomy/LICENSE`) for the Compose port of its kit in `:anatomy`.

This skill makes figures like a small instrument on a bench: a heavy base on feet, rails with tick
rulers, a carriage on bushings, knurled knobs, coil springs, a dial gauge whose needle moves, screws
as tiny dots. The reader understands the idea because they recognise the machine. Every number the
figure shows comes from the same model that moves its parts.

Open the closest example before you start. Today there is one:

- `anatomy/src/jvmMain/kotlin/dev/sebastiano/anatomy/figures/DialIndicator.kt`, a dial gauge on a
  stand over a carriage of gauge blocks, about 400 lines. It is a line-for-line port of the
  original's smallest example and `ParityTest` proves it draws the same paths.

The original's other examples (desk computer, arcade cabinet, Raptor engine, ripple tank, test rig,
spot plate, turning dial) are worth reading for design even though they are JavaScript:
https://github.com/wheresryan22/anatomy/tree/main/examples. The test rig is the quality bar.

## The one idea that matters

**The object is the explanation.** Don't draw boxes, arrows and labels. Invent a real, well-made
physical thing whose working *is* the concept. Here is how earlier figures mapped:

| Concept | Object |
|---|---|
| One shared GPU context serving many canvases | A gantry plotter: one print head visiting 16 wells |
| A cache key that skips work when nothing changed | A pin-tumbler lock: when every pin matches, the plug turns and nothing is redrawn |
| Seven render targets with different bytes per texel | A chest of seven drawers, each as deep as its bytes |
| A separable blur (49 × 49 taps vs 49 + 49) | A bed-of-nails stamp beside two combs on rails |
| A spring that presses and leans | A test rig with springs, a probe and a dial gauge |
| A height profile | A profile (contour) gauge pressed against the edge |
| Light that depends on facing | A small photo studio: key and fill lamps around a turntable |

Two ways this fails, both seen in practice:

- **Too abstract.** Stacked plates, floating slabs or labelled boxes don't read as anything.
- **Too flat and bland.** One big slab with a few lines on it has no volume, no parts and nothing
  to discover.

The fix for both is the same: a recognisable machine, built from many small, precise, purposeful
parts.

**Every creation is highly detailed**, however small the idea or the request: a quick explainer is
still a dense, real machine, never a few slabs.
- **Read the real thing.** Build it the way the real object is built: every screw, seam, bevel,
  bushing, label plate, scale with its ticks, and the parts that hold the parts.
- **Two looks.** At 1× it reads as one clear machine. At 4× every region still has something true to
  find; a bare face or an empty corner is unfinished work, not calm.
- **Purposeful, never noise.** Detail comes from parts that belong to the mechanism. Each one sits on
  another part and ends on an edge.
- **Check it last.** Before you finish, capture 4× close-ups over the subject and mechanism and add
  the real parts that are missing wherever a region is plain.

## What the Compose kit can draw today

The kit is a port of the original's core, `iso-kit.mjs`: floor prisms (`slabOf`, `cylinder`,
`extrude`) with four-tone shading, lines and ticks on faces, knurls, coils, seams, dots, wires, face
text, halos, and groups that move. It does **not** have the original's turned parts on tilted axes
(`lathe.mjs`), pipes and cables (`tube.mjs`), the geometry audit (`audit.mjs`), the line-end check,
WebGL shaders or the 3D mode. So:

- Invent objects whose round parts stand upright (posts, pucks, knobs, feet, dials face up) or lie
  in a vertical face (`sideRing` for bores, rod ends and lenses). Horizontal rods are thin slabs.
- A cable is a `wire`: straight runs with terminal dots that land on parts, kept beside the
  structure, never across the subject's face. Avoid subjects that are mostly pipework.
- Fire, water and light that lines can't draw are out of scope for now. Say so and pick an object
  whose mechanism reads without them.
- You are the audit. Check painter's order and line ends in close-ups (`references/verify.md`).

If a request truly needs a missing piece, tell the user which one and propose a version that fits.

## Workflow

Work in this order. Write the concept and the parts list before any geometry. Most of the quality
is decided there.

1. **Size it before you start.** Tell the user which size the request is and roughly how long it
   will take, before any other work.
   - **Figure**: one mechanism, 40–80 parts. About 30–90 minutes. Build it without asking.
   - **Hero**: a dense object, 80–150 parts. About 2–4 hours. Say so, then build it unless the user
     asked for something quick.
   - **Epic**: a whole vehicle, building or plant with many moving systems. Do not start it. Give the
     estimate, propose a Hero version that keeps the core of the idea, and list what the full version
     would add.
   - Whatever the size, have a figure that renders with `capture` within the first hour, and deepen it
     from there. Run `date` at the start and at each step, and when a step runs past twice its share of
     the estimate, tell the user where it stands and what is left.
2. **Truth first.** Collect the real numbers and behaviour of the thing being explained: sizes,
   counts, thresholds, curves. The readout, motion and caption must all be true. Write the model as
   small pure functions (`heightUnder`, `blockUnder`, `readoutOf` in the dial indicator) and drive
   everything from them. Per the repo's TDD rule, test the model's behaviour first; never freeze
   animation constants in tests.
3. **Invent the object.** Pick a machine, tool, instrument or piece of furniture whose mechanism maps
   one-to-one onto the concept. Say in one sentence what moves and what that motion means. Never copy
   a reference image: take its finish, not its object.
4. **Parts list.** Write at least 40 parts (40–80 for a Figure, 80–150 for a Hero), grouped: base
   (plate, feet, groove, corner screws, label plate, ruler strip), structure (posts, rails, frame bars,
   end stops), mechanism (carriage, bushings, springs, plunger, needle), controls (knurled knob, thumb
   screw, crown) and the subject itself, which is the one lit part. Each part sits on another part.
   `references/craft.md` has a vocabulary of parts with recipes.
5. **Plan the world.** Use world units of about 1 px of the real thing: x and y on the floor, z up.
   Lay out plans as rounded rectangles `Plan(x, y, w, d, r)` and circles. Choose the camera: azimuth
   45° by default, 35–60° to show the long side, elevation 30° (higher, up to about 44°, to look into
   something open). Then fit with `fitProjection(points, width, height, pad, azimuth = …)`.
6. **Build geometry once.** Put the figure in one file under
   `anatomy/src/jvmMain/kotlin/dev/sebastiano/anatomy/figures/`. An `object` holds the constants,
   the projection `P` and the static `ART`, built once with `art { … }` from the kit (`slabOf`,
   `cylinder`, `extrude`, `planOutline`, `topTicks`, `knurl`, `coil`, `radialTicks` and so on).
   Moving parts are built once at a rest position inside `group("name") { … }` and moved with
   `translateAlong`. Only parts whose shape changes (a needle turning, a coil compressing) are rebuilt
   per frame and drawn with the scope's `solid` and `line`.
7. **Paint back to front, bottom to top.** Sort repeated parts with `depthOf`. Split anything that
   wraps another part into a back half and a front half: the springs around a rod (`coil` returns
   both), the bars of a frame around a carriage. A moving part is drawn in the slot where it
   physically sits. See "Painter's order" in `references/craft.md`.
8. **Frame it.** Put the figure in a `Plate`: `Fig n` at top left, a short title at top right, the
   interaction hint at bottom left and the live readout at bottom right. Legend keys and a true
   caption go under the card, and words stay out of the drawing apart from at most one tiny engraved
   label (`faceText`).
9. **Make it live.** Calm, time-based motion: `follow` or real `spring`s in a `rememberFrameLoop`. An
   idle tour loops when nobody touches it. The figure answers the pointer and the keyboard, stops when
   settled, and goes still under reduced motion. The loop writes snapshot state that only the canvas
   reads, so motion redraws and never recomposes. See `references/motion.md`.
10. **Register it.** Add an entry to `Figure` and `FigureContent` in `App.kt`, and a row to the table in
    `anatomy/README.md`. Number it `Fig n` in page order.
11. **Verify like a critic.** The owner finds faults by zooming in, so you zoom in first. Read every
    PNG you make with `view_media` (or open it).
    - Capture the card in light and dark, at 650 and 390 wide, under the pointer, and at several moments
      of the tour with `./gradlew :anatomy:capture`. Commands are in `references/verify.md`.
    - **4× close-ups of every junction in both themes**: every place a part sits on another, every
      crossing, every moving part at both ends of its travel, every knurl, tick row and seam.
    - **Line ends.** Every seam, rule, rib, tick and pointer ends on a stroke, a dot or an outline.
      Check them in the close-ups: there is no automatic line check yet.
    - **Painter's order.** At every pose, nearer parts cover farther ones and nothing sinks into
      anything.
    - Put your PNG next to the dial indicator and ask: is it instantly a real object, rich in small parts
      with volume, with every line ending on something? Is it highly detailed at 1× and still rewarding
      at 4×, with no bare face or empty corner? Iterate until yes. Checklist: `references/verify.md`.
    - Finish with `./gradlew check` (Detekt, ktfmt, tests).

## Craft rules (short form)

These are the rules that separate a crafted figure from a diagram. `references/craft.md` gives the
full reasoning and recipes.

- **Solids, not wireframes.** Every part is filled in paper colour and painted in order, so things in
  front hide things behind. No see-through edges and no dashed hidden lines.
- **Let the kit shade.** `extrude` returns four side tones by facing plus a lighter top. Keep it for
  anything with height, and use `flat = true` only for floors and inlays thinner than 1 unit.
- **Tone hierarchy.** Base and feet are `Lo`, structure is `Mid` and the subject is `Hi`. Exactly one
  accent is `lit` (white in dark, black in light): the part the reader should watch. Grooves are
  `Faint`, minor ticks `Lo`, major ticks `Mid` and the zero mark `Hi`. Use colour only when colour is
  the subject, such as red, green and blue channels.
- **Small precise detail, never noise.** Use bevels of 0.4–1.6, grooves inset 3–5 from an edge, screws
  as 0.4–0.55 dots with a ring at the corners of plates, knurls on knobs, ruler ticks every 4–5 units
  with a major every 5th, and a label plate with three rule lines. Each detail belongs to a part.
- **Lines resolve.** Every line ends on something: a face, another line or a terminal dot. Connectors
  are `wire`s whose terminal dots land on objects. There are no leader lines, balloons, dimension
  arrows or labels floating in the air. Only rule lines that stand for printed text, a needle's tail
  and a pointer's tip may stop on a bare face (mark them `free`).
- **Nothing passes through anything.** Parts meet flush, tangent or rim-on-surface, never sunk in. A
  wire keeps clear of everything but its own two ends, and runs beside the structure rather than
  across the subject's face.
- **A texture ends on a real edge or fades.** Seams on a box wrap its corners (`sideSeam`, drawn as the
  slab's `inner`), ticks hang from a ruler strip's edge, a line on one face runs edge to edge, vents
  are closed outlines, and a single line that must stop ramps out with `fadeLine`.
- **Things on faces fit them.** Text a machine displays fits its window at its widest string.
- **Volume.** Give things real thickness, make them stand on other things, and put a soft `halo`
  under the base and under anything lifted.
- **Composition.** Centre the figure and let it fill about 55–65% of the card. Keep a consistent scale
  across a set of figures, and make sure it still reads at 390 wide.

## References

Read these as you need them:

- `references/craft.md`: the look in depth: what "crafted" means, the anti-patterns that got figures
  rejected, the parts vocabulary with Kotlin recipes, tones, painter's order, camera and composition,
  themes, exploded views.
- `references/kit.md`: the Kotlin API, with signatures, and how it maps to the original's.
- `references/motion.md`: live behaviour in Compose: the frame loop, springs, tours, pointer,
  keyboard, accessibility, reduced motion, envelopes for one-shot events, exploded views.
- `references/verify.md`: the `capture` commands and the review checklist.
