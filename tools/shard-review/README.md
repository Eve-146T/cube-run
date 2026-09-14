Shard display review

Three native wardrobe variants are implemented in `ShardDisplay`:

1. **In the cube** (default): `33/100` and `Frost shards` sit inside the cube.
   At 100, the count becomes `READY` and the taller `UNLOCK` button appears.
2. **Under the name**: one compact progress line and a short segmented bar.
3. **Shard pill**: the game's white currency pill sits just below the cube.

Buy and equip now match the former two-line unlock height. All unlock buttons use the concise `UNLOCK` label with the same dimensions as
the regular buy/equip action. A hidden button keeps its layout slot, so the cube
name and carousel dots do not jump when the collectible becomes ready.

Build and install both the debug and Android test APKs, then run on one device:

```sh
adb -s SERIAL shell am instrument -w -e class cube.run.ui.ShardReviewTest -e captureShards true cube.run.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL pull /sdcard/Android/data/cube.run/files/shard-review captures/shard-review
```

The explicit capture fixtures set native views to 33 and 100 shards without
changing saved progress or enabling purchases. The test is skipped unless
`captureShards=true` is supplied. It verifies the action remains one line and
the same height across all variants.

Copy `index.html` and the screenshots (under `assets/`) into a review directory,
then publish with `drop web`. Do not upload an APK; install the build on the phones.
