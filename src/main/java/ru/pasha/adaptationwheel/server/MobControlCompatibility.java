package ru.pasha.adaptationwheel.server;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.net.CatalogueEntry;

/** Audited hostile controls only; ordinary mounts and voluntary movement stay native. */
public final class MobControlCompatibility {
    public static final String ANACONDA="condition:alexsmobs:constriction";
    public static final String FORSAKEN="condition:alexscaves:forsaken_hold";
    public static final String GORGON="attack:iceandfire:entity_gorgon/m_8107__bfde7ffb";
    public static final String KRAMPUS="condition:born_in_chaos_v1:krampus_disarm";
    private static final String HYDRA_ATTACK="attack:twilightforest:hydra_head_container/execute_attacks_a81c18b2";
    private static final Set<String> CAPTORS=Set.of("alexsmobs:crocodile","twilightforest:pinch_beetle","twilightforest:yeti","twilightforest:alpha_yeti");
    private MobControlCompatibility() {}
    /** AoA's charge pushes even when the preceding melee attack returned false. */
    public static void aoaChargePush(Entity attacker,Entity victim,float strength){
        if(attacker instanceof LivingEntity living&&victim instanceof ServerPlayer p&&AdaptationEvents.active(p)){
            String key=DamageCategories.forEntity(living);AdaptationEvents.continuousExposure(p,key);
            if(AdaptationEvents.state(p).isImmune(key))return;
            strength*=AdaptationEvents.state(p).damageMultiplier(key);
        }
        invokeStatic("net.tslat.aoa3.util.EntityUtil","pushEntityAway",new Class<?>[]{Entity.class,Entity.class,float.class},attacker,victim,strength);
    }
    public static void guardianPush(Entity victim,double x,double y,double z,Entity guardian){
        if(victim instanceof ServerPlayer p&&AdaptationEvents.active(p)){
            String key="attack:draconicevolution:draconic_guardian_entity/collide_with_entities_5d2110e5";
            AdaptationEvents.continuousExposure(p,key);
            if(protectedFrom(p,guardian,key))return;
            double multiplier=AdaptationEvents.state(p).damageMultiplier(key);x*=multiplier;y*=multiplier;z*=multiplier;
        }
        victim.push(x,y,z);
    }
    /** Bite shield wear, forced release and client throw packets precede hurt(). */
    public static List<Entity> hydraBiteTargets(net.minecraft.world.level.Level level,Entity head,net.minecraft.world.phys.AABB area){
        List<Entity> targets=level.getEntities(head,area);
        Entity owner=head instanceof net.minecraftforge.entity.PartEntity<?> part?part.getParent():null;
        if(owner==null)return targets;
        return targets.stream().filter(target->{
            if(!(target instanceof ServerPlayer p)||!AdaptationEvents.active(p))return true;
            AdaptationEvents.continuousExposure(p,HYDRA_ATTACK);
            return !protectedFrom(p,owner,HYDRA_ATTACK);
        }).toList();
    }
    private static String id(Entity entity){return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    public static String captureKey(Entity captor){return "condition:"+id(captor).replace(":",":capture/");}
    private static boolean protectedFrom(ServerPlayer p,Entity attacker,String key){
        return AdaptationEvents.active(p)&&(AdaptationEvents.state(p).isImmune(key)
                ||attacker instanceof LivingEntity living&&AdaptationEvents.state(p).existenceAdapted(DamageCategories.forEntity(living)));
    }
    public static boolean capture(Entity victim,Entity captor,boolean force){
        if(victim instanceof ServerPlayer p&&CAPTORS.contains(id(captor))&&AdaptationEvents.active(p)){
            String key=captureKey(captor);AdaptationEvents.continuousExposure(p,key);
            if(protectedFrom(p,captor,key))return false;
        }
        return victim.startRiding(captor,force);
    }
    public static void captureDismount(Entity victim,Object owner){
        Entity captor=owner instanceof Entity entity?entity:null;
        if(captor==null)try{captor=(Entity)net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.world.entity.ai.goal.MeleeAttackGoal.class,"f_25540_").get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException("Native capture goal changed",e);}
        if(victim instanceof ServerPlayer p&&captor!=null&&CAPTORS.contains(id(captor))&&protectedFrom(p,captor,captureKey(captor)))return;
        victim.stopRiding();
    }
    /** Native disarming procedure is separate from the attack's ordinary HP loss. */
    public static boolean krampus(Entity victim,Entity attacker){
        if(!(victim instanceof ServerPlayer p)||attacker==null||!id(attacker).equals("born_in_chaos_v1:krampus")||!AdaptationEvents.active(p))return false;
        AdaptationEvents.continuousExposure(p,KRAMPUS);return protectedFrom(p,attacker,KRAMPUS);
    }
    public static void maintain(ServerPlayer p){
        if(!AdaptationEvents.active(p))return;
        Entity vehicle=p.getVehicle();if(vehicle==null||!CAPTORS.contains(id(vehicle)))return;
        String key=captureKey(vehicle);AdaptationEvents.continuousExposure(p,key);
        if(!protectedFrom(p,vehicle,key))return;
        if(id(vehicle).startsWith("twilightforest:"))invokeStatic("twilightforest.events.HostileMountEvents","hostileDismount",new Class<?>[]{Entity.class},p);
        else p.stopRiding();
    }
    public static boolean strangling(Entity snake,boolean requested){
        if(!requested||!(snake instanceof Mob mob)||!(mob.getTarget() instanceof ServerPlayer p)||!AdaptationEvents.active(p))return requested;
        AdaptationEvents.continuousExposure(p,ANACONDA);return !protectedFrom(p,snake,ANACONDA);
    }
    public static void snakeTick(Entity snake){
        if(!(snake instanceof Mob mob)||!(mob.getTarget() instanceof ServerPlayer p)||!AdaptationEvents.active(p))return;
        if(!(boolean)invoke(snake,"isStrangling",new Class<?>[]{}))return;
        AdaptationEvents.continuousExposure(p,ANACONDA);
        if(protectedFrom(p,snake,ANACONDA))invoke(snake,"setStrangling",new Class<?>[]{boolean.class},false);
    }
    public static int heldId(Entity captor,int requested){
        Entity victim=requested<0?null:captor.level().getEntity(requested);
        if(victim instanceof ServerPlayer p&&AdaptationEvents.active(p)){
            AdaptationEvents.continuousExposure(p,FORSAKEN);if(protectedFrom(p,captor,FORSAKEN))return -1;
        }
        return requested;
    }
    public static void forsakenTick(Entity captor){
        Entity victim=(Entity)invoke(captor,"getHeldMob",new Class<?>[]{});
        if(!(victim instanceof ServerPlayer p)||!AdaptationEvents.active(p))return;
        AdaptationEvents.continuousExposure(p,FORSAKEN);
        if(protectedFrom(p,captor,FORSAKEN))invoke(captor,"setHeldMobId",new Class<?>[]{int.class},-1);
    }
    public static void snakeMotion(Entity victim,Vec3 requested){motion(victim,requested,ANACONDA);}
    public static void forsakenMotion(Entity victim,Vec3 requested){motion(victim,requested,FORSAKEN);}
    public static void waveKnockback(LivingEntity victim,double strength,double x,double z,Entity wave){
        if(victim instanceof ServerPlayer p&&AdaptationEvents.active(p)){
            String key="attack:alexscaves:projectile/wave";AdaptationEvents.continuousExposure(p,key);
            if(ModAttackCompatibility.skipProjectileEffect(wave,p))return;
            strength*=AdaptationEvents.state(p).damageMultiplier(key);
        }
        victim.knockback(strength,x,z);
    }
    private static void motion(Entity victim,Vec3 requested,String key){
        if(victim instanceof ServerPlayer p&&AdaptationEvents.active(p))requested=p.getDeltaMovement().lerp(requested,AdaptationEvents.state(p).damageMultiplier(key));
        victim.setDeltaMovement(requested);
    }
    /** Called only at the Gorgon's own gaze checks, before a statue is created. */
    public static boolean gorgonLooking(LivingEntity looker,LivingEntity seen,double degree){
        String owner="com.github.alexthe666.iceandfire.entity.EntityGorgon";
        Class<?>[] signature={LivingEntity.class,LivingEntity.class,double.class};
        boolean looking=(boolean)invokeStatic(owner,"isEntityLookingAt",signature,looker,seen,degree);
        if(!looking)return false;
        LivingEntity gorgon=id(looker).equals("iceandfire:gorgon")?looker:id(seen).equals("iceandfire:gorgon")?seen:null;
        LivingEntity victim=gorgon==looker?seen:looker;
        if(gorgon==null||!(victim instanceof ServerPlayer p)||!AdaptationEvents.active(p))return looking;
        if((boolean)invokeStatic(owner,"isBlindfolded",new Class<?>[]{LivingEntity.class},p)||gorgon.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS))return looking;
        if(!(boolean)invokeStatic(owner,"isEntityLookingAt",signature,seen,looker,degree))return looking;
        AdaptationEvents.continuousExposure(p,GORGON);return !protectedFrom(p,gorgon,GORGON);
    }
    private static final Map<String,Method> METHODS=new java.util.concurrent.ConcurrentHashMap<>();
    private static Method method(Class<?> owner,String name,Class<?>[] types){
        return METHODS.computeIfAbsent(owner.getName()+"#"+name+Arrays.toString(types),ignored->{try{return owner.getMethod(name,types);}catch(ReflectiveOperationException e){throw new IllegalStateException("Native control schema changed: "+owner.getName()+"."+name,e);}});
    }
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args){try{return method(target.getClass(),name,types).invoke(target,args);}catch(ReflectiveOperationException e){throw new IllegalStateException("Native control failed: "+name,e);}}
    private static Object invokeStatic(String owner,String name,Class<?>[] types,Object... args){try{return method(Class.forName(owner),name,types).invoke(null,args);}catch(ReflectiveOperationException e){throw new IllegalStateException("Native control failed: "+owner+"."+name,e);}}
    public static List<CatalogueEntry> catalogue(){
        List<CatalogueEntry> result=new ArrayList<>();
        for(String type:CAPTORS){var id=new ResourceLocation(type);if(ModList.get().isLoaded(id.getNamespace())&&BuiltInRegistries.ENTITY_TYPE.containsKey(id)){
            String key="condition:"+type.replace(":",":capture/");result.add(new CatalogueEntry(key,translationKey(key),type,AdaptationProfile.PHYSICAL));
        }}
        if(ModList.get().isLoaded("alexsmobs"))result.add(new CatalogueEntry(ANACONDA,translationKey(ANACONDA),"Anaconda constriction",AdaptationProfile.PHYSICAL));
        if(ModList.get().isLoaded("alexscaves"))result.add(new CatalogueEntry(FORSAKEN,translationKey(FORSAKEN),"Forsaken grip",AdaptationProfile.PHYSICAL));
        if(ModList.get().isLoaded("born_in_chaos_v1"))result.add(new CatalogueEntry(KRAMPUS,translationKey(KRAMPUS),"Krampus disarming strike",AdaptationProfile.PHYSICAL));
        return result;
    }
    public static String translationKey(String key){return key!=null&&(key.equals(ANACONDA)||key.equals(FORSAKEN)||key.equals(KRAMPUS)||key.startsWith("condition:")&&key.contains(":capture/"))?"control.adaptationwheel."+key.substring(10).replace(':','.').replace('/','.') : "";}
}
