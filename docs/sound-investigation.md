# Tick sound removal and issue #8

[Issue #8](https://github.com/Eve-146T/cube-run/issues/8) reported stutter coinciding with repeated clicking and much smoother motion with sound disabled. The owner independently compared the phones and described a night-and-day improvement without the tick, then requested permanent removal.

The tick sample is removed from synthesis/loading and from obstacle-row feedback, lane changes, style taps, count-up animations and mystery-box shaking. Lane-change whooshes and all other sound samples remain. Muting the tick no longer depends on Dev mode. Haptics are unchanged. This branch is based on released 2.2; experimental section changes remain isolated in `fixing-sections`.

## Measurements before removal

Both phones ran instrumented debug builds based on tag `v2.2`, with a fixed section seed, the real-input bot, and haptics disabled equally in all conditions. Each condition ran twice in the order on/off/no-tick/no-tick/off/on, 20 seconds per run, excluding the first five seconds. The normal-clock course was Gauntlet at speed 30; the throttled Motorola used First Steps at speed 30. Frame samples are measured on the render thread and are not optical display-latency measurements. Media volumes were nonzero (Motorola 3/15, Pixel 5/25).

| Device / condition | Sound on frame p95 | Sound off frame p95 | Tick muted frame p95 |
| --- | ---: | ---: | ---: |
| Motorola, normal clocks | 18.804 ms | 18.797 ms | 18.865 ms |
| Pixel 7a, 90 Hz | 12.823 ms | 12.545 ms | 12.831 ms |
| Motorola, requested low clocks | 18.259 ms | 18.318 ms | 18.352 ms |

No measured frame exceeded 25 ms in these comparisons. SoundPool call p95 with all sounds enabled was 0.307 ms on Motorola and 0.212 ms on Pixel. These controlled tests did **not** reproduce a consistent tick-related frame-time regression. They do not invalidate the owner's strong subjective improvement or the original device-specific report. The sound implementation and row-click code are identical between tags 2.1 and 2.2; no controlled 2.1 versus 2.2 release comparison was completed.

The bot was protected with reported collision counts in these stress tests. Warming its planner removed opening failures on Motorola; the Pixel controller still struggled with the jump/duck course and had repeated protected collisions, so its results have that limitation. Initial cold-planner runs are retained locally but excluded from the table. These runs do not establish flawless bot play.

The attempted 614.4/633.6 MHz caps were partly overridden by the Motorola power manager/input boosts. Logs show variable reduced frequencies rather than a fixed one-third-speed test. A bounded on-device watchdog and host `finally` restored the original 1.8048 GHz maximums and schedutil governors; restoration was verified. Aggregate results were recorded locally; raw local traces, preferences and device properties were not published.

## Published APK check

The exact [GitHub 2.2 APK](https://github.com/Eve-146T/cube-run/releases/tag/v2.2) was downloaded and installed on Motorola, including verified save restoration across the signing-key change. Its SHA-256 was `28d8c9b5a96eda6cb090ef358ca699916e4ded90edefc12b14237913c6c6ff3a`. The release configuration has `isMinifyEnabled = false`, so R8 code minification was not enabled. Root successfully attached the existing test runner to this unmodified signed APK (four input tests passed). A dedicated release audio comparison was prepared but not run before the owner requested permanent tick removal; the table above must not be described as measurements of the GitHub APK.

The temporary Pixel comparison used Dev on = no tick, Dev off = tick. The final implementation removes ticks in both modes. Both device saves are preserved during installation and tests. Final validation is recorded below.

## Final validation

Debug and release builds and Android lint pass. `TickRemovalTest` passed on both Motorola G7 Power and Pixel 7a: the native tick sample is absent, row feedback and stale tick requests remain silent in both modes, and whoosh/coin/pop/slide each produce valid SoundPool stream IDs. The permanent no-tick debug build is installed on both phones, replacing Motorola's briefly installed GitHub release; saved preferences were restored and verified after every signing-key change and test. No release, tag or push was performed.
