# Red pill review

The red/white capsule joins the normal shuffled pickup bag. Collection immediately starts a 12-second visual effect: 0.9-second eased entry, then a 1.2-second eased exit after expiry. Another pill refreshes the timer without resetting the blend. Death fades it out; pause freezes it. Collisions, track geometry, score, speed and equipped cosmetics are unchanged.

The renderer keeps black depth-writing faces and draws their actual green edges in one additional bounded batch. This covers road tiles, scenery, obstacles and coins, including spun objects and rolling terrain. GLES 3 keeps instancing; GLES 2 uses the existing CPU geometry fallback. The capsule rocks ±22° so both halves remain visible.

Device checks:

```sh
adb -s SERIAL shell am instrument -w -r -e class cube.run.game.RedPillTest cube.run.test/androidx.test.runner.AndroidJUnitRunner
```

Explicit native review capture (skipped by default):

```sh
adb -s SERIAL shell am instrument -w -r -e class cube.run.game.RedPillReviewTest -e captureRedPill true cube.run.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL pull /sdcard/Android/data/cube.run/files/red-pill-review
```

The capture saves capsule, half-entry, full effect, half-exit and restored frames, then runs a short live entry/exit for screen recording. Only that final review sequence shortens the timer to three seconds. Back up and restore the device's saved progress around instrumentation.
