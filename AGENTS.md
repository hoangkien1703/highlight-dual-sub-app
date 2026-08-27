# AGENTS.md

## Repository purpose

This repository is **Highlight Dual Sub Lab**, a standalone Android experiment for testing spoken-word highlighting against YouTube captions.

Keep this repository focused on caption timing, highlighting, diagnostics, and the minimal bilingual subtitle experience needed to evaluate timing quality. Do not turn it into the full DualSub Replay application unless explicitly requested.

The app must remain independently installable beside the original DualSub Replay app.

## Android identity

- Namespace / release application ID: `com.kienhoang.highlightdualsub`
- Debug / preview application ID: `com.kienhoang.highlightdualsub.preview`
- Release app label: `Highlight Dual Sub Lab`
- Preview app label: `Highlight Dual Sub Lab Preview`
- `minSdk`: 26
- `targetSdk`: 35
- `compileSdk`: 35
- Java/Kotlin target: 17

Do not change the package IDs, remove the debug `applicationIdSuffix`, or otherwise make the preview collide with the original app unless the user explicitly asks for that change.

## Important files

- `app/src/main/java/com/kienhoang/highlightdualsub/MainActivity.kt`
  - Android WebView host.
  - Native subtitle overlay.
  - JavaScript bridge.
  - Active-word rendering.
  - ML Kit English -> Vietnamese translation.
  - Loads the caption engine from assets.

- `app/src/main/assets/youtube-caption-engine.js`
  - Main YouTube caption/timing engine.
  - Reads YouTube player metadata.
  - Loads JSON3 timed text when available.
  - Tracks video frame/media time.
  - Contains the DOM-caption fallback state machine.

- `tests/caption-engine.test.cjs`
  - Node regression tests for the JavaScript caption engine.
  - Any timing/state-machine bug fix should normally add or update a test here.

- `.github/workflows/build-apk.yml`
  - Runs caption-engine tests first.
  - Builds the Android debug/preview APK.
  - Verifies the `.preview` package ID.
  - Publishes numbered preview artifacts/releases from `main`.

## Caption timing priority

Prefer timing sources in this order:

1. **YouTube JSON3 timed-text timing**
2. **Rendered-caption DOM + video-frame clock fallback**
3. Estimation only when YouTube does not expose a usable finer-grained boundary

Do not replace valid JSON3 timing with a weaker DOM estimate.

### JSON3 behavior

The engine should:

- Get caption tracks from the current YouTube player response when possible.
- Prefer an English auto-generated (`kind === "asr"`) track for the current experiment.
- Request `fmt=json3`.
- Support both normal YouTube timed-text URLs and the `m.youtube.com` same-origin fallback.
- Use `tStartMs`, `dDurationMs`, and segment `tOffsetMs` values when provided.
- Keep multi-word timing chunks usable by dividing only the unknown timing inside that chunk rather than throwing away the real chunk anchor.

Remember: a YouTube JSON3 segment is not guaranteed to equal exactly one spoken word.

## DOM fallback invariants

The DOM path exists because JSON3 can fail or YouTube can expose useful live rendered-caption behavior. It must be robust to YouTube rewriting the visible caption line.

### Never regress the highlight on rolling captions

YouTube auto-captions may evolve like:

`I really like` -> `I really like this` -> `really like this video`

When the beginning of the line is dropped/replaced, do **not** blindly reset the active word to index 0.

Preserve progress by mapping suffix/prefix overlap or the previously active token into the new caption whenever possible. A forward-playing video should not visibly flash the highlight back to the first word just because YouTube rewrote the DOM.

### Full/static captions must still advance

Some captions arrive as a complete sentence in one DOM mutation. In that case there may be no newly generated DOM word to trigger the next highlight.

The fallback must continue advancing through the words using the video-frame/media clock. It must not stay stuck on the first word while the sentence remains unchanged.

The current fallback uses an estimated per-word pace only for this no-finer-timing case. Treat that as a fallback, not as more authoritative than JSON3 timing.

### Provisional `0.000s` events

A MutationObserver event can occur before the first real video-frame timestamp and may therefore be reported around `0.000s` even when playback is already later in the video.

Do not use that provisional zero timestamp to calculate a huge elapsed duration. Rebase the DOM timing anchor when the first real media timestamp arrives so a full sentence does not instantly jump to its last word.

### Seeking

A meaningful backward seek is allowed to reset/rebase fallback progression. Normal forward playback should remain monotonic inside the current caption unless a genuine caption transition requires remapping.

## Video clock

Use `requestVideoFrameCallback()` when available so caption highlighting follows presented media time closely. Fall back to `video.currentTime` polling only when necessary.

Do not make DOM mutation arrival time the only playback clock. DOM mutations indicate caption changes; they are not a complete substitute for media time.

## Android bridge and rendering

Caption events sent to Android should include, when available:

- `type: "caption"`
- `text`
- `activeWordIndex`
- `currentSecond`
- `source` (`json3` or `dom`)

The native layer should render the active word supplied by the engine. Do not recompute it from text growth when `activeWordIndex` is already present.

Translation is secondary to timing. ML Kit translation completion must never move or overwrite the active-word timing state.

Keep useful source/timestamp diagnostics in the status strip while this project is still a timing lab.

## Required regression checks

Before merging a caption-engine change, run:

```bash
node --test tests/caption-engine.test.cjs
```

The regression suite should cover at least:

- English auto-generated track preference
- player-response caption-track extraction
- JSON3 chunk offsets
- multi-word JSON3 chunks
- `m.youtube.com` timed-text URL handling
- same-origin timed-text fallback
- full/static DOM caption word progression
- incremental auto-caption growth
- rolling caption overlap/remapping
- shrinking/replaced caption lines
- backward seek/rebase behavior
- provisional `0.000s` DOM timestamp -> real media-time rebase

When fixing a newly observed timing bug, first create a small deterministic test that reproduces the state transition, then change the engine.

## Android build

CI uses Gradle 8.9 and Java 17. The equivalent build command is:

```bash
gradle :app:assembleDebug --stacktrace
```

Do not publish an APK when caption tests or the Android build are failing.

## GitHub Actions preview policy

The workflow runs for PRs and pushes to `main`.

For pull requests:

- Run caption tests.
- Build the APK.
- Verify the preview package ID.
- Upload a numbered Actions artifact.
- Do **not** publish a GitHub release.

For successful pushes to `main`:

- Run the same tests and build checks first.
- Publish a numbered prerelease.

The build number is `github.run_number`.

Expected naming:

- Artifact: `Highlight-Dual-Sub-Lab-preview-<N>`
- APK: `Highlight-Dual-Sub-Lab-preview-<N>.apk`
- SHA file: `Highlight-Dual-Sub-Lab-preview-<N>.apk.sha256`
- Tag: `preview-<N>`
- Release title: `Highlight Dual Sub Lab Preview #<N>`

Do not go back to a single unnumbered rolling APK. The number lets device testers distinguish newer builds from older ones.

## Change workflow

For meaningful code changes:

1. Create a focused branch from `main`.
2. Reproduce the bug with a regression test when practical.
3. Implement the smallest targeted fix.
4. Run the Node caption tests.
5. Let GitHub Actions build the Android preview on the PR.
6. Merge only after the PR checks are green.
7. Confirm the `main` workflow publishes the numbered preview release when a new test APK is desired.

Avoid direct changes to `main` for risky caption-engine work.

For documentation-only changes, it is fine to use a CI-skip commit when no APK needs to be rebuilt.

## Scope and quality rules

- Preserve the app's ability to install beside DualSub Replay.
- Preserve JSON3-first timing.
- Preserve DOM fallback support.
- Preserve frame-clock highlighting.
- Preserve non-regressing rolling-caption state.
- Preserve static/full-caption progression.
- Preserve numbered preview APKs/releases.
- Prefer deterministic tests over timing guesses.
- Keep changes narrow enough that a regression can be attributed to one experiment.
- Do not hide useful diagnostics while a timing approach is still experimental.

If real-device behavior disagrees with a unit test, trust the device observation as evidence that the model/test is incomplete; add a reproduction case rather than dismissing the device result.
