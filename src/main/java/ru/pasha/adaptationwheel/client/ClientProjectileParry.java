package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import ru.pasha.adaptationwheel.AdaptationWheel;

/** Authoritative entity state updates; the ordinary projectile renderer remains untouched. */
@Mod.EventBusSubscriber(modid=AdaptationWheel.MOD_ID,value=Dist.CLIENT)
public final class ClientProjectileParry {
    private ClientProjectileParry() {}
    public static void accept(int id,int owner,Vec3 position,Vec3 velocity) {
        var level=Minecraft.getInstance().level;
        if(level==null || !(level.getEntity(id) instanceof Projectile shot))return;
        shot.setOwner(level.getEntity(owner)); shot.setPos(position); shot.setDeltaMovement(velocity);
        if(velocity.lengthSqr()>1e-6) {
            float yaw=(float)Math.toDegrees(Math.atan2(velocity.x,velocity.z));
            float pitch=(float)Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance()));
            shot.setYRot(yaw);shot.yRotO=yaw;shot.setXRot(pitch);shot.xRotO=pitch;
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void predictedImpact(ProjectileImpactEvent event) {
        if(!event.getProjectile().level().isClientSide || !(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)
                || !(hit.getEntity() instanceof net.minecraft.world.entity.player.Player player)
                || !player.isUsingItem() || !player.getUseItem().is(AdaptationWheel.EXTERMINATION_SWORD.get()))return;
        var state=ClientWheelState.get(player.getId());var owner=event.getProjectile().getOwner();
        if(state==null || !state.enabled() || !state.equipped() || !(owner instanceof net.minecraft.world.entity.LivingEntity) || owner==player)return;
        String key=ru.pasha.adaptationwheel.server.DamageCategories.forEntity((net.minecraft.world.entity.LivingEntity)owner);
        if(state.entries().stream().anyMatch(e->e.key().equals(key)&&e.stage()>=1&&e.enabled())
                && ru.pasha.adaptationwheel.item.BladeGuard.contact(player,event.getProjectile().position(),
                    event.getProjectile().position().add(event.getProjectile().getDeltaMovement()))!=null)
            event.setCanceled(true);
    }
}
