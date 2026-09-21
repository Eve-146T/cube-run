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
