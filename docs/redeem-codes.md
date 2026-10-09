# Redemption codes

Settings → Codes accepts `coin500` for 500 bonus coins. Each code works once per
saved profile. Input is trimmed and case-insensitive; unknown codes leave progress
unchanged. Redemption survives app restarts, and concurrent submissions cannot pay
twice. The code marker and reward share one committed progress transaction.

Add entries to `RedeemCodes.catalogue`, keeping each definition's id stable even
when adding an alias. `Effect.Coins` grants a bonus without increasing gameplay
coin statistics. `Effect.Unlock` persists a flag that future secret settings can
read with `Progress.isCodeUnlocked(key)`. New effect types can extend the sealed
effect model, with their persistence in `Progress.redeemCode` and their success
message in `CodeDialog`.

Redemption writes run on a worker so disk commits never block settings input.
Developer mode preserves coin rewards in the real bank as well as its temporary
bank. A full bank leaves the code available until the complete reward fits.
