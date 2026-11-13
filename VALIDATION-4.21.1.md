# Validation 4.21.1 (Forge 1.20.1) — Adapt Boost 1.6.1

Date: 2026-10-24

## Report
Pasha: the whole game crashes when he dies and at random moments. His pack (2026-10-23): BioForge 2.2, Configured 2.2.3,
Ice and Fire 2.1.13 + Citadel 2.6.0, Jujutsu Craft v51 + Player Animator, OreExcavation, Patchouli 85, Mowzie's Mobs 1.8.2 +
GeckoLib 4.8.4, TLSkinCape 1.38, wheel 4.21.0 (Adapt Boost 1.6.0, DH 3.3.3); OptiFine in the earlier sessions of that day.

His crash reports of 2026-10-23 show two crashes, both caused by Adapt Boost and present since Pasha Boost 1.4:

1. **Death with Fabulous graphics** (23:35, 23:43, no OptiFine; options `graphicsMode:2`): NPE on a null Fabulous render
   target (`LevelRenderer.getItemEntityTarget()`) in the first frame after `Runtime optimization released`, which logs
   right after `Burmalda died` (death takes the wheel off, so FPS adaptation releases Boost). Cause: in 1.20.1
   `graphicsMode().set()` does not rebuild the renderer — only the video settings button calls `allChanged()`. Boost set
   Fast and later restored Fabulous through `set()`. Any `allChanged()` in between (dimension change, DH setup, F3+A)
   ran with Fast and dropped the transparency chain, so Fabulous came back without buffers.
2. **Water splashes with OptiFine** (04:04, 04:07, 22:52, 23:09): NPE `fx is null` in `CustomColors.updateWaterFX`,
   "Exception while adding particle", while swimming or from dripping water. Boost's particle budget returned null for a
   rejected decorative particle (`splash`, `rain`, `mycelium` are on its list); OptiFine recolours splash, rain and
   bubble particles right after creating them without a null check whenever custom water colours are loaded (his
   resource pack). `particleWaterColor`, `waterColors` or `blockColormaps` set is enough.

The other reports of that day are not the wheel's: two "Mod Loading has failed" while he was assembling the pack
(Ice and Fire without Citadel, Mowzie's Mobs without GeckoLib, Jujutsu Craft without Player Animator), and on 10-21
Anomaly Rephased's deliberate jumpscare crash.

## Change
- `RuntimeGraphicsProfile`: after Boost changes or restores `graphicsMode` (and after re-applying it after a resource
  reload) the renderer is rebuilt once with `allChanged()`, as the video settings button does. On disconnect the
  rebuild is left to the next world.
- Before every frame (`RenderTickEvent` START, whether or not Boost is earned): when Fabulous is in effect but the
  transparency buffers are missing, `graphicsChanged()` recreates them; logged once. If that does not produce buffers
  (a renderer that keeps Fabulous without them on purpose), it is not retried until the state changes.
- `ParticleOptimizer`: a rejected vanilla particle is returned as an invisible `NoRenderParticle` that is already removed
  and leaves the engine on its first tick, instead of null. Rejected mod particles still return null (a mod may cast
  the result). The budget does not count these stand-ins as active particles, or a burst of rejections would make the
  next tick reject everything too.
- `NeighbourTableView.row()` of a property the block does not have is empty, as vanilla's table (found in review).
- Version: wheel 4.21.1, embedded Adapt Boost 1.6.1; the wheel requires `[1.6.1,1.7.0)` so a standalone 1.6.0 is not used.

## Checks run
- Reproduction: new probe utility `crash` (`CrashQa`): Fabulous on with buffers → Boost on (Fast) → to the nether and
  back (real dimension change) → 3000 splashes in one tick, with OptiFine's custom water colour set → death wearing the
  wheel → respawn → Fabulous set in code without a rebuild (as a mod might).
  - old 4.21.0, no OptiFine (`crash`): splash burst passes, then **crash 50 ms after `Runtime optimization released`**
    on death: NPE on a null Fabulous target — Pasha's crash.
  - old 4.21.0, OptiFine (`crash-optifine`): **crash on the splash burst**, `fx is null` in `CustomColors.updateWaterFX`
    — Pasha's crash.
- Fixed build, final release jar: `crash`, `crash-optifine`, `crash-pasha` (Pasha's 11 mods), `crash-pasha-optifine`
  (his mods + OptiFine) **PASS** — 3000 splashes, 2936 rejected by the budget, no crash with OptiFine's water colour on;
  death wearing the wheel releases Boost and restores Fabulous with its buffers, frames keep rendering; Fabulous set in
  code without a rebuild is repaired before the next frame (the repair warning logs once per run, in that step only).
- Regression on 4.21.1: `ingame-vanilla`, `ingame-optifine` (also on the final jar), `screens-optifine`, `slash-optifine`,
  `xp-23mods-optifine` (300 orbs: 71 entities off, 4 on, experience 900 both), `stacks-23mods-optifine` **PASS**.
  These ran on the build before the last change (stand-ins not counted by the particle budget); the crash groups and
  `ingame-optifine` were re-run on the final jar.
- Adapt Boost core tests (incl. `StateMemoryTest`), wheel offline + online build with core tests, GameTest **62/62**.
- Errors left in the run logs belong to the mods themselves (Mowzie's Mobs grottol block models, Jujutsu Craft item
  models pointing at missing `minecraft:` models, OptiFine reflector warnings).

## Release files
- `dist/adaptation-wheel-1.20.1-4.21.1-offline.jar` — sha256 `456ce6c811562eeffb0302a94ad3fe4951b52643269def127e8f12d238750d60`
- `dist/adaptation-wheel-1.20.1-4.21.1-online.jar` — sha256 `8ddc90e53ab05fb7407110850c4a1503d0f009fc856937474f5d45c5abc0ad92`
- standalone `adapt-boost-1.20.1-forge-1.6.1.jar` in the Boost project's `dist/` (also embedded in the wheel).

## Not verified
- Pasha's own PC and world, his resource pack (`Remodeled-Minecraft1.20.2.zip`) and Distant Horizons rendering during the
  death (landscape memory was not active in the probe; DH's own `disableFabulousTransparency` is covered by the
  per-frame repair only in principle); an OptiFine shader pack with Fabulous selected.
- Rejected *mod* particles still return null, as since 1.3 (Pasha has `filterModParticles = true`); none of his crash
  reports point at that.
