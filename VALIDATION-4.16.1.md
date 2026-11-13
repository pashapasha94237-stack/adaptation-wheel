# Validation 4.16.1 (Forge 1.20.1)

Date: 2026-10-22

## Fixes

- Silent world: the player's own sounds (PLAYERS source at their feet, unless another player is closer), the ridden mount, and hit/break sounds of the block being mined no longer show hints or train the adaptation. Other players' and mobs' sounds still do.
- Landscape memory: with OptiFine installed the distant LOD renderer is never enabled (OptiFine replaces the chunk renderer and the normal world disappeared after stage four). Terrain keeps being recorded; the J menu explains the limit.
- Landscape memory: the LOD radius is at least the vanilla render distance + 32 chunks.
- Landscape memory: LOD detail lowered by frame-time pressure is restored after ten calm frame windows instead of staying low until logout.

## Checks run

| Check | Result |
| --- | --- |
| Pasha Boost 1.3.4 clean build + core tests | passed |
| Wheel 4.16.1-offline build + all core test suites | passed |
| Wheel 4.16.1-online build + all core test suites | passed |
| package-release: versions, variant markers, bundled payload hashes and notices | passed |

## Not yet run

- In-game check of silent-world hints and of landscape memory with and without OptiFine.
- run-qa.ps1 compatibility matrix.
