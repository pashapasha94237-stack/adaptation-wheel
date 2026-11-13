package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.capabilities.Capability;
import static ru.pasha.multimodqa.MultiModQa.*;

/** Actual optional-mod APIs and native negative state/HP, never calls production guard shortcuts. */
final class NativeStateQa {
    static final String THIRST = "condition:toughasnails:thirst", SANITY = "condition:sanitydim:insanity", RADIATION = "condition:mekanism:radiation";
    static final Class<?>[] NONE = {};
    static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        try { return target.getClass().getMethod(name, types).invoke(target, args); }
        catch (InvocationTargetException error) { throw new RuntimeException("Native state operation failed: " + name, error.getCause()); }
    }
    static double value(Object target, String name) throws Exception { return ((Number)invoke(target, name, NONE)).doubleValue(); }
    static void setInt(Object target, String name, int value) throws Exception { invoke(target, name, new Class<?>[]{int.class}, value); }
    static void setFloat(Object target, String name, float value) throws Exception { invoke(target, name, new Class<?>[]{float.class}, value); }
    static void close(double actual, double expected, String label) { check(Math.abs(actual - expected) < 1e-5, label + ": " + actual + " != " + expected); }
    @SuppressWarnings("unchecked") static Object capability(ServerPlayer p, String owner, String field) throws Exception {
        var token = (Capability<Object>)Class.forName(owner).getField(field).get(null);
        Object cap = p.getCapability(token).orElse(null); check(cap != null, "Native player capability missing: " + owner); return cap;
    }
    static void begin(ServerPlayer p, String key, int targetStage) throws Exception {
        reset(p); state(p).getClass().getMethod("setEnabled", boolean.class).invoke(state(p), true);
        state(p).getClass().getMethod("setCategoryEnabled", String.class, boolean.class).invoke(state(p), key, true);
        if (targetStage > 0) learn(p, key, targetStage);
    }
    static void run(ServerPlayer p) throws Exception {
        p.serverLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, p.server);
        String system = group.get("stateSystem").getAsString();
        switch (system) { case "tan" -> thirst(p); case "sanity" -> sanity(p); case "mekanism" -> radiation(p); default -> throw new AssertionError("Unknown native state system " + system); }
        LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_STATES PASS: {} native state mutation, partial/full protection, disable/unequip and recovery controls", system);
    }

    static void tanTick(ServerPlayer p) throws Exception { call("toughasnails.thirst.ThirstHandler", "onPlayerTick", new Class<?>[]{Player.class}, p); }
    static void tanState(Object cap, int thirst, float hydration, float exhaustion, int ticks) throws Exception {
        setInt(cap, "setThirst", thirst); setFloat(cap, "setHydration", hydration); setFloat(cap, "setExhaustion", exhaustion); setInt(cap, "setTickTimer", ticks);
    }
    static void thirst(ServerPlayer p) throws Exception {
        Object cap = call("toughasnails.api.thirst.ThirstHelper", "getThirst", new Class<?>[]{Player.class}, p);
        int savedThirst = (int)value(cap, "getThirst"), savedTicks = (int)value(cap, "getTickTimer");
        float savedHydration = (float)value(cap, "getHydration"), savedExhaustion = (float)value(cap, "getExhaustion");
        Object config = Class.forName("toughasnails.init.ModConfig").getField("thirst").get(null);
        float exhausted = ((Number)config.getClass().getField("thirstExhaustionThreshold").get(config)).floatValue() + 1;
        ItemStack wheel = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        try {
            begin(p, THIRST, 0); tanState(cap, 4, 0, exhausted, 0); tanTick(p);
            close(value(cap, "getThirst"), 3, "Unadapted native thirst decrement");
            check(stage(p, THIRST) == 0, "First native thirst exposure granted a free stage");
            tanState(cap, 0, 0, 0, 79); float hp = health(p); p.invulnerableTime = 0; tanTick(p);
            check(health(p) < hp, "Unadapted native ThirstHandler starvation did not remove actual HP");
            begin(p, THIRST, 1); tanState(cap, 20, 1, exhausted, 0); tanTick(p);
            close(value(cap, "getHydration"), .2, "Stage one native hydration loss is 20% smaller");
            begin(p, THIRST, 4); tanState(cap, 0, 0, exhausted, 79); hp = health(p); tanTick(p);
            close(value(cap, "getThirst"), 20, "Full protection clears already depleted native thirst");
            close(value(cap, "getHydration"), 2, "Full protection restores native safe hydration");
            check(health(p) == hp, "Full native thirst protection acted after HP was already lost");
            // Native setters outside the survival tick are not globally hijacked.
            setInt(cap, "setThirst", 4); invoke(cap, "addThirst", new Class<?>[]{int.class}, 2);
            close(value(cap, "getThirst"), 6, "Native drinking/recovery was rewritten by full protection");
            controlDisable(p, THIRST); tanState(cap, 0, 0, 0, 79); hp = health(p); p.invulnerableTime = 0; tanTick(p);
            check(value(cap, "getThirst") == 0 && health(p) < hp, "Disabled native thirst protection still changed state or blocked HP loss");
            state(p).getClass().getMethod("setCategoryEnabled", String.class, boolean.class).invoke(state(p), THIRST, true);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); tanState(cap, 0, 0, 0, 79); hp = health(p); p.invulnerableTime = 0; tanTick(p);
            check(value(cap, "getThirst") == 0 && health(p) < hp, "Unequipped native thirst protection remained active");
            LogUtils.getLogger().info("MULTIMOD_QA NATIVE_TAN PASS: native thirst 4->3 and real starvation HP loss; hydration 1->0.2 at stage1; stage4 native state20/2 before starvation; drinking/disable/unequip native");
        } finally { p.setItemSlot(EquipmentSlot.HEAD, wheel); tanState(cap, savedThirst, savedHydration, savedExhaustion, savedTicks); reset(p); }
    }

    static void sanityAdd(ServerPlayer p, Object cap, float delta) throws Exception {
        call("croissantnova.sanitydim.SanityProcessor", "addSanity", new Class<?>[]{Class.forName("croissantnova.sanitydim.capability.ISanity"), float.class, ServerPlayer.class}, cap, delta, p);
    }
    static void sanity(ServerPlayer p) throws Exception {
        Object cap = capability(p, "croissantnova.sanitydim.capability.SanityProvider", "CAP");
        float saved = (float)value(cap, "getSanity"); ItemStack wheel = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        try {
            begin(p, SANITY, 0); setFloat(cap, "setSanity", 0); sanityAdd(p, cap, .2F);
            double baseline = value(cap, "getSanity"); check(baseline > 0 && baseline < 1, "Native positive insanity source did not worsen actual sanity meter");
            check(stage(p, SANITY) == 0, "First native sanity exposure granted a free stage");
            setFloat(cap, "setSanity", 1); sanityAdd(p, cap, .1F); close(value(cap, "getSanity"), 1, "Unadapted native upper-bound clamp");
            begin(p, SANITY, 1); setFloat(cap, "setSanity", 0); sanityAdd(p, cap, .2F);
            close(value(cap, "getSanity"), baseline * .8, "Stage one native insanity increment");
            // Derive the actual recovery result from the mod, then compare with stage one.
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); setFloat(cap, "setSanity", .8F); sanityAdd(p, cap, -.2F);
            double recovery = value(cap, "getSanity"); check(recovery < .8, "Native sanity recovery control did not improve meter");
            p.setItemSlot(EquipmentSlot.HEAD, wheel); setFloat(cap, "setSanity", .8F); sanityAdd(p, cap, -.2F);
            close(value(cap, "getSanity"), recovery, "Adaptation weakened beneficial native sanity recovery");
            begin(p, SANITY, 4); setFloat(cap, "setSanity", 1); close(value(cap, "getSanity"), 1, "Direct setup/load-style setter was globally blocked");
            sanityAdd(p, cap, .1F); close(value(cap, "getSanity"), 0, "Stage four failed to clear native clamped 1+delta boundary");
            setFloat(cap, "setSanity", .8F);
            call("croissantnova.sanitydim.SanityProcessor", "tickPlayer", new Class<?>[]{ServerPlayer.class}, p);
            close(value(cap, "getSanity"), 0, "Full native passive tick left prior insanity active");
            controlDisable(p, SANITY); setFloat(cap, "setSanity", 0); sanityAdd(p, cap, .2F);
            close(value(cap, "getSanity"), baseline, "Disabled native insanity protection remained active");
            state(p).getClass().getMethod("setCategoryEnabled", String.class, boolean.class).invoke(state(p), SANITY, true);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); setFloat(cap, "setSanity", 0); sanityAdd(p, cap, .2F);
            close(value(cap, "getSanity"), baseline, "Unequipped native insanity protection remained active");
            LogUtils.getLogger().info("MULTIMOD_QA NATIVE_SANITY PASS: real optional-mod meter worsens by {}; stage1=20% less; actual stage4 active/passive state cleared including 1+delta native clamp; recovery/direct setters/disable/unequip retained (no HP claim for this non-HP state)", baseline);
        } finally { p.setItemSlot(EquipmentSlot.HEAD, wheel); setFloat(cap, "setSanity", saved); reset(p); }
    }

    static void dose(Object cap, double amount) throws Exception { invoke(cap, "set", new Class<?>[]{double.class}, amount); }
    static void radiationTick(Object cap, ServerPlayer p) throws Exception { p.invulnerableTime = 0; invoke(cap, "update", new Class<?>[]{LivingEntity.class}, p); }
    static float firstRadiationLoss(Object cap, ServerPlayer p) throws Exception {
        float hp = health(p);
        for (int n = 0; n < 32 && health(p) == hp; n++) radiationTick(cap, p);
        float lost = hp - health(p); check(lost > 0, "Native Mekanism radiation did not remove actual HP within32 real native updates"); return lost;
    }
    static void radiation(ServerPlayer p) throws Exception {
        Object cap = capability(p, "mekanism.common.capabilities.Capabilities", "RADIATION_ENTITY");
        double saved = value(cap, "getRadiation"); float food = p.getFoodData().getExhaustionLevel();
        ItemStack wheel = p.getItemBySlot(EquipmentSlot.HEAD).copy(); final double dangerous = 1e6;
        try {
            begin(p, RADIATION, 0); dose(cap, dangerous); float baseline = firstRadiationLoss(cap, p);
            close(value(cap, "getRadiation"), dangerous, "Unadapted native update changed saved dose");
            check(stage(p, RADIATION) == 0, "First native radiation exposure granted a free stage");
            begin(p, RADIATION, 1); dose(cap, dangerous); float partial = firstRadiationLoss(cap, p);
            check(partial < baseline - .01F, "Partial native symptom-dose scaling failed to reduce actual radiation HP loss: " + baseline + " -> " + partial);
            close(value(cap, "getRadiation"), dangerous, "Partial protection rewrote saved radiation instead of its effective symptom dose");
            begin(p, RADIATION, 4); dose(cap, dangerous); float hp = health(p), beforeFood = p.getFoodData().getExhaustionLevel();
            for (int n = 0; n < 16; n++) radiationTick(cap, p);
            close(value(cap, "getRadiation"), 1e-7, "Full native radiation state was not actually cleared");
            check(health(p) == hp && p.getFoodData().getExhaustionLevel() == beforeFood, "Full native radiation symptoms changed HP/food before state cleanup");
            // Native Mekanism has no 1e12 dose ceiling. Finite extreme exposure
            // must not silently bypass a supposedly complete state adaptation.
            dose(cap, 2e12); radiationTick(cap, p);
            close(value(cap, "getRadiation"), 1e-7, "Finite dose above the old arbitrary plan bound bypassed full radiation protection");
            check(health(p) == hp && p.getFoodData().getExhaustionLevel() == beforeFood, "Extreme finite radiation damaged/exhausted fully adapted player");
            dose(cap, dangerous); invoke(cap, "decay", NONE); double decayed = value(cap, "getRadiation");
            check(decayed < dangerous && decayed > 1e-7, "Native radiation decay/load setter was hijacked by full protection");
            controlDisable(p, RADIATION); dose(cap, dangerous); firstRadiationLoss(cap, p);
            close(value(cap, "getRadiation"), dangerous, "Disabled native radiation protection still cleared dose");
            state(p).getClass().getMethod("setCategoryEnabled", String.class, boolean.class).invoke(state(p), RADIATION, true);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); dose(cap, dangerous); firstRadiationLoss(cap, p);
            close(value(cap, "getRadiation"), dangerous, "Unequipped native radiation protection still cleared dose");
            LogUtils.getLogger().info("MULTIMOD_QA NATIVE_MEKANISM PASS: actual native radiation HP loss {} -> partial {}; saved dose preserved until full real state cleanup1e-7 BEFORE HP/food effects; native decay, disable, unequip retained", baseline, partial);
        } finally { p.setItemSlot(EquipmentSlot.HEAD, wheel); dose(cap, saved); p.getFoodData().setExhaustion(food); reset(p); }
    }
}
