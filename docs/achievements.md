# Achievements and the void

The achievements worktree started from `rebalancing` at `e97b018` and has been
fast-forwarded to the 2.4 release (`615bbae`), with the achievement changes retained.

## Discovery and presentation

Achievements cost 3,000 coins in the shop. Buying the unlock reveals the large
white trophy on an orange button beside cosmetics on the main menu. The developer control is
above sound; the section player is above haptics.

Progress is tracked before purchase. Existing best score, lifetime coins, owned
cubes, opened boxes, and current bubble stock migrate into the system. Historical
power-up pickups and wall bounces were not previously recorded, so those counters
begin with this version. Purchasing the unlock acknowledges existing medals
quietly rather than replaying notifications.

One scrolling collection contains all eleven achievements in the shop's card style:
coloured header bands with circular icon badges, dark tinted bodies, and a subdued,
opaque navy-to-teal backdrop. Ink labels on the bands and white progress counters
separate the content clearly. The list clips below the header and coin bank. Each family
has a visual centred on its badge, a connected medal track,
animated progress, and a reserved reward area. Bronze, silver, gold, and diamond
differ in colour; diamond also has its own silhouette. Medal faces and connectors
share one centre line. The current medal gets a round ring, earned medals are solid,
and future medals are faint. Numbers always use groups of three,
such as `500,000`, regardless of the device locale.

An earned tier offers a gold CLAIM button. Its matching target shows a full bar;
claiming flies coins into the bank and advances the card to the next pending
claim or goal. Previously earned tiers remain claimable. Bronze/silver/gold/diamond
pay 250/750/2,000/5,000 coins per family. Big Bubble and Cookie Clicker each pay
2,000; Bouncer, Stay Centered, Homeress, and Gambliphobic each pay 1,500.
The claim marker and coin balance persist together, and each tier pays once.
Claim bonuses enter the bank without advancing the lifetime-coin counter.
Completed challenges show a green check and their claim status. Bouncer retains
its personal best, including after claiming; the other challenges hide completed progress.
Good Runner uses the subtitle “Single-run score” and shows progress only beside its
bar, without a duplicate Best label. It reconciles older saved scores immediately.
Power Collector and Mystery Seeker use “Power Ups collected” and “Mystery boxes opened”.
After all rewards are collected, the
card contracts to a compact Claimed state with a green check. Future payouts appear
as an inline coin amount such as `+750`, with no separate panel or reward label;
only available claims use the bright gold button.

| Achievement | Bronze | Silver | Gold | Diamond |
| --- | ---: | ---: | ---: | ---: |
| Good Runner | >500 | >1,000 | >2,000 | >5,000 |
| Lifetime Coins | 5,000 | 25,000 | 100,000 | 500,000 |
| Unlocked Cubes | 5 | 10 | 15 | 24 |
| Power Collector | 100 | 1,000 | 5,000 | 10,000 |
| Mystery Seeker | 10 | 50 | 100 | 300 |

Big Bubble requires 1,000 stocked bubbles at once. Bouncer requires 67 deliberate
side-wall hits in one run. Holding a smooth drag beyond an edge does not repeatedly
count as a hit. Mystery boxes do not count as power-ups; bubble, magnet, score
multiplier, jetpack, and red-pill pickups do.
Distinct wall flicks count even when Android queues several before one render
frame; there is no simulation-time filter that discards separate gestures.

Stay Centered requires a score of **100 or higher** without leaving the middle lane
before that score. Leaving and returning invalidates that run, even when both inputs
arrive within one rendered frame. Portals that widen or narrow the road preserve
the middle lane. Progress is the best eligible score, capped at 100, and starts fresh
because older runs contain no lane history. Completion persists if the player later
changes lanes. It shares the ordinary unlock gate, quiet discovery, claim flow and
dev-mode support.

Homeress requires a score of **60 or higher** before picking up any track coin.
Any physical coin pickup invalidates the run, even when Coal makes it worthless
or Gambler converts it into a losing lottery ticket. A mystery-box jackpot does
not itself count as a coin pickup. Best eligible progress is capped at 60; after
completion, picking up a coin does not revoke the achievement.

Gambliphobic requires **10 missed mystery boxes in one run**. Each uncollected box
counts once after it passes the player. Collected boxes, including forfeited
Gambler lottery boxes, do not count as missed. Run counters reset on restart;
the best missed-box count persists, capped at 10.

Cookie Clicker requires **1,000 lifetime mute toggles**. Each deliberate sound
button press in the main menu or pause sheet counts once, in either direction.
Loading settings, refreshing controls, and changing haptics do not count. The
counter starts with this version, persists between sessions, and is cleared by
Reset Progress. These three challenges use the same unlock gate, quiet discovery,
claim flow, completed checkmarks, and dev-mode support as the other challenges.

See [cosmetic abilities](cosmetic-abilities.md) for equipped-cube and bubble effects,
including how Gold, Coal, and Gambler affect collected coins.

Run notifications are silent, show one medal at a time for 2.8 seconds, and start
at least 10 seconds apart. Pending tiers from the same achievement coalesce into
the highest tier. The compact white candy popup shows a medal/check, the achievement
name, and its coin reward. A new run gets a 2.2-second grace period. Pausing hides
and preserves an interrupted popup;
results and store awards never spill into the next run. Earned medals persist even
if a run is interrupted or stocked bubbles are later consumed.

## Mystery-box pricing

Purchases use the same loot and 3D opening sequence as collected boxes. The reward
is banked at purchase, and the gift stage presents that exact reward without a
second roll or payment. The displayed payment balance excludes any coin reward
until the opening, preserving the surprise.

Gift rewards use a centred card capped at 360 dp, with room for its spring animation.
The bottom action has a fixed-height slot, and long reward names, amounts and icons
fit together even with enlarged text. The skip prompt is hidden while opening.
Returning from a purchased box fades to an opaque cover, waits for the gift camera
to exit and the shop to render, then fades the cover away. Input remains blocked
through that handoff; returning never charges or grants another reward.

The existing pity rule forces a non-coin pull after two consecutive coin pulls.
Its long-run reward weights are coins 3/7, cosmetics 4/35, shards 8/35, and bubbles
8/35. Coin rewards average 1,100; four bubbles have a shop value of 480. Cosmetic
value follows the actual uniform-category, then uniform-item selection among
remaining eligible items. Shards have no direct coin price, so their value uses
the average regular cube price, prorated by the shards required for a cube and the
17.5-shard average drop. Exhausted collections use the actual fallback rewards.

All coin prices for cubes, bubble skins and trails are ten times their previous
values, including discovered void cosmetics. Free defaults and shard requirements
stay unchanged. Bubble consumables retain their existing prices.

For a fresh collection the expected value is approximately 1,204.79 coins, rounded
up to **1,300**. A fully completed collection has an expected value of approximately
745.71, rounded up to **800**. The displayed price recalculates from the remaining
collection and always rounds up to a multiple of 100. Secret cosmetics are never
part of this reward pool, including after discovery.

## The void

The bottom shop item appears at 100,000 lifetime coins, or immediately in dev mode. It is a
black card holding a live black hole (a tilted accretion disk, its far side lensed over the
shadow, stars creeping inwards), the void's current line, and the price. No progress
towards the discoveries is shown: every offering should feel hopeless.

Paying plays a show on the 3D shop stage (`game.stage.VoidShow`, drawn by
`core.gfx.VoidRenderer`, timed by `core.VoidBeats`). The button punches in, then the shop
sheet drops away and its header fades in place; only the bank pill stays. The sky drains and
a black hole opens above the cube. Coins arc out of the bank, which drops by one coin's worth
as each leaves, then fall onto the disk and orbit down, reddening into the horizon. The cube
is pulled up, stretched, and falls behind the horizon. The disk spins up, the stars drain in,
and the hole shrinks to a point of light. It goes supernova: a white-out, a cooling core,
soft shockwave rings in the disk's plane and hot shards. A pulsar is left sweeping its beams
through a nebula. The cube is rebuilt from shards, the void types its next line (centred, line
by line) in `ui.VoidShowOverlay`, and the camera returns before the sheet slides back up with
the next offering in place. A tap after the blast skips to the return. The GL thread owns the
clock (`Stage.voidClock`), so backgrounding pauses both halves together; touches and Back are
blocked throughout. The shaders compile while the shop sits idle, so the tap never stalls.
Milestone offerings keep the discovery in the dialogue; the cosmetic itself is found in the
wardrobe.

With `n` previous offerings, the next costs `5,000 + 2,500n + 500n²` coins.
Costs rise in clean 500-coin steps, with a defensive cap of one billion coins.

| Offering completed | Newly purchasable cosmetic | Price |
| ---: | --- | ---: |
| 10 | Black void cube | 300,000 |
| 20 | Afterimage trail | 500,000 |
| 30 | Event horizon bubble | 750,000 |

These are discoveries, not automatic ownership grants. The main shop always keeps
one offering with its current dialogue and next price. Discovered cosmetics appear
only in the wardrobe, where they can be purchased; the main shop has no secret
previews, cosmetic purchase buttons, or tappable question marks. Undiscovered items
have no wardrobe preview or position marker. The three secret cosmetics retain
their mystery ability buttons in the wardrobe, displaying only `???` in the same
white explanation card as ordinary abilities. Rapid toggles cancel the preceding transition.
The original ordinary Void cube is preserved. Further offerings continue
after all three discoveries, with rotating dialogue.

See [verification notes](achievements-verification.md) for build and physical-device evidence.

## Developer reset

Dev mode adds a red RESET PROGRESS button after every other shop item. Its native
confirmation sheet covers the whole HUD; Cancel or Back leaves progress untouched.
Confirming clears coins, upgrades, cosmetics, shards, achievements, void progression,
and saved scores, then refreshes the shop and menu immediately. Sound, haptics,
sensitivity, and dev mode are retained. The previous bank saved when entering dev
mode is cleared too, so disabling dev cannot resurrect the deleted balance.
The green achievement-unlocked status is read-only and has no press animation.
Achievement tracking, notifications and claims also work in dev mode. Permanent
claim payouts are added to the saved real bank as well as the temporary dev bank,
so turning dev off cannot erase a collected reward. Dev bypasses only the void's
lifetime-coin entry gate; offerings still cost coins and unlock cosmetics in order.
