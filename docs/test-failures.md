# Test failure history

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
