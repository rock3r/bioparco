# grabby-stepper

Compose Multiplatform Desktop recreation of Vishal Paliwal’s grabby pill
stepper: the count is a thumb you can actually grab, and the dark track
stretches toward it like rubber.

## Run

Requires JDK 21+ (the project uses `jvmToolchain(21)`).

```bash
cd /Users/rock3r/src/grabby-stepper
./gradlew :composeApp:run
```

Compile-only check:

```bash
./gradlew :composeApp:compileKotlinJvm
```

Tests:

```bash
./gradlew :composeApp:jvmTest
```

## Interaction

The control is one pill: **−** · **count** · **+**. Plus and minus are
buttons. The integer is a draggable thumb in the same pill.

- **Tap − / +.** The value changes. The thumb stays centered. The number
  and the pill take a short bouncy punch — no crossfade, no travel.
- **Grab the number and drag sideways.** The thumb follows the pointer.
  The track is not rigid chrome: it **elongates toward the thumb**. − / +
  stay at their rest seats and get covered as the thumb slides over them.
  Crossing a small threshold commits the first ±1; holding the thumb off
  center keeps ticking, faster the further you pull (scrub-at-the-edge).
  On release the **value stays**, and the thumb + track **spring back**
  to rest with overshoot.
- **Pull the number down.** The track reaches after the thumb as a gooey
  tether (stretched stadium + metaball neck). − / + fade; a center **✕**
  appears. Far enough, the neck breaks and the circle can leave the pill.
  Release past the threshold **resets to 0**; otherwise the value is
  kept. Either way, thumb and track spring home together.

Springs are used everywhere — snap-back, button settle, value punches.
Stiffness is low and damping is medium-bouncy on purpose. If only the
label changes, it is a form stepper, and this failed.

No image assets. Dark is the reference vibe; there is a light canvas too.

## Layout

Desktop-only CMP (`:composeApp` / JVM). Kotlin 2.3.20, Compose
Multiplatform 1.10.3, Gradle 9.4.1.
