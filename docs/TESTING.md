# Testing

## Layers

| Layer | Where | In `check`? |
|---|---|---|
| Unit | specimen `commonTest` (physics, keys, hit-testing) | yes |
| Path helpers | `:recordings` untagged tests | yes |
| Spectre recordings | `:recordings` tests tagged `recording` | no |

## TDD

For behaviour that is not "does this spring feel right":

1. Write the test.
2. Run the targeted test.
3. Prove it fails for the intended reason.
4. Implement the minimum fix.
5. Re-run the targeted test, then `./gradlew check`.

## Commands

```bash
./gradlew :grabby-stepper:jvmTest
./gradlew :recordings:test
./gradlew check
./gradlew :recordings:recordSpecimens
```

Recording tests skip on a headless JVM so `./gradlew :recordings:recordSpecimens` can be
typed locally without a display. The recordings job (`v*` tags, or a push to `main`)
must not skip: it runs under `xvfb-run`, and the task fails if any README movie is
missing. Specimen windows must not `exitProcess` on close, or only the first MP4 is
written. See [RECORDING.md](RECORDING.md).
