package ru.pasha.adaptationwheel.server;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import ru.pasha.adaptationwheel.core.StatePlan;

/**
 * Bounded execution endpoint for structurally verified custom negative-state writes.
 * Registration never scans or resets a capability. The discovery bridge must prove
 * the exact native write, healthy baseline, affected player and causal harmful scope.
 * In particular, deserialization, client sync, quests and self-paid abilities must
 * never supply causalHarm=true solely because their field name looks negative.
 */
public final class AdaptiveStatePlans {
    private static final int LIMIT = 1024;
    private static final Map<String, StatePlan> PLANS = new HashMap<>();
    public static final String THIRST = "native:toughasnails/thirst", HYDRATION = "native:toughasnails/hydration";
    public static final String SANITY = "native:sanitydim/insanity", RADIATION = "native:mekanism/radiation";
    static {
        register(new StatePlan(THIRST, "condition:toughasnails:thirst", "I", StatePlan.Direction.LOWER_IS_HARMFUL, 20, 0, 20));
        register(new StatePlan(HYDRATION, "condition:toughasnails:thirst", "F", StatePlan.Direction.LOWER_IS_HARMFUL, 2, 0, 20));
        // Despite its API name, SanityDim's maximum getSanity() is its MOST insane player.
        register(new StatePlan(SANITY, "condition:sanitydim:insanity", "F", StatePlan.Direction.HIGHER_IS_HARMFUL, 0, 0, 1));
        register(new StatePlan(RADIATION, "condition:mekanism:radiation", "D", StatePlan.Direction.HIGHER_IS_HARMFUL, 1e-7, 1e-7, Double.MAX_VALUE));
    }
    private AdaptiveStatePlans() {}

    /** Called only by the server-side structural discovery coordinator, after validation. */
    public static synchronized boolean register(StatePlan plan) {
        if (plan == null) return false;
        StatePlan previous = PLANS.get(plan.id());
        if (previous != null) return previous.equals(plan);
        if (PLANS.size() >= LIMIT) return false;
        PLANS.put(plan.id(), plan); return true;
    }
    public static synchronized int registered() { return PLANS.size(); }
    private static synchronized StatePlan lookup(String id) { return PLANS.get(id); }

    private static double factor(Entity entity, StatePlan plan, boolean causalHarm) {
        if (!causalHarm || plan == null || !(entity instanceof ServerPlayer player)
                || !player.server.isSameThread() || !player.isAlive() || player.isSpectator() || player.isCreative() || !AdaptationEvents.active(player)
                || !AdaptationEvents.state(player).categoryEnabled(plan.conditionKey())) return 1;
        AdaptationEvents.continuousExposure(player, plan.conditionKey());
        return AdaptationEvents.state(player).damageMultiplier(plan.conditionKey());
    }
    public static double number(Entity victim, String id, double before, double requested, boolean causalHarm) {
        StatePlan plan = lookup(id);
        if (plan == null || !plan.accepts(before) || !plan.accepts(requested)
                || !plan.harmful(requested)) return requested;
        return plan.protectNumber(before, requested, factor(victim, plan, causalHarm));
    }
    public static float floating(Entity victim, String id, float before, float requested, boolean causalHarm) {
        StatePlan plan = lookup(id);
        if (plan == null || !plan.nativeDescriptor().equals("F")) return requested;
        return (float)number(victim, id, before, requested, causalHarm);
    }
    public static int integer(Entity victim, String id, int before, int requested, boolean causalHarm) {
        StatePlan plan = lookup(id);
        if (plan == null || !plan.nativeDescriptor().equals("I") || !plan.accepts(before)
                || !plan.accepts(requested) || !plan.harmful(requested)) return requested;
        return plan.protectInt(before, requested, factor(victim, plan, causalHarm));
    }
    public static long wide(Entity victim, String id, long before, long requested, boolean causalHarm) {
        StatePlan plan = lookup(id);
        if (plan == null || !plan.nativeDescriptor().equals("J") || !plan.accepts(before)
                || !plan.accepts(requested) || !plan.harmful(requested)) return requested;
        return plan.protectLong(before, requested, factor(victim, plan, causalHarm));
    }
    public static boolean flag(Entity victim, String id, boolean before, boolean requested, boolean causalHarm) {
        StatePlan plan = lookup(id);
        if (plan == null || !plan.nativeDescriptor().equals("Z") || !plan.harmful(requested ? 1 : 0)) return requested;
        return plan.protectBoolean(before, requested, factor(victim, plan, causalHarm));
    }

    private record Scalar(Method get, Method set) {
        double value(Object capability) { return ((Number)invoke(get, capability)).doubleValue(); }
        void write(Object capability, Object value) { invoke(set, capability, value); }
    }
    private static ClassValue<Scalar> scalar(String get, String set, Class<?> type) {
        return new ClassValue<>() {
            @Override protected Scalar computeValue(Class<?> owner) {
                try { return new Scalar(owner.getMethod(get), owner.getMethod(set, type)); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException("Audited state schema changed: " + owner.getName(), error); }
            }
        };
    }
    private static final ClassValue<Scalar> THIRST_ACCESS = scalar("getThirst", "setThirst", int.class);
    private static final ClassValue<Scalar> HYDRATION_ACCESS = scalar("getHydration", "setHydration", float.class);
    private static final ClassValue<Scalar> SANITY_ACCESS = scalar("getSanity", "setSanity", float.class);
    private static final ClassValue<Scalar> RADIATION_ACCESS = scalar("getRadiation", "set", double.class);
    private static Object invoke(Method method, Object instance, Object... args) {
        try { return method.invoke(instance, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException error) throw error;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Native custom-state operation failed", failure.getCause());
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native custom-state operation unavailable", failure); }
    }

    /** Only the two setter call sites inside native ThirstHandler.onPlayerTick are replaced. */
    public static void toughThirst(Object capability, int requested, Entity victim) {
        Scalar access = THIRST_ACCESS.get(capability.getClass());
        access.write(capability, integer(victim, THIRST, (int)access.value(capability), requested, true));
    }
    public static void toughHydration(Object capability, float requested, Entity victim) {
        Scalar access = HYDRATION_ACCESS.get(capability.getClass());
        access.write(capability, floating(victim, HYDRATION, (float)access.value(capability), requested, true));
    }
    /** Clear already depleted native state before its tick can apply its secondary symptoms. */
    public static void toughTick(Object capability, Entity victim) {
        if (!(victim instanceof ServerPlayer player) || !player.server.isSameThread() || !AdaptationEvents.active(player)) return;
        Scalar thirst = THIRST_ACCESS.get(capability.getClass()), hydration = HYDRATION_ACCESS.get(capability.getClass());
        int oldThirst = (int)thirst.value(capability), newThirst = integer(victim, THIRST, oldThirst, oldThirst, true);
        if (oldThirst != newThirst) thirst.write(capability, newThirst);
        float oldHydration = (float)hydration.value(capability), newHydration = floating(victim, HYDRATION, oldHydration, oldHydration, true);
        if (oldHydration != newHydration) hydration.write(capability, newHydration);
    }
    /** Only native active-source and passive-tick writes are replaced; NBT/sync/commands are untouched. */
    public static void sanity(Object capability, float requested, Entity victim) {
        Scalar access = SANITY_ACCESS.get(capability.getClass());
        // Sanity.setSanity clamps AFTER callers add their delta. Mirror that exact
        // finite native range before plan validation, including already-insane 1 + delta.
        if(Float.isFinite(requested))requested=Math.max(0,Math.min(1,requested));
        access.write(capability, floating(victim, SANITY, (float)access.value(capability), requested, true));
    }

    /**
     * The native update receives the real affected LivingEntity. Preserve dose decay
     * and partial state history; full adaptation clears before native damage/food
     * exhaustion. Background doses below Mekanism's own harm threshold are ignored.
     */
    public static void radiationTick(Object capability, Entity victim) {
        if (!(victim instanceof ServerPlayer player) || !player.server.isSameThread() || !AdaptationEvents.active(player)) return;
        Scalar access = RADIATION_ACCESS.get(capability.getClass()); double dose = access.value(capability);
        if (!MekHarm.harmful(dose)) return;
        StatePlan plan = lookup(RADIATION);
        if (plan != null && plan.accepts(dose) && factor(victim, plan, true) == 0) access.write(capability, plan.healthy());
    }
    /** Scale the two native symptom reads, preserving the accumulated/decaying saved dose. */
    public static double radiationDose(Object capability, Entity victim) {
        Scalar access = RADIATION_ACCESS.get(capability.getClass()); double dose = access.value(capability);
        if (!MekHarm.harmful(dose)) return dose;
        StatePlan plan = lookup(RADIATION);
        return plan == null ? dose : number(victim, RADIATION, plan.healthy(), dose, true);
    }
    private static final class MekHarm {
        private static Method severity;
        private static Object minimum;
        private static Method readMinimum;
        private static boolean unavailable;
        private static boolean harmful(double dose) {
            if (unavailable || !Double.isFinite(dose)) return false;
            try {
                if (severity == null) {
                    severity = Class.forName("mekanism.common.lib.radiation.RadiationManager$RadiationScale").getMethod("getScaledDoseSeverity", double.class);
                    Object config = Class.forName("mekanism.common.config.MekanismConfig").getField("general").get(null);
                    minimum = config.getClass().getField("radiationNegativeEffectsMinSeverity").get(config);
                    readMinimum = minimum.getClass().getMethod("get");
                }
                return ((Number)invoke(severity, null, dose)).doubleValue() > ((Number)invoke(readMinimum, minimum)).doubleValue();
            } catch (ReflectiveOperationException | RuntimeException changedSchema) {
                unavailable = true; return false;
            }
        }
    }
}
