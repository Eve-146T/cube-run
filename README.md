<img align="left" width="80" height="80"
src="fastlane/metadata/android/en-US/images/icon.png" alt="Cube Run">

# Cube Run

A minimalist one-thumb 3D runner: swipe to dodge, jump and slam your cube
through an endless neon gauntlet rushing toward you.

Latest APK: [GitHub releases](https://github.com/Eve-146T/cube-run/releases/latest).

## Gameplay

- Swipe **left / right** to snap between the three lanes.
- Swipe **up** to jump low walls; swipe **down** in mid-air to slam back down fast.
- Pillars and wide bars leave exactly one safe lane, and some obstacles slide
  into the open lane as they approach — read the track and commit late.
- Score one point per row cleared, plus a bonus for shaving past an obstacle.
  The pace keeps climbing the longer you last. Your best score is saved locally.

No accounts, no ads, no tracking, no network access.

## Building

Requires a JDK (17+) and the Android SDK (platform 35, build-tools 35.0.0).

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

A signed release APK is produced by CI on every `v*` tag and attached to a
GitHub release; see [`.github/workflows/build.yml`](.github/workflows/build.yml).
The libGDX native libraries are extracted from the `gdx-platform` artifacts at
build time, so they are not checked in.

## License

Cube Run is free software, licensed under the
[GNU General Public License v3.0](LICENSE). Built with [libGDX](https://libgdx.com/).
