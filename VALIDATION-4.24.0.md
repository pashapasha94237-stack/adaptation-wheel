# Validation 4.24.0 — local factual case memory and guided shots

Date: 2026-10-25. Minecraft Java 1.20.1 / Forge. Base: complete 4.23.0 working tree.

## Change

- `LearnedPatterns`: bounded local case memory, indexed by owner, direct entity, attributed effects and boss phase.
  At least three distinct factual agreeing abilities are needed to refine an unknown weak category. Any contrary
  exact signature abstains. Guesses, names and online advice cannot train the model. Explicit registry facts win.
  Tentative confidence is 74–80%; current work, stage duration and earned protection are never copied or reset.
  Cases are saved in additive player NBT and copied on player recreation.
- `GuidedShots`: genuine swept close misses (default 2 blocks from the target's bounding box) unlock the four-stage
  `environment:near_misses` adaptation. Stage four steers owned arrows, crossbow bolts and supported physical gun
  bullets, preserving speed and each weapon's original collision and damage. Moving-target interception, loaded
  visible target search, allies/PvP/pet exclusions, wall checks, disable/unequip, bounded tracked shots and unload cleanup.
- Explicit optional CGM/Scorched Guns `getShooter` and native projectile-hit event adapters. No gun code or gun JAR
  is bundled. Tested MrCrayfish's Gun Mod Unofficial **1.4.20**, Forge 1.20.1, and Scorched Guns **0.2.8**.
  CGM artifact: https://www.curseforge.com/minecraft/mc-mods/mrcrayfishs-gun-mod-unofficial/files/8399743
- Configured utility settings: `near_misses`, `aim.nearMissRadius`, `aim.homingRange`. RU/EN catalogue and stage hints.
- No Boost, DH, sword, World Slash, inventory, existing menu layout or 24-mod compatibility rewrite.

## Preservation audit

Before editing: `artifacts/4.24-audit/working-tree-before-4.24.zip` snapshots the sources and tracked probe.
All 1,738 existing localization entries in each language are unchanged; 11 keys added in each language.
The complete embedded `META-INF/jarjar` payloads (including metadata) compare byte-for-byte with 4.23.0.
The old 4.23.0 offline JAR remains SHA256
`ac164efc604ebabd8068193121c92e5a73bc2a9dd20a93ba205e998402a4d17c`.
The user's installed mods, configs and worlds were not changed. Native clients use fresh isolated directories.
The 17 client source files, both item implementations, all ten coremods, four compatibility resource tables
and 23 GameTest source/resource files also compare byte-for-byte with the pre-edit snapshot. No baseline source
file was removed.

## Verification

- Clean offline build and all core tests: **PASS** (`build-4.24.0-offline.log`). New case-memory tests cover
  contradictions, minimum support, owner/projectile/phase isolation, correction, persistence, bounds, authoritative
  precedence, no immunity transfer and stable current work. Geometry tests cover exact swept near misses,
  moving interception, unchanged speed, real intersections, distant shots and finite safeguards.
- Clean online build and all core tests: **PASS** (`build-4.24.0-online.log`). Online/offline differ by capability
  resource; the local analyzer remains available in both. MenuLayoutTest retains its 17,633 layout cases.
- Real Forge GameTest server: **63/63 PASS** (`gametest-4.24.0.log`), including existing damage protection,
  timing/configuration, inventory, sword, exact 300 HP World Slash and classification tests.
- Native gun probe `run-aim424-head-20261025-034246`: **PASS**. Three real registry-confirmed incoming cold
  abilities teach an opaque projectile; it still removes HP at stage zero; learned cases survive an NBT reload.
  An actual BowItem close miss starts adaptation; fully adapted bow fired away and native CrossbowItem shot hit
  through their original collision paths. Disabled/unequipped control shots miss, solid walls do not cause fake HP
  loss. Original CGM and Scorched projectile entities learn close misses, redirect shots fired away and cause
  actual native HP loss. Disabled CGM restores misses. Actual unadapted arrow hits do not become miss adaptations;
  learned guidance excludes scoreboard allies.
- Final offline JAR native regression (`9b2eb02dcb0c8631aa10236f64c3331168ff554c762e8c902dbd981613816898`):
  `guns`, `screens-compact`, `screens-wide`, `smart-analysis-native`, `deep-native-analysis`, `analyzer-pasha`
  and `combined-24-mods-cached`: **PASS**. The full 24-mod group checks existing native abilities, debuffs,
  projectiles, direct-health/control hooks, First Aid limb damage, full immunity and disabled controls; zombie
  x1.30 real-hit acceleration and 20% stage-one actual HP/limb reduction remain correct.
  Run directories and exit results are recorded in `artifacts/4.24-audit/native-final-regression.log`.
- Final OptiFine clients: `guns-optifine`, `screens-optifine`, `ingame-optifine`: **PASS**. Native arrows/CGM/
  Scorched repeat the actual HP and control checks with OptiFine; J menu and Boost screens render; world remains
  visible after landscape adaptation, own footsteps are excluded and external mob sounds remain detected.
  All ten final native regression groups exited 0. Compact and wide screenshots were visually inspected.
- Both production JARs pass ZIP CRC checks and contain identical 173 runtime classes, correct offline/online
  capability markers and version strings, and no probe/GameTest classes. Online JAR SHA256:
  `15ee385ebc3e688cd50a606828037c48c71b516a84643d16ef6f9bfee21dd5c9`.

## Limits

This is an offline game-feature analyzer, not a language model with general reasoning. Its confidence is an
evidence score, not a calibrated probability. Hitscan weapons with no moving entity need their own adapter.
Unknown mod bullets are supported only when a recognizable owned projectile exposes a usable owner and movement.
Steering does not pierce walls, shields, invulnerability or another mod's native damage cancellation. Default
search is 64 loaded blocks. No claim that all gun versions or all future mods are verified.
This release makes no new FPS benchmark claim: the reviewed 4.23 optimization payloads are unchanged.
