# Scroll effects

This Compose Desktop specimen recreates [sucodee](https://x.com/sucodeee)’s
[scroll effects](https://x.com/sucodeee/status/2104191756350713967) for SwiftUI, React Native and
the web ([metalforge.xyz](https://metalforge.xyz)). The looping carousel has six grainy gradient
cards. Choose from six effects that animate cards as they move away from the centre:

| Effect | What happens away from the centre |
|---|---|
| Stretch | The card flips over, so the neighbours show their mirrored backs. Their outer half smears out to the window edge and flares like a trumpet bell, with an iridescent rim. |
| Bulge | The strip wraps around a cylinder that curves away from you. The neighbours shrink towards the edges and a lens swells the centre card. |
| Drum | The camera sits inside the cylinder. The neighbours grow taller towards the edges. |
| Shatter | The card breaks into Voronoi shards that drift apart, tilt and turn, then close up again. |
| Thanos | The card turns to dust from its outer edge, and the grains blow outwards and fade. |
| Glitch | The card shrinks back, its bands jump sideways, its colour channels split, and scan bars flicker over it. |

Bulge and Drum also blur with the carousel's speed, as the original does.

Run the specimen on its own:

```bash
./gradlew :scroll-effects:run
```

Or open it from the [showcase](../README.md). Drag the cards, scroll, or use the ← and → keys.
The tabs at the bottom switch the effect.

## Recording

![Scroll effects](https://static.sebastiano.dev/stable/bioparco/scroll-effects.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/scroll-effects.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The original uses WebGL. This implementation draws each effect as textured triangles through Skia's `drawVertices`.

- The renderer paints each card's gradients, grain, number, and chart into one bitmap.
  All six effects use that bitmap as their texture.
- Kotlin code in `commonMain` moves each card's mesh vertices every frame according to its distance from the centre.
  Stretch and the two cylinder effects bend a grid. Shatter moves Voronoi cells.
  Thanos moves about 10,000 one-grain quads per card.
- Glitch adds a small SkSL shader for the colour split, the scanlines and the blocky noise.
- Mesh edges fall on transparent padding around the card art, so the rounded corners stay
  anti-aliased.

The card designs, colours, numbers, and motion follow frame-by-frame observations of the source video.
This implementation uses no original source code.
