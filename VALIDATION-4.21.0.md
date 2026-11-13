# Validation 4.21.0 (Forge 1.20.1) — Adapt Boost 1.6.0

Date: 2026-10-23

## Change
Pasha asked to improve Boost with the mods in `ForgeX.zip` (36 jars) and `Boosted FPS Forge-1.20.1-1.2.1.zip`, taking their code and features, and to rename it to **Adapt Boost**.

- Rename: display name, jar `adapt-boost-1.20.1-forge-1.6.0.jar`, all player-visible strings (Boost lang, wheel lang, F8 screen, logs). Mod ID `pashaboost`, packages `ru.pasha.pashaboost` and config files `pashaboost-*.toml` are unchanged, so saved settings and the wheel's jar-in-jar dependency keep working.
- **Memory (ported from FerriteCore 6.0.1, MIT)**, launch-time, only from the launch after Boost was earned (`startupMemoryEarned`, or 1.5's `packedGlyphsEarned`), skipped when FerriteCore is installed:
  - one `FastMap` per block instead of a neighbour table and property map per state (`StateHolderMemoryMixin`); unlike FerriteCore's default the vanilla table field holds a working read-only view, so mods reading it directly do not crash; skipped next to Lithium/Canary/Radium;
  - equal multipart predicates are one object and all states of a multipart block share one `MultiPartBakedModel` (`ModelDedup`), its selector cache synchronised;
  - equal collision shapes, face-slice arrays and sturdy-face arrays of block-state caches are one object (`ShapeDedup`, reflection by SRG name because Boost has no refmap).
  - Not ported: baked-quad dedup (OptiFine patches `BakedQuad`), small threading detector (OptiFine patches `PalettedContainer`), model-location strings and compact model sides.
- Particles without roll turn their four corners once per camera rotation (idea from particle_core, MIT).
- Experience orbs of the same value merge without vanilla's id-modulo-40 lottery (idea from Clumps, MIT); different values stay apart, so pickup, Mending and XP events see vanilla orbs. Skipped when Clumps is installed.
- Frames held at 30/s while the window is unfocused and 10/s while minimised (idea from Dynamic FPS, MIT); switch «30 FPS в фоне» in F8 → Settings (`backgroundFrameCap`) for recording or streaming from another window.
- FerriteCore's compact face lists of simple models (`SimpleModelMemoryMixin` + `ModelSides`, fields set by reflection because they are protected) were added after the first memory runs; the final-jar run includes them. Its first version aliased the protected fields and crashed at launch ("Non-private field cannot be aliased"), caught by the final-jar memory run before release.
- JVM opt-outs: `-Dpashaboost.disableParticleRotation`, `disableXpOrbMerge`, `disableIdleFrameCap`, `disableMemorySavings`.

### Not used, and why
- Need Sodium/Embeddium and so cannot run with OptiFine: Embeddium, Oculus, Rubidium Extra, Sodium Extras, Sodium Options API, Flerovium (also its `ModelPart` approach would replace OptiFine's patched `ModelPart`/`Cube`, breaking CEM and shaders).
- Licence does not allow copying: Chloride and FpsReducer2 (all rights reserved), Entity Culling (tr7zw Protective License), RRLS (OSL-3.0), EBE, GPUTape, ModelFix and Chunky (GPL; Boost is MIT), fzzy_config.
- Not ported in this release (LGPL/MIT, possible later): the rest of ModernFix (Boost already carries its glyph packing), ImmediatelyFast (batches HUD drawing that OptiFine also patches), AsyncParticles (ticks particles on other threads), Canary (server AI/ticking; the QA scene's mobs have no AI, so a gain could not be measured), memoryleakfix, Saturn. DH 2.4.5 is older than the DH 3.3.3 the wheel embeds; packet, ping and popup fixes do not affect FPS or memory.
- `Boosted FPS Forge-1.20.1-1.2.1.zip` is a CurseForge manifest without jars; its extra mods (Starlight, AI Improvements, Get It Together Drops) were not downloaded.

## Checks run
- Boost core tests including new `StateMemoryTest`: 864 states of five property kinds (bool, int, six-way facing, horizontal facing, enum) built like `StateDefinition`; property maps, iteration order, hash/equals, every neighbour and invalid values match vanilla; the runtime-generated Guava classes load in a plain JVM; config gate parser.
- Wheel: release build offline + online with core tests; GameTest **62/62**.
- Memory, real client, OptiFine + 23 mods (424 537 block states), retained heap after GC (`MemQa`):

  | Run | Retained |
  |---|---|
  | 4.21.0, memory savings off (2 runs) | 3466–3476 MB |
  | 4.21.0, memory savings on (2 runs) | **2358–2378 MB** (421 011 states compacted, 287 973 multipart models shared) |
  | final 4.21.0 jar, savings on, with compact model faces | 2361 MB (the face lists add nothing measurable on this pack) |
  | 4.20.1 + upstream FerriteCore 6.0.1 (reference) | 2138–2146 MB |
  | 4.20.1 without FerriteCore (reference) | 3418–3462 MB |

  FerriteCore by part on the same pack: state tables −490 MB, multipart predicates/models −380 MB, shape caches −215 MB, model names/sides/quads −190 MB (not ported).
- Equivalence: a digest of what every one of the 424 537 states answers — property values, every neighbour via `setValue`, collision and face-occlusion shapes, sturdy faces, model quads (positions, face, tint, sprite) — is **identical** in all five runs, with memory savings on and off and on the final jar. Time to world ready unchanged (87.8–89.7 s on, 91.3 s off).
- Background frame cap: the QA client window is never focused (`focused=false` in `PERF RESULT`), so a 4.21.0 perf run showed the cap working — 28.7 FPS in the Boost phase with 515 capped frames, server tick time unchanged (12.5 ms). The A/B below turns the cap off for both versions.
- Particle rotation: in that scene every particle has no roll; `particleRotationsReused` equals `particleVectorsReused` (1.83 M per 12 s phase).
- FPS A/B, `perf-23mods-optifine-mem` (400 mobs + 400 block entities, OptiFine + 23 mods, memory savings seeded on), wheel 4.20.1 (Boost 1.5) vs 4.21.0 (Adapt Boost 1.6) in the order 20, 21, 21, 20, 20, 21, background cap off for both:

  | Boost phase | Runs | Average FPS | p95 frame |
  |---|---|---|---|
  | Boost 1.5 | 279.8 / 289.0 / 272.9 | 280.6 | 6.61 ms |
  | Adapt Boost 1.6 | 284.6 / 271.0 / 298.2 | 284.6 | 6.49 ms |

  No FPS change beyond run-to-run noise (±13 FPS) on this scene; 1.6's gains are memory, entity count on farms and background frames. Vanilla phases 107–121 FPS in both.
- Regression on the 4.21.0 jar, OptiFine: `screens-optifine` (13 screenshots of J menu, HUD, Boost screens), `ingame-optifine`, `stacks-23mods-optifine`, `slash-optifine` PASS.
- Experience orbs (`XpQa`, OptiFine + 23 mods): 300 orbs worth 3 dropped like mob drops left **82 entities with Boost off and 4 with Boost on**; the experience they carry is 900 in both cases.
- Final release jar (after the face-list fix and a layout fix: the new F8 switch first overlapped the limits note, seen on the `screens-optifine` screenshot): `screens-optifine` PASS with the switch row and the note apart; memory run 2359 MB with the same digest.

## Release files
- `dist/adaptation-wheel-1.20.1-4.21.0-offline.jar` — sha256 `a6918002dd0a42ef77197c2bb82fada278d8662b32c5a7f03a19709b99909619`
- `dist/adaptation-wheel-1.20.1-4.21.0-online.jar` — sha256 `380c6550eadd528b2082c7f69c4c2706fa02371f08218d643b35f3eedf041ba7`
- standalone `adapt-boost-1.20.1-forge-1.6.0.jar` in the Boost project's `dist/` (also embedded in the wheel).

## Not verified
- Pasha's own PC, world and shader pack; a real mob farm (only the 300-orb drop test); the minimised (10 FPS) path — only the unfocused 30 FPS path ran; the multipart selector-cache synchronisation under real chunk-building contention.
- Memory savings start only from the launch after Boost was unlocked once; Pasha's existing config already has `packedGlyphsEarned = true`, so they apply from the first 4.21.0 launch.
