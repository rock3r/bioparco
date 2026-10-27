# Squeeze progress

This Compose Desktop specimen recreates [Petar Cirkovic](https://x.com/kippe07)’s
[progress bar that squeezes when it is paused](https://x.com/kippe07/status/2105216988855542196).
A white line fills a blue field from left to right, with a lighter band behind it showing how far
it has got. Pause it and the line crumples into a springy squiggle just behind its tip while the
field turns orange. Resume it and the squiggle shakes out into a low wave that the moving tip
leaves behind.

Click the round button, or press Space or Enter. The bar fills in eight seconds, holds full for a
moment, then rewinds and starts again.

Run the specimen on its own:

```bash
./gradlew :squeeze-progress:run
```

Or open it from the [showcase](../README.md).

## Recording

![Squeeze progress](https://static.sebastiano.dev/stable/bioparco/squeeze-progress.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/squeeze-progress.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

### The squiggle

The line is drawn as one path. Each point on it has a position `s` along the uncrumpled line.
The crumple (`Crumple.kt`) bends only the stretch `centre ± halfWidth` of it:

- **Up and down:** one and a half waves. The line rises, dips a little further, then rises a
  little and lies flat again. The lobes are 0.95, 1 and 0.45 of the tallest, as measured off the
  video. Each lobe's height changes only where the line crosses its rest height, so the changes
  never show.
- **Square:** as it squeezes, each wave squares off, from `sin φ` towards `tanh(2.5 sin φ)`. The
  sides get steep and the tops flat.
- **Sideways:** the squeeze pulls the line together wherever it climbs and not at all at the lobe
  tops. The pull follows the squared wave's slope profile, `sech²(2.5 sin φ)`, so at a squeeze of 1
  the strokes stand upright and the tops stay as round caps: the video's softened square wave. Past
  1 the strokes lean back and the line crosses itself. The pull has no closed-form integral, so it
  is tabulated once.

The first version used plain sine waves pulled together only where lobes meet. Its peaks came out
pointed and its first stroke leaned like a ramp. A side-by-side with the video showed it, and
`CrumpleTest` now checks that the first stroke rises upright.

Squeezing takes up length. The line left of the squiggle slides right to make room, and the tip
never moves. The line starts off the left edge of the window, so its start never shows.

`CrumpleTest` and `ProgressClockTest` cover the geometry and the clock, which fills, holds and
rewinds.

### The motion

| What | How | Why |
|---|---|---|
| Squeeze on pause | spring, damping 0.36, stiffness 650 | Overshoots by about a third, which crosses the loops, and settles in about a quarter of a second, as in the video |
| Squeeze on resume | spring, damping 0.5, stiffness 110 | Shakes out slowly and dips once the other way, which makes the low ripple |
| Ripple drift | 1.4 s tween | The released squiggle slides 110dp back along the line and spreads to 1.8× its width |
| Blue/orange | 260 ms tween | Colours blend straight through sRGB, as in the video, so halfway the field is a dusty mauve |
| Ink | dips to 20% halfway through the colour swap | The video fades the text, icon and line most of the way out so the two inks never overlap at full strength |

The squiggle's stretch narrows from 110dp to 36dp half width as it squeezes. Its tallest lobe
grows to 37dp. It sits 90dp behind the tip, and it stays at that point on the line after a resume,
so the tip moves away from it.

The percentage is two Jewel texts, one per ink, cross-fading in graphics layers. It recomposes only
when the whole percent changes. Everything else that moves is read while drawing, so a frame
redraws without recomposing.

## Performance

Tracing sections cover the drawing (`squeeze field`, `trace line`), the clock and each pause and
resume. See [docs/TRACING.md](../docs/TRACING.md).

### Frame cost

[`FrameBudgetBenchmark`](src/jvmTest/kotlin/dev/sebastiano/squeezeprogress/FrameBudgetBenchmark.kt)
renders the whole app in an `ImageComposeScene` at the recording's 800 × 600. That covers
composition, layout, drawing and Skia's rasterising, all on the CPU. It renders 60 Hz frames
through playing, pausing, the overshoot, the release and playing again.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :squeeze-progress:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/squeeze-progress-benchmark
```

Measured on a 4-core cloud VM:

| Scenario | Median | p95 | Worst |
|---|---|---|---|
| Playing | 0.80 ms | 2.08 ms | 4.74 ms |
| Crumpling (overshoot and settle) | 1.32 ms | 3.09 ms | 3.09 ms |
| Paused | 0.85 ms | 1.91 ms | 2.22 ms |
| Releasing | 0.85 ms | 2.43 ms | 3.22 ms |

Even the worst whole software frame uses under a third of a 60 Hz frame (16.7 ms). The specimen's
own drawing (`squeeze field`) is 0.02–0.05 ms at the median and 0.8 ms at worst, while it
crumples.

The benchmark first runs the scenario eight times to warm up. While the JIT is still compiling
Compose and Skiko, even an empty scene costs about 9 ms a frame for its first couple of thousand
frames, then under half a millisecond.

### Frame pacing in a window

The specimen ran in a real window under Xvfb with tracing on, through three pauses and resumes,
twice per backend. [xdotool](https://github.com/jordansissel/xdotool) moved the pointer in, then
clicked. The gaps between `frame` sections give the pacing. The first three seconds after launch
(the JIT's time) are left out, and so are gaps while paused and settled, when nothing asks for a
frame.

| Skia backend | Intervals in one 60 Hz frame (13–20 ms) | Median | p99 | Worst |
|---|---|---|---|---|
| `SOFTWARE_FAST` | 97.6–98.0% | 15.2 ms | 18.2 ms | 27–39 ms |
| `SOFTWARE_COMPAT` (what CI records with) | 87.6–89.5% | 15.4–15.5 ms | 25–26 ms | 32–44 ms |

Both backends rasterise the same frames in software. `SOFTWARE_COMPAT` also copies every frame
into the window through Java2D. That copy is where its extra late frames come from, as
[docs/TRACING.md](../docs/TRACING.md#things-that-fool-you) describes. It is a cost of Skiko and the
JDK, not of the specimen. Skia's OpenGL backend could not create a context on Mesa under Xvfb in
the test container, so it is not measured here. A GPU window does less work on the CPU than
either software backend.

### Warm-up

The first pause used to drop up to three frames, and the first resume two. The crumple maths
(with its lookup table) and the animation springs ran for the first time on those frames. The
specimen now runs them once at startup, while the bar is moving anyway. The `pause` and `resume`
sections show it: every pause and resume, the first included, takes about a millisecond, and the
next frame starts within 1–3 ms.

What is left belongs to Compose and AWT, once per launch:

- Compose handles its very first press slowly. It holds the event thread for 20–40 ms before the
  click reaches the specimen, and can cost that press a frame. Later presses take a few
  milliseconds. Toggling from a bare pointer handler instead of `clickable` did not measurably
  change it, so the button keeps `clickable`.
- The first time the pointer turns into a hand over the button, AWT on X11 can take a frame or
  two. Without the hand cursor those late frames did not appear, but the hand is the right cue for
  a button, so it stays.

## Limits

- The video's text is SF Pro. This uses Jewel's default font with tabular figures.
- The source rewinds by hand. Here the bar loops on its own so the specimen keeps moving.
- Pausing again while a released ripple is still drifting restarts the squiggle at the new tip
  rather than gathering up the old ripple.
