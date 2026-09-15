# Duck / bar review

Addresses the visual ambiguity reported in [#16](https://github.com/Eve-146T/cube-run/issues/16),
prioritized in [#6](https://github.com/Eve-146T/cube-run/issues/6), including the moving-bar
confusion in [#3](https://github.com/Eve-146T/cube-run/issues/3).

Five native renderer candidates live in `ObstacleCues.kt`:

1. `EDGE_BANDS`: white top edge for duck, bottom edge for jump (the issue’s suggestion).
2. `ARROWS`: repeated, dark-backed down/up chevrons.
3. `HAZARD`: yellow/black markings at the corresponding edge.
4. `ACTION_COLORS`: stable blue/orange action colors plus chevrons across every world palette.
5. `SHADOW_ARROWS`: chevrons plus a ground footprint for suspended obstacles.

`ORIGINAL` is the comparison baseline. `TrackRenderer.cueStyle` selects the variant;
the worktree defaults to candidate 1 pending review. All five are real rendering
implementations, not image mockups. No collision dimensions, timing, controls or
section generation change. Factory-authored cues cover full and partial walls,
bars, sweepers and pendulums; stompers, pillars, pits and pad-assisted tall walls
keep their own language. Decorations follow stream-in scaling, lane stretching,
terrain and fog. Solid geometry is queued before the added markings.

## Capture

Use a test emulator. Build with `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
-PduckReview=true`, then install both APKs. The opt-in suffix gives the review
its own app data and avoids replacing another worktree’s app. Then:

```sh
adb -s emulator-5562 shell settings put secure immersive_mode_confirmations confirmed
adb -s emulator-5562 shell am instrument -w -r \
  -e class cube.run.game.DuckBarReviewTest -e captureDuckBars true \
  cube.run.duckreview.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p captures/duck-review/assets
adb -s emulator-5562 pull /sdcard/Android/data/cube.run.duckreview/files/duck-review/. captures/duck-review/assets/
cp tools/duck-review/index.html captures/duck-review/index.html
drop web captures/duck-review --title "Cube Run — five duck/bar fixes"
drop shot captures/duck-review/assets/*.png --title "Duck/bar native screenshots"
```

The capture test is opt-in; its other test checks action labels, exclusions and
lane stretching. Four frozen scenes show near duck/jump walls, rolling terrain,
and moving/partial bars. Each scene includes the existing tall-wall stripe for
comparison. The current world is shared across all candidates in a capture run.

## Review limits

These are visual candidates, not a proven reaction-time improvement. The shadow
is a stylized opaque footprint, not a dynamic lighting pass. More elaborate styles
consume more of the existing box batch. In red-pill mode the existing renderer
turns these boxes into wire outlines too. Playtesting at speed and across world
palettes should determine the final choice.
