package ru.pasha.adaptationwheel.server;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.*;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.eventbus.api.*;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import java.util.*;

public final class NegativeEffects {
    private static final Set<ServerPlayerEntity> INTERNAL=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ServerPlayerEntity,Set<Effect>> SHORTEN=new WeakHashMap<>();
    private static final Map<ServerPlayerEntity,VisionLease> OWN_VISION=new WeakHashMap<>();
    private static final String VISION_SAVE="adaptationwheelVision";
    /** Vanilla must not shorten our infinite effect when a stronger potion is merged. */
    private static final class OwnedVision extends EffectInstance {
        OwnedVision() { super(Effects.NIGHT_VISION,Integer.MAX_VALUE,0,true,false,false); }
        @Override public boolean tick(net.minecraft.entity.LivingEntity entity,Runnable callback){return true;}
        @Override public boolean update(EffectInstance incoming) { return false; }
    }
    private static final class VisionLease {
        EffectInstance owned,external;
        int advancedAt;
        VisionLease(EffectInstance owned,EffectInstance external,int tick) {
            this.owned=owned;this.external=external;advancedAt=tick;
        }
        void advance(ServerPlayerEntity p) {
            int elapsed=Math.max(0,p.tickCount-advancedAt);advancedAt=p.tickCount;
            // Night vision has no ticking gameplay action. Use vanilla's duration/hidden-effect
            // ticking so stronger potions and their weaker fallback expire at the correct time.
            for(int i=0;i<elapsed && external!=null;i++)
                if(!external.tick(p,() -> {})) external=null;
        }
        void remember(EffectInstance incoming) {
            EffectInstance copy=EffectInstance.load(incoming.save(new CompoundNBT()));
            if(copy==null || copy.getDuration()!=Integer.MAX_VALUE && copy.getDuration()<=0) return;
            if(external==null) external=copy; else external.update(copy);
        }
    }
    private NegativeEffects() {}
    public static String key(Effect effect) { return "effect:"+ForgeRegistries.POTIONS.getKey(effect); }
    public static boolean harmful(Effect effect) {
        return effect.getCategory()==EffectType.HARMFUL || effect==Effects.BAD_OMEN || effect==Effects.GLOWING;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void applicable(PotionEvent.PotionApplicableEvent event) {
        if(!(event.getEntityLiving() instanceof ServerPlayerEntity))return;ServerPlayerEntity p=(ServerPlayerEntity)event.getEntityLiving();
        if(event.getEntityLiving() instanceof ServerPlayerEntity && !INTERNAL.contains(p) && harmful(event.getPotionEffect().getEffect())
                && AdaptationEvents.active(p) && AdaptationEvents.state(p).isImmune(key(event.getPotionEffect().getEffect())))
            event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent
    public static void added(PotionEvent.PotionAddedEvent event) {
        if(!(event.getEntityLiving() instanceof ServerPlayerEntity))return;ServerPlayerEntity p=(ServerPlayerEntity)event.getEntityLiving();if(INTERNAL.contains(p))return;
        Effect effect=event.getPotionEffect().getEffect();
        if(effect==Effects.NIGHT_VISION) {
            VisionLease lease=visionLease(p);
            if(lease!=null && event.getOldPotionEffect()==lease.owned) {
                // Added runs before vanilla merges the instances. Keep the incoming potion
                // independently even when the visible infinite effect rejects that merge.
                lease.advance(p);lease.remember(event.getPotionEffect());saveVision(p,lease);
            }
        }
        if(harmful(effect) && effect!=Effects.HARM && AdaptationEvents.active(p)) {
            AdaptationEvents.recordEffect(p,effect);
            SHORTEN.computeIfAbsent(p,ignored -> new HashSet<>()).add(effect);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void removed(PotionEvent.PotionRemoveEvent event) {
        if(!(event.getEntityLiving() instanceof ServerPlayerEntity))return;ServerPlayerEntity p=(ServerPlayerEntity)event.getEntityLiving();
        if(event.getEntityLiving() instanceof ServerPlayerEntity && !INTERNAL.contains(p)
                && event.getPotion()==Effects.NIGHT_VISION) {
            VisionLease lease=visionLease(p);
            if(lease!=null && event.getPotionEffect()==lease.owned) {
                // Milk or /effect clear must also clear a potion hidden underneath our vision.
                lease.external=null;saveVision(p,lease);
            }
        }
    }
    public static void completed(ServerPlayerEntity p,List<String> completed) {
        for(EffectInstance effect:new ArrayList<>(p.getActiveEffects()))
            if(completed.contains(key(effect.getEffect())))
                SHORTEN.computeIfAbsent(p,ignored -> new HashSet<>()).add(effect.getEffect());
    }
    public static void maintain(ServerPlayerEntity p) {
        if(!p.isAlive()) { clear(p); return; }
        boolean active=AdaptationEvents.active(p);
        ru.pasha.adaptationwheel.core.AdaptationState state=AdaptationEvents.state(p);
        Set<Effect> shorten=SHORTEN.remove(p);
        for(EffectInstance instance:new ArrayList<>(p.getActiveEffects())) {
            Effect effect=instance.getEffect();
            if(!harmful(effect) || !active) continue;
            String key=key(effect);
            if(!state.categoryEnabled(key)) continue;
            // Loaded/pre-existing harmful effects are discovered without generating a bonus every tick.
            ensureStarted(p,key);
            if(state.isImmune(key)) { p.removeEffect(effect); continue; }
            if(shorten!=null && shorten.contains(effect) && instance.getDuration()>0) {
                double factor=state.damageMultiplier(key);
                if(factor<1) {
                    CompoundNBT tag=instance.save(new CompoundNBT());
                    tag.putInt("Duration",Math.max(1,(int)Math.ceil(instance.getDuration()*factor)));
                    EffectInstance reduced=EffectInstance.load(tag);
                    if(reduced!=null) {
                        INTERNAL.add(p);
                        try { p.removeEffect(effect); p.addEffect(reduced); }
                        finally { INTERNAL.remove(p); }
                    }
                }
            }
        }
        sustainHunger(p);
        maintainVision(p,active && state.isImmune(AdaptationProfile.DARK_KEY) && state.nightVisionEnabled());
    }
    private static void ensureStarted(ServerPlayerEntity p,String key) {
        ru.pasha.adaptationwheel.core.AdaptationState state=AdaptationEvents.state(p);
        if(state.snapshots().stream().noneMatch(e -> e.key().equals(key) && e.running()))
            state.hit(key,false);
    }
    public static void sustainHunger(ServerPlayerEntity p) {
        if(AdaptationEvents.active(p) && AdaptationEvents.state(p).isImmune(AdaptationProfile.HUNGER_KEY)) {
            CompoundNBT nutrition=new CompoundNBT();p.getFoodData().addAdditionalSaveData(nutrition);
            nutrition.putInt("foodLevel",20);nutrition.putFloat("foodSaturationLevel",20);nutrition.putFloat("foodExhaustionLevel",0);
            p.getFoodData().readAdditionalSaveData(nutrition);
            p.removeEffect(Effects.HUNGER);
        }
    }
    public static void observe(ServerPlayerEntity p) {
        if(!AdaptationEvents.active(p) || !p.isAlive()) return;
        if(p.getFoodData().getFoodLevel()<=6) ensureStarted(p,AdaptationProfile.HUNGER_KEY);
        if(DarknessLight.isDark(p)) ensureStarted(p,AdaptationProfile.DARK_KEY);
    }
    private static void maintainVision(ServerPlayerEntity p,boolean wanted) {
        VisionLease lease=visionLease(p);
        if(lease!=null) lease.advance(p);
        EffectInstance current=p.getEffect(Effects.NIGHT_VISION);
        if(!wanted) {
            if(lease==null) return;
            INTERNAL.add(p);
            try {
                // Never remove an effect that another source has replaced outright.
                if(current==lease.owned && !p.removeEffect(Effects.NIGHT_VISION)) {
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
                if(current!=null && !p.removeEffect(Effects.NIGHT_VISION)) return;
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
        // 1.16.5 caps vanilla client effect packets at 32767 ticks. Renew the packet,
        // not the owned server effect, well before the client's flicker window.
        if(p.tickCount%200==0 && p.connection!=null && !(p instanceof net.minecraftforge.common.util.FakePlayer))
            p.connection.send(new net.minecraft.network.play.server.SPlayEntityEffectPacket(p.getId(),lease.owned));
    }
    private static VisionLease visionLease(ServerPlayerEntity p) {
        VisionLease lease=OWN_VISION.get(p);
        if(lease!=null || !p.getPersistentData().contains(VISION_SAVE)) return lease;
        EffectInstance current=p.getEffect(Effects.NIGHT_VISION);
        CompoundNBT saved=p.getPersistentData().getCompound(VISION_SAVE);
        // Infinite vanilla effects are saved with the player. The separate marker keeps
        // ownership and a potion backup recoverable after autosave/restart, without
        // claiming a normal externally supplied night-vision effect as ours.
        if(current!=null && current.getDuration()>Integer.MAX_VALUE-2000 && current.getAmplifier()==0
                && current.isAmbient() && !current.isVisible() && !current.showIcon()) {
            EffectInstance external=saved.contains("External")
                    ? EffectInstance.load(saved.getCompound("External")) : null;
            lease=new VisionLease(current,external,p.tickCount);OWN_VISION.put(p,lease);
        } else p.getPersistentData().remove(VISION_SAVE);
        return lease;
    }
    private static void saveVision(ServerPlayerEntity p,VisionLease lease) {
        CompoundNBT saved=new CompoundNBT();
        if(lease.external!=null) saved.put("External",lease.external.save(new CompoundNBT()));
        p.getPersistentData().put(VISION_SAVE,saved);
    }
    public static void clear(ServerPlayerEntity p) {
        maintainVision(p,false);
        SHORTEN.remove(p); INTERNAL.remove(p);
    }
    public static void clearAll() { SHORTEN.clear(); OWN_VISION.clear(); INTERNAL.clear(); }
}
