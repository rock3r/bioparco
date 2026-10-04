# Honeycomb

A Compose Desktop take on [Shubham Singh](https://x.com/Shubham_iosdev)’s
[honeycomb view](https://x.com/Shubham_iosdev/status/2106375285553992047)
([withanimation](https://www.withanimation.app/library/honeycomb-view)). Circles packed in a
honeycomb sit on black. Drag anywhere and the field follows the pointer, horizontally and
vertically at once. A circle at the centre is full size and full brightness. Further out it
shrinks hard and darkens, until the ones near the edge disappear into the background.

Standalone:

```bash
./gradlew :honeycomb:run
```

Or open it from the [showcase](../README.md).

## Recording

![Honeycomb](https://static.sebastiano.dev/stable/bioparco/honeycomb.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/honeycomb.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The cells are circles, not hexagons. Each row is offset by half a cell, and the row spacing is
`pitch × √3/2`, so the gap between neighbours is the same in every direction.

What a cell looks like depends only on how far its centre is from the viewport centre, not on how
fast the field is moving. Brightness stays full for the first 55dp and reaches 0 at 300dp. Scale
falls from 1 to 0.25 across 400dp. The library's inspector uses a minimum of 0.5 and a falloff of
1000pt, which still leaves a cell near 0.7 when it has already faded out; the shorter falloff is
what makes the edge of the video read at about a quarter size.

While the pointer is down the pan is 1:1. A flick coasts to a stop on the library spring (response
0.4, damping 0.85). It does not snap to a cell. Scale and brightness are applied in `graphicsLayer`,
and the darkening is the cell's alpha over the black background.
