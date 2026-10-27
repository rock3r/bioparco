# Testing

## Test groups

`check` runs unit tests and recording-path tests. The separate recording task opens windows and captures motion.

| Layer | Where | In `check`? |
|---|---|---|
| Unit | specimen `commonTest` (physics, keys, hit-testing) | yes |
| Path helpers | `:recordings` untagged tests | yes |
| Spectre recordings | `:recordings` tests tagged `recording` | no |

## Test-driven development

Use test-driven development for plain Kotlin behavior such as physics, keyboard handling, and hit-testing.
Review visual motion directly. Tests must not freeze animation constants unless the change concerns those values.

1. Write a test for the expected behavior.
2. Run that test.
3. Check that it fails for the intended reason.
4. Make the smallest production change that passes the test.
5. Run the test again, then run `./gradlew check`.

## Commands

```bash
./gradlew :grabby-stepper:jvmTest
./gradlew :recordings:test
./gradlew check
./gradlew :recordings:recordSpecimens
```

Recording tests skip when the JVM has no display.
On a clean checkout, `:recordings:recordSpecimens` then fails its output check because specimen movies are missing.
Existing usable movies can satisfy that check. Runs filtered with `--tests` skip the output check.
The recordings job runs under `xvfb-run` on `v*` tags and pushes to `main`.
That job fails if a test skips or a README movie is missing.
Specimen windows must not call `exitProcess` on close, because that stops the worker after the first MP4.
See [RECORDING.md](RECORDING.md).
