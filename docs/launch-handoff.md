# Cube and menu handoff correction

**Superseded in part:** subsequent user review found a remaining scene snap and
stop/start rotation. [Launch continuity correction](launch-continuity.md)
documents the reproduction, wider tests and replacement handoff. Measurements
and crossfade descriptions below apply to the previous APK.

Worktree: `~/Worktrees/cube-run/optimizing-launch-time`, branch
`optimizing-launchtime`. Previous APK: commit `48a48f2`.

## What was wrong

The system animated a centred rose cube while the app's camera was already
travelling behind it. The exit callback changed the native rotation and colour,
then immediately removed the system view. That exposed the previous native
frame before the corrected pose had been drawn. The system animation also stops
at one second, but the old transfer could continue from a later rotation.

The menu had two entrance clocks: each control's staggered animation and a
translation/fade of the entire HUD. The buttons kept moving as the camera
finished. Deferred HUD construction also made the relative timing depend on
graphics startup.

## Changes

- Hold the camera at the system cube's pose during transfer. Rotation follows
  the reported system phase, capped at the actual vector duration.
- Request the matching native frame and wait for its hardware frame-commit
  callback. Then crossfade the system view over 80 ms. A frame commit means
  submission to the swapchain, not proof of display presentation.
- After removal, continue rotation and ease the camera over the remaining intro
  time. Keep the original 1.57-second native-clock deadline; do not restart the
  intro or add a second complete animation. Native and GL use the same camera,
  rotation and colour clocks.
- Save an Android launch theme when the player equips a cube. Android 12+ can
  draw that skin before the game process starts. All 24 cube skins are covered,
  including changing colours, glow and Ghost's opacity/emission. Classic uses
  the last opening world's colour; if the next world differs, blend smoothly
  into its new colour over 240 ms.
- Reuse the same animated geometry for every saved theme. Generate the paint
  values from `Skins.kt` and `Worlds.kt`, deduplicate identical values and inherit
  unchanged defaults. No video decoder, launcher alias or runtime dependency.
- The initial menu uses one fade on the intro clock. Lay out buttons in their
  final positions, with cutout insets applied before attachment. Page-return
  animations remain available. The idle hint starts breathing from its current
  full opacity, so completing the fade does not introduce another jump.

On the first launch after installation/upgrading from an APK that did not save
a theme, Android still uses the original rose palette. Subsequent launches use
the remembered theme. Android 9–11 lack this persisted splash-theme API and keep
the existing starting window followed by the equipped native cube.

## Progress and verification

1. Reproduced and traced the premature system/native handoff.
2. Implemented frame submission before removal, phase matching, saved palettes
   and a single menu entrance clock.
3. Replaced an experimental 350 ms camera catch-up with smooth travel over the
   remaining intro duration. The catch-up concentrated too much motion into a
   short interval.
4. The shared emulator `emulator-5560` was overwritten by another APK during
   verification. Its prototype recordings and failed test log are **not final
   evidence**. Created a dedicated AVD in `.build-tmp/avd`, `emulator-5582`:
   Android 15/API 35 Google APIs x86_64, Pixel 5, 1080×2340, 440 dpi, 60 Hz,
   host GPU. No physical phone was used.
5. On the isolated emulator, the first 15-test suite passed. It checks all 30
   system/native palettes (24 skins, five additional Classic worlds, and the
   original rose fallback), button screen coordinates throughout the visible
   fade, native/GL silhouettes, clocks, early taps, restart, recreation, rapid
   close and shop rendering. Added a further regression for an already-finished
   system animation and continuation after release.
6. The final suite exposed a test-observer race: `ActivityScenario.onActivity`
   can run after the fade. Registered the frame observer at Activity creation,
   before launch. The corrected button-position check passed without weakening
   its requirement to observe at least five visible frames.
7. Bought and equipped **Lava through the actual wardrobe UI**, then force-stopped
   and relaunched. Both progress and persisted launch preferences contain skin
   2. The first system cube is orange/red and continues Lava's colour animation
   into the native and GL cube. Buttons fade at fixed screen coordinates.
8. Debug, instrumentation and unsigned release builds pass. Lint has zero errors
   and the same two existing warnings (target SDK and the Back callback attribute).
   The final isolated run passes **all 16 tests**, with Lava equipped, including
   every generated palette and the corrected button-position observer.

### Review evidence

- [Saved Lava cold-start recording](../captures/launch-handoff/saved-lava/launch-1.mp4)
- [Source filmstrip with frame numbers and request-relative timestamps](../captures/launch-handoff/saved-lava/strip-1/sheet.png)
- [Verified test log](launch-handoff-reference/tests.txt)
- [APK](../captures/launch-handoff/final-apk/cube-run-launch-handoff.apk)
- [Shared APK](https://apps.muxu.click/d/bhgjfhin)
- [Committed trace, timing results and APK hashes](launch-handoff-reference/evidence.json)

In that **single Lava recording**, first cube pixels occur at source frame 8,
154.56 ms after the request (previous source frame: 131.22 ms). The menu's white
counter settles at frame 76, 2,168.267 ms, confirmed by frame 78. These are actual
frames in the linked recording, not medians or a claim of zero-time loading.
The remaining world/menu work continues after the cube appears.

Some emulator MP4s contain the original Winscope timing packet even though
FFmpeg exposes no data packets. `video_clock.py` recovers those original bytes
and rejects ambiguous/missing data, mismatched frame counts, nonmonotonic times,
or intervals differing from decoded video PTS by over 2 ms. Lava's 243 frame
timestamps match its 243 decoded frames; maximum interval discrepancy is
0.103 ms. No timestamps are reconstructed or shifted to favour this build.
The benchmark now validates metadata and uses a separate device filename for
each recording. Earlier `final/` recordings include Android's first-use
fullscreen tip; the clean Lava recording above is the visual review artifact.

Final build, recording and test evidence is under `captures/launch-handoff/`.
The `before.apk` is the actual previous build, not the original slower baseline.
Raw recordings use the same device-clock launch marker and retain source frame
timestamps; no independently reset panels or accelerated playback.

## Size and dependencies

The unsigned release APK is 8,884,363 bytes versus 8,737,195 before this fix:
147,168 bytes more for persisted animated palettes and handoff code. It is still
68,635 bytes smaller than the original 8,952,998-byte release. No new dependency.
The earlier lossless launcher WebP saving remains. R8 and per-ABI distribution
remain opportunities described in [launch-animation.md](launch-animation.md).

## Platform references

- [SplashScreen.setSplashScreenTheme](https://developer.android.com/reference/android/window/SplashScreen#setSplashScreenTheme(int)) persists the theme name for subsequent starts.
- [SplashScreenView](https://developer.android.com/reference/android/window/SplashScreenView) reports animation start/duration and must be removed on the UI thread outside drawing methods.
- [ViewTreeObserver.registerFrameCommitCallback](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable)) reports hardware frame submission.
