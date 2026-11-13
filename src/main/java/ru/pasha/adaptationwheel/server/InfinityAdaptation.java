package ru.pasha.adaptationwheel.server;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Personal, attack-scoped bypass of Jujutsu Craft's Infinity. Never removes the owner's effect. */
public final class InfinityAdaptation {
    public static final String KEY="condition:jujutsucraft:technique/205";
    private static final ResourceLocation INFINITY=new ResourceLocation("jujutsucraft","infinity_effect");
    private static final ResourceLocation NEUTRALIZATION=new ResourceLocation("jujutsucraft","neutralization");
    private static final TagKey<EntityType<?>> RANGED=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation("forge","ranged_ammo"));
    private static final Map<ServerPlayer,Integer> LAST_IMPACT=new WeakHashMap<>();
    private static final Map<Entity,Set<UUID>> STOPPED_PROJECTILES=new WeakHashMap<>();
    private InfinityAdaptation() {}
    private static boolean isInfinity(MobEffect effect){return INFINITY.equals(BuiltInRegistries.MOB_EFFECT.getKey(effect));}
    public static boolean barrierActive(LivingEntity target){
        MobEffect infinity=BuiltInRegistries.MOB_EFFECT.getOptional(INFINITY).orElse(null);
        MobEffect neutralization=BuiltInRegistries.MOB_EFFECT.getOptional(NEUTRALIZATION).orElse(null);
        return infinity!=null&&target.isAlive()&&target.hasEffect(infinity)
                &&(neutralization==null||!target.hasEffect(neutralization));
    }
    private static ServerPlayer playerOwner(Entity attack){
        Entity current=attack;Set<UUID> seen=new HashSet<>();
        for(int n=0;current!=null&&n<6&&seen.add(current.getUUID());n++){
            if(current instanceof ServerPlayer player)return player;
            Entity next=current instanceof TraceableEntity trace?trace.getOwner():null;
            if(next==null)next=JujutsuCompatibility.owner(current);
            if(next==current)break;current=next;
        }
        return null;
    }
    public static boolean canBypass(Entity attack){
        ServerPlayer player=playerOwner(attack);
        if(player==null||!player.isAlive()||!AdaptationEvents.active(player))return false;
        if(WorldSlash.bypassing(player))return true;
        var state=AdaptationEvents.state(player);
        // This interaction unlocks after the completed fourth stage, like night vision.
        return state.categoryEnabled(KEY)&&state.stage(KEY)>=4;
    }
    private static void encounter(ServerPlayer player){
        if(player==null||!player.isAlive()||!AdaptationEvents.active(player)
                ||!AdaptationEvents.state(player).categoryEnabled(KEY)||AdaptationEvents.state(player).stage(KEY)>=4)return;
        Integer previous=LAST_IMPACT.put(player,player.tickCount);
        // A swing may traverse both AttackEntityEvent and recursive JJC damage callbacks.
        if(previous==null||previous!=player.tickCount)AdaptationEvents.recordExposure(player,KEY,false);
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void attack(AttackEntityEvent event){
        if(event.getEntity() instanceof ServerPlayer player&&event.getTarget() instanceof LivingEntity target
                &&target!=player&&barrierActive(target))encounter(player);
    }
    /** Original attack handler's query, scoped to the actual damage source. */
    public static boolean hasEffectForAttack(LivingEntity target,MobEffect effect,DamageSource source){
        Entity attack=source==null?null:source.getEntity();
        if(attack==null&&source!=null)attack=source.getDirectEntity();
        return hasEffectForCaster(target,effect,attack);
    }
    /** JJC pre-hit checks must see the same personal bypass as the final damage callback. */
    public static boolean hasEffectForCaster(LivingEntity target,MobEffect effect,Entity attack){
        boolean original=target.hasEffect(effect);
        if(!original||!isInfinity(effect)||target.level().isClientSide||!barrierActive(target))return original;
        ServerPlayer owner=playerOwner(attack);
        if(owner==null||owner==target)return original;
        encounter(owner);
        return !canBypass(owner);
    }
    /** Exclude only adapted players' projectiles from the original field's stopping loop. */
    public static <T extends Entity> List<T> candidates(LevelAccessor world,Class<T> type,AABB bounds,Predicate<? super T> predicate,Entity barrier){
        List<T> original=world.getEntitiesOfClass(type,bounds,predicate);
        if(world.isClientSide()||!(barrier instanceof LivingEntity target)||!barrierActive(target))return original;
        List<T> result=new ArrayList<>(original.size());
        for(T entity:original){
            boolean ranged=entity instanceof Projectile||entity.getType().is(RANGED)||entity.getPersistentData().getDouble("NameRanged_ranged")!=0;
            ServerPlayer owner=ranged?playerOwner(entity):null;
            if(owner==null||owner==barrier){result.add(entity);continue;}
            if(canBypass(owner))continue;
            // An arrow already held in the field is not a new hit on each refresh.
            double diameter=8+Math.max(barrier.getBbWidth()*2,barrier.getBbHeight()*2)*2;
            Vec3 toward=barrier.position().add(0,barrier.getBbHeight()*0.5,0)
                    .subtract(entity.position().add(0,entity.getBbHeight()*0.5,0));
            if(toward.length()<diameter*0.25&&toward.dot(entity.getDeltaMovement())>0
                    &&STOPPED_PROJECTILES.computeIfAbsent(entity,ignored->new HashSet<>()).add(barrier.getUUID()))encounter(owner);
            result.add(entity);
        }
        return result;
    }
}
