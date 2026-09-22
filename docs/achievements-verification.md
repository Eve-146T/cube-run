# Achievements verification

Physical review uses the attached **Moto G7 Power**, Android 15 / API 35,
arm64, 720 × 1520 at 320 dpi. Its locale is en-IN, so the displayed `500,000`
also verifies that achievement formatting does not depend on the phone locale.
Each phase archives the complete shared-preferences directory, restores it, and
compares every restored file byte for byte. Test fixtures never become the user's save.

## Stay Centered and cosmetic pricing — September 16, 2026

Added the eighth achievement, Stay Centered, concise collection subtitles, and
the restored “Single-run score” subtitle without a redundant Best label. All 42
paid cube, bubble-skin and trail prices are multiplied by ten. Wardrobe purchase
labels now fit the grouped price and coin together at the final pixel size.

Production and instrumentation builds plus lint pass in
`.build-tmp/hardware/build-center-prices.log`. The final test fixture builds are
`build-center-popup-fixture.log` and `build-center-layout-fixture.log`.

`center-prices-device` ran **42 checks**. Forty-one passed; the narrow-layout
fixture retained an old assertion expecting seven achievements. Updating that
assertion and checking the complete center-lane description produces a passing
focused rerun in `center-prices-layout-final` (**1/1**, 3.368 seconds).
Thus all 42 selected checks pass across the two phases. Coverage includes:

- Real Player and GameHostSession tracking at 99/100, same-frame leave/return,
  center-preserving portal remaps, new-run reset, dev mode and the unlock gate.
- Permanent single claims, the completed check, hidden completed score, and
  the production run's Stay Centered popup. Runner's moving-world popup remains
  separately covered with the earlier challenge already earned.
- All cosmetic prices, actual purchases of the three repriced secrets, the
  mystery-box purchase/return, and normal/dev Android Bouncer gestures.
- Painted icon alignment, scroll clipping and progress fills, all requested
  subtitles, and untruncated 300,000 / 500,000 / 750,000 wardrobe labels at
  280 dp with 150% text size and at normal width.

Both phases restore and verify the full original preferences byte for byte;
both Android runtime error logs are empty. The installed production APK matches
the tested checksum:
`c4f0673b12bb8f5967a54b86f90f0ce3350d826bc3c0a2e00d2a5e94e4fe5a12`.

Fresh captures under `.build-tmp/hardware/center-prices-device` include
`achievements-physical/page-progress.png`, `challenges-progress.png`, and
`achievements-hardware/stay-centered-complete.png`, `stay-centered-claimed.png`,
and `actual-game-event-toast.png`. Earlier files copied from the device remain
historical; only states requested by this phase are fresh evidence.

The standalone [ability proposals](ability-ideas.html) were expanded through three
subagents to **300 brainstorming ideas**: 210 cube and 90 bubble abilities. The
plain passive bonuses lead the list; each card has one short description and a
stable idea number. Source lists are `ability-brainstorm-passives.json`,
`ability-brainstorm-movement.json` and `ability-brainstorm-bubbles.json`.
Counts, schema and title uniqueness validate. Chromium checks cover text search,
cube/bubble filters, plain passives, simple/wild ideas, and empty results. The
plain filter returns 89 cube and 52 bubble proposals; the full list contains
197 simple and 103 wild ideas. Desktop and mobile renders were visually reviewed,
with no horizontal overflow at 390 px. Shared through the requested local drop:
https://apps.muxu.click/d/qd7vufe7.

## Shop-style achievement cards — September 15, 2026

The cards now use coloured header bands and circular badges above dark tinted
bodies. Future payouts are inline coin amounts without a separate pale panel or
“Reward” label; only earned payouts have gold CLAIM buttons.

Build, instrumentation and lint pass in
`.build-tmp/hardware/build-shop-style-achievements.log`.
`shop-style-achievement-preview` passes **7/7 device checks**: category/medal/payout
centering, dev claim persistence and real best display, Bouncer best after claiming,
header clipping during scroll/claim changes, offscreen progress fill, narrow and
enlarged-font layouts, and a mixed-progress visual review. The original preferences
are restored and verified byte for byte after the phase. Screenshots are in
`shop-style-achievement-preview/achievements-physical/page-progress.png` and
`challenges-progress.png` under `.build-tmp/hardware`.

`shop-style-claimed-review` also passes the fully claimed review, including compact
checked challenge cards and completed medal tracks. Both phases have empty Android
runtime error logs. The installed APK matches the tested build:
`2eca6d1b7791932a856e46c33a1b6588787e789866e4812648fad90f5a716364`.
The original save is unchanged and the game is open at the main menu. Its current
achievement page is captured in `shop-style-claimed-review/original-achievements.png`,
including the inline `+750` payouts.

## Earlier contrast, dev mode and gift return checks

Production build, instrumentation build and lint pass in
`.build-tmp/hardware/build-final-reward-contrast.log`. The component fixture's
test-only build also passes in `build-gift-fixture-final.log`.

- `contrast-progression-preview`: **20/20 pass**, including eighteen progression
  checks, painted icon alignment, and the new light-card/dark-background review.
  Real GameHostSession tests cover multiplied score deltas, bonus-only tier crossings,
  stale saved-best recovery within the same activity, dev awards and claims, real-bank
  reward retention when leaving dev, and the dev void gate bypass.
- `dev-controls-final`: **5/5 pass**. Actual dev CLAIM controls pay once and retain
  the real best through tier changes. Bouncer keeps its personal best before/after
  completion and claiming. Actual Android wall flicks earn the dev Bouncer award;
  a new run resets only its run count. The actual void offering works at zero lifetime
  coins in dev, and is hidden again in normal mode. Narrow/enlarged achievement and
  capped-price layouts also pass.
- `gift-ui-final`: the renderer-delayed return, claim/scroll clipping, newly-visible
  bar fill, and narrow/wide reward geometry pass. The hint component fixture exposed
  that Stage.paused intentionally does not stop gift animations. The fixture now
  holds the actual GL queue during synthetic component taps and clears requests
  before releasing it; the complete pre-test save was restored after the failed fixture.
- `gift-final-review`: **3/3 pass**, including both corrected component tests and the
  complete production purchase/open/return/run flow. Footer geometry is verified
  at 280 dp with 150% text size and at 600 dp, including the full spring overshoot.
  The component checks leave preferences unchanged. A live gift purchase/return
  recording also confirms the covered transition without a scene flash.
- `gift-current-recording`: the final purchase/open/return/run flow passes again
  after darkening gift reward text. Its current video is
  `gift-current-recording/gift-box-return.mp4`; `return-filmstrip.png` shows the
  successive fade frames. The Android runtime error log is empty.

The installed APK matches the tested build:
`c6b0cdc915e9f72b3ca91bde503bad6b38e65a3495b165998fad87485bee38df`.
The original save was restored after every instrumentation phase. Final read-only
review of that save shows **Best: 598** and the pending bronze Runner reward in
`gift-current-recording/original-achievements.png`. The bank, claim state and every
unrelated progress key match the backup; only the intended saved-score/earned-Runner
reconciliation changed. The game is left at the main menu.

Fresh contrast screenshots are in
`contrast-progression-preview/achievements-physical/page-progress.png` and
`challenges-progress.png`; personal-best and dev-purchase captures are under
`dev-controls-final/achievements-hardware` (all paths below `.build-tmp/hardware`).

## Earlier interface verification

The earlier debug app, instrumentation APK, and Android lint passed. Its build output is in
`.build-tmp/hardware/build-void-price-fit-final.log`.

- All **14 progression/claim tests** and **3 popup lifecycle/layout tests** passed
  during `achievement-rework`. That run subsequently caught a native circular-reveal
  animator crash. The return now uses a Canvas mask and a normal ValueAnimator;
  the final device checks below cover the crash's pause/resume and detach paths.
- `achievement-final-pass`: **8/8 passed in 81.425 seconds**. These cover scrolling,
  reward claims, moving gameplay, narrow layouts, all six secret-item disclosures,
  actual shop payments, the void transition, and real Android wall-flick input.
  Its Android runtime error log is empty.
- `achievement-popup`: the moving-run popup test passed again while recording
  an **11.4-second MP4**. The original preferences were restored afterward.
- `achievement-final-visuals`: all eleven review states passed in **66.826 seconds**,
  including locked/unlocked, claim-ready/claimed, completed challenges, and the
  three discoveries. Final state screenshots are in its `achievements-physical`
  directory. These are historical captures, before the latest gradient and shop changes.
- `gradient-final-checks`: **14/14 passed in 114.071 seconds**. This adds actual
  painted-bounds checks for all category icons, tier medals/connectors, and locked
  coin rewards; isolated developer reset tests; and the refined gradient/card layout.
  The reset checks cover the read-only unlocked status, dev-only placement, Cancel,
  Back, full reset persistence, settings preservation, and immediate menu refresh.
- The final void/shop checks caught a price wrap at 280 dp and 150% text size.
  Measuring the coin span and digits at the exact final pixel size fixes Android's
  nonlinear font scaling. That case, animation detach, and actual purchases of all
  three Wardrobe-only secrets pass in `void-price-final`. Its remaining failure was
  an assertion comparing a pulsing button's transformed visible height against its
  untransformed layout height; the check now compares transformed bounds.
- `void-return-final`: the corrected complete purchase/run flow passed in
  **25.536 seconds**, including full button visibility and bottom clearance after
  the return animation, no secret card or disclosure in the main shop, an actual
  Wardrobe cube purchase, mystery-box opening, and a gameplay achievement.
- The three popup lifecycle/layout checks pass with the new title-and-reward-only
  layout in `void-wardrobe-final`. `achievement-toast-final` also passed the moving
  run test in **20.53 seconds**, recording an **11.43-second MP4** of the compact popup.
- `achievements-gradient-final`: the focused diamond-ready review passed in
  **7.8 seconds**, with fresh captures of the subdued gradient, centred category
  icons and medals, claim buttons, and `500,000` formatting on the en-IN phone.

Those focused phases reported no Android runtime errors; their original preferences
were restored and verified byte for byte after every test.

The earlier implementation also passed the thirteen existing rebalancing tests.
Earlier screenshots describe older designs; use the specific final captures listed
below. Device capture folders retain older files, so only each phase's requested
states should be treated as fresh evidence.

## What the checks exercise

`AchievementsProgressTest` uses the Android preference store to verify quiet
tracking before the 3,000-coin unlock, strict Runner thresholds, persistent
single-run records, lifetime coins, power-up counts, mystery-box payment and
reward boundaries, and the void's gate and discoveries. Claim checks cover
oldest-pending-tier order, previously earned medals, duplicate attempts, challenge
payouts, locked/dev states, and atomic coin/claim persistence. Claim bonuses do not
advance lifetime-coin progress.

`AchievementsInteractionTest` checks the real attached page:

- Partial scrolling, flings in both directions, and claim updates cannot paint
  over the title or back button. Header pixels are compared throughout the flow.
- A previously offscreen reward bar starts filling when scrolled into view,
  without rebuilding or manually drawing the page first. Its final pixels are checked.
- Real claim buttons pay once, update the bank, and retain scroll position.
  Completed challenges show a check and claim state. Big Bubble hides its record;
  Bouncer retains its personal best without a completed progress bar.
- A controlled run crosses 500 through the production score session while the
  world keeps moving. Obstacles are cleared for repeatability; the run is not paused.

`AchievementsHardwareFlowTest` clicks actual shop buttons and checks a complete
purchase/open/return flow. The darkness slot always remains the coin sink, including
at discovery milestones. All three secret cosmetics are absent from the main shop
and can be purchased only in the wardrobe. Its animation covers the whole HUD
with an opaque scene. The final mask contracts progressively into the updated offering
card; touch and Back stay blocked until completion. Background/resume and detach
are explicitly exercised. All three secrets expand to only question marks in the
wardrobe, including rapid toggles and an actual cosmetic purchase afterward.
The isolated 280 dp / 150% font-scale fixture checks text and control bounds without
changing the phone's display settings. That unattached layout capture precedes
animation, so its bars are initially empty; attached scrolling tests verify the fill.
The capped `1,000,000,000` void price is also checked at that width and font scale:
coin and digits share one centred line, fit within the button, and remain fully visible.

`BouncerAchievementTest` injects actual Android touch events through the input
system. Moving into the outside lane does not count; a held gesture counts once.
Separate outward flicks queued during delayed GL frames all count. It verifies
66/67, ignored input while paused, a fresh run's reset, and the persisted award.

`AchievementToastTest` checks pause/resume preservation, detach/reattach, tier
coalescing, narrow text fitting, and touches passing through the popup. The popup
contains only the title and reward amount, with no “Ready to claim” line.

## Local evidence

Current evidence is under `.build-tmp/hardware`:

- `achievements-gradient-final/achievements-physical/page-diamond-ready.png`
- `gradient-final-checks/achievements-hardware/achievement-bounces-checked-and-claimed.png`
- `gradient-final-checks/achievements-hardware/reset-dev-bottom.png` and
  `reset-confirmation.png`
- `void-price-final/achievements-hardware/void-price-280dp-font150.png`
- `void-return-final/achievements-hardware/void-tenth-offering-continues.png`,
  `void-fullscreen-*.png`, and `void-return-early/middle/late.png`

The current live popup video is
`.build-tmp/hardware/achievement-toast-final/live-achievement-popup.mp4`.
Its screenshot is `achievement-toast-final/achievements-hardware/live-running-achievement-popup.png`.
Each phase retains its own instrumentation output and preference backup;
no backup is overwritten.

## Equipped abilities — 2026-09-17

Installed and tested on the attached Moto G7 Power (Android 15, 720×1520).
Both debug APKs build; lint completes with zero errors and six existing warnings.
The current ability and achievement pass has 86 passing device checks:
53 initial core checks, the corrected narrow-panel layout check, 28 achievement
and rebalancing regressions, three jackpot UI checks, and one renderer capture
fixture. The initial panel assertion counted invisible trailing wrapping spaces;
it now checks visible line width, complete text, and panel bounds.

Coverage includes weighted/fractional lottery tickets, deterministic jackpots,
ordinary loot forfeiture, duplicate pickup protection, Gold coin accumulation,
Coal rendering/accounting, stacked Plasma durations, Bubblegum cooldowns,
Cloud physics, Mint double-jump lifecycle, Zappy collision and input behavior,
new challenge thresholds, mute controls, claims, and existing shop/void flows.

Evidence: `.build-tmp/hardware/abilities-core-01`,
`abilities-regression-01`, and `abilities-visual-01`. The latter contains actual
renderer screenshots of three Zappy animation stages, Coal pickups, both Gambler
flash samples, Cloud, Lottery/Bubblegum/Mint ability panels, and the jackpot HUD.
Screenshots were visually reviewed for legibility, overlap, and effect cleanup.
Each device phase preserves all original preferences and restores/verifies them
byte for byte. Jackpot visuals use a deterministic test draw; they do not depend
on waiting for a naturally occurring jackpot or alter the player's real bank.

## 2.4 integration and ability polish — 2026-09-17

Fast-forwarded achievements to `615bbae` (verified against fetched origin/main),
retaining the local feature work and resolving the shared gameplay/UI changes.
Updated launch palettes for all 25 cubes, including changed Gambler/Cloud colours
and Black void; the generator now accepts underscored prices and symbolic IDs.
The new track shards are excluded from Power Collector progress.

Removed ability stat pills, kept titles, simplified descriptions, added the
notched Lottery ticket icon and inline jackpot coin, and renamed Gold's ability
Midas Little Toe. Dev mode gives virtual unlimited shards without spending real
stock, and 2% lottery odds per coin-value ticket. Box odds remain 1.3%; normal
coin odds remain 1 in 100,000. Gambler's colour cadence is 2.5× faster.

Physical Moto G7 Power: 102 checks passed (63 core/ability/shard checks, 31
achievement/gameplay/shop regressions, eight launch/render review checks).
Build and lint pass. Evidence is in `.build-tmp/hardware/abilities-24-core-01`,
`abilities-24-regression-01`, and `abilities-24-visual-01`; build logs use
`abilities-24-build-02.log`. Reviewed Lottery, Midas, and Floaty panels directly
from the phone. All device phases restored and byte-verified the original save.

## Trophy-room achievement page — 2026-09-22

Rebuilt the achievement page (`AchievementsView`, `AchievementCards`,
`AchievementHero`, `AchievementIcons`): a trophy ring with the share of all 26
medals earned, medal and challenge tallies, one CLAIM ALL button, the five tiered
families as medal cards with each target under its medal, and the six challenges
as a two-column tile grid. The void's price button went back to the shop's gold
one.

Physical Moto G7 Power (ZY323NNKTB): `AchievementAlignmentTest`,
`AchievementDevModeUiTest` and `AchievementToastTest` pass 8/8, and the run
dropped from over 14 minutes to 47 seconds once the page was made to settle.
Build and lint pass. Checked on the phone at three save states — a new account
with nothing earned, a mixed account, and everything claimed — plus a recording
of opening the page and collecting every reward with CLAIM ALL.
