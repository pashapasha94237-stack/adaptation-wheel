# Validation 4.17.0 (Forge 1.20.1)

Date: 2026-10-22

## New: adaptations to things outside the world

Eleven new `MetaMechanic`s (switchable in the J menu and in `adaptationwheel-utility-server.toml`), plus an extended memory adaptation. One event starts adaptation; all four stages then run while the wheel is worn (about 4.5 minutes). Facts that outlive a world are kept in `<game dir>/adaptationwheel/` (`outside-memory.properties`, `session.lock`); the world keeps `adaptationwheel-world.properties`.

| Mechanic | Trigger | Stage 1+ | Stage 4 |
| --- | --- | --- | --- |
| Foreign mods | mod list differs from the last launch | names added/removed mods | + mobs and damage types per new mod, updated versions |
| Log errors | 30 errors per minute in the game log | message | identical WARN/ERROR lines trimmed (first 3 and every 500th kept, FATAL never hidden) |
| Renamed world | world name or folder changed | former name | all former names, first-met date |
| Config editing | the wheel's config file changes while playing | file name, number of values | each value: old → new |
| System clock change | wall clock vs monotonic clock differ by ≥30 s | shift | real session time |
| Crash memory | session marker left by a crash | world, time, crash report file | 30 s Resistance V + fire resistance + water breathing in the same world |
| Idle (AFK) | 3 min without moving or turning | "you stood still for…" | after 1 min idle mobs drop and cannot pick you as target, mob damage cancelled until you move |
| Inventory in combat | inventory/container open while recently hurt by a mob | stage 2: mob damage halved while open | mob damage cancelled for 20 s after opening; hitting a mob ends it |
| Network loss | local network adapters go down (nothing is sent anywhere) | lost / back after N | online build analyses locally without waiting |
| Wheel update | wheel version differs from the last launch | old → new | changelog line |
| Console commands | cheat command typed by the wearer (permission ≥ 2) | — | `/kill` against the wearer refused |
| Memory pressure (extended) | existing heap trigger | — | render distance −2 chunks every 10 s of pressure (min 6), restored after 30 calm s |

Implementation: `core/OutsideSignals` (pure logic), `server/OutsideServer`, `server/LogWatch` (log4j filter in every logger context, Forge gives class loaders separate contexts), `server/NetworkWatch`, `ConfigWatch` (Forge did not report a client config edit in testing; files are also polled by modification time and re-read like Forge does), `client/OutsideClient`.

## Checks run

| Check | Result |
| --- | --- |
| Core tests incl. new `OutsideSignalsTest` (mod/config diffs, clock jumps, repeat limiter, error window, idleness) | passed (offline + online builds) |
| GameTest server | all 53 required tests passed (5 new: `/kill` refusal and re-enable, cheat vs ordinary command, AFK target/damage protection ending on movement, inventory ×0.5 at stage 2 and full block at stage 4 ending on attack, outside event delivered once and only to wearers) |
| Real client, part A (`outside-a.json`) | real InventoryScreen blocks a zombie hit, closing restores damage; 1000 identical errors → 5 lines in latest.log, FATAL kept; log-error event in chat; editing `adaptationwheel-utility-client.toml` on disk → chat shows `heapPressureThreshold: 0.85 → 0.9`; real `/kill` refused; then save + hard halt (simulated crash) |
| Real client, part B (`outside-b.json`, renamed world folder, stored version 4.16.2, an extra stored mod) | chat: removed mod, former world name and history, crash in "Compatibility tests" with protection, update 4.16.2 → 4.17.0 and changelog; Resistance active |
| `run-qa.ps1` 24-mod matrix | MULTIMOD_QA PASS |

## Re-check (same day) — fixes found by review

- Config editing: the FPS adaptation saves its own config every 30 s; that would have been reported in chat as an edit. Own saves now go through `ConfigWatch.ownSave` (snapshot refreshed before writing, file time remembered after). Verified in a real client: own save → no chat line; external edit → still reported.
- Log filter: `isErrorEnabled()`-style checks reach context filters without a message; they were counted as errors and, while trimming, could silence a guarded logger after three checks. Messageless checks are now ignored (GameTest `logLevelChecksAreNotErrors`).
- World start can no longer fail on a damaged `outside-memory.properties` / `session.lock` / world marker (each step guarded; crash time parsed safely).
- Event ids are random and compared for equality, so moving the system clock backwards between sessions cannot hide events. Clock/network/config/log messages expire after 60 s instead of greeting a player who joins hours later.
- AFK: logging in no longer reports "you stood still for …" from the previous session.
- Inventory in combat: a new 20 s window at most every 30 s (reopening, or a modified client spamming "inventory open", cannot make it permanent; GameTest `reopeningInventoryDoesNotExtendProtection`). The client reports its inventory only while wearing the wheel with the adaptation enabled.
- Memory pressure: restoring the render distance never overwrites a distance the player chose meanwhile.
- Crash protection is given only on the rejoin itself (first 2 minutes online), not when the wheel is put on much later.

Re-run after the fixes: core tests (offline + online), 55/55 GameTests, real client part A without OptiFine and with OptiFine HD_U_I6_pre6 (`outside-a-optifine.json`), part B (crash + renamed world + old version + removed mod; session marker removed after a clean exit), the 4.16 in-game checks on 4.17.0 with and without OptiFine (world visible, 12 own steps → 0 markers), 24-mod matrix — all passed.

## Not verified in game

- Network loss (would require disconnecting the machine); the adapter check is local and untested on this PC with the network actually down.
- System clock change in a running game; covered by unit tests only.
- Memory-pressure render-distance reduction (heap never reached the threshold in QA).
- AFK in the real client (GameTest only).
