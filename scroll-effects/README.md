# Scroll effects

A Compose Desktop take on [sucodee](https://x.com/sucodeee)’s
[scroll effects](https://x.com/sucodeee/status/2104191756350713967) for SwiftUI, React Native and
the web ([metalforge.xyz](https://metalforge.xyz)). A looping carousel of six grainy gradient
cards, and six ways for a card to leave the centre:

| Effect | What happens away from the centre |
|---|---|
| Stretch | The card flips over, so the neighbours show their mirrored backs. Their outer half smears out to the window edge and flares like a trumpet bell, with an iridescent rim. |
| Bulge | The strip wraps around a cylinder that curves away from you. The neighbours shrink towards the edges and a lens swells the centre card. |
| Drum | The camera sits inside the cylinder. The neighbours grow taller towards the edges. |
| Shatter | The card breaks into Voronoi shards that drift apart, tilt and turn, then close up again. |
| Thanos | The card turns to dust from its outer edge, and the grains blow outwards and fade. |
| Glitch | The card shrinks back, its bands jump sideways, its colour channels split, and scan bars flicker over it. |

Bulge and Drum also blur with the carousel's speed, as the original does.

Standalone:

```bash
./gradlew :scroll-effects:run
```

Or open it from the [showcase](../README.md). Drag the cards, scroll, or use the ← and → keys.
The tabs at the bottom switch the effect.

## Recording

![Scroll effects](https://static.sebastiano.dev/stable/bioparco/scroll-effects.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/scroll-effects.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The original runs on WebGL. Here every effect is a mesh of textured triangles, drawn with
Skia's `drawVertices`:

- Each card is painted once into a bitmap: gradient blobs, a grain overlay, the number and its
  chart. The bitmap is the texture for all six effects.
- The effects are plain Kotlin in `commonMain`. For each card and each frame they move the mesh
  vertices according to the card's distance from the centre. Stretch and the two cylinders bend a
  grid, Shatter moves Voronoi cells, and Thanos moves about 10,000 one-grain quads per card.
- Glitch adds a small SkSL shader for the colour split, the scanlines and the blocky noise.
- Mesh edges fall on transparent padding around the card art, so the rounded corners stay
  anti-aliased.

The card designs, colours and numbers were read off the source video frame by frame. The motion
is a reconstruction from that video, not a port of the original code.
