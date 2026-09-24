# Severe-clock optimization round — 2026-09-24

Worktree: `low-battery-performance`. Baseline: `de2e823`.

## Kept optimization

Visible particles now build their rotation matrix directly, write the translation,
and apply scale. The old path first initialized an identity matrix, transformed
its translation and composed it with the rotation. The direct path avoids those
redundant operations for each visible shard. Particle physics, geometry, lighting,
draw order and the GPU/CPU rendering paths are unchanged.

A deterministic JVM check compared 10,000 positions, axes, angles (including zero)
and scales with the former matrix composition, with exact float equality. The
particle framebuffer test also checks the actual rendered particles' matrices
against that former composition at every fixture phase.

## Rejected GPU-upload experiment

A three-buffer `glBufferSubData` stream was tried for world boxes, coins and shards.
It passed the pixel/lifecycle checks, but the emulator comparisons did not support
a reliable overall performance improvement. The experiment has been reverted;
the final code retains libGDX's original instance buffers and adds no buffer memory.

The shared host was under substantial concurrent load. The first prepared-emulator
baseline varied from 9.79 FPS in cruise to 49.95 FPS in Matrix mode. A reversed,
warm-order comparison produced the following figures; they are **diagnostics for
the rejected experiment**, not results for the final particle change:

| Scenario | Baseline FPS / GL CPU p50 (ms) | Upload experiment FPS / GL CPU p50 (ms) |
| --- | --- | --- |
| Dense coins | 55.99 / 4.06 | 58.64 / 3.83 |
| Repeated Second Wind | 55.87 / 3.67 | 56.47 / 3.60 |
| Matrix | 59.86 / 6.30 | 37.32 / 7.91 |

These windows do not isolate a device-independent cause for the Matrix slowdown;
changing host load is a confounder. Small wins in the other windows are insufficient
to justify keeping a more complicated upload path before physical-device testing.
Raw logs and installed-APK hashes are retained in `severe-clock-reference`.

## Moto setup and evidence limits

The attached Moto G7 Power initially ran at up to 1,804,800 kHz on both CPU
clusters and 725,000,000 Hz on the GPU. The intended severe limits are:

- policy0: **614,400 kHz** (34% of the original maximum).
- policy4: **633,600 kHz** (35%).
- GPU: **320,000,000 Hz** (44%); the runner also accepts 216 MHz for a harder GPU test.

Initial readbacks caught policy4 staying at 1,094,400 kHz despite lower policy and
msm-performance ceilings. The device has a custom kernel module at
`/sys/module/big_cluster_min_freq_adjust/parameters/min_freq_floor`, set to
1,094,400. The prepared runner lowers this floor with the ceiling and restores it
on exit. Governors, power/performance services and thermal protection stay active.
A preliminary attempt suspending power services did not remove the kernel floor
and stalled the instrumentation launch; it was stopped and all services restored.

The initial run also overlapped another application's instrumentation. Its timing
is excluded. Original CPU/GPU ceilings, input boost and power-service states were
restored and read back before the Moto disconnected. It subsequently reappeared
as **adb unauthorized**. Authorization was subsequently restored; the follow-up
below validates the corrected clock setup. Emulator measurements must not be
presented as evidence of 60 FPS on the severely throttled Moto.

### Authorized physical follow-up

The `5772ad7` APK was installed after the user confirmed the phone was free.
A short watchdog smoke check passed, followed by a planned eight-mode sweep
(`five-boosts,cruise,hills,jet,wide,late,second-wind,matrix`, 40 seconds each,
world 2, section 56, repeatable course, bot enabled, normal sound and haptics).
All 124 clock samples stayed within **614.4/633.6 MHz CPU and 320 MHz GPU**.
The hidden kernel floor correction therefore works on this physical device.

The opening five-boost window measured **59.22 FPS**, with frame p95 **21.13 ms**,
p99 **25.18 ms**, maximum **32.94 ms**, and **22/2,074 frames over 25 ms**.
This is a preliminary result, not a clean 60 FPS pass or an A/B improvement claim.
Process allocations include the planning bot (832 MB and 35 collections in the
35-second measured window); a follow-up must also measure without that overhead.

The sweep was then interrupted: ActivityTaskManager records `straw.berry` launched
at 09:22:39 and `com.kinetic.sand` at 09:22:57. Cube Run's lifecycle logs confirm
pauses and resumes. Cruise's apparent 35.46 FPS includes a 9.71-second absence;
hills' 50.98 FPS includes a 5.53-second absence. These two measurements are invalid,
and later modes were not completed. Stopped only Cube Run upon confirming competing
use. The instrumentation's `Process crashed` result reflects that deliberate stop.

The watchdog restored and verified both CPU ceilings at 1,804,800 kHz, GPU ceiling
725 MHz, kernel floor 1,094,400 kHz, original input-boost/performance votes and
unchanged power-service states. Raw measurements, interference events and control
snapshots are retained under [moto](severe-clock-reference/moto/).

## Repeating the physical test

The runner snapshots all changed controls, checks limits before launching a
command, samples maximum/current frequencies once per second, rejects any sample
above a cap, and verifies restoration. A device-side watchdog restores the saved
controls after its deadline even if the host disconnects. Run only when no other
task is using the phone.

```sh
mkdir -p captures/tmp captures/severe-round/moto
export TMPDIR="$PWD/captures/tmp"
adb -s ZY323NNKTB install -r app/build/outputs/apk/debug/app-debug.apk
adb -s ZY323NNKTB install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
uv run --no-project tools/performance/throttle.py \
  --serial ZY323NNKTB --out captures/severe-round/moto --timeout 420 -- \
  adb -s ZY323NNKTB shell am instrument -w \
  -e class cube.run.game.RunPerformanceTest \
  -e bot true -e repeatable true -e world 2 -e section 56 \
  -e modes cruise,hills,jet,wide,late,second-wind,matrix \
  -e seconds 45 -e minFps 59 \
  cube.run.test/androidx.test.runner.AndroidJUnitRunner
adb -s ZY323NNKTB logcat -d -s RUN_BENCH:I RUN_BOT:I '*:S'
```

Check both the runner exit and instrumentation's `OK (1 test)`; Android's
instrument command can return exit status zero after a test failure. Repeat in
other worlds and with opening boosts. The 59 FPS gate allows small measurement /
60 Hz scheduling variation; it does not mean every individual frame met 16.7 ms.
The log includes measured FPS, total sampled time and frame p95/p99/max. Matrix
mode holds the real Red Pill visual effect, including its CPU-rendered coin wires.
The section argument works independently of audio instrumentation, allowing
normal sound and haptics during the physical-device test. Bot protection remains
labeled, and protected collisions are reported rather than treated as unaided play.

## Validation

Three host watchdog tests exercise the generated restoration script against a
filesystem-backed fake adb: normal completion, failed child command, and a cap
override detected before the child may launch. All three pass. This checks the
control flow; the physical follow-up above separately verifies the Moto's floor
and ceiling behavior.

The final APK passed eight focused device tests on emulator-5584: box and particle pixel parity,
96 coin/Matrix comparisons, terrain continuity, and four launch/background/recreate
checks, including exact comparisons of the actual particle matrices. The coin fixture now explicitly sets its initial depth rule: previously
it could inherit `LEQUAL` from ModelBatch, whereas Matrix wire rendering restores
`LESS`, creating a comparison mismatch for coplanar translucent faces.

Shared-emulator A/B attempts were interrupted by competing installs and are
excluded. The cold dedicated emulator was also unusable under host memory
pressure. The completed upload comparisons used an isolated read-only instance of a prepared
AVD at port 5584: Android 35, NVIDIA RTX 3090 GLES translation, 1080×2340 at 60 Hz,
two virtual CPU cores and 1,536 MiB RAM. The same debug build configuration and deterministic dense-coin course are used for both APKs.

Debug, test and normal release APK builds passed. Android lint reports **zero
errors and 20 existing warnings**. Build, renderer regression and watchdog logs
are retained under [severe-clock-reference](severe-clock-reference/).

Inspected normal run start, spinning coins, lane changes, jumping, boost feedback
and trail/burst particles on the final APK. The [run screenshot](severe-clock-reference/final-run.png)
and [12-second motion sequence](severe-clock-reference/final-motion-sheet.jpg) show
the retained geometry and effects; no new visual or interaction defect was found.
No AndroidRuntime exceptions were logged. The recording is for appearance review,
not a frame-pacing measurement.

**The 60 FPS goal under severe Moto throttling is not yet verified.** No FPS gain
is claimed for the retained particle change from these host-contended experiments.
USB debugging is now authorized and severe clock enforcement is verified. The
remaining blocker is concurrent phone use; the full scenario sweep needs an
exclusive test window. Original phone settings have been restored and verified.
