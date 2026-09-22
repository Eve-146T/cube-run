# Test failure history

## 2026-09-22 — Developer Gambler achievement total read before the jackpot show banked it

- Revision: `jackpot` based on `0c94532`, working changes (3D jackpot show).
- Test: `EquippedAbilitiesTest.developerGamblerUsesTheSameJackpotAndNeverCollectsOrdinaryBoxes`.
- Command/device: `ANDROID_SERIAL=ZY323NNKTB ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cube.run.ui.JackpotCounterTest,cube.run.game.EquippedAbilitiesTest,cube.run.game.LotteryRulesTest`, moto g(7) power, Android 15.
- Failure: `expected:<250000> but was:<0>` for `Progress.achievementCoins` right after the winning coin.
- Classification: stale expectation. The run owns the win at once (`coinsRun` was correct), but the HUD haul and the achievement total now update when the show's counter lands in the coin pill (`JackpotBeats.BANKED`). The test now plays the show out before checking; `gamblerForfeitsOrdinaryLootAndRichCoinsGiveThreeTickets` was adjusted the same way before it could fail.

## 2026-09-21 — Jackpot instrumentation exited after APK replacement

- Revision: `jackpot` based on `884170f`, working changes.
- Command/device: `adb -s emulator-5584 shell am instrument -w -e class cube.run.ui.JackpotToastTest cube.run.test/androidx.test.runner.AndroidJUnitRunner`, Android 35 emulator.
- Failure: runner reported `Process crashed` without test results immediately after replacing both APKs. Android's crash log was empty and the game process was still running when inspected.
- Classification: environment interference. Later motion review showed the old toast; Android's event log confirmed another `installPackageLI` killed the instrumented game at 17:08:40, after our test APK installation at 17:08:36.
- Resolution: switched to the repository's separate `-PduckReview=true` application ID before final verification; all three `JackpotToastTest` tests passed there. The subsequent readable-hold failure and old-toast recording came from the replaced build and are not valid evidence for the revised animation.

## 2026-09-21 — Jackpot readable hold shortened by the new entrance

- Revision: `jackpot` based on `884170f`, working changes.
- Test: `JackpotToastTest.bannerRetiresAfterItsReadableHoldAndIgnoresInvalidAmounts`.
- Command/device: `adb -s emulator-5584 shell am instrument -w -e class cube.run.ui.JackpotToastTest cube.run.test/androidx.test.runner.AndroidJUnitRunner`, Android 35 emulator.
- Failure: payout had already retired at the readable-hold assertion; the longer reel/impact entrance consumed the old toast's hold budget.
- Classification: timing-sensitive test synchronization. The later rerun was also contaminated by another build replacing the app (see above).
- Resolution: `ActivityScenario.onActivity` waits for global UI idle; continuous spectacle repainting delays that wait until the animation ends. Replaced live-state assertions with direct main-thread dispatch. Also extended celebration to 3.8 seconds and moved retirement to 3.6 seconds to give the longer entrance an ample readable hold. All three tests passed on the final isolated review build.

## 2026-09-20 — PR #20 and PR #21 verification commands could not start

- Revisions: `faaec521ee17b1a433750359c3160b33b8a063dc` (PR #20), `170d65ba1f43ee92d76f2a04382a0eddf9277aa5` (PR #21)
- Command: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Environment: isolated local Git worktrees; no Android device attached
- Failure: Gradle wrapper could not create its distribution lock under `/home/user1/.gradle` because the sandbox mounted that cache read-only. No build, test, or lint task ran.
- Classification: environment problem
- Resolution: reran the same commands with access to the existing Gradle user cache; build and lint completed successfully for both PRs, and the unit-test task reported `NO-SOURCE`.

## 2026-09-20 — Baseline magnet recording could not preserve absent preferences

- Revision: `973b87decc7389365b193920c7ef77afab7efb08` (current `origin/main` baseline)
- Command: `uv run --no-project tools/bot/bot.py play --device emulator-5554 --section 56 --seconds 15 --magnet --record --no-build --output captures/pr-audit/magnet-base`
- Device: fresh `eve-pool-1` Android 35 emulator (`emulator-5554`)
- Failure: the bot's pre-test preference backup was smaller than 512 bytes because Cube Run had never been installed or launched on the wiped emulator.
- Classification: environment/setup problem
- Resolution: installed and initialized the baseline app once, then reran the same recording command successfully.

## 2026-09-21 — Test-world APK screenshot destination was absent

- Revision: local combined test build `93e2266` plus the bundled MOTHERLODE/magnet preset
- Command: install, launcher start, activity verification, and `adb exec-out screencap` to `captures/test-world-apk-check.png`
- Device: fresh `eve-pool-1` Android 35 emulator (`emulator-5554`)
- Failure: installation and launch succeeded, but the host shell could not create the screenshot because the isolated worktree had no `captures/` directory.
- Classification: environment/setup problem
- Resolution: create the output directory and repeat only the screenshot capture; the already-running app remains the test subject.

## 2026-09-21 — Integration build could not access the Gradle cache

- Revision: `achievements-translations` merge in progress at `31c8973`
- Command: `./gradlew :app:assembleDebug`
- Environment: repository sandbox
- Failure: the Gradle wrapper could not create its distribution lock under `/home/user1/.gradle` because the cache was mounted read-only. No build task ran.
- Classification: environment problem
- Resolution: reran the build with access to the existing Gradle user cache.

## 2026-09-21 — Combined focused instrumentation run stopped reporting progress

- Revision: `achievements-translations` merge in progress at `31c8973`
- Command: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cube.run.ui.LanguageTest,cube.run.ui.AchievementsInteractionTest,cube.run.ui.AchievementAlignmentTest`
- Device: `eve-pool-1` Android 35 emulator (`emulator-5554`)
- Failure: the runner completed 1 of 8 tests, then produced no progress or failure for more than five minutes while the app remained responsive. The run was interrupted.
- Classification: test-runner or test-interaction problem under investigation
- Resolution: split the combined selection into individual test classes to isolate the stalled class.

## 2026-09-21 — Language visual test process was killed by package cleanup

- Revision: `achievements-translations` merge in progress at `31c8973`
- Test: `cube.run.ui.LanguageTest.rtlVisualReviewCoversAbilitiesShardsPauseAndRewards`
- Command/device: isolated `LanguageTest` run on `eve-pool-1` Android 35 (`emulator-5554`)
- Failure: Android killed `cube.run` for `deletePackageX` while the test was starting, immediately after the prior interrupted Gradle run; no application exception was logged and the instrumentation process was reported as crashed.
- Classification: environment/test-runner cleanup race
- Resolution: waited for the interrupted runner's package cleanup to finish, then reran the class on the same emulator.

## 2026-09-21 — Language visual test waited indefinitely for UI idle

- Revision: `achievements-translations` merge in progress at `31c8973`
- Test: `cube.run.ui.LanguageTest.rtlVisualReviewCoversAbilitiesShardsPauseAndRewards`
- Command/device: isolated `LanguageTest` run on `eve-pool-1` Android 35 (`emulator-5554`)
- Failure: the test remained on the responsive Hebrew main menu while `waitForIdleSync` did not return; a thread/CPU check showed the render loop active and the test runner waiting.
- Classification: stale test synchronization; Cube Run's continuous GL rendering and menu animation do not guarantee Android's global UI-idle condition. Achievement polling on the idle menu added another needless periodic wakeup but was not the sole cause.
- Resolution: replaced the test's explicit post-tap `waitForIdleSync` with targeted state waits and a short settle, and limited achievement polling to active runs. A direct instrumentation rerun plus a JDWP stack inspection then localized the remaining wait to `ActivityScenario.launch` calling Android's own `Instrumentation.waitForIdleSync` before the test body. The English, German and Hebrew flows were therefore verified manually on the same emulator; this visual-review test still needs a launch strategy that does not require global UI idle.

## 2026-09-21 — Language-charge cleanup removed a shared import

- Revision: `achievements-translations` at `884170f` with local changes
- Command: `./gradlew :app:assembleDebug`
- Environment: local repository build
- Failure: Kotlin compilation failed because removing the Hebrew language charge also removed the `Progress` import from `GameActivity`, where two unrelated calls still use it.
- Classification: application compile error
- Resolution: restored the shared import and reran the build.

## 2026-09-22 — Void purchase hardware flow killed mid-run

- Revision: `jackpot` at `1c31c8c` with the uncommitted void-purchase redesign
- Test: `cube.run.ui.AchievementsHardwareFlowTest.actualShopButtonsAndRunEventsReachThePlayer`
- Command/device: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cube.run.ui.AchievementsHardwareFlowTest,cube.run.ui.AchievementDevModeUiTest -Pandroid.testInstrumentationRunnerArguments.captureHardwareAchievements=true` on the shared moto g(7) power (`ZY323NNKTB`, Android 15)
- Failure: "Instrumentation run failed due to Process crashed" after about 10.5 minutes with no application exception; logcat shows `cube.run` being updated by another session's install at 10:00:26 and the process killed with signal 9. No test log lines appeared for the ten minutes before that.
- Classification: environment problem (another agent installed its build on the shared phone). While investigating, the test's void-return assertions also turned out to be stale for the redesign: they require a circular mask that uncovers transparent pixels, and the new return is an opaque picture of the shop unwinding out of the hole.
- Resolution: rewrote the return assertions for the new design (the scene stays fully opaque and less of the void's dark remains at each later point) and reran once the phone was free.

## 2026-09-22 — Void purchase hardware flow never finishes

- Revision: `jackpot` at `1c31c8c` with the uncommitted void-purchase redesign
- Test: `cube.run.ui.AchievementsHardwareFlowTest.actualShopButtonsAndRunEventsReachThePlayer`
- Command/device: same class filter as the entry above, on the moto g(7) power (`ZY323NNKTB`); the phone was otherwise idle this time
- Failure: the runner logged `started:` at 10:05:21 and then nothing for more than 15 minutes; the run was stopped by hand. The first run of the day showed the same silence before another session's install killed it.
- Classification: hanging test, not yet localized. It matches the known global-UI-idle wait under continuous GL rendering (see the 2026-09-21 LanguageTest entry), but whether the void card's always-on animation contributes was not established.
- Resolution: stopped. The void purchase was verified on the phone by hand instead (screen recordings of the whole purchase, frame-by-frame review); this test needs a launch that does not wait for global idle before it can be relied on again.
