# Particle ring

This Compose Desktop specimen recreates the onboarding animation that
[Anoop](https://x.com/itsonly_anoop) [shared](https://x.com/itsonly_anoop/status/2106455242342855106)
for reading Apple Health data. A Health icon waits on a black screen. Click it and its heart
pours out as 6,000 pink particles, which fountain up and settle into a slanted, swirling ring.
The ring reads your workouts, your sleep and your heart rate, then folds in on itself and lands
as a heart inside an arc, which turns solid.

Drag the ring sideways to stir it: it spins with your hand and coasts back to its own pace when
you let go. Drag it up or down to tilt it towards or away from you; it rocks back level when you
let go. The ring never folds while your hand is on it. Space or Enter steps through: it starts
the reading, folds the ring early, and plays the whole thing again from the logo, as a click on
the logo does.

Run the specimen on its own:

```bash
./gradlew :particle-ring:run
```

Or open it from the [showcase](../README.md).

## Recording

![Particle ring](https://static.sebastiano.dev/stable/bioparco/particle-ring.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/particle-ring.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md). The recording script checks the caption after each
input: the icon waits with `Ready to read your Health data`; a click on it bursts the ring, which
reads `Reading your workouts`; a flick along its near side stirs it, a pull downwards tilts it,
and it reads on to `Reading your heart rate` and folds into the logo, `Your highlights are ready`.
A click on the logo brings the icon back, Space starts the reading again, and a second Space folds
the ring early.

## How it works

**The ring.** It is a torus whose tube is a flat ribbon, tipped outwards like a lampshade's band,
seen from 25° above its plane, in perspective, and rolled so its right side rises. The tip is what
makes it look like the original: the far side shows its broad face, the near side only a thin lip,
and the ribbon's edges, seen edge-on, draw bright lines round the hole. Particles sit on or just
under the ribbon's skin, and drift along the ring and round the ribbon at their own speeds, so
the ring churns. The near side is drawn bigger and brighter than the far side, in three bands.

**The frame.** Every position is a function of time, not of the last frame, so a stalled frame
never flings a particle. Particles share six speeds along the ring and six round the ribbon: a
frame takes the sine and cosine of each speed once, and each particle adds its own starting angle
with a few multiplications, so a frame of 6,000 particles has no trigonometry per particle and
allocates nothing. Stepping them takes 0.1 ms.

**The burst.** Each particle waits somewhere in the icon's heart and leaves at its own time over
1.4 s. It flies a curve up past a bend point, fanned out by where it started in the heart, and
down onto its place in the ring, which moves as the ring turns. The flight leaves fast and eases
in over 1.6 s, so the particle lands on its moving place without a bump. As particles leave, the
icon's heart drains; then the tile greys and fades away.

**The fold.** The heart takes 58% of the particles and the arc the rest. Each set, and its places
in the logo, are put in order of their angle round the centre, and the n-th particle takes the
n-th place: the ring closes in on the logo without particles crossing it. The arc's order starts
at its gap, at the bottom. Each particle leaves after its own delay of up to 0.45 s and eases in
over 1.35 s, swirling up to 0.9 rad round the centre on the way. As the last ones land, the solid
heart and arc fade in over them and the particles fade out.

**The hand.** A sideways drag spins the ring by 1.4 rad per ring radius dragged. On release it
coasts, losing two thirds of its speed every 0.7 s, from at most 8 rad/s. An up and down drag
leans the ring on a rubber band, up to 0.5 rad, and on release a spring (stiffness 70, 0.55 of
critical damping) brings it back. A step is capped at 1/30 s.

**The heart.** It is the classic parametric heart, sampled into a 96-point outline that both draws
the icon and the logo and decides which points are inside it.

## Performance

The specimen holds 60 Hz on Skia's OpenGL path, as a window that does nothing does. The table
gives frame-to-frame intervals of the recording script, traced as in
[docs/TRACING.md](../docs/TRACING.md) while Spectre records. The control is a window of the same
size whose only content is a 4 × 40 px bar sliding back and forth; it is not checked in. Idle gaps
over 100 ms are left out. Software rows are ranges over runs interleaved with the control's.

| Under Xvfb, 560 × 680, four cores | Median | p95 | p99 | Within 17.5 ms |
|---|---|---|---|---|
| Particle ring, Skia on OpenGL (Mesa) | 15.2 ms | 16.0 ms | 16.9 ms | 99.3% |
| Control, Skia on OpenGL (Mesa) | 15.3 ms | 15.9 ms | 16.4 ms | 99.6% |
| Particle ring, Skia in software | 15.5 ms | 19.9 to 20.4 ms | 23.0 to 23.8 ms | 78% to 85% |
| Control, Skia in software | 15.4 ms | 18.1 to 18.5 ms | 20.1 to 20.2 ms | 90% to 93% |

After the window's first second, the OpenGL run keeps 99.7% of frames within 17.5 ms, as the
control does. In software, which is how CI records, even the control misses 60 Hz: Skiko presents
the whole window through Java2D every frame. There the specimen misses more than the control:
the stage costs its whole frame of pixels, against nothing for the bar.

In software, a whole frame of the app costs 1.3 to 2 ms of CPU, whatever the phase. These changes
got it there:

- The background faded to a faint plum at the bottom, as the original's does. A gradient over the
  whole window cost 7.5 ms a frame in software, five times everything else together; the
  background is plain black now.
- Until the JIT has compiled the particles' maths, a step of the burst took 3 ms instead of
  0.1 ms, for the first fifteen frames of the first burst. While the icon waits, a whole show now
  plays on a throwaway ring off the main thread, in 0.1 to 0.25 s.
- The first draw of the particles cost 9 ms on OpenGL, and of the logo 6 ms, building their
  shaders on the frame the burst or the logo started. Each is now drawn once at 1/255 alpha over
  the first two frames.
- The captions faded with `animateFloatAsState`, and the test tag Spectre reads moved from one to
  the next. Each change cost 14 to 22 ms on the first frame of every burst. The stage now steps
  the captions' alphas and their layers read them; Spectre reads the caption from one small node
  of its own.

Each band of particles is drawn with one Skia `drawPoints` call from an array with room for every
particle of its tone, since particles move between bands as they swirl. `drawPoints` has no count,
so the unused tail is parked off the canvas. Arrays of the exact size, which have to be allocated
every frame, made no measurable difference.

To repeat the measurements:

```bash
# The whole app in software, frame by frame (ImageComposeScene on a raster surface)
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :particle-ring:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/particle-ring-benchmark

# A real window on Skia's OpenGL path, under Xvfb with Mesa
BIOPARCO_TRACE_DIR=/tmp/traces xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :recordings:recordSpecimens --tests '*ParticleRing*'
python3 tracing/tools/trace-summary.py /tmp/traces/particle-ring
```

Leave `CI=true` off for the OpenGL run: it switches the recordings to software.

## Differences from the original

- The original is the onboarding of a phone app. It asks for Health access, then shows the ring
  while it reads, then the app's highlights. Here the icon starts the reading, the captions name
  what is being read, and the logo is the end.
- The original only plays. This one is a desktop window, so the hand can stir and tilt the ring,
  and the keyboard steps through.
- The original's screen fades to plum at the bottom; this one is plain black, see Performance.
