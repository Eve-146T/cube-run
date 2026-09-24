# Preserving full visuals under CPU pressure

The next optimization round and severe Moto clock controls are documented in
[severe-clock-performance.md](severe-clock-performance.md), including the limits
on the available hardware evidence.

This experiment is based on jackpot (`0ff04de`, the shared experiment base). It
reduces work required by the existing scene. It does not change the frame-rate
request, resolution, antialiasing, draw distance, particles, coin geometry,
collectible frequency, gameplay speed, timers, or input handling.

## Implementation

Coins previously rebuilt and uploaded all 72 vertices of each twelve-sided
prism every frame. The GLES3 path keeps that exact mesh on the GPU and uploads
16 floats per prism (64 bytes instead of 1,152 bytes). The shader applies the
same transform, face lighting, bright face floor, directional glint, fog and
packed-color quantization. Both layers of every coin remain present, with the
same triangle order. This cuts coin vertex upload size by **94.4%** and removes
CPU vertex expansion and per-face color computation from that path. It does not
reduce geometry submitted to the GPU.

GLES2 retains the existing CPU renderer. The Matrix wire transition also uses
the CPU renderer so the original visible edges, blackened faces and blending
are retained. The decision is made when the first coin is submitted: the game
sets the Matrix blend after the batch begins. Returning from the GPU path
invalidates cached CPU lighting, including when the first coin has the same yaw.

Live achievement evaluation no longer constructs eleven UI snapshots, their
claimed-tier lookups, preference-key strings or a preferences editor on every
unchanged evaluation. An editor is created only when a tier actually changes.
Unlock timing, persistence, notification coalescing and claiming behavior are
preserved. Score milestone thresholds now reuse their fixed array.

## Scope of hardware evidence

The requested cold Pixel 7a at a genuine 5% charge is unavailable in this
session. The attached Moto G7 Power reported 61% charge, USB power and 28.3°C
battery temperature. The successful baseline is retained in
[moto-baseline.txt](low-battery-reference/moto-baseline.txt). It measures existing
jackpot behavior, not the candidate.

Another active task installed a different Cube Run APK on that phone during two
initial attempts. Android ActivityManager identified `installPackageLI` as the
reason the process was killed; a sampling trace contained classes absent from
this branch. Those attempts and that profile were excluded. After one clean
baseline completed, further phone installs stopped to avoid disrupting the
other task. Candidate comparisons therefore use an emulator and cannot establish
cold battery performance on the Pixel or the Moto.

No battery, thermal, CPU governor, refresh-rate, or system power settings were
changed. A battery-percentage override alone would not reproduce a cold cell's
voltage or the Pixel's power-management behavior.

## Verification

The candidate retains exact GPU/CPU pixel parity in the tested fixtures. Runtime results and their limits follow.

### Correctness gates

- Debug APK and instrumentation APK built successfully; Android lint: zero errors,
  20 existing warnings, none in changed files.
- **41 regression tests passed**: 26 achievement progression/persistence tests,
  the new coin comparison, two existing batch comparisons, three Matrix effect
  tests, four shop-rendering tests, four launch lifecycle tests, and the actual
  game's low-frame-rate distance/timer test.
- The dedicated coin comparison covers 96 combinations of yaw, face glint, fog,
  opacity, terrain and Matrix strength. Every resulting framebuffer is compared
  byte for byte with the original CPU renderer. It also verifies visible output,
  actual GPU path selection and switching back to CPU at an unchanged yaw.
- That test initially caught a one-pixel edge difference: the GPU grouped world
  position additions differently. Matching the original arithmetic order fixed
  it; the exact assertion and all fixture states remain in place.

### Repeatable comparison

`tools/performance/compare.py` runs the existing real-game performance harness
with track seed 73, scenery seed 74, world 2 and section 56 (dense coin field).
The optional `repeatable=true` harness mode prevents shuffled world transitions.
Each phase lasts 20 seconds, excluding its first five seconds. The app runs at
its normal frame-rate request with full visuals, sound enabled and a labeled
static protected player. This exercises cruise, hills, jet and dense Second Wind
bursts without allowing collisions to end the measurement. It is a renderer
workload, not evidence of successful unaided play.

Two repetitions alternate baseline → candidate, then candidate → baseline.
Each run starts with fresh disposable Cube Run saves. The runner verifies the
installed APK's SHA256 after every run; a competing install invalidates the run.
Process allocation totals include the instrumentation's frame/audio CSV logging.
The GL-thread CPU timer excludes time spent asleep; `render_ms` includes driver
waits and should not be read as pure CPU cost.

The emulator's unchanged configuration is 540×1170, density 220, GLES 3.1 using
the Android NVIDIA RTX 3090 translator. This is an existing emulator display
configuration, not a quality change introduced by this branch. Physical display
metadata is 1080×2340 / 440dpi. Frame pacing is dominated by the emulator's 60Hz
presentation, while host contention can affect outliers. The first baseline
also overlapped the end of incremental lint; the reversed second pair is kept
so the comparison does not rely on that first run alone.

This moves coin transform/lighting arithmetic onto the GPU while reducing CPU
work and transfer volume. A device already limited by GPU processing may benefit
less than a CPU-limited device; the actual cold Pixel still needs validation.

### Results

Each cell shows the range across the two runs, in milliseconds. These are ranges
of per-run percentiles, not pooled percentiles. Raw results and APK hashes are in
[emulator-results.json](low-battery-reference/emulator-results.json).

| Scenario | Baseline GL CPU p50 | Candidate GL CPU p50 | Baseline GL CPU p95 | Candidate GL CPU p95 |
| --- | ---: | ---: | ---: | ---: |
| cruise | 2.41–2.80 | 1.85–1.92 | 3.91–10.25 | 2.95–7.73 |
| hills | 2.24–2.46 | 1.62–1.77 | 2.88–3.41 | 2.06–7.29 |
| jet | 1.72–1.73 | 1.58–1.58 | 2.30–2.31 | 1.95–1.97 |
| second-wind | 1.67–2.18 | 1.65–1.67 | 2.02–3.31 | 2.10–6.54 |

The dense-coin cruise median used **23–31% less GL-thread CPU** in the two paired
runs; hills used **28% less** in both. Jet medians improved about 8–9%. Second
Wind varied from almost unchanged to 23% lower. Frame medians remained about
16.67ms; this does not demonstrate an FPS increase on the emulator.

Tail behavior was mixed: candidate hill and Second Wind CPU p95 were worse in
one run. Baseline cruise also had a 10.25ms CPU p95 outlier window. Candidate
frame p95 ranged 16.95–17.54ms and frame p99 17.42–19.15ms. These results support
lower routine CPU work, not a universal improvement in stalls. Allocation totals
were similar with this fresh-save, instrumentation-heavy workload; no whole-game
GC reduction is claimed from it. The achievement optimization is covered by the
progression tests rather than an allocation claim from this run.

### Sampling and normal app inspection

Separate 20-second baseline/candidate sampling runs completed successfully. Their
summaries are [baseline-profile.txt](low-battery-reference/baseline-profile.txt)
and [candidate-profile.txt](low-battery-reference/candidate-profile.txt).
Baseline samples include prism expansion, face lighting and JNI buffer copying;
those paths shrink or disappear from the candidate's largest sampled costs.
Both profiles are dominated by emulator EGL presentation. The sampler is coarse
and affects execution, so traced runs are excluded from the percentile table.

The final normal APK was launched with fresh saves and no benchmark overlay or
protection. Inspected the cube opening, menu, run start, spinning two-layer coins,
jump, lane change and collision effects in screenshots and a 16-second recording.
The [menu](low-battery-reference/menu.png), [run](low-battery-reference/run.png)
and [motion sequence](low-battery-reference/motion-sheet.jpg) show the retained
styling and geometry. A fresh independent visual reviewer found no actionable
visual defects in these captures. The local full recording is
`captures/low-battery/normal-motion.mp4`; sampled images are not a frame-pacing
measurement. No AndroidRuntime exceptions were logged.

The final candidate has not been tested on a cold Pixel 7a at 5%, and the measured
CPU improvement must not be presented as proof that the reported device stall is
resolved. The separate branch is ready for that hardware follow-up without any
visual-quality or frame-rate reductions.

## Tap-accessible debug course

The debug APK now has **TEST PERFORMANCE** below the main menu's start hint.
Tap it, choose **DENSE COIN COURSE**, then tap to start on the returning menu.
The course repeats MOTHERLODE in Lava Caves with seed 73; entering a run resets
the seed, so time spent on the menu cannot change the layout. It keeps the
selected cube and ordinary controls, with all scene geometry and visual effects.
No magnet, skin or special ability is granted. The developer bank is available
while testing, but the original bank/developer setting is restored on exit.

The test course suppresses surprise upgrades, bonus portals and biome changes.
Restart keeps the selection. To leave, use **Pause → Menu → Test Performance →
Play Normally**; selecting another section or the Red Pill course also exits
performance mode. The existing menu relaunch disposes the previous GL resources.
Selection is process-scoped and does not survive a fresh app process. Release
APKs do not show this shortcut or enable this course mode.

### Shortcut verification

Debug app/test APK assembly and Android lint passed ([build log](low-battery-reference/test-course-build.txt)).
Both focused `PerformanceCourseTest` checks passed on emulator-5560
([test log](low-battery-reference/test-course-instrumentation.txt)): fixed dense
coin layout, no course pickups/portals, unchanged equipment, restored bank, and
ordinary portals and biome changes after leaving the course. The renderer was
unchanged by this follow-up, so the earlier 41 regression checks were retained.

Inspected the actual app at 540×960, density 240: visible shortcut, course picker,
run start, lane change, pause/retry, normal-play return, Red Pill exit and another
section exit. The original bank returned to zero after Play Normally. The
[menu](low-battery-reference/course-menu.png),
[active picker](low-battery-reference/course-active-picker.png) and
[restored menu](low-battery-reference/course-restored-menu.png) show the compact
layout; a fresh independent reviewer found no actionable defects. Inspected
14-second start/menu and 8-second continuous-run recordings, with sampled
[start sequence](low-battery-reference/course-motion-sheet.jpg) and
[run motion](low-battery-reference/course-running-motion.jpg). Local full videos
are under `captures/low-battery/performance-course*.mp4`.
