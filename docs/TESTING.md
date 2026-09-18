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

Recording tests skip on a headless JVM. On a machine with a display they still need the
platform capture grant documented in [RECORDING.md](RECORDING.md).
