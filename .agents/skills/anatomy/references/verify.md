# Verify

Adapted from the original's `references/verify.md` (MIT). Look at the figure the way a demanding
reviewer would, before anyone else does. Rendering it is cheap, and guessing is expensive.

## Capture

`./gradlew :anatomy:capture` renders one figure in its card offscreen with `ImageComposeScene` and
writes PNGs. It needs no display, so it runs in CI, an orb or over SSH. Paths are relative to the
repository root. Use `build-brief` instead of `./gradlew` if the environment asks for it. Write
review shots somewhere ignored (`.amp/in/artifacts/` or `build/`), never into the module.

```sh
./gradlew :anatomy:capture --args="dial-indicator --out shots/dial.png"                 # light, 650 wide, scale 2
./gradlew :anatomy:capture --args="dial-indicator --dark --out shots/dial-dark.png"     # dark
./gradlew :anatomy:capture --args="dial-indicator --width 390 --out shots/phone.png"    # narrow
./gradlew :anatomy:capture --args="dial-indicator --hover 0.7,0.4 --times 1.5"          # pointer at 70% across
./gradlew :anatomy:capture --args="dial-indicator --times 0,1.2,2.4,3.6,4.8"            # the tour: name-<ms>.png
./gradlew :anatomy:capture --args="dial-indicator --key Right,Right --times 1"          # keys after Tab focuses it
./gradlew :anatomy:capture --args="token-bucket --click 0.5,0.4 --times 0.4"            # a press and release
./gradlew :anatomy:capture --args="dial-indicator --zoom 330,170,140,110"               # 4× close-up of x,y,w,h
./gradlew :anatomy:capture --args="dial-indicator --reduced --times 2"                  # reduced motion
```

| Option | Default | What it does |
|---|---|---|
| `<figure>` | | The figure's `id` from `Figure` in `App.kt`. |
| `--out` | `build/anatomy/<id>[-dark].png` | Where to write. With several `--times`, `name-<ms>.png` beside it. |
| `--light`, `--dark` | light | The theme. Light ships, so design and review it first. |
| `--width` | 650 | The card width in dp. |
| `--scale` | 2 | Pixels per dp. |
| `--times` | 0 | Seconds after the first frame; the scene's clock is simulated, so motion is exact and repeatable. |
| `--hover x,y` | | The pointer, as fractions of the card's width and of the whole capture's height, from the start. |
| `--click x,y` | | A press and release there after the first frame, in the same fractions as `--hover`. |
| `--key` | | Keys sent in order after a Tab focuses the figure: `Right`, `Left`, `Up`, `Down`, `Home`, `End`, `Space`, `Enter`, `Escape`, `Tab`. |
| `--zoom x,y,w,h` | | A region in card dp (from the card's top-left corner), rendered at `--zoom-scale`. |
| `--zoom-scale` | 4 | |
| `--reduced` | | Reduced motion. |

Read every PNG you make: open it with `view_media` and look. Then run the figure for real with
`./gradlew :anatomy:run` when you can, since the pointer feel only shows live.

## Close-ups

The owner finds faults on a close-up of the light theme. Shoot that close-up yourself, everywhere,
before anyone else does.

1. **List the junctions:** every place a part sits on another, every post through a clamp, every
   crossing, every knurl and tick row, every seam and trim line on a box face (does it reach both
   corners?), every row of marks on a plate (is it on a ruler strip?), every moving part at rest and at
   both ends of its travel, and every window with text in it.
2. **Shoot each area at 4× in both themes**, at rest and at the poses that matter (`--times`,
   `--hover`, `--key` to get there).
3. **Look at each PNG for these faults, in this order:** a line that runs through or into something; a
   crossing whose nearer part is drawn behind; a part standing off what it should sit on, or sunk into
   it; a row of dots or rule lines stopping in the middle of a surface; a seam that stops short of the
   edge it should reach; a short mark on a top that touches nothing; text that crosses its window.
   Compare with the previous round's shot of the same region.

**Detail pass.** Last, sweep the subject and mechanism at 4× in a grid of crops and look for any face,
corner or stretch of base with nothing true on it. Add the real parts that belong there (screws,
seams, bevels, label plates, scales, the brackets that hold things), never decoration.

## The review checklist

**It is an object**
- [ ] At a glance it reads as one real, well-made thing, and you can name it.
- [ ] Its mechanism maps to the concept: what moves and what that means can be said in one sentence.
- [ ] It isn't a stack of abstract plates and isn't one plain slab.

**Parts and volume**
- [ ] 30–60 solids for an inline figure. Every part rests on another and has a job.
- [ ] Sides shaded and tops lighter; bevels on the main slabs; a halo under the base and under lifted
  parts.
- [ ] Small details belong to parts: screws at the corners of plates, grooves, rulers, knurls, label
  plates.

**Lines**
- [ ] At 4×, every line ends on a face, another line or a terminal dot. Nothing stops in the air;
  every `free` end is a rule line standing for text, a needle tail or a pointer tip.
- [ ] No leader lines, balloons, dimension arrows or floating labels; at most one engraved label.
- [ ] Painter's order is right everywhere and in every pose: nothing behind shows through, springs and
  frames are split.
- [ ] No wire passes through a solid or another wire; wires run beside the structure, not across the
  subject's face.
- [ ] Every row of dots, rule lines or grooves ends on a real edge or fades out. Seams on boxes wrap
  their corners and ticks sit on a ruler strip.

**Tone and colour**
- [ ] Base `Lo`, structure `Mid`, subject `Hi`, exactly one `lit` accent.
- [ ] Monochrome unless colour is the subject. Every colour comes from the `Palette`.

**Card and words**
- [ ] `Fig n` / title / hint / live readout are in the corners, with legend keys and a caption under
  the card.
- [ ] Every number is true and comes from the model. The readout changes as things move, and its words
  and numbers come from the same instant.
- [ ] Text the machine displays fits its window at its widest string.

**Motion and access**
- [ ] Eased, calm, no snapping; an idle tour; it stops on interaction and resumes after a pause.
- [ ] The loop stops once settled; motion never recomposes (read per-frame state in the canvas only).
- [ ] Still under `--reduced`, and it still answers input.
- [ ] Keyboard works, and the semantics describe the object and its state in words.

**Composition**
- [ ] Centred, filling about 55–65% of the card; it doesn't touch the corner labels.
- [ ] Still readable at 390 wide, and the longest readout doesn't run into the hint.

**The repository**
- [ ] Registered in `Figure` and `FigureContent`, and listed in `anatomy/README.md`.
- [ ] The model's behaviour has tests; animation constants don't.
- [ ] `./gradlew check` passes.

## Common faults and fixes

| You see | Fix |
|---|---|
| A part shows through something in front of it | It is painted too late. Move it earlier, or split the front part. |
| An ellipse shows across a post where it meets a face | A full `ring` drawn over a cylinder that continues shows its back half. Use `sideArc` (front half only), or let the cylinder's own outline make the edge. |
| A post looks pasted onto the block it passes through | Draw it in two segments, below and above, with the block painted between them. |
| A crease or bevel stops short of a corner | Use `bevel` on `slabOf`/`cylinder`. It already ends on the silhouette. Don't hand-draw edge lines. |
| Flat, dead sides | You passed `flat = true`, or the solid has height 0. Give it thickness. |
| Too sparse | Add structure, not texture: feet, rails with rulers and end stops, a label plate, screws, a knob, a second stage. |
| Too busy | Drop texture-like repeats and tone grooves down to `Faint`; keep one lit accent. |
| The figure is tiny in the card | Fit with a smaller `pad`, or tighten the points passed to `fitProjection`. |
| A moving part snaps | It is written from the target. Write it from a `follow` or `spring` state. |
| The whole page recomposes while it moves | Per-frame state is read in composition. Read it only inside the `IsoCanvas` block. |
| The outline is dashed where seams meet it | Seams drawn after the solid. Pass them as `inner`. |
| A seam on a box stops short of its corners | `sideSeam(plan, z, P, steps)` as `inner`. |
| A texture line stops in the middle of a face | End it on a real edge, or draw it with `fadeLine` so it ramps out. |
| LCD or counter digits cross the window | Fit the type to the window's height and widest string, and clip it. |
