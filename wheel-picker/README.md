# Wheel picker

This Compose Desktop specimen recreates [Alex](https://x.com/a_cucos)’s
[wheel picker with edge distortion](https://x.com/a_cucos/status/2104949571461829086). A
duration ruler runs under a fixed indicator. The ticks squeeze, fade and soften toward the
ends, and split into red, green and blue as they go, as if the ruler were printed on a drum
seen through a cheap lens. The duration above it rolls one character at a time, each old
character rising and blurring out as the new one comes up and sharpens.

Drag the ruler, or flick it and let it coast to a stop. A mouse wheel or trackpad steps one
stop at a time. Click a tick to go to it. ← and → step, Home and End jump to the ends.

Run the specimen on its own:

```bash
./gradlew :wheel-picker:run
```

Or open it from the [showcase](../README.md).

## Recording

![Wheel picker](https://static.sebastiano.dev/stable/bioparco/wheel-picker.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/wheel-picker.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md). The recording script also checks the readout after
each input: Home shows `∞`, a click four ticks along shows `20m`, two → presses show `30m`, three
wheel notches show `45m`, and End shows `24h`, still after a pull past the end.

## How it works

**Stops.** The ruler holds 35 stops that get coarser as durations grow, as in the original:
no limit (`∞`), then 5 minutes apart to an hour, quarter hours to 2h, half hours to 4h, hours
to 12h, and two hours to 24h. The labelled majors are `∞`, 1h, 2h, 4h, 8h, 12h and 24h.

**The lens.** A tick `d` along the ruler from the indicator is drawn at `r × sin(d / r)`, where
`r` is half the ruler's width: true to size at the centre, tighter and tighter toward the ends,
gone at `d = r × π/2`. The same angle sets the effects. Visibility is `cos^1.5`. The edge amount
`1 − cos` widens a tick up to 3.2× and dims it as much, which is what a blurred line looks
like, and pulls its red copy outward and its blue copy inward. The copies are drawn with
`BlendMode.Plus`, so they add back to white where they overlap. The split also grows with
speed, and is capped below the tick's width, so a tick gets coloured edges rather than turning
into three ticks of one colour each. Labels narrow with the drum and keep their grey, with only
faint red and blue halos: thin strokes split three ways read as three labels, green brightest.
Ticks right next to the indicator stand up to a third taller.

**Motion.** A drag moves the ruler 1:1 and rubber-bands past either end, by at most 0.6 of a
stop. A release always lands on a stop: where its velocity would carry the ruler in 0.18 s,
rounded, then a spring (damping 0.82, stiffness 260) takes it there. A click undoes the lens to
find the tick under the pointer. The ruler's position is read only in the draw phase, so
scrolling never recomposes it.

**The readout.** The duration is spread over seven slots. When a slot's character changes,
the old one rises 22dp, fades and blurs to 9dp while the new one comes up from below and
sharpens, on a spring (damping 0.78, stiffness 320). Slots start 28 ms apart, left to right,
and each slot's width follows its roll, so the readout recentres as it goes. Every glyph is
laid out once and drawn into nine small images, sharp to fully blurred. A rolling glyph
cross-fades the two nearest, drawn on whole pixels, so a roll costs image copies, not a text
layout and a live blur.

## Performance

The specimen holds 60 Hz. Frame-to-frame intervals of the recording script, traced as in
[docs/TRACING.md](../docs/TRACING.md) while the recorder runs. The control is a window of the
same size whose only content is one 4 × 40 px rectangle sliding back and forth; it is not checked
in. Idle gaps over 100 ms are left out:

| Under Xvfb, 920 × 560 | Median | p95 | p99 | Within 17.5 ms |
|---|---|---|---|---|
| Wheel picker, Skia on OpenGL (Mesa) | 15.4 ms | 16.3 ms | 17.7 ms | 98.5% |
| Control, Skia on OpenGL (Mesa) | 15.4 ms | 16.1 ms | 22.7 ms | 97.4% |
| Wheel picker, Skia in software | 19.9 ms | 29.2 ms | 44.6 ms | 38.0% |
| Control, Skia in software | 19.9 ms | 24.8 ms | 27.9 ms | 42.3% |

On the OpenGL path the specimen keeps pace with the control. In software, which is how CI
records, even the control misses 60 Hz: Skiko draws and presents the whole window every frame,
so the time follows the window's pixels, not what is in it. At a quarter of the pixels the
same script runs at a 15.4 ms median. On OpenGL, the only frames over 34 ms come as the window
opens, while the first frame lays out the readout's glyphs.

In software, a frame of the whole app takes 1.7 ms at rest and about 3 to 4 ms while it rolls or
is dragged. Three changes got it there:

- The readout glyphs were `Text`s with a live `BlurEffect`. A roll cost 7.4 ms a frame; blur
  steps drawn up front brought it to between 3 and 4 ms.
- Each roll used to lay out its new glyphs, and the first rolls' frames took 10 to 15 ms.
  Every glyph is now laid out once, as the readout starts.
- The ruler labels were text layouts drawn with a new alpha and colour every frame, which makes
  Skia update the paragraph and lay it out again. Drawn as images, they went from 0.66 ms to
  0.08 ms to record.

To repeat the measurements:

```bash
# The whole app in software, frame by frame (ImageComposeScene on a raster surface)
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :wheel-picker:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/wheel-picker-benchmark

# A real window on Skia's OpenGL path, under Xvfb with Mesa
BIOPARCO_TRACE_DIR=/tmp/traces xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :recordings:recordSpecimens --tests '*WheelPicker*'
python3 tracing/tools/trace-summary.py /tmp/traces/wheel-picker
```

Leave `CI=true` off for the OpenGL run: it switches the recordings to software. The Spectre
script sends a pointer event every 8 ms, as a 125 Hz mouse does. A drag only redraws when the
pointer moves, so a slower script would cap the frame rate itself.

## Differences from the original

- The original is a phone timer. This one is a desktop window, so it adds a mouse wheel, clicks
  and the arrow keys.
- The edge blur is drawn as a wider, dimmer tick rather than a real blur, which keeps every
  frame cheap in software.
