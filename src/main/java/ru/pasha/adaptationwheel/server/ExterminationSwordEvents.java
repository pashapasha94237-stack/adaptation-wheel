package ru.pasha.adaptationwheel.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.SwordSettings;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.item.ExterminationSwordItem;

/** Server-authoritative sword powers. Targeting comes from the complete editable server list. */
public final class ExterminationSwordEvents {
    private ExterminationSwordEvents() {}

    public static float energyMultiplier(LivingEntity target) {
        var settings=SwordSettings.current();
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        if(settings.specialMobs().contains(id))return (float)settings.specialMultiplier(id);
        return target.getMobType()==MobType.UNDEAD?(float)settings.undeadMultiplier():1;
    }
    @SubscribeEvent public static void refreshHeldWeapon(LivingEvent.LivingTickEvent event) {
        if(!event.getEntity().level().isClientSide)ExterminationSwordItem.refreshHeldDamage(event.getEntity());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void refreshBeforeAttack(AttackEntityEvent event) {
        if(!event.getEntity().level().isClientSide)ExterminationSwordItem.refreshHeldDamage(event.getEntity());
    }
    private static boolean swordMelee(DamageSource source) {
        return !WorldSlash.isAttack(source) && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player player
            && source.getDirectEntity()==player && player.getMainHandItem().is(AdaptationWheel.EXTERMINATION_SWORD.get());
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void positiveEnergy(LivingHurtEvent event) {
        if(!event.getEntity().level().isClientSide && event.getAmount()>0 && swordMelee(event.getSource()))
            event.setAmount(event.getAmount()*energyMultiplier(event.getEntity()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void impactLight(LivingDamageEvent event) {
        if(event.getAmount()>0 && swordMelee(event.getSource()) && event.getEntity().level() instanceof ServerLevel level
                && energyMultiplier(event.getEntity())>1) {
            var target=event.getEntity();
            level.sendParticles(ParticleTypes.END_ROD,target.getX(),target.getY()+target.getBbHeight()/2,target.getZ(),
                14,target.getBbWidth()/2,target.getBbHeight()/3,target.getBbWidth()/2,0.04);
            level.playSound(null,target.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,0.8F,1.5F);
        }
    }

    private static boolean learnedGuard(ServerPlayer player,LivingEntity attacker) {
        if(attacker==player || !player.isAlive() || !player.isUsingItem()
                || !player.getUseItem().is(AdaptationWheel.EXTERMINATION_SWORD.get()) || !AdaptationEvents.active(player))return false;
        var state=AdaptationEvents.state(player);String key=DamageCategories.forEntity(attacker);
        return state.stage(key)>=1 && state.categoryEnabled(key);
    }
    private static boolean meleeSource(DamageSource source) {
        if(DamageCategories.excluded(source.typeHolder()) || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.THORNS))return false;
        if(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK))return true;
        // Custom direct physical hits can parry too; vanilla hazards and magical beams never become melee attacks.
        return source.typeHolder().unwrapKey().map(key->!key.location().getNamespace().equals("minecraft")).orElse(true)
            && DamageCategories.profile(source)==AdaptationProfile.PHYSICAL;
    }
    private static void spendParryDurability(ServerPlayer player) {
        var hand=player.getUsedItemHand();player.getUseItem().hurtAndBreak(2,player,p->p.broadcastBreakEvent(hand));
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void parryAdaptedMelee(LivingAttackEvent event) {
        if(event.getAmount()<=0 || !(event.getEntity() instanceof ServerPlayer player) || !meleeSource(event.getSource())
                || !(event.getSource().getDirectEntity() instanceof LivingEntity attacker)
                || event.getSource().getEntity()!=attacker || !learnedGuard(player,attacker))return;
        Vec3 incoming=attacker.position().subtract(player.position()).multiply(1,0,1);
        if(incoming.lengthSqr()<1.0E-6 || Vec3.directionFromRotation(0,player.getYRot()).dot(incoming.normalize())<0.15)return;
        event.setCanceled(true);
        spendParryDurability(player);
        // AdaptationEvents.onContact observes cancelled LivingAttackEvent exactly once; do not queue a second exposure here.
        Vec3 sparks=player.getEyePosition().add(incoming.normalize().scale(0.7));
        player.serverLevel().sendParticles(ParticleTypes.CRIT,sparks.x,sparks.y-0.2,sparks.z,12,0.18,0.18,0.18,0.06);
        player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.SHIELD_BLOCK,SoundSource.PLAYERS,0.9F,1.25F);
    }

    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void reflectAdaptedProjectile(ProjectileImpactEvent event) {
        if(event.getProjectile().level().isClientSide || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer player))return;
        var projectile=event.getProjectile();
        if(!(projectile.getOwner() instanceof LivingEntity attacker) || !learnedGuard(player,attacker))return;
        String key=DamageCategories.forEntity(attacker);
        Vec3 incoming=projectile.position().subtract(player.getEyePosition());
        if(incoming.lengthSqr()<1.0E-6 || player.getLookAngle().dot(incoming.normalize())<0.15)return;
        Vec3 direction=attacker.getEyePosition().subtract(projectile.position()).normalize();
        if(direction.lengthSqr()<0.5)return;
        float speed=(float)Math.max(0.6,Math.min(4,projectile.getDeltaMovement().length()));
        projectile.setOwner(player);
        projectile.setPos(player.getEyePosition().add(direction.scale(0.8)));
        projectile.shoot(direction.x,direction.y,direction.z,speed,0);
        // Fireballs have acceleration as well as velocity; both must point away from the defender.
        if(projectile instanceof AbstractHurtingProjectile fireball) {
            double acceleration=Math.sqrt(fireball.xPower*fireball.xPower+fireball.yPower*fireball.yPower+fireball.zPower*fireball.zPower);
            fireball.xPower=direction.x*acceleration;fireball.yPower=direction.y*acceleration;fireball.zPower=direction.z*acceleration;
        }
        projectile.hurtMarked=true;
        event.setCanceled(true);
        AdaptationEvents.recordExposure(player,key,DamageCategories.boss(attacker));
        spendParryDurability(player);
        ServerLevel level=player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD,projectile.getX(),projectile.getY(),projectile.getZ(),12,0.15,0.15,0.15,0.08);
        level.playSound(null,player.blockPosition(),SoundEvents.TRIDENT_RETURN,SoundSource.PLAYERS,0.8F,1.6F);
    }
}
