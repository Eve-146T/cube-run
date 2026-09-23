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

The page is a trophy room on a deep indigo-to-teal backdrop. It opens with a trophy
inside a ring that fills with the share of all 68 medals and challenges earned, the count under it,
and a tally of bronze, silver, gold, diamond and completed challenges. One gold
CLAIM ALL button collects every waiting reward at once; it is only there while
something is waiting, and it shrinks away once it has paid.

Below that, two labelled groups. MEDALS holds the eleven tiered families as wide
cards in the shop's card style: a glossy coloured band with a circular icon badge,
the four medals on one track with each target written under its medal, and the next
goal with its payout. The link between two medals fills as you approach the next one.
The current medal wears a ring, a reward waiting to be claimed glows and beats, claimed
medals are solid and later ones are faint. CHALLENGES holds the 24 one-off dares as
tiles, two to a row, dropping to one per row on narrow screens or at large text sizes;
tiles in a row share the taller one's height, so their goals line up. Numbers always use
groups of three, such as `500,000`, regardless of the device locale; medal targets are
written short (`5K`, `500K`). The list clips below the header and coin bank.

A card with a reward waiting is lit: a gold edge and a sheen that sweeps across its band
every few seconds. Claiming — from the card or from CLAIM ALL — flies coins into the
bank, then the medal that was claimed stamps down onto the fresh card inside two rings of
its own colour. Fully claimed families recede into slate with a green check.

Bronze/silver/gold/diamond pay 250/750/2,000/5,000 coins per family. Big Bubble and
Cookie Clicker each pay 2,000; Bouncer, Stay Centered, Homeress, and Gambliphobic each
pay 1,500. Previously earned tiers remain claimable. The claim marker and coin balance
persist together, and each tier pays once. Claim bonuses enter the bank without advancing
the lifetime-coin counter. Completed challenges show a green check and their claim status.
Bouncer retains its personal best, including after claiming; the other challenges hide
completed progress. Good Runner uses the subtitle “Single-run score” and shows progress
only beside its bar, without a duplicate Best label; once its number is beaten, the counter
shows the number it beat. It reconciles older saved scores immediately. Power Collector and
Mystery Seeker use “Power Ups collected” and “Mystery boxes opened”. Future payouts appear
as an inline coin amount such as `+750`, with no separate panel or reward label; only
available claims use the bright gold button.

The tiered thresholds, bronze through diamond: Good Runner beats 500, 1,000, 2,000
and 5,000 points in one run; Lifetime Coins collects 5,000, 25,000, 100,000 and
500,000; Unlocked Cubes owns 5, 10, 15 and 24; Power Collector picks up 100, 1,000,
5,000 and 10,000 power-ups; Mystery Seeker opens 10, 50, 100 and 300 boxes.

The added families are Globetrotter (1/2/3/4 distinct bonus worlds across runs),
Long Hauler (10,000/100,000/500,000/2,000,000 metres across runs), Shardsmith
(25/100/250/750 collected shards), Regular (10/100/500/2,000 started runs),
Bubble Popper (10/100/500/2,000 spent stocked bubbles), and Near Miss
(50/500/2,500/10,000 near-miss bonuses). Portal Hopper waits for the portal rework.
Stage Fright counts terminal crashes within two seconds of crossing the start gate, excluding
revived crashes. Bankrupt needs a paid transaction that takes a positive balance
to zero, so an empty new account does not qualify.

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
black card holding a live black hole (a smooth tilted accretion disk, a lensed ring over the
shadow, stars creeping inwards, a faint nebula, a glow creeping round the card's edge), the
void's current line, and the same gold price button as every other card. No progress
towards the discoveries is shown: every offering should feel hopeless.

Paying plays a show on the 3D shop stage (`game.stage.VoidShow`, drawn by
`core.gfx.VoidRenderer`, timed by `core.VoidBeats`). The button punches in, then the shop
sheet drops away and its header fades in place; only the bank pill stays. The cube slips out
of sight (it takes no part in the offering), the sky drains and a black hole opens. Coins arc
out of the bank, which drops by one coin's worth as each leaves, then fall onto the disk and
orbit down, reddening into the horizon. The disk spins up, the stars drain in,
and the hole shrinks to a point of light. It goes supernova: a soft lilac bloom, a cooling core,
soft shockwave rings in the disk's plane and hot shards. A pulsar is left sweeping its beams
through a nebula. The cube is rebuilt from shards, the void types its next line (centred, line
by line) in `ui.VoidShowOverlay`, and the camera returns before the sheet slides back up with
the next offering in place. On the way back the cube eases into the shop's own pose and the
shop's rays keep their normal pace, so nothing snaps when the show hands over. A tap after the blast skips to the return. The GL thread owns the
clock (`Stage.voidClock`), so backgrounding pauses both halves together; touches and Back are
blocked throughout. The shaders compile while the shop sits idle, so the tap never stalls.
Milestone offerings keep the discovery in the dialogue; the cosmetic itself is found in the
wardrobe.

With `n` previous offerings, the next costs `5,000 + 2,500n + 500n²` coins.
Costs rise in clean 500-coin steps, with a defensive cap of one billion coins.

The tenth completed offering reveals the black void cube (300,000 coins), the
twentieth the afterimage trail (500,000), and the thirtieth the event horizon
bubble (750,000).

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
