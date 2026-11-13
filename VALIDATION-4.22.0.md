# Validation 4.22.0 (Forge 1.20.1) — analyzer

Date: 2026-10-24. Adapt Boost unchanged (1.6.1).

## Request
Pasha: «улучши анализатор».

## What was wrong
The analyzer's saved decisions in Pasha's worlds (`saves/Новый мир (16…22)/playerdata/*.dat`,
`ForgeData.adaptationwheel.Entries` + `Analysis`) and a survey of the damage types, damage type tags and effects of
the 24 tested mods plus his 2026-10-23 pack showed four weak spots:

1. **Untagged vanilla damage was guessed from its name.** `minecraft:sting` became PROJECTILE ("sting" was in the
   projectile word list; vanilla has no projectile tag on it), `mob:minecraft:bee` fell back to 30%,
   `minecraft:in_wall` was a 40% name hint. Same for cactus, berry bush, thorns, falling blocks, cramming, dry-out.
2. **Mod damage type tags were ignored.** Jujutsu Craft tags `damage_curse` as `forge:curse` and `damage_combat` as
   `forge:combat`; AoA3 uses `forge:magic`, Vampirism `vampirism:entity_physical`. In world 21
   `damage:jujutsucraft:damage_curse` was a 40% name hint, `damage_combat` and the Jujutsu mobs hitting with it
   (`great_serpent`, `panda_normal`) were 30% fallbacks.
3. **Keys from the wheel's own compatibility tables looked unknown.** Jujutsu techniques
   (`condition:jujutsucraft:technique/1606`…) and other `condition:` keys showed "little data, 30%", though the
   wheel's table names them.
4. **The name vocabulary missed common words** of these mods: gorgon, hypothermia, plague, infection, neurotoxin,
   chilling, scorched, thrown, sword, whip, axe, bleeding, bomb… Survey: the names of 92 of 148 mod damage types and
   269 of 322 mod effects (beneficial ones included) matched no word; after the change 51 and 221. Many of the
   remaining types are classified by their vanilla tags or are opaque on purpose (`twilightforest:expired`).

## Change
- `AdaptationProfile.vanillaDamage`: the game's meaning of untagged vanilla types (sting, cactus, berry bush,
  thorns, fly into wall, falling block/anvil/stalactite → physical; in wall, cramming, dry out → suffocation;
  magic types → magic; starve → hunger; outside border → environment). Registry-level evidence.
- Mod tags: `DamageCategories.tagProfiles` reads every non-vanilla tag of the damage type through
  `AdaptationProfile.fromTag`, which skips relation tags (`witch_resistant_to`, `bypasses_shield`, `*_immune`,
  `no_*`…). New evidence `TAG_*`, confidence 78 (62–70 with competing tags), reason `tag.category`; re-read on every
  checked hit, so a tag removed by a datapack is forgotten. Also used for the catalogue profile.
- `COMPAT_PROFILE`: any `condition:` key, except a Jujutsu technique missing from `jujutsucraft-v51.json`, takes its
  table profile with 86%, reason `compat.known` (≥85, so a later debuff does not revise it and the online advisor
  is not asked).
- Order: datapack tags → vanilla tags and types → observed delivery/elements → control → compatibility table →
  mod tags → attributed effect → name → harmful effect → fallback. Evidence values are appended, so saved masks keep
  their meaning; earned stages are untouched, only category and future stage times follow the new evidence.
- `fromName` vocabulary extended from the survey; "sting" moved from projectile to physical. Jujutsu technique
  profiles that change: Sword Skill 1–5/Ogi, Long Gavel Whip, Great Gavel Slam, Evening Moon Sword Drawing →
  physical; Air Cannon, Liquid Metal: Arrows → projectile; Crystal: Bomb → explosion; Burned Out, Ember Insects →
  fire. "pierce" was left out on purpose: Piercing Blood stays magic.
- Reason texts `compat.known`, `tag.category` (ru/en); changelog `meta.adaptationwheel.changelog.4.22.0`.

## Checks run
- Core tests (`coreTest`, part of `build` for both variants): new `vocabularyAndVanillaTypes` and
  `modTagsAndCompatibilityTables` — vanilla table, survey names, relation tags ignored, tier order (vanilla tag >
  observed delivery > compatibility table > mod tag > name), competing tags, tag forgotten after removal, debuff
  does not replace a tag, ordinals of saved evidence unchanged.
- GameTest **63/63** (new `untaggedVanillaTypesModTagsAndCompatibilityTables`: real damage from a bee's sting and a
  wall, a test type tagged `forge:curse` plus a relation tag `testmod:fire_resistant_to`, and the Ice and Fire frozen
  condition through `continuousExposure`).
- Real client with Pasha's 11 mods, new probe group `analyzer-pasha` (`AnalyzerQa`): real hits from a bee, a wall,
  Jujutsu's `damage_curse` and `damage_combat` (tags from the real Jujutsu jar) and a `cursed_spirit_grade_33` using
  technique 1606 (listed) and 4200 (not listed). Each row is logged with profile, reason and confidence.

  | key | 4.21.1 | 4.22.0 |
  |---|---|---|
  | `damage:minecraft:sting` | PROJECTILE, name hint 40% | PHYSICAL, registry 96% |
  | `mob:minecraft:bee` | PHYSICAL, fallback 30% | PHYSICAL, registry 96% |
  | `damage:minecraft:in_wall` | SUFFOCATION, name hint 40% | SUFFOCATION, registry 96% |
  | `damage:jujutsucraft:damage_curse` | MAGIC, name hint 40% | MAGIC, mod tag 78% |
  | `damage:jujutsucraft:damage_combat` | ENVIRONMENT, fallback 30% | PHYSICAL, mod tag 78% |
  | `condition:jujutsucraft:technique/1606` | MAGIC, fallback 30% | MAGIC, compatibility table 86% |
  | `condition:jujutsucraft:technique/4200` | MAGIC, fallback 30% | MAGIC, mod tag 78% |

  The 4.21.1 column matches what Pasha's world 21 had saved. 4.21.1: 7 rows differ (expected); 4.22.0: **PASS**.
- Earlier analyzer groups on 4.22.0: `smart-analysis-base` and `smart-analysis-native` (5 mods; tags, explicit
  categories, separation, effects, dynamic projectiles, network, Russian GUI) **PASS**; `deep-native-analysis`
  **PASS** on 4.22.0 and on 4.21.1. Its `LOCAL_PROJECTILE_CAUSE` check had expected PROJECTILE for a named light
  beam fired as an arrow and failed on 4.21.1 as well: since 4.16.2 such a beam keeps LIGHT
  (`native.projectile_element`). The check now asserts what it is for: the attributed poison changes neither
  ability's category nor its running clock.
- Regression on the 4.22.0 release jar: `ingame-vanilla`, `ingame-optifine`, `crash-pasha` (Pasha's pack: splash
  burst, death wearing the wheel, Fabulous guard) **PASS**.
- Release script: Adapt Boost 1.6.1 clean build + tests, wheel offline + online `build` with core tests.

## Release files
- `dist/adaptation-wheel-1.20.1-4.22.0-offline.jar` — sha256 `3793c976c056828151bb63ad3d40526bf9d293d28520fcbc68fde207d01696c3`
- `dist/adaptation-wheel-1.20.1-4.22.0-online.jar` — sha256 `ba2887bd994422866208a1cd26a7ec2157ab56d8658723f12f5fb24c71d3cbe9`
- `dist/adaptation-wheel-1.20.1-4.22.0-sources.zip` contains this file, so its hash is only in
  `dist/adaptation-wheel-1.20.1-4.22.0-SHA256SUMS.txt`.

## Not verified
- Pasha's own worlds on 4.22.0. Existing entries keep their earned stages; their category and future stage times
  change on the next hit (e.g. world 21's `damage:minecraft:sting` PROJECTILE → PHYSICAL), which the probe shows for
  fresh entries only.
- The online advisor still applies below 85%, so with a configured service it may override a mod-tag decision (78%),
  as it could override the 30–40% guesses before. The offline build has no advisor.
- Mods outside the tested set may use tags whose names mean something else; relation words are filtered, but a
  tag named after a mod feature (say `mymod:fire_dragon_drops`) would still read as fire. Datapack tags
  `adaptationwheel:adaptation/<profile>` override it.
