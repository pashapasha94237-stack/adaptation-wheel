# Validation 4.7.3 — 150 HP spatial damage, 60 second cooldown

Offline Gradle build and coreTest pass (build-world-slash-473.log).
Final actual-client integration: Forge 47.4.16, Java 17, Jujutsu Craft v51 RU.
Run: jujutsucraft-russian-1.20.1/probe/run-spatial-damage-473-20261021-061338
JJC_FIXED_SLASH_QA PASS, JJC_WORLD_SLASH_QA PASS, JJC_INFINITY_QA PASS,
JJC_WHEEL_QA PASS; process exit 0.

The real cast removes exactly 150 HP from cow, zombie, vex and wither with:
- 1000 initial health, armor 30, armor toughness 20;
- netherite chestplate with Protection IV;
- Resistance V and 200 absorption HP;
- attacker base attack attribute deliberately raised to 400;
- wither existence adaptation already complete (no instant-kill substitution).
Cooldown remains active after 1199 timer ticks and ends exactly at 1200 ticks.
Real Gojo takes damage through active Infinity, with no separate Infinity adaptation.
Existing unlock, persistence, equipment, catalogue, negative effects and technique checks pass.

Implementation:
- Dedicated damage type bypasses armor, enchantment protection, resistance, shield,
  effects and the vanilla hurt cooldown; final LivingDamageEvent pins health loss to 150.
- Absorption is temporarily excluded from the synchronous cut and restored afterwards,
  retaining any newly granted absorption. There is no direct setHealth/die shortcut.
- Sword attack attributes, positive-energy multipliers and adapted-boss instant execution
  do not change the slash; those existing mechanics remain on ordinary melee attacks.
- Only this own, active attack skips JJC v51's normal melee source/damage rewrite.
  Its Infinity final guard remains present and uses the existing personal bypass.
- PvP/team checks, creative immunity and cancelled damage events remain authoritative.

Initial probe setup errors were corrected: vanilla max HP is capped at 1024, and
JJC disables vanilla hostile spawns by default. The isolated probe enables its own
world's doVanillaMobSpawning flag. Actual user worlds and installed mods are untouched.
Earlier runtime probes also found absorption early-return and JJC source rewriting;
the final implementation addresses both, as verified by the protected-target assertions.

Final JAR SHA256:
E9306E57D28A8413BF39F537A6FB822C5FFF0046B974F1F25C089B9FD684C8FC
