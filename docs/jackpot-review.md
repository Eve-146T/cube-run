# Jackpot review build

Build the repeatable phone preset with:

```sh
./gradlew :app:assembleDebug -PjackpotTestWorld=true
```

This debug-only preset equips Gambler on every launch, enables developer lottery odds,
loops the obstacle-free MOTHERLODE coin field, supplies a one-hour magnet, and disables
portals and unrelated pickups. Normal debug builds and release builds omit the preset.

The full-screen jackpot holds simulation while its reels, payout, and coin fountains
play. Pause, detach, reset, and normal completion release that hold. Simultaneous wins
update the displayed total without restarting the entrance.

Verified on Moto G7 Power (`ZY323NNKTB`): build and lint passed; all three
`JackpotToastTest` tests passed, including hold cleanup, pause/detach recovery, narrow
large-font layout, and touch-through. Phone recordings reviewed against Deep Space
and bright Candy Fields show repeated awards, the complete takeover, and resumed
gameplay. Sound/haptic cues follow the reel stops and impact and respect user settings;
physical haptic feel and speaker output were not independently measured.
