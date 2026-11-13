# Validation 4.17.1 (Forge 1.20.1)

Date: 2026-10-23

## World Slash

- The cut is now a huge diagonal plane (`core/SlashPlane`): 64 blocks ahead of the swing, 10 blocks to each side along a blade tilted 35°, 3 blocks thick. It passes through walls and hits every creature the plane crosses (Ender Dragon via its parts). Spares the caster, the caster's own tamed pets and players vanilla PvP/team rules protect. Still exactly 150 HP through armor/resistance/absorption and through Infinity.
- Cooldown 60 s → **45 s** (`WorldSlash.COOLDOWN = 900`).
- Visual: a black tear (dark dust + ink, white sweep at the blade edges) that travels to the far end over 6 ticks, sent as forced particles so the whole length is visible; smoke rises where the plane passes through blocks; nothing is drawn in the first 4 blocks so the caster is not blinded. Louder sweep + sonic boom.
- New server option `sword.worldSlashCutsBlocks` (default **off**): also removes blocks along the plane (no drops, max 4096), skipping unbreakable blocks, containers/block entities, fluids and areas protected through `BlockEvent.BreakEvent`.

## Fix: possible crash when opening a world (introduced in 4.17.0)

`ConfigWatch` iterated a config while Forge's watcher thread reloaded the same file right after correcting it → `ConcurrentModificationException` in the config Loading event → server stopped. Now: snapshots retry on concurrent modification, the handler never lets an exception escape into Forge, and the extra modification-time check is limited to client configs (Forge reports server files itself).

## Checks run

| Check | Result |
| --- | --- |
| Core tests incl. new `SlashPlaneTest` (length, span, thickness, diagonal blade, looking straight down, bounds) | passed (offline + online) |
| GameTest server | all 56 required tests passed (new `worldSlashCutsThroughWallsAlongItsPlane`: two zombies behind a wall at 12 and 20 blocks cut, one behind the player and one 16 blocks aside untouched, caster's tamed wolf in the path untouched) |
| Real client, `slash.json` without OptiFine and `slash-optifine.json` with OptiFine HD_U_I6_pre6 | real `WorldSlash.cast` with the sword: zombies at 12/30/55 blocks behind a wall killed, cooldown 45 s; screenshots show the long diagonal black tear in both |
| Real client `outside-a.json` after the ConfigWatch fix | inventory, log trimming, external config edit reported, own FPS save silent, /kill refused |
| `run-qa.ps1` 24-mod matrix | MULTIMOD_QA PASS |

Note: the plane follows where you look. Aiming 8° down, the cut reaches the ground after about 11 blocks and misses mobs standing further away on flat ground; aim at the target.
