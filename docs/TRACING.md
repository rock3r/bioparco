# Tracing

Every specimen, the showcase and the Spectre recordings can write a
[Perfetto](https://perfetto.dev) trace. Tracing is off unless you ask for it, and a trace section
that is off costs one field read, so sections can stay in hot paths for good.

The `:tracing` house module holds it. It writes through
[androidx.tracing](https://developer.android.com/jetpack/androidx/releases/tracing)'s wire
driver, the same library Spectre's own `PerfettoTracer` uses.

## Turn it on

Name a directory in `BIOPARCO_TRACE_DIR`, or in the `bioparco.trace.dir` system property:

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:run
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :showcase:run
BIOPARCO_TRACE_DIR=/tmp/traces CI=true xvfb-run -a ./gradlew :recordings:recordSpecimens
```

Each run writes a `.perfetto-trace` into a folder named after the specimen, such as
`/tmp/traces/peel-sticker/`. The file is flushed when the JVM exits, so close the window rather
than killing the process. A recording run writes one folder per specimen, since each recording has
a JVM of its own.

## What is in a trace

- `frame`: every frame a window renders, from `TracedFrames()`, which every specimen's window and
  the recordings' `SpecimenWindow` call. It wraps the window's Skiko render delegate, so it sees
  each frame whatever changed. It covers Compose recording the frame's draw calls, not Skiko
  playing them back into pixels and handing them to the window, which comes after it: see below.
  Gaps between `frame` sections are that, or stalls.
- Anything a specimen marks with `Tracing.section`:

  ```kotlin
  Tracing.section("lifted shadow") { drawLiftedShadow(canvas, lifted, outline, frame, fold) }
  ```

  Nested sections nest in the trace. Peel sticker marks each of its drawing layers and the
  printing of a new sticker, for example.

## Read a trace

Open it at [ui.perfetto.dev](https://ui.perfetto.dev), or summarise it on the command line:

```bash
pip install perfetto
python3 tracing/tools/trace-summary.py /tmp/traces            # every trace in the folder
python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker
```

For each trace it lists every section, nested under its parent, with count, median, 95th
percentile and worst time, and the frame-to-frame pacing of the `frame` sections. The first run
downloads Perfetto's trace processor.

For a worked example, the peel sticker's software rendering was traced and optimised with these
tools: see [peel-sticker/PERFORMANCE.md](../peel-sticker/PERFORMANCE.md), and its
`SoftwareRenderingBenchmark` and `CpuCostLadder` tests, which are skipped unless asked for. Its
`GpuParity` test draws on Skia's OpenGL backend under Xvfb, with Mesa's software OpenGL, and
compares with the CPU backend: GPU-only artefacts show up there without a GPU.

## Things that fool you

- **Compose records draw calls and rasterises them later.** A section around drawing code in an
  app times the recording of the draw calls, which is usually well under a millisecond, not the
  pixels. To time the pixels, call the same drawing code on a raster `Surface`
  (`Surface.makeRasterN32Premul`), where Skia draws at call time, and trace that. The peel sticker
  was optimised that way.
- **In software, most of a frame is after the `frame` section.** Skiko plays the recorded frame
  back into a bitmap, then `SOFTWARE_COMPAT` hands it to the window through Java2D, whose
  per-pixel colour conversion (`OpaqueCopyAnyToArgb`, `ComponentColorModel.getRGB`) took about
  two thirds of the event thread in a peel sticker recording under Xvfb, and most of the 60 ms
  between frames in software on a Retina Mac. Playing back the sticker's drawing took a sixth.
  That is Skiko's and the JDK's, not a specimen's; `SOFTWARE_FAST` did not make the recordings
  any smoother. Java Flight Recorder sees it where the trace cannot.
- **Skia's CPU backend is not the GPU.** Under Xvfb (the recordings) Skia draws on the CPU, one
  thread, a batch of pixels at a time through its raster pipeline. A frame that is cheap on a GPU
  can be slow in a recording. Measured on the Skia in Skiko 0.150.1 (per pixel, 2.1 GHz Xeon):

  | | ns/px |
  |---|---|
  | Solid fill, opaque / with alpha | 0.2 / 6 |
  | Image copy onto whole pixels | 0.5 |
  | Image drawn filtered (bilinear, fractional offset) | 32 |
  | Runtime shader returning a constant | 3 |
  | Filtered texture sample inside a shader | 10–20 each |
  | 8 × `sin`, `cos`, `sqrt` in a shader (the same maths in a plain Kotlin loop: 181) | 81–88 |

  Skia jumps over an `if` block that no pixel in the batch takes, and over maths after an early
  `return`, but **not over texture samples after an early `return`**: those run for every pixel
  (4 samples: 80 ns/px either way, 5 ns/px inside an untaken `if`). Keep samples inside `if`
  blocks, or give each case its own program. A branch that differs between neighbouring pixels
  runs both sides.
- **`ImageComposeScene` redraws everything on every `render()`**, unlike a window, which only
  redraws when something changed. It is fine for comparing costs, not for counting frames.
