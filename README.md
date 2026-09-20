# bioparco

A public collection of cute [Compose Desktop](https://www.jetbrains.com/compose-multiplatform/)
experiments, gathered like specimens in a bioparco.

Owner: [Sebastiano Poggi](https://github.com/rock3r) (`rock3r`).

The Jewel showcase is the front gate. Each enclosure is its own folder, its own Gradle
module, and its own README.

## Enclosures

| Specimen | What you should see | Recording |
|---|---|---|
| [Grabby stepper](grabby-stepper/) | A thumb you grab; the dark track stretches after it like rubber | [mp4](https://github.com/rock3r/bioparco/releases/download/recordings/grabby-stepper.mp4) |
| [Chat bubble transition](chat-bubble-transition/) | Composer chrome flies into the transcript and becomes a sent bubble | [mp4](https://github.com/rock3r/bioparco/releases/download/recordings/chat-bubble-transition.mp4) |
| [Thinking Orbs](thinking-orbs/) | Nine dotted 3D signals for what an AI or agent is doing | [mp4](https://github.com/rock3r/bioparco/releases/download/recordings/thinking-orbs.mp4) |

Recordings are generated automatically with [Spectre](https://github.com/rock3r/spectre).
They live on the floating [`recordings` release](https://github.com/rock3r/bioparco/releases/tag/recordings),
not in git. How to regenerate: [docs/RECORDING.md](docs/RECORDING.md).

## Run the showcase

JDK 21+ (`jvmToolchain(21)`). A JetBrains Runtime is nicer for Jewel fonts; Temurin works.

```bash
./gradlew :showcase:run
```

Or visit one enclosure directly:

```bash
./gradlew :grabby-stepper:run
./gradlew :chat-bubble-transition:run
./gradlew :thinking-orbs:run
```

## Versions

| Piece | Version |
|---|---|
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.0 |
| Jewel (showcase) | `0.39.1-262.9437.29` (`jewel-int-ui-standalone`) |
| Spectre (recordings) | 0.6.0 |
| Gradle | 9.4.1 |

## House rules for agents

[AGENTS.md](AGENTS.md) is the operating manual. `./gradlew check` is the gate.
