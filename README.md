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

![Grabby stepper](https://static.sebastiano.dev/public/74d7fbbb-0f95-4660-9a99-4977907ad265.webp)

[mp4](https://static.sebastiano.dev/public/c07c7530-b94a-47f9-80c6-b9cff3007dbe.mp4)

### [Chat bubble transition](chat-bubble-transition/)

**Seb** · Kavsoft “Chat Bubble Transition” ([gist](https://gist.github.com/rock3r/4db005c28217aadb9cb7672200f98c2e))

![Chat bubble transition](https://static.sebastiano.dev/public/a54aef31-be29-455f-baf6-bbd62f3a559a.webp)

[mp4](https://static.sebastiano.dev/public/a16ba9a3-15ed-4ea8-ae6a-7fa97945d28b.mp4)

### [Processing field](processing-field/)

**Chris** ([c5inco](https://github.com/c5inco)) · [Haplo ProcessingField](https://github.com/haplollc/ProcessingField)

![Processing field](https://static.sebastiano.dev/public/9ba8d684-f078-4601-a788-cbc43ee77a3e.webp)

[mp4](https://static.sebastiano.dev/public/0f1a3f5f-cc47-4291-bc08-e0d852e4abfb.mp4)

### [Thinking Orbs](thinking-orbs/)

**Chris** · [Haplo ThinkingOrbs](https://github.com/haplollc/ThinkingOrbs)

![Thinking Orbs](https://static.sebastiano.dev/public/47905992-e9e5-4826-9d75-2b9c205de570.webp)

[mp4](https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/thinking-orbs.mp4)

### [Dot-matrix recorder](dot-matrix-recorder/)

**Seb** · [Sasha Birukoff](https://x.com/sashabirukoff)’s [Halogen recorder](https://x.com/sashabirukoff/status/2103156002220589129)

![Dot-matrix recorder](https://static.sebastiano.dev/public/4f22a5fb-1c39-460e-95d6-019a52ec1861.webp)

[mp4](https://static.sebastiano.dev/public/014d19da-1048-403a-b6c2-6c3a010987cd.mp4)

### [Border beam](border-beam/)

**Seb** · [Playground](https://beam.jakubantalik.com/) · [Jakub Antalik’s border-beam](https://github.com/Jakubantalik/border-beam)

![Border beam](https://static.sebastiano.dev/public/81fb11e5-0b99-4baf-8bd8-7ea269d75bdc.webp)

[mp4](https://static.sebastiano.dev/public/f284f1c3-b80b-458e-af76-6632575a5f42.mp4)

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
...
```

## License

[Apache License 2.0](LICENSE). Copyright 2026 Sebastiano Poggi.
