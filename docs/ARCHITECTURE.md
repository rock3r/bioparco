# Architecture

bioparco is a Gradle monorepo of Compose Desktop specimens plus a Jewel showcase.

```
showcase  -->  grabby-stepper
          \->  chat-bubble-transition
          \->  processing-field

recordings --> grabby-stepper
           \->  chat-bubble-transition
           \->  processing-field
           \->  Spectre (Maven Central)
```

| Module | What it is |
|---|---|
| `:showcase` | Jewel catalog. Routes into specimen composables. |
| `:grabby-stepper` | Specimen 1. Library + standalone `run`. |
| `:chat-bubble-transition` | Specimen 2. Library + standalone `run`. |
| `:processing-field` | Specimen 3. Library + standalone `run`. |
| `:recordings` | Spectre-driven recording tests. Not part of `check`. |

## Invariants

- Specimens stay independently runnable (`:module:run`) and embeddable (`App()` / `ChatApp()` / processing-field `App()`).
- The showcase owns Jewel chrome. Specimens keep their own look.
- Motion-critical reads stay in `graphicsLayer` / `offset` lambdas, not composition.
- Recordings attach to a real titled window and write MP4s under `build/recordings/`.

## Jewel

The showcase uses published standalone Int UI:

`org.jetbrains.jewel:jewel-int-ui-standalone:0.41.0-262.10968.63`

Latest published standalone Int UI (Jewel 0.41, IJP 262.10968.63, CMP 1.12.0). A
JetBrains Runtime is nicer for fonts; Temurin 21 still runs the catalog.

## Spectre

Recording tests depend on Spectre 0.6.0 from Maven Central (`spectre-core`,
`spectre-testing`, `spectre-recording`, plus the platform helper artifacts).
