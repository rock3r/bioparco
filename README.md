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

![Grabby stepper](https://static.sebastiano.dev/public/591216f5-6742-4352-99e5-5153377f94df.webp)

[mp4](https://static.sebastiano.dev/public/5de810a2-d521-4e4c-80d2-a872b96468a7.mp4)

### [Chat bubble transition](chat-bubble-transition/)

**Seb** · Kavsoft “Chat Bubble Transition” ([gist](https://gist.github.com/rock3r/4db005c28217aadb9cb7672200f98c2e))

![Chat bubble transition](https://static.sebastiano.dev/public/a54aef31-be29-455f-baf6-bbd62f3a559a.webp)

[mp4](https://static.sebastiano.dev/public/a16ba9a3-15ed-4ea8-ae6a-7fa97945d28b.mp4)

### [Processing field](processing-field/)

**Chris** ([c5inco](https://github.com/c5inco)) · [Haplo ProcessingField](https://github.com/haplollc/ProcessingField)

![Processing field](https://static.sebastiano.dev/public/95f54b64-486b-4270-a3a2-bc27ee39ee77.webp)

[mp4](https://static.sebastiano.dev/public/2509f71b-ae97-425e-9c53-cb88fc3702c9.mp4)

### [Thinking Orbs](thinking-orbs/)

**Chris** · [Haplo ThinkingOrbs](https://github.com/haplollc/ThinkingOrbs)

![Thinking Orbs](https://static.sebastiano.dev/public/f9cb4a37-fd43-4dfc-8370-80750de8f85f.webp)

[mp4](https://static.sebastiano.dev/public/a87a0cc8-46cf-4154-8f27-8279a215a757.mp4)

### [Dot-matrix recorder](dot-matrix-recorder/)

**Seb** · [Sasha Birukoff](https://x.com/sashabirukoff)’s [Halogen recorder](https://x.com/sashabirukoff/status/2103156002220589129)

![Dot-matrix recorder](https://static.sebastiano.dev/public/4f22a5fb-1c39-460e-95d6-019a52ec1861.webp)

[mp4](https://static.sebastiano.dev/public/014d19da-1048-403a-b6c2-6c3a010987cd.mp4)

### [Border beam](border-beam/)

**Seb** · [Playground](https://beam.jakubantalik.com/) · [Jakub Antalik’s border-beam](https://github.com/Jakubantalik/border-beam)

![Border beam](https://static.sebastiano.dev/public/81fb11e5-0b99-4baf-8bd8-7ea269d75bdc.webp)

[mp4](https://static.sebastiano.dev/public/f284f1c3-b80b-458e-af76-6632575a5f42.mp4)

### [Achievement badge](achievement-badge/)

**Seb** · [Adrian](https://x.com/adriankuleszo)’s [badge animation](https://x.com/adriankuleszo/status/2103473953868063151)

![Achievement badge](https://static.sebastiano.dev/public/3721f42d-9636-44d5-ba96-c0f77b90dd86.webp)

[mp4](https://static.sebastiano.dev/public/70e42da8-d4dc-46f3-9e64-e2ef80212447.mp4)

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
