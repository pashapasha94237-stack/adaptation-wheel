package ru.pasha.adaptationwheel.server;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.*;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import java.util.*;

public final class NegativeEffects {
    private static final Set<ServerPlayer> INTERNAL=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ServerPlayer,Set<MobEffect>> SHORTEN=new WeakHashMap<>();
    private static final Map<ServerPlayer,VisionLease> OWN_VISION=new WeakHashMap<>();
    private static final String VISION_SAVE="adaptationwheelVision";
    private static final java.lang.reflect.Field DURATION=ObfuscationReflectionHelper.findField(MobEffectInstance.class,"f_19503_");
    private static final java.lang.reflect.Field HIDDEN=ObfuscationReflectionHelper.findField(MobEffectInstance.class,"f_19510_");
    /** Vanilla must not shorten our infinite effect when a stronger potion is merged. */
    private static final class OwnedVision extends MobEffectInstance {
        OwnedVision() { super(MobEffects.NIGHT_VISION,INFINITE_DURATION,0,true,false,false); }
        @Override public boolean update(MobEffectInstance incoming) { return false; }
    }
    private static final class VisionLease {
        MobEffectInstance owned,external;
        int advancedAt;
        VisionLease(MobEffectInstance owned,MobEffectInstance external,int tick) {
            this.owned=owned;this.external=external;advancedAt=tick;
        }
        void advance(ServerPlayer p) {
            int elapsed=Math.max(0,p.tickCount-advancedAt);advancedAt=p.tickCount;
            // Night vision has no ticking gameplay action. Use vanilla's duration/hidden-effect
            // ticking so stronger potions and their weaker fallback expire at the correct time.
            for(int i=0;i<elapsed && external!=null;i++)
                if(!external.tick(p,() -> {})) external=null;
        }
        void remember(MobEffectInstance incoming) {
            MobEffectInstance copy=MobEffectInstance.load(incoming.save(new CompoundTag()));
            if(copy==null || !copy.isInfiniteDuration() && copy.getDuration()<=0) return;
            if(external==null) external=copy; else external.update(copy);
        }
    }
    private NegativeEffects() {}
    public static String key(MobEffect effect) { return "effect:"+BuiltInRegistries.MOB_EFFECT.getKey(effect); }
    public static boolean harmful(MobEffect effect) {
        return DamageCategories.isHarmful(effect);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void applicable(MobEffectEvent.Applicable event) {
        if(event.getEntity() instanceof ServerPlayer p && !INTERNAL.contains(p) && harmful(event.getEffectInstance().getEffect())
                && AdaptationEvents.active(p) && AdaptationEvents.state(p).isImmune(key(event.getEffectInstance().getEffect())))
            event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent
    public static void added(MobEffectEvent.Added event) {
        if(!(event.getEntity() instanceof ServerPlayer p) || INTERNAL.contains(p)) return;
        MobEffect effect=event.getEffectInstance().getEffect();
        if(effect==MobEffects.NIGHT_VISION) {
            VisionLease lease=visionLease(p);
            if(lease!=null && event.getOldEffectInstance()==lease.owned) {
                // Added runs before vanilla merges the instances. Keep the incoming potion
                // independently even when the visible infinite effect rejects that merge.
                lease.advance(p);lease.remember(event.getEffectInstance());saveVision(p,lease);
            }
        }
        if(harmful(effect) && effect!=MobEffects.HARM && AdaptationEvents.active(p)) {
            if(T2SCompatibility.effectAdded(p,event) || JujutsuCompatibility.effectAdded(p,event)) return;
            AdaptationEvents.recordExposure(p,key(effect),false);
            SHORTEN.computeIfAbsent(p,ignored -> new HashSet<>()).add(effect);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void removed(MobEffectEvent.Remove event) {
        if(event.getEntity() instanceof ServerPlayer p && !INTERNAL.contains(p)
                && event.getEffect()==MobEffects.NIGHT_VISION) {
            VisionLease lease=visionLease(p);
            if(lease!=null && event.getEffectInstance()==lease.owned) {
                // Milk or /effect clear must also clear a potion hidden underneath our vision.
                lease.external=null;saveVision(p,lease);
            }
        }
    }
    public static void onCompletions(ServerPlayer p,List<String> completed) {
        for(MobEffectInstance effect:new ArrayList<>(p.getActiveEffects()))
            if(completed.contains(key(effect.getEffect())))
                SHORTEN.computeIfAbsent(p,ignored -> new HashSet<>()).add(effect.getEffect());
    }
    public static void maintain(ServerPlayer p) {
        if(!p.isAlive()) { clear(p); return; }
        boolean active=AdaptationEvents.active(p);
        var state=AdaptationEvents.state(p);
        Set<MobEffect> shorten=SHORTEN.remove(p);
        for(MobEffectInstance instance:new ArrayList<>(p.getActiveEffects())) {
            MobEffect effect=instance.getEffect();
            if(!harmful(effect) || !active) continue;
            String key=key(effect);
            if(!state.categoryEnabled(key)) continue;
            // Loaded/pre-existing harmful effects are discovered without generating a bonus every tick.
            ensureStarted(p,key);
            if(state.isImmune(key)) { p.removeEffect(effect); continue; }
            if(shorten!=null && shorten.contains(effect) && instance.getDuration()>0) {
                double factor=state.damageMultiplier(key);
                if(factor<1) {
                    // Removing/readding can execute a mod's curse expiry, summon or
                    // initial attack again. Keep the original instance and attributes.
                    shortenDuration(instance,factor,Collections.newSetFromMap(new IdentityHashMap<>()));
                    p.serverLevel().getChunkSource().broadcastAndSend(p,new ClientboundUpdateMobEffectPacket(p.getId(),instance));
                }
            }
        }
        sustainHunger(p);
        if(active && state.isImmune(AdaptationProfile.COLD_KEY)) p.setTicksFrozen(0);
        maintainVision(p,active && state.nightVisionUnlocked() && state.categoryEnabled(AdaptationProfile.DARK_KEY) && state.nightVisionEnabled());
    }
    /** Loaded effects are removed before vanilla dispatches a mod's next harmful tick. */
    public static void beforeTick(ServerPlayer p){
        if(!p.isAlive()||!AdaptationEvents.active(p))return;
        var state=AdaptationEvents.state(p);
        for(var instance:new ArrayList<>(p.getActiveEffects()))
            if(harmful(instance.getEffect())&&state.isImmune(key(instance.getEffect())))p.removeEffect(instance.getEffect());
    }
    private static void ensureStarted(ServerPlayer p,String key) {
        AdaptationEvents.continuousExposure(p,key);
    }
    private static void shortenDuration(MobEffectInstance instance,double factor,Set<MobEffectInstance> visited) {
        if(instance==null||!visited.add(instance))return;
        try {
            if(!instance.isInfiniteDuration()&&instance.getDuration()>0)
                DURATION.setInt(instance,Math.max(1,(int)Math.ceil(instance.getDuration()*factor)));
            shortenDuration((MobEffectInstance)HIDDEN.get(instance),factor,visited);
        }catch(IllegalAccessException failure){throw new IllegalStateException("Cannot shorten native effect duration",failure);}
    }
    /** Keep the native dispatcher's duration precondition before observing a tick. */
    public static boolean skipNativeInstance(MobEffectInstance instance,LivingEntity entity) {
        return (instance.isInfiniteDuration()||instance.getDuration()>0)&&skipNativeAction(instance.getEffect(),entity);
    }
    /** Called at the entry to audited native effect tick/instant callbacks. */
    public static boolean skipNativeAction(MobEffect effect,LivingEntity entity) {
        if(!(entity instanceof ServerPlayer p)||!harmful(effect)||!AdaptationEvents.active(p))return false;
        String key=key(effect);ensureStarted(p,key);
        return AdaptationEvents.state(p).isImmune(key);
    }
    /** Only audited punitive expiry callbacks use this; normal attribute cleanup still runs. */
    public static boolean suppressPunitiveExpiry(MobEffect effect,LivingEntity entity) {
        return entity instanceof ServerPlayer p&&harmful(effect)&&AdaptationEvents.active(p)
                &&AdaptationEvents.state(p).isImmune(key(effect));
    }
    public static void sustainHunger(ServerPlayer p) {
        if(AdaptationEvents.active(p) && AdaptationEvents.state(p).isImmune(AdaptationProfile.HUNGER_KEY)) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(20);
            p.getFoodData().setExhaustion(0);
            p.removeEffect(MobEffects.HUNGER);
        }
    }
    public static void observeEnvironment(ServerPlayer p) {
        if(!AdaptationEvents.active(p) || !p.isAlive()) return;
        if(p.getFoodData().getFoodLevel()<=6) ensureStarted(p,AdaptationProfile.HUNGER_KEY);
        if(p.getTicksFrozen()>0) ensureStarted(p,AdaptationProfile.COLD_KEY);
        if(DarknessLight.isDark(p)) ensureStarted(p,AdaptationProfile.DARK_KEY);
    }
    private static void maintainVision(ServerPlayer p,boolean wanted) {
        VisionLease lease=visionLease(p);
        if(lease!=null) lease.advance(p);
        MobEffectInstance current=p.getEffect(MobEffects.NIGHT_VISION);
        if(!wanted) {
            if(lease==null) return;
            INTERNAL.add(p);
            try {
                // Never remove an effect that another source has replaced outright.
                if(current==lease.owned && !p.removeEffect(MobEffects.NIGHT_VISION)) {
                    saveVision(p,lease);return;
                }
                OWN_VISION.remove(p);p.getPersistentData().remove(VISION_SAVE);
                if(lease.external!=null && p.isAlive()) p.addEffect(lease.external);
            } finally { INTERNAL.remove(p); }
            return;
        }
        if(lease==null) {
            lease=new VisionLease(null,null,p.tickCount);
            if(current!=null) lease.remember(current);
        } else if(current!=null && current!=lease.owned) {
            lease.remember(current);
        }
        if(current==null || current!=lease.owned || !(current instanceof OwnedVision)) {
            INTERNAL.add(p);
            try {
                if(current!=null && !p.removeEffect(MobEffects.NIGHT_VISION)) return;
                OwnedVision vision=new OwnedVision();
                if(!p.addEffect(vision)) {
                    OWN_VISION.remove(p);p.getPersistentData().remove(VISION_SAVE);
                    if(lease.external!=null) p.addEffect(lease.external);
                    return;
                }
                lease.owned=vision;OWN_VISION.put(p,lease);
            } finally { INTERNAL.remove(p); }
        }
        saveVision(p,lease);
    }
    private static VisionLease visionLease(ServerPlayer p) {
        VisionLease lease=OWN_VISION.get(p);
        if(lease!=null || !p.getPersistentData().contains(VISION_SAVE)) return lease;
        MobEffectInstance current=p.getEffect(MobEffects.NIGHT_VISION);
        CompoundTag saved=p.getPersistentData().getCompound(VISION_SAVE);
        // Infinite vanilla effects are saved with the player. The separate marker keeps
        // ownership and a potion backup recoverable after autosave/restart, without
        // claiming a normal externally supplied night-vision effect as ours.
        if(current!=null && current.isInfiniteDuration() && current.getAmplifier()==0
                && current.isAmbient() && !current.isVisible() && !current.showIcon()) {
            MobEffectInstance external=saved.contains("External")
                    ? MobEffectInstance.load(saved.getCompound("External")) : null;
            lease=new VisionLease(current,external,p.tickCount);OWN_VISION.put(p,lease);
        } else p.getPersistentData().remove(VISION_SAVE);
        return lease;
    }
    private static void saveVision(ServerPlayer p,VisionLease lease) {
        CompoundTag saved=new CompoundTag();
        if(lease.external!=null) saved.put("External",lease.external.save(new CompoundTag()));
        p.getPersistentData().put(VISION_SAVE,saved);
    }
    public static void clear(ServerPlayer p) {
        maintainVision(p,false);
        SHORTEN.remove(p); INTERNAL.remove(p);
    }
    public static void clearAll() { SHORTEN.clear(); OWN_VISION.clear(); INTERNAL.clear(); }
}

