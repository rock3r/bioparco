# Hairline

A Compose Desktop port of [Lucas Marques](https://github.com/lucasmarkes)’s
[hairline](https://github.com/lucasmarkes/hairline) ([live](https://hairline.lucasmarkes.com)):
nineteen isometric line figures that answer the pointer. Each one is drawn in hairlines on a
400 × 320 stage, with springs for what follows the pointer and a long ease-out for what it picks.
One `intensity` slider sets how strongly every figure answers, from subtle to strong.

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

Standalone:

```bash
./gradlew :hairline:run
```

Or open it from the [showcase](../README.md). The header has the intensity slider, a light/dark
switch and a reduced-motion box. Riffle takes the arrow keys and Escape once it has focus.

## Recording

![Hairline](https://static.sebastiano.dev/stable/bioparco/hairline.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/hairline.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The original draws into SVG: each figure builds a tree of groups, paths, circles and ellipses once,
then rewrites their `d` attributes and classes every frame. The port keeps that shape, so each
figure reads almost line for line against its TypeScript:

- `Iso.kt`, `Solids.kt` and `Motion.kt` are the original's `core/iso.ts` and `core/motion.ts`: the
  orthographic camera and its ground-plane inverse, rounded rings with their normals, hulls,
  prisms, fillets and reflections, the 240 Hz substepped springs and the 700ms lift tween.
- `Svg.kt` is a small retained tree with the few DOM moves the figures make (`append`, `after`,
  `before`, class lists). `Stage.kt` is the original's frame loop, pointer and tear-down, behind a
  `Stage` interface the Compose host implements.
- `figures/` holds the nineteen engines.
- `HairlineRenderer.kt` paints the tree in order on a `Canvas`, so a nearer plate covers what is
  behind it, as in the original. The stylesheet's cascade is `paintOf` in `Style.kt`. Strokes are
  0.9dp at any size, like the original's non-scaling strokes; a class change eases its colour
  over 260ms, as the CSS transition does; reflections fade out through a gradient, as the mask does.
- `HairlineFigure` is the composable. A figure asks for frames only while something moves, and its
  drawing invalidates in the draw phase only, so a figure at rest costs nothing.

### Parity with the original

`ParityTest` holds the port to the original. The goldens under `src/jvmTest/resources/goldens/`
were captured by running the original figures in jsdom (commit
[`c3692e0`](https://github.com/lucasmarkes/hairline/commit/c3692e0c797956268847d843949f79714b6f7a41))
through one pointer script: rests, a flick, a circle, dwells, intensity changes and leaving, plus
Riffle's keys. At every checkpoint the test replays the same steps on a fake clock and compares the
whole tree, node for node and class for class, every number within 0.02 viewBox units, and the
caption. All nineteen match. To capture them again, follow the note at the top of
[`parity/capture.test.ts`](parity/capture.test.ts).

### Where it differs

- Desktop has no `prefers-reduced-motion` query, so reduced motion is a checkbox. It does what the
  original's does: springs and tweens land at once, and Phosphor and Slow stop their ambient motion.
- The theme is a parameter rather than CSS custom properties, with the original's two palettes.
- There is no `IntersectionObserver`: the grid is lazy, so a figure scrolled away is disposed.
- The accessible name is the figure's content description; there is no ARIA live region.

## Credit and license

Ported from [hairline](https://github.com/lucasmarkes/hairline) by Lucas Marques: the figures,
their drawing engine, their names, descriptions and captions, the intensity table and the palettes
are his. His code is MIT licensed; the preserved license is in [LICENSE](LICENSE), every source
file derived from it says so at its top, and the jar carries it as `META-INF/LICENSE-hairline.txt`.
The goldens are output of the original, under the same license. The rest (the page, the tests and
the capture script) is this repository's, under its [Apache 2.0 license](../LICENSE).
