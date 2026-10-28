# Layout morph

This Compose Desktop specimen recreates [Elia](https://x.com/eliakuratli)’s
[layout morph](https://x.com/eliakuratli/status/2106829749125550143): one shop collection, six
ways to browse it. Switch the view and every card flies to its new place.

The collection is a small makers’ shop: fourteen pieces from ten makers, as 24 cards. The views
are:

- **Grid**: eight across, three down, each card a picture over a caption.
- **Masonry**: eight columns, every card at its own proportions.
- **List**: rows of thumbnail, name, maker and price, three columns of eight.
- **Stack**: a fanned hand of cards. Hover along it and each card slides up out of the fan.
- **Carousel**: big captioned cards in a row. Drag or scroll and it settles on the nearest card.
- **Helix**: the cards on a spiral round a turning cylinder. Drag to spin it; throw it and it keeps
  turning, then slows back to its drift.

Click a view in the switcher, or press 1–6. Click any card to open it in the carousel. ← and →
step the carousel or nudge the helix. The footer shows the card under the pointer, or the
carousel’s card.

Run the specimen on its own:

```bash
./gradlew :layout-morph:run
```

Or open it from the [showcase](../README.md).

## Recording

![Layout morph](https://static.sebastiano.dev/stable/bioparco/layout-morph.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/layout-morph.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

### The flight

`MorphEngine` (plain Kotlin) moves the cards. Each card follows a target pose from its view (where
it sits, its size, its tilt and turn, how faded it is, and how far its face is a list row or a
tile) plus an offset that springs to zero.

Switching view turns where a card is now into a fresh offset from its new target. So:

- **Nothing jumps.** A card changes its mind mid-flight from wherever it is, at the speed it had.
- **Moving targets are followed exactly.** The carousel and the helix move their cards every
  frame. Once the offset has died away, the card is on the moving target, not chasing it.
- **Cards set off one after another,** 16 ms apart, in reading order of where they are now: left
  to right, a row counting for a third. A flight settles in about half a second, with a whisper of
  overshoot.
- **No two cards fight over which is on top.** While they travel, each card's place in the draw
  order slides from where it was to where the new view stacks it, all on one clock. Two cards'
  places close on each other at a steady rate, so an overlapping pair swaps at most once.
  (The first version lifted flying cards over settled ones. As they landed one by one, out of
  the fan's order, overlapping cards swapped back and forth: 1,271 times over the 30 possible
  switches. `MorphEngineTest` now checks for none.)

The tile and row faces cross-fade over the middle of a morph only, between 30% and 70% of the way.

### The views

`Arrangements` lays out each view for the field’s size, as plain functions of the card count (and
of the carousel’s position or the helix’s turn). The grid reads row by row and the list column by
column, so the grid’s first row becomes the list’s first column, as in the original.

The helix puts seven cards to a turn, the middle card in front. Turning it also screws the spiral
along, so cards run off one end and wrap round to the other. They fade out towards the field’s top
and bottom edges first, so the wrap is never seen. A card still flying in keeps its lap until it
lands, so its target runs on past the end instead of jumping back. One that lands past the end
wraps round see-through and fades in.

`CarouselDrive` and `HelixDrive` hold the carousel’s spring and the helix’s spin and throw.
`hitTest` finds the card under the pointer from the same poses: the top card by depth, in the
card’s own turned and scaled axes. Faded and edge-on cards cannot be picked.

`ArrangementsTest`, `MorphEngineTest` and `HitTestTest` cover the layouts, the flights (stagger,
no jumps on a change of mind, the short way round, no wrap in sight even under a hard spin, solid
cards in front of a fast spin), the carousel and helix drives, and hit-testing. They were written
first.

### The cards

The original uses photos. These are flat vector illustrations (`CardArt.kt`): ten portraits, five
photographed pieces, and nine pieces shown as their name and price, as the original does. Vectors
stay sharp at every size a card flies through, and need no image files.

Every card is a recorded graphics layer. One draw pass lays them down, deepest first, so the order
is right even while the helix spins. Moving, tilting and fading a card only changes its layer; its
face is recorded again only while it morphs, or when it turns its back. Nothing recomposes while
cards move. Text is laid out once per card and drawn from those layouts.

## Performance

Tracing sections cover the engine’s step (`morph step`), the card pass (`cards`) and each face
recorded (`card face`). See [docs/TRACING.md](../docs/TRACING.md).

### Frame cost

[`FrameBudgetBenchmark`](src/jvmTest/kotlin/dev/sebastiano/layoutmorph/FrameBudgetBenchmark.kt)
renders the whole app in an `ImageComposeScene` at the recording’s 960 × 630. That covers
composition, layout, drawing and Skia’s rasterising, all on the CPU, at 60 Hz through every
flight, a carousel drag and a thrown helix.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :layout-morph:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/layout-morph-benchmark
```

Measured on a 4-core 2.1 GHz Xeon cloud VM:

| Scenario | Median | p95 | Worst |
|---|---|---|---|
| Grid, at rest | 3.3 ms | 5.5 ms | 7.8 ms |
| To masonry | 5.6 ms | 7.4 ms | 9.4 ms |
| To list | 4.9 ms | 8.4 ms | 9.2 ms |
| To stack | 8.4 ms | 11.3 ms | 12.3 ms |
| To carousel | 3.8 ms | 14.0 ms | 24.6 ms |
| Carousel drag | 1.7 ms | 7.6 ms | 7.6 ms |
| To helix | 4.0 ms | 5.8 ms | 7.4 ms |
| Helix, thrown and spinning | 3.3 ms | 3.8 ms | 4.6 ms |
| Back to grid | 5.2 ms | 9.7 ms | 10.3 ms |

The engine’s step is 0.01 ms at the median, and the card pass (recording faces and setting layers)
under 1.2 ms at p95. The rest is Skia rasterising. The carousel flight’s worst frame is a one-off
pause inside the engine’s step, which is otherwise never over 0.1 ms.

### Frame pacing in a window

`LayoutMorphRecordingTest` drove the specimen in a real 960 × 660 window under Xvfb, with tracing
on and Spectre recording, through every view and interaction. The gaps between `frame` sections
give the pacing. The first three seconds (the JIT’s time) are left out, and so are gaps while
nothing moves. The control is the same window sliding one 4 × 40 dp rectangle, recorded the same
way (not checked in).

| Skia backend | Intervals in one 60 Hz frame (13–20 ms) | Median | p99 | Over 25 ms |
|---|---|---|---|---|
| OpenGL (Mesa), three runs | 96.2–97.9% | 15.3 ms | 18.3–31.8 ms | 1–15 of 1,200 |
| `SOFTWARE_FAST`, three runs | 97.4–97.7% | 15.3 ms | 18.3–19.7 ms | 1–3 of 1,210 |
| Control, OpenGL (Mesa) | 98.0% | 15.3 ms | 16.9 ms | 1 of 1,320 |
| `SOFTWARE_COMPAT` (what CI records with) | 43.1% | 21.0 ms | 39.4 ms | 239 of 844 |
| Control, `SOFTWARE_COMPAT` | 57.2% | 18.8 ms | 31.9 ms | 248 of 970 |

On OpenGL and `SOFTWARE_FAST` the specimen paces close to the empty control. The late frames
that are left bunch in one place: the flight from the list into the fanned stack, the heaviest
half second, where 24 growing, tilting cards each draw both faces while they cross-fade. One
OpenGL run lost a third of that second's frames; the others lost a few. Mesa’s OpenGL under Xvfb
rasterises on the CPU too; a real GPU does far less of this work. `SOFTWARE_COMPAT` copies every frame into the
window through Java2D, which costs even the control most of a frame (see
[docs/TRACING.md](../docs/TRACING.md#things-that-fool-you)). The recording is 30 fps, so it still
shows every flight smoothly.

### What got it there

The first traces in a window met 60 Hz on 82–84% of intervals, and the fanned stack cost 47 ms a
software frame. Bisecting the benchmark found:

- **Strokes.** A card’s soft shadow, hairline and the art’s lines were strokes. On a tilted card a
  stroke is a path the stroker rebuilds on every frame: they were 40% of the stack’s frame. Shadow
  and edge are now three opaque fills (a darker lip, the hairline, the card a pixel inside), and
  lines are filled quads.
- **Translucent shadows.** The first shadows were translucent fills under the whole card, so every
  card’s pixels were blended twice. Blending is the dearest thing a software frame does.
- **Clips.** Rounded clips on each card and picture became anti-aliased masks once a card tilted.
  The art now keeps inside its frame and rounds its own corners.
- **Gradients.** The backdrops are flat colours. Only the dusk sky and the lamp’s glow keep one.
- **Perspective.** A helix card’s turn is drawn as a narrowing, without perspective. Under a 3D
  turn Skia draws text as fresh paths on every software frame, which doubled the cost of a flight
  out of the helix.
- **Alpha leak.** Layers use the default compositing. `ModulateAlpha` would skip the offscreen pass
  for fading cards, but on Compose Desktop one layer’s alpha leaked into the layers drawn after it,
  and the spinning helix came out washed out. Each layer is sized to its own card, so a fading card’s
  offscreen pass stays small.

## Review tools

[`ArrangementCapture`](src/jvmTest/kotlin/dev/sebastiano/layoutmorph/ArrangementCapture.kt)
renders every view, two frames of each flight into it and a thrown helix to PNGs, for reviewing
changes without a window:

```bash
LAYOUT_MORPH_CAPTURE=/tmp/morph ./gradlew :layout-morph:jvmTest --tests '*ArrangementCapture*' --rerun
```

## Limits

- The original’s pictures are photos. These are illustrations, and the shop, its makers and its
  pieces are made up.
- The original’s text is SF Pro. This uses Jewel’s default font.
- Helix cards turn without perspective (see above), so a card at the side of the cylinder narrows
  without its near edge growing.
- The original rolls the footer’s figures like an odometer. Here they change in place.
