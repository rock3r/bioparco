# Grabby stepper

Drag the number to change the count, or use the −/+ buttons in the same pill.
The dark track stretches toward the pointer and springs back with the thumb on release.

Run the specimen on its own:

```bash
./gradlew :grabby-stepper:run
```

Or open it from the [showcase](../README.md).

## Recording

![Grabby stepper](https://static.sebastiano.dev/stable/bioparco/grabby-stepper.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/grabby-stepper.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Concepts

- The pill handles taps on −/+ and drags on the thumb.
- After the pointer crosses the movement threshold, the control locks to an axis.
  Horizontal movement changes the count. Vertical movement pulls the count towards zero.
- `Animatable` uses low-stiffness, medium-bouncy springs for motion.
- `graphicsLayer` and `offset` lambdas read `displayX`, `displayY`, and scale values.
  Reading these values during composition cancels `pointerInput` and reduces drag updates to ~15fps.
- Arrow keys and −/+ change the count. The 0, Delete, Backspace, and Home keys reset it.
  The control also exposes semantic actions.

## Reference

- Vishal Paliwal’s original: https://x.com/iamvishal16_ios/status/2100596660032172287

## Motion and input limits

- Preserve 60fps dragging. If the thumb stutters, check whether composition reads `motion.liveX` or `Animatable.value`.
- The pill bounds clamp thumb travel. The track follows a fraction of that travel through
  `HORIZONTAL_STRETCH_FOLLOW` and `VERTICAL_STRETCH_FOLLOW`.
- Hit-testing uses the resting pill's local coordinates. Input in the surrounding overflow area must miss the control.
- Keep frame-rate and recomposition probes behind `DEBUG_MOTION_PROBES`.
- Tests still cover the older metaball-based `elasticTrackPath` geometry.
  The visible control uses `graphicsLayer` scaling to reduce UI-thread work during dragging.
