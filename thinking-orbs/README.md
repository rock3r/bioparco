# Thinking Orbs

This Compose Desktop port of [Haplo LLC’s ThinkingOrbs](https://github.com/haplollc/ThinkingOrbs)
has nine monochrome, dotted indicators for AI and agent activity. Each indicator has regular
and small variants with motion adjusted for its size.

Run the specimen on its own:

```bash
./gradlew :thinking-orbs:run
```

Or open it from the [showcase](../README.md).

## Recording

![Thinking Orbs](https://static.sebastiano.dev/stable/bioparco/thinking-orbs.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/thinking-orbs.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Designs

- **Working** moves particles along tilted orbits.
- **Searching** sweeps a meridian across a dotted globe.
- **Solving** scrambles spherical bands and returns them to their starting positions.
- **Listening** moves waveforms through latitude rings.
- **Connecting** moves a group of points and sends packets along the lines between them.
- **Weaving** twists three strands around a sphere.
- **Composing** deforms a strip with several bands.
- **Breathing** slowly deforms a ring viewed from the front.
- **Shaping** changes a dotted circle into a triangle and square.

The port keeps the original engine formulas, depth sorting, and grayscale rendering.
The 64-point and 20-point designs retain separate motion settings.

## Credit and license

This specimen ports Haplo LLC's [ThinkingOrbs](https://github.com/haplollc/ThinkingOrbs).
Jakub Antalik created the original designs and engine. Their code uses the MIT license,
which this port keeps in [LICENSE](LICENSE).
