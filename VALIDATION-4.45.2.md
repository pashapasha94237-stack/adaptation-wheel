# Validation 4.45.2 — wheel looks and Shrine cuts under shader packs

Adapt Boost 1.7.0 and Distant Horizons 3.3.3 unchanged (byte-identical, checked by `release-4.45.2.ps1`), no protocol or UI change.

## Causes
- OptiFine draws an untextured render type through the pack's `gbuffers_basic`. The Shrine cuts and soft light used a POSITION_COLOR type: LUMA drew them as black streaks, Chocapic as an opaque white blob over the screen.
- The glowing boxes of the looks used `RenderType.beaconBeam`, which OptiFine draws through `gbuffers_beaconbeam`. Builders (Modded, QoL) with FANCY_BEACONS projects vertices near a block column centre with w=0: infinite rays, thin coloured streaks from the wheel to the sky. A shadow-pass guard alone (test build fix2) did not remove them.

## Fixes
- `CutRibbon.type()` under shaders: a type named `eyes` (NEW_ENTITY, `RENDERTYPE_EYES_SHADER`, `textures/misc/white.png`, translucent, no cull, polygon offset, no depth write) → `gbuffers_spidereyes`; vertices get uv/overlay/light/normal.
- `SkinRenderer`: glowing boxes use `RenderType.eyes(look.texture)` under shaders, `beaconBeam` without.
- Soft light (`slashLight`) and world cuts (`ShrineCuts`) are skipped in the shader shadow pass.
- Without a shader pack `CutRibbon.shaders()` is false: the old types and vertices are used unchanged.

## Results (release code = test build fix3, trained jar the same size)
Probe `run-shaders.ps1` (external probe project), Malevolent Shrine look, 26 screenshots each at GUI 1146×472 (back 0–9, first person, front), model r3 = true:

| Pack | Checks | Result |
|---|---|---|
| Builders Modded | 586 | PASS (first run: harness flake «GUI is 1x1», rerun PASS) |
| Builders QoL | 611 | PASS |
| Chocapic ultra | 583 | PASS |
| Chocapic 9.1 extreme beta5 | 588 | PASS |
| Chocapic v9 extreme | 641 | PASS |
| Chocapic v9 high | 611 | PASS |
| Chocapic v9 low | 551 | PASS |
| Chocapic v9 medium | 601 | PASS |
| LUMA v1.54 fix | 611 | PASS |
| Complementary Unbound r5.6.1 | 547 | PASS |
| Vanilla Plus 3.3 | 550 | PASS |
| Sildur's extreme-vl | 584 | PASS |
| Sildur's extreme | 594 | PASS |
| Sildur's high-mb | 574 | PASS |
| Sildur's high | 594 | PASS |
| Sildur's lite | 609 | PASS |
| Sildur's medium | 626 | PASS |

Screenshots checked by eye: no black streaks, no white blob, no rays to the sky (Builders back-9 fix2 vs fix3: the long streak to the upper right is gone).

- `coreTest` in all four variants, GameTests 191/191.
- `dist/`: `trained` 73DE94FBC04AD935… (28 778 028 B), `untrained` 6283E74CCBA36849…, `trained-online` 322E562D26FFD74D…, `untrained-online` E660C8EF69BEA436…; full hashes in `adaptation-wheel-1.20.1-4.45.2-SHA256SUMS.txt`.

## Not covered
- Pasha's own PC and GPU driver.
- No separate run without shaders (that code path is unchanged).
