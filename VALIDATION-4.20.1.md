# Validation 4.20.1 (Forge 1.20.1)

Date: 2026-10-23

## Change
- World Slash damage raised from 150 HP (75 hearts) to **300 HP (150 hearts)** per target, as Pasha asked (`WorldSlash.DAMAGE`). Still fixed final health damage through armour, toughness, protection enchantments, Resistance and absorption (absorption hearts are kept), no sword multipliers, through Jujutsu Craft's Infinity; the plane, 45 s cooldown and 3 durability are unchanged.
- Sword tooltip, README (also corrected its outdated 24-block / 60 s / stops-at-blocks description) and changelog `meta.adaptationwheel.changelog.4.20.1` updated.

## Checks run
- Build: `release-4.20.1.ps1` — Boost 1.5.0 and wheel offline/online with all core tests.
- GameTest: **62/62**. New `worldSlashDealsExactly150Hearts`: a mob with 1000 HP, armour 30, toughness 20, Resistance III and 10 absorption loses exactly 300 HP and keeps its absorption. Before this release no test checked the exact amount.
- Real client on the release JAR: `slash-optifine` and `slash` PASS (3 zombies at 12/30/55 blocks cut through a wall, cooldown set).

## Not verified
- Live hit on a Jujutsu Craft opponent with Infinity in this release (the bypass code is unchanged since 4.17.1).
