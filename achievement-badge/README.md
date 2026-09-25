# Achievement badge

A Compose Desktop take on [Adrian](https://x.com/adriankuleszo)’s
[celebratory badge animation](https://x.com/adriankuleszo/status/2103473953868063151).
A trophy badge spins into an empty slot, bursts with a shockwave, confetti and light rays, draws
its own neon outline, catches a shine, and then idles with slowly turning rays and twinkles.

Standalone:

```bash
./gradlew :achievement-badge:run
```

Or open it from the [showcase](../README.md). The **Replay** button plays the celebration again.

## Recording

![Achievement badge](https://static.sebastiano.dev/public/3721f42d-9636-44d5-ba96-c0f77b90dd86.webp)

[mp4](https://static.sebastiano.dev/public/70e42da8-d4dc-46f3-9e64-e2ef80212447.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
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

Positions and colours were measured on the bitmap, and a render of the SVG was compared with it
pixel by pixel. The mean difference is about 7 of 255 per colour channel, mostly on anti-aliased
edges and in the bitmap's background vignette, which the SVG does not include. The animation timings come from the source video, measured frame by frame at 60 fps.
