# The audit

Adapted from the original's `references/kit.md` section 12, the audit parts of `references/craft.md`
section 8 and the "Audit" section of `references/verify.md` (MIT). The code is
`dev.sebastiano.anatomy.kit.audit`, a port of the original's `kit/audit.mjs` (in
`anatomy/src/jvmMain/kotlin/dev/sebastiano/anatomy/kit/audit/`). `AuditParityTest` holds it to the
original's own numbers.

A detailed figure fails in ways the eye misses until a reviewer zooms in: a pipe or cable that
passes through something, at rest or in any frame of an explode; a flange that floats off its
mount; a part that hangs in the air or is sunk into its neighbour; a route that doubles back or
pinches to a point on screen; a bend or end that lands on another line's outline; a cable end that
dangles when the figure comes apart; and two things drawn in the wrong order where they overlap.
The audit measures all of them from the real geometry. Use it for any figure with pipes, cables,
round parts or an exploded view, and for any figure past a few dozen overlapping parts, where
hand-set painter's biases start to fight each other.

## Record

Record a shape for everything you draw, in world units, as you build it, into one `Recorder`. Make
the figure's helpers do it, so nothing escapes.

```kotlin
val R = Recorder(P, order = STAGGER, spring = SPRING)   // order and spring only for an explode

// A static part: its art, its true shape, its reference point for the starting key.
val deck = R.solid(slab(plan, 0.0, 4.0, ShapeMeta(name = "stand.deck")))
R.put(art { solid(slabOf(plan, 0.0, 4.0, P), tone = Tone.Lo) }, listOf(deck), at = centre, name = "deck")

// A turned part, recorded along the same curve the drawing uses.
val bell = R.solid(solid(F, profile, meta = ShapeMeta(name = "nozzle.bell", curve = profile)))
R.put(art { solid(solidOf(profile, F, P)) }, listOf(bell), at = mid, name = "nozzle.bell", piece = "nozzle", move = NOZZLE)

// A pipe: the route for the clearance, end and screen checks, and one item per drawn chunk.
R.route("feed", line, 1.5, RouteOptions(gaps = listOf(10.0 to 12.0), piece = "plumbing", move = OUT))
chunks.forEachIndexed { index, chunk ->
    R.put(chunk.art, listOf(tube(chunk.points, 1.5)), at = chunk.mid, name = "feed:$index", route = "feed", chunk = index, piece = "plumbing", move = OUT)
}
```

- `R.put(art, shapes, at, bias, key, name, piece, move, ride, route, chunk)` adds a drawn item. Its
  key is `dot3(at, V) + bias` unless you pass `key`. Its shapes are stamped with its `move`, `ride`
  and `piece`, so every check can pose them in any explode state. `route` and `chunk` mark the
  chunks of one pipe; neighbouring chunks aren't measured against each other (`sameRun`).
- `R.solid(shape)` records a shape and returns it: for an item's `shapes`, or for a static part
  drawn outside the sorted layer (a stand drawn in the back layer is still checked).
- `R.route(name, points, radius, RouteOptions(…))` records a pipe or cable: one tube per drawn run
  between `gaps`, with its `tips`, `tipAxes`, `tipRims` (`rims`, the flange or connector radius at
  each end) and `free` ends, plus the route for `folds` and `crowding`. `radius` may be a function
  of the share 0..1 of the length. `owner` groups the pipe with its own flanges, bands and valve;
  `touch` names what it may touch on purpose; `bundle` lets cables of one harness lie side by side;
  `limp` marks a cable, which holds nothing up; `closed` a loop with no ends.
- `R.nameFor(base)` numbers repeated names (`base`, `base#2`, …).

**Shapes** (`Shapes.kt`), each with a `ShapeMeta`:

| Constructor | Shape |
|---|---|
| `solid(F, profile, radius)` | A turned part: the profile closed to the axis at both ends, the list you give `solidOf`. Smooth runs are traced along the same Hermite curve the drawing uses (or along `radius(s)`), so the recorded surface is the drawn one. |
| `lathe(F, meridian, radius)` | The shape of `lathe(meridian, F, P)`, traced the same way: rings, valve bodies, cans. |
| `body(F, polygon)` | Any closed `(s, r)` polygon turned round `F`, as it is. Edges on the axis are not surface. |
| `disc(F, s0, s1, r)`, `ring(F, s0, s1, rIn, rOut)` | Flanges, bosses, pins, collars. |
| `box(min, max)`, `orientedBox(centre, axes, half)` | Square blocks; a beam at 45°. |
| `slab(plan, z, height, meta, steps)`, `extruded(ring, z, height)`, `cylinder(cx, cy, r, z, height)` | The shapes of `slabOf`, `extrude` and `cylinder`, concave rings included. |
| `prism(F, polygon, s0, s1)` | The shape of `prismOf`. |
| `traced(meridian, radius, error, depth)` | The polygon `solid` and `lathe` record. |
| `ball(at, r, meta, flats)` | Spheres and terminal dots. `BallFlat(n, d)` keeps the half-space `n·(p − c) ≤ d`. |
| `tube(points, radius)` | A pipe centreline: one radius, a radius per point, or a function of 0..1. |
| `slide(F, from, to, r)` | A rod sliding out of a gland at `from`. Posed, it stretches from the gland face instead of translating. Put it on the moving item. |
| `moved(shape, offset)`, `sdf(shape, p)`, `boundsOf(shape)` | Translate, signed distance (negative inside), world bounds. |

`ShapeMeta` carries `name` (in reports, matched by prefix with `"nozzle.bell*"`), `owner`, `touch`,
`bundle`, `joined`, `limp`, `anchor = false` (a clamp: an end may not count it as its mount),
`fitting` (a clamp or collar whose centre `crowding` checks), `seated` (a pad sunk into its host by
design), `cut` (the shapes subtracted from this one: a saddle pad is a disc cut by its host),
`curve` (the smoothed profile of a turned part with no analytic slope, for `wobble`) and `ground`
(stands on the ground, as a name starting `stand.` does).

## Settle and paint

`R.settle()` measures true depth wherever two items overlap, in every state the figure rests in and
every frame of its real explode, and writes each item's painter's key. Run it on every build of the
art (once, when the figure's static art is built), then paint in the settled order:

```kotlin
R.settle()
val drawn = R.paintedArt()                 // every item's art, back to front, as one Art
// in the canvas: drawArt(drawn)
```

For an exploded figure the order changes as parts move: each item's key in a state is
`key + lift · share` (`keyAt`), `lift` being its move toward the viewer (`R.liftOf(item)`). Keep the
items, and each frame sort them by that key and draw each one's art at its offset:

```kotlin
// per frame, in the canvas, with the springs' shares (one per assembly):
val state = Staged("frame", shares)
for (item in R.painted(state)) drawArt(item.art!!, axisVector(P, offsetOf(item, state)))
```

Re-sort only when the order really changes; most frames change nothing. The settled keys are found
by `overlapsOf` and `orderKeys`:

- **`overlapsOf(items, P, step = 0.25, tolerance = 0.3, least = 0.25, states, skip)`** finds every pair
  whose screen footprints overlap in each state and, on a grid of `step` viewBox units over the
  overlap, ray-casts both shapes. A cell counts when one surface is more than `tolerance` nearer.
  Each side's flagged cells are closed, eroded once and their largest piece measured in px²; a side
  is in front when that is at least `least`. Each item is ray-cast once at home and shifted for
  every state, and pairs that move together are measured once.
- **`orderKeys(items, overlaps, gap = 0.02)`** turns every one-sided pair into a rule
  `keyFront − keyBack ≥ gap + shareBack · liftBack − shareFront · liftFront`, linear in the keys,
  so one relaxation settles every state at once. A loop that can't be met drops its weakest rule
  (`dropped`). Pairs in front of each other in different places are `cycles`: an interlock, which
  needs a split, a gap or a tangent contact, never a bias.

**States.** An explode state is `Uniform(share)` (every assembly at that share) or
`Staged(label, shares)`, one share per assembly. `explodeStates(order, spring)` gives the solver's
`depth` states (uniform shares 0, 0.1 … 1; every fourth frame of the page's own springs replayed with
their stagger, opening, closing and reversed 8 ms either side of every stagger boundary; and the
prefix states) and the clearance `frames` (every replayed frame plus the prefix states).
`springStates`, `uniformStates`, `reversalsOf` and `prefixStates` are the pieces; `SpringSpec`
holds the stagger and spring constants. Keep them in one place with the live figure's springs, so
the audit replays what the viewer sees. `shareOf` and `offsetOf` read an item's share (by its
`ride`, else its `piece`) and its offset.

## Audit

`audit(R, P, AuditOptions(allow, crowd, cover, ground, frames, apart))` runs every check on the
recorded shapes as one `AuditResult`; `report(result)` formats it and `failures(result)` counts what
fails. `auditReport(R, P, options)` does all three, with the original's summary line and verdict, and
returns an `AuditRun(result, text, failures)`.

To audit a figure from the capture CLI, give the figure an object that implements `AuditedFigure`
(`record()` builds the figure's recorder at the projection it draws with, and settles it;
`auditOptions` holds what it excuses) and list it in `AUDITED_FIGURES` (`AuditedFigures.kt`). Then:

```sh
./gradlew :anatomy:capture --args="<figure> --audit --out shots/figure.png"
```

It prints the report, writes it to `shots/figure-audit.txt` (read it there: `build-brief` and other
wrappers hide a task's output) and exits 1 on any failure. A test can call `auditReport` directly.

```
clearance: 0 problems
clearance apart: 0 problems
terminals: 0 ends not on a mount
loose ends: 0
screen folds: 0
support: 0 unsupported solids · 0 hanging tubes · 0 sunk pairs
depth order: 0 problems
through in mid-flight: 0 pairs
self-overlap: 0 chunks whose outline crosses itself on screen
wobble: 0 smoothed profiles whose slope turns between its ends
saddles off their host apart: 0
stand-in shapes: 0 items whose recorded shape covers too little of what it draws
crowding: 0 pairs where a bend or end touches another line on screen
audit · 2 tubes · 10 solids · 17 items · 1 depth states · 18 overlaps · 18 order rules · 0.0 s
audit passed
```

Every line counts, including `items with no shape`, except two lists that are there to be read: `in
passing` (interlocks in the uniform states only, which the page never shows) and `free ends apart`.
On the original's Raptor (478 items, 1,119 depth states) the keys take about a minute and a full
audit about three; the port keeps the same grids and sampling steps.

| Line | Meaning | Fix |
|---|---|---|
| `through A × B  clearance -2.4` | Tube A intersects B over that arc-length span (`clearances`). | Reroute A round B with about 1.5 radii to spare, or move B. Only the shape A's end rim sits on is excused, and only within a radius or so of that end. |
| `close A × B  clearance 0.2 < 0.6` | A passes B with less than half its radius to spare. | Nudge the route; widen harness lane spacing. |
| `clearance apart: through share 1 …` | Apart, a tube passes through something. | Change that assembly's explode vector. |
| `terminals: A end 1.40 from B (rim -1.1…1.3)` | A's end, or the rim of its flange, doesn't sit on its mount (`mountsOf`, `terminals`). | End the route exactly on the surface along its normal; make the boss at least as wide as the flange; on a curved body seat the flange on a saddle pad. A cable never ends on a clamp. |
| `loose ends: share 1 A end touches nothing that moves with it` | Apart, the end hangs in the air (`looseEnds`). | Give it a mount that moves with it, or mark it free (`RouteOptions(free = …)`) if it is meant to come off. |
| `screen folds: A turns 150° on screen for 78° in 3D` | The route doubles back on screen and reads as a cusp (`folds`). | Route the leg before the turn sideways on screen. |
| `screen folds: A turns 110° on screen round a 0.4 px bend` | A visible bend pinches to a point. | Turn inside a fitting (a ball elbow), bend in a plane that faces the viewer, or move the port. |
| `self-overlap: A:3 its outline folds back on screen` | One chunk turns on a screen radius under its own half-width (`selfOverlaps`). | Leave the port through an elbow ball, give the turn more straight leg, or bend in a plane that faces the viewer. |
| `support: loose A` / `hanging A` / `sunk A × B 1.2 deep` | A solid, or a rigid tube, touches nothing that leads to the ground, or is buried in B (`supports`). | Add the bracket, stud or plate that holds it; for a sunk part fix the contact (rim on surface, or a pad cut by its host and marked `seated`). |
| `order  A is in front of B but drawn first` | A's surface is nearer where they overlap but B paints over it (`depthOrder`). | `settle` normally fixes these; one left over means a rule was dropped from a loop: find the interlocked pair. |
| `cycle  A … (n the other way)` | A and B are each in front of the other somewhere. | Change the geometry: gap the pipe at its collar, split the band at the ring, end the rod on the ball, keep a pad off its host's silhouette. |
| `through in mid-flight: apart 0.233 s A × B` | In that replayed frame A passes through B, a tube or a solid (`solidClearances`). | Order the stagger so nothing has to pass through: the part outside leaves first; threaded parts share a spring. |
| `wobble: A dr/ds turns at s = …` | A profile smoothed from typed knots has a slope that reverses, so its tone bands staircase (`wobble`). | Give it an analytic `r(s)` and `Smooth(slope = …)`. |
| `saddles off their host apart: A (piece) is cut to B` | A saddle pad flies off with another assembly, showing its cut base (`seats`). | Give the pad to the host's assembly. |
| `stand-in shapes: A its shapes cover 4% of its drawing` | The shapes recorded for A cover far less of the screen than its art (`coverage`). | Record the true shape of what you draw (`slide` for a rod out of a gland). Excuse it (`cover`) only when the drawing really reaches past the body. |
| `crowding: A bend × B outlines overlap 0.05 px` | A's bend or end lands on B's outline on screen and reads as a joint (`crowding`). | Reroute so the meeting is a clear crossing of two straights, or leave a visible gap. Excuse a pair (`crowd`) only after looking at it at 8×. |
| `crowding: A end × B (rod)` | A lands on a slender solid (`slenderOf`). | Approach across the rod, or turn the elbow so the last leg lies across the screen. |
| `crowding: C fitting × B` | A clamp lands within 2 px of another line. | Change the explode vector or the clamp's angle. |
| `items with no shape` | Something is drawn without a recorded shape, so nothing checks it. | Record it in the helper that draws it. |

A zero audit is necessary, not sufficient: it can't see a seam, a staircase or a texture that
stops, and it only checks what you recorded. Run the line check (`--lines`), then look.

## Clearance and crossings

A pipe keeps about 1.5 radii between its centreline and anything but its own connection. Where two
lines cross on screen they cross in depth, one well in front of the other, on straights, and the
chunk keys order them; where a pipe passes through a collar, clamp or valve it is gapped there.

1. Record every item and every tube as you build.
2. Settle the keys on every build: assembled, apart, and in every frame of the real explode.
3. Audit until every failing line prints zero. An interlocked pair is a geometry bug; don't bias it
   away.
4. Lines don't double back on screen: route the leg before a drop sideways on screen (`folds`).
5. No outline folds back on itself and no bend pinches to a point (`folds`, `selfOverlaps`): turn
   inside a fitting, bend in a plane that faces the viewer, or move the port.
6. Crossings happen on straights, clearly: a bend, end or clamp that lands on another line's outline
   reads as a joint, whatever the true depth (`crowding`).

## How it differs from the original

- `R.put` takes the item's `Art` instead of an SVG string, and `coverage` measures what an item
  draws from its art (`artBox`: every path, dot and line through group clips, without halos and
  text) instead of parsing markup (`markupBox`). The art isn't rounded to 0.1 as the original's
  markup is, so its boxes differ from the original's by up to 0.05.
- The original's `meta` object is `ShapeMeta`; its `cut` is always a list. A route's options are
  `RouteOptions`; the audit's are `AuditOptions`.
- `auditOrExit` is `auditReport` (it returns the text and the count) and `capture --audit` (it
  prints, writes the `.txt` and exits non-zero).
- After `settle` the items keep the order they were put in; `painted()`, `painted(state)` and
  `paintedArt()` give them in painter's order, where the original sorts `R.items` itself.
