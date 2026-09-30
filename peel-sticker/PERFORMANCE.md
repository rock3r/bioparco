# Peel sticker performance

How the peel sticker went from 6 to 8 distinct frames a second in its recording to about 20, what
was measured on the way, what a run on a GPU found, and how to check it again.

## The problem

The recordings run under Xvfb, where Skia draws on the CPU: one thread, a batch of pixels at a
time through its raster pipeline. The first version drew the whole sticker with one SkSL shader, in
four full passes: the shadow of the part on the table, that part, the lifted part's shadow and the
lifted part. In the recording, 30 fps on paper, only 6 to 8 frames a second were distinct, and the
recording ran 31 s for a 24 s script.

## How it was measured

- [`SoftwareRenderingBenchmark`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/SoftwareRenderingBenchmark.kt)
  traces two things: the whole app in an `ImageComposeScene`, a `scenario …` section per frame
  (idle, hover, peel, release, swap, Halftone, Ripple, Sparkle); and the renderer drawn straight
  onto a raster `Surface`, where Skia draws at call time, so the sections time pixels rather than
  Compose recording draw calls (see [docs/TRACING.md](../docs/TRACING.md)).
- [`CpuCostLadder`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/CpuCostLadder.kt) times single
  operations per pixel, to explain what the traces showed.
- [`trace-summary.py`](../tracing/tools/trace-summary.py) reads the traces with Perfetto's trace
  processor.
- Real-window recordings under Xvfb, counting how many frames of the MP4 differ from the one
  before.
- [`GpuParity`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/GpuParity.kt) draws peels with
  Skia's OpenGL backend, on Mesa's software OpenGL under Xvfb, and checks they match the CPU
  backend: a way to see GPU-only artefacts without a GPU.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker-benchmark
BIOPARCO_BENCH=1 ./gradlew :peel-sticker:jvmTest --tests '*CpuCostLadder*' --rerun
cat peel-sticker/build/reports/cpu-cost-ladder.md
BIOPARCO_GL_PARITY=1 xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :peel-sticker:jvmTest --tests '*GpuParity*' --rerun
```

All numbers below are from a 4-core 2.1 GHz Xeon (AVX2, AVX-512), JDK 25, Skiko 0.150.1, at
density 1 in a 760 × 690 window. They move by 10 to 20% between runs on that machine.

## What changed, step by step

Raster times are medians of the renderer drawn straight onto a raster surface: the sticker at
rest, at rest with the Prism shine, and mid-peel.

| Step | At rest | Prism | Peeling |
|---|---|---|---|
| One shader, four full passes | 54 ms | 60 ms | 147 ms |
| A program per layer and shine; the flat sticker as an image except where the shine reaches; the drop shadow blurred once and cached; the lifted shadow at a quarter of the resolution | 11 ms | 21 ms | 95 ms |
| The lifted shadow blurred in its small surface rather than while scaling up; the lifted part clipped to the hull of where it can land | 7.8 ms | 15 ms | 46 ms |
| The sticker on whole pixels, drawn as a copy; the shadow scaled up with nearest sampling | 0.2 ms | 14 ms | 29 ms |
| The lifted part in three bands, each with the least program it needs | 0.3 ms | 12 ms | 14 ms |
| The fold without seams (below) | 0.2 ms | 12 ms | 16 ms |

Also:

- A new picture (P, or a dropped file) is printed on a background thread; it used to stall a frame
  for as long as printing took. See [Starting up](#starting-up) for how long that is.
- The page's dot grid is painted once per size; it cost 5.5 ms on every page redraw.
- Full app frames in `ImageComposeScene` (which redraws everything, every frame) went from 67 ms
  to about 13 ms at rest and from 154 ms to about 30 ms while peeling.
- The recording went from 6 to 8 distinct frames a second to about 20 (peaks of 27 to 29), and the
  same script from 31 s to 19 s.

Renders at 2× before and after match to within sub-pixel edge shifts, from putting the sticker on
whole pixels.

## What the measurements taught

From `CpuCostLadder` (ns per pixel):

| | ns/px |
|---|---|
| Solid fill, opaque / half transparent | 0.2 / 6 |
| Image copied onto whole pixels | 0.4 |
| Image drawn bilinear at a fractional offset | 30 |
| Runtime shader returning a constant | 4 |
| Filtered texture sample in a shader | 10 to 20 each |
| 8 × `sin`, `cos`, `sqrt` in a shader | 73 to 88 |
| The same maths in a plain Kotlin loop | 185 |
| 4 samples in an untaken `if` / after an early `return` | 5 / 80 |
| Maths after an early `return` | 8 |
| Maths in an `if` taken by half the rows / every other pixel | 42 / 83 |

- **Skiko is not the problem.** It is a thin JNI layer, and its Skia has the AVX2 and AVX-512 code
  paths. Copies and fills run at memory speed.
- **Texture samples after an early `return` run for every pixel.** Skia's CPU pipeline jumps over
  an `if` block no pixel in the batch takes, and over maths after an early `return`, but not over
  samples after one. The first shader returned early for the part on the table, with all of the
  peel's sampling after it: 175 ns a pixel for a flat sticker that needed one sample.
- **Filtered image draws are expensive, copies are not.** Drawing the flat sticker at a fractional
  position cost 4.5 ms; a copy onto whole pixels costs 0.07 ms.
- **Divergent branches run both sides.** Splitting the lifted part into bands by geometry took it
  from 17 ms to 6 ms.

Dead ends, kept here so nobody tries them again: mipmapped against plain bilinear sampling made no
difference; a polynomial `acos` instead of the built-in one barely did (kept, since it is accurate
to a hundredth of a pixel); clipping the lifted part to the die-cut's hull rather than the texture's
box did nothing for the X, whose hull is nearly its box.

## On a GPU

Codex ran the checklist below on an M1 Max (macOS, 120 Hz display, Metal) at commit `96c0887`:
every specimen traced in its window, on Metal and in software, and recorded both ways.

| Peel sticker, whole run | Metal | Software |
|---|---|---|
| `frame`, median | 0.67 ms | 1.5 ms |
| `frame`, 95th percentile | 7.3 ms; 8.1 ms while peeling, against 8.3 ms a refresh | 12 ms |
| `lifted shadow`, median | 4.2 ms | 6.1 ms |
| Frame to frame, median | 8.4 ms, the refresh interval | |
| Distinct frames a second in the recording, median while moving | 19 | 10 |

- **The lifted shadow was 96% of the sticker's drawing on Metal, and 92% of the frame.** Compose
  records a frame's draw calls and plays them back on the GPU later, but the quarter-resolution
  shadow is rasterised on a CPU surface when it is recorded, whatever the backend. It is now drawn
  as a blurred layer when the window renders on a GPU: Compose records it and the GPU renders it
  on playback. The window's render API is read on every draw, so a fallback to software goes back
  to the quarter-resolution shadow. Drawn in software, the layer costs about 27 ms more a peeling
  frame; the two shadows differ by at most 3 in 255.
- **Pale or dotted hairlines ran along the fold**, on Metal and in software alike. There were
  three:
  - between the lifted part's bands, where pixels right on a boundary fell in neither band's
    clip. The shaders now decide the band from the same `q`, and the clips only bound the work;
  - at the fold's axis, where the table part's anti-aliased edge showed the page through. The
    tight curl now starts 1.5 px before the axis, over that edge;
  - at the rim of the curl, where the roll stands edge-on and one pixel spans tens of pixels of
    the backing. Sampled once, the watermark came and went from pixel to pixel. The print now
    blends into the paper where the roll squeezes it past about 3 to 1, as a mipmap would, and
    the rim fades over its last pixel like any anti-aliased edge.

  In software, those fixes cost the lifted part about 1.3 ms a peeling frame.
- **The first frame is every specimen's slowest**: 50 to 114 ms on Metal, up to 215 ms in
  software. See [Starting up](#starting-up).

### The recheck

Codex ran it again at `6c1b36b`, from the scripted recording on each backend; the Mac was locked,
so there are no hand-driven traces this time.

| Peel sticker recording | Metal | Software |
|---|---|---|
| `frame`, median / p95 | 0.44 / 1.1 ms | 0.76 / 7.8 ms |
| `frame`, p95 while peeling | 1.1 ms, against 8.3 ms a refresh | 8.1 ms |
| `lifted shadow`, median | 0.05 ms, 10% of a peeling frame | 4.3 ms, 91% |

- **The shadow on the GPU works**: on Metal `lifted shadow` went from 4.2 ms to 0.05 ms, and a
  peeling frame's 95th percentile from 8.1 ms to 1.1 ms. The first run was driven by hand and
  this one by the recording script, but the shadow's share of the frame, 92% then and 10% now,
  leaves little doubt.
- **A dotted line still ran along the fold on Metal**, blue across the G, and not in software.
  [`GpuParity`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/GpuParity.kt) reproduced it on
  OpenGL, differing from software by up to 150 in 255. A GPU picks a texture's mip level from
  how fast its coordinates change between neighbouring pixels, and inside the shaders' branches,
  at the edge of each band, that rate is undefined, so those pixels drew from a far too coarse
  level. The shaders now sample without mipmaps, as Skia's CPU backend already did: OpenGL and
  software now agree to within 5 in 255, and software is unchanged.
- The whole recordings run at 9.1 distinct frames a second on Metal and 7.2 in software; the first
  run counted only while things moved, at a different size, so the two are not comparable. In
  software on the Mac, frames came 63 ms apart while `frame` itself took under 8 ms. Codex's
  profile found the rest after the render callback: Skiko playing the frame back, then Java2D
  converting its pixels one by one on the way to the window, 97% of the event thread's samples
  while peeling. Under Xvfb it is two thirds; see
  [docs/TRACING.md](../docs/TRACING.md#things-that-fool-you). It is Skiko's `SOFTWARE_COMPAT`
  path, not the sticker, and the recordings already show 22 to 24 distinct frames a second while
  things move, out of 30.

## Starting up

Timed in a real window under Xvfb, in software, each run in a new JVM.

**The first frame is the JVM starting cold, not the sticker.** It draws the page before the
sticker is printed, and none of the sticker's sections is in it.

| Window content | First frame |
|---|---|
| Nothing | 30 ms |
| One line of text | 80 ms |
| Jewel's theme and one line of text | 90 ms |
| The peel sticker's page | 200 to 255 ms |

Sampled every millisecond with Java Flight Recorder, the event thread spends that time loading
classes (a quarter of the samples), generating the classes behind lambdas and method handles
(another sixth; Kotlin 2 compiles lambdas to `invokedynamic`), building Jewel's theme and laying out
text for the first time, mostly before the JIT has caught up. The page's own code is a few milliseconds of it. Every specimen pays
the same, which is why the first frame was the slowest everywhere on the Mac.

A JDK 25 AOT cache ([JEP 483](https://openjdk.org/jeps/483), [JEP 514](https://openjdk.org/jeps/514),
[JEP 515](https://openjdk.org/jeps/515)), recorded in one training run, loads and links those classes
ahead of time: the first frame went from 195 to 255 ms to 78 to 120 ms. It is not wired into the
build, as it is not worth it for a specimen. The cache needs jars on the class path, a training run that quits by itself, and one cache
per specimen that goes stale with every build; the JVM ignores a stale one, with a warning. To try
it:

```bash
./gradlew :peel-sticker:jvmJar
# CP: build/libs/peel-sticker-jvm.jar plus the jvmRuntimeClasspath jars, no class directories
java -XX:AOTCacheOutput=app.aot -cp "$CP" dev.sebastiano.peelsticker.MainKt   # use it, then close it
java -XX:AOTCache=app.aot -cp "$CP" dev.sebastiano.peelsticker.MainKt
```

**The sticker appears once it is printed**, on a background thread, while the first frames draw
the empty page. Printing the first one took 745 ms, 540 ms of it the die-cut's two distance
transforms over a padded 1158 × 1158 grid, with their column passes striding through 10 MB. The
transform now finds the column distances in two integer sweeps down the rows (Meijster, Roerdink
and Hesselink) and only runs the parabola envelope along rows, all in memory order. Its output is
bit for bit the same. The die-cut now takes about 43 ms warm instead of 90, and 210 to 390 ms cold
instead of 540.

The X's and the G's die-cuts never change, though, so they now ship baked, as PNG masks in
`src/jvmMain/resources/die-cuts/`, and printing them only decodes one, in about 25 ms.
[`DieCutCacheTest`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/DieCutCacheTest.kt) checks they
still match a fresh cut (exactly, here) and rewrites them when `BIOPARCO_UPDATE_DIE_CUTS` is set. A
dropped picture is still cut when it is printed. The first sticker is now printed 100 to 160 ms
after launch instead of 745, and 25 to 50 ms once the JVM is warm. `print sticker` has a section for
each step: `draw picture`, `die-cut`, `backing` and `silhouette`.

**Then it has to reach the screen.** With the print no longer in the way, the sticker waited on:

1. the first frame, the empty page, which ends 200 to 230 ms after launch;
2. 65 to 100 ms in which Skiko hands that frame to the window, the software present described in
   [docs/TRACING.md](../docs/TRACING.md#things-that-fool-you), slow the first time;
3. the frame that first draws the sticker, 72 ms, of which 26 to 42 ms went on scaling the face to
   the stage and blurring its drop shadow, on the UI thread. Blurring even a 393 px image takes
   8 ms on this CPU, and the shadow resampled the 1024 px texture, building its mipmaps, first.

The face and its shadow are now made on the print's background thread, `prepare stage`, once the
first layout gives the stage's size, and they are ready before the first frame ends. The UI thread
only copies them: the first draw of the sticker takes 0.2 ms. Getting there needed the background
thread to wait for the size itself: hopping back to the UI thread between printing and preparing
waited for the first frame and its present, and started the preparing at 255 ms.

Warm, making them still cost 11 ms at density 1 and 50 ms at density 2: Skia's CPU backend takes
about 30 ns a pixel for any filtered draw on this machine, so scaling the face to 754 px is 17 ms
and a blur image filter over it 31 ms. (A blur mask filter would be far quicker, but Skia ignores
mask filters on image draws: the shadow came out unblurred.) Two changes:

- **On a GPU none of it is made.** The face is scaled and the shadow blurred as each frame plays
  back, which a GPU does for nothing; the CPU only records the draws. `GpuParity` checks the result
  against the software images, to within 5 in 255.
- **In software the shadow is blurred at half resolution** once its sigma reaches 5 px (density 2),
  then scaled back up: within about 1 in 255 of the full blur as drawn, and 33 ms instead of 50 in
  all. At density 1 it stays at full resolution, 11 ms.

Warm, in a new window in a JVM that has already opened a few, the frame that first shows the
sticker takes 6 to 12 ms against 1.5 to 2.5 ms for a steady frame, including rasterising the whole
window in software: composing, laying out and first drawing the stage costs 4 to 9 ms.

At launch it is slower, because the JVM loads about 150 classes the first time the stage is
composed: Compose's animation (with 16 lambdas for its vector converters), coroutine mutexes,
focus, pointer input and graphics layers, at 0.1 to 0.6 ms each. Over six launches each, moving the
images off the UI thread took the sticker's first frame from 72 to 44 ms, and the sticker reached
the screen after 349 ms instead of 394 (medians).

## Checking it on a GPU

On a GPU Skia draws the shaders there, so most of the software work above matters little. What
still runs on the CPU every frame is recording the draw calls and the band and hull clips, which
are paths. To check:

1. Run the specimen with tracing, then peel it several times, hover each shine and press P:

   ```bash
   BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:run
   ```

   Close the window to write the trace.
2. Summarise it:

   ```bash
   python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker
   ```

3. Read:
   - `frame` is Compose recording each frame, from Skiko's render callback, plus drawing done on
     the spot. It does not include playing the frame back, in software or on the GPU. Its 95th percentile should stay well under the refresh interval
     (16.7 ms at 60 Hz).
   - The frame-to-frame line shows pacing: while something moves, the median should sit on the
     refresh interval.
   - `lifted shadow` on a GPU now only records a layer, so it should be a small share of `frame`.
     If it is not, the window was not seen as GPU-backed.
4. For a like-for-like comparison, run it again in software and compare the two summaries:

   ```bash
   SKIKO_RENDER_API=SOFTWARE_COMPAT BIOPARCO_TRACE_DIR=/tmp/traces-software ./gradlew :peel-sticker:run
   ```

5. The Spectre recording also works with tracing, on macOS with Screen Recording allowed:

   ```bash
   BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :recordings:recordSpecimens
   ```
