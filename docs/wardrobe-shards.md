# Shard collectibles and wardrobe rendering

Shard-only cubes keep their full-height action button visible at every count.
The button contains the matching crystal icon, a `count/100` label and shard
name; its face fills in the shard color. At 100 it becomes Unlock. Owned items
use the ordinary Equip button at the same height. Nothing is drawn over the
cube to represent shard progress.

Ordinary track pickups include Ember, Frost and Void crystals. A single shared
shard slot occurs in each shuffled pickup bag, materializing every second
eligible offer after score 100; jetpacks materialize every second offer after
the same threshold. Each shard chooses one of the three colors uniformly.
Developer mode changes pickup spacing but preserves these gates. Red Pill keeps
its independent 1/30 filter relative to the magnet bag slot. Rejected offers
remain empty. Mystery boxes award 2–4 shards.

Touching a crystal removes that pickup and records exactly one shard of its
color. The session banks these alongside coins once at game over, then shows
only the collected colors and counts on the results card. Crystals use one
batched geometry pass, with terrain height, distance fog and faceted lighting.

Bubble previews ease hue, saturation, rim and fill over 380 ms, with a smooth
crossfade between color modes. Hue interpolation follows the short arc and
keeps the two ends of a film in the same hue range. Electric bubbles use a
continuous traveling glow, and an expiring shield breathes instead of flashing.
The outer sphere surface is drawn once, avoiding overlapping transparent back
faces; the mesh has a smoother silhouette. Preview wobble is smaller and slower,
and the cube's browse spin integrates its deceleration across variable frames.

Ghost uses near-white color, a faint
emissive lift and 72% body opacity instead of pulsing to gray.

## Moto validation (2026-09-14)

- 23 device checks passed: wardrobe layouts and all ten bubble hues, actual track
  collection, duplicate-bank protection, results display, spawn rates, Red Pill,
  existing abilities and wardrobe motion/controls.
- Seeded normal sample: 408 jets, 204 shards (69 Ember, 77 Frost, 58 Void).
  Developer sample: 1,840 jets, 920 shards (310, 312, 298).
- Separate 120-frame steady-preview probes, without screenshot overhead:

| Preview | Median frame interval | 95th percentile |
| --- | ---: | ---: |
| Classic | 16.74 ms | 18.30 ms |
| Plasma bubble | 16.72 ms | 18.15 ms |
| Lava bubble | 16.65 ms | 18.03 ms |

The figures above describe the September 14 build, before the track rate doubled.
These are finite on-device samples, not an across-device performance guarantee.
`ShardReviewTest` accepts `captureShards=true` for screenshots;
`WardrobeFrameProbeTest` accepts `probeWardrobe=true` for timing measurements.

## Shard rebalance (2026-09-15)

Track crystals now materialize every second eligible shard offer, twice the
previous rate and matching jetpacks. Mystery-box shard amounts are uniformly
2–4 instead of 5–30. Existing inventory is preserved.

Seeded normal track sample: 407 jets and 407 shards (157 Ember, 115 Frost,
135 Void). Developer sample: 1,840 jets and 1,840 shards (606, 610, 624).
Ninety forced shard-box rolls covered all of 2, 3 and 4, and the persisted
inventory matched the reward totals exactly.

Galaxy retains its original purple pulsing appearance. The experimental portal
material and its visual checks live on the separate `galaxy-cube` branch.
