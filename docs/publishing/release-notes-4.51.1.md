# Adaptation Wheel 4.51.1 — Projectile Parry Fix

**Main download: [Trained + Online Advisor](https://github.com/pashapasha94237-stack/adaptation-wheel/releases/download/v4.51.1/adaptation-wheel-1.20.1-4.51.1-trained-online.jar).** The advisor is optional and stays disabled until configured; local analysis works without a remote service.

- Incoming arrows now collide with the raised Sword of Extermination before reaching the player. The same arrow briefly stops at the blade, then returns to its shooter.
- Removed the old jump to the player's eye position. Projectile position, ownership and return velocity are synchronized to the ordinary client renderer.
- The guard geometry follows the worn blade, player scale and handedness. A shot passing beside the blade is not treated as a successful parry.
- Parrying still requires the wheel, a raised sword, an enabled adaptation and at least stage I against the attacker. Each successful parry spends 2 durability and advances the existing adaptation once.
- All four variants include the same gameplay fix. World Slash still leaves blocks intact by default; Memory of Defeat remains removed.

| File variant | Starting knowledge | Optional external advisor |
| --- | --- | --- |
| **Trained + Online — main file** | 506 prepared hints | Included, disabled until configured |
| Trained (Offline) | 506 prepared hints | Not included |
| Untrained + Online | Rules and gameplay learning, without the extra prepared base | Included, disabled until configured |
| Untrained (Offline) | Rules and gameplay learning, without the extra prepared base | Not included |

Install exactly one JAR. Minecraft Java **1.20.1**, **Forge 47.4.0+**, **Java 17**, **client and server**. Multiplayer requires matching 4.51.1 builds on the server and clients (network protocol 26).

Validation: four offline builds with core tests; 191/191 Forge GameTests; real-client arrow returns with and without OptiFine. The release contains four JARs, the source archive and SHA-256 checksums.