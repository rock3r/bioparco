# Component anatomy

A Jewel button, taken apart into the layers it really paints. The stack tilts, opens up, and
tours the button's states to a beat: Normal, Hovered, Pressed, Focused, Disabled. Every layer has
a label that names the style property it is painting right now, and the value. When a state
changes, the layers that changed glow.

The exploded view is after [Jae](https://x.com/Jaenam97)’s
[Badge Tech](https://x.com/Jaenam97/status/2104201809132990900) enamel pin. That piece is a
raymarched shader. This one is plain Compose: no shaders, just flat layers and one matrix.

Standalone:

```bash
./gradlew :component-anatomy:run
```

Or open it from the [showcase](../README.md).

## Recording

![Component anatomy](https://static.sebastiano.dev/stable/bioparco/component-anatomy.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/component-anatomy.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Playing with it

- The stage follows the beat until you touch something: a state chip, the style chips, the
  **Exploded** checkbox, or a drag on the stage to orbit the stack.
- Then it holds what you picked, and a **Back to groovin'** link appears. Click it to hand the
  stage back to the beat.
- **Music** is off by default. Turn it on and the beat comes from the audio itself, so the picture
  stays locked to what you hear. The checkbox is disabled on machines with no audio output.

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

Two things this makes visible:

- The focus outline is on top of everything, including the border.
- The border and the background choose their state differently. The background prefers
  Pressed and Hovered over Focused. The border prefers Focused. On the default button, the
  dark ring you see inside the focus halo is the border, painted in `borderFocused`.

## How it stays honest

The specimen does not draw a lookalike. `ButtonLayers.kt` forks `ButtonImpl` and splits its
modifier chain into one composable per layer. `ButtonParityTest` stacks those layers flat and
compares them, pixel by pixel, with a real `DefaultButton` and `OutlinedButton`, in every state,
in the light and the dark theme. All 40 cases must match exactly. If Jewel changes how a button
paints, the test fails before the diagram can lie.

The 3D is one matrix per layer, built by `Axonometry`. The same matrix draws the layer and places
its label's anchor, so a leader line always points at its layer. `AxonometryTest` checks that the
two agree.

The view looks down on the stack, so the front layers come towards you and down, and each
layer's far edge rises. A view from below is just as valid geometrically, but the eye reads it
inside out.

The choreography is `AnatomyTimeline`, a pure function of the beat: eight bars at 120 BPM, one
state per bar. The groove is synthesised in memory by `GrooveSynth`, in stereo: a
four-on-the-floor kick, a filtered clap, swung hats, an off-beat sub bass, FM electric-piano
stabs and a soft pad over Fmaj9, Em9, Dm9 and Cmaj9. Everything but the drums ducks under the
kick. The groove is exactly one loop long, and the repo ships no audio files.
