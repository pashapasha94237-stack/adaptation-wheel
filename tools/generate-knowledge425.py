"""Generate the reviewed starting hints from explicit vanilla semantics and our audited manifests.

This is not a corpus of simulated fights. It contains category hints only, never earned stages,
immunity, fabricated observations or an assumed threat for every entity belonging to a mod.
Unrecognised class/method pairs are deliberately omitted. --check verifies the shipped JSON.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "src/main/resources/knowledge/pretrained-v1.json"


def build() -> dict:
    facts: dict[str, dict] = {}

    def add(key: str, profile: str, source: str, confidence: int = 76) -> None:
        value = dict(key=key, profile=profile, confidence=confidence, source=source)
        previous = facts.get(key)
        if previous and previous["profile"] != profile:
            raise ValueError(f"Conflicting reviewed seed labels for {key}")
        facts[key] = value

    # Explicit Minecraft 1.20.1 identities. Operator/void kills stay outside adaptation.
    vanilla = {
        "FIRE": "in_fire on_fire lava hot_floor fireball unattributed_fireball",
        "ELECTRIC": "lightning_bolt",
        "COLD": "freeze",
        "SUFFOCATION": "in_wall cramming drown dry_out",
        "HUNGER": "starve",
        "PHYSICAL": "cactus fall fly_into_wall sweet_berry_bush stalagmite falling_block falling_anvil falling_stalactite sting mob_attack mob_attack_no_aggro player_attack thorns",
        "MAGIC": "magic indirect_magic wither dragon_breath sonic_boom",
        "PROJECTILE": "arrow trident mob_projectile fireworks thrown wither_skull",
        "EXPLOSION": "explosion player_explosion bad_respawn_point",
        "ENVIRONMENT": "outside_border",
    }
    for profile, paths in vanilla.items():
        for path in paths.split():
            add(f"damage:minecraft:{path}", profile, "seed:vanilla/1.20.1-damage-identities", 80)

    effects = {
        "EFFECT": "slowness mining_fatigue nausea weakness levitation unluck bad_omen glowing",
        "MAGIC": "instant_damage wither", "HUNGER": "hunger", "POISON": "poison",
        "DARKNESS": "blindness darkness",
    }
    for profile, paths in effects.items():
        for path in paths.split():
            add(f"effect:minecraft:{path}", profile, "seed:vanilla/harmful-effect-properties", 80)
    add("environment:cold", "COLD", "seed:vanilla/freezing-exposure", 80)
    add("environment:darkness", "DARKNESS", "seed:vanilla/darkness-exposure", 80)

    # These exact conditions are backed by the named compatibility controllers, not by names.
    for path in "flu cold fever measles".split():
        add(f"effect:trytosurvivetwo:t_2_s_{path}_disease_effect", "EFFECT", "seed:compat/T2S-2.0-infections")
    for path in "t_2_s_rabies_disease t_2_s_dread_effect t_2_s_out_of_stamina_effect".split():
        add(f"effect:trytosurvivetwo:{path}", "EFFECT", "seed:compat/T2S-2.0-infections")
    for path in "enteritis encephalitis hypothermia stomach_ulcer heatstroke brain_damage nyctophobia arachnophobia kinemortophobia crohns_disease sleeplessness exhaustion panting stress weak_immunity poor_rest".split():
        add(f"condition:trytosurvivetwo:{path}", "EFFECT", "seed:compat/T2S-2.0-condition-controller")
    add("condition:trytosurvivetwo:chill", "COLD", "seed:compat/T2S-2.0-temperature-controller")
    add("condition:trytosurvivetwo:overheating", "FIRE", "seed:compat/T2S-2.0-temperature-controller")
    add("condition:trytosurvivetwo:encumbrance", "PHYSICAL", "seed:compat/T2S-2.0-weight-controller")
    for key, profile in {
        "condition:iceandfire:frozen": "COLD",
        "effect:mowziesmobs:frozen": "COLD",
        "condition:iceandfire:siren_charm": "EFFECT",
        "condition:weather2:tornado": "ENVIRONMENT",
        "condition:anomaly_rephased:execution": "MAGIC",
        "condition:jujutsucraft:projection_freeze": "MAGIC",
        "condition:alexsmobs:constriction": "PHYSICAL",
        "condition:alexscaves:forsaken_hold": "PHYSICAL",
        "condition:born_in_chaos_v1:krampus_disarm": "PHYSICAL",
    }.items():
        add(key, profile, "seed:compat/audited-condition-controllers")

    # Reviewed Jujutsu technique identities. Positive/support-only abilities and unknown IDs
    # are omitted. The skill's registry/table still outranks every seed hint at runtime.
    techniques = json.loads((ROOT / "src/main/resources/compat/jujutsucraft-v51.json").read_text(encoding="utf-8-sig"))["skills"]
    technique_ids = {
        "MAGIC": "105 106 205 206 207 208 215 220 305 306 307 308 309 320 505 506 507 515 520 618 620 805 806 807 809 810 820 910 920 1006 1007 1008 1016 1018 1020 1120 1205 1206 1220 1310 1320 1405 1406 1410 1420 1505 1506 1507 1508 1509 1510 1515 1520 1607 1619 1905 1910 1920 2005 2015 2020 2105 2108 2113 2120 2205 2207 2208 2210 2305 2306 2307 2308 2320 2505 2506 2520 2719 2815 2906 2907 2920 3005 3020 3105 3205 3209 3406 3505 3605 3606 3610 3810 3908 3909 3912 3915 3920 4105 4110 4610",
        "PHYSICAL": "204 503 504 703 704 719 904 1005 1105 1106 1107 1306 1307 1503 1504 1516 1604 1605 1606 1608 1615 1706 1907 2003 2004 2006 2007 2106 2304 2611 2612 2613 2614 2615 2619 2705 2706 2707 2708 2710 2711 2904 3006 3007 3008 3107 3905 3906 3907 4303 4304 4405 4410 4411 4415 4505 4704 4705 4706 4707 4708",
        "FIRE": "120 405 406 407 408 409 415 420",
        "ELECTRIC": "607 705 715 716 717",
        "COLD": "2405 2406 2408 2409",
        "LIGHT": "4805 4806 4807 4808 4811 4812 4815",
        "DARKNESS": "4809",
        "EXPLOSION": "4709 4715",
    }
    for profile, ids in technique_ids.items():
        for identity in ids.split():
            if identity not in techniques:
                raise ValueError(f"Reviewed Jujutsu technique absent from v51 manifest: {identity}")
            add(f"condition:jujutsucraft:technique/{identity}", profile, "seed:compat/jujutsucraft-v51-technique-metadata", 72)

    # Exact audited attack sites. These select source-table entries, not arbitrary prefixes.
    sites = {
        ("ad_astra", "CryoFuelLiquidBlock", "m_7892_"): "COLD",
        ("ad_astra", "IceSpit", "m_5790_"): "COLD",
        ("ad_astra", "OxygenApiImpl", "entityTick"): "SUFFOCATION",
        ("ad_astra", "Rocket", "burnEntitiesUnderRocket"): "FIRE",
        ("ad_astra", "Rover", "doEntityCollisionTick"): "PHYSICAL",
        ("ad_astra", "TemperatureApiImpl", "burnEntity"): "FIRE",
        ("ad_astra", "TemperatureApiImpl", "freezeEntity"): "COLD",
        ("draconicevolution", "LaserBeamPhase", "serverTick"): "LIGHT",
        ("draconicevolution", "ChargingPlayerPhase", "serverTick"): "PHYSICAL",
        ("draconicevolution", "DraconicGuardianEntity", "attackEntitiesInList"): "PHYSICAL",
        ("draconicevolution", "DraconicGuardianEntity", "collideWithEntities"): "PHYSICAL",
        ("draconicevolution", "GuardianProjectileEntity", "detonate"): "EXPLOSION",
        ("draconicevolution", "ProcessExplosion$1", "execute"): "EXPLOSION",
        ("draconicevolution", "ProcessChaosImplosion$ChaosImplosionTrace", "updateProcess"): "EXPLOSION",
        ("twilightforest", "FireBeetle", "doBreathAttack"): "FIRE",
        ("twilightforest", "FireJetBlockEntity", "tickFlame"): "FIRE",
        ("twilightforest", "FieryBlock", "m_141947_"): "FIRE",
        ("twilightforest", "IceBomb", "hitNearbyEntities"): "COLD",
        ("twilightforest", "IceSnowball", "m_5790_"): "COLD",
        ("twilightforest", "WinterWolf", "doBreathAttack"): "COLD",
        ("twilightforest", "SnowQueen", "doBreathAttack"): "COLD",
        ("twilightforest", "HydraMortar", "detonate"): "EXPLOSION",
        ("twilightforest", "LichBolt", "m_5790_"): "MAGIC",
        ("twilightforest", "LifedrainScepterItem", "m_5929_"): "MAGIC",
        ("twilightforest", "TomeBolt", "m_5790_"): "MAGIC",
        ("twilightforest", "TwilightWandBolt", "m_5790_"): "MAGIC",
        ("twilightforest", "UrGhastFireball", "m_5790_"): "FIRE",
        ("mowziesmobs", "EntityIceBall", "m_8119_"): "COLD",
        ("mowziesmobs", "EntityIceBreath", "hitEntities"): "COLD",
        ("mowziesmobs", "EntityPoisonBall", "explode"): "POISON",
        ("mowziesmobs", "EntitySolarBeam", "m_8119_"): "LIGHT",
        ("mowziesmobs", "EntitySunstrike", "damageEntityLivingBaseNearby"): "LIGHT",
        ("mowziesmobs", "EntityBoulderProjectile", "m_8119_"): "PHYSICAL",
        ("mowziesmobs", "EntityEarthSpike", "m_8119_"): "PHYSICAL",
        ("mowziesmobs", "EntityAxeAttack", "dealDamage"): "PHYSICAL",
        ("mowziesmobs", "EntityAxeAttack", "attackTargetEntityWithCurrentItem"): "PHYSICAL",
        ("alexscaves", "AcidBlock", "m_7892_"): "POISON",
        ("alexscaves", "IrradiatedEffect", "m_6742_"): "POISON",
        ("alexscaves", "BubbledEffect", "m_6742_"): "SUFFOCATION",
        ("alexscaves", "DepthChargeEntity", "m_5790_"): "EXPLOSION",
        ("alexscaves", "NuclearExplosionEntity", "m_8119_"): "EXPLOSION",
        ("alexscaves", "MineExplosion", "explode"): "EXPLOSION",
        ("alexscaves", "TephraExplosion", "explode"): "EXPLOSION",
        ("alexscaves", "TotemExplosion", "explode"): "EXPLOSION",
        ("alexscaves", "PrimalMagmaBlock", "m_141947_"): "FIRE",
        ("alexscaves", "PrimalMagmaBlock", "m_7892_"): "FIRE",
        ("alexscaves", "FissurePrimalMagmaBlock", "m_141947_"): "FIRE",
        ("alexscaves", "FissurePrimalMagmaBlock", "m_7892_"): "FIRE",
    }
    entries = json.loads((ROOT / "src/main/resources/compat/mod-attacks.json").read_text(encoding="utf-8-sig"))["entries"]
    matched = set()
    for entry in entries:
        site = (entry["mod"], entry["owner"].rsplit("/", 1)[-1], entry["method"])
        if site in sites:
            matched.add(site)
            add(entry["key"], sites[site], f"seed:compat/{entry['mod']}/audited-attack-site", 76)
    if missing := sites.keys() - matched:
        raise ValueError(f"Reviewed sites absent from attack manifest: {sorted(missing)}")
    for stem in ("dragon_breath", "dragon_charge"):
        for suffix, profile in (("fire", "FIRE"), ("ice", "COLD"), ("lightning", "ELECTRIC")):
            add(f"attack:iceandfire:{stem}/{suffix}", profile, "seed:compat/iceandfire/audited-element-split", 78)

    return {
        "schema": 1,
        "description": "Reviewed starting category hints for Minecraft 1.20.1 and exact compatibility identities. This is a curated domain knowledge base, not a language model or simulated combat history.",
        "policy": "All seed confidence is below live authoritative registry evidence. No observations, stages, immunity or inventory are included. Positive/support-only effect identities and operator/void kills are omitted.",
        "sources": ["Minecraft 1.20.1 vanilla damage/effect definitions", "server/T2SCompatibility.java", "server/HazardCompatibility.java", "server/MobControlCompatibility.java", "compat/jujutsucraft-v51.json", "compat/mod-attacks.json"],
        "facts": [facts[key] for key in sorted(facts)],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = build()
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if DESTINATION.read_text(encoding="utf-8") != text:
            raise SystemExit("Pretrained knowledge does not match the reviewed generator.")
    else:
        DESTINATION.parent.mkdir(parents=True, exist_ok=True)
        DESTINATION.write_text(text, encoding="utf-8")
    print(f"Reviewed starting knowledge: {len(result['facts'])} exact category hints; no simulated observations or protection stages.")


if __name__ == "__main__":
    main()
