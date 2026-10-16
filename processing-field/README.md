# Processing field

A grid of marks forms an animated shape. The mark centres stay fixed while their
radius and opacity change over time.

Run the specimen on its own:

```bash
./gradlew :processing-field:run
```

Or open it from the [showcase](../README.md).

## Recording

![Processing field](https://static.sebastiano.dev/stable/bioparco/processing-field.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/processing-field.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Concepts

- One `Canvas` draws every mark.
- Three folds with incommensurate periods deform a circle in mark-space. A smoothstep function softens its boundary.
- `.dots` draws filled circles. `.lines` draws short segments tangent to the outline.
- `isActive = false` selects a still frame with the shape centred and halfway through its expansion.
- The controls adjust mark style, animation, pitch, reach, softness, drift and fold speeds, and mark weight.
  Mark weight includes lower and upper limits for radius and opacity.

## Reference

- Haplo LLC's SwiftUI original: https://github.com/haplollc/ProcessingField

## Layout and timing limits

- The grid adjusts to fit whole cells across its width. It divides spare height between the top and bottom to avoid partial edge marks.
- Setting speeds to 0 freezes the clocks at t=0. That time still includes the Y-phase offset of 0.9.
  Use `time = null` for the centred still frame.
- Cap the timeline at 30fps. Halving `pitch` roughly quadruples the mark count.
- Keep the background transparent so the field can act as an overlay.
