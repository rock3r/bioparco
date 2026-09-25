# Chat bubble transition

Kavsoft’s SwiftUI “Chat Bubble Transition”, ported to Compose Desktop: the composer’s
chrome and typed text detach on Send, fly into the transcript, and morph into a sent
bubble while older messages slide up.

Standalone:

```bash
./gradlew :chat-bubble-transition:run
```

Or open it from the [showcase](../README.md).

## Recording

![Chat bubble transition](https://static.sebastiano.dev/public/a54aef31-be29-455f-baf6-bbd62f3a559a.webp)

[mp4](https://static.sebastiano.dev/public/a16ba9a3-15ed-4ea8-ae6a-7fa97945d28b.mp4)

Recorded with Spectre. Regenerate with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Concepts

- `LazyColumn(reverseLayout = true)` so new messages land at the bottom.
- `Modifier.animateItem()` slides older bubbles up in sync with the flight.
- Overlay-only morph: measure composer bounds as start, the clipped destination bubble
  as end, then lerp an overlay along a quadratic Bézier.
- Right-anchored flight so a still-wide morph does not overshoot the message margin.
- Text size stays constant. The chrome lerps from composer white to bubble grey.

## Reference

- Gist snapshot: https://gist.github.com/rock3r/4db005c28217aadb9cb7672200f98c2e
- Kavsoft’s SwiftUI original (search “Chat Bubble Transition”).

## Tricky bits

- Desktop `SharedTransitionLayout` was unreliable for this flight. The overlay path is
  the one that actually looks like Kavsoft.
- `reverseLayout` can leave the new item off-screen; the send path pins `scrollToItem(0)`
  so the destination composes and the flight can arm.
- Draw the overlay immediately at composer bounds. Waiting for the destination makes the
  text blink out.
- Use `graphicsLayer { translationX/Y }` for the overlay, not `IntOffset`. Rounding
  hitch is visible on the arc.
- Enter sends; Shift+Enter inserts a newline.
