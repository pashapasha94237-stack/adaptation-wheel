package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.AdaptationConfig;
import net.minecraftforge.fml.config.*;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import ru.pasha.adaptationwheel.item.ExterminationSwordItem;
import ru.pasha.adaptationwheel.server.*;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExterminationSwordGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void individualMobMultipliersChangeRealDamageIndependently(GameTestHelper h) {
        var config=ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
            .filter(c->c.getModId().equals(AdaptationWheel.MOD_ID)).findFirst().orElseThrow();
        try{com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().setWatch(config.getFullPath(),()->{});}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        var oldTargets=java.util.List.copyOf(AdaptationConfig.SWORD_SPECIAL_MOBS.get());
        var oldRules=java.util.List.copyOf(AdaptationConfig.SWORD_MOB_MULTIPLIERS.get());double oldCommon=AdaptationConfig.SWORD_CURSED_MULTIPLIER.get();
        TestPlayer p=new TestPlayer(h.getLevel());p.stopUsingItem();p.setOnGround(true);var original=p.getMainHandItem();
        try {
            AdaptationConfig.SWORD_SPECIAL_MOBS.set(java.util.List.of("minecraft:wither","minecraft:warden","minecraft:zombie","minecraft:vex"));
            AdaptationConfig.SWORD_CURSED_MULTIPLIER.set(3.0);
            AdaptationConfig.SWORD_MOB_MULTIPLIERS.set(java.util.List.of("minecraft:wither=4","minecraft:warden=6","minecraft:zombie=1.5","minecraft:creeper=50"));reload(config);
            actualStrike(h,p,EntityType.WITHER,40);actualStrike(h,p,EntityType.WARDEN,60);actualStrike(h,p,EntityType.ZOMBIE,15);
            actualStrike(h,p,EntityType.VEX,30);actualStrike(h,p,EntityType.CREEPER,10);
            AdaptationConfig.SWORD_CURSED_MULTIPLIER.set(5.0);
            AdaptationConfig.SWORD_MOB_MULTIPLIERS.set(java.util.List.of("minecraft:wither=4","minecraft:warden=0","minecraft:zombie=1.5"));reload(config);
            actualStrike(h,p,EntityType.WITHER,40);actualStrike(h,p,EntityType.WARDEN,0);actualStrike(h,p,EntityType.VEX,50);
            AdaptationConfig.SWORD_SPECIAL_MOBS.set(java.util.List.of("minecraft:vex"));reload(config);
            actualStrike(h,p,EntityType.WITHER,20);actualStrike(h,p,EntityType.WARDEN,10);
            AdaptationConfig.SWORD_SPECIAL_MOBS.set(java.util.List.of("minecraft:wither","minecraft:warden"));
            AdaptationConfig.SWORD_MOB_MULTIPLIERS.set(java.util.List.of());reload(config);
            actualStrike(h,p,EntityType.WITHER,50);actualStrike(h,p,EntityType.WARDEN,50);
            check(h,p.getMainHandItem()==original,"Individual multiplier edits work on the same held sword");
        }finally{
            AdaptationConfig.SWORD_SPECIAL_MOBS.set(oldTargets);AdaptationConfig.SWORD_MOB_MULTIPLIERS.set(oldRules);AdaptationConfig.SWORD_CURSED_MULTIPLIER.set(oldCommon);reload(config);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void configuredSpecialMobListAddsRemovesAndClearsRealDamageTargets(GameTestHelper h) {
        var config=ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
            .filter(c->c.getModId().equals(AdaptationWheel.MOD_ID)).findFirst().orElseThrow();
        try {com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().setWatch(config.getFullPath(),()->{});}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        var old=java.util.List.copyOf(AdaptationConfig.SWORD_SPECIAL_MOBS.get());
        TestPlayer p=new TestPlayer(h.getLevel());p.stopUsingItem();p.setOnGround(true);ItemStack original=p.getMainHandItem();
        try {
            AdaptationConfig.SWORD_SPECIAL_MOBS.set(java.util.List.of("minecraft:creeper","minecraft:zombie","examplemod:missing_spirit"));reload(config);
            actualStrike(h,p,EntityType.CREEPER,40);actualStrike(h,p,EntityType.ZOMBIE,40);
            actualStrike(h,p,EntityType.WITHER,20);actualStrike(h,p,EntityType.WARDEN,10);actualStrike(h,p,EntityType.VEX,10);
            actualStrike(h,p,EntityType.ZOMBIE_VILLAGER,20);
            check(h,ru.pasha.adaptationwheel.core.SwordSettings.current().specialMobs().contains("examplemod:missing_spirit"),"Unknown mod IDs are retained without affecting existing creatures");
            AdaptationConfig.SWORD_SPECIAL_MOBS.set(java.util.List.of());reload(config);
            actualStrike(h,p,EntityType.CREEPER,10);actualStrike(h,p,EntityType.ZOMBIE,20);actualStrike(h,p,EntityType.WITHER,20);
            check(h,ru.pasha.adaptationwheel.core.SwordSettings.current().specialMobs().isEmpty(),"An explicitly empty list never falls back to defaults or legacy tags");
            AdaptationConfig.SWORD_SPECIAL_MOBS.set(java.util.List.of("minecraft:wither"));reload(config);
            actualStrike(h,p,EntityType.WITHER,40);actualStrike(h,p,EntityType.WARDEN,10);
            check(h,p.getMainHandItem()==original,"All list edits apply to the same held sword without recreating it");
        }finally{AdaptationConfig.SWORD_SPECIAL_MOBS.set(old);reload(config);}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void defaultSpecialCreaturesReceiveFourfoldActualDamage(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());p.stopUsingItem();p.setOnGround(true);
        for(EntityType<? extends LivingEntity> type:java.util.List.of(EntityType.WITHER,EntityType.WARDEN,EntityType.ELDER_GUARDIAN,
                EntityType.GHAST,EntityType.VEX,EntityType.WITHER_SKELETON)) {
            check(h,ExterminationSwordEvents.energyMultiplier(type.create(h.getLevel()))==4,"Built-in roster receives x4: "+type);
            actualStrike(h,p,type,40);
        }
        actualStrike(h,p,EntityType.ZOMBIE,20);actualStrike(h,p,EntityType.SKELETON,20);actualStrike(h,p,EntityType.COW,10);
        check(h,ExterminationSwordEvents.energyMultiplier(EntityType.ENDER_DRAGON.create(h.getLevel()))==1,"Boss status alone does not mark unrelated creatures as spirits");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void configuredSwordValuesChangeRealAttacksWithoutSwappingExistingSword(GameTestHelper h) {
        var config=ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
            .filter(c->c.getModId().equals(AdaptationWheel.MOD_ID)).findFirst().orElseThrow();
        try {com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().setWatch(config.getFullPath(),()->{});}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        double oldBase=AdaptationConfig.SWORD_BASE_DAMAGE.get(),oldUndead=AdaptationConfig.SWORD_UNDEAD_MULTIPLIER.get(),oldCursed=AdaptationConfig.SWORD_CURSED_MULTIPLIER.get();
        var oldMobs=java.util.List.copyOf(AdaptationConfig.SWORD_SPECIAL_MOBS.get());
        TestPlayer p=new TestPlayer(h.getLevel());p.stopUsingItem();p.setOnGround(true);
        ItemStack originalSword=p.getMainHandItem();
        try {
            var targets=new java.util.ArrayList<String>(oldMobs);targets.add("minecraft:zombie_villager");AdaptationConfig.SWORD_SPECIAL_MOBS.set(targets);
            AdaptationConfig.SWORD_BASE_DAMAGE.set(5.5);AdaptationConfig.SWORD_UNDEAD_MULTIPLIER.set(1.5);AdaptationConfig.SWORD_CURSED_MULTIPLIER.set(3.0);reload(config);
            actualStrike(h,p,EntityType.COW,5.5F);
            actualStrike(h,p,EntityType.ZOMBIE,8.25F);
            actualStrike(h,p,EntityType.VEX,16.5F);
            actualStrike(h,p,EntityType.ZOMBIE_VILLAGER,16.5F);
            AdaptationConfig.SWORD_BASE_DAMAGE.set(25.0);reload(config);
            actualStrike(h,p,EntityType.COW,25);actualStrike(h,p,EntityType.ZOMBIE,37.5F);actualStrike(h,p,EntityType.VEX,75);
            check(h,p.getMainHandItem()==originalSword,"Configuration edits update an already held sword without recreating or swapping it");
            UUID otherModifier=UUID.randomUUID();
            p.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(new AttributeModifier(otherModifier,"Other mod strength",2,AttributeModifier.Operation.ADDITION));
            ExterminationSwordItem.refreshHeldDamage(p);actualStrike(h,p,EntityType.COW,27);
            p.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(otherModifier);
            AdaptationConfig.SWORD_UNDEAD_MULTIPLIER.set(0.0);reload(config);actualStrike(h,p,EntityType.ZOMBIE,0);
            actualStrike(h,p,EntityType.VEX,75);
            AdaptationConfig.SWORD_BASE_DAMAGE.set(0.0);reload(config);actualStrike(h,p,EntityType.COW,0);
        }finally{
            AdaptationConfig.SWORD_BASE_DAMAGE.set(oldBase);AdaptationConfig.SWORD_UNDEAD_MULTIPLIER.set(oldUndead);AdaptationConfig.SWORD_CURSED_MULTIPLIER.set(oldCursed);AdaptationConfig.SWORD_SPECIAL_MOBS.set(oldMobs);reload(config);
        }
        h.succeed();
    }
    private static void actualStrike(GameTestHelper h,TestPlayer p,EntityType<? extends LivingEntity> type,float expected) {
        LivingEntity target=type.create(h.getLevel());target.setPos(0,100,1);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        p.attack(target);
        check(h,Math.abs(target.getHealth()-(200-expected))<1e-4,"Real player attack on "+type+" expected "+expected+" damage, got "+(200-target.getHealth()));
        check(h,Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE)-ru.pasha.adaptationwheel.core.SwordSettings.current().baseDamage())<1e-4 || expected==27,"Held attack attribute uses configured base damage");
    }
    private static void reload(ModConfig config){AdaptationConfig.SPEC.afterReload();AdaptationConfig.changed(new ModConfigEvent.Reloading(config));}
    @GameTest(template="empty",timeoutTicks=60)
    public static void positiveEnergyChangesRealMeleeDamageAndRespectsEquipment(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());
        checkDamage(h,p,EntityType.COW,10,p.damageSources().playerAttack(p));
        checkDamage(h,p,EntityType.ZOMBIE,20,p.damageSources().playerAttack(p));
        checkDamage(h,p,EntityType.VEX,40,p.damageSources().playerAttack(p));
        var arrow=EntityType.ARROW.create(h.getLevel());arrow.setOwner(p);
        checkDamage(h,p,EntityType.ZOMBIE,10,p.damageSources().arrow(arrow,p));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AdaptationWheel.EXTERMINATION_SWORD.get()));
        checkDamage(h,p,EntityType.ZOMBIE,10,p.damageSources().playerAttack(p));
        h.succeed();
    }
    private static void checkDamage(GameTestHelper h,TestPlayer player,EntityType<? extends LivingEntity> type,float expected,DamageSource source) {
        LivingEntity target=type.create(h.getLevel());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.hurt(source,10);
        check(h,Math.abs(target.getHealth()-(100-expected))<1e-4,"Expected "+expected+" actual health damage to "+type);
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void learnedParryReturnsArrowAndAcceleratesExistingAdaptation(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());var shooter=EntityType.SKELETON.create(h.getLevel());shooter.setPos(0,100,8);
        var unlearned=arrow(p,shooter,true);var first=impact(unlearned,p);
        check(h,!first.isCanceled() && unlearned.getOwner()==shooter,"Unlearned projectiles are not blocked for free");
        learn(p,shooter);var state=AdaptationEvents.state(p);var before=state.snapshots().get(0);
        var arrow=arrow(p,shooter,true);var reflected=impact(arrow,p);
        check(h,reflected.isCanceled() && arrow.getOwner()==p && arrow.getDeltaMovement().lengthSqr()==0,"Learned frontal arrow stops at the blade with correct ownership");
        check(h,arrow.getZ()>.3 && arrow.getZ()<1.3,"Contact remains on the blade in front of the body, without jumping to the eyes");
        check(h,p.getUseItem().getDamageValue()==2,"Successful reflection spends sword durability exactly once");
        p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(h,state.snapshots().stream().anyMatch(e->e.key().equals(before.key()) && e.hits()==before.hits()+1),"Parried contact still accelerates the running adaptation once");
        h.runAfterDelay(3,()->{check(h,arrow.getDeltaMovement().z>0,"After blade contact the same arrow flies back toward the shooter");h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void parryRequiresFacingRaisedBladeAndActiveLearnedWheel(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());var shooter=EntityType.SKELETON.create(h.getLevel());shooter.setPos(0,100,8);learn(p,shooter);
        check(h,!impact(arrow(p,shooter,false),p).isCanceled(),"Attacks from behind are not reflected");
        p.stopUsingItem();check(h,!impact(arrow(p,shooter,true),p).isCanceled(),"Merely carrying the sword does not parry");
        p.startUsingItem(InteractionHand.MAIN_HAND);var state=AdaptationEvents.state(p);
        state.setEnabled(false);check(h,!impact(arrow(p,shooter,true),p).isCanceled(),"Global adaptation toggle is respected");state.setEnabled(true);
        String key=DamageCategories.forEntity(shooter);state.setCategoryEnabled(key,false);
        check(h,!impact(arrow(p,shooter,true),p).isCanceled(),"Per-source adaptation toggle is respected");state.setCategoryEnabled(key,true);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,!impact(arrow(p,shooter,true),p).isCanceled(),"Parry requires the wheel to be worn");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void fireballParryRedirectsAccelerationAndPreservesProjectile(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());var blaze=EntityType.BLAZE.create(h.getLevel());blaze.setPos(0,100,8);learn(p,blaze);
        var edge=ru.pasha.adaptationwheel.item.BladeGuard.edge(p);
        var ball=EntityType.SMALL_FIREBALL.create(h.getLevel());ball.setOwner(blaze);ball.setPos(edge[0].add(edge[1]).scale(.5).add(0,0,1));ball.setDeltaMovement(0,0,-1);
        ball.zPower=-0.1;var reflected=impact(ball,p);
        check(h,reflected.isCanceled() && !ball.isRemoved() && ball.getOwner()==p,"Parry reuses the original fireball rather than deleting or duplicating it");
        check(h,ball.getDeltaMovement().z>0 && ball.zPower>0,"Both fireball velocity and acceleration point back at the attacker");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void swordRecipeAttributesAndNormalDamageWhileGuarding(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());ItemStack sword=p.getMainHandItem();
        check(h,sword.getMaxStackSize()==1 && sword.getMaxDamage()==2031,"Sword has netherite durability and cannot stack");
        check(h,!p.isBlocking(),"Raised sword is not a generic shield that blocks unadapted damage");
        float health=p.getHealth();p.damage(p.damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel())),3);
        check(h,p.getHealth()==health-3,"Unadapted melee still damages a guarding player");
        var menu=new AbstractContainerMenu(null,0) {
            public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
            public boolean stillValid(Player player){return true;}
        };
        var grid=new TransientCraftingContainer(menu,3,3);
        Item[] ingredients={Items.AIR,Items.QUARTZ_BLOCK,Items.AIR,Items.STRING,Items.QUARTZ_BLOCK,Items.STRING,Items.END_ROD,Items.NETHERITE_INGOT,Items.END_ROD};
        for(int i=0;i<9;i++)grid.setItem(i,new ItemStack(ingredients[i]));
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,h.getLevel());
        check(h,recipe.isPresent() && recipe.get().assemble(grid,h.getLevel().registryAccess()).is(AdaptationWheel.EXTERMINATION_SWORD.get()),"Survival crafting produces the registered sword");
        h.succeed();
    }
    private static void learn(TestPlayer p,LivingEntity attacker) {
        var state=AdaptationEvents.state(p);String key=DamageCategories.forEntity(attacker);state.hit(key,DamageCategories.boss(attacker));
        while(state.stage(key)<1)state.tick();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void bladeSweepRejectsNearMissAndCatchesFastArrowExactlyOnce(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());
        var shooter=EntityType.SKELETON.create(h.getLevel());shooter.setPos(0,100,6);learn(p,shooter);
        var edge=ru.pasha.adaptationwheel.item.BladeGuard.edge(p);
        var centre=edge[0].add(edge[1]).scale(.5);
        var miss=arrow(p,shooter,true);miss.setPos(centre.add(2,0,3));miss.setDeltaMovement(0,0,-6);
        check(h,!ProjectileParry.tryParry(miss,p)&&miss.getOwner()==shooter,"A nearby arrow missing the blade must remain unreflected");
        var fast=arrow(p,shooter,true);fast.setPos(centre.add(0,0,3));fast.setDeltaMovement(0,0,-6);
        check(h,ProjectileParry.tryParry(fast,p),"Swept collision catches an arrow crossing the whole blade in one tick");
        check(h,fast.position().distanceTo(centre)<.3,"The contact stays on the physical blade");
        check(h,!ProjectileParry.tryParry(fast,p)&&p.getUseItem().getDamageValue()==2,"The same contact cannot spend durability twice");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void leftHandGuardHasAMirroredPhysicalBlade(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());
        var right=ru.pasha.adaptationwheel.item.BladeGuard.edge(p);
        p.setMainArm(HumanoidArm.LEFT);
        var left=ru.pasha.adaptationwheel.item.BladeGuard.edge(p);
        for(int i=0;i<2;i++)check(h,Math.abs(right[i].x+left[i].x)<1e-5 && Math.abs(right[i].y-left[i].y)<1e-5
                && Math.abs(right[i].z-left[i].z)<1e-5,"Both handedness settings must mirror the physical worn blade");
        h.succeed();
    }
    private static AbstractArrow arrow(TestPlayer p,LivingEntity owner,boolean front) {
        var edge=ru.pasha.adaptationwheel.item.BladeGuard.edge(p);
        var arrow=EntityType.ARROW.create(p.level());arrow.setOwner(owner);arrow.setPos(0,(edge[0].y+edge[1].y)/2,front?1.5:-1);arrow.setDeltaMovement(0,0,front?-2:1);return arrow;
    }
    private static ProjectileImpactEvent impact(Projectile projectile,TestPlayer p) {
        var event=new ProjectileImpactEvent(projectile,new EntityHitResult(p));MinecraftForge.EVENT_BUS.post(event);return event;
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level) {
            super(level,new GameProfile(UUID.randomUUID(),"SwordTest"));setPos(0,100,0);setYRot(0);setXRot(0);setHealth(20);
            setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
            setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AdaptationWheel.EXTERMINATION_SWORD.get()));startUsingItem(InteractionHand.MAIN_HAND);
        }
        @Override public boolean isInvulnerableTo(DamageSource source){return false;}
        @Override public float getAttackStrengthScale(float partialTick){return 1;}
        void damage(DamageSource source,float amount){super.actuallyHurt(source,amount);}
    }
}
