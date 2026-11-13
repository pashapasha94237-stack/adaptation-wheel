package ru.pasha.adaptationwheel.server;

import java.lang.reflect.Field;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import ru.pasha.adaptationwheel.core.NativeStateMath;

/** Two exact custom-state schemas audited in the supplied 616-JAR corpus, not arbitrary capability resets. */
public final class CorpusStateCompatibility {
    public static final String NUCLEAR = "condition:nuclearcraft:radiation";
    public static final String SEA = "condition:dragonsurvival:dehydration";
    private CorpusStateCompatibility() {}
    private static double factor(Entity victim, String key) {
        if (!(victim instanceof ServerPlayer player) || !player.server.isSameThread() || !player.isAlive()
                || player.isCreative() || player.isSpectator() || !AdaptationEvents.active(player)
                || !AdaptationEvents.state(player).categoryEnabled(key)) return 1;
        AdaptationEvents.continuousExposure(player, key);
        return AdaptationEvents.state(player).damageMultiplier(key);
    }
    private static Class<?> declaring(Class<?> actual, String expected) throws ClassNotFoundException {
        for (Class<?> type = actual; type != null; type = type.getSuperclass()) if (type.getName().equals(expected)) return type;
        throw new ClassNotFoundException("Unexpected native state receiver: " + actual.getName());
    }
    private record NuclearAccess(Field radiation, Field maximum) {
        long dose(Object state) {
            try { return radiation.getLong(state); }
            catch (IllegalAccessException failure) { throw new IllegalStateException("Native radiation field inaccessible", failure); }
        }
        void clear(Object state) {
            try { radiation.setLong(state, 0); }
            catch (IllegalAccessException failure) { throw new IllegalStateException("Native radiation field inaccessible", failure); }
        }
        boolean dangerous(long dose) {
            try { long max = maximum.getLong(null); return dose > 0 && max > 0 && (double)dose >= (double)max * .22; }
            catch (IllegalAccessException failure) { throw new IllegalStateException("Native radiation maximum inaccessible", failure); }
        }
    }
    private static final ClassValue<NuclearAccess> NUCLEAR_ACCESS = new ClassValue<>() {
        @Override protected NuclearAccess computeValue(Class<?> owner) {
            try {
                Class<?> nativeType = declaring(owner, "igentuman.nc.radiation.data.PlayerRadiation");
                Field dose = nativeType.getDeclaredField("radiation");
                if (dose.getType() != long.class) throw new NoSuchFieldException("Not a native long dose");
                dose.setAccessible(true);
                return new NuclearAccess(dose, nativeType.getField("maxPlayerRadiation"));
            }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("NuclearCraft radiation schema changed", failure); }
        }
    };
    private static final ClassValue<Field> SEA_ACCESS = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> owner) {
            try { Field field = declaring(owner, "by.dragonsurvivalteam.dragonsurvival.common.dragon_types.types.SeaDragonType").getDeclaredField("timeWithoutWater"); if (field.getType() != double.class) throw new NoSuchFieldException("Not a double counter"); return field; }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Sea dragon dehydration schema changed", failure); }
        }
    };
    /** Native stage-zero cleanup still runs and removes its own previously applied contamination symptoms. */
    public static void beforeNuclearStage(Object state, Entity victim) {
        NuclearAccess access = NUCLEAR_ACCESS.get(state.getClass()); long dose = access.dose(state);
        if (access.dangerous(dose) && factor(victim, NUCLEAR) == 0) access.clear(state);
    }
    /** Scale only the native symptom-stage reads; saved dose, decay, inventory and commands stay native. */
    public static long nuclearDose(Object state, Entity victim) {
        NuclearAccess access = NUCLEAR_ACCESS.get(state.getClass()); long dose = access.dose(state);
        return access.dangerous(dose) ? NativeStateMath.scalePositive(dose, factor(victim, NUCLEAR)) : dose;
    }
    /** Only four writes in SeaDragonType.onPlayerUpdate call this; NBT and other dragon state are untouched. */
    public static void seaCounter(Object dragonType, double requested, Entity victim) {
        Field field = SEA_ACCESS.get(dragonType.getClass());
        try {
            double old = field.getDouble(dragonType), result = requested;
            if (Double.isFinite(old) && old >= 0 && Double.isFinite(requested) && requested > 0) {
                double remaining = factor(victim, SEA);
                if (remaining == 0) result = 0;
                else if (remaining < 1 && requested > old) {
                    double delta = requested - old;
                    if (Double.isFinite(delta)) result = old + delta * remaining;
                }
            }
            field.setDouble(dragonType, result);
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Native sea dehydration write unavailable", failure); }
    }
}
