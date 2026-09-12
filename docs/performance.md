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
and frame-build wall-time p50/p95/p99/max, render-thread CPU time, frames over 25/50 ms, burst execution
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

## Continued work after 2.1

Version 2.1 (`v2.1`, commit `11e3539`) contains the first optimization pass above.
The following work remains on `performance` for the next release:

- World boxes and coin prisms reject conservative bounds outside the camera
  before assembling and uploading vertices. Ground bounds include both hill
  heights, and spun shapes include their rotated extents. No draw distance or
  geometry detail is reduced.
- A fixed 512-entry orientation cache shares box trigonometry and face lighting
  across frames, especially for the cave's crystal clusters. Collisions replace
  one entry; memory use cannot grow with distance.
- The outer coin and its raised heart share trigonometry, face lighting and glint
  calculations. Each still uses its own dimensions, color, fog and opacity.

The new `five-boosts` mode sends all five requests through the real opening boost
path and verifies that all five applied. It retains normal difficulty progression.
Use `-e world 2` to hold Lava Caves, `1` for Neon City, or `5` for Deep Space.
`threadCpu` reports `Debug.threadCpuTimeNanos()` deltas between GL callbacks; it
excludes driver/scheduler waits but includes the benchmark callback itself.

Thirty-second measurement windows in Lava Caves (35 seconds including warm-up):

| Build / scenario | GL-thread CPU p50 / p95 (ms) | Frame p95 / p99 / max (ms) | Frames >25 ms |
| --- | --- | --- | ---: |
| 2.1, five boosts | 9.33 / 11.19 | 18.81 / 20.06 / 23.26 | 0 / 1796 |
| Continued optimization, five boosts | 8.82 / 10.95 | 18.98 / 20.58 / 26.91 | 2 / 1796 |
| 2.1, maximum-speed cruise | 7.71 / 9.30 | 18.81 / 19.91 / 27.52 | 1 / 1795 |
| Continued optimization, maximum-speed cruise | 7.23 / 9.20 | 18.97 / 21.94 / 30.55 | 2 / 1796 |

Median render-thread CPU fell about 5–6% in these runs. Frame timing remains
vsync-limited with variable outliers; these results do not establish a reduction
in worst-case latency. Procedurally generated content differs between runs.
Culling alone was roughly neutral in median CPU time; the lower CPU medians
were recorded after adding the lighting caches.

`BatchVisibilityTest` compares exact framebuffer bytes at twelve camera/hill
positions. The reference disables culling and recomputes orientation lighting
for every object; the optimized render must match with caches and culling on.
The fixture covers screen edges, behind-camera and far-plane rejection, rotated
boxes, sloped ground, and paired coins with different sizes, colors and fog.

The final renderer passed all **34 device regression tests**, plus debug/test
APK builds, the normal release build, and lint (only the existing target-SDK
advisory). A two-minute five-boost cave run measured **6,883 frames** after
warm-up: p95 / p99 / max **19.07 / 21.63 / 24.87 ms**, no frames over 25 ms,
no GC, and at most 18 track rows. It reached 40.7 units/s after a generated
pickup. Sound, haptics, display resolution and device governors were unchanged.

The following two-minute dense Second Wind phase measured **6,884 frames**:
p95 / p99 / max **19.20 / 20.63 / 23.59 ms**, no frames over 25 ms, one GC,
and at most 18 track rows. It executed **60 Second Winds**; measured burst
execution was **4.01 ms median / 5.86 ms maximum**.

Additional final checks (each window excludes five seconds of warm-up):

| World / scenario | Measured frames | Frame p95 / p99 / max (ms) | Frames >25 ms | GC |
| --- | ---: | --- | ---: | ---: |
| Neon City, five boosts (45 s) | 2395 | 18.98 / 20.95 / 25.13 | 1 | 0 |
| Deep Space, jet (35 s) | 1797 | 19.33 / 23.73 / 28.28 | 11 | 0 |
| Deep Space, five lanes (35 s) | 1793 | 19.05 / 21.13 / 27.83 | 5 | 0 |
| Deep Space, hills (35 s) | 1796 | 18.83 / 20.00 / 27.51 | 1 | 0 |
| Deep Space, advanced time/distance (35 s) | 1796 | 18.94 / 21.79 / 29.19 | 2 | 0 |

All additional phases passed, with no frames over 50 ms and at most 18 resident
track rows. The jet phase reached 52.5 units/s. The phone's progress, scores and
settings were restored from the pre-test backup and verified by SHA-256; the
tested debug APK remains installed.

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

## 2.2 candidate: elapsed time, 90 Hz and GPU geometry

This local pass requests the best same-resolution display mode up to 90 Hz through
[Android's Surface frame-rate API](https://developer.android.com/media/optimize/performance/frame-rate).
It does not change global display settings. The old 35 ms delta clamp discarded
elapsed time during stalls. `FrameStepper` now consumes raw elapsed time in small
collision-safe slices, retains excess debt after long stalls, and clears debt on
pause/resume. Deliberate impact slow motion remains separate. Actual-game tests
at 90/60/30/20/8 rendered FPS all cover 300 units in ten seconds at capped speed
and consume ten seconds of a power-up timer. Normal running already caps at
30 units/s; jetpack speed caps at 52.5. Development boosts now cycle through ten.

Pickup bobbing, spinning, flame and bubble pulse phases no longer depend on the
row's scrolling position. Their animation rates therefore stay stable as travel
speed rises. A double accumulator also avoids accumulating Float clock error
through long runs.

World boxes and shards now use GLES 3 instancing: static geometry stays on the
GPU, while each visible object uploads a compact transform/color record. Face
lighting and packed-color quantization match the previous appearance. The GLES 2
fallback keeps CPU batching, face compaction and cached orientations. No particle
pool, draw distance, resolution, antialiasing or object detail was reduced.
Framebuffer tests compare both optimized CPU and GPU paths with the unculled CPU
reference across twelve camera/hill and twelve tumbling-particle frames.

### Pixel 7a

Android 17, 1080×2400, normal debug APK, USB charging at **4% battery** during the
baseline and this pass. Do not extrapolate one device's battery/thermal policy to
other phones. All figures below are unprofiled runs in Lava Caves. Profiling is
run separately because even sampling materially changes timings.

| Scenario | Before: GL CPU p50 / p95 (ms) | Candidate: GL CPU p50 / p95 (ms) | Candidate frames / measured seconds | Frame p95 / p99 / max (ms) |
| --- | --- | --- | --- | --- |
| Five opening boosts | 15.65 / 17.92 | 7.81 / 9.83 | 3609 / 40 | 12.79 / 13.95 / 21.88 |
| Dense Second Wind every 2 s | 16.88 / 28.62 | 8.34 / 9.85 | 3612 / 40 | 12.62 / 14.31 / 17.95 |
| Jetpack, 52.5 units/s | — | 7.80 / 8.63 | 3615 / 40 | 12.72 / 13.52 / 16.39 |
| Five-lane bonus | — | 7.73 / 8.66 | 3613 / 40 | 12.69 / 14.47 / 27.40 |
| Hills | — | 7.98 / 10.64 | 3612 / 40 | 12.53 / 13.56 / 16.04 |
| Advanced clock/distance | — | 7.88 / 8.89 | 3615 / 40 | 12.61 / 13.30 / 16.96 |

The first two baseline windows measured 62.7 and 50.2 FPS; the candidate sustains
about 90 FPS in all six windows. This is not a claim that every frame meets
11.1 ms: one five-lane frame exceeded 25 ms, and smaller scheduling outliers
remain. GL-thread CPU p95 is below the 90 Hz budget in every listed scenario.
Dense smash execution is 3.15 ms median / 3.55 ms maximum. Resident rows remain
bounded at 19 or fewer. These renderer comparisons used the static protected
harness before the playing-bot default was added. Generated content varies;
the dense obstacle fixture is fixed. Raw artifacts: `captures/performance-pass/`
`pixel-baseline` and `pixel-final-gpu`.

### Playing-bot performance tests

The harness now shares `LiveBotDriver` with normal demos. It sends real Android
flicks, pursues original coin lanes even with a magnet, and plans surviving routes.
A debug-only crash observer protects long test runs without spending Second Wind
stock. Each continuous fatal collision episode increments `protectedHits` and
shows a red warning. A regression proves collisions still reach the observer and
that removing it immediately restores normal collision/stock behavior. Release
builds cannot enable this protection. Normal bot demos keep fatal collisions.

`RunPerformanceTest` defaults to the playing bot; `-e bot false` selects the
labeled static rendering reference. Dense bot fixtures distribute their coin
lines across lanes. Logs distinguish `bot=true/false`; `RUN_BOT` reports actions
and acknowledgements. Planner work runs off the GL thread but shares the process
heap, so its substantial search allocations and GC are included in bot-enabled
runs. Do not compare their allocation totals with game-only rendering totals.

At normal Motorola clocks, 35-second cave phases (30 seconds measured) gave:

| Bot scenario | GL CPU p50 / p95 (ms) | Frame p95 / p99 / max (ms) | Frames | Protected collisions |
| --- | --- | --- | ---: | ---: |
| Five boosts | 5.67 / 8.02 | 18.84 / 20.29 / 24.76 | 1796 | 0 |
| Dense Second Wind | 6.62 / 9.42 | 19.10 / 20.53 / 23.04 | 1796 | 0 |

Both sustained about 59.9 FPS, with no frames over 25 ms. These are controller
runs, not guarantees that the bot survives every generated course unaided.

### Motorola with reduced CPU clocks

The measured CPU ceilings were **1,036.8 MHz / 1,094.4 MHz** (policy0 /
policy4), about 57% / 61% of their normal 1,804.8 MHz maximum. Readbacks of
both limits and actual current frequencies were sampled throughout each run;
all samples inside the comparison windows stayed at or below those ceilings.
Normal power services, governors, GPU settings and thermal protection remained
active for these measurements. Original limits were restored and verified.

Each phase measured twenty seconds after five seconds of warm-up:

| Scenario | Baseline GL CPU p50 / p95 (ms) | Candidate with playing bot p50 / p95 (ms) | Candidate frame p95 / p99 / max (ms) | Protected collisions |
| --- | --- | --- | --- | ---: |
| Five boosts | 9.47 / 10.91 | 7.74 / 10.02 | 19.02 / 22.66 / 26.86 | 2 |
| Advanced time/distance | 8.33 / 10.43 | 6.20 / 8.11 | 18.68 / 20.96 / 28.08 | 3 |
| Dense Second Wind | 10.75 / 14.04 | 7.37 / 9.05 | 18.59 / 20.90 / 25.73 | 0 |

Every window measured 1,198 frames (about 59.9 FPS). The candidate includes the
playing bot's additional CPU and GC work; the baseline is the earlier static
harness, so these are not identical-controller comparisons. The bot's protected
collisions were logged and displayed, not counted as successful unaided play.
Artifacts include raw frame logs, epoch timestamps, sampled clocks and restoration
readbacks in `moto-60pct-baseline` and `moto-60pct-current`.

Earlier attempts at lower limits were overridden by automatic boosts; another
attempt temporarily suspending power hints timed out. Those trials are excluded.
The timeout watchdog restored both power services, the input-boost setting and
clock limits. Subsequent successful measurements left those services active.

The Pixel was disconnected by the user after its completed six-scenario run;
remaining validation uses the Motorola. The candidate remains local and unpushed.

Final candidate validation: **45 Motorola regression tests passed**, including
GPU/CPU framebuffer equivalence, terrain mesh continuity, frame-rate-independent
distance/timers, economy pity persistence, bubble cooldown, dev boost cycling,
collision observer behavior, shop rendering, UI animations and real bot-model
parity. Debug/test and normal unsigned release builds passed. Lint reports zero
errors and only the existing target-SDK advisory. Release DEX contains no bot or
instrumentation controller. Both phones' original preferences were restored and
verified; Motorola CPU limits, boost settings and power services were restored.

Permanent-upgrade prices, mystery-box rewards and their 15–25-hour target model
are documented in [progression.md](progression.md). The candidate identifies as
version 2.2 / code 9; no main-game release tag or Git push is part of this pass.
