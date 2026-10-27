# Border beam

This Compose Desktop specimen ports [Jakub Antalik’s border-beam](https://github.com/Jakubantalik/border-beam)
([libraries.dev](https://libraries.dev/beam.html), [playground](https://beam.jakubantalik.com/)).
A glow moves around a card's border in the `md`, `sm`, and `line` variants.
The `pulse-inner` and `pulse-outside` variants pulse in place.
The `colorful`, `mono`, `ocean`, and `sunset` palettes support light and dark themes.

Run the specimen on its own:

```bash
./gradlew :border-beam:run
```

Or open it from the [showcase](../README.md).

## Recording

![Border beam](https://static.sebastiano.dev/stable/bioparco/border-beam.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/border-beam.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Credit and license

Jakub Antalik wrote the original [border-beam](https://github.com/Jakubantalik/border-beam) effect under the MIT license.
This port keeps that license in [LICENSE](LICENSE).
