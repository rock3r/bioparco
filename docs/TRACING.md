# Tracing

Every specimen, the showcase, and Spectre recordings can write a [Perfetto](https://perfetto.dev) trace.
Tracing starts disabled. A disabled section costs one field read, so sections can remain in frequently called code.

The shared `:tracing` module writes through the
[androidx.tracing](https://developer.android.com/jetpack/androidx/releases/tracing) wire driver.
Spectre's `PerfettoTracer` uses the same library.

## Turn it on

Set an output directory with `BIOPARCO_TRACE_DIR` or the `bioparco.trace.dir` system property:

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:run
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :showcase:run
BIOPARCO_TRACE_DIR=/tmp/traces CI=true xvfb-run -a ./gradlew :recordings:recordSpecimens
```

Each run writes a `.perfetto-trace` in a specimen-specific folder, such as `/tmp/traces/peel-sticker/`.
Close the window normally so the JVM can flush the file on exit. Killing the process can lose trace data.
Recording runs use one JVM and output folder per specimen.

## What is in a trace

- `frame` measures every window frame through `TracedFrames()`.
  Every specimen window and the recordings' `SpecimenWindow` call it.
  It wraps Skiko's render delegate and measures Compose recording draw calls.
  Skiko later rasterises those calls and presents the pixels outside this section.
  Gaps between sections include that later work and any stalls.
- Specimens add named sections with `Tracing.section`:

  ```kotlin
  Tracing.section("lifted shadow") { drawLiftedShadow(canvas, lifted, outline, frame, fold) }
  ```

  The trace preserves nested sections. Peel sticker adds sections for each drawing layer and for printing a new sticker.

## Read a trace

Open it at [ui.perfetto.dev](https://ui.perfetto.dev), or summarise it on the command line:

```bash
pip install perfetto
python3 tracing/tools/trace-summary.py /tmp/traces            # every trace in the folder
python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker
```

The script lists each section under its parent, with count, median, 95th percentile, and worst time.
It also reports the intervals between `frame` sections. Its first run downloads Perfetto's trace processor.

[peel-sticker/PERFORMANCE.md](../peel-sticker/PERFORMANCE.md) and
[holo-card/PERFORMANCE.md](../holo-card/PERFORMANCE.md) describe measurements from these tools.
Peel sticker's `SoftwareRenderingBenchmark` and `CpuCostLadder` tests run only when requested.
Its `GpuParity` test compares Skia's OpenGL and CPU backends under Xvfb.
Mesa's software OpenGL runs the GPU path, so the test can detect GPU-path artefacts without physical GPU hardware.

<a id="things-that-fool-you"></a>

## Measurement limits

- Compose records draw calls before Skiko rasterises them.
  A section around app drawing code usually measures well under a millisecond of recording work.
  To measure rasterisation, trace the same drawing code on `Surface.makeRasterN32Premul`, where Skia draws immediately.
  Peel sticker's optimisation used this method.
- In software, Skiko draws the recorded frame into a bitmap after `frame` ends.
  `SOFTWARE_COMPAT` then presents it through Java2D.
  Java2D converts pixels with `OpaqueCopyAnyToArgb` and `ComponentColorModel.getRGB`.
  This conversion took about two thirds of the event thread in a peel sticker recording under Xvfb.
  It also took most of the 60 ms between software frames on a Retina Mac.
  Playing back the sticker drawing took a sixth. These costs belong to Skiko and the JDK.
  `SOFTWARE_FAST` did not improve recording smoothness. Java Flight Recorder can measure this work outside the trace sections.
- Under Xvfb, Skia's raster pipeline draws batches of pixels on one CPU thread.
  A frame with low GPU cost can therefore be slow in a recording.
  The following measurements used Skia in Skiko 0.150.1 on a 2.1 GHz Xeon:

  | | ns/px |
  |---|---|
  | Solid fill, opaque / with alpha | 0.2 / 6 |
  | Image copy onto whole pixels | 0.5 |
  | Image drawn filtered (bilinear, fractional offset) | 32 |
  | Runtime shader returning a constant | 3 |
  | Filtered texture sample inside a shader | 10–20 each |
  | 8 × `sin`, `cos`, `sqrt` in a shader (the same maths in a plain Kotlin loop: 181) | 81–88 |

  Skia skips `if` blocks that no pixel takes and skips maths after early `return` statements.
  Texture samples after an early `return` still run for every pixel.
  Four samples cost 80 ns/px either way, compared with 5 ns/px inside an untaken `if`.
  Keep samples inside `if` blocks or use separate programs. Branches that differ between neighbouring pixels run both sides.
- `ImageComposeScene` redraws everything on every `render()` call. A window redraws only after a change.
  Use scene renders to compare costs. They cannot measure a window's actual frame count.
