# Departure board

This Compose Desktop specimen recreates [HASSCO](https://x.com/itshassco)’s
[airport matrix clock](https://x.com/itshassco/status/2106761271282737292). Five world clocks
sit on a dot-matrix departure board. Each character is a 5×7 grid of dots on a near-black stage.
When a row changes, its cells run through random letters and digits and settle from left to right.

- Click the board, or **Refresh**, to send every row through a scramble, one row after another.
- Type in the search field to list matching cities. Click one, or press Enter for the first, and
  it replaces the bottom row. A city already on the board scrambles in place.
- When the minute changes, only the digits that change scramble, briefly.

Run the specimen on its own:

```bash
./gradlew :departure-board:run
```

Or open it from the [showcase](../README.md).

## Recording

![Departure board](https://static.sebastiano.dev/stable/bioparco/departure-board.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/departure-board.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

- `DotFont` holds the 5×7 glyphs as bit masks. As in the reference, the digit 0 has no slash.
- `BoardModel` is plain state with no clock. Each cell keeps the character it is leaving, the one it
  is heading for, and when it starts and stops scrambling. Callers pass the time in, so the unit
  tests check whole scrambles without waiting.
- Between start and settle, a cell shows a random glyph that changes every 55 ms. The glyph comes
  from a hash of the cell, the write and the step, so a frame looks the same however often it is drawn.
  About one glyph in eight is a blank, so the scramble flickers rather than fills.
- A whole-board scramble starts each row 110 ms after the one above. Within a row, each cell starts
  28 ms after its left neighbour and settles 420 ms later, plus up to 160 ms of per-cell jitter.
  Both edges sweep left to right, and the settle edge frays a little, as in the reference.
- Glyphs change at once. The reference has no fade between them.

## Performance

The board aims for 60 fps. While nothing scrambles, it draws nothing.

- Every glyph is drawn once, at the current dot pitch, into one immutable Skia image. Each tile
  holds the background, the unlit dots and the lit ones. A frame copies about a hundred tiles onto
  whole pixels at their own size, with nearest sampling.
- The frame loop writes a draw-phase tick only. Composition never reads it: Spectre's
  recomposition monitor counts one recomposition per whole-board scramble, from the click itself.
- The loop stops asking for frames once the last cell settles. The next write wakes it.

Measured from the recording script with `BIOPARCO_TRACE_DIR` under Xvfb, using Skiko's software
renderer at 1100 × 700 (see [docs/TRACING.md](../docs/TRACING.md)):

| | `board` section, median | `frame`, median | Animated frame-to-frame, median / p95 |
|---|---|---|---|
| Compose `drawImage` from a mutable `ImageBitmap` | 3.42 ms | 3.99 ms | 15.3 / 17.0 ms |
| Skia `drawImageRect` from an immutable `Image` | 0.03 ms | 0.59 ms | 15.3 / 16.3 ms |

Building the atlas takes about 18 ms. It happens on the first frame and again only when a resize
changes the dot pitch.

Clicks in the Jewel search field cost up to 30 ms of frame work while the JVM is cold. After about
30 s of use they drop to 5–12 ms. The board's own frames stay under 2 ms at p99.

Under `SOFTWARE_COMPAT`, which the CI recordings use, frames arrive about every 21 ms. The
board's frame work is the same. The extra time is Java2D presenting the pixels (see
[docs/TRACING.md](../docs/TRACING.md#things-that-fool-you)).

Xvfb gave Skiko no OpenGL context for a window, so these numbers do not cover the GPU path.

## Credit

The design is by [HASSCO](https://x.com/itshassco), from the
[airport matrix clock post](https://x.com/itshassco/status/2106761271282737292). This specimen
reimplements it for Compose Desktop. The cities, font and timings are reconstructions from the
video.
