# Validation 4.13.0

All native tests use separate worlds under multi-mod-compat-1.20.1/probe. Actual
user worlds, installed mods and configs are not edited. Existing 4.12 dist retained.

- `coreTest`: all existing timing/speed/protection/classifier/FPS/client rotation
  tests plus MetaMechanicsTest pressure continuity and safe notification expiry,
  counter bounds, size limits and danger exclusions pass.
- `run-pocket-border413-anchor-20261022-053842`: actual inventory pressure,
  conservation/hotbar, physical wheel NBT and ownership, nested container rejection,
  shared border unchanged, locked crossing, full immunity, disable restores damage,
  actual unequip returns to saved entry anchor; PASS.
- `run-utility-client413-20261022-051626`: actual master-volume-zero SoundEngine
  event, native action-bar injection, exact repeat x2, danger retained, LOD disabled
  before stage IV; PASS.
- `run-defeat413-respawn-window-20261022-053333`: actual three-hit lethal attack,
  actual player respawn/clone with retained NBT, repeat two-hit prefix produces
  warning before lethal third, ghost path packet delivered; PASS. Fixture waits
  out vanilla respawn invulnerability before replaying attacks.
- `run-performance413-slow-ticks-20261022-053345`: deliberate actual 110ms tick
  work sustained 15s starts low-TPS adaptation; after stage IV, native state NBT
  replacement count falls 40 -> 2 over 40 progressing ticks; PASS.
- `run-landscape413-two-threads-20261022-053321`: native blocks form a red-roof
  house. Public terrain API confirms real red wool in recorded data. At 220 blocks
  vanilla client chunk (0,0) is absent and house remains visible in screenshot.
  Toggle off removes LOD but retains archive. Median frame interval ON 16.6874ms,
  OFF 16.6825ms; p95 ON 17.8062ms, OFF 17.5212ms; cap60; PASS. Does not establish
  an uncapped FPS speedup or zero overhead in other scenes.
- `run-landscape413-disk-restart-20261022-053756`: full JVM restart, copy of
  previous isolated world/SQLite; original roof is read before its vanilla client
  chunk is received. Screenshot saved; PASS.

The supplied DH JAR is unmodified; bridge calls public API. A real issue found
in native QA was task starvation with a one-thread, very-low-duty shared engine
pool. Fixed to two workers/0.5 duty (0.25 under heap pressure), smaller 32-chunk
inactive working radius, no distant worldgen, camera/frustum filtering, active
detail feedback. No System.gc, forced third-party cache clear or archive area quota.

This verifies the stated fixtures. It does not establish every possible third-party
sound/message/attack or arbitrary mod conflicts. 4.12 compatibility coverage remains
documented separately; new integration smoke results are appended when completed.

Final integration smoke: `run-utility413-optifine-parent-20261022-054328`
actual Options -> VideoSettings -> OwnVideoScreen with supplied OptiFine and
bundled engine PASS. An initial fixture passed a null parent to OptiFine, which
its constructor rejects; the real user transition uses an initialized Options
parent and passes. `run-all24-413-utilities-20261022-054341` combined24 PASS.
