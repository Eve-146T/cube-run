# Issue 46: sound-triggered screen stutter

Report: https://github.com/Eve-146T/cube-run/issues/46

Investigated on 2026-10-06 from main a47f5d2.

## Evidence and limits

The attached 14.8-second, 60 FPS recording shows gameplay pauses before the
player mutes sound in the pause menu, followed by smoother gameplay. Comparing
successive downscaled grayscale frames in the central scene found near-static
runs of 67–100 ms around 2.12, 4.77, 5.72, 6.55 and 7.52 seconds. No comparable
runs appeared after leaving the pause menu at approximately 10.3 seconds.
This confirms visible stutter in the recording, but does not alone prove its cause.

SoundFx called SoundPool.play directly from its caller, including the GL render
thread. A repeatable sound-on run on the attached Moto G7 Power measured 147
successful playback calls; the slowest took 3.568 ms on that thread. The severe
stutter seen in the recording was not reproduced on this phone.

## Change

Dispatch SoundPool playback to a single process-scoped sfx-play worker. Keep
FIFO order, existing pitch/volume limits, and recheck mute before playback.
The queue holds at most 32 pending effects and discards effects older than
100 ms so stalled audio does not accumulate a delayed burst. A full queue
drops the incoming effect without waiting for the backend.

Debug sound observers now run on the audio worker and still measure actual
SoundPool calls. Their timestamps no longer measure render-thread work.

## Validation

- Debug app and instrumentation APK builds and lintDebug passed.
- Four device regression tests passed: caller progress while the backend is
  blocked, bounded backlog, stale-effect removal, real worker playback/mute,
  and the existing tick-removal test (two queue cases plus two integration cases).
- RunPerformanceTest passed before and after with audio=on, repeatable=true,
  seconds=15, modes=cruise, world=0 on the Moto G7 Power.
- Baseline/fixed measured gameplay: 599 frames each, mean frame time 16.705 ms
  each (~59.9 FPS), p95 18.787/19.026 ms, maximum 20.891/21.612 ms. Neither
  run had frames above 33.4 ms. All 147/164 observed playback calls succeeded.

The fix removes synchronous platform playback from rendering. These runs show
no material performance regression, but do not establish that the reporter's
device-specific stutter is resolved; that still needs testing on their device.
