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
./gradlew :anatomy:capture --args="dial-indicator --lines --out shots/lines.png"        # line ends, ringed
./gradlew :anatomy:capture --args="<figure> --audit --out shots/figure.png"             # geometry audit
./gradlew :anatomy:capture --args="turning-dial --turn dial=37,lift=1 --out shots/t.png" # hold a turning figure
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
| `--lines` | | Check line ends on each shot (see "Line ends"): print the report, ring loose ends in red on the PNG, write the report to a `.txt` beside it, and exit 1 if any end floats. |
| `--audit` | | Run the geometry audit on a figure that records itself (see "Audit"): print the report, write it to `<name>-audit.txt` beside the PNG, and exit 1 on any failure. A turning figure runs the orbit audit after it. |
| `--quick`, `--verbose` | | With `--audit` on a turning figure: the orbit audit at about a tenth of the states (for iteration; it never ends `passed`), and its progress check by check. |
| `--turn name=value,…` | | Hold a turning figure (`references/3d.md`) at these values: its groups, and figure values such as the dial's `lift` or the pod's time `pod`. The figure stops its tour and shows them (`TurnHook`, the original's `__isoTurn.set`). Combine with `--zoom`, `--lines` and both themes. |

Read every PNG you make: open it with `view_media` and look. Then run the figure for real with
`./gradlew :anatomy:run` when you can, since the pointer feel only shows live.

## Line ends

A screenshot only shows a line stopping in the middle of a face if you look in the right place.
`--lines` checks the drawing itself: every mark the figure painted in the frame being shot, per-frame
parts and moved groups included, as the original's `scripts/lines.mjs` does in headless Chrome.

```sh
./gradlew :anatomy:capture --args="token-bucket --lines --out shots/lines.png"
./gradlew :anatomy:capture --args="token-bucket --lines --times 0,1.5,3 --out shots/lines.png"   # poses
./gradlew :anatomy:capture --args="token-bucket --lines --dark --out shots/lines-dark.png"
```
```
line ends: 3 of 208 ends on 52 lines float
  line faint  start  3.62 from anything   at card 227.6,222.6   view 203.0,170.0   length 64.7   d M203 170L264 191.4…
```

Every open run of every line in the card (seams, grooves, ribs from `faded`, rule lines, ticks,
needles, pointers, wires) must end within 1 viewBox unit of another stroke, a dot or an outline, or
under a fill painted after it. A short straight mark (up to 7 units: a tick) needs one end rooted
within 2 units; both ends floating fails. A solid's own outline, crease and bevel aren't checked
(lines land on them), and neither are lines fainter than 0.3, the ramps of a `fadeLine`, or a `free`
end. `line(path, free = Free.Start)` (or `End`, `Both`; the per-frame `line` takes it too) marks an
end that is meant to stop: rule lines that stand for printed text, a needle's tail, a pointer's tip.
Use it for nothing else.

Each loose end is printed `at card x,y` in dp, ready for `--zoom`, and `view x,y` in the figure's
viewBox units, with the start of its path (`IsoPath.svg()`) so you can find it in the source.
`build-brief` and other wrappers hide a task's output, so read the report from the `.txt` written
beside each PNG (`shots/lines.txt`, or `shots/lines-<ms>.txt` with `--times`); a non-zero exit only
says something floats. Run it in both themes and in every pose the figure rests in, and at moments
of its motion, and fix every line until it prints 0. `FigureLineEndsTest` keeps every registered
figure at 0 in its resting pose, so a new figure must pass it too.

| You see | Fix |
|---|---|
| A seam or weld line on a box inset from the corners | `sideSeam(plan, z, P, steps)` as the slab's `inner`: it wraps the corners and ends on the silhouette. |
| A stiffener or panel line on one face, inset at both ends | Run it edge to edge: from the face's bottom to its top, or from end to end. |
| Ticks floating on a bare plate or hanging below an edge | Put them on a ruler strip, rooted on its edge (`topTicks` `edge`, `sideTicks` `top` on the face's top edge). |
| Vent or grille lines | Closed slots: a thin rounded outline, not a line. |
| A line that runs under a part and stops just past it | Draw it before the part, full length, and let the part hide it. |
| The floor of a bore seen through its mouth pokes out past the rim | Keep only the stretch of the floor's circle inside the mouth, so it ends on the mouth's ring. |
| Rule lines on a label plate, a needle's tail | `free`. |

How it differs from the original, because it reads marks instead of a browser's SVG: it is the same
rules with the same tolerances, and on the dial indicator it counts the same 367 ends on 23 lines.
It sees what `IsoScope` paints, not anything a figure draws straight on `draw`; under a group's
`clip` it checks only what the clip lets show, an end the clip cuts is out of sight and isn't checked,
and what the clip hides is nothing to land on; a tube piece's body and stripes hide ends painted
before them and its edges and rings are landings, never checked (`tb-edge` is in the original's skip
list); a fill hides an end when it is painted later and covers the point, without the browser's
hit testing (so a dashed line's end in a gap isn't treated differently); and opacity is the mark's
own (`faded` levels, dot tones and alphas, the halo's opacity), since there are no CSS groups.

## Audit

Screenshots catch what you think to look at. A figure with pipes, cables, round parts or an
exploded view also needs its geometry checked, because a cable through a duct or a part drawn over
the thing in front of it is invisible at 1× and obvious at 4×. Record the figure into a `Recorder`,
settle its painter's keys from the geometry, and audit it (`references/audit.md`). Once the figure's
object implements `AuditedFigure` and is listed in `AUDITED_FIGURES`:

```sh
./gradlew :anatomy:capture --args="<figure> --audit --out shots/figure.png"
```

It prints the report and `audit passed` or `audit failed: n problems`, writes the same text to
`shots/figure-audit.txt` (read it there: `build-brief` hides a task's output), and exits 1 on any
failure. A figure that doesn't record itself exits 2 and says so. Every line must print zero,
assembled, apart and in every replayed frame of an explode; `in passing` and `free ends apart` are
lists to read, not failures. `references/audit.md` has what each line means and how to fix it. A
zero audit is necessary, not sufficient: it only checks what was recorded, so run `--lines` and look
too.

### The orbit audit

A turning figure (`references/3d.md`) implements `TurningFigure` as well: after the kit audit,
`--audit` runs the orbit audit (`orbit` in `kit/turn`), which samples every turn angle, clip frame
and pose for order, clearances, routes, coverage, depth and continuity. It appends its report to
the same `-audit.txt` and ends `turn audit passed` or `turn audit failed: n problems`; any failure
exits 1. Only a full run ends `passed`. It is slow (the turning dial takes about 40 s with
`--quick` and about five minutes in full), so iterate with `--quick --verbose` and run the final full
audit in the background, then read the `.txt`:

```sh
./gradlew :anatomy:capture --args="turning-dial --audit --quick --verbose --out shots/dial.png"
./gradlew :anatomy:capture --args="turning-dial --audit --out shots/dial.png" > audit.log 2>&1 &
```

`references/3d.md`, section 13, has what each line means and how to fix it. Then shoot the figure
with `--turn` at the angles a line names, at the extremes of every clip, and with `--lines`.

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
- [ ] **Line ends zero.** `capture --lines` prints `line ends: 0` in both themes, in every resting
  pose and at moments of the motion (read the `.txt` beside each PNG).
- [ ] No leader lines, balloons, dimension arrows or floating labels; at most one engraved label.
- [ ] Painter's order is right everywhere and in every pose: nothing behind shows through, springs and
  frames are split.
- [ ] **Audit zero.** With pipes, cables, round parts or an explode, `capture --audit` ends `audit
  passed` (read `<name>-audit.txt`): zero on every failing line, assembled, apart and in every
  replayed frame. No interlock was hidden with a key bias, and every excused pair (`allow`,
  `crowd`, `cover`) has been looked at at 8×.
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
