# Architecture

bioparco is a Gradle monorepo of Compose Desktop specimens plus a Jewel showcase.

```
showcase  -->  grabby-stepper
          \->  chat-bubble-transition
          \->  processing-field
          \->  thinking-orbs
          \->  dot-matrix-recorder

recordings --> grabby-stepper
           \->  chat-bubble-transition
           \->  processing-field
           \->  thinking-orbs
           \->  dot-matrix-recorder
           \->  Spectre (Maven Central)
```

| Module | What it is |
|---|---|
| `:showcase` | Jewel catalog. Routes into specimen composables. |
| `:grabby-stepper` | Specimen 1. Library + standalone `run`. |
| `:chat-bubble-transition` | Specimen 2. Library + standalone `run`. |
| `:processing-field` | Specimen 3. Library + standalone `run`. |
| `:thinking-orbs` | Specimen 4. Library + standalone `run`. |
| `:dot-matrix-recorder` | Specimen 5. Library + standalone `run`. |
| `:recordings` | Spectre-driven recording tests. Not part of `check`. |

## Invariants

- Specimens stay independently runnable (`:module:run`) and embeddable (`App()` / `ChatApp()` / processing-field `App()`).
- UI controls and text are Jewel components everywhere, in the showcase and in every specimen.
  Specimens keep their own palettes and motion; Jewel only supplies the widgets.
- No Compose Material. The root build excludes `org.jetbrains.compose.material`, which
  `compose.desktop.currentOs` would otherwise pull in.
- A specimen's public `App()` wraps itself in `IntUiTheme`, so it works standalone, inside the
  showcase, and in Spectre recording windows alike.
- Motion-critical reads stay in `graphicsLayer` / `offset` lambdas, not composition.
- Recordings attach to a real titled window and write MP4s under `build/recordings/`.

## Jewel

The showcase uses published standalone Int UI:

`org.jetbrains.jewel:jewel-int-ui-standalone:0.41.0-262.10968.63`

Latest published standalone Int UI (Jewel 0.41, IJP 262.10968.63, CMP 1.12.0). Jewel 0.41 is
compiled for Java 25 (class file 69), so every module builds with `jvmToolchain(25)`. Use
JetBrains Runtime 25. On Java 21 the catalog fails with `UnsupportedClassVersionError`.

## Spectre

Recording tests depend on Spectre 0.7.1 from Maven Central (`spectre-core`,
`spectre-testing`, `spectre-recording`, plus the platform helper artifacts).
