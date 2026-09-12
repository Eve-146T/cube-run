# Section bot reference

24,012 trials; 58 patterns (53 library sections, three hill patterns, warm-up and breather); six generated variants per pattern.

All work and artifacts are local. These are bounded-search witnesses and reference ratings, not proofs of impossibility or measurements of human players.

## How to read the numbers

- Speed is world units per simulated second. Ordinary gameplay starts at 12.4, five boosts reach 21.6, cruise is 27, and the ground ceiling is 30. Jet flight can reach 52.5 but grants immunity; the speed survey deliberately tests **grounded, unassisted** traversal, even above 30.
- Fixtures use the actual Track decoder and gap calculation. Each repeats its section twice, preserving lane-walk changes across the join. The six variants cover both mirrors and all three entry lanes, with seeds 73–78 and three animation-clock phases. This is sampled coverage, not every random seed or every pairing of different sections.
- Physics runs at 60 Hz with one classic swipe per allowed input slot. Perfect allows one gesture per frame; the pro model spaces gestures by at least 8 frames (7.5/s); relaxed uses 12 frames (5/s). These are explicit analyst-selected input budgets, not empirically established human limits. The solver knows the course ahead, like a memorized run.
- Search uses a beam of 32, retried at 96 when it fails. Survival discards fatal trajectories. Ratings ignore optional loot and paid protection; live play values coins and powerups while rejecting predicted crashes.
- A successful route is moved toward the center of verified timing windows, then replayed with independent seeded jitter on each action: ±1 frame (16.7 ms) and ±3 frames (50 ms), 12 trials each. Input order is preserved and same-frame deliveries are serialized. This probes tolerance; it does not simulate visual recognition or fatigue.
- Highest pass means **all six variants** passed at that sampled speed. Pro robust additionally requires at least 11/12 ±1-frame jitter replays for every variant. A failed search is “not solved”; it is not proof that a better bot or human cannot succeed.
- Moving obstacles and input sampling can make success non-monotonic with speed. Read both highest pass and first gap. Values at the top of the tested grid are lower bounds (≥90), not finite ceilings. Longitudinal collision checks are expanded across the frame to avoid counting obvious high-speed skips; every accepted route is also checked against the literal discrete model.

## Ratings at speed 30

Ratings describe found survival routes at the ordinary maximum: 1 needs no gestures; 2 has ≤3 gestures in its busiest second and ≥90% relaxed ±50 ms survival; 3 has ≥95% pro ±16.7 ms survival; 4 has ≥80%; 5 falls below that. They are bot-derived reference labels, not a definitive minimum skill ranking.

| ID | Section | Rating at 30 | Perfect highest / first gap | Pro cadence highest | Pro robust highest | Peak gestures/s at 30 |
| ---: | --- | --- | --- | ---: | ---: | ---: |
| -5 | VALLEY GATES | 3 Skilled | ≥90 / none tested | ≥90 | ≥90 | 4 |
| -4 | CREST HOP | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 3 |
| -3 | RIDGE WEAVE | 3 Skilled | ≥90 / 82.5 | 55 | 45 | 3 |
| -2 | WARM-UP | 1 Gentle | ≥90 / none tested | ≥90 | ≥90 | 0 |
| -1 | BREATHER | 1 Gentle | ≥90 / none tested | ≥90 | ≥90 | 0 |
| 0 | FIRST STEPS | 3 Skilled | 87.5 / 90 | 50 | 37.5 | 5 |
| 1 | WEAVE | 1 Gentle | ≥90 / none tested | ≥90 | ≥90 | 0 |
| 2 | HOP | 3 Skilled | ≥90 / none tested | 70 | 47.5 | 3 |
| 3 | SLALOM | 3 Skilled | 85 / 75 | 50 | 37.5 | 5 |
| 5 | LEAP & WEAVE | 4 Expert timing | ≥90 / none tested | 55 | 32.5 | 5 |
| 6 | CLOSING GATES | 3 Skilled | ≥90 / none tested | 37.5 | 30 | 3 |
| 7 | DUCK & DODGE | 3 Skilled | ≥90 / none tested | 65 | 37.5 | 5 |
| 8 | GAUNTLET | 5 Very tight | ≥90 / none tested | 60 | 37.5 | 5 |
| 10 | STORM | 3 Skilled | ≥90 / none tested | 65 | 47.5 | 4 |
| 11 | TRAPS | 3 Skilled | 70 / 75 | 35 | 30 | 4 |
| 12 | HURDLES | 2 Moderate | ≥90 / none tested | ≥90 | 52.5 | 2 |
| 13 | STAIRCASE | 5 Very tight | ≥90 / none tested | 52.5 | 37.5 | 5 |
| 15 | SKYLIGHTS | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 2 |
| 16 | THE WAVE | 3 Skilled | 60 / 65 | 60 | 42.5 | 4 |
| 17 | TUNNEL | 4 Expert timing | ≥90 / none tested | ≥90 | 80 | 4 |
| 19 | PEEKABOO | 3 Skilled | ≥90 / none tested | ≥90 | ≥90 | 4 |
| 20 | PINCER | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 2 |
| 21 | GATEKEEPER | 3 Skilled | ≥90 / none tested | 70 | 47.5 | 4 |
| 23 | BLENDER | 3 Skilled | ≥90 / none tested | ≥90 | 35 | 4 |
| 24 | TAR PITS | 2 Moderate | ≥90 / none tested | ≥90 | 85 | 3 |
| 25 | PUMP HOUSE | 2 Moderate | ≥90 / none tested | ≥90 | 55 | 3 |
| 26 | STOMP | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 2 |
| 27 | SWEEPERS | 2 Moderate | ≥90 / none tested | ≥90 | 55 | 3 |
| 28 | CHASM RUN | 5 Very tight | ≥90 / none tested | ≥90 | 47.5 | 6 |
| 29 | MACHINE ROOM | 3 Skilled | ≥90 / none tested | ≥90 | 70 | 4 |
| 30 | HELLRIDE | 4 Expert timing | ≥90 / none tested | ≥90 | 82.5 | 4 |
| 32 | HIGH ROAD | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 1 |
| 33 | ROOFTOPS | 2 Moderate | ≥90 / none tested | ≥90 | 80 | 3 |
| 34 | OVERPASS | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 1 |
| 35 | TRAIN YARD | 2 Moderate | ≥90 / none tested | ≥90 | 85 | 1 |
| 38 | RAMP UP | 3 Skilled | ≥90 / none tested | 70 | 32.5 | 4 |
| 40 | SKYBRIDGE | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 1 |
| 42 | TRAPDOORS | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 3 |
| 43 | CRUSH HOUR | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 3 |
| 44 | ROOF RUNNER | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 1 |
| 45 | LEAPFROG | 3 Skilled | ≥90 / 21.6 | ≥90 | 55 | 4 |
| 46 | FINALE | 3 Skilled | ≥90 / none tested | ≥90 | ≥90 | 4 |
| 47 | GOLD RUSH | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 1 |
| 48 | LOW BRIDGE | 4 Expert timing | ≥90 / none tested | ≥90 | 52.5 | 4 |
| 50 | TREASURY | 2 Moderate | ≥90 / 75 | 82.5 | 50 | 2 |
| 52 | TAR & FEATHER | 2 Moderate | ≥90 / none tested | ≥90 | 70 | 3 |
| 53 | COIN CANYON | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 1 |
| 55 | GAUNTLET II | 2 Moderate | ≥90 / none tested | 82.5 | 70 | 3 |
| 56 | MOTHERLODE | 1 Gentle | ≥90 / none tested | ≥90 | ≥90 | 0 |
| 57 | SPRINGBOARD | 2 Moderate | 50 / 52.5 | 50 | 50 | 2 |
| 60 | SWING SET | 3 Skilled | ≥90 / none tested | ≥90 | 40 | 4 |
| 61 | BOUNCE HOUSE | 2 Moderate | 50 / 52.5 | 50 | 50 | 3 |
| 62 | HIGH JUMP | 2 Moderate | 50 / 52.5 | 50 | 50 | 3 |
| 65 | TRAMPOLINE | 2 Moderate | 50 / 52.5 | 50 | 50 | 1 |
| 66 | ROOF HOP | 2 Moderate | ≥90 / none tested | 85 | 60 | 2 |
| 68 | PENDULUMS | 3 Skilled | ≥90 / none tested | ≥90 | ≥90 | 4 |
| 70 | BIG AIR | 3 Skilled | 50 / 52.5 | 50 | 50 | 4 |
| 72 | GRANDFATHER | 2 Moderate | ≥90 / none tested | ≥90 | ≥90 | 2 |

The CSV and JSON beside this file contain jitter scores for sorting and further analysis. See `docs/bot.md` for device validation, input measurements, limits, and commands.
