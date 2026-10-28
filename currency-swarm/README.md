# Currency swarm

This Compose Desktop specimen recreates the particle login screen that
[Karthik Balasubramanian](https://x.com/_iam_karthik)
[shared](https://x.com/_iam_karthik/status/2106622203337044366). A soft cloud of 2,600 dots
condenses into ₹, holds it, then dissolves and reforms as $, ₩ and ¥ in turn. The dots never
quite sit still: each one crawls around its spot, so the signs shimmer like the original's.

Move the pointer through the dots and they part around it, then close up behind it. The sign
waits while the pointer is over it. Click the dots, or press → and ←, for the next or previous
sign, or pick one from the chips under the caption. A change mid-flight turns the dots towards
the new sign without a jump.

Run the specimen on its own:

```bash
./gradlew :currency-swarm:run
```

Or open it from the [showcase](../README.md).

## Recording

![Currency swarm](https://static.sebastiano.dev/stable/bioparco/currency-swarm.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/currency-swarm.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md). The recording script checks the caption after each
input: the cloud forms `Indian rupee`; the sign stays while the pointer sweeps through it and
moves on to `US dollar` once the pointer leaves; a click on the dots shows `South Korean won`, and
→ half a second later turns the flight to `Japanese yen`; the ₹ chip brings back `Indian rupee`,
and ← goes back to `Japanese yen`.

## How it works

**The signs.** Each sign is the outline of its glyph in Inter Bold, the font Jewel ships, so
every machine draws the same shapes whatever fonts it has. Outlines are scaled so the cap height
is one unit and centred on the origin, then 2,600 points are scattered over the ink at random.
The stage draws one unit as 40% of its width or 55% of its height, whichever is smaller.

**Who goes where.** When the sign changes, each dot needs a place in the new one. Both sets of
points are put in order along a Hilbert curve laid over them, and the n-th dot takes the n-th
place. Points close together on the curve are close together on the screen, so neighbours stay
neighbours and a sign flows into the next instead of crossing itself. Swapping partners between
dots near each other on the curve, where that shortens both flights, was tried and dropped:
between glyphs it shortened the average flight by a tenth for 6 ms a change.

**The flight.** Each dot flies for 1 s on a cubic ease in and out, after its own delay of up to
0.35 s. It bows sideways by up to 22% of the distance, and drifts off course by up to 0.07 units,
most at halfway. That puff of scatter is what makes the sign dissolve into a haze and reform, as
in the original. A new sign mid-flight starts every flight from where the dot is: dots already
in the air leave at once rather than wait out a new delay, so nothing jumps or freezes.

**The shimmer.** On top of its flight, every dot wanders by up to 0.0045 units, about a pixel, on
two sine waves of 0.7 to 2.3 Hz with their own phases. A third of the dots are dim and small, the
rest mid or bright and bigger.

**The pointer.** Dots within 0.2 units of the pointer, about 45 px, are pushed straight away from
it, harder the closer they are. Each push rides on a spring (stiffness 90, 0.6 of critical
damping), so the dots part smoothly and settle back with a hint of overshoot once the pointer
leaves. A step is capped at 1/30 s, so a stalled frame never flings the dots.

**The timing.** The cloud waits 0.6 s, then forms ₹. Each sign holds 1.5 s after its last dot
lands, then the next one starts, unless the pointer is over the dots. The waits count frame-clock
time, so a scene rendered faster or slower than real time plays the same sequence.

## Performance

The specimen holds 60 Hz on Skia's OpenGL path. Frame-to-frame intervals of the recording script,
traced as in [docs/TRACING.md](../docs/TRACING.md) while Spectre records. The control is a window
of the same size whose only content is one 4 × 40 px rectangle sliding back and forth; it is not
checked in. Idle gaps over 100 ms are left out:

| Under Xvfb, 560 × 680 | Median | p95 | p99 | Within 17.5 ms |
|---|---|---|---|---|
| Currency swarm, Skia on OpenGL (Mesa) | 15.1 ms | 16.1 ms | 19.6 ms | 98.1% |
| Control, Skia on OpenGL (Mesa) | 15.2 ms | 16.0 ms | 17.4 ms | 99.1% |
| Currency swarm, Skia in software | 15.8 ms | 23.0 ms | 32.4 ms | 67.1% |
| Control, Skia in software | 15.4 ms | 20.2 ms | 24.0 ms | 85.1% |
| Currency swarm, software, not recording | 15.6 ms | 21.9 ms | 27.1 ms | 78.5% |
| Control, software, not recording | 15.5 ms | 19.7 ms | 23.7 ms | 83.7% |

On OpenGL, most slow frames come in the first second, as the window opens. After that, about one
frame in a hundred runs over, as with the control. In software, which is how CI records, even the
control misses 60 Hz: Skiko presents the whole window through Java2D every frame. The recorder
costs the specimen more than the control, because 2,600 shimmering dots are much harder to
encode than one rectangle, and the encoder shares the machine's four cores with the window.

In software, a whole frame of the app costs 1 ms of CPU, against 0.15 ms for an empty window.
Stepping the dots takes 0.1 to 0.2 ms. Four changes got it there:

- Skia draws a round dot as a small path, but a square one as a rectangle. In software, 2,600
  round dots took 3.5 ms to draw and square ones 0.4 ms. Anti-aliased, a square under 2.5 px
  across looks like a disc, so smaller dots are square. Larger dots, on HiDPI screens, are round.
- Pairing the dots sorted boxed indices and took 10 to 38 ms on the frame of each change. One
  sort of packed `Long` keys brought it to about 1 ms, and it now runs on another thread, from a
  snapshot of the dots. The flights start on the next frame.
- The caption was a `Crossfade`, which laid out new text on the frame the dots set off. All four
  captions are now laid out once and faded on their layers.
- The sign was read where the whole stage composes, so each change recomposed the stage. Only
  the caption and the chips read it now. In the benchmark, the slowest frame of an ordinary sign
  change went from 16 ms to 5 ms.

`ImageComposeScene.render` returns a snapshot of its surface. Left open, it makes the next
frame's clear copy the whole surface into fresh memory first: up to 8 ms a frame on this
machine, more than the specimen itself. The benchmark closes each snapshot.

To repeat the measurements:

```bash
# The whole app in software, frame by frame (ImageComposeScene on a raster surface)
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :currency-swarm:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/currency-swarm-benchmark

# A real window on Skia's OpenGL path, under Xvfb with Mesa
BIOPARCO_TRACE_DIR=/tmp/traces xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :recordings:recordSpecimens --tests '*CurrencySwarm*'
python3 tracing/tools/trace-summary.py /tmp/traces/currency-swarm
```

Leave `CI=true` off for the OpenGL run: it switches the recordings to software. The Spectre
script moves the pointer every 8 ms, as a 125 Hz mouse does.

## Differences from the original

- The original is a phone app's sign-in screen, with the app's name and a sign-in button under
  the dots. Here the caption names the currency, and chips pick one.
- The original only plays. This one is a desktop window, so the pointer pushes the dots, a click
  or the arrow keys change the sign, and the sign waits while the pointer is over it.
- The signs are Inter Bold rather than the iPhone's system font.
