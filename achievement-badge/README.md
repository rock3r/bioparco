# Achievement badge

This Compose Desktop specimen recreates [Adrian](https://x.com/adriankuleszo)’s
[celebratory badge animation](https://x.com/adriankuleszo/status/2103473953868063151).
The animation spins a trophy badge into an empty slot and adds a shockwave, confetti and light rays.
It then draws a neon outline and a moving highlight. The rays keep turning while small lights flash.

Run the specimen on its own:

```bash
./gradlew :achievement-badge:run
```

Or open it from the [showcase](../README.md). The **Replay** button plays the celebration again.

## Recording

![Achievement badge](https://static.sebastiano.dev/stable/bioparco/achievement-badge.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/achievement-badge.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## The artwork

Adrian shared the source SVG only as a bitmap. [art/badge.svg](art/badge.svg) is a reconstruction
of it, and the Compose drawing uses the same geometry:

- The badge is two six-point stars with 90° tips, rotated 30° from each other. The back star is
  yellow at the top and pink at the bottom. The front star is the purple face.
- The neon line is the face's outline, moved 38 px inward, with gaps for the handles and the base.
- The light fan is five flat bands. Each band gets its own white ramp towards the cup.
- The fan, the sparkle and the trophy sit 5 px left of the stars. That offset is in the source
  bitmap, so the reconstruction keeps it.

The reconstruction uses positions and colours measured from the bitmap.
A pixel comparison between the SVG render and bitmap found a mean difference of about 7 of 255 per colour channel.
Most differences occur at anti-aliased edges and in the bitmap's background vignette. The SVG omits that vignette.
The animation uses timings measured frame by frame from the source video at 60 fps.
