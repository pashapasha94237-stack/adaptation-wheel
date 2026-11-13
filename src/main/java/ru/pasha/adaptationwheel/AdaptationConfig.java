package ru.pasha.adaptationwheel;

import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.DamageCategories;

/** Standard Forge SERVER config, discovered automatically by Configured. All times are seconds. */
public final class AdaptationConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Map<AdaptationProfile,List<ForgeConfigSpec.IntValue>> CATEGORIES=new EnumMap<>(AdaptationProfile.class);
    public static final Map<String,List<ForgeConfigSpec.IntValue>> MOBS=new LinkedHashMap<>();
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SOURCE_OVERRIDES;
    public static final ForgeConfigSpec.IntValue HIT_ACCELERATION_PERCENT;
    public static final List<ForgeConfigSpec.IntValue> PROTECTION_PERCENT;
    public static final ForgeConfigSpec.BooleanValue FULL_HEAL_AFTER_STAGE;
    public static final ForgeConfigSpec.BooleanValue EXISTENCE_ENABLED,ONE_HIT_KILL_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ANALYSIS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ELEMENT_EXPERIENCE;
    public static final ForgeConfigSpec.DoubleValue SWORD_BASE_DAMAGE,SWORD_UNDEAD_MULTIPLIER,SWORD_CURSED_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SWORD_SPECIAL_MOBS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SWORD_MOB_MULTIPLIERS;
    public static final ForgeConfigSpec.BooleanValue WORLD_SLASH_CUTS_BLOCKS;
    static {
        ForgeConfigSpec.Builder b=new ForgeConfigSpec.Builder();
        b.translation("config.adaptationwheel.analyzer").push("analyzer");
        ANALYSIS_ENABLED=b.comment("Analyze observed attacks and harmful effects locally to choose their category and stage durations, including modded sources.",
            "Manual source timings take priority, followed by category timings changed from their defaults. Unchanged values allow automatic timings.",
            "An automatically chosen duration is fixed when its stage begins; repeated hits change speed, not the amount of remaining work.",
            "Disabling restores configured category/source timings without deleting learned adaptations. No internet connection is used.")
            .translation("config.adaptationwheel.analyzer_enabled").define("enabled",true);
        ELEMENT_EXPERIENCE=b.comment("Each finished source of a category shortens the stages of new sources of that category by 5%, up to 35%.",
            "Only speed carries over: stages and protection are always earned per source.")
            .translation("config.adaptationwheel.element_experience").define("elementExperience",true);
        b.pop();
        b.translation("config.adaptationwheel.sword").push("sword");
        SWORD_BASE_DAMAGE=b.comment("Total damage in HP of a fully charged sword strike, before armour, enchantments and critical hits.",
            "One heart is 2 HP. Updates existing swords and held attack attributes without restarting. 0 disables base weapon damage.")
            .translation("config.adaptationwheel.sword_base_damage").defineInRange("baseDamage",10.0,0.0,1024.0);
        SWORD_UNDEAD_MULTIPLIER=b.comment("Melee damage multiplier against creatures of the UNDEAD mob type. 1 gives normal damage; 0 prevents sword damage to them.")
            .translation("config.adaptationwheel.sword_undead_multiplier").defineInRange("undeadMultiplier",2.0,0.0,1000.0);
        SWORD_CURSED_MULTIPLIER=b.comment("Melee damage multiplier against special creatures: Wither, Warden, Elder Guardian, Ghast, Vex and Wither Skeleton by default.",
            "The editable specialMobs list determines which creatures receive this multiplier.",
            "Overrides the undead multiplier if both apply; the multipliers do not stack.")
            .translation("config.adaptationwheel.sword_cursed_multiplier").defineInRange("cursedSpiritMultiplier",4.0,0.0,1000.0);
        SWORD_SPECIAL_MOBS=b.comment("Complete list of entity type IDs that receive the special-creature multiplier, e.g. minecraft:wither or examplemod:spirit.",
            "Add or remove entries freely. An empty list disables the special group; ordinary undead still use their separate multiplier.",
            "Replaces the legacy cursed_spirits tag for sword targeting, so removing a mob actually removes its special bonus.",
            "Uninstalled mod IDs are allowed and remain inactive until that entity type exists. Changes apply without restarting.")
            .translation("config.adaptationwheel.sword_special_mobs")
            .defineListAllowEmpty(List.of("specialMobs"),()->SwordSettings.DEFAULT_SPECIAL_MOBS,SwordSettings::validMobId);
        SWORD_MOB_MULTIPLIERS=b.comment("Optional individual multipliers for selected specialMobs, e.g. minecraft:wither=4.0 and minecraft:warden=6.0.",
            "Only selected mobs receive these overrides. Missing entries inherit cursedSpiritMultiplier. Values range from 0 to 1000.",
            "In Configured, use the mob picker to edit a multiplier beside each chosen mob without entering IDs.")
            .translation("config.adaptationwheel.sword_mob_multipliers")
            .defineListAllowEmpty(List.of("mobMultipliers"),List::of,SwordSettings::validMultiplierRule);
        WORLD_SLASH_CUTS_BLOCKS=b.comment("World Slash also cuts the blocks along its plane (no drops). Unbreakable blocks, containers, fluids and protected areas are skipped.",
                "Renamed from worldSlashCutsBlocks in 4.17.2 so the new default (on) applies to worlds opened with 4.17.1.")
            .translation("config.adaptationwheel.world_slash_cuts_blocks").define("worldSlashBreaksBlocks",true);
        b.pop();
        b.translation("config.adaptationwheel.bosses").push("bosses");
        EXISTENCE_ENABLED=b.comment("Enable the fifth existence adaptation stage for bosses.",
            "Disabling pauses its progress, suppresses earned fifth-stage powers, and uses fourth-stage protection instead. Saved degrees and progress are retained.")
            .translation("config.adaptationwheel.existence_enabled").define("existenceAdaptationEnabled",true);
        ONE_HIT_KILL_ENABLED=b.comment("Allow this mod's one-hit boss execution after completed existence adaptation.",
            "Disabling preserves fifth-stage immunity. Requires existence adaptation to be enabled and completed.")
            .translation("config.adaptationwheel.one_hit_kill_enabled").define("oneHitKillEnabled",true);
        b.pop();
        b.translation("config.adaptationwheel.healing").push("healing");
        FULL_HEAL_AFTER_STAGE=b.comment("Fully restore the player's current maximum health after a completed adaptation stage.",
            "Includes the fifth existence stage. Disabling this does not affect vanilla regeneration or other healing sources.")
            .translation("config.adaptationwheel.heal_after_stage").define("fullHealAfterStage",true);
        b.pop();
        b.comment("Absolute damage reduction for each ordinary adaptation stage, in percent.",
            "0 gives no damage reduction; 100 gives immunity. Values do not add together.",
            "When enabled, the fifth existence stage gives 100 percent protection; one-hit execution has a separate toggle.")
            .translation("config.adaptationwheel.protection").push("protection");
        List<ForgeConfigSpec.IntValue> protections=new ArrayList<>();
        for(int stage=1;stage<=4;stage++)protections.add(b.comment("Damage reduction after this stage, from 0 to 100 percent. Applies to already earned stages immediately.")
            .translation("config.adaptationwheel.protection_stage"+stage)
            .defineInRange("stage"+stage+"Percent",StageProtections.DEFAULTS.percent(stage),0,100));
        PROTECTION_PERCENT=List.copyOf(protections);b.pop();
        b.translation("config.adaptationwheel.acceleration").push("acceleration");
        HIT_ACCELERATION_PERCENT=b.comment("Extra percent of base adaptation speed per repeated hit. 50 means x1.00, x1.50, x2.00, etc.",
            "0 disables repeat-hit acceleration. Applies to running adaptations without resetting elapsed time or hit count.")
            .translation("config.adaptationwheel.hit_acceleration_percent")
            .defineInRange("bonusPerHitPercent",AdaptationTimings.DEFAULT_HIT_ACCELERATION_PERCENT,0,AdaptationTimings.MAX_HIT_ACCELERATION_PERCENT);
        b.pop();
        b.comment("All durations are in seconds. Applied to actual server progression without restarting the world.",
            "Changed category values override automatic analysis for those stages. Default values allow automatic analysis when enabled.",
            "Per-source values override categories. Zero in a source override inherits the automatic or configured category duration.")
            .translation("config.adaptationwheel.categories").push("categories");
        for(AdaptationProfile profile:AdaptationProfile.values()) {
            b.translation(profile.translationKey()).push(profile.name().toLowerCase(Locale.ROOT));
            CATEGORIES.put(profile,stages(b,profile,false));b.pop();
        }
        b.pop();b.comment("Separate timings for common mobs. 0 = inherit that stage's automatic or configured category duration.",
            "The zombie entry controls all zombie variants, including husks and drowned.")
            .translation("config.adaptationwheel.mobs").push("mobs");
        for(String mob:List.of("zombie","skeleton","spider","creeper","enderman","witch","blaze","warden","ender_dragon","wither")) {
            String key="mob:minecraft:"+mob;
            b.translation(mob.equals("zombie")?"entity.adaptationwheel.zombie_family":"entity.minecraft."+mob).push(mob);
            MOBS.put(key,stages(b,AdaptationProfile.forKey(key,List.of("warden","ender_dragon","wither").contains(mob)),true));b.pop();
        }
        b.pop();
        SOURCE_OVERRIDES=b.comment("Optional exact timings for any mob, effect or damage source, including mods.",
            "Format: mob:examplemod:creature=5,35,65,95,125 (five stages in seconds).",
            "Also accepts damage:examplemod:laser=5,10,15,20,25 and effect:minecraft:poison=5,10,15,20,25.",
            "For a detected ability, copy its attack:modid:ability key from the adaptation catalogue.",
            "0 inherits the automatic or configured category duration for that stage. Exact rules here override common mob fields.")
            .translation("config.adaptationwheel.source_overrides")
            .defineListAllowEmpty(List.of("sourceOverrides"),List::of,AdaptationConfig::validOverride);
        SPEC=b.build();
    }
    private AdaptationConfig() {}
    private static List<ForgeConfigSpec.IntValue> stages(ForgeConfigSpec.Builder b,AdaptationProfile profile,boolean inherit) {
        List<ForgeConfigSpec.IntValue> values=new ArrayList<>();
        for(int stage=0;stage<5;stage++) values.add(b.comment(inherit?"Duration in seconds. 0 = inherit automatic or category timing. Fifth stage applies only to bosses.":"Duration in seconds at x1.00 speed. A value changed from its default overrides automatic analysis for this stage. Fifth stage applies only to bosses.")
            .translation("config.adaptationwheel.stage"+(stage+1))
            .defineInRange("stage"+(stage+1)+"Seconds",inherit?0:profile.duration(stage)/20,inherit?0:1,StageDurations.MAX_SECONDS));
        return List.copyOf(values);
    }
    public static void changed(ModConfigEvent event) {
        if(event.getConfig().getSpec()!=SPEC)return;
        if(event instanceof ModConfigEvent.Unloading) {AdaptationTimings.defaults();SwordSettings.defaults();return;}
        apply();
    }
    public static void apply() {
        if(!SPEC.isLoaded())return;
        SwordSettings.replace(new SwordSettings.Values(SWORD_BASE_DAMAGE.get(),SWORD_UNDEAD_MULTIPLIER.get(),SWORD_CURSED_MULTIPLIER.get(),List.copyOf(SWORD_SPECIAL_MOBS.get()),SwordSettings.parseMultipliers(SWORD_MOB_MULTIPLIERS.get())));
        Map<AdaptationProfile,StageDurations> profiles=new EnumMap<>(AdaptationProfile.class);
        CATEGORIES.forEach((p,v)->profiles.put(p,StageDurations.seconds(v.get(0).get(),v.get(1).get(),v.get(2).get(),v.get(3).get(),v.get(4).get())));
        Map<String,List<Integer>> sources=new HashMap<>();
        MOBS.forEach((key,v)->sources.put(key,v.stream().map(ForgeConfigSpec.IntValue::get).toList()));
        for(String rule:SOURCE_OVERRIDES.get()) {
            String[] pair=rule.split("=",-1);
            sources.put(DamageCategories.canonicalKey(pair[0].trim()),Arrays.stream(pair[1].split(",")).map(String::trim).map(Integer::parseInt).toList());
        }
        AdaptationTimings.replace(profiles,sources,HIT_ACCELERATION_PERCENT.get(),new StageProtections(
            PROTECTION_PERCENT.get(0).get(),PROTECTION_PERCENT.get(1).get(),PROTECTION_PERCENT.get(2).get(),PROTECTION_PERCENT.get(3).get()),FULL_HEAL_AFTER_STAGE.get(),EXISTENCE_ENABLED.get(),ONE_HIT_KILL_ENABLED.get());
        AdaptationTimings.setAnalysisEnabled(ANALYSIS_ENABLED.get());
        AdaptationTimings.setElementExperience(ELEMENT_EXPERIENCE.get());
    }
    public static boolean validOverride(Object object) {
        if(!(object instanceof String rule))return false;
        String[] pair=rule.split("=",-1);
        if(pair.length!=2 || !pair[0].trim().matches("(?:mob|damage|effect|condition|attack):[a-z0-9_.-]+:[a-z0-9_./-]+|environment:[a-z0-9_]+"))return false;
        String[] seconds=pair[1].split(",",-1);if(seconds.length!=5)return false;
        try {for(String number:seconds) {int value=Integer.parseInt(number.trim());if(value<0||value>StageDurations.MAX_SECONDS)return false;}return true;}
        catch(NumberFormatException invalid){return false;}
    }
}
