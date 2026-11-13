# Validation 4.16.2 (Forge 1.20.1)

Date: 2026-10-22

Contains the 4.16.1 fixes (silent world ignores the player's own sounds; landscape memory never draws under OptiFine) plus:

## Fixes

- Pasha Boost 1.3.5 (embedded): a dedicated server no longer fails to load. `DistExecutor.safeRunWhenOn(CLIENT, () -> BoostClient::register)` linked the client class on the server (`Attempted to load class Screen for invalid dist DEDICATED_SERVER`); registration is now a plain `FMLEnvironment.dist` check.
- Analyzer: a generic projectile tag no longer overrides the element the source classification names (a mod "light beam" tagged as a projectile stays LIGHT instead of becoming PROJECTILE). New reason `native.projectile_element` with en/ru text. A MAGIC fallback, melee/physical attacks and plain projectiles are unchanged.
- GameTest `configuredTimingsChangeActualProgressAndCatalogue` updated to the analyzer rules since 4.14: later stages without manual times follow the analyzer suggestion; with analysis off they inherit the category (both asserted).
- Build: `runGameTestServer` runs without the embedded jars (Distant Horizons casts GameTestServer to DedicatedServer; the reobfuscated Pasha Boost calls SRG names absent in the dev runtime and hung the light engine on save). Shipped JARs still contain both.
- QA harness `run-qa.ps1`: reads the process handle so Windows PowerShell 5.1 reports the real exit code (a passing run was reported as a crash).

## Checks run

| Check | Result |
| --- | --- |
| Pasha Boost 1.3.5 clean build + core tests | passed |
| Wheel 4.16.2 offline + online build, all core test suites (incl. new analyzer cases) | passed |
| GameTest server | all 48 required tests passed (2 failed before the fixes) |
| `run-qa.ps1` 24-mod matrix (`combined-24-mods-cached`) | MULTIMOD_QA PASS, all server sections PASS |
| In-game, Forge without OptiFine (`ingame-vanilla.json`) | PASS: landscape full stage, distant renderer on, normal world visible (screenshot); 12 real own footsteps → 0 silent-world markers; zombie step still marked |
| In-game, profile `ForgeOptiFine 1.20.1` + OptiFine HD_U_I6_pre6 (`ingame-optifine.json`) | PASS: OptiFine detected, distant renderer stays off, normal world visible; 12 own footsteps → 0 markers; zombie step marked |
| Diagnostic: DH forced on under OptiFine, no shader pack | world still visible, so the 4.16.0 disappearance needs more than OptiFine alone. The user's log shows the OptiFine shader pack failing on DH programs (`dh_water`, GL link errors), the likely trigger. The 4.16.1 rule (no DH drawing under OptiFine) covers both cases. |

## User log (4.15.0 session) findings, not wheel bugs

- 16 396 tooltip errors: Forbidden Arcanus' tooltip mixin is incompatible with OptiFine (`InjectionError: LVT in GuiGraphics.renderTooltipInternal`); it breaks tooltips on every screen, including the J menu.
- `Missing pack_format`: a resource pack without `pack_format`, reported when Pasha Boost reloads resources once for font compaction.
