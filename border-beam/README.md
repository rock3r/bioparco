# Border beam

A Compose Desktop port of [Jakub Antalik’s border-beam](https://github.com/Jakubantalik/border-beam)
([libraries.dev](https://libraries.dev/beam.html), [playground](https://beam.jakubantalik.com/)):
a glow that rides the border of a card. Sizes `md`, `sm`, and `line` travel; `pulse-inner` and
`pulse-outside` breathe. Palettes are `colorful`, `mono`, `ocean`, and `sunset`, in light or dark.

Standalone:

```bash
./gradlew :border-beam:run
```

Or open it from the [showcase](../README.md).

## Recording

![Border beam](https://static.sebastiano.dev/public/81fb11e5-0b99-4baf-8bd8-7ea269d75bdc.webp)

[mp4](https://static.sebastiano.dev/public/f284f1c3-b80b-458e-af76-6632575a5f42.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Credit and license

Ported from [border-beam](https://github.com/Jakubantalik/border-beam) by Jakub Antalik. The
original effect is MIT licensed; the preserved license is in [LICENSE](LICENSE).
