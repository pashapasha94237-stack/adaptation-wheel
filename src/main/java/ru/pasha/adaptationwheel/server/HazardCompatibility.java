package ru.pasha.adaptationwheel.server;

import java.lang.reflect.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;

/** Native non-potion hazards. Continuous exposure is not a new hit every render/server tick. */
public final class HazardCompatibility {
    public static final String ICE="condition:iceandfire:frozen",MOWZIE="effect:mowziesmobs:frozen",TORNADO="condition:weather2:tornado";
    public static final String SIREN="condition:iceandfire:siren_charm",ANOMALY="condition:anomaly_rephased:execution";
    private HazardCompatibility() {}
    /** Eternal Tales assumes every compatible dimension has at least two surface rules. */
    public static void insertSurfaceRule(java.util.List<Object> rules,int index,Object rule) {
        rules.add(Math.max(0,Math.min(index,rules.size())),rule);
    }
    private static void continuous(ServerPlayer p,String key) {
        AdaptationEvents.continuousExposure(p,key);
    }
    public static boolean sirenTick(Object data,LivingEntity holder) {
        if(!(holder instanceof ServerPlayer p)||!AdaptationEvents.active(p))return false;
        try {
            if(!data.getClass().getField("isCharmed").getBoolean(data)&&data.getClass().getField("charmedBy").get(data)==null)return false;
            continuous(p,SIREN);
            if(!immune(p,SIREN))return false;
            data.getClass().getMethod("clearCharm").invoke(data);return true;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Ice and Fire siren-state schema changed",failure);}
    }
    public static void sirenMotion(Entity entity,Vec3 requested) {
        if(entity instanceof ServerPlayer p&&AdaptationEvents.active(p))
            requested=entity.getDeltaMovement().lerp(requested,AdaptationEvents.state(p).damageMultiplier(SIREN));
        entity.setDeltaMovement(requested);
    }
    public static boolean anomalyEffect(LivingEntity victim,MobEffectInstance effect) {
        if(victim instanceof ServerPlayer p&&NegativeEffects.harmful(effect.getEffect())) {
            continuous(p,ANOMALY);
            if(immune(p,ANOMALY))return false;
        }
        return victim.addEffect(effect);
    }
    /** The two audited Anomaly jumpscare executions, never /kill or other removals. */
    public static void anomalyKill(Entity victim,Entity attacker) {
        if(victim instanceof ServerPlayer p&&AdaptationEvents.active(p)) {
            continuous(p,ANOMALY);
            if(immune(p,ANOMALY)||attacker instanceof LivingEntity living
                    &&AdaptationEvents.state(p).existenceAdapted(DamageCategories.forEntity(living)))return;
        }
        victim.kill();
    }
    public static boolean immune(Entity entity,String key) {
        return entity instanceof ServerPlayer p&&AdaptationEvents.active(p)&&AdaptationEvents.state(p).isImmune(key);
    }
    public static int freezeDuration(LivingEntity entity,int ticks) {
        if(!(entity instanceof ServerPlayer p)||ticks<=0||!AdaptationEvents.active(p))return ticks;
        AdaptationEvents.recordExposure(p,ICE,false);
        double factor=AdaptationEvents.state(p).damageMultiplier(ICE);
        return factor==0?Integer.MIN_VALUE:(int)Math.ceil(ticks*factor);
    }
    public static boolean freezeCanceled(int duration) { return duration==Integer.MIN_VALUE; }
    public static boolean iceTick(Object data,LivingEntity entity) {
        if(!(entity instanceof ServerPlayer p)||!AdaptationEvents.active(p))return false;
        try {
            if(!data.getClass().getField("isFrozen").getBoolean(data))return false;
            continuous(p,ICE);
            if(!immune(p,ICE))return false;
            Method clear=data.getClass().getDeclaredMethod("clearFrozen",LivingEntity.class);clear.setAccessible(true);clear.invoke(data,entity);
            return true;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Ice and Fire frozen-state schema changed",failure);}
    }
    public static float mowzieAmount(LivingEntity entity,float amount) {
        if(!(entity instanceof ServerPlayer p)||amount<=0||!AdaptationEvents.active(p))return amount;
        continuous(p,MOWZIE);
        return (float)(amount*AdaptationEvents.state(p).damageMultiplier(MOWZIE));
    }
    public static boolean mowzieFreeze(LivingEntity entity) { return immune(entity,MOWZIE); }
    public static boolean mowzieTick(Object data,LivingEntity entity) {
        if(!immune(entity,MOWZIE))return false;
        try{
            if(data.getClass().getField("frozen").getBoolean(data)||data.getClass().getField("freezeProgress").getFloat(data)>0)
                data.getClass().getMethod("onUnfreeze",LivingEntity.class).invoke(data,entity);
            return true;
        }
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Mowzie frozen-state schema changed",failure);}
    }
    public static void tornadoMotion(Entity entity,Vec3 requested) {
        if(entity instanceof ServerPlayer p&&AdaptationEvents.active(p)) {
            continuous(p,TORNADO);
            requested=entity.getDeltaMovement().lerp(requested,AdaptationEvents.state(p).damageMultiplier(TORNADO));
        }
        entity.setDeltaMovement(requested);
    }
    public static boolean tornadoPush(Object entity,float x,float y,float z) {
        if(!(entity instanceof ServerPlayer player))return false;
        tornadoMotion(player,player.getDeltaMovement().add(x,y,z));return true;
    }
    public static void tornadoMotion3(Entity entity,double x,double y,double z) { tornadoMotion(entity,new Vec3(x,y,z)); }
    public static boolean atmosphere(Entity entity,String family) {
        if(!immune(entity,family))return false;
        if(family.contains("oxygen"))entity.setAirSupply(entity.getMaxAirSupply());
        if(family.contains("burn"))entity.clearFire();
        if(family.contains("freeze"))entity.setTicksFrozen(0);
        return true;
    }
}
