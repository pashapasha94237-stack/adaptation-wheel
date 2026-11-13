# Validation 4.19.0 (Forge 1.20.1)

Date: 2026-10-23

## Changes

### Sword of Extermination worn on the forearm
- The item model is now `forge:separate_transforms`: `extermination_sword_icon.json` (the old model, used for the inventory icon, the ground and item frames) and `extermination_sword_worn.json` for both hands — the blade runs along the outer side of the forearm at scale 0.55, three bandage straps wrap the arm and the blade's base, the point extends about 0.7 block past the fist.
- First person (`client/ForearmBlade`): vanilla hides the arm whenever the hand holds an item, so for this item `RenderHandEvent` draws the arm with the empty-hand transform (`ItemInHandRenderer.renderPlayerArm`, swings and equipping included) and then the blade through the same path `ItemInHandLayer` uses in third person.
- Parry pose: `UseAnim` changed from `SPEAR` to `NONE` — in Forge 1.20.1 the vanilla use-animation pose (spear throw, arm straight up) is chosen before `IClientItemExtensions.getArmPose`. The new `ADAPTATIONWHEEL_FOREARM_GUARD` pose raises the forearm across the chest; first person lifts the arm so the blade stands before the crosshair. `BLOCK` was not used because vanilla would then treat the blade as a shield. Parry logic is unchanged (it checks `isUsingItem` and the item). Side effect: a player raising the blade is no longer reported to nearby players as a "windup" (`CombatObserver` only counts BOW/SPEAR/CROSSBOW).

### Adaptation menu (J) and HUD
- `client/WheelUi`: shared flat widgets (panel with gold accent, flat buttons, labelled switches, progress bars, stage squares, category chips).
- `AdaptationScreen` rewritten on it, same features: global and night-vision switches, Mine/Catalogue tabs, category picker (now a popup grid with colours instead of a separate screen), search, per-entry toggle (click the switch at the right of a row), details. Rows are 25 px instead of 37 and scroll instead of paging: at 960×640 with GUI scale 2 eight entries fit instead of four. Each row: category colour stripe, name, category + short status, stage squares (5th in purple for bosses), protection %, switch and a progress line for running adaptations. Running entries sort first. The J key closes the menu again (unless typing in search). Summary line: learned / complete / in progress (FPS adaptation included) or catalogue size / met.
- Details: name, category and boss chips, a stage timeline (one segment per stage with its protection and time, current stage filled to its progress), sections "Status", "How it works", "Attack analysis", "Stage times"; all previous lines kept.
- Corner HUD (`AdaptationOverlay`): 30 px per running adaptation instead of 54 (name + stage squares, stage text in the category colour, time left and speed, progress bar), hint moved into the title line, hidden when nothing is running.

### Pasha Boost 1.5.0
- Optimizations (found with JFR on OptiFine + 23 mods with 1.4 already on; client entity ticking was 13.7 % of the render thread, particle rendering 18 % and 380 MB of garbage per 12 s):
  - `ClientEntityTickMixin` + `EntityOptimizer.skipClientTick`: ordinary mobs beyond 1.25 × the drawing distance tick on the client every fourth game tick (so do other mods' client tick handlers for them). Exempt: players, bosses (100+ HP), named, passengers/vehicles, glowing, leashed, hurt or dying entities. Server untouched.
  - `QuadParticleVertexMixin`: the four corner vectors of `SingleQuadParticle.render` are reused on the render thread.
  - `QuadParticleLightMixin`: the per-frame `ClassValue` check is cached per particle.
  - `UniformBiomes` (fixed during 4.18.0 validation): uniform sections are found by their 64 cells.
  - JVM opt-outs `-Dpashaboost.disableEntityTickThrottle`, `-Dpashaboost.disableParticleScratch`; counters `entityTicksSkipped`, `particleVectorsReused`.
- UI: new F8 screen (`BoostScreen`, flat teal widgets in `BoostUi`) with Overview (FPS, 1 % low, p95, frame-time graph, memory, savings per second of every optimization, presets with the current one highlighted, last A/B result) and Settings (switches and sliders; settings fixed by the adaptation reward are shown locked, sliders show the limit actually in force, e.g. "Mobs: 80 blocks → 48"). Video settings: Minecraft's option controls are repainted flat after their own render (tooltips and narration kept). HUD: compact card with coloured FPS, 1 % low, p95, frame graph, memory and Boost state; hidden while the Boost screen is open.

## Checks run

- Build: `release-4.19.0.ps1` — Pasha Boost 1.5.0 clean build and core tests (FrameStats, ParticleBudget, AdaptiveBudget 13, VisibilityCache 11, LightChunkCache, CompactGlyphRows), wheel offline and online builds with all core tests. JARs, sources zip and SHA256SUMS in `dist/`.
- GameTest: 57/57 on 4.19.0 (server side is unchanged by the later client-only fixes).
- Real client, multimod probe (`run-qa.ps1`):
  - `forearm.json` / `forearm-optifine.json` (`ForearmQa`): first person idle, swing and parry; third person front, quarter and back, idle and parry — PASS on Forge and OptiFine. Screenshots reviewed: blade along the outer forearm with straps, parry raises the forearm across the chest, no spear pose.
  - `screens.json` / `screens-optifine.json` (`ScreensQa`): 13 screenshots each — J menu (Mine, Catalogue, category popup, 4 detail pages), HUD with five running adaptations, Boost video settings (3 pages), Boost Overview and Settings — PASS. The first build drew the video sliders' value text twice (vanilla text is batched in front of the repaint); fixed by flushing and repainting at z+1, re-checked on the release JAR (below).
  - Release JAR (`-4190r` runs, all PASS): `screens-optifine` and `screens` (video sliders now drawn once, flat), `forearm`, `slash-optifine` (3 zombies at 12/30/55 blocks cut through a wall), `ingame-optifine` (world visible after full landscape memory, own footsteps ignored), `perf-24mods`.
- Performance (`PerfQa`, 400 mobs + 400 block entities + 30 campfires, phases Boost off/on/off/on, render distance 8, averages of the two phases):

  | Setup | Boost off | Boost on (1.5.0) | 1 % low on |
  |---|---|---|---|
  | OptiFine, flat world (`perf-optifine`) | 85.7 | 273.2 | 147.2 |
  | OptiFine + 23 mods (`perf-23mods-optifine`) | 118.9 | 281.0 | 68.5 |
  | Forge + 24 mods (`perf-24mods`) | 50.5 | 152.5 | 65.5 |

  Single runs vary by about ±20 FPS on this PC (4.18.0 once gave 174 with 24 mods, another day 144–155), so versions are compared below only in alternating runs.
- 4.18.0 vs 4.19.0, Forge + 24 mods, Boost on, order 18 · 19 · 19A · 19A · 19 · 18 (19A = 4.19.0 with `-Dpashaboost.disableEntityTickThrottle=true -Dpashaboost.disableParticleScratch=true`):

  | Variant | avg FPS | 1 % low | p95 frame |
  |---|---|---|---|
  | 4.18.0 (Boost 1.4.0) | 144.4 / 154.7 → **149.6** | 69.5 / 58.3 | 12.34 / 11.81 ms |
  | 4.19.0 (Boost 1.5.0) | 159.2 / 159.1 → **159.2** | 61.2 / 82.4 | 10.22 / 10.55 ms |
  | 4.19.0, new paths off | 152.2 / 145.2 → 148.7 | 65.3 / 44.6 | 11.56 / 11.73 ms |

  Boost 1.5 is about 6 % faster on average and its p95 frame time is 1.7 ms (14 %) shorter; with the two new paths off it falls back to the 1.4 level, so the gain comes from them.
- OptiFine + 23 mods, same JAR, new paths on (B) vs off (A), 12 runs (A B A B, then a 2×2 factorial B T S A A S T B with T = tick throttle off, S = particle scratch off): average FPS is lost in the noise (all B runs 281.5, all A runs 280.2), but p95 frame time is shorter in every B run — B 6.18 / 6.50 / 6.58 / 6.69 ms, A 6.69 / 6.83 / 6.90 / 7.09 ms (−0.4 ms, 6 %). Counters per 12 s phase: ≈ 50 000 client entity ticks skipped, ≈ 18 million particle vectors reused.
- Known, not caused by this release: with OptiFine, chat shows "OpenGL error 1282" once per session around Distant Horizons' GL context creation; present since 4.13.

## Not verified
- Pasha's own game directory, mod set and shader pack.
- Long play sessions with the entity tick throttle: mods that animate distant mobs on the client tick them every fourth tick beyond 1.25 × the mob drawing distance (full rate again before they are drawn).
- GUI scales other than 2 and window sizes other than 960×640 for the new screens (layouts are computed from the window size, but only that size was photographed).
