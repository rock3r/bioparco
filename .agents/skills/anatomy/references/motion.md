# Motion and interaction

Adapted from the original's `references/motion.md` (MIT) for Compose. A figure is alive when its
mechanism moves the way the real thing would: a carriage glides and settles, a spring overshoots
once, a needle swings to the new reading. Motion should be calm enough to follow and true to the
model. `DialIndicatorFigure` is a complete example.

## Principles

- **Time-based.** `rememberFrameLoop` gives `dt` in seconds, clamped to 1/30 s. Never step a fixed
  amount per frame.
- **Ease toward targets.** Pointer and keyboard set a *target*, and the part follows with
  `follow(current, target, dt, τ, calm)` (τ = 0.1–0.3 s) or a `spring`. Parts never snap to the
  cursor.
- **Real springs where the subject is springy.** `spring(state, target, dt, stiffness, damping, calm)`
  sub-steps about 240 times a second and snaps once error and velocity are below 5e-4. Use stiffness
  and damping from the real model.
- **One loop per figure.** One `tick(dt, calm)` updates the model, then writes snapshot state that the
  canvas reads. It returns true while anything still moves; the loop stops when it returns false.
- **Never recompose per frame.** The loop writes `mutableDoubleStateOf` values; read them only inside
  the `IsoCanvas` block, so a frame only redraws. Keep the readout in its own state; `Plate` reads it
  in the readout's text only.
- **Stop when settled or offscreen.** The loop ends when `tick` returns false, and a figure that
  leaves composition (scrolled out of a lazy list) stops with it. Restart it with the `kick` that
  `rememberFrameLoop` returns.
- **Reduced motion.** Desktop has no system query, so a figure takes `reducedMotion`. Then `calm` is
  true: targets are reached at once, springs are critically damped, there is no tour, flow or pulse,
  and the figure still answers input. The model itself keeps running: a bucket still refills and a
  clock still ticks, only the animation between states is skipped.
- **Idle tour.** When nobody is interacting, play a scripted loop that shows the point of the figure,
  as a pure function of time (`tourAt(t)`). Stop it on any input, and resume it about 3.6 s after the
  last one (a `LaunchedEffect` keyed on a touch counter that `delay`s, then kicks the loop).
- **Readout updates live** from the same model, in true units and short: `block 2 · reads 14.0 px`.
  Read it from where the parts are, not from the target. Take a phase word and the number beside it
  from the same instant of the same source.

## Frame loop

```kotlin
val state = remember { object { var x = REST; var target = REST; val tip = Spring(0.0); var touring = true; var clock = 0.0 } }
var carriageX by remember { mutableDoubleStateOf(state.x) }       // what the canvas reads
var readout by remember { mutableStateOf(readoutOf(state.x)) }
val kick = rememberFrameLoop(reducedMotion) { dt, calm ->
    val s = state
    if (s.touring) { s.clock += dt; s.target = tourAt(s.clock) }
    s.x = follow(s.x, s.target, dt, 0.28, calm)
    val settled = spring(s.tip, heightUnder(s.x), dt, 420.0, 26.0, calm)
    carriageX = s.x
    readout = readoutOf(s.x, s.tip.at)
    s.touring || s.x != s.target || !settled                      // keep going?
}
```

## Pointer

- Map the pointer to world space either with the screen vector of an axis (`axisVector(P, Point3(1.0,
  0.0, 0.0))`) or by mapping the figure's width fraction onto the part's travel range. The range
  mapping is simpler and feels better when the travel is short.
- `Modifier.pointerInput` on the canvas: `Move` and `Enter` steer, `Exit` holds the current target.
  Hover previews and press commits.
- While dragging, consume the changes so a scroll container doesn't steal them.

## Keyboard and accessibility

- The canvas is `focusable()` and handles `onKeyEvent`: arrows step by a sensible unit (Shift for
  bigger steps), Home and End go to the extremes, Space or Enter presses or toggles, and Escape
  releases.
- `semantics { contentDescription = LABEL; stateDescription = readout }`. The label says what the
  object is and what it shows. Write the state description when the reader's choice changes, not
  from every frame, if it would otherwise become a stream of numbers.

## Shapes that change

When a part changes shape rather than position (a compressing spring, a needle turning), rebuild only
its paths each frame with the kit and draw them with the scope's `line` or `solid`. One solid costs
well under 0.1 ms.

- **Precomputed frames step.** A lever drawn from 41 prebuilt frames over ±15° moved its knob about
  0.8 px per frame, visible in slow motion. Rebuild a rotating part each frame.
- **A rod that slides out of a gland** is one full-length rod that moves, clipped at the gland face.
  Never draw it as several overlapping copies.

## Discrete events and things that come and go

Requests, coins, tokens and packets are events, not values. Keep them in a plain list on the
figure's state, each with its start time (`at = clock`) and whatever it carries. Each frame, derive
every moving thing's position from its age (`clock - at`) through the same `follow`, `spring` or
`burst` shapes, draw it, and drop it from the list once it has arrived. The model decides what an
event means (passed or refused) at the instant it happens; the animation only shows it. The loop
keeps running while the list is not empty.

## Envelopes for one-shot events

A flash, a click, a pop or a shake is an event with an age, not a value you set. Drive it with
`burst(age, rise, fall)`: a smoothstep attack over `rise`, then an exponential decay.

```kotlin
val flash = if (calm) 0.0 else burst(clock - mainAt, 0.08, 0.13)
```

- The attack is 40–120 ms. Setting a value to 1 in one frame is a pop.
- Store the event's start time and compute every envelope from its age each frame. Two events that
  overlap then simply add.
- Under reduced motion every event envelope is 0.

## Critically damped settle

For a value that should arrive without overshoot (a hue, a brightness, a reach), use
`settle(Settling(x), target, dt, seconds, calm)`. It returns true while still moving.

- **Separate what a phase changes.** When one quantity drives colour, shape and brightness, give
  colour its own slower `settle` (0.3–1 s).
- **A fade-out only gets darker.**

## Exploded views

- Build every piece at its assembled position inside a `group`, and give each assembly a world move
  vector.
- One `Spring` per assembly, 0 assembled and 1 apart. The Raptor uses stiffness 64 and damping 14.2,
  just under critical.
- Stagger the targets by 35 ms, in an order that lets nothing pass through anything; closing reverses
  it. On a reversal, reschedule only the assemblies whose target changes.
- Each group's offset is `translateAlong(P, move * spring.at)`.
- Parts that move toward or away from the viewer change painter's order. Give each group a static key
  `depthOf(centre) + bias` and a lift `depthOf(move)`, and sort the groups by
  `key + lift * spring.at` each frame before drawing them.
