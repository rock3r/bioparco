# Holo card performance

The card moves on every frame while it is turned, dragged, swapped or running the demo, and its
foil is a per-pixel program. This guide records how it was brought to a steady 60 Hz in
software rendering, the slowest backend, and how to repeat the checks.

Measurements use a 4-core 2.1 GHz Xeon, JDK 25, Skiko 0.150.1, Xvfb and a 980 × 740 window at
density 1, the same machine class as [peel-sticker/PERFORMANCE.md](../peel-sticker/PERFORMANCE.md).
That machine is shared and noisy. A plain CPU loop on it took 15.6 ms at best, 17 ms at the median
and 30 ms at the 90th percentile for the same work. Read single slow frames with that in mind.

## Result

The demo ran for 12 s in a real window with tracing on. Frame-to-frame times come from the
`frame` sections, leaving out idle waits between interactions. A missed refresh is an interval
longer than 25 ms, one and a half refreshes.

| Window, `SOFTWARE_FAST` | Missed refreshes | Median | p95 | Frames a second |
|---|---|---|---|---|
| First version | 8.8% | 16.9 ms | 31.1 ms | 53 |
| Now, run 1 | 0.5% | 15.2 ms | 16.2 ms | 65 |
| Now, run 2 | 0.9% | 15.3 ms | 16.5 ms | 63 |

| Window, `SOFTWARE_COMPAT` | Missed refreshes | Median | p95 | Frames a second |
|---|---|---|---|---|
| First version | 99% | 33.7 ms | 56.6 ms | 27 |
| Now | 72% | 26.4 ms | 38.8 ms | 36 |

`frame` itself, the Compose recording of a frame, took 0.8 ms at the median and 2.2 ms at the
95th percentile. On a GPU backend that is the CPU's whole share, so Metal, Direct3D and OpenGL
windows have plenty of room at 60 Hz and above.

`SOFTWARE_COMPAT` still misses refreshes because of the Java2D step that presents each frame. The
same frames take about 11 ms longer there than with `SOFTWARE_FAST`, outside the app's code. See
[docs/TRACING.md](../docs/TRACING.md#measurement-limits). Peel sticker reaches about 20 frames a
second on that path. The Spectre recording, which runs under `SOFTWARE_COMPAT` while GStreamer
captures the window, has 542 distinct frames out of 602, close to its 30 fps cap during movement.

Skiko could not create an OpenGL context for a window under Xvfb on this machine, even though
GLX with Mesa's llvmpipe works, so no GPU window was measured.

## What cost what

The cost ladder renders each case round-robin in an `ImageComposeScene`. The scene rasterises the
whole window on every frame, so these are full software frames at density 1:

| Case | min | median | p95 (ms) |
|---|---|---|---|
| Empty window | 0.23 | 0.41 | 1.05 |
| Card, flat, no foil | 2.11 | 2.77 | 3.80 |
| Card, flat, foil | 3.82 | 4.67 | 6.42 |
| Card, turned, no foil | 3.16 | 3.80 | 5.05 |
| Card, turned, foil | 4.94 | 5.84 | 7.70 |
| Card, turned, foil, clipped to its corners | 5.54 | 6.27 | 9.58 |
| The app at rest | 4.17 | 4.90 | 7.48 |

The foil was the largest single cost. On a raster surface, where Skia draws at call time:

| Foil, one mascot | First version | Now |
|---|---|---|
| 196 px, density 1 | 5.0 ms | 1.6 to 2.7 ms |
| 392 px, density 2 | 13 to 19 ms | 6.2 to 9.3 ms |

Turning the foil in perspective, as the card does, added little on top of that.

Whole-app scene frames now take, at density 1, median / p95: hover 11.6 / 14.8 ms, drag 8.2 /
9.5 ms, demo 7.6 / 10.2 ms, swap 6.5 / 13.3 ms. The scene redraws everything on every frame; a
window only redraws after a change. Use these numbers to compare cases.

## What changed

- **The foil program does less per pixel.** Skia's CPU backend runs the program for every pixel.
  The view vector, the light's halfway vector, the glow's centre and the hue drift depend only on
  the tilt, so they are now worked out once a frame in Kotlin and passed in as uniforms. The
  rainbow uses a triangle wave smoothed by a cubic, within 2% of a cosine, in place of three `cos`
  calls. The glow and the rim use squared distances, which removes two square roots. One square
  root is left, for the normal's z. These changes and the next one landed together and took the
  foil from 5.0 ms to under 2.7 ms; they were not timed one by one.
- **Only the mascot's box is shaded.** Baking records the bounds of the covered pixels. Pixie and
  Botto fill little more than half of their square, and the rest used to run the program to its
  early return.
- **No clip through the perspective turn.** Clipping the turned layer to rounded corners cost
  0.4 ms a frame at the median and 1.9 ms at the 95th percentile. Nothing on the face reaches
  past its corners, so the face paints its own rounded background and the glare is a rounded
  rectangle.
- **The shadow is a copy.** It is blurred once per size into an image, then drawn on whole pixels
  with nearest sampling. A blur, or a filtered draw at a fractional offset, would cost several
  milliseconds a frame in software.
- **Motion stays out of composition.** The springs publish the tilt as state that only
  `graphicsLayer` and draw lambdas read, so a moving card re-records its layers and draws without
  recomposing. When everything settles, the frame loop waits for the next target, and a resting
  card draws no frames.
- **The normal maps bake off the UI thread.** Inflating the three silhouettes takes 200 to 270 ms
  on a cold JVM, mostly in the first one. The card shows its text first and its mascot as soon as
  the bake ends.

## How it was measured

```bash
# Scene frames per scenario, the foil on a raster surface, and the cost ladder:
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :holo-card:jvmTest --tests '*HoloCardBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/holo-card-benchmark
cat holo-card/build/reports/holo-card-cost-ladder.md

# A real window: press Space (or the play button), let the demo run, then close the window.
SKIKO_RENDER_API=SOFTWARE_FAST BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :holo-card:run
python3 tracing/tools/trace-summary.py /tmp/traces/holo-card

# The Spectre recording, traced, as CI makes it:
CI=true BIOPARCO_TRACE_DIR=/tmp/traces xvfb-run -a ./gradlew :recordings:recordSpecimens --tests '*HoloCard*'
```

In the summary, `frame` should stay well under 16.7 ms at the 95th percentile. While the card
moves, frame to frame should sit at the refresh interval. The summary's frame-to-frame line counts
idle waits between interactions too, so a long worst case there is a pause, not a stall.
