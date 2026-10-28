# Routine timeline

This Compose Desktop specimen recreates [Ravi Sawlani](https://x.com/gravity_vi)’s
[radial routine timeline](https://x.com/gravity_vi/status/2106249721962791116). A half dial shows
twelve hours of a daily routine: morning light, a caffeine window, a last meal, winding down and
sleep. The time is always at the marker on the right. Turn the dial and the day runs past it: the
windows light up as they start, the outer arc turns from amber to violet at dusk, the sun sinks
and the moon comes up behind the clock, and the list beside the dial slides to whatever is on now
or next.

Drag the dial round like a wheel and flick it to let it coast. The mouse wheel and ↑ ↓ step a
quarter of an hour at a time. **Now** (or Home, or N) spins the dial back to the present the short
way round.

Run the specimen on its own:

```bash
./gradlew :routine-timeline:run
```

Or open it from the [showcase](../README.md).

## Recording

![Routine timeline](https://static.sebastiano.dev/stable/bioparco/routine-timeline.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/routine-timeline.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md). The recording freezes now at 9:52 AM, where the source
video starts, so every run looks the same.

## How it works

### One day round

Half the dial is twelve hours, so the whole dial is exactly one day, at a quarter of a degree a
minute. Everything on it belongs to a time of day, and the whole face turns under the marker at
three o'clock. Later times are clockwise, which is down the screen. Each window is drawn once,
at the angle of its start: an angle and the same angle a day later are the same place.

The outer arc's colours are one sweep gradient over the day, made once and turned with the face.
The hour marks, the clock digits and AM/PM are text drawn into images once, so a frame only
draws images. Changing a text layout's alpha every frame makes Skia lay it out again (see
[wheel-picker](../wheel-picker/README.md#performance)).

The lanes fade into the card near the top and bottom of the dial under a gradient the colour of
the card, and a clip keeps everything on the right half.

### The routine

`Routine.kt` is plain Kotlin and test-first. Time is a running count of minutes that scrubbing
can push past midnight or below zero, so the list keeps going round day after day.

- A window is **in window** from its start up to its end, and crosses midnight if its end comes
  first. Outside it, it is **earlier** or **later**, whichever of its edges is closer.
- The list centres on the window in progress that started last, or else on the next to start.
  It counts windows through the days, so scrolling past midnight carries on down the list
  instead of jumping back to the top.
- A window lights up over its first 20 minutes and dims over its last 20.

`DialMath.kt` turns times into angles, a pointer's turn into minutes (taking the short way across
the back of the dial), picks the copy of now nearest the dial for **Now**, and finds the next
quarter hour for a step. `DayClock.kt` formats the clock. All three have tests.

### The motion

| What | How | Why |
|---|---|---|
| Drag | Angle of the pointer round the dial's centre | What is under the pointer stays under it, like turning a wheel. The centre is a dead zone, where a small move swings the angle a long way |
| Flick | Exponential decay, friction 1.6, from the drag's velocity | A quick flick coasts a few hours and stops |
| Wheel and ↑ ↓ | Spring, no bounce, stiffness 320, to the next quarter hour | Steps queue up, so four quick presses go an hour |
| Now | Spring, damping 0.8, stiffness 42, the short way | A long spin that overshoots a few minutes and settles |
| List | Spring, damping 0.86, stiffness 260, after the focus | Rows slide rather than jump, even when a flick passes several windows |
| Sun and moon | Swap over the middle of dawn and dusk | The sun sinks and shrinks, the moon drops in from above; they are never both half there |

When a spring stops it is within a hair of its target, and the clock would read the minute
before. Steps and Now land exactly on their target.

The time and the list position are read only while drawing and in offset and layer lambdas.
There is one list row per window, composed once; each slides to where its window sits and wraps
round out of sight. Composition runs only when a label changes between earlier, in window and
later.

## Performance

Tracing sections cover the dial (`routine dial`, with `lanes`, `marks` and `clock` inside it),
the list's highlight, flings and Now. See [docs/TRACING.md](../docs/TRACING.md).

### Frame cost

[`FrameBudgetBenchmark`](src/jvmTest/kotlin/dev/sebastiano/routinetimeline/FrameBudgetBenchmark.kt)
renders the whole app in an `ImageComposeScene` at the recording's 620 × 580, all on the CPU:
composition, layout, drawing and Skia's rasterising. It renders 60 Hz frames through resting, a
slow turn, a flick and its coast, four steps and Now.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :routine-timeline:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/routine-timeline-benchmark
```

Measured on a 4-core cloud VM:

| Scenario | Median | p95 | Worst |
|---|---|---|---|
| Resting | 2.25 ms | 3.15 ms | 3.31 ms |
| Turning | 2.32 ms | 3.74 ms | 10.93 ms |
| Coasting | 3.10 ms | 4.48 ms | 5.89 ms |
| Stepping | 2.95 ms | 4.40 ms | 7.49 ms |
| Back to now | 2.67 ms | 3.22 ms | 5.23 ms |

Every whole software frame fits in a 60 Hz frame (16.7 ms), and the median uses under a fifth of
it. The specimen's own drawing (`routine dial`) is 0.08 ms at the median and 0.21 ms at worst.

### Frame pacing in a window

The Spectre recording script ran in a real 620 × 580 window under Xvfb with tracing on, per Skia
backend. It sends a pointer event every 8 ms, as a 125 Hz mouse does, so a drag never caps the
frame rate itself. The gaps between `frame` sections give the pacing. The first three seconds
(the JIT's time, before the recording starts) and idle gaps over 100 ms are left out.

| Skia backend | Intervals in one 60 Hz frame (≤ 17.5 ms) | Median | p95 | p99 | Worst |
|---|---|---|---|---|---|
| OpenGL (Mesa), two runs | 98.4–99.2% | 15.1 ms | 16.2–16.4 ms | 17.4–18.1 ms | 20.6–21.6 ms |
| `SOFTWARE_FAST` | 99.2% | 15.1 ms | 16.2 ms | 17.5 ms | 21.2 ms |
| `SOFTWARE_COMPAT` (what CI records with) | 79.4% | 15.6 ms | 21.1 ms | 25.6 ms | 53.4 ms |

On OpenGL and `SOFTWARE_FAST` no interval reaches two frames: the late ones are a few
milliseconds of jitter. `SOFTWARE_COMPAT` also copies every frame into the window through Java2D,
which is where its late frames come from, as
[docs/TRACING.md](../docs/TRACING.md#things-that-fool-you) describes. It is a cost of Skiko and the
JDK, not of the specimen: the specimen's own drawing never took more than 2.2 ms after the first
frame in that run.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :recordings:recordSpecimens --tests '*RoutineTimeline*'
python3 tracing/tools/trace-summary.py /tmp/traces/routine-timeline
```

The first traces showed the list costing frames. It composed a new row whenever the focus moved
on, and those frames took 7–14 ms against a dial drawn in 0.3 ms, with the worst interval at
72 ms. Now there is one row per window, composed once, and the worst interval is 21.6 ms.

## Differences from the original

- The original is a phone widget. This one adds a mouse wheel, the arrow keys and a flick that
  coasts.
- The routine's times are made up, after the source's windows: light 7–9 AM, caffeine until
  2 PM, a last meal 6:30–8:30 PM, winding down at 10 PM and sleep from 11 PM to 7 AM.
- The source's glow is a blur. Here it is three wider, fainter strokes, which software Skia draws
  for a fraction of the cost.
- The source's text is SF Pro; this uses Jewel's default font, with tabular figures for the clock.
