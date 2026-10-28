# Stamp book

This Compose Desktop specimen recreates [Adi](https://x.com/AdityaSur11)’s
[stamp book animation](https://x.com/AdityaSur11/status/2105485353133969504). A small book with a
frosted, coloured cover sits on a grey table, with the stamps inside showing through it as soft
blurs. Hover it and the cover swings open on its left hinge while two stamps slide out to the
right: a pale one with a line-drawn sparrow, and a loud teal one with a chubby bird over waves.
Move away and they tuck back in before the cover shuts. The three dots underneath recolour the
book gold, magenta or black.

Hover the book to open it. Click it, or press Space or Enter, to pin it open (or shut) for touch and
keyboards. Left and Right change the colour.

Run the specimen on its own:

```bash
./gradlew :stamp-book:run
```

Or open it from the [showcase](../README.md).

## Recording

![Stamp book](https://static.sebastiano.dev/stable/bioparco/stamp-book.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/stamp-book.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

### The book

The cover hinges on its left edge and turns in 3D (`graphicsLayer` `rotationY`), its free edge
swinging towards the viewer as a real cover opens. The camera sits well back, so the perspective
stays mild, as in the video: a close camera blows the near edge up. Shut, the cover sits 5° ajar,
and the back board and the bird stamp's perforations peek out past it. Open, it turns 60°.

While shut, the cover is frosted: the stamps behind it show through as coloured blurs. That frost
is a baked image of the two stamps in their tucked-in places, blurred once at startup, with the
cover's colour laid over it. It clears in the first 40% of the swing, so the open cover is a solid
sheet with a little shade towards its free edge.

The video's lettering is Japanese. Here it is Italian, for a bioparco: the book is a *Raccolta*, the
pale stamp says *Quiete*, and the loud one *Uccellino*.

### The stamps

Both stamps are drawn in code: a pale one with a line-drawn sparrow on a twig, and a teal one with
a chubby bird on a branch over seigaiha waves, under a sun and a cloud. `Perforation` places their
holes: a whole number per side, the pitch stretched to fit, with every corner a whole tooth.

Opening, the pale stamp slides half out onto the board, and the bird stamp slides all the way out
past the board's edge. Closing, they tuck back in first and the cover follows.

| What | How | Why |
|---|---|---|
| Cover opening | spring, damping 0.78, stiffness 170 | Swings open in about half a second without a visible bounce |
| Cover closing | spring, damping 0.9, stiffness 260, 90 ms after the stamps | The stamps tuck in before the cover shuts on them |
| Pale stamp | spring, damping 0.8, stiffness 150, 40 ms after the cover | Follows the cover out |
| Bird stamp | spring, damping 0.62, stiffness 130, 100 ms after the cover | Overshoots a touch and rocks back |
| Bird stamp tilt | 3.5° inside, −2° out, plus a lean of 2.2° per unit of speed (at most 5°) | It leans into its own motion and straightens as it lands |
| Bird stamp lift | grows by up to 3.5% mid-slide, its shadow drops and darkens | It lifts off the board as it passes over the pale stamp |
| Colour | changes at once | As in the video |

### Hover

`HoverLatch` decides when the book is open. Hovering the shut book opens it. Once open, it stays
open anywhere over the wider spread the stamps fan out into, so following a stamp with the pointer
doesn't snap the book shut. A click, Space or Enter pins it open for touch and keyboards. Clicking a
hovered-open book shuts it, and it stays shut until the pointer leaves and comes back.
`KeyRepeatFilter`, borrowed from [squeeze-progress](../squeeze-progress/), keeps a held key from
flapping it.

`HoverLatchTest`, `PerforationTest`, `SwatchesTest` and `KeyRepeatFilterTest` cover the plain Kotlin.

## Performance

Tracing sections cover each layer (`back board`, `quiet stamp`, `bird shadow`, `bird stamp`,
`cover`), the open, close and recolour triggers, and the startup bake. See
[docs/TRACING.md](../docs/TRACING.md).

### What a frame draws

Every frame only copies images, baked once at startup on a background thread (`bake art`, about a
third of a second): the two stamps, the bird stamp's shadow and its tucked-in tilt, and for each
colour the board with the book's shadow and the cover's face, frosted to clear.

The first version drew the cover live: a fill, the frost, a tint, a sheen, a shade, a rim and the
label, each through the cover's perspective, where every pixel costs a filtered sample. A software
frame then cost about 10 ms to rasterise even with nothing moving. The trace sections didn't show
it, because Skia rasterises after the `frame` section ends; timing each layer alone in an
`ImageComposeScene` did. Now:

- the cover is one image through the perspective: its frost clears in six baked steps, which never
  show on a cover in motion, because cross-fading two faces cost 5–7 ms a frame;
- the stamps slide in whole pixels, so copying them needs no filtering;
- the bird stamp's shadow sits outside its turning layer, and while the stamp rests tucked in, it is
  drawn from an image already turned to its tilt, so its layer stays square.

### Frame cost

[`FrameBudgetBenchmark`](src/jvmTest/kotlin/dev/sebastiano/stampbook/FrameBudgetBenchmark.kt)
renders the whole app in an `ImageComposeScene` at the recording's 800 × 600: composition, layout,
drawing and Skia's rasterising, all on the CPU. It renders 60 Hz frames through the book resting
shut, hovered open, resting open, closing and recolouring.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :stamp-book:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/stamp-book-benchmark
```

Measured on a 4-core cloud VM:

| Scenario | Median | p95 | Worst |
|---|---|---|---|
| Shut | 2.54 ms | 3.54 ms | 3.57 ms |
| Opening | 3.86 ms | 5.23 ms | 5.80 ms |
| Open | 4.15 ms | 4.72 ms | 4.75 ms |
| Closing | 2.80 ms | 5.70 ms | 5.86 ms |
| Recolouring | 2.51 ms | 4.09 ms | 4.09 ms |

The worst whole software frame uses about a third of a 60 Hz frame (16.7 ms). Before the bake, the
book cost about 10 ms a frame at rest. The benchmark closes each frame's image as soon as it has it:
left to the garbage collector, the 2 MB snapshots made timings swing by several milliseconds.

### Frame pacing in a window

The specimen ran in a real window under Xvfb with tracing on, seven times on `SOFTWARE_FAST` and
twice on `SOFTWARE_COMPAT`. [xdotool](https://github.com/jordansissel/xdotool) hovered the book
open, moved over the bird stamp, left it to close and clicked a swatch, six times. The gaps between
`frame` sections give the pacing. The first three seconds after launch (the JIT's time) are left
out, and so are gaps over 100 ms, when nothing asks for a frame. For comparison,
[squeeze-progress](../squeeze-progress/) ran on the same VM in the same way.

| Skia backend | Intervals in one 60 Hz frame (13–20 ms) | Over 20 ms | Median | p99 |
|---|---|---|---|---|
| `SOFTWARE_FAST` | 97.9–99.2% | 4 of about 3,600 | 15.2–15.3 ms | 16.2–17.0 ms |
| `SOFTWARE_COMPAT` (what CI records with) | 82.3–88.5% | 12–18% | 15.6–15.8 ms | 24.7–26.5 ms |
| squeeze-progress, `SOFTWARE_COMPAT` | 82.3% | 17% | 15.9 ms | 27.0 ms |

On `SOFTWARE_FAST` it holds 60 Hz. Five of the seven runs had no interval over 20 ms. The other
two had two each, 24–59 ms, at random moments rather than when the book starts to move. In the one
of those runs logged with GC, no collection came near them, so they look like the shared VM
stalling the process. The intervals under 13 ms are frames that came early.

`SOFTWARE_COMPAT` also copies every frame into the window through Java2D (see
[docs/TRACING.md](../docs/TRACING.md#things-that-fool-you)), which takes most of a frame by itself.
There the book paces like squeeze-progress, whose frames cost under a millisecond to draw. With the
cross-fade the frost used before it was baked in steps, this backend managed only 50–68%. Skia's OpenGL backend could not create a context on Mesa under Xvfb in the test
container, so it is not measured here. A GPU window does almost none of this work on the CPU.

## Limits

- The video's stamps are illustrations. These are drawn in code, in the same spirit, not copies.
- The video's text is Japanese, and in SF Pro and a serif. This uses Italian words and the default
  sans-serif and serif fonts.
- The book appears about a third of a second after launch, once its art has baked.
