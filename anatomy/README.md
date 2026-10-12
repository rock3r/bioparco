# Anatomy

A Compose Desktop port of Ryan’s [Anatomy](https://github.com/wheresryan22/anatomy): a way to
explain an idea by building it as a real, working object. A cache key that skips work becomes a
pin-tumbler lock that only turns when every pin matches; a gauge reads a height because its
plunger rides onto the block beneath it. Each figure is a crafted isometric drawing of many small,
solid, shaded parts, in a card with a title, a hint and a live readout, and it moves calmly under
the pointer and the keyboard.

The original is three things, and so is this port:

| Part | Original | Here |
|---|---|---|
| The kit | `kit/iso-kit.mjs`: camera, solids, a parts library, an SVG renderer, the card | `src/commonMain/…/kit/` (plain Kotlin geometry and motion) and `src/jvmMain/…/kit/` (retained art, a Compose renderer, the card, a frame loop) |
| The skill | `SKILL.md` and `references/`, for Claude Code | [`.agents/skills/anatomy/`](../.agents/skills/anatomy/SKILL.md), rewritten for this kit |
| The checks | `scripts/capture.mjs` in headless Chrome | `./gradlew :anatomy:capture`, offscreen with `ImageComposeScene` |

Standalone:

```bash
./gradlew :anatomy:run
```

Or open it from the [showcase](../README.md). The header has a light/dark switch and a
reduced-motion box.

## Recording

![Anatomy](https://static.sebastiano.dev/stable/bioparco/anatomy.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/anatomy.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## The figures

| Figure | What it shows |
|---|---|
| Dial indicator | A dial gauge on a stand over a carriage of three gauge blocks. Hover to slide the carriage: the plunger rides onto the block under it, and one turn of the needle is 20 px of travel, so each block reads its own height. Arrow keys step block to block. |
| Token bucket | A token dispenser explaining a token bucket rate limiter. A feed wheel under a hopper drops one token a second into a magazine that holds eight, and stops while it is full. Click or press Space to send a request: the plunger pushes the bottom token out to the payout box, or strokes empty and the request is refused. A burst of eight passes at once, then one a second. |
| Bunsen burner | A Bunsen burner on a lab bench, its flame a shader in the figure's own camera. Hover across or use the arrow keys to turn the air collar: its slots open the ports, the jet draws in primary air, and the lazy luminous yellow flame (about 1,000 °C) shortens into a roaring blue flame with an inner cone (about 1,500 °C). Flame length is Roper's correlation and the cone is Gouy's, for 20 L/h of methane; a ruler zeroed at the rim has its cursor at the tip. |
| Espresso machine | A heat-exchanger espresso machine with its side panel off and its boiler cut in half, so the heat-exchanger tube shows inside it with the element in the water under it. Click or press Space to pull a shot: the E61 lever rises, the vibration pump pushes fresh water from the tank up into the back of the heat exchanger and on through the upper thermosyphon pipe to the group, the OPV returns the surplus to the tank, the gauge climbs to 9 bar and 36 g falls into the cup in about 27 s. Each dot in the pipes is a quarter millilitre moving at its true speed. It is the first figure built with turned parts, pipes and the geometry audit together, and `capture --audit` passes on it. |
| Turning dial | The original's 3D example, ported: a combination dial on a raised door plate with a small robot hand lying on its hub, drawn by the turning-groups engine. The dial turns in true 3D with everything on it, an index wheel turns geared to it under a window, a pawl rides its knurl, one finger lifts and taps, and B breaks off a hex pod, which somersaults onto the dial face. Drag round the dial to turn it; arrow keys step one number (Shift: ten), Home brings 0 to the index. |
| Raptor 2 | The original's Raptor engine, ported line for line: a Raptor 2 hanging in a vertical test stand, about 270 solids in 478 depth-sorted items. Hover, the arrow keys or the legend take it apart into its eleven assemblies on staggered springs, re-sorting the painter's order every frame, and light one; I, Enter or the Ignite button fires it through chill-down, the preburners and main stage at 300 bar and 230 tf, with the plume, Mach diamonds, splash and steam as shaders between the stand and the engine. Its parts are drawn and recorded by the kit's recording helpers (`PartRecorder`), and `capture --audit` passes on it with the original's report. |
| Desk computer (Fig 7) | The original's 1984-style all-in-one computer, with 58 moving keycaps, a coiled keyboard cord, a floppy slot and a CRT recessed into the case front. Type or click the keys; Backspace erases, Return clears, and Escape or a click on the case switches power. The picture collapses to a line and a dot when switched off. Its three-phrase tour resumes after nine seconds idle; reduced motion keeps input working without the tour or collapse. |
| Arcade cabinet (Fig 8) | The original's upright cabinet with its side off: 243 solids and 15 cables in 349 settled items. C, 5 or a click on the quarters/coin door drops a quarter through the mech and down a 30° chute, past the switch wire into the cash box. Pulses reach the meter and board; the relay wakes the marquee and CRT, which warms up, self-tests and runs a native 224 × 288 attract game in the tilted screen plane. Left/right tilt the stick and close the opposite microswitch; Space/Enter fire, Escape switches off. Input pauses the repeating tour for 12 seconds. Reduced motion disables the tour and pulses and accepts coins immediately. |

## Making a new one

Ask an agent in this repository for a figure that explains something, for example
“`/anatomy` explain how a token bucket rate limiter works, interactive”. The
[skill](../.agents/skills/anatomy/SKILL.md) has it collect the true numbers, invent the object,
write a parts list, build the geometry once with the kit, paint it back to front, frame it, make it
live, and then review its own PNGs in both themes and in close-up.

The token bucket was made that way: an agent with no other context got only that one-line prompt
and this skill, and built, reviewed and tested the figure in about 22 minutes. What it found
missing in the skill and the kit was then added to both.

```bash
./gradlew :anatomy:capture --args="dial-indicator --out shots/dial.png"
./gradlew :anatomy:capture --args="dial-indicator --dark --hover 0.3,0.5 --times 0.5,2"
./gradlew :anatomy:capture --args="dial-indicator --zoom 330,170,140,110"
```

## How it works

- `Camera.kt`, `Plans.kt`, `Solids.kt` and `Faces.kt` are the original’s `iso-kit.mjs`, function for function and in the same argument order:
  the camera (`iso`, `depthOf`, `fitProjection`), solids (`extrude`, `slabOf`, `cylinder`, with
  four side tones by facing and a lighter top), lines on faces, and the parts library (`knurl`,
  `coil`, `topTicks`, `radialTicks` and the rest). It returns `IsoPath`, a list of polylines,
  where the original returns SVG path strings.
- The rest of the original kit follows the same way: `lathe.mjs` (turned parts on any axis, smooth
  shading) is `Frames.kt`, `Profiles.kt`, `Lathe.kt`, `RoundSolids.kt`, `Bands.kt`, `RoundLines.kt`
  and their neighbours; `tube.mjs` (pipes, hoses and cables) is `Tube.kt` and `TubeRings.kt`;
  `audit.mjs` (painter's order settled from true depth, and proof that nothing passes through
  anything) is `kit/audit/`; and `scripts/line-ends.mjs` is `LineEnds.kt`, run by `capture --lines`.
- `Art.kt` is the retained art a figure builds once, in painter’s order: solids, lines, dots,
  wires, face text, halos, and named groups that live code moves by a screen offset, as the
  original moves a `<g data-part>`.
  Palette-backed `FillMark`s and `IsoScope.clipped` let the desk computer paint its logo and
  picture inside nested face-plane recesses. `FaceMatrix.composeMatrix` shares the face transform
  with text, and `faceTextAdvance` measures the cursor position rather than guessing glyph widths.
- `Render.kt` paints it the way the original’s CSS styles the SVG: fills in paper colour so nearer
  parts hide farther ones, hairlines 0.6 px at any size, halos blurred through Skia. `Palette.kt`
  is the CSS tokens, one instance per theme.
- `Shader.kt` is the original’s `gl.mjs`: a Skia runtime shader (SkSL) drawn inside the same
  canvas between the back and front art, seeing the figure’s own camera through `SKSL_ISO`
  (`ShaderCameraTest` proves it projects as `iso()` does). It renders in captures too, on Skia’s
  CPU raster. See the skill’s [shaders reference](../.agents/skills/anatomy/references/shaders.md).
- `Plate.kt` is the card; `Motion.kt` and `Loop.kt` are `follow`, `spring`, `settle`, the event
  envelopes and the frame loop. A figure writes per-frame state that only its canvas reads, so
  motion redraws and never recomposes, and the loop stops once everything has settled.

### Parity with the original

The parity tests hold the kit to the original. Their goldens are the original’s own output (commit
[`240a9bf`](https://github.com/wheresryan22/anatomy/commit/240a9bf7c50cc6226148762a1017466fcbc08523)),
captured in Node by the scripts in [`parity/`](parity/), and the Kotlin side must match every path
to the 0.1 the original writes:

| Test | Original | Golden |
|---|---|---|
| `ParityTest` | `iso-kit.mjs`, and every element of the built dial-indicator page | 225 calls, 365 elements |
| `LatheParityTest` | `lathe.mjs` | 2,093 lines |
| `TubeParityTest` | `tube.mjs` | 93 cases, 18,162 lines |
| `AuditParityTest` | `audit.mjs`: distances, states, ordering and every check | 852 lines |
| `TurnParityTest` | `turn-build.mjs` and `turn.mjs`: a lab scene and the turning dial built, packed and driven through many poses (frames, order, covers, anchors), V8's trigonometry bit for bit, and the materials | 19,167 lines |
| `OrbitParityTest` | `turn-audit.mjs`: the orbit audit's report on a small scene, clean and with three planted faults | 103 lines |
| `RaptorParityTest` | `examples/raptor-engine/build.mjs` run in Node: the back art, every recorded item in settled order (name, assembly, ride, key, lift, move, every element of its drawing) and every route; and the live figure's baked paint order against a fresh settle | 478 items, 4,842 lines, exact |
| `DeskParityTest` | Every element and attribute of both committed built desk-computer pages, including face matrices, nested clips, CRT content, 58 keycaps and both rocker poses (`parity/desk-computer.mjs`) | 1,181 lines per theme, exact, no numeric tolerance |
| `ArcadeParityTest` | `examples/arcade-cabinet/build.mjs` run in Node: back art, every recorded item in settled order (name, piece, key, move, every drawing path and native screen element), face matrices and routes; the baked paint order against a fresh settle | 349 items, 15 routes, 3,141 lines, exact |

The line check is proven against the original’s on the dial indicator: both report 0 loose ends of
367 on 23 lines. To capture a golden again, follow the note at the top of its script.
On the desk computer, both themes report 0 loose ends of 82 on 82 lines at rest and while typing.
Switched off, both report 2 of 82: the original reflection ends float once the lit picture is gone.
That inherited geometry is preserved. Counts include source endpoints hidden by a clip, but never
new endpoints introduced by clipping, matching the original checker.

### Not ported yet

The original's undocumented experiments (`turn-fixed.mjs`, `canvas-painter.mjs` and
`gl-shared.mjs`) and its other examples. Everything else in its kit is here.

## Credit and license

Ported from [Anatomy](https://github.com/wheresryan22/anatomy) by Ryan: the kit, its craft rules,
the dial indicator, its palettes and the skill’s method are his. His code is MIT licensed; the
preserved license is in [LICENSE](LICENSE), every source file derived from it says so at its top,
and the jar carries it as `META-INF/LICENSE-anatomy.txt`. The goldens are output of the original,
under the same license. The rest is this repository’s, under its [Apache 2.0 license](../LICENSE).
