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

For release-variant measurements, build both APKs with `./gradlew -PperformanceRelease=true assembleRelease assembleReleaseAndroidTest`. This signs the local release with the debug key and enables `BuildConfig.PERFORMANCE_TEST`, allowing the instrumentation's crash observer to protect an unattended run. Ordinary release builds keep that flag false. Protection uses the observer rather than the removed jetpack landing grace timer.

Install the matching `app-release.apk` and `app-release-androidTest.apk`. Debug instrumentation cannot drive a release APK: Kotlin internal accessors include the build variant in their names. When comparing an older release, build its release instrumentation from its tag and pass it as `--baseline-test-apk` to `tools/performance/compare.py`. A downloaded release APK can be re-signed with the local test key without changing its code or resources. Record that provenance and any differences in collision protection alongside the results.

Menus, restarts and taps are measured by `UserActionBenchmarkTest`: see [action-benchmarks.md](action-benchmarks.md). The playing bot used by long tests is in [bot.md](bot.md).

Compare before and after on the same phone, battery level and temperature, and don't profile while measuring: even sampling changes the timings a lot.

## What we learned

- Frame time is the budget, not the average: look at p95 and p99 and the frames over 25 ms. A smooth average with a stutter every few seconds still feels bad.
- No per-pixel tricks: a ray-marched volume ran at 11 fps on the Moto. Effects are built from boxes, flat shading and a few soft glows.
- Scenery and obstacles are flat-shaded boxes drawn through GLES 3 instancing (a CPU batch on GLES 2). Keep pieces few and chunky; every extra facet costs on the Adreno.
- Time steps follow real elapsed time in small, collision-safe slices, so a stall never changes how far the cube travels.
- Never allocate in the frame loop. Particle and shard pools are fixed in size and replace their oldest entry in constant time when full.
- Animated text (score and coin pulses, bobbing labels) is drawn from a cached layer, never re-rasterized each frame.
- Property animations invalidate the window without dirtying the animated view's cached drawing. Custom Canvas effects still invalidate their own content.
- Results keep the score's horizontal constraint fixed while it counts. Hidden menu preparation waits until the main menu: building shop and collection trees during results competes with the count and record celebration.
- The shop holds its decorative card animation while its panel slides, then resumes it after navigation.
- Hidden achievements build and measure their cards in a detached column, then attach it together on the main menu or when achievements opens. Preparation pauses for runs and overlay sheets; incremental additions must never remeasure an unrelated visible screen.
- The section explorer builds detached cards in small batches, retains them across grouping and loop-selection changes, and caches previews and family classification. A recycled list measures only the visible rows. Only visible rows run entrance animations, whose completion does not remeasure the list. Its prepaint layer is released before interaction so scrolling does not redraw a full-screen texture.
- Vibration runs on its own worker with a bounded queue, so a burst of coin ticks can't pile up behind an impact.
- Sound must never stutter the frame: frequent sounds are short, pre-synthesized and loaded once.
