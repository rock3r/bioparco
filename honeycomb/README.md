# Honeycomb

This Compose Desktop specimen recreates [Shubham Singh](https://x.com/Shubham_iosdev)’s
[honeycomb view](https://x.com/Shubham_iosdev/status/2106375285553992047)
([withanimation](https://www.withanimation.app/library/honeycomb-view)). Circles form a
honeycomb pattern on a black background. Drag anywhere to move the grid horizontally and
vertically. Circles at the centre have full size and brightness. Circles further from the
centre shrink and darken until they disappear near the edge.

Run the specimen on its own:

```bash
./gradlew :honeycomb:run
```

Or open it from the [showcase](../README.md).

## Recording

![Honeycomb](https://static.sebastiano.dev/stable/bioparco/honeycomb.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/honeycomb.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The grid uses circular cells. Each row shifts by half a cell, with row spacing of `pitch × √3/2`.
This gives neighbours equal spacing in every direction.

A cell's distance from the viewport centre controls its appearance. Movement speed has no effect.
Brightness stays full for the first 55dp and reaches 0 at 300dp. Scale falls from 1 to 0.25 across 400dp.
The source library's inspector uses a minimum of 0.5 and a falloff of 1000pt.
Those settings leave a cell near 0.7 scale when it has faded out.
The shorter falloff matches the video, where edge cells appear at about a quarter size.

Dragging moves the grid 1:1 with the pointer. A flick uses the library spring with response 0.4 and damping 0.85.
The grid stops without snapping to a cell. `graphicsLayer` applies scale and alpha.
Lower alpha darkens cells against the black background.
