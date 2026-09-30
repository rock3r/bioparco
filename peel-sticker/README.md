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

### Performance

The peel is drawn by SkSL runtime shaders, so how fast it runs depends on where Skia runs them. On a
GPU (Metal, OpenGL, Direct3D) shading is nearly free and the CPU only records draw calls. In
software (`SOFTWARE_COMPAT`, which the Xvfb recordings use) Skia's raster pipeline shades every
pixel on one CPU thread, about 30 ns a pixel for any filtered draw on the machine it was tuned on.
Everything below was measured with the house [tracing](../docs/TRACING.md), which every specimen
carries.

How it got here:

1. **One shader, four full passes.** 54 ms a frame at rest and 147 ms while peeling, in software;
   the recording showed 6 to 8 distinct frames a second.
2. **A program per layer and shine**, since Skia's CPU pipeline runs texture samples after an early
   `return` for every pixel. The flat sticker became an image, with the shine shaded only where it
   reaches. 11 ms at rest, 95 ms peeling.
3. **Whole pixels and small surfaces.** The sticker is placed on whole pixels, so drawing it is a
   copy (0.07 ms instead of 4.5 ms); the drop shadow is blurred once and cached; the lifted shadow is
   shaded and blurred at a quarter of the resolution; the lifted sheet is shaded in three bands
   along the fold, each with the least program it needs. 0.3 ms at rest, 14 ms peeling, and about
   20 distinct frames a second in the recording.
4. **A run on an M1 Max (Metal)** found the quarter-resolution lifted shadow was 96% of the
   sticker's drawing there, because it rasterises on the CPU while Compose records the frame,
   whatever the backend. On a GPU it is now a blurred layer the GPU draws: 4.2 ms went to 0.05 ms,
   and a peeling frame's 95th percentile from 8.1 ms to 1.1 ms, against 8.3 ms a refresh at 120 Hz.
5. **Seams.** Dotted lines along the fold came from band clips missing boundary pixels, the table
   part's anti-aliased edge showing through, the watermark aliasing at the curl's rim, and, on the
   GPU only, mip levels picked from undefined derivatives inside shader branches. The shaders now
   pick their band from the same `q`, overlap the fold, fade the print where the roll squeezes it and
   sample without mipmaps. `GpuParity` draws the peel on Skia's OpenGL backend under Xvfb and checks
   it against software, so GPU-only artefacts show up without a GPU.
6. **Starting up.** The first sticker was printed 745 ms after launch, most of it the die-cut's
   distance transforms. The transform was rewritten (Meijster's, row by row: 90 ms to 43 ms warm) and
   the built-in pictures' die-cuts now ship baked. Scaling the face and blurring its shadow moved off
   the UI thread, into the print's background work, so the sticker's first draw is a 0.2 ms copy.
   The rest of a cold start is the JVM warming up.

GPU and software want different things:

| | On a GPU | In software |
|---|---|---|
| Lifted part's shadow | A blurred layer, drawn by the GPU as the frame plays back | Coverage shaded and blurred at a quarter of the resolution, then scaled up with nearest sampling |
| Flat sticker and its drop shadow | The texture itself, scaled and blurred each frame by the GPU | Scaled and blurred once per layout on a background thread, then copied on whole pixels; the shadow blurred at half resolution at density 2 |
| Shaders | Sample without mipmaps: in branches the GPU cannot choose a mip level | One small program per layer and shine; the lifted sheet in bands; nothing sampled after an early `return` |
| What to watch | Work that quietly rasterises on the CPU while recording | Pixels shaded, and filtered draws at about 30 ns a pixel |

The stage asks which one it is on every draw, from the render API of the windows on screen, so a
fallback to software switches paths. [PERFORMANCE.md](PERFORMANCE.md) has the step-by-step numbers,
the per-pixel cost ladder, the dead ends, the Metal results and how to check it all again.

The layout, colours, the G's geometry and the peel's look were read off the source video frame by
frame. White and Sparkle do not appear in the video, so those two are this specimen's own reading
of their names. The motion is a reconstruction from that video, not a port of the original code.
