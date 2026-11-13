# Validation 4.16.0 (Forge 1.20.1)

Date: 2026-10-22

## Changes

- Pasha Boost 1.3.4 (embedded):
  - Forge render settings are read once per client tick (`RenderSettings`), not per particle or per rendered mob.
  - Light chunk cache: 16 entries in four parity sets of four ways with round-robin eviction; neighbouring and stride-4 chunks no longer evict each other.
  - Distant ordinary living entities are rejected in `EntityRenderDispatcher.shouldRender` (`EntityDistanceMixin`) after the native frustum test, before model setup. Players, bosses (max HP >= 100), named, leashed, glowing, ridden or large entities are never culled.
- Local analyzer:
  - `EffectThreat`: a harmful effect's risk depends on its amplifier and remaining duration (bounded at 0.85); poison/magic and darkness start from a higher base.
  - The estimate applies only to harmful effects; ordinary damage risk is unchanged.
- Packaging: the bundled Pasha Boost hash is checked against `embedded/pasha-boost-1.20.1-forge-1.3.4.jar` at package time instead of a hard-coded 1.3.3 hash.
- Build: `release-4.16.0.ps1` / `RUN-BUILD-4.16.0.cmd` run under Windows PowerShell 5.1; compiler warnings on stderr no longer abort the build.

## Checks run

| Check | Result |
| --- | --- |
| Pasha Boost 1.3.4 `clean build` + coreTest (FrameStats, ParticleBudget, AdaptiveBudget, VisibilityCache, LightChunkCache, CompactGlyphRows) | passed |
| Wheel 4.16.0-offline `build` + coreTest (all suites incl. new `AdaptationAnalysisTest.effectThreat`) | passed |
| Wheel 4.16.0-online `build` + coreTest | passed |
| `package-release.ps1`: variant markers, mod version, bundled payload hashes and notices, identical classes across variants | passed |

## Not yet run

- In-game GameTest server and the 24-mod compatibility matrix (`run-qa.ps1`).
- Client smoke/FPS benchmark for the new Pasha Boost culling.
