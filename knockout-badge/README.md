# Knockout badge

This Compose Desktop specimen recreates the knockout video card that
[jhey](https://x.com/jh3yy) [shared](https://x.com/jh3yy/status/2108593170569175495) to show
off CSS `mix-blend-mode`. A conference pass is a black sticker with white ink, laid over a video
with `multiply`. Black multiplies the video all the way down to black; white has no darkness to
add, so the video plays through the word untouched. The rest of the badge is printed on top and
not blended.

Hover over the badge and it tips away from the pointer, as if pressed. Its top right corner is
always curled a little: grab it and peel the sticker back to see the video playing under it, and
let go to lay it back down. Click the badge, or press Space or Enter, to take it apart into its
four layers, each labelled with what it does; click again to put it back together. Type to change
the word cut out of the sticker: the first key after a pause starts a new word, Backspace edits
it, and it fits up to six characters.

Run the specimen on its own:

```bash
./gradlew :knockout-badge:run
```

Or open it from the [showcase](../README.md).

## Recording

![Knockout badge](https://static.sebastiano.dev/stable/bioparco/knockout-badge.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/knockout-badge.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md). The recording script checks the badge's state after
each input: it starts `together · init`; the pointer circles over it, then peels the corner down
to the middle and lets go; a click takes it apart, `apart · init`, and another puts it back.
Then it types `cool!`, waits for `together · cool!`, and Space takes the new word apart and puts
it back.

## How it works

**The layers.** Each layer is drawn in its own frame, the badge's, and a matrix places that frame
on the stage. Together, every matrix is the same. Apart, each one turns the layer by 30°, squashes
it to lie back like a page on a table, and lifts it up the stack. Notes fade in once the layers
have cleared each other.

**The knockout.** The black and the ink are drawn into one offscreen layer, which is composited
onto the video with `BlendMode.Multiply`, as `mix-blend-mode: multiply` does in the original.
Apart, multiplied white ink would vanish wherever no video is under it, so as the badge comes
apart the sticker fades from multiplying to lying over the video like paint: it is composited
twice, once multiplied and once plain, with alphas that add up to one.

**The video.** It is a small SkSL program rather than a file: out-of-focus club lights, twelve
warm bulbs in three rows drifting past at their own speeds and flickering, on an orange haze that
pumps at 124 bpm. Everything that depends only on time is worked out once a frame in Kotlin, so
each pixel costs a gradient and twelve squared distances. It renders at a sixth of the badge's
resolution and is drawn scaled up, since it is all soft light anyway. Together, the sticker hides
all of the video but the word, so only the word's box is drawn.

**The peel.** The corner folds like paper: the crease is the line halfway between where the corner
was and where the hand has it, square to the line joining them. What is past the crease is cut
out of the sticker, showing the video beneath, and lands mirrored across the crease as the
sticker's grey backing. Nothing is clipped to a path: the sticker and the print are drawn whole,
then the video is painted over the lifted part and the flap over that.

**The hand.** Hover presses the side under the pointer away, up to 11°, on a slightly bouncy spring
(stiffness 140, 0.55 of critical damping); it eases flat when the pointer leaves, and while the
corner is held. The corner follows the hand exactly, up to 330 dp from where it started, and
springs back when let go (stiffness 260, 0.75). Coming apart is a spring too (stiffness 70, 0.78).
A step is capped at 0.1 s, so a stalled frame never flings anything.

## Performance

The specimen holds 60 Hz on Skia's OpenGL path. The table gives frame-to-frame intervals of the
recording script, traced as in [docs/TRACING.md](../docs/TRACING.md) while Spectre records: four
runs of the specimen and three of a control, interleaved. The control is a window of the same size whose only
content is a 4 × 40 px bar sliding back and forth, driven by the same pointer, clicks, keys and
semantics polling; it is not checked in. The first second and idle gaps over 100 ms are left out.

| Under Xvfb, 760 × 720, four cores | Median | p95 | Within 17.5 ms |
|---|---|---|---|
| Knockout badge, Skia on OpenGL (Mesa) | 15.3 to 15.4 ms | 16.2 to 16.8 ms | 97.8% to 98.9% |
| Control, Skia on OpenGL (Mesa) | 15.3 ms | 16.0 ms | 99.4% to 99.8% |
| Knockout badge, Skia in software | 24 to 25 ms | 38 ms | 3% to 8% |
| Control, Skia in software | 18 to 20 ms | 26 ms | 40% to 49% |

The misses left on OpenGL are single frames of 17.5 to 20 ms, scattered across the script. In
software, which is how CI records, even the control misses 60 Hz: Skiko presents the whole window
through Java2D every frame. A whole frame of the app costs 5 to 12 ms of CPU there, the most while
it is tilted in perspective.

Without a GPU, Mesa's llvmpipe runs OpenGL on the CPU, so on that path pixels cost what they cost
in software. These changes took the share of frames within 17.5 ms from 33% to about 98.5%:

- The footage was a per-pixel program drawn at full resolution. With it switched off and nothing
  else changed, the specimen held 99.5%. It is soft light, so it now renders at a sixth of the
  badge's resolution, once a frame, and is drawn scaled up: 0.7 ms a frame at the median. Rendering
  it on a thread of its own made no measurable difference, so it does not.
- The resting curl of the corner clipped the ink and the print to the crease on every frame.
  Clipping to a path is costly on Skia's GPU backends; now nothing is clipped, and the footage
  and the flap are painted over the lifted corner instead.
- The sticker's offscreen layer covered the whole window while the badge came apart. It now
  covers the two layers it holds.
- Typing a letter rebuilt the whole badge: the globe's grid, every line of print and the barcode,
  10 to 15 ms. Only the word is laid out again now.
- The notes and the sticker's plain composite are drawn at 1/255 alpha over the first two frames,
  so their first real frames cost no more than the rest.

Drawing the print and the ink from images made of them, to spare Skia rasterising glyphs under a
moving transform, made it worse: 49% within 17.5 ms, against 64% at that point. Sampling a
badge-sized texture through a perspective costs more on llvmpipe than the glyphs do. The shadow,
the sticker's offscreen layer and the globe's grid each made no measurable difference once the
footage was cheap.

To repeat the measurements:

```bash
# The whole app in software, frame by frame (ImageComposeScene on a raster surface)
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :knockout-badge:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/knockout-badge-benchmark

# A real window on Skia's OpenGL path, under Xvfb with Mesa
BIOPARCO_TRACE_DIR=/tmp/traces xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :recordings:recordSpecimens --tests '*KnockoutBadge*'
python3 tracing/tools/trace-summary.py /tmp/traces/knockout-badge
```

Leave `CI=true` off for the OpenGL run: it switches the recordings to software.

## Differences from the original

- The original plays a video file. Here a shader stands in for it, so the specimen ships no
  media.
- The original is a web page with a config panel: blend mode, sound, notes, theme, which video
  and which print. Here the badge is the whole page, the notes come with taking it apart, and
  the word is typed straight onto it.
- The badge's holder is made up.
