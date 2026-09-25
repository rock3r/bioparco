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

### [Grabby stepper](grabby-stepper/)

**Seb** ([rock3r](https://github.com/rock3r)) · [Vishal’s original](https://x.com/iamvishal16_ios/status/2100596660032172287)

![Grabby stepper](https://static.sebastiano.dev/stable/bioparco/grabby-stepper.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/grabby-stepper.mp4)

### [Chat bubble transition](chat-bubble-transition/)

**Seb** · Kavsoft “Chat Bubble Transition” ([gist](https://gist.github.com/rock3r/4db005c28217aadb9cb7672200f98c2e))

![Chat bubble transition](https://static.sebastiano.dev/stable/bioparco/chat-bubble-transition.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/chat-bubble-transition.mp4)

### [Processing field](processing-field/)

**Chris** ([c5inco](https://github.com/c5inco)) · [Haplo ProcessingField](https://github.com/haplollc/ProcessingField)

![Processing field](https://static.sebastiano.dev/stable/bioparco/processing-field.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/processing-field.mp4)

### [Thinking Orbs](thinking-orbs/)

**Chris** · [Haplo ThinkingOrbs](https://github.com/haplollc/ThinkingOrbs)

![Thinking Orbs](https://static.sebastiano.dev/stable/bioparco/thinking-orbs.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/thinking-orbs.mp4)

### [Dot-matrix recorder](dot-matrix-recorder/)

**Seb** · [Sasha Birukoff](https://x.com/sashabirukoff)’s [Halogen recorder](https://x.com/sashabirukoff/status/2103156002220589129)

![Dot-matrix recorder](https://static.sebastiano.dev/stable/bioparco/dot-matrix-recorder.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/dot-matrix-recorder.mp4)

### [Border beam](border-beam/)

**Seb** · [Playground](https://beam.jakubantalik.com/) · [Jakub Antalik’s border-beam](https://github.com/Jakubantalik/border-beam)

![Border beam](https://static.sebastiano.dev/stable/bioparco/border-beam.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/border-beam.mp4)

### [Achievement badge](achievement-badge/)

**Seb** · [Adrian](https://x.com/adriankuleszo)’s [badge animation](https://x.com/adriankuleszo/status/2103473953868063151)

![Achievement badge](https://static.sebastiano.dev/stable/bioparco/achievement-badge.webp)

[mp4](https://static.sebastiano.dev/stable/bioparco/achievement-badge.mp4)

Recordings are generated automatically with [Spectre](https://spectre.sebastiano.dev).
README previews are animated, lower quality WebPs; original mp4 files are the full clips.

## Run the showcase

JetBrains Runtime (JBR) 25 (`jvmToolchain(25)`). Jewel 0.41 is compiled for Java 25, so the showcase does not start on
an older runtime. Another JDK 25 also works, but JBR renders fonts better.

```bash
./gradlew :showcase:run
```

Or visit one enclosure directly:

```bash
./gradlew :grabby-stepper:run
./gradlew :chat-bubble-transition:run
./gradlew :processing-field:run
./gradlew :thinking-orbs:run
./gradlew :dot-matrix-recorder:run
./gradlew :border-beam:run
./gradlew :achievement-badge:run
...
```

## License

[Apache License 2.0](LICENSE). Copyright 2026 Sebastiano Poggi.
