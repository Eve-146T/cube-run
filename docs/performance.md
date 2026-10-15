# Performance

The game must hold a steady frame rate on the slowest phone we test on: the Moto G7 Power (Snapdragon 632, Adreno 506, 720 × 1520 at 60 Hz). Faster phones run at up to 90 Hz. Anything new (a world, a biome, a skin, an effect) is measured on the Moto before it ships. The history of earlier performance passes is in git (`docs/performance.md` before October 2026).

## Measuring

`RunPerformanceTest` runs the real game, GL surface, HUD, sound and vibration at full speed, keeps unattended crashes from ending the run, and logs frame interval and frame-build time (p50/p95/p99/max), GL-thread CPU time, long frames, allocations and GC to `RUN_BENCH`. Each phase skips five seconds of warm-up.

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb -s ZY323NNKTB install -r app/build/outputs/apk/debug/app-debug.apk
adb -s ZY323NNKTB install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s ZY323NNKTB shell am instrument -w -e class cube.run.game.RunPerformanceTest \
  cube.run.test/androidx.test.runner.AndroidJUnitRunner
adb -s ZY323NNKTB logcat -d -s RUN_BENCH:I '*:S'
```

For longer coverage add `-e seconds 120 -e modes cruise,hills,second-wind,jet,wide,late`: `jet` holds the jetpack's top speed, `wide` the five-lane bonus world, and `late` jumps the clock and distance far into a run. These are test-only controls.

Menus, restarts and taps are measured by `UserActionBenchmarkTest`: see [action-benchmarks.md](action-benchmarks.md). The playing bot used by long tests is in [bot.md](bot.md).

Compare before and after on the same phone, battery level and temperature, and don't profile while measuring: even sampling changes the timings a lot.

## What we learned

- Frame time is the budget, not the average: look at p95 and p99 and the frames over 25 ms. A smooth average with a stutter every few seconds still feels bad.
- No per-pixel tricks: a ray-marched volume ran at 11 fps on the Moto. Effects are built from boxes, flat shading and a few soft glows.
- Scenery and obstacles are flat-shaded boxes drawn through GLES 3 instancing (a CPU batch on GLES 2). Keep pieces few and chunky; every extra facet costs on the Adreno.
- Time steps follow real elapsed time in small, collision-safe slices, so a stall never changes how far the cube travels.
- Never allocate in the frame loop. Particle and shard pools are fixed in size and replace their oldest entry in constant time when full.
- Animated text (score and coin pulses, bobbing labels) is drawn from a cached layer, never re-rasterized each frame.
- Vibration runs on its own worker with a bounded queue, so a burst of coin ticks can't pile up behind an impact.
- Sound must never stutter the frame: frequent sounds are short, pre-synthesized and loaded once.
