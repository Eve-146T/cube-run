# Test failure history

## 2026-09-23 — Localization lint rejected literal percentages

- Revision: `jackpot` at `adde49b` with uncommitted translations
- Check: `:app:lintDebug`
- Command/device: `./gradlew :app:assembleDebug :app:lintDebug --offline`, local build
- Failure: `StringFormatInvalid` in new English and German ability descriptions: literal percent signs were interpreted as format conversions.
- Classification: application resource error
- Resolution: marked the affected non-format strings `formatted="false"` in all three catalogs and reran lint.

## 2026-09-22 — Trophy-room idle animation stalled the achievement UI suite

- Revision: `jackpot` at `3e1ff83` (the rebuilt achievement page).
- Tests: `AchievementDevModeUiTest` (all), `AchievementAlignmentTest`, `AchievementToastTest`.
- Command/device: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cube.run.ui.AchievementAlignmentTest,cube.run.ui.AchievementDevModeUiTest,cube.run.ui.AchievementToastTest`, moto g(7) power (ZY323NNKTB), Android 15.
- Failure: two problems. `developerCanClaimOnceWithoutRedundantRunnerBestAndProgressPersists` died on activity destroy with `NullPointerException: ... View.dispatchDetachedFromWindow()` on a null child, and the suite crawled: `bouncerPersonalBestRemainsVisibleBeforeCompletionAndAfterClaim` alone took 9 minutes, so a first attempt was abandoned as hung.
- Classification: two application bugs in the new page. The claim stamp's rings removed themselves from an `onAnimationEnd` that also fires on cancel, so a cancel during the window's detach walk left a hole in the parent's children. Separately the trophy ring repainted every frame for its turning rays, and the waiting medal beat forever, so the page never went idle — `ActivityScenario.onActivity` waits for global UI idle (the same cause recorded for the jackpot toast on 2026-09-21).
- Resolution: the ring stamp ignores cancelled animations; the ring's rays are now still, the medal beats six times and the card sheen sweeps three times, so everything settles. All 8 tests pass, and the suite went from over 14 minutes to 47 seconds.

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
- Resolution: the 2D scene was later replaced by a 3D show on the GL stage, so the return assertions were rewritten again around the GL-driven overlay (it covers and blocks the HUD, follows `Stage.voidClock`, and hands the shop back in place); a stage that never answers is covered by `voidOverlayReleasesTheHudWhenTheStageNeverAnswers`.

## 2026-09-22 — Void purchase hardware flow never finishes

- Revision: `jackpot` at `1c31c8c` with the uncommitted void-purchase redesign
- Test: `cube.run.ui.AchievementsHardwareFlowTest.actualShopButtonsAndRunEventsReachThePlayer`
- Command/device: same class filter as the entry above, on the moto g(7) power (`ZY323NNKTB`); the phone was otherwise idle this time
- Failure: the runner logged `started:` at 10:05:21 and then nothing for more than 15 minutes; the run was stopped by hand. The first run of the day showed the same silence before another session's install killed it.
- Classification: hanging test, not yet localized. It matches the known global-UI-idle wait under continuous GL rendering (see the 2026-09-21 LanguageTest entry), but whether the void card's always-on animation contributes was not established.
- Resolution: stopped. The void purchase was verified on the phone by hand instead (screen recordings of the whole purchase, frame-by-frame review); this test needs a launch that does not wait for global idle before it can be relied on again.

## 2026-09-21 — Ability translations treated percentages as format tokens

- Revision: `achievements-translations` at `884170f` with local changes
- Command: `./gradlew :app:assembleDebug :app:lintDebug`
- Environment: local repository build
- Failure: the APK assembled, but Android lint rejected six English and German ability resources because literal percent signs were parsed as incomplete format conversions.
- Classification: resource-formatting error
- Resolution: marked non-parameterized percentage strings with `formatted="false"` and reran build and lint.

## 2026-09-23 — low-battery-performance baseline capture overlap

- Revision: jackpot base `0ff04de`; device Moto G7 Power `ZY323NNKTB`.
- Command: `am instrument -w -e class cube.run.game.RunPerformanceTest -e bot false -e audio on -e section 8 -e world 2 -e modes cruise,hills,jet,second-wind -e seconds 30`.
- Failure: baseline and sampling instrumentation returned `Process crashed` after a second instrumentation launch overlapped the still-running first launch. No AndroidRuntime exception was logged.
- Classification: test orchestration/environment problem. Discarded both runs, reran sequentially, and wait for each instrumentation process before subsequent phone operations.

- Follow-up: the sequential retry also stopped after cruise. Android ActivityManager confirms an unrelated APK install at 09:23:24 killed `cube.run` (`stop cube.run due to installPackageLI`); this was not an application crash. Recorded the interruption, reinstalled the retained baseline and reran successfully. Further phone installs stopped once competing use was confirmed.

## 2026-09-23 — instanced coin exact framebuffer edge

- Revision: low-battery-performance work in progress on `0ff04de`; emulator-5560, GLES3.
- Command: `am instrument -w -e class cube.run.game.CoinBatchTest,cube.run.game.BatchVisibilityTest,cube.run.data.AchievementsProgressTest cube.run.test/androidx.test.runner.AndroidJUnitRunner`.
- Failure: dedicated coin comparison found four differing channels (one pixel, max delta 38) at yaw phase 9 with translucent coins. Existing batch comparisons and all 26 achievement tests passed.
- Classification: renderer rounding at a projected edge. Reordered GPU world-position arithmetic to match the CPU reference's left-to-right additions rather than adding the translation last. Exact comparison retained.
- Resolution: exact 96-state framebuffer rerun passed after matching coordinate arithmetic order (no tolerance or fixture removal).

## 2026-09-23 — performance course shortcut localization gate

- Revision: performance shortcut work on `c96b35a`.
- Command: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1` (two builds while the focused test was being added).
- Failure: lint reported four `MissingTranslation` errors for the new debug course labels in German and Hebrew. Both APKs compiled.
- Classification: incomplete UI resources. Added the four labels in both supported translations and reran the gate.
- Resolution: final debug APK, instrumentation APK and lint gate passed; both focused course tests passed on emulator-5560.

## 2026-09-23 — animated menu accessibility dump

- Revision: performance shortcut work on `c96b35a`; emulator-5560 at 540×960, density 240.
- Command: `adb -s emulator-5560 shell uiautomator dump /sdcard/course-ui.xml`.
- Failure: the accessibility dump could not obtain an idle state while the main menu animated; no dump file was created.
- Classification: inspection-tool limitation, not an application failure. Used actual screenshot coordinates for the tap-only flow and inspected screenshots and recordings instead. Course selection, retry, normal exit, Red Pill exit and section exit all worked.

## 2026-09-24 — severe Moto stress setup and concurrent device use

- Revision: `low-battery-performance` at `de2e823`.
- Device: Moto G7 Power `ZY323NNKTB`; `RunPerformanceTest`, static cruise/hills/jet/wide/late/second-wind, 25 seconds per phase.
- Failure: preliminary CPU cap validation found policy4 remaining at 1,094,400 kHz rather than 633,600 kHz. The first cruise phase completed, then the GL snapshot timed out. ActivityManager shows concurrent `com.kinetic.sand` instrumentation bringing another activity forward.
- Classification: environment/setup failure; these measurements are excluded. CPU/GPU limits and boost settings were restored. Stopped Cube Run's next profiling attempt upon discovering concurrent use; requested exclusive access and continued independent work.

## 2026-09-24 — streaming renderer Matrix coin comparison

- Revision: `de2e823` with rotating instance upload buffers; emulator-5560.
- Test: `CoinBatchTest.instancesMatchCpuAcrossGlintsFogFadeTerrainAndMatrixTransitions` in the focused renderer/lifecycle suite.
- Failure: exact framebuffer mismatch at phase 0, Matrix blend 0.45 (first differing channel 158600, 78 vs 53). World-box and particle comparisons passed.
- Investigation: checking unchanged baseline and GPU/CPU state transitions before accepting the upload change.
- Classification: test fixture state leakage. The unchanged depth setup inherited `LEQUAL` from ModelBatch, while the first Matrix wire draw restored `LESS`; later comparisons therefore had a different depth rule for coplanar translucent triangles. The candidate passes all 96 comparisons when this test runs alone. Made the fixture explicitly set `LESS` before each reference/candidate render; exact byte comparisons remain unchanged.
- Resolution: the corrected eight-test renderer/terrain/lifecycle suite passed together, retaining byte-exact assertions for every coin state.

## 2026-09-24 — baseline emulator comparison interrupted by another install

- Revision: unchanged `de2e823`; emulator-5562.
- Test: isolated `CoinBatchTest` baseline investigation.
- Failure: instrumentation returned `Process crashed`; ActivityManager identifies `installPackageLI` from a competing session at 07:37:25 as the cause.
- Classification: environment interference. Excluded the run and stopped using emulator-5562; continued sequential checks on emulator-5560.

## 2026-09-24 — shared emulator performance comparison interrupted

- Revision: `de2e823` baseline; emulator-5560, alternating comparison runner.
- Failure: baseline completed cruise and hills, then returned `Process crashed`; APK identity lookup failed because Cube Run was temporarily absent. ActivityManager confirms a competing `deletePackageX` at 07:41:18 followed by installation.
- Classification: environment interference. Discarded the incomplete comparison and created a dedicated emulator for this round.

## 2026-09-24 — dedicated emulator display setup

- Command: `emulator -avd cube-severe-round -port 5588 -no-window -no-snapshot -gpu host`.
- Failure: the host GPU backend could not initialize EGL because the shell had no `DISPLAY`.
- Classification: environment setup. Restarted only this newly created emulator with the working emulators' display/Xauthority settings; other emulator processes were left running.

## 2026-09-24 — cold dedicated emulator cannot provide useful timing

- Revision: unchanged `de2e823`; fresh Android 35 emulator, 720×1520, host GPU, initially 2 GiB then 1 GiB guest RAM.
- Failure: under heavy concurrent host activity (about 20 GiB swapped), cold boot stalled; after boot the unchanged baseline cruise measured only 1.39 FPS, 594 ms median frame time. This is unsuitable for a renderer optimization comparison and is not a Cube Run regression attributable to the candidate.
- Classification: environment/resource pressure. Stopped the run and its dedicated emulator; attempting a prepared, isolated read-only AVD instead of further cold-boot measurements.

## 2026-09-24 — authorized severe Moto sweep interrupted by other applications

- Revision: `5772ad7`; Moto G7 Power `ZY323NNKTB`, verified CPU caps 614400/633600 kHz and GPU cap 320 MHz.
- Command: `tools/performance/throttle.py --serial ZY323NNKTB --out captures/severe-round/moto-all --timeout 450 -- adb -s ZY323NNKTB shell am instrument -w -e class cube.run.game.RunPerformanceTest -e bot true -e repeatable true -e world 2 -e section 56 -e modes five-boosts,cruise,hills,jet,wide,late,second-wind,matrix -e seconds 40 cube.run.test/androidx.test.runner.AndroidJUnitRunner`.
- Failure: after user confirmation of availability, other sessions brought `straw.berry` (09:22:39) and `com.kinetic.sand` (09:22:57) forward. Cruise/hills timings include background gaps of 9.71/5.53 seconds and cannot establish rendering performance. Stopped Cube Run, resulting in instrumentation `Process crashed`; no application exception caused that stop.
- Classification: environment interference. Discarded affected timings, stopped further phone testing after the user confirmed another session may control it. Opening five-boost result is preliminary (59.22 FPS, 22 frames over 25 ms); no complete 60 FPS gate passed.
- Resolution: all 124 sampled CPU/GPU readings respected the caps. Watchdog restored and verified original ceilings, kernel floor, input boost, performance votes and service states. Retained evidence in `docs/severe-clock-reference/moto/`; exclusive hardware access remains necessary.
