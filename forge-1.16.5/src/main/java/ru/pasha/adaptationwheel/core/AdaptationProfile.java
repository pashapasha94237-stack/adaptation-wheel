package ru.pasha.adaptationwheel.core;

import java.util.Locale;

/** Shared timing/catalogue policy, independent of Minecraft client classes. */
public enum AdaptationProfile {
    FIRE(8,12,0xFFFF9859), PHYSICAL(20,30,0xFFE8CA85), MAGIC(40,40,0xFFD2A3FF),
    EFFECT(12,18,0xFFAFD28D), HUNGER(10,15,0xFFFFCC78), COLD(10,15,0xFF9DDFFF),
    DARKNESS(10,15,0xFFA6B3FF), ENVIRONMENT(10,15,0xFFB3C9BB),
    PROJECTILE(20,30,0xFFE0B890), EXPLOSION(25,30,0xFFFFB16B), LIGHT(32,35,0xFFFFF4AC),
    ELECTRIC(25,30,0xFFA8E8FF), POISON(12,18,0xFF9AD773), SUFFOCATION(12,18,0xFF87B4C5);
    public static final String HUNGER_KEY="effect:minecraft:hunger";
    public static final String COLD_KEY="environment:cold";
    public static final String DARK_KEY="environment:darkness";
    public final int baseSeconds,extraSeconds,color;
    AdaptationProfile(int base,int extra,int color) { baseSeconds=base;extraSeconds=extra;this.color=color; }
    public int duration(int stage) { return (baseSeconds+extraSeconds*Math.max(0,Math.min(4,stage)))*20; }
    public String translationKey() { return "type.adaptationwheel."+name().toLowerCase(Locale.ROOT); }
    public String exampleKey() { return "example.adaptationwheel."+name().toLowerCase(Locale.ROOT); }
    public static AdaptationProfile forKey(String key,boolean boss) {
        if (key==null) return ENVIRONMENT;
        if (HUNGER_KEY.equals(key) || "damage:minecraft:starve".equals(key)) return HUNGER;
        if (COLD_KEY.equals(key) || "damage:minecraft:freeze".equals(key)) return COLD;
        if (DARK_KEY.equals(key)) return DARKNESS;
        if (key.startsWith("mob:")) {
            if (boss || key.matches(".*:(witch|evoker|illusioner|blaze|wither|ender_dragon|warden)$")) return MAGIC;
            return PHYSICAL;
        }
        // A boss flag must not erase a specific attack's fire/light/etc. profile.
        return fromName(key.substring(key.lastIndexOf(':')+1),key.startsWith("effect:") ? EFFECT : ENVIRONMENT);
    }

    /** Conservative local name heuristics; the server supplements these with registry tags. */
    public static AdaptationProfile fromName(String name,AdaptationProfile fallback) {
        if (name==null || name.trim().isEmpty()) return fallback;
        String words="_"+name.replaceAll("([a-z0-9])([A-Z])","$1_$2")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_")+"_";
        if (word(words,"lightning|electric|electricity|electrical|electrocution|thunder|shock|electro")) return ELECTRIC;
        if (word(words,"explosion|explosive|explode|blast|detonation|detonate|tnt")) return EXPLOSION;
        if (word(words,"hunger|starve|starvation|exhaustion")) return HUNGER;
        if (word(words,"freeze|freezing|frozen|frost|cold|ice|snow")) return COLD;
        if (word(words,"poison|poisoned|poisoning|venom|venomous|toxic|toxin|toxicity|acid|corrosion")) return POISON;
        if (word(words,"drown|drowning|suffocate|suffocation|asphyxia|asphyxiation") || words.contains("_in_wall_") || words.contains("_cramming_")) return SUFFOCATION;
        if (word(words,"fire|fireball|burn|burning|flame|flames|lava|magma|scorch|heat") || words.contains("_hot_floor_")) return FIRE;
        if (word(words,"light|lightbeam|laser|radiant|radiance|holy|sunbeam|sunlight|solar|photon")) return LIGHT;
        if (word(words,"dark|darkness|shadow|blindness")) return DARKNESS;
        if (word(words,"magic|magical|arcane|spell|sorcery|curse|cursed|wither|sonic|psychic")
                || words.contains("_dragon_breath_") || words.contains("_instant_damage_")) return MAGIC;
        if (word(words,"projectile|arrow|trident|bullet|shot|shrapnel|dart|spear|sting")) return PROJECTILE;
        if (word(words,"fall|falling|stalagmite|cactus|thorns|crush|crushing|melee|punch|kick|bite|slash|stab|physical")
                || words.contains("_mob_attack_") || words.contains("_player_attack_")
                || words.contains("_fly_into_wall_") || words.contains("_sweet_berry_bush_")) return PHYSICAL;
        return fallback;
    }

    private static boolean word(String words,String alternatives) {
        return java.util.regex.Pattern.compile("_(?:"+alternatives+")_").matcher(words).find();
    }
}
