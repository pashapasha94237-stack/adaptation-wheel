package ru.pasha.adaptationwheel.server;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.item.BladeGuard;
import ru.pasha.adaptationwheel.net.WheelNetwork;

/** Sweeps incoming projectiles against the raised blade before vanilla hits the defender's body. */
public final class ProjectileParry {
    private record Release(long tick,Vec3 velocity,boolean noGravity) {}
    private static final Map<Projectile,Release> HELD=new WeakHashMap<>();
    private ProjectileParry() {}

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.START || !(event.level instanceof ServerLevel level))return;
        for(var it=HELD.entrySet().iterator();it.hasNext();) {
            var entry=it.next(); Projectile shot=entry.getKey(); Release release=entry.getValue();
            if(shot.isRemoved()){it.remove();continue;}
            if(shot.level()!=level)continue;
            if(level.getGameTime()>=release.tick()) {
                shot.setNoGravity(release.noGravity()); shot.setDeltaMovement(release.velocity());
                shot.hurtMarked=true; WheelNetwork.parryProjectile(shot);
                it.remove();
            } else shot.setDeltaMovement(Vec3.ZERO);
        }
        for(ServerPlayer player:level.players()) {
            if(!player.isUsingItem() || !player.getUseItem().is(ru.pasha.adaptationwheel.AdaptationWheel.EXTERMINATION_SWORD.get())
                    || !AdaptationEvents.active(player))continue;
            for(Projectile shot:level.getEntitiesOfClass(Projectile.class,player.getBoundingBox().inflate(8)))
                tryParry(shot,player);
        }
    }
    public static boolean tryParry(Projectile shot,ServerPlayer player) {
        if(HELD.containsKey(shot) || shot.isRemoved() || !(shot.getOwner() instanceof LivingEntity attacker)
                || !ExterminationSwordEvents.learnedGuard(player,attacker))return false;
        Vec3 velocity=shot.getDeltaMovement();
        if(velocity.dot(player.getLookAngle())>=-.01)return false;
        Vec3 point=BladeGuard.contact(player,shot.position(),shot.position().add(velocity));
        if(point==null)return false;
        HitResult obstruction=player.level().clip(new ClipContext(shot.position(),point,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shot));
        if(obstruction.getType()!=HitResult.Type.MISS)return false;
        Vec3 target=attacker.position().add(0,attacker.getBbHeight()*.65,0);
        double speed=Math.max(.6,Math.min(4,velocity.length()));
        Vec3 returned=target.subtract(point).normalize().scale(speed);
        shot.setOwner(player); shot.setPos(point);
        if(shot instanceof AbstractArrow) {
            shot.setDeltaMovement(Vec3.ZERO);
            HELD.put(shot,new Release(player.level().getGameTime()+2,returned,shot.isNoGravity()));
            shot.setNoGravity(true);
        } else shot.setDeltaMovement(returned);
        shot.hurtMarked=true;
        if(shot instanceof AbstractHurtingProjectile ball) {
            double power=Math.sqrt(ball.xPower*ball.xPower+ball.yPower*ball.yPower+ball.zPower*ball.zPower);
            Vec3 direction=returned.normalize();ball.xPower=direction.x*power;ball.yPower=direction.y*power;ball.zPower=direction.z*power;
        }
        ExterminationSwordEvents.projectileParried(player,attacker,point);
        WheelNetwork.parryProjectile(shot);
        return true;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){HELD.clear();}
}
