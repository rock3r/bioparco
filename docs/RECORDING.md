# Recordings

Every specimen should be visible in motion. The movies are produced by Spectre tests in
`:recordings`, not by hand-waving a screen recorder.

They are **not** committed on `main`. MP4s are large and they go stale. CI records a
specimen only when that specimen's sources change, uploads the MP4 to
[static.sebastiano.dev](https://static.sebastiano.dev), and rewrites the README
`<video>` tags to the new URL.

GitHub's README sanitizer strips `<video>` for GitHub Release and
`raw.githubusercontent.com` URLs (those hosts look like downloads/attachments). Hosted
public objects on `static.sebastiano.dev` are the README movies. Do not convert them to
GIFs.

`recordings/published.json` is the ledger: each enclosure has a source fingerprint and
the hosted URL. The fingerprint hashes that specimen's `src/**`, its `*RecordingTest.kt`,
and the shared `SpecimenWindow.kt`. Changing `SpecimenWindow.kt` invalidates every
enclosure. README and docs changes do not.

## When CI records

The `recordings` job runs on a push to `main`. It prints stale specimen names. If the
list is empty, the job exits without recording, uploading, or committing. If a name is
stale, CI records only that enclosure (`BIOPARCO_RECORD_ONLY`), POSTs the MP4 to
`https://static.sebastiano.dev/upload` with `PR_ASSET_UPLOAD_TOKEN`, writes the new
URL into `published.json`, regenerates the enclosure table, and commits the result.

A version tag is not a recording signal. Re-record happens when the specimen code
moves, not because a tag or an unrelated main push happened.

The repo secret `PR_ASSET_UPLOAD_TOKEN` is required for the upload step. Public uploads
also send bookkeeping headers (`X-Asset-Owner/Repo/Pr/Name`); the URL itself is an
opaque UUID.

## Regenerate

```bash
./gradlew :recordings:recordSpecimens
```

Record only some enclosures:

```bash
BIOPARCO_RECORD_ONLY=grabby-stepper,processing-field \
  ./gradlew :recordings:recordSpecimens
```

You do **not** need a seated desktop monitor. Spectre records an AWT window:

| Host | What is enough |
|---|---|
| Linux CI / agents | `xvfb-run -a` plus GStreamer (`ximagesrc`). No physical display. |
| macOS (Coso) | Aqua session + Screen Recording for the JDK. `DISPLAY` can stay unset. |
| Windows | Graphics Capture. Interactive or RDP session. |

Outputs (one MP4 per requested specimen module):

- `recordings/build/recordings/grabby-stepper.mp4`
- `recordings/build/recordings/chat-bubble-transition.mp4`
- `recordings/build/recordings/processing-field.mp4`

### Linux / Xvfb

```bash
sudo apt-get install -y xvfb \
  gstreamer1.0-tools gstreamer1.0-plugins-base \
  gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
  gstreamer1.0-plugins-ugly x11-utils
CI=true xvfb-run -a ./gradlew :recordings:recordSpecimens
```

`gstreamer1.0-plugins-bad` is required for `h264parse`. Without it Spectre can skip
and leave empty outputs while Gradle still looks green — CI installs the same set and
fails closed if a requested MP4 is missing or tiny.

`CI=true` turns on Skiko `SOFTWARE_COMPAT` so the virtual framebuffer has pixels.

### macOS

Grant Screen Recording to the JDK that launches Gradle (`spectre permissions request`
from a [Spectre](https://github.com/rock3r/spectre) install, or System Settings). Then
the same Gradle task. Xvfb is a Linux X11 thing; it does not drive ScreenCaptureKit.

If capture permission is missing, Spectre fails fast on purpose.

## Publish a movie by hand

Need a token in `PR_ASSET_UPLOAD_TOKEN` and a numeric PR for the public bookkeeping
headers. Then, for each MP4:

```bash
curl -sS -X POST "https://static.sebastiano.dev/upload" \
  -H "Authorization: Bearer $PR_ASSET_UPLOAD_TOKEN" \
  -H "Content-Type: video/mp4" \
  -H "X-Asset-Visibility: public" \
  -H "X-Asset-Owner: rock3r" \
  -H "X-Asset-Repo: bioparco" \
  -H "X-Asset-Pr: 123" \
  -H "X-Asset-Name: grabby-stepper" \
  --data-binary @recordings/build/recordings/grabby-stepper.mp4
```

The JSON `url` goes into `published.json` via:

```bash
./gradlew :recordings:publishSpecimen -Pspecimen=grabby-stepper -Purl='https://static.sebastiano.dev/public/<uuid>.mp4'
./gradlew :recordings:writeReadmeEnclosures
```

See the [pr-asset-upload](https://github.com/rock3r/pr-asset-upload) skill for the
endpoint contract.

## What the tests do

Each `recording`-tagged test:

1. Opens the specimen in a titled Compose Desktop window.
2. Starts `AutoRecorder.startWindow(...)`.
3. Drives the UI with `ComposeAutomator.inProcess()`.
4. Stops the recorder and asserts the MP4 is non-empty.

Windows use `application(exitProcessOnExit = false)`. Compose Desktop's default
`application {}` calls `exitProcess(0)` when the window closes, which kills the
Gradle test worker after the first MP4 (chat-bubble, alphabetically) and leaves
later enclosures unwritten. The task also forks one JVM per test so a leaked
`exitProcess` cannot cancel the rest of the set.

`./gradlew check` excludes the `recording` tag. It does assert that the README
enclosure table embeds the hosted MP4s from `published.json`.

The `recordings` CI job on `main` runs stale tests under `xvfb-run` and uploads
only those MP4s. A skipped or empty recording of a requested specimen fails the job.

List stale names without recording:

```bash
./gradlew -q :recordings:printStaleSpecimens
```

Config-cache hygiene (does not need a display; `--dry-run` still stores the task):

```bash
./gradlew :recordings:recordSpecimens --dry-run --configuration-cache
```

A stored entry with "cannot serialize Gradle script object references" means the
`doLast` expected-files check captured `rootProject` / script objects again.

Spectre 0.6.0 from Maven Central. Helpers ride along as `testRuntimeOnly`
(`spectre-recording-macos` / `-linux` / `-windows`).
