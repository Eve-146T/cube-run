# Jackpot review build

Build the repeatable phone preset with:

```sh
./gradlew :app:assembleDebug -PjackpotTestWorld=true
```

This debug-only preset equips Gambler on every launch, enables developer lottery odds,
loops the obstacle-free MOTHERLODE coin field, supplies a one-hour magnet, and disables
portals and unrelated pickups. Normal debug builds and release builds omit the preset.

The jackpot currently uses a small fade-in/fade-out placeholder reading
"real animation goes here", with the awarded coin total beneath it. Gameplay keeps
running. The casino takeover, particles, jackpot sounds/haptics, and simulation hold
have been removed. Simultaneous wins update the total without restarting the entrance.

The retained `JackpotToastTest` checks cover pause/detach recovery, placeholder text,
narrow large-font layout, touch-through, and automatic retirement.
