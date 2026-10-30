# Tab switch

This Compose Desktop specimen recreates the tab switching that
[Elia](https://x.com/eliakuratli) [shared](https://x.com/eliakuratli/status/2108248320699498919).
A dark bar holds four tabs: Home, Saved, Inbox with a badge, and Profile. The selected tab sits
on a light pill, with its icon solid and its name beside it. Click another tab and the pill
inches across to it: its back end leaves first and its front end follows, so it pinches in on
the way and stretches out on arrival. Where the pill covers an icon, that part of the icon turns
dark and solid; the rest stays a light outline.

Rest the pointer on a tab and its icon lights up, and the pill leans towards it. The left and
right arrow keys step through the tabs, Home and End jump to either end, and 1 to 4 pick a tab.

Run the specimen on its own:

```bash
./gradlew :tab-switch:run
```

Or open it from the [showcase](../README.md).

## Recording

![Tab switch](https://static.sebastiano.dev/stable/bioparco/tab-switch.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/tab-switch.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md). The recording script checks the selected tab after
each input. It rests the pointer on each tab in turn and clicks it, from Home to Profile, then
jumps back to Home. Then the right arrow steps to Saved, a quick double press goes on to Profile,
retargeting the pill on its way, and 1 brings it home.

## How it works

**Two layers.** The bar is drawn twice in one node. Underneath, every icon is a light outline.
On top, clipped to the pill, the pill itself and every icon again, dark and solid, with the
labels and a dark badge. Wherever the pill is, it shows the top layer, so an icon half under the
pill is half solid, and the badge flips from light to dark as the pill passes.

**The tabs.** A closed tab is an icon; an open one is icon, gap and label. The bar keeps one
width: the open tab takes its own width and the closed tabs share the rest equally. Every tab
opens or closes on the same spring, so their widths always add up to the bar's and the tabs
slide over each other, as in the original. The bar is as wide as the widest open tab with three
62 dp slots beside it.

**The pill.** Each end of the pill chases the new tab on its own critically damped spring, so
neither overshoots. The trailing end leaves at once (natural frequency 24 rad/s, 95% of the way
in 0.2 s); the leading end waits 85 ms and follows more softly (18 rad/s). Both were fitted
frame by frame to the original at 60 fps. The pill is never narrower than it is tall: on a long
jump, the trailing end catches up and the pill rolls along as a ball until the leading end
pulls it out again.

**The labels.** The old label fades out in 70 ms. The new one waits until the pill covers 92% of
its tab, which for a neighbour is 0.27 s after the click, as in the original, then fades in over
0.12 s. On a long jump it waits for the pill to arrive.

**The pointer.** A hovered tab's icon brightens, and the pill leans 8 dp towards it.

**The badge.** While the tabs settle, the closed ones drift by fractions of a pixel. Skia placed
the badge's count on whole pixels, so it hopped a pixel against its circle as Inbox came to rest.
The count's style turns on sub-pixel glyph positioning, and the count is not rounded: it now stays
within a quarter of a pixel of its circle's centre, the finest step Skia positions glyphs at.

**The frame.** The motion is plain Kotlin, stepped on the frame clock while anything moves, then
asleep until the next input. Each spring integrates in sub-steps of 1 ms, in doubles: near rest,
a sub-step moves a pill end less than a float can resolve. A step is capped at 1/30 s, so a
stalled frame never jumps the pill to the end. The drawing reads the motion while drawing, so a
moving frame redraws one node and recomposes nothing; the tabs' semantics nodes move once per
switch.

## Performance

The specimen holds 60 Hz. The table gives frame-to-frame intervals while the bar moves, over
three or four runs of the recording script each, traced as in [docs/TRACING.md](../docs/TRACING.md) while
Spectre records. Between inputs the bar is still and asks for no frames, so those gaps are left
out.

| Under Xvfb, 560 × 320 | Median | p95 | p99 | Within 17.5 ms |
|---|---|---|---|---|
| Skia on OpenGL (Mesa) | 15.2 ms | 16.0 to 16.1 ms | 16.8 to 17.7 ms | 98.7% to 99.6% |
| Skia in software, as CI records | 15.1 to 15.2 ms | 16.0 to 16.1 ms | 18.0 to 19.0 ms | 98.1% to 98.5% |

In software, a whole frame of the app, drawn from scratch, costs 0.94 ms at the median and 4 ms
at worst while switching. Drawing the bar costs 0.08 ms of that, and stepping the motion 0.03 ms.

These changes got it there:

- The springs settled to within 0.0001 dp, which took a second, so the frame loop never slept
  between the script's inputs. The pill's ends now snap within 0.01 dp, and the loop sleeps 0.4 to
  0.5 s after each switch. The tabs' openings still settle to 0.0001, since they have to snap
  together to keep filling the bar.
- Cold, the first switch drew a 16 ms frame in software: the other labels' glyphs, the solid icons
  and the dark badge were drawn for the first time. The first two frames now draw them inside the
  resting pill, just before the pill is painted over them, which leaves every pixel as it was, and
  a throwaway bar switches through every tab off the main thread at launch, so the springs are
  compiled. The first switch now costs 10 to 11 ms cold.

Each run still has one long gap, of 60 to 75 ms, at the first click, and most have one of 50 to
100 ms at the first press of a digit key. Neither is the specimen's: the frames on either side
take 2 to 8 ms, and Java Flight Recorder shows the UI thread parked, waiting for events, for the
whole gap. They happen whether or not the script reads the semantics tree, and only on the first
input of each kind, which points at the virtual X server taking Spectre's injected input.

To repeat the measurements:

```bash
# The whole app in software, frame by frame (ImageComposeScene on a raster surface)
BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :tab-switch:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
python3 tracing/tools/trace-summary.py /tmp/traces/tab-switch-benchmark

# A real window on Skia's OpenGL path, under Xvfb with Mesa
BIOPARCO_TRACE_DIR=/tmp/traces xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" \
    ./gradlew :recordings:recordSpecimens --tests '*TabSwitch*'
python3 tracing/tools/trace-summary.py /tmp/traces/tab-switch
```

Leave `CI=true` off for the OpenGL run: it switches the recordings to software.

## Differences from the original

- The original is driven with a pointer. This one adds the keyboard.
- The original's font is its own; this one uses Jewel's default font.
- The original only shows switches between neighbours. A long jump here pinches the pill into a
  ball that rolls across, which the original never shows.
