# Peel sticker

This Compose Desktop specimen recreates [sucodee](https://x.com/sucodeee)’s
[sticker effect](https://x.com/sucodeee/status/2104873657885438135) for SwiftUI, React Native and
the web ([metalforge.xyz](https://metalforge.xyz)). Drag a die-cut sticker's edge to curl it up and reveal its printed backing.
Release it to return it to the flat position. Moving the pointer over the sticker changes its shine:

| Shine | What happens under the pointer |
|---|---|
| White | A soft white glare. |
| Sparkle | Glitter twinkles on and off, each glint a little tinted. |
| Prism | The colour channels split apart at every edge: red and yellow on one side, cyan and blue on the other. |
| Ripple | Rings spread out from the pointer and bend the picture, as on water. |
| Halftone | The picture turns into a printed dot screen around the pointer. |

Run the specimen on its own:

```bash
./gradlew :peel-sticker:run
```

Or open it from the [showcase](../README.md). Use the bottom tabs or keys 1 to 5 to switch shine effects.
Press P to switch between the two pictures from the original video. Drop an image file on the window to use another picture.

## Recording

![Peel sticker](https://static.sebastiano.dev/stable/bioparco/peel-sticker.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/peel-sticker.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The original runs on WebGL. This version combines cached images with separate SkSL programs for
each layer and shine effect. It draws the flat sticker and its shadow, then the lifted shadow
and sheet. GPU and software rendering use different paths, described under Performance below.

- The renderer prints the picture once into two 1024 × 1024 textures.
  A distance transform offsets the picture's coverage by 4.1% of its size to create the die-cut border.
  A morphological closing rounds inside corners. The front combines the picture and white border.
  The white backing has a faint mirrored watermark with the original's text-to-base contrast of about 1.4:1.
- `PeelCurve` defines the sheet's side-view curve in Kotlin.
  From the fold axis, the sheet rises through a tight curl, rolls over a looser curl, and ends flat and flipped.
  The shader inverts that curve per pixel. It draws the backing on the flipped part and the face on the rising curl.
- The die-cut's convex hull identifies the starting edge in the drag's direction.
  Drags anywhere on the sticker therefore lift an edge. Both curls grow with drag distance.
  A critically damped spring returns the sticker to its flat position on release.
- Shine effects use face texture coordinates, so they follow the curl.

### Performance

SkSL runtime shaders draw the peel. Metal, OpenGL, and Direct3D run those shaders on a GPU while the CPU records draw calls.
The Xvfb recordings use `SOFTWARE_COMPAT`, where Skia shades every pixel on one CPU thread.
Filtered draws cost about 30 ns a pixel on the tuning machine.
The measurements below use the shared [tracing](../docs/TRACING.md) tools.

1. The first version used one shader in four full passes.
   Software drawing took 54 ms at rest and 147 ms while peeling. Recordings showed 6 to 8 distinct frames a second.
2. Separate programs for each layer and shine reduced drawing to 11 ms at rest and 95 ms while peeling.
   The flat part uses an image, and shine shaders cover only the affected area.
   This avoids texture samples after early `return` statements, which Skia's CPU pipeline runs for every pixel.
3. Whole-pixel placement reduced sticker copies from 4.5 ms to 0.07 ms.
   The renderer caches the blurred drop shadow and draws the lifted shadow at quarter resolution.
   Three bands along the fold use separate minimal shader programs.
   These changes reached 0.3 ms at rest, 14 ms while peeling, and about 20 distinct recorded frames a second.
4. On M1 Max with Metal, the quarter-resolution lifted shadow accounted for 96% of sticker drawing.
   It still rasterised on the CPU during frame recording. The GPU path now draws that shadow as a blurred layer.
   Shadow time fell from 4.2 ms to 0.05 ms. Peeling frame time at the 95th percentile fell from 8.1 ms to 1.1 ms.
   The display refresh interval at 120 Hz is 8.3 ms.
5. Fold seams came from missed clip boundaries, the flat part's anti-aliased edge, watermark aliasing, and undefined GPU mip-level derivatives.
   Shaders now select bands from the same `q`, overlap the fold, fade compressed print, and sample without mipmaps.
   `GpuParity` compares OpenGL output with software under Xvfb using Mesa's software OpenGL.
6. Printing the first sticker originally took 745 ms after launch, mostly for distance transforms.
   Meijster's row-ordered transform reduced warm transform time from 90 ms to 43 ms. Built-in pictures now include precomputed die-cuts.
   Background printing also scales the face and blurs its shadow. The first sticker draw takes a 0.2 ms copy.
   Other cold-start work includes JVM warm-up.

The renderer selects work according to the backend:

| | On a GPU | In software |
|---|---|---|
| Lifted part's shadow | A blurred layer, drawn by the GPU as the frame plays back | Coverage shaded and blurred at a quarter of the resolution, then scaled up with nearest sampling |
| Flat sticker and its drop shadow | The texture itself, scaled and blurred each frame by the GPU | Scaled and blurred once per layout on a background thread, then copied on whole pixels; the shadow blurred at half resolution at density 2 |
| Shaders | Sample without mipmaps: in branches the GPU cannot choose a mip level | One small program per layer and shine; the lifted sheet in bands; nothing sampled after an early `return` |
| What to watch | Work that quietly rasterises on the CPU while recording | Pixels shaded, and filtered draws at about 30 ns a pixel |

The stage checks the visible windows' render API on every draw. It changes paths if a window falls back to software.
[PERFORMANCE.md](PERFORMANCE.md) lists measurements, unsuccessful approaches, Metal results, and reproduction steps.

The layout, colours, G geometry, and peel motion follow frame-by-frame observations of the source video.
The video omits White and Sparkle, so this specimen defines those effects from their names.
This implementation uses no original source code.
