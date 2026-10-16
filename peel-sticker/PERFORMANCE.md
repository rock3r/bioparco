# Peel sticker performance

Software-rendering changes increased peel sticker recordings from 6 to 8 distinct frames a second to about 20.
This guide lists the measurements, GPU findings, and commands for repeating the checks.

## The problem

Under Xvfb, Skia's raster pipeline draws batches of pixels on one CPU thread.
The first version used one SkSL shader in four full passes.
The passes drew the flat part's shadow, the flat part, the lifted shadow, and the lifted part.
The recording targeted 30 fps but contained only 6 to 8 distinct frames a second.
A 24 s script produced a 31 s recording.

## How it was measured

- [`SoftwareRenderingBenchmark`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/SoftwareRenderingBenchmark.kt) traces the app in `ImageComposeScene` and the renderer on a raster `Surface`.
  The scene trace adds a `scenario …` section per frame for idle, hover, peel, release, swap, Halftone, Ripple, and Sparkle.
  On the raster surface, Skia draws immediately, so sections measure rasterisation.
  Scene sections measure Compose recording draw calls. See [docs/TRACING.md](../docs/TRACING.md).
- [`CpuCostLadder`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/CpuCostLadder.kt) measures individual operations per pixel to explain the trace costs.
- [`trace-summary.py`](../tracing/tools/trace-summary.py) reads the traces with Perfetto's trace
  processor.
- Real-window recordings under Xvfb measure distinct MP4 frames by comparing each frame with the previous one.
- [`GpuParity`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/GpuParity.kt) compares Skia's OpenGL output with its CPU output.
  Mesa's software OpenGL runs under Xvfb, so the test detects GPU-path artefacts without physical GPU hardware.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker-benchmark
BIOPARCO_BENCH=1 ./gradlew :peel-sticker:jvmTest --tests '*CpuCostLadder*' --rerun
cat peel-sticker/build/reports/cpu-cost-ladder.md
BIOPARCO_GL_PARITY=1 xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :peel-sticker:jvmTest --tests '*GpuParity*' --rerun
```

Unless a section names another machine, measurements use a 4-core 2.1 GHz Xeon with AVX2 and AVX-512, JDK 25, and Skiko 0.150.1.
The window measures 760 × 690 at density 1. Results vary by 10 to 20% between runs on that machine.

## What changed, step by step

The table reports median renderer times on a raster surface.
The cases show the sticker at rest, at rest with Prism shine, and during peeling.

| Step | At rest | Prism | Peeling |
|---|---|---|---|
| One shader, four full passes | 54 ms | 60 ms | 147 ms |
| A program per layer and shine; the flat sticker as an image except where the shine reaches; the drop shadow blurred once and cached; the lifted shadow at a quarter of the resolution | 11 ms | 21 ms | 95 ms |
| The lifted shadow blurred in its small surface rather than while scaling up; the lifted part clipped to the hull of where it can land | 7.8 ms | 15 ms | 46 ms |
| The sticker on whole pixels, drawn as a copy; the shadow scaled up with nearest sampling | 0.2 ms | 14 ms | 29 ms |
| The lifted part in three bands, each with the least program it needs | 0.3 ms | 12 ms | 14 ms |
| The fold without seams (below) | 0.2 ms | 12 ms | 16 ms |

Other changes reduced app and recording costs:

- A background thread prints new pictures selected with P or dropped files.
  Printing previously blocked a frame for its entire duration. [Starting up](#starting-up) lists printing times.
- The renderer caches the page's dot grid per size. Drawing that grid previously cost 5.5 ms on every page redraw.
- Full-app `ImageComposeScene` frames fell from 67 ms to about 13 ms at rest, and from 154 ms to about 30 ms while peeling.
  `ImageComposeScene` redraws the entire app on every frame.
- Recordings increased from 6 to 8 distinct frames a second to about 20, with peaks of 27 to 29.
  The same script's recording shortened from 31 s to 19 s.

Before-and-after renders at 2× match within sub-pixel edge shifts caused by whole-pixel sticker placement.

## What the measurements taught

`CpuCostLadder` measured these costs in nanoseconds per pixel:

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

- Skiko uses a thin Java Native Interface layer. Its Skia build includes AVX2 and AVX-512 paths.
  Copies and fills run at memory speed.
- Skia's CPU pipeline skips an `if` block when no pixel takes it. It also skips maths after an early `return`.
  Texture samples after that `return` still run for every pixel.
  The first shader placed all peel sampling after the flat part's early return.
  It cost 175 ns a pixel for a flat sticker that needed one sample.
- Drawing the flat sticker at a fractional position cost 4.5 ms. A whole-pixel copy costs 0.07 ms.
- Branches that differ between neighbouring pixels run both sides.
  Splitting the lifted part into geometric bands reduced its time from 17 ms to 6 ms.

Mipmapped sampling gave the same performance as plain bilinear sampling.
A polynomial `acos` gave little improvement over the built-in function. The renderer keeps it because its error stays within a hundredth of a pixel.
Clipping to the die-cut hull gave no improvement for X, whose hull nearly fills its texture box.

## On a GPU

At commit `96c0887`, Codex traced and recorded every specimen on an M1 Max running macOS with a 120 Hz display.
The checks used both Metal and software rendering in specimen windows.

| Peel sticker, whole run | Metal | Software |
|---|---|---|
| `frame`, median | 0.67 ms | 1.5 ms |
| `frame`, 95th percentile | 7.3 ms; 8.1 ms while peeling, against 8.3 ms a refresh | 12 ms |
| `lifted shadow`, median | 4.2 ms | 6.1 ms |
| Frame to frame, median | 8.4 ms, the refresh interval | |
| Distinct frames a second in the recording, median while moving | 19 | 10 |

The lifted shadow accounted for 96% of sticker drawing on Metal and 92% of the frame.
The quarter-resolution shadow rasterised on a CPU surface during recording, regardless of the window backend.
GPU windows now use a blurred layer that the GPU renders during playback.
The renderer checks the window API on every draw and restores the quarter-resolution path after a software fallback.
In software, the blurred layer costs about 27 ms more per peeling frame. The shadow images differ by at most 3 in 255.

Metal and software both showed three pale or dotted seams along the fold:

- Pixels on band boundaries could miss both clips. Shaders now choose bands from the same `q`; clips only limit drawing work.
- The flat part's anti-aliased edge exposed the page at the fold axis.
  The tight curl now begins 1.5 px before that axis and covers the edge.
- At the edge-on curl rim, one screen pixel covered tens of backing pixels. Single samples made the watermark alternate between pixels.
  The renderer now fades the print into its white backing beyond about 3 to 1 compression, like mipmapping.
  It also fades the rim over its final pixel for anti-aliasing.

These fixes added about 1.3 ms per software peeling frame to the lifted part.
Every specimen's first frame was its slowest: 50 to 114 ms on Metal and up to 215 ms in software.
See [Starting up](#starting-up).

### The recheck

At `6c1b36b`, Codex repeated the measurements with scripted recordings on each backend.
These results contain no manually driven window traces.

| Peel sticker recording | Metal | Software |
|---|---|---|
| `frame`, median / p95 | 0.44 / 1.1 ms | 0.76 / 7.8 ms |
| `frame`, p95 while peeling | 1.1 ms, against 8.3 ms a refresh | 8.1 ms |
| `lifted shadow`, median | 0.05 ms, 10% of a peeling frame | 4.3 ms, 91% |

On Metal, `lifted shadow` fell from 4.2 ms to 0.05 ms.
Peeling frame time at the 95th percentile fell from 8.1 ms to 1.1 ms.
The first run used manual input and the second used the recording script.
The shadow's share of the frame fell from 92% to 10%.

Metal still showed a dotted blue line across the G's fold that software did not show.
[`GpuParity`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/GpuParity.kt) reproduced it on OpenGL, with differences up to 150 in 255 against software.
GPUs choose mip levels from coordinate changes between neighbouring pixels.
Those derivatives were undefined inside shader branches at band edges, causing overly coarse samples.
Shaders now sample without mipmaps, matching Skia's CPU backend.
OpenGL and software agree within 5 in 255. Software output remains unchanged.

Whole recordings reached 9.1 distinct frames a second on Metal and 7.2 in software.
The earlier run counted only moving periods at a different size, so these frame rates cannot be compared directly.
On the Mac's software backend, frames arrived 63 ms apart while `frame` took under 8 ms.
Codex's profile found the remaining work after the render callback: Skiko playback and Java2D per-pixel conversion for presentation.
That work accounted for 97% of event-thread samples while peeling. Under Xvfb, it accounted for two thirds.
See [docs/TRACING.md](../docs/TRACING.md#measurement-limits) for the `SOFTWARE_COMPAT` presentation path.
The recordings show 22 to 24 distinct frames a second during movement, out of 30.

## Starting up

Startup measurements use a real window under Xvfb with software rendering and a new JVM for each run.
The first frame draws the empty page before background printing finishes. It contains none of the sticker's trace sections.

| Window content | First frame |
|---|---|
| Nothing | 30 ms |
| One line of text | 80 ms |
| Jewel's theme and one line of text | 90 ms |
| The peel sticker's page | 200 to 255 ms |

Java Flight Recorder sampled the event thread every millisecond.
Class loading accounted for a quarter of samples. Generating classes for lambdas and method handles accounted for another sixth.
Kotlin 2 compiles lambdas to `invokedynamic`.
Other startup work included building Jewel's theme and laying out text before the just-in-time compiler had warmed up.
The page's own code took a few milliseconds. Every specimen had similar startup costs on the Mac.

A JDK 25 ahead-of-time (AOT) cache loads and links classes recorded during a training run.
See [JEP 483](https://openjdk.org/jeps/483), [JEP 514](https://openjdk.org/jeps/514), and [JEP 515](https://openjdk.org/jeps/515).
The cache reduced first-frame time from 195 to 255 ms to 78 to 120 ms.
The build leaves this option disabled because maintaining a cache per specimen adds work for these small demos.
The cache needs jars on the class path and a training run that exits on its own.
Each build invalidates the cache. The JVM ignores stale caches and prints a warning.
Use these commands to try it:

```bash
./gradlew :peel-sticker:jvmJar
# CP: build/libs/peel-sticker-jvm.jar plus the jvmRuntimeClasspath jars, no class directories
java -XX:AOTCacheOutput=app.aot -cp "$CP" dev.sebastiano.peelsticker.MainKt   # use it, then close it
java -XX:AOTCache=app.aot -cp "$CP" dev.sebastiano.peelsticker.MainKt
```

A background thread prints the sticker while initial frames draw the empty page.
The original first print took 745 ms. Two die-cut distance transforms took 540 ms over a padded 1158 × 1158 grid.
Their column passes accessed 10 MB with non-contiguous reads.
The current transform uses Meijster, Roerdink, and Hesselink's algorithm.
Two integer sweeps down rows find column distances. The parabola envelope runs only along rows, preserving memory order.
The output matches bit for bit. Die-cut time fell from 90 ms to about 43 ms warm, and from 540 ms to 210 to 390 ms cold.

The built-in X and G use precomputed PNG masks in `src/jvmMain/resources/die-cuts/`.
Decoding a mask for printing takes about 25 ms.
[`DieCutCacheTest`](src/jvmTest/kotlin/dev/sebastiano/peelsticker/DieCutCacheTest.kt) checks exact equality against a fresh cut.
Setting `BIOPARCO_UPDATE_DIE_CUTS` makes the test rewrite the masks. Dropped pictures still need a fresh cut when printed.
The first print now finishes 100 to 160 ms after launch, compared with 745 ms before.
Printing takes 25 to 50 ms with a warm JVM.
The `print sticker` trace contains `draw picture`, `die-cut`, `backing`, and `silhouette` sections.

After printing improved, three other steps delayed the visible sticker:

1. The empty page's first frame ended 200 to 230 ms after launch.
2. Skiko spent another 65 to 100 ms presenting that frame to the window.
   [docs/TRACING.md](../docs/TRACING.md#measurement-limits) describes this software presentation work, which is slower on its first run.
3. The first frame containing the sticker took 72 ms.
   UI-thread face scaling and shadow blur accounted for 26 to 42 ms.
   Blurring a 393 px image took 8 ms on this CPU.
   Before blurring, the shadow also resampled the 1024 px texture and built mipmaps.

The printing thread now prepares the face and shadow in `prepare stage` after the first layout supplies the stage size.
The images are ready before the first frame ends. The UI thread copies them in 0.2 ms on the sticker's first draw.
The background thread waits directly for the stage size.
Returning to the UI thread between printing and preparation previously delayed preparation until 255 ms, after the first frame's presentation.

Warm preparation still cost 11 ms at density 1 and 50 ms at density 2.
Filtered draws cost about 30 ns a pixel on this CPU.
Scaling the face to 754 px took 17 ms; applying a blur image filter took 31 ms.
Skia ignores mask filters on image draws, so the faster blur mask filter left the shadow unblurred.
The renderer now handles each backend separately:

- GPU playback scales the face and blurs the shadow each frame. The CPU only records those calls and creates no prepared images.
  `GpuParity` compares the result with software images within 5 in 255.
- Software rendering blurs the shadow at half resolution once sigma reaches 5 px at density 2, then scales it back up.
  The result differs from the full blur by about 1 in 255 and reduces total preparation from 50 ms to 33 ms.
  At density 1, preparation stays at full resolution and takes 11 ms.

In a warm JVM that has opened several windows, the first sticker frame takes 6 to 12 ms, including whole-window software rasterisation.
Steady frames take 1.5 to 2.5 ms. Composing, laying out, and first drawing the stage costs 4 to 9 ms.

Cold stage composition loads about 150 classes at 0.1 to 0.6 ms each.
These cover Compose animation, coroutine mutexes, focus, pointer input, and graphics layers. Animation vector converters account for 16 lambdas.
Across six launches per version, moving image work off the UI thread reduced the median first sticker frame from 72 ms to 44 ms.
The median time until the sticker reached the screen fell from 394 ms to 349 ms.

## Checking it on a GPU

On GPU backends, the CPU still records draw calls and builds the band and hull clip paths every frame.
Use the following checks to measure that work and frame pacing.

1. Run the specimen with tracing, then peel it several times, hover each shine and press P:

   ```bash
   BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:run
   ```

   Close the window to write the trace.
2. Summarise it:

   ```bash
   python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker
   ```

3. Inspect the summary:
   - `frame` measures Compose recording in Skiko's render callback and any immediate drawing.
     It excludes later software or GPU playback. Its 95th percentile should stay well below the refresh interval, 16.7 ms at 60 Hz.
   - During movement, the median frame-to-frame interval should stay close to the refresh interval.
   - On a GPU, `lifted shadow` only records a layer and should take a small share of `frame`.
     A larger share suggests that the renderer did not detect a GPU-backed window.
4. Repeat the same interaction in software and compare the summaries:

   ```bash
   SKIKO_RENDER_API=SOFTWARE_COMPAT BIOPARCO_TRACE_DIR=/tmp/traces-software ./gradlew :peel-sticker:run
   ```

5. The Spectre recording also works with tracing, on macOS with Screen Recording allowed:

   ```bash
   BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :recordings:recordSpecimens
   ```
