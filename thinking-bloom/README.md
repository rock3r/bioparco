# Thinking bloom

This Compose Desktop specimen recreates daniel petho ([@nonzeroexitcode](https://x.com/nonzeroexitcode))’s
[shimmering “Thinking…” label](https://x.com/nonzeroexitcode/status/2105042956583670154).
A band of colour sweeps the black label from left to right. Where it arrives, specks of colour
appear on the glyphs. They open into small flat flowers that hide the ink and puff the letters out
past their outlines, then close back to specks and leave the plain label behind. The three dots
bob as the band passes them. The sweep repeats every three seconds.

Type another word in the field under the label to bloom that instead.

Run the specimen on its own:

```bash
./gradlew :thinking-bloom:run
```

Or open it from the [showcase](../README.md).

## Recording

![Thinking bloom](https://static.sebastiano.dev/stable/bioparco/thinking-bloom.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/thinking-bloom.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The label is Jewel’s Inter at Medium weight, sized to fit the stage. The word and each dot are laid
out once. They are drawn into an alpha mask, and a jittered grid scatters about 750 blossoms over
the inked pixels, two or three across a stroke. Each blossom gets a size between 0.12 and 0.26 of
the font size, one of thirteen petal and centre colour pairs picked from the reference’s frames, a
small random lag so the band’s front is ragged, and a wobble phase.

`BloomMath` holds the timing. The band is 1.1 label widths wide and crosses the label in 2.85 s,
then the plain label rests briefly. A point’s phase says how far the band has passed it. A blossom
arrives as a speck, opens to full size with a little overshoot, holds, then shrinks back to a speck.
The ink fades out just after the specks arrive and back in just before they leave, through a
horizontal gradient on the word and an alpha on each dot. Each dot bobs on the same phase, so they
hop one after another.

The clock is a `withFrameNanos` loop. Only the draw phase reads it, so a frame redraws the stage
without recomposing it.

## Performance

Every blossom is a small image drawn ahead of time: one per colour pair, two petal turns and
whole-pixel diameter, made the first time a blossom needs it. A frame copies each open blossom’s
image onto whole pixels. Skia’s software rasteriser copies pixels about thirty times faster than it
fills the same anti-aliased petals; on a GPU these are a few hundred small textured quads. The
price is that blossoms keep a fixed turn rather than spinning, which the reference does not
visibly do either, and that the wobble moves them a pixel at a time.

[`BloomRenderingBenchmark`](src/jvmTest/kotlin/dev/sebastiano/thinkingbloom/BloomRenderingBenchmark.kt)
traces the app in an `ImageComposeScene`, which rasterises each frame on the spot, and the stage on
a raster surface at full bloom. Measured in software on a 4-core cloud VM, density 1, 960 × 572:

| Blossoms drawn as | Blossoms, raster, median | Whole scene at full bloom, median / p95 |
|---|---|---|
| A petal path through a rotated, scaled canvas (754 blossoms) | 17.7 ms | 33 / 45 ms |
| Petal paths built in device space (257 blossoms) | 5.2 ms | 17 / 28 ms |
| Pre-drawn images on whole pixels (754 blossoms) | 2.4 ms | 5.4 / 11 ms |

In a real window under Xvfb, with `SOFTWARE_FAST` (Skiko’s default there) and tracing on,
`frame` to `frame` after the first second was 15.1 ms median, 16.0 ms p95 and 17.6 ms p99 over 777
frames, with 2 gaps longer than 25 ms: Xvfb’s refresh, about 66 Hz. Thinking orbs, which also
animates without pause, measured 17.0 ms p95 and 13 long gaps under the same conditions. With
`SOFTWARE_COMPAT`, which the recordings use, presenting through Java2D caps both specimens below
60 Hz (20.6 ms median here, 28.7 ms for thinking orbs). See
[docs/TRACING.md](../docs/TRACING.md#things-that-fool-you).

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :thinking-bloom:jvmTest --tests '*BloomRenderingBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/thinking-bloom-benchmark
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :thinking-bloom:run
python3 tracing/tools/trace-summary.py /tmp/traces/thinking-bloom
```

## Limits

- Typing a new word lays out and scatters the label again, about 25 ms: one late frame per keystroke.
- The first sweep draws the blossom images as it needs them, a few per frame.
- The label is a single line of left-to-right text. The three dots are always appended.
- Blossoms land on whole pixels only if the stage does. A stage at a fractional pixel offset, as at
  some display scales, filters each blossom image instead of copying it: slower in software.
- The background stays white, as in the reference.
