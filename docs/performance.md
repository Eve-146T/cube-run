# Run performance

The `performance` branch targets high-speed runs and Second Wind on the Moto G7
Power (Snapdragon 632, 720 × 1520 at 60 Hz, rooted LineageOS / Android 15).

## Changes

- Second Wind clears its original speed-dependent recovery zone, with one sound /
  haptic impact for the event. Nearby rows shatter and distant rows dissolve with
  smaller particle bursts. Platforms, pads, decoration and rows outside the zone
  retain their original behavior.
- Android vibration calls run on a dedicated worker. Pending feedback is bounded
  and favors stronger effects, so coin ticks cannot build up behind an impact.
  Common vibration effects are created once, including Android 9 fallbacks.
- The shard pool replaces particles in constant time when full and caps oversized
  burst requests before generating particles that would immediately be replaced.
- Terrain sampling uses a primitive float interface. Lanes, kerbs and land at the
  same depth share their hill-height samples and slope lighting within each frame.
- Score and coin pulses cache their drawing for the scale animation, retaining
  window repainting without re-rasterizing outlined text every animation frame.

## Device benchmark

`RunPerformanceTest` uses the real game, GL surface, HUD, sound and vibration.
It raises difficulty to its normal maximum and prevents unattended collisions
from ending the run. The Second Wind phase rebuilds a dense obstacle fixture and
invokes the real revive effect every two seconds. A separate regression test
enters that path through an actual collision and verifies stock consumption,
clearance bounds and non-solid obstacles.

Each phase excludes five seconds of warm-up. `RUN_BENCH` records frame interval
and frame-build wall-time p50/p95/p99/max, frames over 25/50 ms, burst execution
times, maximum resident rows, speed, ART allocations and GC count. Frame-build
wall time includes driver waits; it is not a pure CPU utilization measurement.
Normal generated sections vary between runs; the dense smash fixture is fixed.
The benchmark itself allocates measurement samples, included in ART totals.

```sh
mkdir -p captures/tmp
export TMPDIR="$PWD/captures/tmp"
./gradlew -Djava.io.tmpdir="$TMPDIR" assembleDebug assembleDebugAndroidTest lintDebug --offline
adb -s ZY323NNKTB install -r app/build/outputs/apk/debug/app-debug.apk
adb -s ZY323NNKTB install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s ZY323NNKTB shell am instrument -w \
  -e class cube.run.game.RunPerformanceTest \
  cube.run.test/androidx.test.runner.AndroidJUnitRunner
adb -s ZY323NNKTB logcat -d -s RUN_BENCH:I '*:S'
```

For sustained coverage, add `-e seconds 120 -e modes
cruise,hills,second-wind,jet,wide,late` to the instrumentation command. `jet`
exercises the 52.5-unit/s jetpack ceiling, `wide` holds the five-lane bonus,
and `late` advances the game clock to 10,000 seconds and distance to 300,000.
These are test-only controls; release gameplay has no benchmark shortcuts.
The test activity can render over a locked test phone without changing its lock
configuration. Back up game preferences before running device tests and restore
them afterward: tests do not bank run scores, but ordinary pickup collection can
increase bubble stock.

## Baseline

Baseline commit: `2cc97bd` (same debug build settings, sound and haptics enabled).
Twenty-second measurement windows after warm-up:

| Scenario | Frame p50 / p95 / p99 / max (ms) | Frames >25 ms | ART allocated | GC |
| --- | --- | ---: | ---: | ---: |
| Maximum-speed cruise | 16.72 / 19.30 / 23.60 / 26.64 | 3 / 1198 | 35.0 MB | 1 |
| Rolling hills | 16.75 / 18.80 / 19.84 / 21.73 | 0 / 1198 | 33.5 MB | 2 |
| Repeated Second Wind | 16.67 / 19.43 / 22.93 / 42.62 | 10 / 1192 | 37.1 MB | 2 |

Dense Second Wind execution took **25.80 ms median, 26.74 ms maximum** before
rendering. An earlier baseline run measured 25.10 / 27.79 ms. Consolidating
feedback and bounding particle work first reduced this to 8.06 ms median.

## Optimized comparison

Same device and finalized harness, twenty-second measurement windows:

| Scenario | Frame p50 / p95 / p99 / max (ms) | Frames >25 ms | ART allocated | GC |
| --- | --- | ---: | ---: | ---: |
| Maximum-speed cruise | 16.70 / 18.79 / 21.00 / 27.60 | 3 / 1198 | 2.8 MB | 0 |
| Rolling hills, including a collected jetpack | 16.73 / 18.92 / 20.22 / 26.96 | 4 / 1197 | 2.8 MB | 0 |
| Repeated Second Wind | 16.65 / 19.40 / 20.89 / 22.78 | 0 / 1198 | 3.3 MB | 0 |

Dense Second Wind execution is **5.30 ms median, 5.61 ms maximum**, about 79%
less median work. Its largest measured frame interval falls from 42.62 to
22.78 ms. Per-window allocations fall by about 91–92%. Both builds normally
reach the 60 Hz display limit; the improvement is in collision spikes and
allocation pressure, not a claim of higher-than-display FPS. The hill windows
contain different generated content; the optimized run reached 52.5 units/s
after collecting a jetpack, while the baseline hill window stayed at 30 units/s.

## Sustained checks

Ninety-second phases (85 seconds measured after warm-up):

| Scenario | Measured frames | Frame p95 / p99 / max (ms) | Frames >25 ms | Maximum rows | GC |
| --- | ---: | --- | ---: | ---: | ---: |
| Second Wind, clean repeat | 5089 | 19.27 / 20.80 / 27.18 | 5 | 19 | 0 |
| Five lanes | 5089 | 18.92 / 20.56 / 24.39 | 0 | 15 | 0 |
| Hills | 5089 | 18.77 / 19.63 / 28.67 | 1 | 18 | 0 |
| Advanced time and distance | 5088 | 18.92 / 20.77 / 27.80 | 1 | 18 | 0 |

The clean repeated-smash run executed 45 Second Winds: **4.54 ms median,
9.95 ms maximum**. These runs sustained about 59.9 frames/s; occasional small
scheduling outliers remain, so this is not a guarantee that every frame meets
16.7 ms. Active track rows remained bounded.

A separate clean 60-second jetpack run (55 seconds measured) reached **52.5
units/s** for 3,293 measured frames: frame p95 / p99 / max **19.16 / 22.40 /
27.91 ms**, 12 frames over 25 ms, none over 50 ms, no GC, and at most 18 rows.

The initial sustained Second Wind and jetpack phases included intrusive
`dumpsys meminfo` / screenshot collection and recorded isolated 145 / 80 ms
frame intervals. Those diagnostic windows are excluded from the clean figures
above. Repeating Second Wind without those probes removed the large outlier.
Read logcat during timing runs; collect screenshots, heap diagnostics and CPU
profiles separately.

## Regression validation

- All **33 device regression tests passed** on the optimized APK: real collision /
  revive behavior, particle-pool bounds, terrain meshes and cache invalidation,
  shop framebuffer comparisons, rapid and overlapping Android animations, and
  track balance / pickup rules.
- Debug APK, test APK, and release APK builds passed. Android lint reported no
  errors and only the existing target-SDK advisory.
- Repeated-pulse testing caught a temporary hardware-layer retention issue during
  optimization; the final implementation passes the cleanup test as well as the
  existing entrance / exit / navigation regression tests.
- Measurements use the normal debug APK with sound and haptics enabled. Root was
  used for CPU sampling and backing up / restoring the test phone's preferences;
  CPU/GPU governors, display resolution and quality settings were not changed.
