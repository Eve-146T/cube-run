# Upgrade and mystery-box tuning

Target: roughly **15–25 hours** for a regular player to max permanent upgrades.
Existing levels and owned items are preserved; new prices apply to future purchases.

Prices are rounded to 50 coins: `base × (level + 1)^1.65`. Duration upgrades use
base 750, Rich coins uses 2,400, and the other permanent perks use 1,500.

| Upgrade group | Levels | First level | Final level | Total |
| --- | ---: | ---: | ---: | ---: |
| Each of four duration upgrades | 10 | 750 | 33,500 | 143,600 |
| Rich coins (1× → 3×) | 10 | 2,400 | 107,200 | 459,600 |
| Each of three other perks | 5 | 1,500 | 21,350 | 51,500 |

All 65 permanent levels cost **1,188,500 coins**. Consumable bubble and Second
Wind prices are unchanged. Developer mode's temporary bank is large enough to
try every upgrade; leaving it restores the real bank.

The reference earning rate is the player's observation: about 5,000 coins per
five minutes at 3×, or 1,000/min. Assuming the same collection skill at 1× gives
about 333/min before Rich coins. Buying Rich coins first, then the remaining
upgrades, takes about **18.4 hours** if two boxes per five minutes contribute
their expected coin rewards. This is a tuning model, not a measured completion
time: collection skill, upgrade order, box frequency, magnet uptime and spending
on cosmetics or consumables change it. Longer player sessions should validate it.

Mystery boxes now have separate, reachable reward bands: 10% cosmetic, 20%
shards, 20% bubbles and 50% coins. After two consecutive coin rewards the next
box must give a non-coin reward; the streak survives app restarts. If a cosmetic
or shard category is exhausted, it falls back to remaining shards or bubbles.
The previous branch ordering made the bubble reward range unreachable.

Coin rewards are 500–1,000 (80%) or 2,000–3,000 (20%). Bubble rewards contain
3–5, and shard rewards contain 5–30. Including the two-coin limit, long-run coin
rewards occur in about 3/7 of boxes, averaging about 471 coins per box across
all outcomes (about 189 coins/min at the reference box rate).

A used or expired bubble starts a **10-second gameplay cooldown**. Tapping again
during cooldown spends no stock; the HUD shows remaining seconds. Pause freezes
the cooldown, and a new run resets it. Automatic Second Wind protection retains
its existing behavior.
