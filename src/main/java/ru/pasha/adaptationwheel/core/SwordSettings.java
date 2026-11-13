package ru.pasha.adaptationwheel.core;
import java.util.*;

/** Immutable server weapon policy, also sent explicitly to clients for item tooltips. */
public final class SwordSettings {
    public static final List<String> DEFAULT_SPECIAL_MOBS=List.of("minecraft:wither","minecraft:warden","minecraft:elder_guardian",
        "minecraft:ghast","minecraft:vex","minecraft:wither_skeleton");
    public record Values(double baseDamage,double undeadMultiplier,double cursedSpiritMultiplier,List<String> specialMobs,Map<String,Double> mobMultipliers) {
        public Values(double baseDamage,double undeadMultiplier,double cursedSpiritMultiplier) {
            this(baseDamage,undeadMultiplier,cursedSpiritMultiplier,DEFAULT_SPECIAL_MOBS);
        }
        public Values(double baseDamage,double undeadMultiplier,double cursedSpiritMultiplier,List<String> specialMobs) {
            this(baseDamage,undeadMultiplier,cursedSpiritMultiplier,specialMobs,Map.of());
        }
        public Values {
            if(!valid(baseDamage,1024) || !valid(undeadMultiplier,1000) || !valid(cursedSpiritMultiplier,1000))
                throw new IllegalArgumentException("Invalid sword damage settings");
            if(specialMobs==null || specialMobs.stream().anyMatch(id->!validMobId(id)))
                throw new IllegalArgumentException("Invalid special mob identifier");
            specialMobs=List.copyOf(new LinkedHashSet<>(specialMobs));
            if(mobMultipliers==null || mobMultipliers.entrySet().stream().anyMatch(e->!validMobId(e.getKey()) || e.getValue()==null || !validMultiplier(e.getValue())))
                throw new IllegalArgumentException("Invalid per-mob damage multiplier");
            mobMultipliers=Map.copyOf(mobMultipliers);
        }
        public double specialMultiplier(String id){return mobMultipliers.getOrDefault(id,cursedSpiritMultiplier);}
        private static boolean valid(double value,double maximum){return Double.isFinite(value) && value>=0 && value<=maximum;}
    }
    public static boolean validMobId(Object value) {
        return value instanceof String id && id.length()<=256 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }
    public static boolean validMultiplier(double value){return Double.isFinite(value) && value>=0 && value<=1000;}
    public static boolean validMultiplierRule(Object value) {
        if(!(value instanceof String rule))return false;
        String[] pair=rule.split("=",-1);if(pair.length!=2 || !validMobId(pair[0].strip()))return false;
        try{return validMultiplier(Double.parseDouble(pair[1].strip()));}catch(NumberFormatException invalid){return false;}
    }
    public static Map<String,Double> parseMultipliers(Collection<? extends String> rules) {
        Map<String,Double> result=new LinkedHashMap<>();
        for(String rule:rules) {
            if(!validMultiplierRule(rule))throw new IllegalArgumentException("Invalid per-mob multiplier rule");
            String[] pair=rule.split("=",-1);result.put(pair[0].strip(),Double.parseDouble(pair[1].strip()));
        }
        return Map.copyOf(result);
    }
    public static List<String> multiplierRules(Map<String,Double> multipliers) {
        return multipliers.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).toList();
    }
    public static final Values DEFAULTS=new Values(10,2,4);
    private static volatile Values current=DEFAULTS;
    private SwordSettings() {}
    public static Values current(){return current;}
    public static void replace(Values values){current=java.util.Objects.requireNonNull(values);}
    public static void defaults(){current=DEFAULTS;}
}
