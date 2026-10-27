# Holo card

This Compose Desktop specimen recreates [Ann Nguyen](https://x.com/ann_nnng)’s
[holo 3D card effect](https://x.com/ann_nnng/status/2108122086569136417). A black trading card
lies on a white table. It tilts towards the pointer in 3D, and its puffy foil mascot shifts
colour as it turns. Drag the card to turn it further. It lifts off the table and wobbles back
when you let go. The dock below holds three cards: pick one and it drops in from above while the
old one sinks away. The play button starts a hands-off demo that sweeps the card around.

The keyboard works too: Left and Right switch cards, and Space starts or stops the demo.

Run the specimen on its own:

```bash
./gradlew :holo-card:run
```

Or open it from the [showcase](../README.md).

## Recording

![Holo card](https://static.sebastiano.dev/stable/bioparco/holo-card.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/holo-card.mp4)

Spectre captures this recording. Regenerate it with `./gradlew :recordings:recordSpecimens`. See
[docs/RECORDING.md](../docs/RECORDING.md).

## How it works

The card is an ordinary Compose layout: text, the mascot and the name. A `graphicsLayer` turns it
in perspective. Hovering points it at the pointer so that the side under the pointer recedes, up
to 14°. A drag adds to that, up to 34°. The demo follows a slow Lissajous curve. All three set a
target that two slightly underdamped springs follow, one per axis, so the card swings a little
past where it is asked to go. The springs sleep once the card settles, so a resting card draws no
frames.

Each mascot starts as a signed distance field: circles and rounded boxes blended with a smooth
minimum. When the specimen starts, a background thread inflates each silhouette like a balloon.
It solves ∇²u = −1 inside the shape and uses 2√u as the height. A disc becomes a hemisphere, and
other shapes become smooth pillows with no creases at corners or along thin legs. The height is
flattened, then baked into a normal map.

A small SkSL program turns that normal map into foil on every frame. The body drifts through the
friend's hue as the card turns. The rim runs through the rainbow. A soft glow inside the jelly
slides against the tilt, and a highlight follows a light at the top left. The name is drawn with
a rainbow gradient that slides with the tilt, and a faint glare crosses the black. The mascot
floats a few dp above the card and slides a little against the tilt.

The shadow is one blurred image made once per size. Each frame draws it on whole pixels with
nearest sampling, narrowed as the card turns and pushed further out while it is lifted.

[PERFORMANCE.md](PERFORMANCE.md) explains how the card keeps to 60 Hz in software rendering.

## Limits

- The mascots are this specimen's own shapes, not the original's characters. They are flat
  silhouettes blown up into pillows, so they have no sculpted detail and no extruded side wall
  when the card turns edge-on.
- The card has no back, and it never turns far enough to show one.
- The foil is a stylised thin film, tuned by eye against the video, not a physical model.
- In software rendering at density 2, a moving frame costs about 25 ms of CPU, so a HiDPI window
  without a GPU backend runs at about 40 frames a second. HiDPI Macs use Metal instead.
- With Skiko's `SOFTWARE_COMPAT` renderer, the one CI recordings use, Java2D presentation limits
  the window to about 35 frames a second. See [PERFORMANCE.md](PERFORMANCE.md).
