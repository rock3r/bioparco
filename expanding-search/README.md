# Expanding search

This Compose Desktop specimen recreates sucodee ([@sucodeee](https://x.com/sucodeee))’s
[expanding search](https://x.com/sucodeee/status/2106690154429841524). A small round button with
a magnifier sits on an off-white page. Click it and it springs open, from its middle, into a wide
pill with a text field. A white panel unfolds underneath it, with four suggestions sharpening out
of a blur one after another. A grey highlight slides from row to row under the pointer. Type and
the list filters as you go: the letters that match turn bold and the rest of each name greys out.
The cross clears the text, and a second click folds the panel up and shrinks the pill back into
the button.

Click the button, or press `/`. In the field, the arrow keys move the highlight, Enter picks the
highlighted row, and Escape works like the cross. A click on the page closes the field.

Run the specimen on its own:

```bash
./gradlew :expanding-search:run
```

Or open it from the [showcase](../README.md).

## Recording

![Expanding search](https://static.sebastiano.dev/stable/bioparco/expanding-search.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/expanding-search.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

### Searching

`searchComponents` (`ComponentSearch.kt`) matches names that contain the query, ignoring case and
surrounding spaces. A name matches where the query first appears in it, and earlier matches rank
higher. Ties keep catalogue order and the list stops at five. The video uses exactly this order:
"to" lists Toast (match at 0), Hold to Confirm (at 5), then Action Button (at 10), and "t" lists
Toast, Tab Bar, Action Button, Quantity Stepper and Invite Field. An empty field shows the
video's four suggestions. The rest of the catalogue is chosen so these two queries list what the
video lists. `ComponentSearchTest` covers all of it.

### Rows

A row only keeps its place if it keeps its slot. A row that moves fades out where it was and fades
back in where it goes, starting a fifth of a row towards its old place so it still reads as a
move. The first version slid rows to their new slots. When the field was cleared, Toast slid down
as Action Button slid up through it, and the two names overlapped halfway. The video never does
that. `planRows` (`RowStep.kt`) works out which rows stay, leave, enter or come back, and
`RowPlanTest` covers it, including that case.

Hover and clicks are worked out from the pointer's height over the panel (`RowSlots.kt`), not per
row, so rows sliding under a still pointer still update the hover. The highlight goes to the
hovered row, or to the keyboard's row when the pointer leaves, as in the video.

### The motion

| What | How | Why |
|---|---|---|
| Pill opening | spring, damping 0.95, stiffness 500 | Fitted to the pill's width frame by frame in the 60 fps video: two thirds open at 100 ms, settled by about 250 ms, no visible overshoot |
| Panel opening | 60 ms later, out of a 4dp bar, spring 0.75/325 | Fitted to the panel's height in the video, to within 2dp: it overshoots by about 4% at 300 ms and settles back |
| Rows appearing | from 85 ms, 30 ms apart, each a 160 ms fade out of a blur | Read off the darkest pixel of each row's text in the video, frame by frame |
| Highlight | spring 0.82/800 | Slides quickly between rows with a slight settle |
| Rows changing | 160 ms in, 110 ms out | Fast enough to keep up with typing |
| Panel closing | 180 ms tween that starts fast and eases out | The video's panel is half folded after 33 ms and nearly gone by 100 ms. A spring starts from rest, so it cannot do that |
| Pill closing | spring 1/700, once the panel is 70% folded | The video's pill starts to shrink about 100 ms after the click, then matches this spring to within a few percent a frame |

The pill's opening and the whole closing were checked against frames rendered at exact 60 Hz steps
in an `ImageComposeScene`, not against the recording: a 30 fps screen capture does not time frames
closely enough.

The pill grows from its middle. The magnifier stays half the pill's height from its left end, so
it sits in the middle of the round button and moves left as the pill widens. The text field is
as wide as the pill leaves room for, so the placeholder is uncovered as the pill grows ("Search
c…") rather than squeezed. The cross fades in a little after the text.

Blur only takes whole multiples of 4 px, as in the dot-matrix recorder: each new radius makes
Skia's GPU backend compile a program. Each row's blurred layer is only as wide as its text, plus
room for the blur to spread.

Everything that moves is read while drawing or placing, so a frame of motion redraws without
recomposing. Composition only runs when the field opens or closes, on a keystroke, and when the
row list changes.

### The look

Sizes are measured off the video, where the pill is 146 px tall: 54dp here. The pill is a grey
well inside a thick white rim. The panel is as wide as the pill and follows its width while it
grows and shrinks. Both have a one-pixel edge and a long, soft shadow. Colours are sampled from
the video.

## Performance

Tracing sections cover the drawing (`search chrome`, `result rows`), the search, the row changes
(`show rows`) and each input (`open`, `close`, `hover`, `step`). See
[docs/TRACING.md](../docs/TRACING.md). With tracing on, a frame drawn while anything is still
animating also gets a `moving` mark. Such a frame owes another within one frame, so the intervals
after `moving` frames measure frame pacing without counting idle time.

### Frame cost

[`FrameBudgetBenchmark`](src/jvmTest/kotlin/dev/sebastiano/expandingsearch/FrameBudgetBenchmark.kt)
renders the whole app in an `ImageComposeScene` at the recording's 720 × 560. That covers
composition, layout, drawing and Skia's rasterising, all on the CPU. It renders 60 Hz frames
through the recording's run: open, hover down the rows, type "t" then "to", clear, find nothing,
clear, close.

```bash
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :expanding-search:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/expanding-search-benchmark
```

Measured on a 4-core cloud VM, over two runs:

| Scenario | Median | p95 | Worst |
|---|---|---|---|
| Opening | 2.2–2.5 ms | 3.9–4.8 ms | 5.3–6.0 ms |
| Hovering | 3.0–3.2 ms | 3.8–4.8 ms | 4.0–5.0 ms |
| Typing | 2.7–2.8 ms | 5.8–5.9 ms | 6.9–7.1 ms |
| Clearing | 2.6–3.0 ms | 5.1–6.7 ms | 6.1–8.0 ms |
| No match | 2.1–2.7 ms | 4.4–5.9 ms | 4.7–6.9 ms |
| Closing | 1.5–1.6 ms | 3.6–3.7 ms | 5.6–5.7 ms |

The worst whole software frame uses under half of a 60 Hz frame (16.7 ms). The specimen's own
drawing (`search chrome`) records in 0.02–0.05 ms at the median.

### Frame pacing in a window

The specimen ran in a real window under Xvfb with tracing on, twice per backend.
[xdotool](https://github.com/jordansissel/xdotool) drove four rounds of: open, hover three rows,
type "to", two arrow keys, Escape, type "in", Enter, Escape twice. The table counts every interval
after a `moving` frame, leaving out the first three seconds.

| Skia backend | Intervals in one 60 Hz frame (up to 20 ms) | Median | p99 | Worst |
|---|---|---|---|---|
| `SOFTWARE_FAST` | 99.1–99.5% | 15.2–15.3 ms | 17.5–18.8 ms | 60–63 ms |
| `SOFTWARE_COMPAT` (what CI records with) | 83.1–84.8% | 15.5–15.7 ms | 28–29 ms | 43–67 ms |

The one or two slow intervals per `SOFTWARE_FAST` run have frames of about a millisecond on both
sides. The event thread was held up by something outside the specimen. `SOFTWARE_COMPAT` also
copies every frame into the window through Java2D, which is where its extra late frames come from,
as [docs/TRACING.md](../docs/TRACING.md#things-that-fool-you) describes. Over six runs with the
final drawing it ranged from 83% to 88%. Squeeze progress, which draws less each frame, measures
87.6–89.5% on the same backend. The late `SOFTWARE_COMPAT` frames gather where seven rows fade
at once, when Escape clears "to".

### What the traces changed

- **Shadows.** The first shadows were a round rect drawn through a blur mask filter. In software
  that painted the whole shadow, including the inside hidden under the surface: about 2 ms of a
  3 ms frame. Under `SOFTWARE_COMPAT`, 81–82% of intervals were on time, against 86–90% with no
  shadows at all. `SoftShadow` now paints only the ring where the shadow fades: four gradient
  ramps along the edges and four quarter rings at the corners, following the blur's Gaussian
  falloff. Gradients are built once and placed with canvas transforms, so drawing allocates
  nothing. Rendered frames differ from the blurred version by a median of 5 levels out of 255 in
  the most different channel. `SOFTWARE_COMPAT` went to 83–88%.
- **Row layers.** Each row's blurred layer used to be the panel's full width. Shrinking it to the
  text brought the worst clearing frame (seven rows fading at once) from 12.3 ms to 6–8 ms in the
  benchmark.
- **Warm-up.** Cold, the first open spent 7.5 ms working out its rows and drew a 46 ms frame, and
  the first keystroke drew two of 22–23 ms. The first open composed the rows for the first time
  and focused the text field for the first time, which sets up text input. The first keystroke
  edited the field for the first time and loaded the bold font for the matches. 400 ms after
  launch the specimen now does each of these once, out of sight. The field takes focus, gets a
  "t" and loses it again while it is closed and zero wide. The rows for "t" go through the real
  row changes: they enter, fade in and leave. Closed, the panel is a 4dp bar and every row starts
  5dp down it, so the panel's clip hides them; 90 frames rendered through the warm-up are
  identical, pixel for pixel. On the first open, working out the rows now takes 0.3–0.4 ms and
  its frames 9–13 ms. The first keystroke's frames take 8–14 ms.

## Limits

- The video's text is SF Pro. This uses Jewel's default font.
- The video only shows the field opening, filtering and closing, so picking a result here just puts
  its name in the field.
- The catalogue is fixed: twenty component names, picked to match the video's results.
