# Recordings

Every specimen should be visible in motion. The movies are produced by Spectre tests in
`:recordings`, not by hand-waving a screen recorder.

They are **not** committed on `main`. MP4s are large and they go stale. CI writes them
to two places:

- the floating GitHub Release named [`recordings`](https://github.com/rock3r/bioparco/releases/tag/recordings)
  (download / archive; browsers treat these URLs as attachments)
- the `recordings-assets` branch under `media/*.mp4`, at stable
  `raw.githubusercontent.com` URLs

`raw.githubusercontent.com` serves these files as `application/octet-stream` with
`X-Content-Type-Options: nosniff`, so a browser downloads them instead of playing them.
The READMEs therefore link a third copy.

## README media

Every run of the `recordings` job also publishes each specimen's media to stable URLs on
static.sebastiano.dev, overwriting the previous run's files:

- `https://static.sebastiano.dev/stable/bioparco/<specimen>.webp`: an animated WebP preview (`image/webp`, which GitHub renders inline)
- `https://static.sebastiano.dev/stable/bioparco/<specimen>.mp4`: the recording (`video/mp4`, which plays in the browser)

The URLs never change, so the READMEs always show the latest recording. After an upload,
Cloudflare's edge serves the previous file for at most 60 seconds.

Every specimen shows both, in its root README entry and in its own `README.md` under
`## Recording`. `ReadmeMediaTest` requires exactly these URLs in `./gradlew check`.

The job cuts each preview from the MP4 with `recordings/previews/make-previews.sh`.
[`recordings/previews/previews.tsv`](../recordings/previews/previews.tsv) sets the frame rate,
width, length and crop per specimen. Tune a preview there, not by uploading by hand. To try
a change locally, run the script on a folder of MP4s; it needs `ffmpeg` and `img2webp`.

Uploads use the `STATIC_UPLOAD_TOKEN` Actions secret, a token scoped to the `bioparco`
namespace.

Do not put `<video>` inside a markdown `| table |` cell: GitHub strips it.

**Tag `vX.Y.Z` to refresh the recordings.** That is the intentional signal. The
`recordings` CI job records under `xvfb-run`, fails if any specimen MP4 is missing or
empty, publishes the README media, then uploads every `*.mp4` to the floating
`recordings` release (`--clobber` / delete-asset) and force-pushes `recordings-assets`. A
push to `main` is a backup feed of the same job. On a version tag the job also attaches
the same MP4s to that GitHub Release for archival.

**A new specimen** needs its stable URLs filled before its README links work. Run the CI
workflow on the specimen's branch (`gh workflow run CI --ref <branch>`). A manual run
records and publishes the README media, but leaves the release and `recordings-assets`
alone.

You do not need Coso, Screen Recording, a seated monitor, or a local Gradle recording
run to get the recordings. Local regeneration is optional: useful when iterating on
motion before the next tag.

## Regenerate

```bash
./gradlew :recordings:recordSpecimens
```

You do **not** need a seated desktop monitor. Spectre records an AWT window:

| Host | What is enough |
|---|---|
| Linux CI / agents | `xvfb-run -a` plus GStreamer (`ximagesrc`). No physical display. |
| macOS (Coso) | Aqua session + Screen Recording for the JDK. `DISPLAY` can stay unset. |
| Windows | Graphics Capture. Interactive or RDP session. |

Outputs (one MP4 per specimen module; discovered from Gradle includes, not a hardcoded
list):

- `recordings/build/recordings/grabby-stepper.mp4`
- `recordings/build/recordings/chat-bubble-transition.mp4`
- `recordings/build/recordings/processing-field.mp4`
- `recordings/build/recordings/thinking-orbs.mp4`
- `recordings/build/recordings/dot-matrix-recorder.mp4`
- `recordings/build/recordings/border-beam.mp4`
- `recordings/build/recordings/achievement-badge.mp4`

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
fails closed if an MP4 is missing or tiny.

`CI=true` turns on Skiko `SOFTWARE_COMPAT` so the virtual framebuffer has pixels.

### macOS

Grant Screen Recording to the JDK that launches Gradle (`spectre permissions request`
from a [Spectre](https://github.com/rock3r/spectre) install, or System Settings). Then
the same Gradle task. Xvfb is a Linux X11 thing; it does not drive ScreenCaptureKit.

If capture permission is missing, Spectre fails fast on purpose.

## Publish the movies

Happy path: `git tag vX.Y.Z && git push origin vX.Y.Z`. CI refreshes the README media,
the release and `recordings-assets`. Do not `gh release upload` by hand unless the job is
down.

Emergency local publish:

```bash
gh release view recordings || gh release create recordings --title "specimen recordings" --notes "Auto-generated by ./gradlew :recordings:recordSpecimens"
gh release upload recordings recordings/build/recordings/*.mp4 --clobber
```

Also refresh `recordings-assets` (`media/*.mp4`) so the stable download URLs stay current.

Stable download URLs:

- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/grabby-stepper.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/chat-bubble-transition.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/processing-field.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/thinking-orbs.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/dot-matrix-recorder.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/border-beam.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/achievement-badge.mp4

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

`./gradlew check` excludes the `recording` tag. The `recordings` CI job (`v*` tags, or
a push to `main`) runs them under `xvfb-run` on Ubuntu and uploads every MP4 to the
floating release. A skipped or empty recording fails the job instead of publishing a
partial set.

### Prove all seven MP4s

On the `recordings` CI job, the "Record specimens" step must list every enclosure
as `PASSED` (`AchievementBadgeRecordingTest`, `BorderBeamRecordingTest`, `ChatBubbleRecordingTest`,
`DotMatrixRecorderRecordingTest`, `GrabbyStepperRecordingTest`, `ProcessingFieldRecordingTest`,
`ThinkingOrbsRecordingTest`) and `recordings/build/recordings/` must contain seven files each
larger than 1 KB. The task fails closed if any name is missing.

Locally, same gate under Xvfb:

```bash
CI=true xvfb-run -a ./gradlew :recordings:recordSpecimens
ls -la recordings/build/recordings/*.mp4
```

Config-cache hygiene (does not need a display; `--dry-run` still stores the task):

```bash
./gradlew :recordings:recordSpecimens --dry-run --configuration-cache
```

A stored entry with "cannot serialize Gradle script object references" means the
`doLast` expected-files check captured `rootProject` / script objects again.

Spectre 0.7.1 from Maven Central. Helpers ride along as `testRuntimeOnly`
(`spectre-recording-macos` / `-linux` / `-windows`).
