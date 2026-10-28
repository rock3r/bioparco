# Architecture

Bioparco keeps its Compose Desktop specimens and Jewel showcase in one Gradle repository.
The showcase and recording tests depend on the specimens as shown below.

```
showcase  -->  grabby-stepper
          \->  chat-bubble-transition
          \->  processing-field
          \->  thinking-orbs
          \->  dot-matrix-recorder
          \->  border-beam
          \->  achievement-badge
          \->  component-anatomy
          \->  scroll-effects
          \->  peel-sticker
          \->  honeycomb
          \->  hairline
          \->  departure-board
          \->  holo-card
          \->  wheel-picker
          \->  thinking-bloom
          \->  squeeze-progress
          \->  anatomy
          \->  layout-morph

recordings --> grabby-stepper
           \->  chat-bubble-transition
           \->  processing-field
           \->  thinking-orbs
           \->  dot-matrix-recorder
           \->  border-beam
           \->  achievement-badge
           \->  component-anatomy
           \->  scroll-effects
           \->  peel-sticker
           \->  honeycomb
           \->  hairline
           \->  departure-board
           \->  holo-card
           \->  wheel-picker
           \->  thinking-bloom
           \->  squeeze-progress
           \->  anatomy
           \->  layout-morph
           \->  Spectre (Maven Central)

every specimen, showcase, recordings --> tracing --> androidx.tracing (Perfetto)
```

| Module | What it is |
|---|---|
| `:showcase` | Jewel catalog. Routes into specimen composables. |
| `:grabby-stepper` | Specimen 1. Library + standalone `run`. |
| `:chat-bubble-transition` | Specimen 2. Library + standalone `run`. |
| `:processing-field` | Specimen 3. Library + standalone `run`. |
| `:thinking-orbs` | Specimen 4. Library + standalone `run`. |
| `:dot-matrix-recorder` | Specimen 5. Library + standalone `run`. |
| `:border-beam` | Specimen 6. Library + standalone `run`. |
| `:achievement-badge` | Specimen 7. Library + standalone `run`. |
| `:component-anatomy` | Specimen 8. Library + standalone `run`. |
| `:scroll-effects` | Specimen 9. Library + standalone `run`. |
| `:peel-sticker` | Specimen 10. Library + standalone `run`. |
| `:honeycomb` | Specimen 11. Library + standalone `run`. |
| `:hairline` | Specimen 12. Library + standalone `run`. |
| `:departure-board` | Specimen 13. Library + standalone `run`. |
| `:holo-card` | Specimen 14. Library + standalone `run`. |
| `:wheel-picker` | Specimen 15. Library + standalone `run`. |
| `:thinking-bloom` | Specimen 16. Library + standalone `run`. |
| `:squeeze-progress` | Specimen 17. Library + standalone `run`. |
| `:anatomy` | Specimen 18. The Anatomy kit, its figures, and `capture` for PNG review. |
| `:layout-morph` | Specimen 19. Library + standalone `run`. |
| `:recordings` | Spectre-driven recording tests. Not part of `check`. |
| `:tracing` | House module: Perfetto trace sections for any specimen, off unless asked for. See [TRACING.md](TRACING.md). |

## Invariants

- The shared modules `:showcase`, `:recordings`, and `:tracing` have no specimen recording or README media.
  `RecordingPaths` and `:recordings` list these modules.
- Every specimen's `main` calls `startTracingFromEnvironment`. Its window calls `TracedFrames()`.
  These calls let developers trace any specimen without editing it.
- Each specimen supports standalone execution through `:module:run` and embedding through its composable
  (`App()`, `ChatApp()`, or processing-field's `App()`).
- The showcase and specimens use Jewel for UI controls and text. Each specimen defines its own palette and motion.
- The root build excludes `org.jetbrains.compose.material`, which `compose.desktop.currentOs` would otherwise include.
- Each specimen's public `App()` applies `IntUiTheme` for standalone, showcase, and Spectre recording windows.
- Read motion values in `graphicsLayer` and `offset` lambdas. Composition must not read these values.
- Recording tests attach to real titled windows and write MP4s under `build/recordings/`.

## Jewel

The showcase uses this published standalone Int UI dependency:

`org.jetbrains.jewel:jewel-int-ui-standalone:0.41.0-262.10968.63`

The dependency pairs Jewel 0.41 with IntelliJ Platform 262.10968.63 and Compose Multiplatform 1.12.0.
Jewel 0.41 uses Java 25 class files (version 69). Every module therefore builds with `jvmToolchain(25)`.
Use JetBrains Runtime 25. Java 21 fails to start the catalog with `UnsupportedClassVersionError`.

## Spectre

The recording tests use Spectre 0.7.1 from Maven Central. They depend on `spectre-core`,
`spectre-testing`, `spectre-recording`, and the platform helper artifacts.
