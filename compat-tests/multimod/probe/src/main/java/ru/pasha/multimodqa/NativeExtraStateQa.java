package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.NativeStateQa.*;

/** Actual new custom-state controllers plus an explicitly labelled old Ad Astra regression. */
final class NativeExtraStateQa {
    static final String NC = "condition:nuclearcraft:radiation", SEA = "condition:dragonsurvival:dehydration";
    static final String NC_CLASS = "igentuman.nc.radiation.data.PlayerRadiation";
    static final String DS = "by.dragonsurvivalteam.dragonsurvival.";
    static void run(ServerPlayer p) throws Exception {
        p.serverLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, p.server);
        switch (group.get("extraStateSystem").getAsString()) {
            case "nuclearcraft" -> nuclear(p);
            case "sea-dragon" -> sea(p);
            case "ad-astra-regression" -> oxygen(p);
            default -> throw new AssertionError("Unknown extra custom-state fixture");
        }
    }
    static long radiation(Object cap) throws Exception { return ((Number)invoke(cap, "getRadiation", NONE)).longValue(); }
    static void radiation(Object cap, long value) throws Exception { invoke(cap, "setRadiation", new Class<?>[]{long.class}, value); }
    static int contamination(Object cap) throws Exception {
        Field field = cap.getClass().getDeclaredField("contaminationStage"); field.setAccessible(true); return field.getInt(cap);
    }
    static void nuclearStage(Object cap, ServerPlayer p) throws Exception { invoke(cap, "updateContaminationStage", new Class<?>[]{Player.class}, p); }
    static void nuclearWhole(Object cap, ServerPlayer p) throws Exception { invoke(cap, "updateRadiation", new Class<?>[]{Level.class, LivingEntity.class}, p.level(), p); }
    static void nuclear(ServerPlayer p) throws Exception {
        Object cap = capability(p, "igentuman.nc.radiation.data.PlayerRadiationProvider", "PLAYER_RADIATION");
        CompoundTag saved = ((CompoundTag)invoke(cap, "serializeNBT", NONE)).copy(); ItemStack wheel = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        long maximum = Class.forName(NC_CLASS).getField("maxPlayerRadiation").getLong(null);
        check(maximum >= 1000 && maximum < Integer.MAX_VALUE / 2, "Unexpected isolated native NC maximum: " + maximum);
        long severe = maximum / 10 * 7, overMaximum = maximum + Math.max(1_000_000L, maximum / 10);
        try {
            begin(p, NC, 0); radiation(cap, Long.MAX_VALUE); nuclearWhole(cap, p);
            check(radiation(cap) == 0, "Native Long.MAX_VALUE averaging overflow control changed; do not mistake this native zero for wheel protection");
            LogUtils.getLogger().info("MULTIMOD_QA CORPUS_NC_NATIVE_OVERFLOW CONTROL: no-stage native whole update Long.MAX_VALUE ->0 from its own arithmetic; separate nonoverflow over-max whole-update proof is required");
            begin(p, NC, 0); double attack = p.getAttributeValue(Attributes.ATTACK_DAMAGE); radiation(cap, severe); nuclearWhole(cap, p);
            check(contamination(cap) == 3 && p.hasEffect(MobEffects.POISON), "Native radiation did not cause actual severe contamination effects");
            int nativeWeakness = p.getEffect(MobEffects.WEAKNESS).getAmplifier();
            check(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < attack, "Native contamination did not weaken the real player attribute");
            float hp = health(p); p.invulnerableTime = 0; MobEffects.POISON.applyEffectTick(p, 0);
            check(health(p) < hp, "Actual native contamination poison caused no HP loss");
            LogUtils.getLogger().info("MULTIMOD_QA CORPUS_NC_NATIVE_BASELINE: real stage{}, weakness amplifier{}, attack attribute{}->{}, poison HP{}->{}", contamination(cap), nativeWeakness, attack, p.getAttributeValue(Attributes.ATTACK_DAMAGE), hp, health(p));
            begin(p, NC, 1); radiation(cap, severe); nuclearStage(cap, p);
            check(contamination(cap) == 2 && p.getEffect(MobEffects.WEAKNESS).getAmplifier() < nativeWeakness,
                    "Partial NC adaptation did not reduce the real native contamination stage/amplifier");
            check(radiation(cap) == severe, "Partial NC protection rewrote persistent dose/decay instead of effective symptom reads");
            begin(p, NC, 4);
            // A previously saved severe state must really be present before the
            // cure. reset()/removeAllEffects() is setup, not evidence of cleanup.
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); radiation(cap, severe); nuclearStage(cap, p);
            check(contamination(cap) == 3 && p.hasEffect(MobEffects.WEAKNESS) && p.hasEffect(MobEffects.POISON)
                    && p.getAttributeValue(Attributes.ATTACK_DAMAGE) < attack, "Preexisting native contamination fixture was not actually installed");
            p.setItemSlot(EquipmentSlot.HEAD, wheel); invoke(cap, "setTimestamp", new Class<?>[]{int.class}, 123);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 500));
            radiation(cap, overMaximum); hp = health(p); nuclearWhole(cap, p);
            check(p.isAlive() && health(p) == hp && radiation(cap) == 0 && contamination(cap) == 0,
                    "Full NC state protection failed before native WHOLE-update maximum-dose execution");
            check(!p.hasEffect(MobEffects.WEAKNESS) && !p.hasEffect(MobEffects.POISON)
                    && ((List<?>)cap.getClass().getField("contaminationEffects").get(cap)).isEmpty(), "Native stage-zero cleanup left old contamination state/effects");
            close(p.getAttributeValue(Attributes.ATTACK_DAMAGE), attack, "Native stage-zero cleanup left stale contamination attribute modifiers");
            check(p.hasEffect(MobEffects.MOVEMENT_SPEED) && value(cap, "getTimestamp") == 123, "NC cleanup erased beneficial effects or unrelated capability state");
            // Native averaging can itself overflow for Long.MAX_VALUE. Therefore
            // the nonoverflow overMaximum whole-update assertion above is essential.
            radiation(cap, Long.MAX_VALUE); nuclearWhole(cap, p);
            check(p.isAlive() && health(p) == hp && radiation(cap) == 0, "Whole NC update failed its full native-long range control");
            radiation(cap, Long.MAX_VALUE); nuclearStage(cap, p);
            check(p.isAlive() && health(p) == hp && radiation(cap) == 0, "Direct native stage controller bypassed full protection above2^53");
            controlDisable(p, NC); radiation(cap, severe); nuclearStage(cap, p);
            check(contamination(cap) == 3 && p.hasEffect(MobEffects.POISON), "Disabled NC protection still suppressed actual contamination");
            state(p).getClass().getMethod("setCategoryEnabled", String.class, boolean.class).invoke(state(p), NC, true);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); radiation(cap, severe); nuclearStage(cap, p);
            check(contamination(cap) == 3 && p.hasEffect(MobEffects.WEAKNESS), "Unequipped NC protection remained active");
            radiation(cap, 0); nuclearStage(cap, p);
            check(contamination(cap) == 0 && !p.hasEffect(MobEffects.POISON), "Native radiation recovery/stage-zero cleanup was blocked");
            LogUtils.getLogger().info("MULTIMOD_QA CORPUS_NUCLEARCRAFT PASS: native whole update severe dose creates stage3/weakness/poison and actual HP loss; partial stage2 with persistent dose retained; full WHOLE over-max native execution and Long.MAX_VALUE controller blocked before HP, actual dose/stage/effects cleared; speed/timestamp/disable/unequip/recovery retained");
        } finally { p.setItemSlot(EquipmentSlot.HEAD, wheel); invoke(cap, "deserializeNBT", new Class<?>[]{CompoundTag.class}, saved); nuclearStage(cap, p); reset(p); }
    }

    static void seaTick(Object type, Object handler, ServerPlayer p) throws Exception {
        invoke(type, "onPlayerUpdate", new Class<?>[]{Player.class, Class.forName(DS + "common.capability.DragonStateHandler")}, p, handler);
    }
    static void sea(ServerPlayer p) throws Exception {
        Object handler = call(DS + "common.capability.DragonStateProvider", "getHandler", new Class<?>[]{net.minecraft.world.entity.Entity.class}, p);
        check(handler != null, "Actual Dragon Survival player capability absent");
        CompoundTag saved = ((CompoundTag)invoke(handler, "writeNBT", NONE)).copy(); ItemStack wheel = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        long day = p.serverLevel().getDayTime(); BlockPos feet = p.blockPosition(), below = feet.below();
        var oldFeet = p.serverLevel().getBlockState(feet); var oldBelow = p.serverLevel().getBlockState(below);
        try {
            Object initialSea = Class.forName(DS + "common.dragon_types.types.SeaDragonType").getConstructor().newInstance();
            invoke(handler, "setType", new Class<?>[]{Class.forName(DS + "common.dragon_types.AbstractDragonType")}, initialSea);
            Object type = invoke(handler, "getType", NONE); check(type != null && type.getClass().getSimpleName().equals("SeaDragonType"), "Native player sea-dragon type was not installed");
            Field counter = type.getClass().getField("timeWithoutWater");
            Class<?> config = Class.forName(DS + "config.ServerConfig"); check((Boolean)config.getField("penalties").get(null), "Native dragon penalties disabled in fixture");
            int threshold = (Integer)config.getField("seaTicksWithoutWater").get(null);
            Class<?> waterClass = Class.forName(DS + "magic.abilities.SeaDragon.passive.WaterAbility");
            Object waterAbility = call(DS + "magic.DragonAbilities", "getSelfAbility", new Class<?>[]{LivingEntity.class, Class.class}, p, waterClass);
            if (waterAbility != null) threshold += ((Number)call(DS + "util.Functions", "secondsToTicks", new Class<?>[]{int.class}, (int)value(waterAbility, "getDuration"))).intValue();
            check(threshold > 0 && threshold < 1_000_000, "Unexpected native sea dehydration threshold: " + threshold);
            p.serverLevel().setDayTime(6000); p.serverLevel().setWeatherParameters(6000, 0, false, false);
            p.serverLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 2); p.serverLevel().setBlock(below, Blocks.STONE.defaultBlockState(), 2);
            Object magic = invoke(handler, "getMagicData", NONE); int mana = (int)value(magic, "getCurrentMana"); double size = value(handler, "getSize");
            begin(p, SEA, 0); counter.setDouble(type, 0); p.tickCount = 80; seaTick(type, handler, p);
            double increment = counter.getDouble(type); check(increment > 0 && increment < 10, "Native dry sea-dragon update did not increase real custom counter");
            counter.setDouble(type, threshold * 3d); float hp = health(p); p.invulnerableTime = 0; seaTick(type, handler, p);
            check(health(p) < hp, "Native dehydrated sea-dragon update did not remove actual HP");
            LogUtils.getLogger().info("MULTIMOD_QA CORPUS_SEA_NATIVE_BASELINE: real dry delta{}, saved counter3x{} ->{}, native dehydration HP{}->{}", increment, threshold, counter.getDouble(type), hp, health(p));
            begin(p, SEA, 1); counter.setDouble(type, 0); seaTick(type, handler, p);
            close(counter.getDouble(type), increment * .8, "Partial native sea dehydration delta");
            // Full protection must cure an already dangerous saved meter BEFORE
            // the original onPlayerUpdate reaches either native hurt branch.
            begin(p, SEA, 4); counter.setDouble(type, threshold * 3d); hp = health(p); p.invulnerableTime = 0; seaTick(type, handler, p);
            check(counter.getDouble(type) == 0 && health(p) == hp, "Full sea-dragon state protection left prior dehydration/HP harm active");
            check(invoke(handler, "getType", NONE) == type && value(handler, "getSize") == size && value(magic, "getCurrentMana") == mana,
                    "Dehydration handler changed species, body size or ability resource");
            controlDisable(p, SEA); counter.setDouble(type, threshold * 3d); hp = health(p); p.invulnerableTime = 0; seaTick(type, handler, p);
            check(counter.getDouble(type) > threshold && health(p) < hp, "Disabled sea dehydration protection remained active");
            state(p).getClass().getMethod("setCategoryEnabled", String.class, boolean.class).invoke(state(p), SEA, true);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); counter.setDouble(type, threshold * 3d); hp = health(p); p.invulnerableTime = 0; seaTick(type, handler, p);
            check(counter.getDouble(type) > threshold && health(p) < hp, "Unequipped sea dehydration protection remained active");
            var cauldron = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
            p.serverLevel().setBlock(feet, cauldron, 2); p.serverLevel().setBlock(below, cauldron, 2);
            check((Boolean)call(DS + "common.handlers.DragonTraitHandler", "isInCauldron", new Class<?>[]{net.minecraft.world.level.block.state.BlockState.class, net.minecraft.world.level.block.state.BlockState.class}, cauldron, cauldron), "Native recovery cauldron is not recognized");
            counter.setDouble(type, threshold); seaTick(type, handler, p); double nativeRecovery = counter.getDouble(type);
            check(nativeRecovery < threshold, "Native water/cauldron recovery failed");
            p.setItemSlot(EquipmentSlot.HEAD, wheel); begin(p, SEA, 1); counter.setDouble(type, threshold); seaTick(type, handler, p);
            close(counter.getDouble(type), nativeRecovery, "Partial adaptation weakened native water recovery");
            MobEffect peace = (MobEffect)Class.forName(DS + "registry.DragonEffects").getField("PEACE").get(null);
            p.addEffect(new MobEffectInstance(peace, 200)); p.setAirSupply(-10); seaTick(type, handler, p);
            check(p.getAirSupply() == p.getMaxAirSupply(), "State handler skipped native beneficial breathing/peace branch");
            LogUtils.getLogger().info("MULTIMOD_QA CORPUS_SEA_DRAGON PASS: real dry native counter delta{} and actual DEHYDRATION HP loss; partial delta20% smaller; full prior-dangerous meter cured before native hurt; disable/unequip/water recovery/peace air/species/size/mana retained", increment);
        } finally {
            p.setItemSlot(EquipmentSlot.HEAD, wheel); p.serverLevel().setBlock(feet, oldFeet, 2); p.serverLevel().setBlock(below, oldBelow, 2); p.serverLevel().setDayTime(day);
            invoke(handler, "readNBT", new Class<?>[]{CompoundTag.class}, saved); reset(p);
        }
    }

    /** Existing 4.26 coverage: regression only, never counted as a newly synthesized state adapter. */
    static void oxygen(ServerPlayer p) throws Exception {
        ServerLevel original = p.serverLevel(); var position = p.position(); ItemStack wheel = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        ServerLevel mars = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation("ad_astra", "mars")));
        check(mars != null, "Native Ad Astra Mars dimension absent");
        Object oxygen = Class.forName("earth.terrarium.adastra.common.systems.OxygenApiImpl").getConstructor().newInstance(); String key = family("OxygenApiImpl", "entityTick");
        try {
            p.teleportTo(mars, 0, 90, 0, 0, 0); mars.getChunkAt(p.blockPosition());
            begin(p, key, 0); float hp = health(p); p.invulnerableTime = 0; invoke(oxygen, "entityTick", new Class<?>[]{ServerLevel.class, LivingEntity.class}, mars, p);
            check(health(p) < hp && p.getAirSupply() == -80, "Native Mars vacuum did not produce actual HP/air harm");
            begin(p, key, 4); hp = health(p); p.setAirSupply(-80); invoke(oxygen, "entityTick", new Class<?>[]{ServerLevel.class, LivingEntity.class}, mars, p);
            check(health(p) == hp && p.getAirSupply() == p.getMaxAirSupply(), "Old Ad Astra full protection/air restoration regressed");
            controlDisable(p, key); hp = health(p); p.invulnerableTime = 0; invoke(oxygen, "entityTick", new Class<?>[]{ServerLevel.class, LivingEntity.class}, mars, p);
            check(health(p) < hp && p.getAirSupply() == -80, "Disabled old vacuum protection stayed active");
            state(p).getClass().getMethod("setCategoryEnabled", String.class, boolean.class).invoke(state(p), key, true);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); hp = health(p); p.invulnerableTime = 0; invoke(oxygen, "entityTick", new Class<?>[]{ServerLevel.class, LivingEntity.class}, mars, p);
            check(health(p) < hp, "Unequipped old vacuum protection stayed active");
            LogUtils.getLogger().info("MULTIMOD_QA CORPUS_ADASTRA_REGRESSION PASS: existing old native Mars oxygen HP/air protection and disable/unequip retained; no new coverage claimed");
        } finally { p.setItemSlot(EquipmentSlot.HEAD, wheel); p.teleportTo(original, position.x, position.y, position.z, 0, 0); p.setAirSupply(p.getMaxAirSupply()); reset(p); }
    }
}
