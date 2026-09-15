# Launch-time optimization

## Target and workspace

Show the intro cube as close to the launch action as Android allows. Work takes
place on `optimizing-launchtime` in
`~/Worktrees/cube-run/optimizing-launch-time`, starting from `4a314a9`.
Measurements and recordings stay locally in `captures/launch-time/` (ignored).

## Progress — 2026-09-15

1. Created the isolated worktree and built the unchanged debug APK.
2. Inspected the startup path. The platform launch screen is explicitly blank;
   it remains until the full GL renderer, particle pools, world and player have
   initialized and rendered. HUD and bubble shader loading are already deferred.
3. Started the API 35 Pixel 5 emulator, x86_64, 1080×2340, 60 Hz, host GPU,
   from `eve-pool-1` in read-only mode. No physical-device testing is involved.
4. Established repeated process-cold baselines and recorded visible startup.
5. The first installed baseline launch reproduced the reported delay: 1,022 ms
   application-to-first-cube, 1,279 ms Android TotalTime. Renderer batches alone
   consumed 245 ms. Subsequent launches benefit from warmed device caches.
6. Added a six-path vector of the intro's starting pose to Android's starting
   window. The platform can display it before the app process is ready. A native
   cover keeps that pose visible until the first GL buffer has swapped, then
   fades into the equipped cube over 80 ms. The cover also supports Android 9–11.
7. Moved scenery/coin/pill/crystal/particle rendering resources and bubble shader
   preparation behind the first real cube frame. Preparation uses a 4 ms CPU
   budget between cube frames (an individual driver call can exceed it). The
   opening clock holds at zero while preparation finishes. Direct run starts
   finish any remaining preparation, and disposal tolerates partial startup.
8. Disabled libGDX's unused audio backend; the game's existing SoundFx audio and
   haptics remain enabled. Disabled the unused rotation-vector sensor too.
9. Visual inspection caught clipping by Android's 192 dp circular icon mask.
   The starting projection now fits it, with matching dp geometry in GL. The
   system cube has a fixed pink colour and crossfades to the equipped skin;
   Android's pre-process starting window cannot read saved wardrobe state.
10. Lifecycle checks passed. Broader regressions exposed an unfinished intro
    applying its pose after returning from the shop. Navigation now finishes
    the opening before handing the camera to a preview stage.
11. Added emulator tests for a visible first GL cube before scenery preparation,
    equipped skin preservation, early start/disposal, and the platform icon mask.
    The existing shop framebuffer fixture now explicitly finishes the intro
    before comparing settled menu frames, instead of treating an 800 ms sleep
    as proof that the 1.75-second intro has completed.
12. Debug/release/test builds and lint completed. All 25 selected emulator
    regression tests passed. Final repeated measurements follow below;
    exploratory runs with recording or overlapping build load are excluded
    from the unrecorded timing comparison.

## Results

The cube appears **about 80% sooner**: median **133 ms instead of 680 ms** in
three separate screen recordings per build. It is already visible during the
system launch transition, before `Application.onCreate`. Android still owns
process scheduling and the launch animation, so this is not literal zero time.

| Metric | Baseline median (range), ms | Optimized median (range), ms |
| --- | ---: | ---: |
| Request → visible cube, recorded, 3 launches | 680 (661–743) | 133 (130–139) |
| Application → real cube GL frame, unrecorded, 10 launches | 331 (306–341) | 296 (284–302) |
| Android `am start -W` TotalTime, unrecorded, 10 launches | 370 (366–383) | 376.5 (350–400) |

The first row measures pixels, including Android's transition and process
startup. The second row measures renderer submission, not display presentation;
the new callback waits for the following GL frame, after buffer swap. The
third row is Android's activity-display metric: it did **not** improve. Showing
the starting cube before the process is ready is the main perceived improvement.

The real equipped cube still needs roughly 300 ms of app initialization on this
warmed emulator. A fixed pink native cube displays in the meantime and fades
into it. The initial pose is slightly smaller to fit Android's circular splash
icon mask. The 1.75-second animated opening and rendering quality are retained;
its clock stays still during renderer preparation. No sound, haptics, antialiasing,
resolution, draw distance or game detail was removed.

The emulator uses the API 35 Google APIs x86_64 image, Pixel 5 AVD geometry,
1080×2340 at 440 dpi, 60 Hz, host GPU, default animation settings. These are
process-cold launches with warmed OS/driver caches, not device reboot timings.
The very first baseline install/launch took 1,022 ms application-to-cube and
1,279 ms Android TotalTime; that single observation is not mixed into the
repeat-run comparison. Emulator results are not physical-device guarantees.

### Evidence

- [Committed measurements and APK hashes](launch-time-reference/measurements.json)
  contain every final unrecorded run and all six visible-frame measurements.
- Local raw traces: `captures/launch-time/{baseline,final}-isolated/`.
- Local videos and visible-frame results:
  `captures/launch-time/{baseline,final}-video/`.
- Visual handoff contact sheet: `captures/launch-time/final-video/handoff.png`.
- Side-by-side video: `captures/launch-time/comparison.mp4`, using the median
  recorded trial from each build and aligning both device-clock request times.
- Passing test output: `captures/launch-time/regression-verified.txt`.
- Build logs: `.build-tmp/final-build.log` and `.build-tmp/final-tests-build.log`.

### Validation

**25 tests passed:** launch lifecycle (4), startup renderer (3), timing/balance
(6), shop framebuffer/navigation (4), touch input (5), collision/revive (3).
This covers early tap-to-skip without starting a run, automatic restart,
background/resume, recreation, rapid close/relaunch, partial GL disposal, the
equipped cube, and the native icon's clipping bounds.

Debug, test, and unsigned release APK builds passed. Android lint reports zero
errors and two existing advisories (target API level and the newer back-callback
manifest attribute). API 28–32 fallback resources compile but were not run on an
older emulator; only the installed API 35 image was tested.

## Reproduce

Keep scratch output inside the worktree:

```sh
mkdir -p .build-tmp
export TMPDIR="$PWD/.build-tmp"
export UV_CACHE_DIR="$PWD/.build-tmp/uv-cache"
export ANDROID_HOME=/home/user1/android-sdk
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/.build-tmp"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
adb -s emulator-5560 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
uv run --no-project tools/launch-time/benchmark.py --serial emulator-5560 --output captures/launch-time/repeat --runs 10
uv run --no-project tools/launch-time/benchmark.py --serial emulator-5560 --output captures/launch-time/video-repeat --runs 3 --record
uv run --no-project tools/launch-time/analyze_video.py captures/launch-time/video-repeat/launch-1.mp4 --trace captures/launch-time/video-repeat/trace-1.txt
adb -s emulator-5560 shell am instrument -w -e class cube.run.game.LaunchRendererTest,cube.run.game.LaunchLifecycleTest,cube.run.game.TimingAndBalanceTest,cube.run.game.ShopRenderingRegressionTest,cube.run.response.TouchInputTest,cube.run.game.SmashRegressionTest cube.run.test/androidx.test.runner.AndroidJUnitRunner
```

Wait for builds/tests to finish before measuring. The video analyzer reads
screenrecord's Winscope metadata to align individual presented frames with the
device-clock launch-request log. It requires at least 100 pixels above brightness
60 in the cube's centre region, then reports the previous and first visible frame
times. Frame intervals near detection were 13–33 ms. Its dark, empty launcher
centre assumption was checked visually on this AVD; inspect the detected frames
when using a different launcher or wallpaper.

Regenerate the vector after changing the starting pose with
`uv run --no-project tools/launch-time/cube_drawable.py`. Its projection and
`CubeOpening` must use the same reference height of 640 dp.

## Measurement rules

- Force-stop before each cold launch; allow the emulator to settle after boot.
- Keep the same APK type, emulator resolution, GPU and animation settings.
- Distinguish the first visible cube from the first rendered game frame and
  completion of the intentional 1.75-second intro animation.
- `CUBE_START` currently measures from application initialization, excluding
  process creation. Android `am start -W` and screen recordings complement it.
- Do not claim literal zero latency: Android still schedules process creation
  and composes the launch transition.

## Platform reference

[Android splash-screen documentation](https://developer.android.com/develop/ui/views/launch/splash-screen)
describes the system-managed starting window and the Android 13+
`windowSplashScreenBehavior=icon_preferred` setting.

[AOSP screenrecord source](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/master/cmds/screenrecord/screenrecord.cpp)
defines the elapsed-realtime frame metadata used by the video analyzer.
