# Peel sticker

A Compose Desktop take on [sucodee](https://x.com/sucodeee)’s
[sticker effect](https://x.com/sucodeee/status/2104873657885438135) for SwiftUI, React Native and
the web ([metalforge.xyz](https://metalforge.xyz)). A die-cut sticker sits on the table. Grab it by
an edge and peel it off: it curls up, rolls over and shows its printed backing. Let go and it lays
itself back down. Move over it and its face catches the light:

| Shine | What happens under the pointer |
|---|---|
| White | A soft white glare. |
| Sparkle | Glitter twinkles on and off, each glint a little tinted. |
| Prism | The colour channels split apart at every edge: red and yellow on one side, cyan and blue on the other. |
| Ripple | Rings spread out from the pointer and bend the picture, as on water. |
| Halftone | The picture turns into a printed dot screen around the pointer. |

Standalone:

```bash
./gradlew :peel-sticker:run
```

Or open it from the [showcase](../README.md). The tabs at the bottom, or the keys 1 to 5, switch
the shine. P swaps the picture between the two from the original video, and dropping an image file
on the window prints that instead.

## Recording

![Peel sticker](https://static.sebastiano.dev/stable/bioparco/peel-sticker.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/peel-sticker.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The original runs on WebGL. Here the whole sticker is one SkSL shader, drawn in four passes: the
soft shadow of the part still on the table, that part, the shadow the lifted part casts, and the
lifted part itself.

- The picture is printed once into two 1024 × 1024 textures. The die-cut border is an exact
  distance-transform offset of the picture's coverage, 4.1% of its size, with the inside corners
  rounded by a closing. The front is the picture on that white border; the back is a white
  backing with a faint mirrored watermark, at the original's text-to-base contrast (about 1.4:1).
- The peel is a side-view curve in plain Kotlin (`PeelCurve`): from the fold axis the sheet stands
  up around a tight curl, rolls back over a looser one, then lies flipped and flat. The shader
  inverts it per pixel, so it draws the backing wherever the sheet has come over, and the face
  on the rising curl.
- The peel starts from the sticker's edge in the direction the drag came from, found from the
  die-cut's convex hull, so a drag from anywhere on the sticker lifts an edge instead of popping in
  halfway across. Both curls grow with the drag. On release a critically damped spring lays the
  sticker back down.
- The shines run on the face in texture space, so they follow the sticker onto the curl.

### Software rendering

Under Xvfb, where the recordings are made, Skia draws on the CPU, where each filtered texture
sample costs 10 to 20 ns a pixel, and samples after an early `return` still run for every pixel
(see [docs/TRACING.md](../docs/TRACING.md)). The first shader returned early for the part on the
table, with all the peel's sampling after it. Traced with
the house tracing (see [docs/TRACING.md](../docs/TRACING.md)), the first version took 54 ms to draw a still sticker and 147 ms mid
peel (6 to 8 distinct frames a second in the recording). Now:

- Each layer and shine has its own small SkSL program, and the lifted sheet is drawn in three bands
  (flat flap, loose roll, tight curl), each with the least program it needs, clipped to the hull of
  where the die-cut can land.
- The plain sticker is a copy of the face pre-scaled to the stage, on whole pixels: a copy costs
  0.07 ms where any filtered image draw costs about 4.5 ms. Only the area the shine reaches is shaded.
- The drop shadow is blurred once per layout. The lifted part's shadow is shaded and blurred at a
  quarter of the resolution, then scaled up with nearest sampling.
- A new picture is printed off the UI thread.

A still sticker now draws in 0.2 ms and a peel in about 14 ms, and the recording shows about 20
distinct frames a second, near its 30 fps cap. On a GPU none of this was ever slow. The drawing
layers and the printing of a new sticker stay marked as trace sections.

The layout, colours, the G's geometry and the peel's look were read off the source video frame by
frame. White and Sparkle do not appear in the video, so those two are this specimen's own reading
of their names. The motion is a reconstruction from that video, not a port of the original code.
