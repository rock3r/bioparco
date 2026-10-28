# Recordings

Spectre tests in `:recordings` produce a movie for every specimen.
CI stores the large MP4 files outside `main` and replaces them on each publishing run.
It writes download copies to two places:

- the floating GitHub Release named [`recordings`](https://github.com/rock3r/bioparco/releases/tag/recordings)
  (download / archive; browsers treat these URLs as attachments)
- the `recordings-assets` branch under `media/*.mp4`, at stable
  `raw.githubusercontent.com` URLs

`raw.githubusercontent.com` serves these files as `application/octet-stream` with
`X-Content-Type-Options: nosniff`, so a browser downloads them instead of playing them.
The READMEs link a third copy that browsers can play.

## README media

Every run of the `recordings` job also publishes each specimen's media to stable URLs on
static.sebastiano.dev, overwriting the previous run's files:

- `https://static.sebastiano.dev/stable/bioparco/<specimen>.webp`: an animated WebP preview (`image/webp`, which GitHub renders inline)
- `https://static.sebastiano.dev/stable/bioparco/<specimen>.mp4`: the recording (`video/mp4`, which plays in the browser)

The URLs never change, so the READMEs always show the latest recording. After an upload,
Cloudflare's edge serves the previous file for at most 60 seconds.

Every specimen shows both, in its root README entry and in its own `README.md` under
`## Recording`. `ReadmeMediaTest` requires exactly these URLs in `./gradlew check`.

The job creates each preview from the MP4 with `recordings/previews/make-previews.sh`.
[`recordings/previews/previews.tsv`](../recordings/previews/previews.tsv) sets the frame rate,
width, length and crop per specimen. Change preview settings in that file.
To test a change locally, run the script on a folder of MP4s with `ffmpeg` and `img2webp` installed.

Uploads use the `STATIC_UPLOAD_TOKEN` Actions secret, a token scoped to the `bioparco`
namespace.

Do not put `<video>` inside a markdown `| table |` cell: GitHub strips it.

Push a `vX.Y.Z` tag to refresh the recordings. The `recordings` CI job captures them under `xvfb-run`.
It fails if any specimen MP4 is missing or empty, then publishes the README media.
The job replaces every `*.mp4` on the floating `recordings` release with `--clobber` or delete-asset and force-pushes `recordings-assets`.
Pushes to `main` also run this job as a backup. Version-tag runs attach the same MP4s to the version's GitHub Release for archival.

A new specimen needs published files before its README links work.
Run the CI workflow on the specimen's branch and pass its name:

```bash
gh workflow run CI --ref <branch> -f specimens=<specimen>
```

A manual run records every specimen and publishes README media only for the names passed to it.
This prevents an older branch from overwriting other specimens' media. Manual runs leave the release and `recordings-assets` unchanged.

CI can produce recordings without Coso, local Screen Recording permission, or a physical monitor.
Local recording runs are optional and help test motion before the next tag.

## Regenerate

```bash
./gradlew :recordings:recordSpecimens
```

Spectre records an Abstract Window Toolkit (AWT) window. A physical monitor is optional when the host meets these requirements:

| Host | What is enough |
|---|---|
| Linux CI / agents | `xvfb-run -a` plus GStreamer (`ximagesrc`). No physical display. |
| macOS (Coso) | Aqua session + Screen Recording for the JDK. `DISPLAY` can stay unset. |
| Windows | Graphics Capture. Interactive or RDP session. |

The task discovers specimen modules from Gradle includes and writes one MP4 per module:

- `recordings/build/recordings/grabby-stepper.mp4`
- `recordings/build/recordings/chat-bubble-transition.mp4`
- `recordings/build/recordings/processing-field.mp4`
- `recordings/build/recordings/thinking-orbs.mp4`
- `recordings/build/recordings/dot-matrix-recorder.mp4`
- `recordings/build/recordings/border-beam.mp4`
- `recordings/build/recordings/achievement-badge.mp4`
- `recordings/build/recordings/component-anatomy.mp4`
- `recordings/build/recordings/scroll-effects.mp4`
- `recordings/build/recordings/peel-sticker.mp4`
- `recordings/build/recordings/honeycomb.mp4`
- `recordings/build/recordings/hairline.mp4`
- `recordings/build/recordings/departure-board.mp4`
- `recordings/build/recordings/holo-card.mp4`
- `recordings/build/recordings/wheel-picker.mp4`
- `recordings/build/recordings/thinking-bloom.mp4`
- `recordings/build/recordings/squeeze-progress.mp4`
- `recordings/build/recordings/anatomy.mp4`
- `recordings/build/recordings/email-invite.mp4`
- `recordings/build/recordings/routine-timeline.mp4`
- `recordings/build/recordings/layout-morph.mp4`

### Linux / Xvfb

```bash
sudo apt-get install -y xvfb \
  gstreamer1.0-tools gstreamer1.0-plugins-base \
  gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
  gstreamer1.0-plugins-ugly x11-utils
CI=true xvfb-run -a ./gradlew :recordings:recordSpecimens
```

`h264parse` requires `gstreamer1.0-plugins-bad`. Without that package, Spectre can skip recording and leave empty outputs despite a successful Gradle result.
CI installs these packages and fails if an MP4 is missing or too small.

`CI=true` turns on Skiko `SOFTWARE_COMPAT` so the virtual framebuffer has pixels.

### macOS

Grant Screen Recording permission to the JDK that launches Gradle.
Use `spectre permissions request` from a [Spectre](https://github.com/rock3r/spectre) installation, or grant it in System Settings.
Then run the recording task. macOS uses ScreenCaptureKit; Xvfb supports Linux X11 capture only.
Spectre stops immediately if capture permission is missing.

## Publish the movies

Use `git tag vX.Y.Z && git push origin vX.Y.Z` for routine publishing.
CI refreshes the README media, release, and `recordings-assets`.
Use `gh release upload` manually only when the job is unavailable:

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
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/component-anatomy.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/scroll-effects.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/peel-sticker.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/honeycomb.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/hairline.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/departure-board.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/holo-card.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/wheel-picker.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/thinking-bloom.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/squeeze-progress.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/anatomy.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/email-invite.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/routine-timeline.mp4
- https://raw.githubusercontent.com/rock3r/bioparco/recordings-assets/media/layout-morph.mp4

## Traces

Set `BIOPARCO_TRACE_DIR` to get a Perfetto trace of each recording as well, one folder per
specimen. See [TRACING.md](TRACING.md).

## What the tests do

Each `recording`-tagged test:

1. Opens the specimen in a titled Compose Desktop window.
2. Starts `AutoRecorder.startWindow(...)`.
3. Drives the UI with `ComposeAutomator.inProcess()`.
4. Stops the recorder and asserts the MP4 is non-empty.

Recording windows use `application(exitProcessOnExit = false)`.
The default `application {}` calls `exitProcess(0)` when its window closes.
That call kills the Gradle test worker after the first MP4, chat-bubble in alphabetical order, and prevents later recordings.
The task also starts one JVM per test to contain accidental `exitProcess` calls.

`./gradlew check` excludes the `recording` tag. The `recordings` CI job (`v*` tags, or
a push to `main`) runs them under `xvfb-run` on Ubuntu and uploads every MP4 to the
floating release. A skipped or empty recording fails the job instead of publishing a
partial set.

### Prove all twenty-one MP4s

In the `recordings` CI job, the "Record specimens" step must report `PASSED` for all twenty-one tests:

- `AchievementBadgeRecordingTest`
- `AnatomyRecordingTest`
- `BorderBeamRecordingTest`
- `ChatBubbleRecordingTest`
- `ComponentAnatomyRecordingTest`
- `DepartureBoardRecordingTest`
- `DotMatrixRecorderRecordingTest`
- `EmailInviteRecordingTest`
- `GrabbyStepperRecordingTest`
- `HairlineRecordingTest`
- `HoloCardRecordingTest`
- `HoneycombRecordingTest`
- `LayoutMorphRecordingTest`
- `PeelStickerRecordingTest`
- `ProcessingFieldRecordingTest`
- `RoutineTimelineRecordingTest`
- `ScrollEffectsRecordingTest`
- `SqueezeProgressRecordingTest`
- `ThinkingBloomRecordingTest`
- `ThinkingOrbsRecordingTest`
- `WheelPickerRecordingTest`

`recordings/build/recordings/` must contain twenty-one files, each larger than 1 KB.
The task fails if any expected file is missing.
A run filtered with `--tests` records only matching specimens and skips this completeness check.

Run the same checks locally under Xvfb:

```bash
CI=true xvfb-run -a ./gradlew :recordings:recordSpecimens
ls -la recordings/build/recordings/*.mp4
```

Check configuration-cache storage without a display. `--dry-run` still stores the task:

```bash
./gradlew :recordings:recordSpecimens --dry-run --configuration-cache
```

A "cannot serialize Gradle script object references" error means the `doLast` expected-files check captured `rootProject` or script objects.

The tests use Spectre 0.7.1 from Maven Central.
Platform helpers use `testRuntimeOnly` dependencies: `spectre-recording-macos`, `spectre-recording-linux`, and `spectre-recording-windows`.
