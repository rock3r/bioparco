# Hairline

This Compose Desktop specimen ports [Lucas Marques](https://github.com/lucasmarkes)’s
[hairline](https://github.com/lucasmarkes/hairline) ([live](https://hairline.lucasmarkes.com)).
Its nineteen isometric line figures respond to pointer movement on a 400 × 320 stage.
Spring animations follow the pointer. Selections use a long ease-out animation.
The `intensity` slider controls how strongly the figures respond.

| Figure | What it is | A stronger intensity |
|---|---|---|
| Riffle | A tray of eight cards. The card under the pointer stands up; the arrow keys walk the cards. | The ripple spreads further from the pulled card. |
| Terrain | Eighty-one pillars on a plinth that rise around the pointer. | A wider area rises. |
| Exploded | An app window in four layers. Moving across opens the gap; moving down picks a layer. | The layers open further. |
| Phosphor | A dot matrix that plays a loop, and fades like phosphor where the pointer paints it. | The trail lingers longer. |
| Slow | Crates riding a belt through a gate. Hovering slows the clock without stopping it. | Time slows down more. |
| Turntable | Blocks on a turntable. A flick spins it; it settles on the nearest quarter turn. | The spin coasts longer. |
| Keyboard | A sixty-key board. The key under the pointer sinks, and its neighbours follow it down. | A wider patch of keys sinks. |
| Elevator | Four floors beside an open shaft. The pointer's height picks a floor; the car travels there. | The car travels faster between floors. |
| Phone | A phone in layers: glass, board, battery, shell. Across opens the gap; down picks a layer. | The layers open further. |
| Laptop | A thin laptop, open on its hinge. The pointer's height sets the lid; it follows on a spring. | The lid opens wider. |
| Terminal | A terminal window. The pointer's height scrolls back; the line under it lifts. | The lift spreads further. |
| Cabinet | A rack of twelve blades. The pointer's height pulls the nearest ones out. | More blades come out. |
| Branches | A commit graph. The commit under the pointer rises, and its history rises after it. | More of the history rises. |
| Vault | A vault door. The pointer turns the dial; detents catch every ten; on forty the bolts draw back. | The dial coasts longer. |
| Lockers | Twelve lockers, one ajar. The locker under the pointer opens; the one at rest closes. | The door opens wider. |
| Padlock | A padlock. As the pointer comes near, the shackle lifts out and swings open. | The shackle swings further. |
| Patch | A patch panel of twenty-four ports. The cable under the pointer lifts; its neighbours lean away. | The lean spreads further. |
| Dish | A parabolic dish on a two-axis gimbal. The pointer aims it; it follows on a spring. | The dish swings further. |
| Router | A router with its antennas up. Each antenna leans toward the pointer, the nearest most. | The lean spreads further. |

Run the specimen on its own:

```bash
./gradlew :hairline:run
```

Or open it from the [showcase](../README.md). The header has the intensity slider, a light/dark
switch and a reduced-motion box. Riffle takes the arrow keys and Escape once it has focus.

## Recording

![Hairline](https://static.sebastiano.dev/stable/bioparco/hairline.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/hairline.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The original builds each figure's SVG tree once, then updates path `d` attributes and classes every frame.
The port keeps this structure close to the TypeScript source.

- `Iso.kt`, `Solids.kt`, and `Motion.kt` port `core/iso.ts` and `core/motion.ts`.
  They define the orthographic camera, its ground-plane inverse, and geometry for rings, hulls, prisms, fillets, and reflections.
  They also keep the 240 Hz substepped springs and 700ms lift tween.
- `Svg.kt` retains the tree and supports `append`, `after`, `before`, and class lists.
  `Stage.kt` handles frames, pointer input, and cleanup through a `Stage` interface that the Compose host implements.
- `figures/` contains the nineteen animation engines.
- `HairlineRenderer.kt` draws the tree in order on a `Canvas`. Nearer plates cover those behind them.
  `paintOf` in `Style.kt` implements the stylesheet cascade. Strokes stay at 0.9dp regardless of size.
  Class changes animate colour over 260ms, matching the CSS transition. Gradients fade reflections as the original mask does.
- `HairlineFigure` hosts the figure in Compose. Moving figures request frames and invalidate only drawing.
  Resting figures request no frames.

### Parity with the original

`ParityTest` compares the port with the original. The goldens in `src/jvmTest/resources/goldens/`
come from the original figures running in jsdom at commit
[`c3692e0`](https://github.com/lucasmarkes/hairline/commit/c3692e0c797956268847d843949f79714b6f7a41)
with one pointer script. The script covers rest, a flick, a circle, pauses, intensity changes, pointer exit, and Riffle's keys.
The test replays each checkpoint with a fake clock. It compares every node, class, caption, and number within 0.02 viewBox units.
All nineteen figures match. To capture new goldens, follow the instructions at the top of
[`parity/capture.test.ts`](parity/capture.test.ts).

### Where it differs

- A checkbox controls reduced motion because Desktop has no `prefers-reduced-motion` query.
  Each `HairlineFigure` accepts its own setting. Springs and tweens finish immediately when reduced motion is enabled.
  Phosphor and Slow stop ambient motion. Phosphor also stops requesting frames.
- A theme parameter selects the original's two palettes. The port has no CSS custom properties.
- The lazy grid disposes figures after they scroll away. The port has no `IntersectionObserver`.
- Each figure's content description supplies its accessible name. The port has no ARIA live region.

## Credit and license

Lucas Marques created [hairline](https://github.com/lucasmarkes/hairline), including its figures, drawing engine, names, descriptions, captions, intensity table, and palettes.
His code and the original's golden outputs use the MIT license.
This port keeps that license in [LICENSE](LICENSE) and the jar's `META-INF/LICENSE-hairline.txt`.
Every derived source file includes a notice at the top.
This repository's page, tests, and capture script use its [Apache 2.0 license](../LICENSE).
