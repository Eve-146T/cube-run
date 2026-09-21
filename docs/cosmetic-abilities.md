# Cosmetic abilities

Abilities are taken from the equipped cube and bubble skin when a run starts.
Trying on a cosmetic in the wardrobe does not grant its ability. Existing item IDs
are preserved: Neon cube becomes Gambler (ID 1), and Lemon becomes Cloud (ID 17).
The Neon bubble is unchanged. Cosmetic prices, ownership, and shard requirements
are unaffected by these ability additions.

## Cubes

| Cube | Ability | Behavior |
| --- | --- | --- |
| Gold | Midas Little Toe | Collected coin value ×1.2. |
| Eclipse | Zappy | Instantly changes to the target lane, with an electrical trace, departure afterimage, arrival flash, sound, and haptic tick. |
| Plasma | Power stretch | Power-up durations ×1.25. |
| Bubblegum | Bubble saver + Quick bubble | Retains the 35% chance not to consume a stocked bubble; activation cooldown ×0.7. |
| Coal | Coal | Track coins become charcoal lumps and give no coins. |
| Gambler | Lottery | Forfeits ordinary coin and mystery-box rewards in exchange for jackpot chances. Flashes red and yellow. |
| Cloud | Floaty | Softer gravity and takeoff keep the cube airborne longer, at approximately its usual jump height. |
| Ghost | Phase | Existing ability: pass through one obstacle per run. |
| Speedy cube | Speed | Existing ability: running speed ×1.3. |
| Black void | ??? | Existing hidden ability remains undisclosed in the wardrobe. |

Gold's multiplier applies after Rich Coins and the Kaleido world's coin multiplier.
Fractional value accumulates across pickups in the run before whole coins are
shown and banked. It does not multiply mystery-box rewards or achievement claims.

Zappy changes the collision position immediately; it does not sweep through
intermediate lanes, collect their pickups, grant invulnerability, or slow the run.
Rapid opposite swipes remain separate teleports. Portal lane remapping keeps the
cube aligned with its lane. The brief visual traces have a bounded reusable pool.

Plasma extends magnet, score multiplier, jetpack, red-pill, and bubble durations.
It also scales Safe Start and revival shields. Bubblegum's ordinary five-second
bubble cooldown becomes **3.5 seconds**, both after a crash pops the bubble and
after natural expiry. Bubble saver still requires a stocked bubble to activate.

Cloud uses jump velocity 6.9 and gravity 16, versus ordinary values 8.4 and 26.
The slam retains ordinary gravity, and jetpack/hover behavior remains separate.

Coal pickups still disappear on contact and respond to a magnet. They trigger a
charcoal burst but never increase run coins, the bank, or lifetime collected coins.
They are physical pickups for Homeress and therefore invalidate its coinless run.
Mystery boxes retain their ordinary rewards when using Coal.

## Lottery

Each **one coin of value** gives a **1 in 100,000** chance to win **250,000 coins**.
Rich Coins and the Kaleido multiplier determine how much value a track coin would
have yielded. Fractional values carry across pickups within the run: ten pickups
worth 1.4 each create fourteen tickets. A pickup worth three gives three tickets,
so the mean wait is approximately 33,333 such pickups. This is an average, not a
guarantee or pity threshold.

Every collected mystery box instead gives one independent **1.3%** chance at the
same **250,000-coin** jackpot. Whether it wins or loses, the box is forfeited and
never added to the post-run opening queue. A collected lottery box is not a missed
box for Gambliphobic, and forfeiting it does not advance Mystery Seeker.

Weighted coins use a geometric waiting-time sampler equivalent to independent
per-ticket rolls; one weighted pickup can win more than once. Losing pickups give
no ordinary coins. Jackpot earnings appear in the run coin total with celebratory
feedback and are banked once through the ordinary run-end path. Their lifetime-coin
progress uses the same accounting as other run earnings. Lottery tickets and
fractional carry reset with each run.

## Bubbles

| Bubble | Ability | Behavior |
| --- | --- | --- |
| Mint | Double jump | One additional airborne jump while the shield is active. |
| Plasma | Long bubble | Bubble duration ×1.3. |

Mint's extra jump becomes available after takeoff and resets on landing. It is
available immediately when the shield activates during a jump, and is unavailable
after the shield pops or expires. It cannot grant repeated midair jumps and does
not override jetpack flight or hover-world controls.

Plasma cube and Plasma bubble multiply together: **1.25 × 1.3 = 1.625**, or **62.5%
longer** than the upgraded base bubble duration. A ten-second base lasts 16.25
seconds with both. The same duration multipliers apply to Safe Start and revival
shields; they do not increase the number of crashes a bubble absorbs.

## Related achievements

The three new challenges retain the 3,000-coin achievements unlock and quiet
retroactive discovery. Progress tracks before purchase; completing a challenge
makes its reward claimable once after achievements are unlocked.

| Challenge | Requirement | Reward |
| --- | --- | ---: |
| Homeress | Reach score 60 without a physical coin pickup. Coal and lottery coin pickups invalidate eligibility. | 1,500 coins |
| Gambliphobic | Miss 10 distinct mystery boxes in one run. Collected lottery boxes do not count. | 1,500 coins |
| Cookie Clicker | Toggle mute 1,000 times over the lifetime of the save, using the menu or pause sound button. | 2,000 coins |

See [achievements](achievements.md) for the full eleven-card collection, claim
behavior, and reset semantics. This document describes implementation; it does not
claim completion of physical-device verification.

Developer mode provides unlimited virtual shards without spending real shard stock.
Lottery uses 2% per coin-value ticket in developer runs; normal coin odds and 1.3% box odds are unchanged.
Gambler flashes red/yellow 2.5 times as fast as the ordinary Strobe cube.
