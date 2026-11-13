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
    private static final java.util.regex.Pattern CAMEL=java.util.regex.Pattern.compile("([a-z0-9])([A-Z])");
    private static final java.util.regex.Pattern SEPARATORS=java.util.regex.Pattern.compile("[^a-z0-9]+");
    private static final java.util.Map<String,java.util.regex.Pattern> WORD_PATTERNS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> MAGIC_MOBS=java.util.Set.of("witch","evoker","illusioner","wither","ender_dragon","warden");
    /** Vanilla mobs that do not fight with their body. The catalogue starts from this; a real hit replaces it. */
    private static final java.util.Map<String,String> MOB_ATTACKS=java.util.Map.of(
            "ghast","FIRE","blaze","FIRE","creeper","EXPLOSION",
            "skeleton","PROJECTILE","stray","PROJECTILE","pillager","PROJECTILE","shulker","PROJECTILE",
            "llama","PROJECTILE","guardian","MAGIC","elder_guardian","MAGIC");
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
        if(key.startsWith("condition:trytosurvivetwo:")) return switch(key.substring(key.lastIndexOf(':')+1)) {
            case "chill" -> COLD;case "overheating" -> FIRE;case "encumbrance" -> PHYSICAL;default -> EFFECT;
        };
        if(key.startsWith("condition:jujutsucraft:"))return MAGIC;
        if(key.equals("condition:toughasnails:thirst"))return HUNGER;
        if(key.equals("condition:sanitydim:insanity"))return EFFECT;
        if(key.equals("condition:mekanism:radiation"))return POISON;
        if(key.equals("condition:nuclearcraft:radiation"))return POISON;
        if(key.equals("condition:dragonsurvival:dehydration"))return HUNGER;
        if(key.equals("condition:iceandfire:siren_charm"))return EFFECT;
        if(key.equals("condition:anomaly_rephased:execution"))return MAGIC;
        if(key.startsWith("condition:")&&(key.contains(":capture/")||key.equals("condition:alexsmobs:constriction")||key.equals("condition:alexscaves:forsaken_hold")||key.equals("condition:born_in_chaos_v1:krampus_disarm")))return PHYSICAL;
        if(key.contains(":health_write/"))return ENVIRONMENT;
        if(key.startsWith("attack:"))return fromName(key.substring(key.lastIndexOf(':')+1),PHYSICAL);
        if (key.startsWith("mob:")) {
            if (boss || MAGIC_MOBS.contains(key.substring(key.lastIndexOf(':')+1))) return MAGIC;
            if (!key.startsWith("mob:minecraft:")) return PHYSICAL;
            String attack=MOB_ATTACKS.get(key.substring(14).replace("trader_llama","llama"));
            return attack==null ? PHYSICAL : valueOf(attack);
        }
        // A boss flag must not erase a specific attack's fire/light/etc. profile.
        return fromName(key.substring(key.lastIndexOf(':')+1),key.startsWith("effect:") ? EFFECT : ENVIRONMENT);
    }

    /** Conservative local name heuristics; the server supplements these with registry tags. */
    public static AdaptationProfile fromName(String name,AdaptationProfile fallback) {
        if (name==null || name.isBlank()) return fallback;
        String words=words(name);
        // Mods glue words together (projectile_shadowflameball). Only the part after the mod id is read this way:
        // a mod called iceandfire or lightningcraft says nothing about its attack.
        String glued=words(name.substring(name.lastIndexOf(':')+1));
        // Vocabulary from the damage types and effects of the tested mods (Twilight Forest, AoA3, Eternal Tales,
        // Ice and Fire, Alex's Caves, Vampirism, Born in Chaos...). Earlier groups win: "cold_fire" is cold.
        if (word(words,"lightning|electric|electricity|electrical|electrocution|electrocute|electrocuted|electrified|thunder|thunderbolt|shock|shocked|electro|zap|zapped|spark|sparks|tesla|volt|voltage|taser|tasered")
                || part(glued,"lightning|thunder|electr")) return ELECTRIC;
        if (word(words,"explosion|explosive|explode|exploding|blast|detonation|detonate|tnt|bomb|bombs|grenade|mortar|implosion|nuke|flak")
                || part(glued,"explos|explod")) return EXPLOSION;
        if (word(words,"hunger|starve|starvation|starving|starved|exhaustion|famine|malnutrition|thirst|dehydration|dehydrated")) return HUNGER;
        if (word(words,"freeze|freezing|frozen|frost|frosted|frostbite|cold|ice|icy|icicle|snow|snowball|blizzard|hail|chill|chilly|chilled|chilling|hypothermia|glacial|permafrost|cryo|cryogenic")
                || part(glued,"frost|frozen|blizzard")) return COLD;
        if (word(words,"poison|poisoned|poisoning|venom|venomous|toxic|toxin|toxicity|neurotoxin|acid|acidic|corrosion|corrosive|corroded|radiation|irradiated|radioactive|pollution|smog|gas"
                +"|plague|disease|diseased|infection|infected|virus|virulence|virulent|rabies|intoxication|intoxicated|blight|miasma|rot|rotted|rotting|rotten|mutation")
                || part(glued,"poison|venom|toxic")) return POISON;
        if (word(words,"drown|drowning|suffocate|suffocation|asphyxia|asphyxiation|oxygen|vacuum|choke|choking|strangle|strangled|stranglehold|smother|smothered|drench|drenched|quicksand|bubbled")
                || words.contains("_in_wall_") || words.contains("_cramming_") || words.contains("_dry_out_")) return SUFFOCATION;
        if (word(words,"fire|fireball|burn|burned|burnt|burning|flame|flames|lava|magma|scorch|scorched|scorching|heat|fiery|inferno|incinerate|incinerated|incineration|ember|embers|molten|ignite|ignited|blazing|hellfire|scald|scalded|boiling|steamed|hyperthermia|overheated|overheat")
                || words.contains("_hot_floor_") || part(glued,"flame|fireball|firebolt|firestorm|magma")) return FIRE;
        if (word(words,"light|lightbeam|laser|radiant|radiance|holy|sunbeam|sunlight|sun|sundamage|solar|photon|smite|sunburn")) return LIGHT;
        if (word(words,"dark|darkness|shadow|blindness|blind|blinded|shade|gloom|umbral|eclipse|eclipsed|abyss|abyssal") || part(glued,"shadow")) return DARKNESS;
        if (word(words,"magic|magical|arcane|arcana|spell|sorcery|curse|cursed|hex|hexed|wither|sonic|psychic|soul|souls|lich|drain|lifedrain|haunt|haunted"
                +"|gorgon|petrify|petrified|petrifying|petrification|chaos|chaotic|mana|eldritch|occult|hypnosis|hallucination|hallucinations|fear|terror|madness|insanity|drained|draining|siphon|leeching|doom|voided|oblivion")
                || words.contains("_dragon_breath_") || words.contains("_instant_damage_")) return MAGIC;
        if (word(words,"projectile|arrow|arrows|trident|bullet|bullets|shot|shrapnel|dart|darts|spear|gun|guns|rifle|pistol|cannon|cannonball|pellet|pellets"
                +"|pebble|boomerang|pickarang|thrown|missile|needle|needles|quill|quills|harpoon|javelin|bolt|bolts|snipe|sniped|sniper|minigun|railgun|buckshot|shotgun|shotgunned|grapeshot|slingshot|shuriken")) return PROJECTILE;
        // A bee's sting is melee: vanilla has no projectile tag on it.
        if (word(words,"fall|falling|stalagmite|stalactite|cactus|thorns|crush|crushing|crushed|melee|punch|kick|bite|slash|stab|physical|sting|stinger|combat"
                +"|claw|claws|jaw|jaws|maul|smash|slam|squish|crash|impale|impaled|spike|spikes|spiked|stake|dagger|sword|blade|axe|axing|hammer|whip"
                +"|trample|gore|headbutt|bleed|bleeding|wound|wounded|fracture|trap|clamp|clamped|collision|hemorrhage"
                +"|slice|sliced|skewer|skewered|squash|squashed|flatten|flattened|chop|chopped|hack|slay|slain|struck|backstab|backstabbed"
                +"|katana|saw|drill|roller|mauled|clawed|bit|bitten|poked|pricked|punctured|shredded|pierced|munch|munched|torn|swarm|infested|crunch|crunched|diced|grazed|knife|knifed|slap|slapped|macerated|thorn|bramble|razorwire|penetrate|penetrated")
                || words.contains("_mob_attack_") || words.contains("_player_attack_") || words.contains("_ran_over_") || words.contains("_run_over_")
                || words.contains("_fly_into_wall_") || words.contains("_sweet_berry_bush_")) return PHYSICAL;
        return fallback;
    }

    /** A mod's own death line: "%1$s was frozen by the Yeti" names what an id like yeti_damage does not. */
    public static AdaptationProfile fromText(String line) {
        return line==null ? null : fromName(line.replaceAll("%(\\d+\\$)?[sd]"," "),null);
    }

    /**
     * Vanilla damage types without a vanilla category tag, as the game itself defines them. Tagged types (fire,
     * lightning, explosion, freezing, drowning, fall, projectile) and plain melee are classified from their tags.
     */
    public static AdaptationProfile vanillaDamage(String path) {
        return switch(path==null?"":path) {
            case "in_wall","cramming","dry_out" -> SUFFOCATION;
            case "cactus","sweet_berry_bush","sting","thorns","fly_into_wall","falling_block","falling_anvil","falling_stalactite" -> PHYSICAL;
            case "magic","indirect_magic","wither","sonic_boom","dragon_breath" -> MAGIC;
            case "starve" -> HUNGER;
            case "outside_border" -> ENVIRONMENT;
            default -> null;
        };
    }

    /**
     * A mod's own damage type tag (forge:magic, forge:curse, vampirism:entity_physical...) names a category the mod
     * author chose. Tags about who resists or what is bypassed (witch_resistant_to, bypasses_shield) say nothing
     * about the attack itself and are ignored.
     */
    public static AdaptationProfile fromTag(String path) {
        if (path==null || path.isBlank()) return null;
        if (word(words(path),"immune|immunity|resistant|resistance|resist|resists|ignore|ignores|ignored|bypass|bypasses|break|breaks|protect|protects|protection"
                +"|prevent|prevents|reduced|reduces|no|not|non|vulnerable|vulnerability|weak|weakness|damages|block|blocks|blockable|unblockable|deflect|reflect|heals|always|never|only|except|exempt|allow|allows"))
            return null;
        return fromName(path,null);
    }

    private static String words(String name) {
        return "_"+SEPARATORS.matcher(CAMEL.matcher(name).replaceAll("$1_$2").toLowerCase(Locale.ROOT)).replaceAll("_")+"_";
    }

    private static boolean word(String words,String alternatives) {
        return WORD_PATTERNS.computeIfAbsent(alternatives,key->java.util.regex.Pattern.compile("_(?:"+key+")_")).matcher(words).find();
    }

    private static boolean part(String words,String alternatives) {
        return WORD_PATTERNS.computeIfAbsent("part:"+alternatives,key->java.util.regex.Pattern.compile(alternatives)).matcher(words).find();
    }
}

