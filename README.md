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

![Grabby stepper](https://static.sebastiano.dev/private/79fd0de3-eb08-4de5-bbf1-d9978a6b3f8b.webp)

[mp4](https://static.sebastiano.dev/private/8fab85f0-c68b-4b10-a790-90857d9742a4.mp4)

### [Chat bubble transition](chat-bubble-transition/)

**Seb** · Kavsoft “Chat Bubble Transition” ([gist](https://gist.github.com/rock3r/4db005c28217aadb9cb7672200f98c2e))

![Chat bubble transition](https://static.sebastiano.dev/private/e876899a-eb62-4463-9abf-9b729589efcc.webp)

[mp4](https://static.sebastiano.dev/private/d774f8e5-804b-49be-be95-244d05e52170.mp4)

### [Processing field](processing-field/)

**Chris** ([c5inco](https://github.com/c5inco)) · [Haplo ProcessingField](https://github.com/haplollc/ProcessingField)

![Processing field](https://static.sebastiano.dev/private/9775e378-85ab-43cd-9bb9-89778f2b7def.webp)

[mp4](https://static.sebastiano.dev/private/6e15ef72-44e3-46eb-bbb9-7b2954b1ce41.mp4)

Recordings are generated automatically with [Spectre](https://spectre.sebastiano.dev).
README previews are animated WebP hosted on [static.sebastiano.dev](https://static.sebastiano.dev); mp4 links are the full clips.

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
