# Email invite

This Compose Desktop specimen recreates [sucodee](https://x.com/sucodeee)’s
[email invite component](https://x.com/sucodeee/status/2106836743257542853).
A white pill holds three overlapping avatars and a grey “+ Invite” button. Click Invite and the pill
grows into an email field: the avatars tuck behind the front one, the plus turns into an envelope,
“Invite” greys into the placeholder “Invite by email” and a Send button blurs in. Send stays grey
until the address is whole, then turns black. Send it and the button turns blue and reads “Sent”;
the address blurs away, the new guest hops out of the field into the front of the stack, and the
pill folds back to “+ Invite” as a shine sweeps the new avatar.

Click Invite or press Enter to open the field. Enter or Send sends; Escape, or a click outside the
pill, closes it without sending. The stack shows four people at most: past that, the oldest leaves
from the back.

Run the specimen on its own:

```bash
./gradlew :email-invite:run
```

Or open it from the [showcase](../README.md).

## Recording

![Email invite](https://static.sebastiano.dev/stable/bioparco/email-invite.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/email-invite.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The pill sits in a fixed box. `PillGeometry` places everything from three moving values: how open
the field is, how tucked the avatars are, and how many avatars the stack makes room for. The pill
stays centred while it grows and shrinks. The sizes are measured off the video at about 0.4 dp per
source pixel: a 56dp pill, 35dp avatars stepping 24dp apart (8dp tucked), a 100dp button that
grows into a 260dp field.

The pill, its shadow, the field and the icon are drawn in one draw block. The avatars, labels, field
text and Send button are composables placed and faded in graphics layers. Composition reads only
the phase (collapsed, editing, sent) and the guest list, which change once per open, send or close.
Everything else is read while drawing, so a frame of motion redraws without recomposing.

The plain logic is test-first: `EmailAddress` (when Send arms, and the new avatar’s letter),
`InviteFlow` (the phases and the only ways between them, so a held Enter sends once),
`GuestStack` (newest first, four at most) and `HeldKeys` (one action per key press).

### The motion

| What | How | Why |
|---|---|---|
| Opening | spring, damping 0.78, stiffness 380 | About 250 ms with a hair of overshoot, as the video’s field |
| Avatars tucking | spring, damping 0.85, stiffness 420 | They slide behind the front one; only its letter stays |
| Plus to envelope | 160 ms, handed over at halfway | The plus shrinks away before the envelope grows in, so they never overlap |
| Placeholder | “Invite” greys in at 60 ms, “ by email” sharpens from a blur at 110 ms | The button’s label becomes the placeholder’s first word |
| Send | blurs in from 220 ms | As in the video, it arrives last |
| Arming | 160 ms grey to black | Once the address has a name, one `@` and a dotted domain ending in two letters or more |
| Sending | a 70 ms press, then 120 ms to blue as the last letter blurs from d to t | |
| Hold | 230 ms of blue “Sent” | The video starts closing about 370 ms after the press |
| Closing | the address blurs away first; Send at 90 ms; the field folds at 90 ms on a softer spring (damping 0.86, stiffness 240) | The video staggers it: the stack fans out with the new guest a beat before the field folds, so the pill briefly grows before it shrinks |
| The new guest | a 380 ms hop from above the field, arcing over the stack, scaling up with a little overshoot and sharpening from a blur | |
| Shine | 520 ms white band across the new avatar | |
| The previous newest | 500 ms from blue to a soft lavender | Only the latest invite keeps the accent |

## Performance

Tracing sections cover the pill (`invite pill`), each avatar, and each `open`, `send`, `arrive` and
`cancel`. See [docs/TRACING.md](../docs/TRACING.md).

### Frame cost

[`FrameBudgetBenchmark`](src/jvmTest/kotlin/dev/sebastiano/emailinvite/FrameBudgetBenchmark.kt)
renders the whole app in an `ImageComposeScene` at the recording’s 800 × 600, which composes, lays
out, draws and rasterises every frame on the CPU. It plays a whole invite (open, type, send, the
close and the new guest’s arrival) and then a cancel.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :email-invite:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/email-invite-benchmark
```

Measured twice on a 4-core cloud VM, density 1:

| Scenario | Median | p95 | Worst |
|---|---|---|---|
| Opening | 2.0–2.2 ms | 2.9–3.8 ms | 7.1–7.8 ms |
| Typing | 2.4–2.6 ms | 4.6–4.8 ms | 4.7–4.9 ms |
| Sending, closing and the arrival | 1.6–1.7 ms | 3.6–4.0 ms | 6.5–7.1 ms |
| Cancelling | 2.5 ms | 4.1–6.2 ms | 6.5–17.8 ms |

A median frame uses about an eighth of a 60 Hz frame (16.7 ms), and every worst frame fits in half of
one, except a single 17.8 ms frame in one cancel. The specimen’s own drawing (`invite pill` and the
avatars) is 0.04–0.2 ms at the median.

Two changes made most of the difference, both found with this benchmark:

- **Blurs clip to their own layer.** A graphics layer with a blur and no clip made software Skia
  blur an offscreen the size of the whole window. Clipping each blurred layer to its bounds, with
  room left around its content for the spread, cut opening frames from 8.3 ms to 2.6 ms at the
  median and cancelling from 12.7 ms to 2.9 ms. Turning the blurs off entirely would save only a
  few tenths of a millisecond more.
- **The shadow is a nine-patch.** Seven translucent round rects cost software Skia about 6 ns a
  pixel each, every frame. The shadow is now drawn once into a bitmap and copied onto whole pixels:
  its two round ends as they are, its middle column stretched without filtering. That took another
  20–45% off every scenario.

### Frame pacing in a window

The specimen ran in a real window under Xvfb with tracing on.
[xdotool](https://github.com/jordansissel/xdotool) clicked Invite, typed an address a key at a
time and clicked Send three times, did it twice more from the keyboard, then opened and cancelled.
The gaps between `frame` sections give the pacing, counted only while something moves: from each
`open`, `send`, `arrive` or `cancel` until its animations end. Holds where nothing moves, such as
the blue “Sent” beat, draw no frames and are left out.

| Skia backend | Runs | Intervals in one 60 Hz frame (13–20 ms) | Median | p99 | Worst |
|---|---|---|---|---|---|
| `SOFTWARE_FAST` | 5 | 97.8–99.6% | 15.3–15.4 ms | 16.4–17.4 ms | 20–43 ms, once 126 ms |
| `SOFTWARE_COMPAT` (what CI records with) | 3 | 72.2–88.7% | 15.5–15.8 ms | 26–31 ms | 33–94 ms |

The 126 ms gap came once, on the first open from the keyboard in one run, with no frame in between.
It did not come back in the next two runs, recorded with Java Flight Recorder, whose longest garbage
collection pause was 10 ms.

Squeeze progress, playing without pause, measured 98.5% and 79.5% on the same VM.
`SOFTWARE_COMPAT` copies every frame into the window through Java2D: a Java Flight Recorder profile
of a run like the ones above put 86% of the event thread in that copy (`OpaqueCopyAnyToArgb`,
`ComponentColorModel.getRGB`) and under 1% in this specimen’s code. That is a cost of Skiko and
the JDK, as [docs/TRACING.md](../docs/TRACING.md#things-that-fool-you) describes. A GPU window does
neither.

### Warm-up

Cold, the first open took 13–24 ms in each of its first two frames, and the first arrival one slow
frame. The time was in Compose’s own code running for the first time (the field enabling, the
buttons swapping their click handling, the first blur layers), not in the specimen’s drawing,
which took about a millisecond. Warming the drawing and the springs on their own did not help.

So at startup the specimen rehearses: it plays a whole invite and a cancel once in an offscreen
`ImageComposeScene`, a frame at a time on the UI thread with gaps between (`Rehearsal.kt`, about
four seconds, nothing visible). The JIT then has all of it compiled, and the first real open’s
first two frames take 7–15 ms, inside a 60 Hz frame. The rehearsal must stay on the UI thread: on a thread of its own it raced the
window’s first frames, which then drew some layers without their parameters until the pointer
moved.

## Limits

- The video’s text is SF Pro. This uses Jewel’s default font.
- Interacting in the first four seconds after launch shares the UI thread with the rehearsal.
- The new guest’s letter is the first letter or digit of the address. The video does not show what
  happens with a second invite or a full stack: the lavender for earlier invites and the guest
  leaving from the back are this specimen’s.
- Long addresses scroll inside the field rather than growing it.
