# Chat bubble transition

This Compose Desktop version of Kavsoft’s SwiftUI “Chat Bubble Transition” animates sending a message.
When you select Send, the input field and typed text move into the conversation and become a sent
bubble. Older messages slide up.

Run the specimen on its own:

```bash
./gradlew :chat-bubble-transition:run
```

Or open it from the [showcase](../README.md).

## Recording

![Chat bubble transition](https://static.sebastiano.dev/stable/bioparco/chat-bubble-transition.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/chat-bubble-transition.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## Concepts

- `LazyColumn(reverseLayout = true)` places new messages at the bottom.
- `Modifier.animateItem()` slides older bubbles up while the new bubble moves.
- An overlay changes shape between the measured composer bounds and the clipped destination bubble.
  It follows a quadratic Bézier curve.
- The overlay anchors to the right, so its wider starting shape stays inside the message margin.
- The text keeps its size. The background interpolates from composer white to bubble grey.

## Reference

- Gist snapshot: https://gist.github.com/rock3r/4db005c28217aadb9cb7672200f98c2e
- Kavsoft’s SwiftUI original (search “Chat Bubble Transition”).

## Tricky bits

- Desktop `SharedTransitionLayout` did not reliably reproduce the reference transition. The implementation uses an overlay.
- `reverseLayout` can leave the new item off-screen. Sending calls `scrollToItem(0)` so Compose creates the destination before the transition starts.
- Draw the overlay at the composer bounds immediately. Waiting for the destination makes the text disappear briefly.
- Use `graphicsLayer { translationX/Y }` for the overlay. `IntOffset` rounds positions and causes visible jumps along the curve.
- Enter sends the message. Shift+Enter inserts a newline.
