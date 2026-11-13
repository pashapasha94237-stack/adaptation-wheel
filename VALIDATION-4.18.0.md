# Validation 4.18.0 (Forge 1.20.1)

Date: 2026-10-23

## Change

Embeds **Pasha Boost 1.4.0** (was 1.3.5). Boost stays gated exactly as before: nothing runs until stage 4 of the low FPS adaptation with the wheel worn. New work in 1.4.0, found with JFR on a 400-mob / 400-block-entity scene:

- **Shader sampler cache** (`ShaderSamplerMixin`): vanilla `ShaderInstance.apply()` calls `glGetUniformLocation` for every sampler on every apply (thousands of driver calls per frame); locations are now cached per program. This is most of the gain.
- **Biome shortcut for ambient particles** (`UniformBiomes`, `AnimateTickBiomeMixin`): `doAnimateTick` skips the noise-based biome zoom when every candidate section holds one biome; the answer is bit-for-bit vanilla.
- **Particle light reuse** (`ParticleLight`, `QuadParticleLightMixin`): a quad particle's light is computed once per block position per game tick; particles that override `getLightColor` are untouched.
- **Distant decorative block entities** (`BlockEntityDistanceMixin`, `EntityOptimizer.cullBlockEntity`): chests, signs, banners, skulls, beds, shulker boxes etc. are not drawn beyond `max(32, mob distance)`; beacons, spawners, mod block entities stay.
- **Adaptive mob distance** (`AdaptiveBudget.distance`): the frame-time controller that scales particle budgets also pulls the mob render distance in (≥ half, ≥ protected radius) and lets it back out.
- **Distant Horizons lightmap** (`DistantLightmapMixin`, only when DH is loaded): DH 3.3 re-uploaded an unchanged lightmap every frame; skipped when pixels and level are unchanged.
- **No forced resource reload**: packed glyphs used to be applied/removed with a full reload on every activation and release (a multi-second freeze each login). Once earned (`packedGlyphsEarned`) they are used by the next natural reload.
- Every new path has a JVM opt-out (`-Dpashaboost.disableShaderCache`, `disableBiomeShortcut`, `disableParticleLightCache`, `disableBlockEntityCulling`, `disableDistantLightmapSkip`) and counters in `OptimizationCounters`.
- Wheel: `mods.toml` and `META-INF/jarjar/metadata.json` require pashaboost `[1.4.0,1.5.0)`; changelog key `meta.adaptationwheel.changelog.4.18.0`.

## Checks run

| Check | Result |
| --- | --- |
| Pasha Boost clean build + core tests | passed (AdaptiveBudget 13 checks, VisibilityCache 11, LightChunkCache, ParticleBudget, FrameStats, CompactGlyphRows) |
| Wheel core tests (offline + online) | passed |
| GameTest server | all 57 required tests passed (runs without the embedded jars, so the later Boost-only fix below does not affect it) |
| 24 mods, Forge without OptiFine (`perf-24mods.json`, `PerfQa`) | MULTIMOD_QA PASS; Boost activated in both "on" phases; no Boost errors in the log |
| 23 mods + OptiFine HD_U_I6_pre6 (`perf-23mods-optifine.json`) | MULTIMOD_QA PASS; Boost activated; screenshots show the same scene with and without Boost (only clouds differ, as the temporary profile turns them off) |
| After the biome fix (final JARs): OptiFine + 23 mods, and OptiFine alone in a flat world | both MULTIMOD_QA PASS; the biome shortcut now fires in the normal world (about 461 k lookups saved per phase, was 0) and still in the flat one (about 252 k) |

## Frame rate (QA PC, 960×640 hidden window, render distance 8, 400 mobs + 400 block entities + 30 campfires, phases off/on/off/on of 12 s after a 20 s warm-up)

| Build | Boost off | Boost on |
| --- | --- | --- |
| OptiFine, no other mods, flat world (2 runs before the biome fix) | 83–91 FPS, 1% low 32–49 | 255–258 FPS, 1% low 116–122 |
| OptiFine, no other mods, flat world (final JAR) | 99 FPS, 1% low 75 | 270 FPS, 1% low 184 |
| OptiFine, empty flat world (2 runs) | 159–163 FPS | 389–396 FPS |
| Forge without OptiFine, no other mods | 47 FPS, 1% low 22 | 147 FPS, 1% low 86 |
| Forge without OptiFine, all 24 mods (platform at y=170) | 52.5 FPS, 1% low 12 | 174 FPS, 1% low 52 |
| OptiFine + 23 mods (platform at y=170), before the biome fix | 101 FPS, 1% low 24 | 269 FPS, 1% low 107 |
| OptiFine + 23 mods (platform at y=170), final JAR | 116 FPS, 1% low 69 | 279 FPS, 1% low 89 |

Server tick time (mspt) is unchanged by Boost in every run. The "on" phases include the temporary video profile (fast graphics, no clouds/entity shadows), which Boost 1.3.5 already had: with it, 1.3.5 reached about 105 FPS on the OptiFine scene, and 1.4.0 without the shader cache about 99.5, so the shader cache is the main gain. The platform runs sit 22 blocks under the cloud layer, so turning clouds off weighs a little more there than in the flat world.

Counters per 12 s "on" phase, OptiFine + 23 mods: about 2.4–2.6 M shader location lookups saved, 3.6–4.0 M particle light computations reused, 0.55–0.59 M distant decorative block entity draws skipped.

## Found and fixed during this validation

- **QA probe**: Eternal Tales makes the test world a normal (not flat) world; `PerfQa` built the scene where the flat-world floor would be and the player fell out of the world, so the first 24-mod run measured a death screen (Boost could not activate: `boostActive=false`). `PerfQa` now builds a grass platform at y=170 with cleared air when the world is not flat (flat worlds unchanged, so earlier numbers stay comparable).
- **QA group**: AdPother 8.1.51 cannot load with OptiFine at all (`NoSuchMethodError ResourceLocation.fromNamespaceAndPath`: Forge 47.4 adds that method to `ResourceLocation`, OptiFine replaces the class). Not caused by the wheel; the OptiFine mod group is `perf-23mods-optifine.json` without AdPother and its library ForgeEndertech.
- **Pasha Boost biome shortcut**: it never fired in a normal world (`biomeLookupsSaved=0`). A freshly generated section keeps the plains it was created with in its biome palette after noise fills it, and the server sends that palette unchanged, so the palette check saw almost every section as mixed (still correct, just inactive). `UniformBiomes` now reads the section's 64 biome cells once and caches the result.

## Mod interactions seen (no errors, vanilla fallback)

- AdPother injects into the same `getBiome` call of `ClientLevel.doAnimateTick`; with AdPother loaded the biome shortcut does not fire.
- With the mod sets the Distant Horizons lightmap skip never fires (`distantLightmapUploadsSkipped=0`, versus about 4000 per phase without mods). Most likely cause (not proven): Alex's Caves injects at the head of `LightTexture.updateLightTexture`, so the lightmap pixels change every frame and there is no unchanged upload to skip. The skip only ever cancels an upload of identical pixels, so this is the correct fallback.

## Not verified

- A live check on the user's PC and with the user's OptiFine shader pack.
- Frame rates on other hardware; the numbers above are from one PC.
