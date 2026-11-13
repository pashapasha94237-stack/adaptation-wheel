# Validation 4.17.2 (Forge 1.20.1)

Date: 2026-10-23

## Change

- World Slash cuts blocks **by default**. The server option is now `sword.worldSlashBreaksBlocks` (default `true`); it was renamed from `worldSlashCutsBlocks` so worlds already opened with 4.17.1 (which stored `false`) also get the new default. Set it to `false` in the world's `serverconfig/adaptationwheel-server.toml` or through Configured to turn it off.
- The cut removes blocks along the plane's centre sheet (half-block steps, max 4096, no drops), from the first block in front of the player; it carves walls and, with the lower half of the blade, a trench in the ground. Skipped: unbreakable blocks (bedrock), block entities (chests and other containers), fluids, spawn-protected positions and anything a protection mod vetoes through `BlockEvent.BreakEvent`.
- The travelling slash keeps a direct reference to the caster for its 6 ticks.

## Checks run

| Check | Result |
| --- | --- |
| Core tests (offline + online) | passed |
| GameTest server | all 57 required tests passed (new `worldSlashCutsBlocksButSparesProtectedOnes`: a diagonal line cut through an 11×11 stone wall, a chest and bedrock on the plane kept) |
| Real client with OptiFine HD_U_I6_pre6 (`slash-optifine.json`), default config | key corrected to `worldSlashBreaksBlocks = true`; 3 zombies at 12/30/55 blocks killed, 7 stone bricks cut out of the 9×6 wall along the diagonal, a trench cut in the ground; screenshot `slash-after.png` |
| `run-qa.ps1` 24-mod matrix | MULTIMOD_QA PASS |
