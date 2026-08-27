# Highlight Dual Sub Lab

A standalone Android experiment for testing whether YouTube's own live auto-caption rendering can drive spoken-word highlighting more accurately than estimated word timestamps.

## Completely separate from DualSub Replay

- Android package: `com.kienhoang.highlightdualsub`
- App label: **Highlight Dual Sub Lab**
- Can be installed beside the original DualSub Replay app.
- Uses its own app data, WebView storage, cookies, and version history.

## Experiment

1. Open an English YouTube video with auto-generated captions.
2. The app enables YouTube captions inside its WebView.
3. JavaScript observes YouTube's rendered caption DOM with `MutationObserver`.
4. `requestVideoFrameCallback()` keeps the latest presented media time.
5. Every live caption change is sent directly to Android.
6. The native overlay highlights the newly revealed word instead of estimating timing from line duration.
7. ML Kit translates the current English caption to Vietnamese as the second subtitle line.

The small status strip shows the YouTube media timestamp for each caption event, making it easier to compare against apps such as 4You.

## Important limitation

This is deliberately a timing lab, not a replacement for the full DualSub Replay app. If YouTube emits a whole phrase in one DOM mutation instead of word-by-word growth, the app cannot recover missing per-word boundaries from that event alone. That behavior is itself useful evidence for deciding whether the next experiment should use JSON3/SRV3 timing or acoustic forced alignment.

## Build

GitHub Actions builds a debug APK named `Highlight-Dual-Sub-Lab.apk`. The debug build is installable directly on Android and uses the independent package ID above.
