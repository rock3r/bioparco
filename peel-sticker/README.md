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

Under Xvfb, where the recordings are made, Skia draws on the CPU. The first version drew 6 to 8
distinct frames a second there; traced and reworked, it draws about 20, and a still sticker takes
0.3 ms instead of 54 ms. Each layer and shine has its own small SkSL program, the flat sticker is a
copy on whole pixels, the drop shadow is cached, the lifted shadow is drawn at a quarter of the
resolution, and the lifted sheet is shaded in bands. The drawing layers and the printing of a new
sticker stay marked as trace sections.

[PERFORMANCE.md](PERFORMANCE.md) has the step-by-step numbers, what they taught, the benchmarks to
rerun, what a run on Metal found, and how to check it on a GPU. On a GPU window the lifted shadow
is a blurred layer the GPU renders, since the quarter-resolution one is rasterised on the CPU
whatever the backend.

The layout, colours, the G's geometry and the peel's look were read off the source video frame by
frame. White and Sparkle do not appear in the video, so those two are this specimen's own reading
of their names. The motion is a reconstruction from that video, not a port of the original code.
