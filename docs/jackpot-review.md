# Jackpot review build

Build the repeatable phone preset with:

```sh
./gradlew :app:assembleDebug -PjackpotTestWorld=true
```

This debug-only preset equips Gambler on every launch, enables developer lottery odds,
loops the obstacle-free MOTHERLODE coin field, supplies a one-hour magnet, and disables
portals and unrelated pickups. Normal debug builds and release builds omit the preset.
Add `--ei section -1 --ez idle_bot true` to the launch intent to see a jackpot on an
ordinary obstacle road with the dev autopilot playing.

## The show

A jackpot holds the run for 7.2 s and plays in the run's own 3D world
(`game/Jackpot.kt`). One timeline, `core/JackpotBeats.kt`, drives it and the HUD counter
(`ui/JackpotCounter.kt`). The GL thread owns the clock (`Stage.jackpotClock`), so pausing
freezes both together.

- Hit (0 s): the world stops on the winning pickup. The cube lifts off, turns gold and
  spins up to a drum roll while the camera cranes behind it. Gates and obstacles the run
  has already passed are removed under the flash, so they cannot block that camera.
- Burst (1.3 s): rays blow open behind the cube, a fanfare plays, and a shockwave ring
  spreads over the road. Every obstacle ahead shatters in a wave, so the run resumes on a
  clear road. The cube erupts in a fountain of 3D coins, and the counter pops in and
  rolls up to the win.
- Slam (4.6 s): the counter lands on the total.
- Gather (4.9 s): the coins spiral back into the cube.
- Return (6.1 s): the camera swings back to the chase, and the counter arcs into the HUD
  coin pill. The pill shows the new total when the counter lands (6.65 s). The run winds
  back up to speed at 7.2 s.

The win belongs to the run as soon as it is drawn (`coinsRun`). Only the HUD's run haul,
and with it the unbanked achievement total, waits for the counter to land. Several wins
while the show plays are added to the same show. No text is shown, only the coin icon and
the number.
