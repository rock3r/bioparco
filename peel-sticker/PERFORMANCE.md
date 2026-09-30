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

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker-benchmark
BIOPARCO_BENCH=1 ./gradlew :peel-sticker:jvmTest --tests '*CpuCostLadder*' --rerun
cat peel-sticker/build/reports/cpu-cost-ladder.md
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

- A new picture (P, or a dropped file) is printed on a background thread. Printing takes 300 ms to
  1 s; it used to stall a frame that long.
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
| Distinct frames a second in the recording | 19 | 10 |

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
  software. Shader compilation and, here, printing the first sticker. Not looked into yet.

## Checking it on a GPU

The lifted shadow's layer has not been measured on a GPU yet; this is how to. On a GPU Skia draws the shaders there, so most of the software work above matters little. What
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
   - `frame` is the CPU side of each frame, from Skiko's render callback. On a GPU it does not
     include the GPU's own work. Its 95th percentile should stay well under the refresh interval
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
