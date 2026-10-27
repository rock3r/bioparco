# Dot-matrix recorder

This floating control simulates video recording and screenshots. Every icon is a 5×5 grid of dots.
At rest, the control is a small vertical pill. Hover over it to open the menu. Select Record
to count down 3, 2, 1 on the red indicator. The control then shows a timer and scrolling diagonal stripes.

Run the specimen on its own:

```bash
./gradlew :dot-matrix-recorder:run
```

Or open it from the [showcase](../README.md).

## Recording

![Dot-matrix recorder](https://static.sebastiano.dev/stable/bioparco/dot-matrix-recorder.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/dot-matrix-recorder.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How to use it

| State | Collapsed | Hovered |
|---|---|---|
| Idle | Vertical pill: lens and screenshot frame | Record, Screenshot |
| Counting down | Horizontal pill: digit and a grey `00:00` | Grey `00:00` (click to cancel), Screenshot |
| Recording | Horizontal pill: striped lens and timer | Timer (click to stop), Restart, Delete |

Stop and Delete both return the control to idle. This demo does not capture video or screenshots.

## Concepts

- `RecorderState` is a reducer that takes time as a parameter. Unit tests can check the countdown and timer without a clock.
- The recording starts at the countdown's scheduled end. A late frame does not shorten the recording time.
- `DotMatrix` eases each of the 25 dots towards its own target level. Glyph changes fade one dot at a time.
- The dot frame loop writes only a draw-phase tick. The timer text recomposes once per second.
- One `AnimatedContent` morphs between four faces (dock, menu, recording menu, pill). A
  `SizeTransform` spring moves the outline, and each face fades in with a short blur.
- A second `AnimatedContent`, keyed on a session counter, blurs the whole pill out on Stop
  or Delete and brings the idle pill back.

## Matching the reference

The implementation uses dot states measured frame by frame from the 4K reference video:

- Recording shows diagonal stripes. They are 3.5 columns wide, repeat every 7.7 columns, scroll
  at 6.6 columns per second, and each lower row runs 0.33 columns ahead of the row above.
- Hovering Record runs one stripe across the resting lens, then returns to rest.
- The countdown shows each digit for 800 ms, then flashes every dot for 200 ms.
  The "3" follows the reference's shape.
- At rest, the dim ring shimmers very slightly.
- Colours, the dock size (as tall as the two-row menu), row heights, and the ~200 ms morphs come
  from the same measurements.

## Credit

The design is by [Sasha Birukoff](https://x.com/sashabirukoff), from the
[Halogen teaser video](https://x.com/sashabirukoff/status/2103156002220589129). This is a
Compose rebuild from that video. It does not use the original code or assets.

## Drawing and timing limits

- Draw the outline and fill on a `Box` around `AnimatedContent`.
  Putting them on the `AnimatedContent` modifier made the outline disappear during dot redraws without size animation in `ImageComposeScene` renders.
- Each face gets a fixed state snapshot (`Look`). Otherwise, the outgoing face would show the new state while fading out.
- The fade blur uses whole multiples of 4 px. Smaller values disable blur.
  Skia chooses a GPU blur program from `ceil(3 * sigma)`, according to the Skia m150 sources.
  Skia compiles each program synchronously on its first draw.
  A radius that decreased smoothly to zero selected several programs and stalled frames by ~100 ms, measured with androidx.tracing.
  Keeping a continuous radius inside one program band caused 43–51 ms stalls.
  Fixed pixel steps keep program selection consistent across display densities.
- Collapsing waits 220 ms after the pointer leaves. Without the wait, the pill can flicker when
  the outline shrinks under a pointer that has not moved.
- The record lens hides its four corner dots, so it reads as round. The countdown digits are
  drawn inside that same 21-dot disc.
