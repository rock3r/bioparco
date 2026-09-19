<img width="100%" alt="bioparco banner" src="https://github.com/user-attachments/assets/bea3be12-0d9e-4e60-9d2e-f71a63de1a96" />

# Bioparco

A public collection of cute [Compose Desktop](https://www.jetbrains.com/compose-multiplatform/)
experiments, gathered like specimens in a bioparco (wildlife park). Each specimen is in its own
enclosure (Gradle module).

The easiest way to try the specimens is the showcase. Each enclosure is its own folder, its own
Gradle module, and its own README.

Note that all examples in this repository are grown (i.e., mostly vibe coded) and not crafted,
because the goal here is to prove that you _can_ create all those nice effects you see on iOS
and the Web in Compose, too, even if you're not an expert. You just need a decent agent, time,
an eye for detail, and the passion to care about these things.

## Enclosures

| Specimen | What you should see | Recording |
|---|---|---|
| [Grabby stepper](grabby-stepper/) | A thumb you grab; the dark track stretches after it like rubber | [mp4](https://github.com/rock3r/bioparco/releases/download/recordings/grabby-stepper.mp4) |
| [Chat bubble transition](chat-bubble-transition/) | Composer chrome flies into the transcript and becomes a sent bubble | [mp4](https://github.com/rock3r/bioparco/releases/download/recordings/chat-bubble-transition.mp4) |
| [Processing field](processing-field/) | A grid of marks whose sizes describe one soft mass that drifts and folds | [mp4](https://github.com/rock3r/bioparco/releases/download/recordings/processing-field.mp4) |

Recordings are generated automatically with [Spectre](https://spectre.sebastiano.dev).

## Run the showcase

JDK 21+ (`jvmToolchain(21)`). A JetBrains Runtime is strongly recommended; Temurin works but not as well.

```bash
./gradlew :showcase:run
```

Or visit one enclosure directly:

```bash
./gradlew :grabby-stepper:run
./gradlew :chat-bubble-transition:run
./gradlew :processing-field:run
...
```

## License

[Apache License 2.0](LICENSE). Copyright 2026 Sebastiano Poggi.
