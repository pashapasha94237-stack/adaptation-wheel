# Validation 4.25.0 — prepared knowledge, adaptive learning and world research

Date: 2026-10-25. Minecraft Java 1.20.1 / Forge. Base: complete 4.24.0 / 4.23 tree.

## Delivered behavior

- Trained and untrained JARs run the same local algorithm. Trained includes 335 reviewed category hints;
  untrained omits the dataset. No fabricated battles, pretrained adaptation stages or language model are included.
  Optional `-online` counterparts preserve the existing external advisor capability.
- `ResearchMemory`: bounded factual knowledge and empirical contexts, causal owner/projectile/effect/phase/range
  separation, conflict abstention, correction by fresh factual observations, raw-variant anomaly checks, indexed
  transfer and conservative world/archive merge. Copied counters do not become independent observations.
- `LearningController` keeps NBT and per-player crossworld archives separated by flavor. Async immutable archive
  IO, main-thread model merge, coalesced writes with retries, lifecycle checkpoints and current-definition cache
  validation. One canonical source/context/tick is one research sample; the existing adaptation engine still
  counts every actual hit for acceleration and damage protection.
- `WorldStudy` incrementally reads current damage/effect definitions, tags and already-loaded native hazards.
  It never creates chunks/entities, issues commands, damages the player or grants earned stages.
- Wheel menu footer **Обучение** / **Ctrl+L**: frozen reference, adaptive gameplay and world research modes;
  real server counters and server-owned Configured policy. Original 4.23 responsive menu is retained.

## Independent review and corrections

Subagents implemented/reviewed the core, world data and interface separately, then independently checked the
integrated code. Confirmed issues were fixed and exercised by new native probes:

1. Valid old-world NBT hid newer crossworld experience. It now merges the archive; copies use maximum counts,
   not sums, and conflicting factual profiles retain an abstention barrier.
2. Removing a tag could leave the derived `live:registry.*` label or empirical support. A stable fingerprint of
   damage tags/identities, effect properties and wheel rules distinguishes real changes from identical reloads.
   Dependent labels/support retire on changes while actual observation/raw-loss statistics and earned stages remain.
3. Unconditional revision reset discarded frozen metadata on a normal reload. Identical fingerprints now retain it.
4. Archive reads previously performed filesystem/Gson work on the server thread. Reads and writes now use the IO
   executor; only immutable results return to the server thread. Pending-write acceptance is tracked for retries.
5. Protection-key aliases could look like multiple abilities. New empirical support uses canonical source identity
   and a per-context/tick limit; actual protection/acceleration is unaffected.
6. Removed a duplicate status packet after changing a mode. No critical UI/authority issues remained in that review.

## Checks actually run

- Four clean builds + all core tests: **PASS** (`build-4.25.0-{trained,untrained,trained-online,untrained-online}.log`).
  New ResearchMemory tests cover source authority, ambiguity, current-context correction, no self-training,
  finite/bounded persistence, causal identity, raw anomalies, merge union/idempotence and fresh conflict correction.
  Existing 17,633 layout cases, rate/timing/configuration, protection, healing, existence and FPS tests remain passing.
- Final real Forge GameTest server: **63/63 PASS** (`gametest-4.25.0-final.log`), including original sword,
  exact 300 HP World Slash, inventory limits, real protection and configuration scenarios.
- Prepared knowledge generator `python tools/generate-knowledge425.py --check`: **PASS**, 335 unique reviewed hints,
  confidence 72–80, exact source provenance. Selected mod attack sites are checked against actual hook manifests.
- Native trained compact / untrained wide: **PASS**. Starts with 335 / 0 seed facts respectively. Native zombie
  damage still removes HP while frozen; real cold impacts add empirical observations in adaptive mode; NBT reload
  preserves them. Research checks 94 real registry definitions at the configured budget, discovers an unseen freezing
  tag without creating stages/HP changes, retains positive speed, stops when unequipped or in adaptive-only mode.
  Native Ctrl+L, mode buttons, C2S mode changes and S2C counters are checked; compact/wide screenshots inspected.
- Native shared archive: **PASS** (`run-final425-learning-shared-20261025-174230`). A real checkpoint is read from
  disk after removing only the isolated player's model snapshot; a new native lightning-tagged hit learns a fact;
  reopening the genuine older NBT merges the new archive fact and observations. Stages/HP are never imported.
- Native tag reload: **PASS** (`run-final425-learning-reload-20261025-174324`). Temporarily changes a real registered
  holder's tags, performs three real impacts in separate ticks, verifies unchanged tags/NBT retain frozen knowledge,
  then removes the tag. Old exact COLD and its support retire; measurements remain, and the next untagged hit loses
  real HP without stale COLD inference or changing the running timer. Fixture tags are restored.
- Final regression: **PASS** for `learning-optifine`, `smart-analysis-native`, `deep-native-analysis`,
  `screens-compact`, `combined-24-mods-cached`, and `guns-optifine` (real BowItem/CrossbowItem, CGM 1.4.20 and
  Scorched Guns 0.2.8 redirects and native collision HP loss). All ten final native groups exited 0.
  Main regression transcript: `artifacts/4.25-audit/final-native.log`; separate learning runs are in the isolated probe.
- Four production JARs have identical runtime classes, correct capability/knowledge markers, and trained-only dataset
  presence. ZIP CRC and release/source audits are run by the packaging/final audit. Exact hashes are in the release
  SHA256SUMS file. Embedded Adapt Boost 1.6.1 and Distant Horizons 3.3.3 remain byte-identical to 4.24.

## Scope

This is a local facts-and-experience analyzer, not a general-purpose intelligence. Confidence is an
evidence score. Structured registry/datapack inspection does not decompile or rewrite arbitrary Java code and cannot
invent protection hooks for a mod that bypasses the existing paths. Server authority, manual timings, earned stages,
old compatibility hooks and all 4.24 guided-shot limits remain. No new FPS improvement is claimed.
Installed user mods/configs/worlds were not edited; every client test used an isolated directory.
