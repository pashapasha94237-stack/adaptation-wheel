package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.server.AdaptationEvents;
import java.util.UUID;

/** Calls ServerPlayer.hurt rather than injecting downstream Forge events. */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiveDamageGameTests {
    private static final String Z="mob:minecraft:zombie";
    @GameTest(template="empty",timeoutTicks=60)
    public static void adaptedMeleeParryCancelsRealDamageAndAcceleratesOncePerAttack(GameTestHelper h) {
        var p=player(h);raiseSword(p);p.setYRot(0);var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setPos(0,100,1);
        var source=p.damageSources().mobAttack(zombie);
        check(h,p.hurt(source,1) && p.getHealth()==19,"First unlearned hit deals actual damage while the sword is raised");tick(p);
        var state=AdaptationEvents.state(p);while(state.stage(Z)<1)state.tick();p.invulnerableTime=0;p.setHealth(20);
        int before=entry(p).hits();
        check(h,!p.hurt(source,4) && !p.hurt(source,4),"Two real frontal melee attacks are cancelled after degree one");
        check(h,p.getHealth()==20 && p.getMainHandItem().getDamageValue()==4,"Each parry prevents health damage and spends exactly two durability");
        tick(p);check(h,entry(p).hits()==before+2 && Math.abs(entry(p).speed()-1.6)<1e-8,"Two parries add exactly two acceleration bonuses, without double counting");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void meleeParryRespectsFacingRaisedSwordWheelAndSourceToggle(GameTestHelper h) {
        var p=player(h);raiseSword(p);p.setYRot(0);var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setPos(0,100,1);learn(p,zombie);
        var source=p.damageSources().mobAttack(zombie);var state=AdaptationEvents.state(p);
        p.stopUsingItem();checkMeleeDamage(h,p,source,"Sword must be actively raised");raiseSword(p);
        zombie.setPos(0,100,-1);checkMeleeDamage(h,p,source,"Attacks from behind cannot be parried");zombie.setPos(0,100,1);
        state.setEnabled(false);checkMeleeDamage(h,p,source,"Global adaptation toggle is respected");state.setEnabled(true);
        state.setCategoryEnabled(Z,false);checkMeleeDamage(h,p,source,"Per-source adaptation toggle is respected");state.setCategoryEnabled(Z,true);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);checkMeleeDamage(h,p,source,"Wheel must remain equipped");p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        var spider=EntityType.SPIDER.create(h.getLevel());spider.setPos(0,100,1);
        checkMeleeDamage(h,p,p.damageSources().mobAttack(spider),"Learning zombies does not allow parrying an unlearned species");
        check(h,p.getMainHandItem().getDamageValue()==0,"Rejected parries never consume sword durability");
        p.invulnerableTime=0;p.setHealth(20);check(h,!p.hurt(source,4) && p.getHealth()==20,"Restoring all guard conditions restores melee parrying");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void meleeGuardDoesNotTurnSonicBoomExplosionsOrThornsIntoMelee(GameTestHelper h) {
        var p=player(h);raiseSword(p);p.setYRot(0);var warden=EntityType.WARDEN.create(h.getLevel());warden.setPos(0,100,1);learn(p,warden);
        checkMeleeDamage(h,p,p.damageSources().sonicBoom(warden),"Sonic boom remains a special ranged attack");
        checkMeleeDamage(h,p,p.damageSources().explosion(warden,warden),"Explosions cannot be parried as melee");
        checkMeleeDamage(h,p,p.damageSources().thorns(warden),"Reflected thorns damage is not a melee strike");
        checkMeleeDamage(h,p,p.damageSources().fall(),"Environmental damage does not become parryable");
        check(h,p.getMainHandItem().getDamageValue()==0,"Special attacks never spend melee-parry durability");
        p.invulnerableTime=0;p.setHealth(20);check(h,!p.hurt(p.damageSources().mobAttack(warden),4),"Warden's ordinary melee strike is parryable after learning its species");
        h.succeed();
    }
    private static void raiseSword(ServerPlayer p) {
        if(!p.getMainHandItem().is(AdaptationWheel.EXTERMINATION_SWORD.get()))p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(AdaptationWheel.EXTERMINATION_SWORD.get()));
        p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    private static void learn(ServerPlayer p,LivingEntity mob) {
        var state=AdaptationEvents.state(p);String key=ru.pasha.adaptationwheel.server.DamageCategories.forEntity(mob);state.hit(key,ru.pasha.adaptationwheel.server.DamageCategories.boss(mob));
        while(state.stage(key)<1)state.tick();
    }
    private static void checkMeleeDamage(GameTestHelper h,ServerPlayer p,DamageSource source,String message) {
        p.invulnerableTime=0;p.setHealth(20);check(h,p.hurt(source,4) && p.getHealth()<20,message);
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void displayedMultipliersMatchActualZombieAndWardenRates(GameTestHelper h) {
        for(EntityType<?> type:new EntityType<?>[]{EntityType.ZOMBIE,EntityType.WARDEN}) {
            ServerPlayer p=player(h);var mob=(LivingEntity)type.create(h.getLevel());
            var source=p.damageSources().mobAttack(mob);
            String key=ru.pasha.adaptationwheel.server.DamageCategories.forEntity(mob);
            p.hurt(source,1);tick(p);
            for(int hits=1;hits<=6;hits++) {
                p.hurt(p.damageSources().mobAttack(mob),1);tick(p);
                var state=AdaptationEvents.state(p);
                var e=state.snapshots().stream().filter(v->v.key().equals(key)).findFirst().orElseThrow();
                check(h,e.hits()==hits,"Exactly one bonus per real "+type+" contact");
                if(hits==1||hits==4||hits==6) {
                    double before=e.work();double speed=1+.3*hits;
                    for(int t=0;t<20;t++)tick(p);
                    var after=state.snapshots().stream().filter(v->v.key().equals(key)).findFirst().orElseThrow();
                    check(h,Math.abs(after.work()-before-20*speed)<1e-7,
                        type+" multiplier x"+speed+" advances exactly "+speed+" adaptation seconds per real second");
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void thirtySecondsStayThirtyWhenSpeedChanges(GameTestHelper h) {
        ServerPlayer p=player(h);String key="damage:minecraft:fall";
        var state=AdaptationEvents.state(p);state.hit(key,false);
        for(int i=0;i<800;i++)state.tick(); // Second stage, 400/1000 work: exactly 30 seconds remain.
        var before=state.snapshots().stream().filter(e->e.key().equals(key)).findFirst().orElseThrow();
        check(h,before.remaining()==600,"Fixture has thirty adaptation seconds remaining");
        p.hurt(p.damageSources().fall(),1);
        var contact=state.snapshots().stream().filter(e->e.key().equals(key)).findFirst().orElseThrow();
        check(h,contact.work()==before.work() && contact.remaining()==600,"The hit itself subtracts no time");
        tick(p);
        var faster=state.snapshots().stream().filter(e->e.key().equals(key)).findFirst().orElseThrow();
        check(h,Math.abs(faster.speed()-1.3)<1e-8 && Math.abs(faster.work()-before.work()-1.3)<1e-8,
            "One server tick passes at x1.30; the timer is not divided by that multiplier");
        double initial=faster.work();for(int i=0;i<20;i++)tick(p);
        var elapsed=state.snapshots().stream().filter(e->e.key().equals(key)).findFirst().orElseThrow();
        check(h,Math.abs(elapsed.work()-initial-26)<1e-7,"One real second consumes 1.30 adaptation seconds");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void actualPlayerDamageAndCooldownHitsAccelerate(GameTestHelper h) {
        ServerPlayer p=player(h);
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        var source=p.damageSources().mobAttack(zombie);
        p.hurt(source,1);tick(p);
        check(h,entry(p).running() && entry(p).hits()==0,"First surviving hit starts at x1.00");
        float health=p.getHealth();
        for(int i=1;i<=4;i++) {
            p.hurt(p.damageSources().mobAttack(zombie),1);tick(p);
            check(h,entry(p).hits()==i,"Real cooldown hit must add exactly one bonus: "+i+" actual="+entry(p).hits());
            check(h,Math.abs(entry(p).speed()-(1+.3*i))<1e-8,"Real hit increases timer rate");
        }
        check(h,p.getHealth()==health,"Cooldown hits accelerate without subtracting health");
        p.invulnerableTime=0;p.hurt(source,1);tick(p);
        check(h,entry(p).hits()==5,"A health-damaging hit is counted once across all events");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void actualBossImmuneHitsAccelerateExistence(GameTestHelper h) {
        ServerPlayer p=player(h);var zombie=EntityType.ZOMBIE.create(h.getLevel());
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);
        var state=AdaptationEvents.state(p);state.hit(Z,true);
        while(state.stage(Z)<4)state.tick();
        float health=p.getHealth();
        for(int i=1;i<=3;i++) {
            p.hurt(p.damageSources().mobAttack(zombie),3);tick(p);
            check(h,entry(p).hits()==i,"Fully blocked boss attack accelerates fifth stage once");
        }
        check(h,p.getHealth()==health,"Boss immunity remains intact");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void canceledModContactsStillAccelerateKnownSources(GameTestHelper h) {
        ServerPlayer p=player(h);var zombie=EntityType.ZOMBIE.create(h.getLevel());
        var source=p.damageSources().mobAttack(zombie);
        var canceledFirst=new LivingAttackEvent(p,source,2);canceledFirst.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(canceledFirst);tick(p);
        check(h,AdaptationEvents.state(p).stage(Z)==0 && AdaptationEvents.state(p).snapshots().isEmpty(),
            "Canceled first contact must not grant an adaptation");
        p.hurt(source,1);tick(p);float health=p.getHealth();
        for(int i=1;i<=3;i++) {
            var blocked=new LivingAttackEvent(p,source,2);blocked.setCanceled(true);
            MinecraftForge.EVENT_BUS.post(blocked);tick(p);
            check(h,entry(p).hits()==i,"Known attack blocked by another mod still adds 30 percent");
        }
        check(h,p.getHealth()==health,"Contact bonus does not introduce damage");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void rebuiltDamageSourceIsNotAnotherHit(GameTestHelper h) {
        HookPlayer p=new HookPlayer(h.getLevel());var zombie=EntityType.ZOMBIE.create(h.getLevel());
        var source=p.damageSources().mobAttack(zombie);p.damage(source,1);tick(p);
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(p,source,1));
        p.damage(new DamageSource(source.typeHolder(),source.getDirectEntity(),source.getEntity()),1);tick(p);
        check(h,entry(p).hits()==1,"Rebuilt source in downstream damage hook must not duplicate an attack");
        h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"LiveWheelTest"));
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p);
        for(int i=0;i<60;i++)p.tick(); // Expire vanilla login protection before exercising real damage.
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        return p;
    }
    private static ru.pasha.adaptationwheel.core.AdaptationState.Snapshot entry(ServerPlayer p) {
        return AdaptationEvents.state(p).snapshots().stream().filter(e->e.key().equals(Z)).findFirst().orElseThrow();
    }
    private static void tick(ServerPlayer p) {p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static void check(GameTestHelper h,boolean ok,String message){if(!ok)h.fail(message);}
    private static final class HookPlayer extends net.minecraftforge.common.util.FakePlayer {
        HookPlayer(ServerLevel level){super(level,new GameProfile(UUID.randomUUID(),"HookWheelTest"));setHealth(20);setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));}
        @Override public boolean isInvulnerableTo(DamageSource source){return false;}
        void damage(DamageSource source,float amount){super.actuallyHurt(source,amount);}
    }
}
