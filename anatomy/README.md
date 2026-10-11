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

## Making a new one

Ask an agent in this repository for a figure that explains something, for example
“`/anatomy` explain how a token bucket rate limiter works, interactive”. The
[skill](../.agents/skills/anatomy/SKILL.md) has it collect the true numbers, invent the object,
write a parts list, build the geometry once with the kit, paint it back to front, frame it, make it
live, and then review its own PNGs in both themes and in close-up.

```bash
./gradlew :anatomy:capture --args="dial-indicator --out shots/dial.png"
./gradlew :anatomy:capture --args="dial-indicator --dark --hover 0.3,0.5 --times 0.5,2"
./gradlew :anatomy:capture --args="dial-indicator --zoom 330,170,140,110"
```

## How it works

- `Iso.kt` is the original’s `iso-kit.mjs`, function for function and in the same argument order:
  the camera (`iso`, `depthOf`, `fitProjection`), solids (`extrude`, `slabOf`, `cylinder`, with
  four side tones by facing and a lighter top), lines on faces, and the parts library (`knurl`,
  `coil`, `topTicks`, `radialTicks` and the rest). It returns `IsoPath`, a list of polylines,
  where the original returns SVG path strings.
- `Art.kt` is the retained art a figure builds once, in painter’s order: solids, lines, dots,
  wires, face text, halos, and named groups that live code moves by a screen offset, as the
  original moves a `<g data-part>`.
- `Render.kt` paints it the way the original’s CSS styles the SVG: fills in paper colour so nearer
  parts hide farther ones, hairlines 0.6 px at any size, halos blurred through Skia. `Palette.kt`
  is the CSS tokens, one instance per theme.
- `Plate.kt` is the card; `Motion.kt` and `Loop.kt` are `follow`, `spring`, `settle`, the event
  envelopes and the frame loop. A figure writes per-frame state that only its canvas reads, so
  motion redraws and never recomposes, and the loop stops once everything has settled.

### Parity with the original

`ParityTest` holds the kit to the original. Its goldens are the original’s own output (commit
[`240a9bf`](https://github.com/wheresryan22/anatomy/commit/240a9bf7c50cc6226148762a1017466fcbc08523)):
225 kit calls over three cameras, and every element of the built dial-indicator page in paint
order. The Kotlin side must match every path to the 0.1 the original writes. To capture them again,
follow the note at the top of [`parity/capture.mjs`](parity/capture.mjs).

### Not ported yet

The original kit has more than this port so far:

- `lathe.mjs` (turned parts on any axis, smooth shading) and `tube.mjs` (pipes and cables).
- `audit.mjs`, which proves nothing passes through anything, and `scripts/lines.mjs`, the
  line-end check.
- `gl.mjs`, the WebGL layer for fire, water and light. On Compose this would be a Skia
  `RuntimeShader` between a back and a front canvas.
- The beta 3D mode (`turn*.mjs`), and the other seven examples.

The skill says so, and keeps figures to what the kit can draw: slabs, cylinders and extrusions.

## Credit and license

Ported from [Anatomy](https://github.com/wheresryan22/anatomy) by Ryan: the kit, its craft rules,
the dial indicator, its palettes and the skill’s method are his. His code is MIT licensed; the
preserved license is in [LICENSE](LICENSE), every source file derived from it says so at its top,
and the jar carries it as `META-INF/LICENSE-anatomy.txt`. The goldens are output of the original,
under the same license. The rest is this repository’s, under its [Apache 2.0 license](../LICENSE).
