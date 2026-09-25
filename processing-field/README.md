# Processing field

A grid of marks whose sizes describe one soft shape that drifts, folds and breathes. The
centres never move; only radius and ink change, so it reads as one surface working rather
than particles flying about.

Standalone:

```bash
./gradlew :processing-field:run
```

Or open it from the [showcase](../README.md).

## Recording

![Processing field](https://static.sebastiano.dev/public/95f54b64-486b-4270-a3a2-bc27ee39ee77.webp)

[mp4](https://static.sebastiano.dev/public/2509f71b-ae97-425e-9c53-cb88fc3702c9.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Concepts

- One `Canvas`, no composable per mark.
- A circle in mark-space, bent by three incommensurate folds, ramped across a soft
  shoulder (smoothstep).
- `.dots` fills circles; `.lines` strokes short segments tangent to the outline so they
  comb around the mass.
- `isActive = false` parks the field on a still frame (mass dead-centre, mid swell).
- The enclosure is a playground: mark style, running/still, pitch, reach, softness,
  drift/fold speeds, and mark weight (radius and ink floors/ceilings).

## Reference

- Haplo LLC's SwiftUI original: https://github.com/haplollc/ProcessingField

## Tricky bits

- The grid is nudged so a whole number of cells fills the width; leftover height is split
  top and bottom. Nothing is half-drawn at an edge.
- Speeds of 0 freeze the *clocks* at t=0, which is not the still frame: t=0 still has the
  Y-phase offset of 0.9 baked into the wander. The still frame is `time = null`.
- Cap the timeline at 30fps. Halving `pitch` roughly quadruples the mark count.
- Draw no background. The field is an overlay by design.
