package ru.pasha.adaptationwheel.server;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import ru.pasha.adaptationwheel.MetaConfig;
import ru.pasha.adaptationwheel.core.*;

/** Tracks only player shots; steering preserves each mod's own collision and damage path. */
public final class GuidedShots {
    public static final String KEY=MetaMechanic.MISSES.key;
    private static final int LEVEL_LIMIT=512,PLAYER_LIMIT=128,LIFETIME=400;
    private static final Map<ServerLevel,Map<UUID,Shot>> SHOTS=new IdentityHashMap<>();
    private static final java.lang.reflect.Field IN_GROUND=ObfuscationReflectionHelper.findField(AbstractArrow.class,"f_36703_");
    private static final ClassValue<Optional<Method>> OWNERS=new ClassValue<>(){
        protected Optional<Method> computeValue(Class<?> type){for(String name:List.of("getShooter","getOwner"))try{return Optional.of(type.getMethod(name));}catch(NoSuchMethodException ignored){}return Optional.empty();}
    };
    private static final class Shot {
        final Entity entity;final ServerPlayer owner;final long born;Vec3 previous;UUID target;boolean hit,missed;long retry;
        Shot(Entity entity,ServerPlayer owner){this.entity=entity;this.owner=owner;born=owner.serverLevel().getGameTime();previous=entity.position();}
    }
    private GuidedShots(){}
    public static void registerGunEvents(){
        registerGunEvent("cgm","com.mrcrayfish.guns.event.GunProjectileHitEvent");
        registerGunEvent("scguns","top.ribs.scguns.event.GunProjectileHitEvent");
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void registerGunEvent(String mod,String name){
        if(!ModList.get().isLoaded(mod))return;
        try{
            Class type=Class.forName(name);Method projectile=type.getMethod("getProjectile"),result=type.getMethod("getRayTrace");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,type,(java.util.function.Consumer<Event>)event->{
                try{if(result.invoke(event) instanceof EntityHitResult)hit((Entity)projectile.invoke(event));}catch(ReflectiveOperationException ignored){}
            });
        }catch(ReflectiveOperationException|LinkageError error){com.mojang.logging.LogUtils.getLogger().warn("Optional gun hit observation unavailable: {}",mod,error);}
    }
    public static boolean supports(Entity entity){
        if(entity instanceof AbstractArrow)return !(entity instanceof ThrownTrident);
        var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());if(id==null||id.getNamespace().equals("minecraft"))return false;
        String path=id.getPath().toLowerCase(Locale.ROOT);
        if(path.contains("grenade")||path.contains("rocket")||path.contains("missile")||path.contains("turret"))return false;
        return path.contains("bullet")||path.contains("round")||path.contains("slug")||path.contains("bolt")
                ||(Set.of("cgm","scguns").contains(id.getNamespace())&&path.contains("projectile"));
    }
    private static ServerPlayer owner(Entity entity){
        if(entity instanceof Projectile p)return p.getOwner() instanceof ServerPlayer player?player:null;
        try{var getter=OWNERS.get(entity.getClass());return getter.isPresent()&&getter.get().invoke(entity) instanceof ServerPlayer player?player:null;}
        catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel level&&supports(event.getEntity())){
            Entity entity=event.getEntity();ServerPlayer owner=owner(entity);
            if(owner==null||!MetaServer.active(owner,MetaMechanic.MISSES))return;
            var shots=SHOTS.computeIfAbsent(level,ignored->new LinkedHashMap<>());
            if(shots.size()>=LEVEL_LIMIT||shots.values().stream().filter(s->s.owner==owner).count()>=PLAYER_LIMIT)return;
            var shot=new Shot(entity,owner);shots.put(entity.getUUID(),shot);
            if(MetaServer.full(owner,MetaMechanic.MISSES))guide(shot);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void impact(ProjectileImpactEvent e){if(e.getRayTraceResult() instanceof EntityHitResult)hit(e.getProjectile());}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void damaged(LivingAttackEvent e){Entity direct=e.getSource().getDirectEntity();if(direct!=null)hit(direct);}
    private static void hit(Entity entity){if(entity.level() instanceof ServerLevel level){var shots=SHOTS.get(level);if(shots!=null){var shot=shots.get(entity.getUUID());if(shot!=null)shot.hit=true;}}}
    private static boolean embedded(Entity entity){
        if(!(entity instanceof AbstractArrow))return false;
        try{return IN_GROUND.getBoolean(entity);}catch(IllegalAccessException ignored){return true;}
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(!(event.level instanceof ServerLevel level))return;var shots=SHOTS.get(level);if(shots==null)return;
        for(var iterator=shots.values().iterator();iterator.hasNext();){Shot shot=iterator.next();
            if(!shot.owner.isAlive()||shot.owner.level()!=level||level.getGameTime()<shot.born||level.getGameTime()-shot.born>LIFETIME||!MetaServer.active(shot.owner,MetaMechanic.MISSES)){iterator.remove();continue;}
            if(event.phase==TickEvent.Phase.START){
                if(!shot.entity.isRemoved()&&!embedded(shot.entity)&&MetaServer.full(shot.owner,MetaMechanic.MISSES))guide(shot);
            }else{
                if(!shot.hit&&!shot.missed&&!MetaServer.full(shot.owner,MetaMechanic.MISSES))observeMiss(shot);
                shot.previous=shot.entity.position();
                if(shot.hit||shot.entity.isRemoved()||embedded(shot.entity))iterator.remove();
            }
        }
        if(shots.isEmpty())SHOTS.remove(level);
    }
    private static boolean targetable(ServerPlayer owner,LivingEntity target){
        if(target==owner||!target.isAlive()||!target.isAttackable()||target instanceof ArmorStand||target.isSpectator()||target.isInvulnerable()||owner.isAlliedTo(target))return false;
        if(target instanceof TamableAnimal tame&&tame.isTame())return false;
        if(target instanceof Player player)return !player.isCreative()&&owner.canHarmPlayer(player)&&owner.server.isPvpAllowed();
        return true;
    }
    private static GuidedAim.Vector vector(Vec3 v){return new GuidedAim.Vector(v.x,v.y,v.z);}
    private static void observeMiss(Shot shot){
        Vec3 from=shot.previous,to=shot.entity.position();double travel=from.distanceToSqr(to);
        if(!Double.isFinite(travel)||travel<1e-8||travel>256*256)return;
        double radius=MetaConfig.NEAR_MISS_RADIUS.get();AABB swept=new AABB(from,to).inflate(radius);
        int checked=0;
        for(var target:shot.owner.serverLevel().getEntitiesOfClass(LivingEntity.class,swept,e->targetable(shot.owner,e))){
            if(++checked>128)break;
            AABB box=target.getBoundingBox();double distance=GuidedAim.distanceSquared(vector(from),vector(to),new GuidedAim.Vector(box.minX,box.minY,box.minZ),new GuidedAim.Vector(box.maxX,box.maxY,box.maxZ));
            if(distance<=1e-8||distance>radius*radius)continue;
            // Do not learn from a projectile still approaching a genuine future hit.
            if(!shot.entity.isRemoved()&&!embedded(shot.entity)&&to.subtract(box.getCenter()).dot(to.subtract(from))<=0)continue;
            if(!shot.owner.hasLineOfSight(target))continue;
            shot.missed=true;MetaServer.expose(shot.owner,MetaMechanic.MISSES);return;
        }
    }
    private static LivingEntity choose(Shot shot){
        double range=MetaConfig.HOMING_RANGE.get();
        var candidates=new ArrayList<Candidate>(16);
        Vec3 origin=shot.owner.getEyePosition(),look=shot.owner.getLookAngle();
        for(var target:shot.owner.serverLevel().getEntitiesOfClass(LivingEntity.class,shot.owner.getBoundingBox().inflate(range),e->targetable(shot.owner,e))){
            Vec3 delta=target.getBoundingBox().getCenter().subtract(origin);double distance=delta.length();if(distance>range)continue;
            double dot=distance<1e-8?1:look.dot(delta)/distance;
            boolean aimed=dot>.7;
            boolean enemy=target instanceof net.minecraft.world.entity.monster.Enemy||target instanceof Mob mob&&mob.getTarget()==shot.owner||shot.owner.getLastHurtMob()==target||shot.owner.getLastHurtByMob()==target;
            if(!aimed&&!enemy)continue;
            double score=aimed?(1-dot)*32+distance*.01:100+distance;
            if(candidates.size()<16||score<candidates.get(candidates.size()-1).score()){
                candidates.add(new Candidate(target,score));candidates.sort(Comparator.comparingDouble(Candidate::score));if(candidates.size()>16)candidates.remove(16);
            }
        }
        for(Candidate candidate:candidates)if(shot.owner.hasLineOfSight(candidate.target()))return candidate.target();
        return null;
    }
    private record Candidate(LivingEntity target,double score){}
    private static void guide(Shot shot){
        double speed=shot.entity.getDeltaMovement().length();if(!Double.isFinite(speed)||speed<.05)return;
        var level=shot.owner.serverLevel();LivingEntity target=shot.target==null?null:level.getEntity(shot.target) instanceof LivingEntity living?living:null;
        if(target==null||!targetable(shot.owner,target)||target.distanceToSqr(shot.owner)>Math.pow(MetaConfig.HOMING_RANGE.get(),2)||!shot.owner.hasLineOfSight(target)){
            if(level.getGameTime()<shot.retry)return;shot.retry=level.getGameTime()+4;target=choose(shot);shot.target=target==null?null:target.getUUID();
        }
        if(target==null)return;
        Vec3 destination=target.getBoundingBox().getCenter(),position=shot.entity.position();
        if(level.clip(new ClipContext(position,destination,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shot.entity)).getType()!=HitResult.Type.MISS)return;
        var movement=GuidedAim.velocity(vector(destination.subtract(position)),vector(target.getDeltaMovement()),speed);
        if(movement.squared()<1e-8)return;
        shot.entity.setDeltaMovement(movement.x(),movement.y(),movement.z());shot.entity.hasImpulse=true;shot.entity.hurtMarked=true;
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)SHOTS.remove(level);}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){SHOTS.clear();}
}
