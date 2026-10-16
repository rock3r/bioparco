# Component anatomy

This view separates a Jewel button into its drawing layers. The layers tilt and spread apart
as the button cycles through Normal, Hovered, Pressed, Focused and Disabled states in time with music.
Each label shows the layer's current style property and value. Changed layers glow.

The exploded view follows [Jae](https://x.com/Jaenam97)’s
[Badge Tech](https://x.com/Jaenam97/status/2104201809132990900) enamel pin. That piece is a
raymarched shader. This version uses flat Compose layers and one transformation matrix.

Run the specimen on its own:

```bash
./gradlew :component-anatomy:run
```

Or open it from the [showcase](../README.md).

## Recording

![Component anatomy](https://static.sebastiano.dev/stable/bioparco/component-anatomy.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/component-anatomy.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Controls

- The animation follows the beat until you select a state or style, toggle **Exploded**,
  or drag the stage to rotate the layers.
- The view then keeps your selection and shows a **Resume animation** link. Click it to resume
  the automatic animation.
- **Music** is off by default. When enabled, the audio supplies the beat that controls the animation.
  Machines without audio output disable the checkbox.

## What it shows

Jewel's `ButtonImpl` paints the whole button from one box, with the modifier chain
`.background().focusOutline().border()`. A background draws *before* the box's content. Both
borders draw *after* it. So the real paint order, bottom to top, is:

| # | Layer | Comes from |
|---|---|---|
| 01 | Background | `colors.background…`, `metrics.cornerSize` |
| 02 | Label | `colors.content…`, `metrics.padding`, `metrics.minSize` |
| 03 | Border | `colors.border…`, `metrics.borderWidth`, inside |
| 04 | Focus outline | `globalColors.outlines.focused`, `focusOutlineAlignment`, `metrics.focusOutlineExpand` |

The separated layers show two details:

- The focus outline is on top of everything, including the border.
- The border and the background choose their state differently. The background prefers
  Pressed and Hovered over Focused. The border prefers Focused. On the default button, the
  dark ring you see inside the focus halo is the border, painted in `borderFocused`.

## Layer and geometry tests

`ButtonLayers.kt` copies `ButtonImpl` and splits its modifier chain into one composable per layer.
`ButtonParityTest` stacks these layers flat and compares pixels against `DefaultButton` and `OutlinedButton`.
It checks every state in light and dark themes. All 40 cases must match exactly.
A change to Jewel's button drawing can therefore fail the test.

`Axonometry` builds one transformation matrix per layer. That matrix controls both the drawing and its label anchor.
`AxonometryTest` checks that both positions agree.
The view looks down on the layers. Front layers move forward and down, while each layer's far edge rises.

`AnatomyTimeline` calculates the animation from the beat: eight bars at 120 beats per minute, with one state per bar.
`GrooveSynth` generates one stereo loop in memory. The repository contains no audio files.
The loop combines a kick on each beat, a filtered clap, swung hi-hats, and off-beat sub bass.
It adds frequency-modulated electric-piano notes and a soft sustained sound over Fmaj9, Em9, Dm9, and Cmaj9.
The kick lowers the volume of all instruments except the drums.
