# Moving animation at launch

**Follow-up:** [Cube and menu handoff correction](launch-handoff.md) fixes the
system/native transition and button movement, and adds saved-skin launch themes.
Measurements below describe the preceding APK, not that follow-up build.

This supersedes the static-cube result in `66f1309` and its reporting correction
in `d7c1a5f`. Worktree: `optimizing-launchtime`, at
`~/Worktrees/cube-run/optimizing-launch-time`. Original baseline: `4a314a9`.

## What was taking so long

The original opening went through the entire graphics startup pipeline:

1. Android creates the process and Activity.
2. libGDX loads its native backend and creates the graphics surface.
3. Android acquires an EGL/OpenGL context for that surface.
4. The game builds shaders, meshes, world renderers, and the player.
5. The first buffer reaches the display; only then does the intro run.

The first baseline launch after installation took 1,022 ms from Application to
the cube draw. That is a single observation, not a median. With warmed emulator
caches, graphics-surface setup still consumed roughly 140–250 ms after the host
was attached. Drawing a few cube faces does not need this entire pipeline.

The earlier optimization put a **static** cube ahead of this work. It also froze
the intro clock while preparing renderers, then played the full intro. That is
why its menu could finish later. Its old comparison export also subtracted two
different frame gaps when trimming the recordings. Neither the static image nor
the mismatched time origins is used as evidence of faster animation here.

## Approaches actually tried

| Approach | Emulator observation | Decision |
| --- | --- | --- |
| Native Android Canvas | First draw submitted 348 ms after request in the isolated probe; actual projected 3D faces, lighting and glow | Use it while the game loads |
| VideoView / MediaPlayer | Prepared at 413 ms; first-frame callback at 1,174 ms after request | Reject for this launch path: decoder/surface startup added delay |
| Vector with path keyframes | Inflated successfully but geometry stayed static in the recording | Reject; caught by visual inspection |
| Vector with sequential path morphs | Geometry visibly turns in Android's starting window | Use on Android 12+ |

The first two rows are **one diagnostic trial each**, with recording enabled.
The numbers describe draw/callback submission, not display presentation. Probe
videos and traces are under `captures/launch-animation/probes/`. The video is a
1080×2340, 60 fps H.264 render, 337,812 bytes; it uses the built-in Android player,
not another playback dependency. A video is possible, but this measured player
path did not make playback instant. Other hardware/decoders may behave differently.

## Final implementation

- Android 12+ starts an animated vector before the game process is ready. Its
  paths come from projected 3D geometry, rather than rotating a flat cube image.
  Eight path segments per second interpolate continuously; colour/opacity use
  supported scalar keyframes. Unseen faces are omitted.
- A small Android Canvas view continues the moving cube independently of EGL and
  game shaders. It draws the equipped skin with the same projection and lighting
  as the real player. On Android 9–11, this native view follows the static starting
  window; pre-process animation is only provided by the newer SplashScreen API.
- The system's reported animation start carries rotation into the native view.
  Camera travel starts on the native clock so removing the system window does
  not teleport the cube down the screen. The system's fixed rose colour blends
  into the equipped skin over 160 ms.
- Native and GL renderers share `OpeningPose` and `OpeningClock`. Preparing world
  renderers no longer freezes or restarts the opening. GL joins at the current
  pose. Since the native view owns the animation, the obsolete 4 ms per-frame
  GL preparation budget is removed; GL now finishes the batches together. The
  native cover fades over 50 ms after a complete scene buffer has
  been submitted and swapped.
- The original 180 ms camera hold is now covered by the system lead-in instead
  of being replayed in the app. The native phase lasts 1.57 seconds; camera travel
  and fades retain their original durations. Request-to-menu time is measured too.
- Leaving the app pauses the clock. Early taps, restart, recreation and navigation
  retain their existing behavior.

Android does not promise exact synchronization between its vector RenderThread
and the app thread. The reported system start provides the phase estimate; the
native-to-GL pose is shared exactly. The system vector is a one-second lead-in;
exceptionally slow process startup can outlast it. We do not claim a zero-ms
launch or identical results on untested devices.

## Validation and progress

- Inspected recordings frame by frame: caught the silently static vector and an
  initial system/native camera jump, then corrected both.
- Checked the entire vector path against Android's 96 dp circular mask. A pulse
  peak extended about 2 dp outside it; reducing the shared projection reference
  from 640 to 620 dp keeps every animated corner inside without clipping.
- Added a framebuffer test comparing native and GL silhouettes at 0, 0.25, 0.8,
  and 1.5 seconds; required overlap exceeds 96%.
- Added clock checks for repeated start, system phase adoption, background pause,
  resume and early finish.
- The first 27-test run found a glow phase mismatch when returning from the shop.
  Fixed it by continuing the game's visual time, instead of giving only the
  player an offset. The rerun passed **all 27 tests**: native/GL, renderer startup,
  lifecycle, shop rendering, timing/balance, touch input and smash regressions.
  All 13 affected launch/clock/shop tests also pass after the final timing and
  GL preparation changes.
- Debug, instrumentation and release builds pass. Lint has no errors and only
  the two pre-existing warnings about target SDK and the Back callback attribute.
- Emulator: API 35 Google APIs x86_64, Pixel 5 geometry, 1080×2340 at 440 dpi,
  60 Hz, host GPU, `emulator-5560`. No physical phone was used. Android 9–11 was
  not emulator-tested because those system images are not installed.

## Measurement method

Fresh baseline and final recordings use the same emulator, world 0, force-stop,
launch command, and screenrecord settings. Installation/build time is excluded.
Each video is timed from the device-clock `CUBE_LAUNCH request` marker using
screenrecord's per-frame Winscope metadata. APK hashes identify the exact builds.

`analyze_video.py` detects the first cube pixels. `measure_motion.py` separately
reports a conservative **motion-confirmed-by** time: it ignores the first 300 ms
of Android's window zoom and requires a normalized silhouette change in two
consecutive source frames. This is an upper bound, not an assertion that motion
started at that exact instant. Filmstrips retain source frame numbers/timestamps
so changes can be visually audited. `measure_menu.py` checks menu settling in the same videos: white coin-counter
pixels must be within 1/255 mean RGB intensity of their final appearance on
three consecutive frames. The logo keeps bobbing, so it is not a completion marker.

`compare.py` shifts original PTS to the common launch-request origin before
sampling either video. It does not reset each trimmed clip to its next frame.
Both panels use the same clock and playback speed, and run past menu completion.

## Final results

All values below are **milliseconds from the launch request**. Three recorded,
process-cold launches per build; medians with min–max in parentheses. These are
warm emulator caches, not fresh-install or device-reboot guarantees.

| Visible milestone | Before | After |
| --- | ---: | ---: |
| First cube pixels | 681 (658–691) | 134 (125–152) |
| Motion confirmed by (conservative upper bound) | 714 (691–724) | 618 (566–651) |
| Menu visually settled | 2324 (2266–2332) | 2007 (1950–2035) |

The cube appears about 80% sooner and the menu settles about 318 ms sooner.
**134 ms is first appearance, not the motion detector's timestamp.** The source
filmstrip shows the vector turning earlier than the conservative motion check,
which discards the launch-window zoom and requires a larger silhouette change.
Neither figure is a claim of a zero-ms launch.

The entire game's graphics initialization did **not** improve in these recorded
runs: Application-to-first-GL-cube draw was 435 ms before versus 506 ms after
(medians). Android TotalTime was 471 versus 586 ms. These diagnostic milestones
must not be presented as faster full-game startup; the improvement comes from
animating independently of it. The menu still settles earlier, including every
pair in this small sample.

### The actual linked video (trial 2, not the medians)

| Panel | First cube pixels | Motion confirmed by | Menu settled |
| --- | ---: | ---: | ---: |
| Before | 657.81 | 691.19 | 2331.82 |
| After | 124.86 | 565.81 | 2006.80 |

The separately decoded 60 fps comparison shows the first cube at
0.667 s before and 0.133 s after.
Each source frame is rounded up to the next common 60 Hz tick. The export never
advances a frame ahead of its source timestamp; the remaining difference is
less than one frame, with no independent re-zeroing or changed playback speed.

- [Comparison video](../captures/launch-animation/comparison-final.mp4)
- [After source filmstrip](../captures/launch-animation/final/strip-2/sheet.png)
- [Before source filmstrip](../captures/launch-animation/before/strip-2/sheet.png)
- [Committed measurements, traces and APK hashes](launch-animation-reference/measurements.json)
- Final raw recordings: `captures/launch-animation/{before,final}/`.
- Final tested APK: `captures/launch-animation/final-apk/app-debug.apk`.
- Test logs: `captures/launch-animation/tests-final.txt` (27 passed),
  `tests-mask.txt` (5 passed), and `tests-bulk.txt` (13 passed on final code).

The final unsigned release APK is **8,737,195 bytes**,
down from 8,952,998: **215,803 bytes smaller** (2.41%), including the new
animation. No playback library is added. Debug builds include the experimental
Activity/video and are larger: 11,027,560 bytes versus the baseline debug
APK's 10,447,980. The release reduction must not be applied to debug sizes.


## APK size and dependencies

The pre-revision unsigned release APK was 8,952,998 bytes. The launcher image was
1,247,192 bytes inside the APK. Converting it to lossless WebP reduces that entry
to 1,014,188 bytes: **233,004 bytes saved**, with zero different decoded pixels
(`magick compare -metric AE`). After adding the moving intro, the final release
size is recorded with the measurement results above.

No runtime library was added. The video and probe Activity exist only in the
debug source set and are excluded from release. The unused libGDX audio backend
remains disabled; that avoids initializing a second SoundPool but does not remove
the libGDX dependency.

Further opportunities, not applied here:

- Release code shrinking is disabled. The previous APK's uncompressed DEX alone
  occupied 6,686,744 bytes. R8 could remove unused libGDX/Kotlin code, but savings
  and reflection/JNI compatibility require a separate minified-release test.
- A universal APK includes about 707 KB of native libraries across four ABIs.
  ABI-specific distribution could omit approximately 514–547 KB per download,
  at the cost of managing separate artifacts. All four ABIs remain supported.
- The `:bot` dependency backs the dev-mode idle pilot and prediction code; it is
  not safely removable as an unused dependency. Splitting that feature into a
  debug-only variant would be a product/build choice.

## Reproduce

```sh
export TMPDIR="$PWD/.build-tmp" UV_CACHE_DIR="$PWD/.build-tmp/uv-cache"
export ANDROID_HOME=/home/user1/android-sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
uv run --no-project tools/launch-time/motion_assets.py
# Optional: regenerate the debug-only video (remove the old generated MP4 first).
# uv run --no-project tools/launch-time/motion_assets.py --video
JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR" ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
uv run --no-project tools/launch-time/benchmark.py --serial emulator-5560 \
  --output captures/launch-animation/final --runs 3 --record --world 0
```

Install each APK before running its benchmark; do not build, run instrumentation,
or execute another launch benchmark concurrently with a measured recording.

## Platform references

[Android AnimatedVectorDrawable](https://developer.android.com/reference/android/graphics/drawable/AnimatedVectorDrawable)
documents RenderThread execution and synchronization limits.
[SplashScreenView](https://developer.android.com/reference/android/window/SplashScreenView)
provides the system animation start.
[Android splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen)
describes the system starting window and icon animation.
