# Thinking Orbs

A Compose Desktop port of [Haplo LLC’s ThinkingOrbs](https://github.com/haplollc/ThinkingOrbs):
nine monochrome, dotted indicators for AI and agent activity, each in regular and small
purpose-tuned sizes.

Standalone:

```bash
./gradlew :thinking-orbs:run
```

Or open it from the [showcase](../README.md).

## Recording

![Thinking Orbs](https://static.sebastiano.dev/public/47905992-e9e5-4826-9d75-2b9c205de570.webp)

[mp4](https://static.sebastiano.dev/public/a87a0cc8-46cf-4154-8f27-8279a215a757.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Designs

- **Working** — particles on tilted orbits
- **Searching** — a scan meridian sweeping a dotted globe
- **Solving** — spherical bands scrambling and returning to solved
- **Listening** — waveforms rolling through latitude rings
- **Connecting** — a wandering constellation with packets crossing its edges
- **Weaving** — three strands plaiting around a sphere
- **Composing** — an undulating multi-band sash
- **Breathing** — a face-on ring slowly deforming
- **Shaping** — a dotted circle morphing into a triangle and square

The implementation keeps the original engine formulas, depth sorting, grayscale rendering,
and independent tunings for the 64-point and 20-point designs.

## Credit and license

Ported from [ThinkingOrbs](https://github.com/haplollc/ThinkingOrbs) by Haplo LLC. The original
designs and engine are by Jakub Antalik. Their code is MIT licensed; the preserved license is
in [LICENSE](LICENSE).
