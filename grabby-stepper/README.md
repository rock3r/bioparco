# Grabby stepper

The count is a **thumb**, not a label. − / + live in the same pill. The dark track is
rubber: it stretches toward the grab, then springs home with the thumb.

Standalone:

```bash
./gradlew :grabby-stepper:run
```

Or open it from the [showcase](../README.md).

## Recording

![Grabby stepper](https://static.sebastiano.dev/stable/bioparco/grabby-stepper.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/grabby-stepper.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Concepts

- Pointer input on one pill: tap − / +, or grab the thumb.
- Axis lock after slop: horizontal scrub vs vertical pull-to-zero.
- Springs everywhere (`Animatable` + low stiffness / medium-bouncy).
- **Layer-only motion reads.** `displayX` / `displayY` / scales are read inside
  `graphicsLayer` (and `offset` lambdas), never during composition. Reading them in
  composition cancels `pointerInput` and drops the grab to ~15fps.
- Keyboard and semantics: arrows and −/+ step; 0 / Delete / Backspace / Home reset.

## Reference

- Vishal Paliwal’s original: https://x.com/iamvishal16_ios/status/2100596660032172287

## Tricky bits

- **Do not regress the 60fps path.** If the thumb stutters, you probably let a
  composable read `motion.liveX` or an `Animatable.value` again.
- Thumb travel is hard-clamped to the pill. The track follows only a fraction of that
  travel (`HORIZONTAL_STRETCH_FOLLOW` / `VERTICAL_STRETCH_FOLLOW`).
- Hit-testing uses rest-pill local coordinates. Overflow gutters around the control
  must miss.
- Debug FPS / recompose probes stay behind `DEBUG_MOTION_PROBES`.
- `elasticTrackPath` is the older gooey-metaball math, still tested. The live chrome
  currently stretches with `graphicsLayer` scale so the grab stays on the UI thread’s
  cheap path.
