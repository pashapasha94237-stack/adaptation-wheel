package ru.pasha.adaptationwheel.test;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.AdaptationState;
import ru.pasha.adaptationwheel.server.*;
import java.util.*;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdaptationGameTests {
    private static final String Z="mob:minecraft:zombie",F="damage:minecraft:fall";
    @GameTest(template="empty",timeoutTicks=40)
    public static void blockedBossHitRestartsExistenceAfterDeathAndLegacyImmunity(GameTestHelper h) {
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);
        DamageSource source=h.getLevel().damageSources().mobAttack(zombie);
        TestPlayer p=new TestPlayer(h.getLevel());
        AdaptationEvents.state(p).hit(Z,true);advanceTo(p,Z,4);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(p,p.damageSources().generic()));
        check(h,!AdaptationEvents.state(p).snapshots().get(0).running(),"Death resets the unfinished fifth stage");
        var hit=new LivingAttackEvent(p,source,5);MinecraftForge.EVENT_BUS.post(hit);tick(p);
        check(h,hit.isCanceled() && AdaptationEvents.state(p).snapshots().get(0).running(),
            "A blocked boss hit resumes existence adaptation after death");
        advanceTo(p,Z,5);check(h,AdaptationEvents.state(p).existenceAdapted(Z),"Resumed fifth stage can complete");
        TestPlayer migrated=new TestPlayer(h.getLevel());var legacy=new net.minecraft.nbt.CompoundTag();
        legacy.putInt("Version",1);var completed=new net.minecraft.nbt.ListTag();
        completed.add(net.minecraft.nbt.StringTag.valueOf(Z));legacy.put("Completed",completed);
        migrated.getPersistentData().put(AdaptationWheel.MOD_ID,legacy);
        var legacyHit=new LivingAttackEvent(migrated,source,5);MinecraftForge.EVENT_BUS.post(legacyHit);tick(migrated);
        var entry=AdaptationEvents.state(migrated).snapshots().get(0);
        check(h,legacyHit.isCanceled() && entry.stage()==4 && entry.boss() && entry.running(),
            "Legacy full immunity can discover and start its new boss existence stage");
        advanceTo(migrated,Z,5);check(h,AdaptationEvents.state(migrated).existenceAdapted(Z),"Migrated boss fifth stage completes");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void contactHitsAccelerateWithoutHealthLossAndNeverDoubleCount(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel());
        DamageSource source=helper.getLevel().damageSources().fall();
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(p,source,1)); tick(p);
        check(helper,AdaptationEvents.state(p).snapshots().isEmpty(),"First adaptation still requires actual damage");
        p.damage(source,1); tick(p);
        // One normal strike follows both event paths. Two more contact strikes
        // only reach Attack, as happens during vanilla hurt invulnerability.
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(p,source,1)); p.damage(source,1);
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(p,source,1));
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(p,source,1)); tick(p);
        var entry=AdaptationEvents.state(p).snapshots().get(0);
        check(helper,entry.hits()==3,"Three contact hits count exactly once each, even with the same DamageSource");
        check(helper,Math.abs(entry.speed()-1.9)<.00001,"Three repeats provide +90% speed");
        check(helper,Math.abs(entry.work()-1.9)<.00001,"Bonus applies immediately to server progress");
        check(helper,p.getHealth()==18,"Cooldown contact hits do not require further lost health");
        int remaining=entry.estimatedTicks(); advance(p,remaining-1);
        check(helper,AdaptationEvents.state(p).stage(F)==0,"No early completion"); tick(p);
        check(helper,AdaptationEvents.state(p).stage(F)==1,"Accelerated predicted countdown matches actual completion");
        check(helper,AdaptationEvents.state(p).snapshots().get(0).hits()==0,"Next stage resets acceleration");
        AdaptationEvents.state(p).setCategoryEnabled(F,false);
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(p,source,1)); tick(p);
        check(helper,AdaptationEvents.state(p).snapshots().get(0).hits()==0,"Disabled category cannot collect contact bonuses");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void everyStageFullyHealsToActualMaximum(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel());
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30);
        String key="mob:minecraft:warden";
        AdaptationEvents.state(p).hit(key,true);
        for(int stage=1;stage<=5;stage++) {
            p.setHealth(3);
            tick(p);
            check(helper,p.getHealth()==3,"An unfinished stage must not heal");
            advanceTo(p,key,stage);
            check(helper,p.getHealth()==p.getMaxHealth(),"Stage "+stage+" fully heals to actual max HP");
        }
        p.setHealth(3); advance(p,100);
        check(helper,p.getHealth()==3,"Finished adaptations must not repeatedly heal");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void fourActualDamageReductions(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel());
        Zombie zombie=EntityType.ZOMBIE.create(helper.getLevel());
        DamageSource source=helper.getLevel().damageSources().mobAttack(zombie);
        p.damage(source,2); tick(p);
        check(helper,p.getHealth()==18,"First hit is unprotected");
        int[] reductions={20,40,70,100};
        for(int stage=1;stage<=4;stage++) {
            advanceTo(p,Z,stage);
            p.setHealth(20); p.damage(source,10);
            float expected=20-10*(1-reductions[stage-1]/100f);
            check(helper,Math.abs(p.getHealth()-expected)<.001,"Stage "+stage+" must reduce damage by "+reductions[stage-1]+"%");
            tick(p);
        }
        for(EntityType<? extends Zombie> type:List.of(EntityType.ZOMBIE,EntityType.HUSK,EntityType.DROWNED,EntityType.ZOMBIE_VILLAGER,EntityType.ZOMBIFIED_PIGLIN)) {
            var other=type.create(helper.getLevel());
            DamageSource hit=helper.getLevel().damageSources().mobAttack(other);
            check(helper,Z.equals(DamageCategories.from(hit)),"All zombie variants share adaptation");
            LivingAttackEvent event=new LivingAttackEvent(p,hit,10);
            MinecraftForge.EVENT_BUS.post(event); check(helper,event.isCanceled(),"A different zombie cannot attack");
        }
        p.setHealth(20); var skeleton=EntityType.SKELETON.create(helper.getLevel());
        p.damage(helper.getLevel().damageSources().mobAttack(skeleton),2);
        check(helper,p.getHealth()==18,"Unrelated mob remains dangerous");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void repeatedHitsSpeedAndEquipmentPause(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel()); DamageSource fall=helper.getLevel().damageSources().fall();
        p.damage(fall,1); tick(p); advance(p,100);
        p.damage(fall,1); tick(p);
        var entry=AdaptationEvents.state(p).snapshots().get(0);
        check(helper,entry.hits()==1 && Math.abs(entry.speed()-1.3)<.00001,"Second hit accelerates by 30%");
        double work=entry.work(); p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY); advance(p,100);
        check(helper,AdaptationEvents.state(p).snapshots().get(0).work()==work,"Removing wheel pauses timers");
        p.setHealth(20); p.damage(fall,5); tick(p);
        check(helper,p.getHealth()==15,"No protection without wheel");
        check(helper,AdaptationEvents.state(p).snapshots().get(0).hits()==1,"No exposure gain without wheel");
        p.equip(); advanceTo(p,F,1);
        AdaptationEvents.state(p).setCategoryEnabled(F,false);
        work=AdaptationEvents.state(p).snapshots().get(0).work(); advance(p,100);
        check(helper,AdaptationEvents.state(p).snapshots().get(0).work()==work,"Menu category toggle pauses only that source");
        p.setHealth(20); p.damage(fall,10); check(helper,p.getHealth()==10,"Disabled category removes partial protection");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void bossFifthStageAndOnePunch(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel()); LivingEntity warden=EntityType.WARDEN.create(helper.getLevel());
        String key=DamageCategories.forEntity(warden);
        p.damage(helper.getLevel().damageSources().mobAttack(warden),1); tick(p); advanceTo(p,key,4);
        check(helper,AdaptationEvents.state(p).isImmune(key),"Fourth boss stage fully protects");
        check(helper,!AdaptationEvents.state(p).existenceAdapted(key),"Fourth stage cannot one-hit");
        check(helper,!AdaptationEvents.executeAdaptedBoss(p,warden),"Execution locked before fifth");
        LivingAttackEvent hit=new LivingAttackEvent(p,helper.getLevel().damageSources().mobAttack(warden),10);
        MinecraftForge.EVENT_BUS.post(hit); tick(p);
        check(helper,hit.isCanceled(),"Boss attack canceled at full immunity");
        check(helper,AdaptationEvents.state(p).snapshots().get(0).hits()==1,"Blocked boss attack accelerates existence");
        advanceTo(p,key,5);
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(p,warden));
        check(helper,!warden.isAlive(),"An empty-hand melee event defeats adapted warden");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void customHighHealthMobAndInclusiveThreshold(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel()); LivingEntity mob=EntityType.SKELETON.create(helper.getLevel());
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        check(helper,DamageCategories.boss(mob),"Exactly 100 HP qualifies as a boss");
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(99);
        check(helper,!DamageCategories.boss(mob),"Below 100 HP does not qualify");
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(101); mob.setHealth(101);
        check(helper,DamageCategories.boss(mob),"Any mob above 100 HP qualifies");
        String key=DamageCategories.forEntity(mob); AdaptationEvents.state(p).hit(key,true); advanceTo(p,key,5);
        AdaptationEvents.state(p).setCategoryEnabled(key,false);
        check(helper,!AdaptationEvents.executeAdaptedBoss(p,mob),"Disabled category cannot execute a boss");
        AdaptationEvents.state(p).setCategoryEnabled(key,true);
        check(helper,AdaptationEvents.executeAdaptedBoss(p,mob) && !mob.isAlive(),"Custom high-health mob defeated in one hit");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void dragonPartAndWitherExecution(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel()); EnderDragon dragon=EntityType.ENDER_DRAGON.create(helper.getLevel());
        String key=DamageCategories.forEntity(dragon); AdaptationEvents.state(p).hit(key,true); advanceTo(p,key,5);
        check(helper,AdaptationEvents.executeAdaptedBoss(p,dragon.head),"Dragon multipart target resolves to dragon");
        check(helper,dragon.getHealth()==0,"Dragon health depleted by one hit");
        LivingEntity wither=EntityType.WITHER.create(helper.getLevel()); key=DamageCategories.forEntity(wither);
        AdaptationEvents.state(p).hit(key,true); advanceTo(p,key,5);
        check(helper,!DamageCategories.boss(helper.getLevel().damageSources().wither()),"Unattributed effect alone does not imply a >100 HP mob");
        float before=p.getHealth(); p.damage(helper.getLevel().damageSources().wither(),5);
        check(helper,p.getHealth()==before,"Wither existence protection also blocks its lingering effect");
        check(helper,AdaptationEvents.executeAdaptedBoss(p,wither) && !wither.isAlive(),"Wither defeated in one hit");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void persistenceDeathAndFatalHit(GameTestHelper helper) {
        TestPlayer p=new TestPlayer(helper.getLevel()); p.damage(helper.getLevel().damageSources().fall(),2); tick(p); advanceTo(p,F,2);
        AdaptationEvents.state(p).setCategoryEnabled(F,false); AdaptationEvents.state(p).setNightVisionEnabled(false); advance(p,123); AdaptationEvents.save(p);
        var saved=AdaptationEvents.loadState(p);
        check(helper,saved.stage(F)==2 && !saved.categoryEnabled(F),"NBT keeps stage and category toggle");
        TestPlayer replacement=new TestPlayer(helper.getLevel());
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,p,true));
        var state=AdaptationEvents.state(replacement);
        check(helper,state.stage(F)==2 && !state.snapshots().get(0).running(),"Death keeps completed degrees and stops unfinished progress");
        check(helper,!state.nightVisionEnabled() && !saved.nightVisionEnabled(),"Vision preference survives NBT and death clone");
        TestPlayer fatal=new TestPlayer(helper.getLevel()); fatal.setHealth(1);
        fatal.damage(helper.getLevel().damageSources().fall(),100); tick(fatal);
        check(helper,state.stage(F)==2 && AdaptationEvents.state(fatal).snapshots().isEmpty(),"Fatal first hit cannot start adaptation");
        check(helper,DamageCategories.from(helper.getLevel().damageSources().genericKill())==null,"Kill cannot adapt");
        check(helper,DamageCategories.from(helper.getLevel().damageSources().fellOutOfWorld())==null,"Void cannot adapt");
        helper.succeed();
    }
    private static void check(GameTestHelper helper,boolean condition,String message) { if(!condition) helper.fail(message); }
    private static void tick(TestPlayer p) { p.tickCount++; MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p)); }
    private static void advance(TestPlayer p,int count) { for(int i=0;i<count;i++) tick(p); }
    private static void advanceTo(TestPlayer p,String key,int stage) {
        int guard=20000;
        while(AdaptationEvents.state(p).stage(key)<stage && guard-->0) tick(p);
        if(guard<=0) throw new AssertionError("Stage never completed: "+key);
    }
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level) { super(level,new GameProfile(UUID.randomUUID(),"WheelTest")); setPos(0,100,0); setHealth(20); equip(); }
        void equip() { setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get())); }
        @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
        void damage(DamageSource source,float amount) { super.actuallyHurt(source,amount); }
    }
}
