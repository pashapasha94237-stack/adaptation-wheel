package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OutsideGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void fullConsoleAdaptationRefusesKill(GameTestHelper h) {
        var p=player(h);learn(p,MetaMechanic.COMMANDS,4);
        p.kill();check(h,p.isAlive()&&p.getHealth()==20,"/kill went through a full console-command adaptation");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.COMMANDS.key,false);
        p.kill();check(h,p.getHealth()<=0,"Disabled adaptation still blocks /kill");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void cheatCommandStartsAdaptation(GameTestHelper h) {
        var p=player(h);
        h.getLevel().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"time set day");
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.COMMANDS.key),"A cheat command typed by the wearer did not start adaptation");
        var q=player(h);
        h.getLevel().getServer().getCommands().performPrefixedCommand(q.createCommandSourceStack().withPermission(2),"say hi");
        check(h,!AdaptationEvents.state(q).pending().containsKey(MetaMechanic.COMMANDS.key),"An ordinary command counted as a cheat");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void idleWearerIsHiddenFromMobsUntilMoving(GameTestHelper h) {
        var p=player(h);learn(p,MetaMechanic.AFK,4);
        for(int i=0;i<OutsideServer.AFK_SHIELD_AFTER+40;i++)tick(p);
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setPos(p.getX()+2,p.getY(),p.getZ());h.getLevel().addFreshEntity(zombie);
        zombie.setTarget(p);check(h,zombie.getTarget()==null,"A mob picked an idle fully adapted wearer as its target");
        p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(zombie),4);check(h,p.getHealth()==20,"A mob hurt an idle fully adapted wearer");
        p.setPos(p.getX()+3,p.getY(),p.getZ());for(int i=0;i<40;i++)tick(p);
        p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(zombie),4);check(h,p.getHealth()<20,"Idle protection survived the player moving");
        zombie.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void openInventoryInCombat(GameTestHelper h) {
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        var half=player(h);learn(half,MetaMechanic.INVENTORY,2);
        OutsideServer.inventory(half,true);half.invulnerableTime=0;half.hurt(half.damageSources().mobAttack(zombie),4);
        var control=player(h);control.invulnerableTime=0;control.hurt(control.damageSources().mobAttack(zombie),4);
        float plain=20-control.getHealth();
        check(h,plain>0&&Math.abs((20-half.getHealth())-plain/2)<.01,"Stage two did not halve mob damage with an open menu: "+(20-half.getHealth())+" of "+plain);
        var full=player(h);learn(full,MetaMechanic.INVENTORY,4);
        full.invulnerableTime=0;full.hurt(full.damageSources().mobAttack(zombie),4);check(h,full.getHealth()<20,"Protection without an open menu");
        full.setHealth(20);OutsideServer.inventory(full,true);full.invulnerableTime=0;full.hurt(full.damageSources().mobAttack(zombie),4);
        check(h,full.getHealth()==20,"Full adaptation did not protect while the inventory was open");
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(full,zombie));
        full.invulnerableTime=0;full.hurt(full.damageSources().mobAttack(zombie),4);check(h,full.getHealth()<20,"Attacking a mob kept the menu protection");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=500)
    public static void reopeningInventoryDoesNotExtendProtection(GameTestHelper h) {
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        var p=player(h);learn(p,MetaMechanic.INVENTORY,4);
        OutsideServer.inventory(p,true);
        h.runAfterDelay(OutsideServer.MENU_SHIELD+5,()->{
            OutsideServer.inventory(p,false);OutsideServer.inventory(p,true);
            p.setHealth(20);p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(zombie),4);
            check(h,p.getHealth()<20,"Closing and reopening within 30 seconds started a new protection window");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void logLevelChecksAreNotErrors(GameTestHelper h) {
        var log=org.apache.logging.log4j.LogManager.getLogger("OutsideGameTestLevelCheck");
        LogWatch.installNow();LogWatch.serverWants(true);
        long errors=LogWatch.totalErrors();int hidden=LogWatch.suppressed();
        for(int i=0;i<20;i++)log.isErrorEnabled();
        log.error("OutsideGameTest guarded error");
        check(h,LogWatch.totalErrors()==errors+1,"Level checks were counted as errors: "+(LogWatch.totalErrors()-errors));
        check(h,LogWatch.suppressed()==hidden,"A real error after level checks was trimmed");
        for(int i=0;i<10;i++)log.error("OutsideGameTest repeated {}",i);
        check(h,LogWatch.suppressed()==hidden+7,"Identical patterned errors were not trimmed after three: "+(LogWatch.suppressed()-hidden));
        LogWatch.serverWants(false);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void worldSlashCutsThroughWallsAlongItsPlane(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.setYRot(0);p.setXRot(0);p.yHeadRot=0;
        for(int x=-3;x<=3;x++)for(int y=0;y<4;y++)level.setBlockAndUpdate(base.offset(x,y,5),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var behindWall=mob(h,base.getX()+.5,base.getY(),base.getZ()+12.5);
        var farAhead=mob(h,base.getX()+.5,base.getY(),base.getZ()+20.5);
        var behind=mob(h,base.getX()+.5,base.getY(),base.getZ()-6.5);
        var aside=mob(h,base.getX()+16.5,base.getY(),base.getZ()+12.5);
        var wolf=EntityType.WOLF.create(level);wolf.setPos(base.getX()+.5,base.getY(),base.getZ()+8.5);wolf.tame(p);level.addFreshEntity(wolf);
        float wolfHealth=wolf.getHealth();
        int hit=WorldSlash.cut(p,level,WorldSlash.plane(p));
        check(h,!behindWall.isAlive()&&!farAhead.isAlive(),"The slash did not cut through the wall along its length");
        check(h,behind.isAlive()&&aside.isAlive(),"The slash hit something behind the swing or beyond its span");
        check(h,wolf.isAlive()&&wolf.getHealth()==wolfHealth,"The slash hurt the caster's own pet");
        check(h,hit==2,"Unexpected number of cut targets: "+hit);
        check(h,WorldSlash.COOLDOWN==45*20&&WorldSlash.LENGTH>=64,"World Slash cooldown or length is not the requested one");
        for(var e:List.of(behind,aside,wolf))e.discard();
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void worldSlashDealsExactly150Hearts(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.setYRot(0);p.setXRot(0);p.yHeadRot=0;
        var target=mob(h,base.getX()+.5,base.getY(),base.getZ()+10.5);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(30);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,200,2));
        target.setAbsorptionAmount(10);
        int hit=WorldSlash.cut(p,level,WorldSlash.plane(p));
        check(h,hit==1&&target.getHealth()==700,"World Slash must take exactly 300 HP (150 hearts) through armour and resistance, took "+(1000-target.getHealth()));
        check(h,target.getAbsorptionAmount()==10,"World Slash used up absorption instead of cutting health");
        target.discard();
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void worldSlashCutsBlocksButSparesProtectedOnes(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.setYRot(0);p.setXRot(0);p.yHeadRot=0;
        var stone=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for(int x=-5;x<=5;x++)for(int y=-3;y<=7;y++)level.setBlockAndUpdate(base.offset(x,y,7),stone);
        var plane=WorldSlash.plane(p);
        double[] c=plane.point(7.5,0),edge=plane.point(7.5,3.5);
        var chestPos=net.minecraft.core.BlockPos.containing(c[0],c[1],c[2]);var bedrockPos=net.minecraft.core.BlockPos.containing(edge[0],edge[1],edge[2]);
        level.setBlockAndUpdate(chestPos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(bedrockPos,net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
        int before=0;for(int x=-5;x<=5;x++)for(int y=-3;y<=7;y++)if(level.getBlockState(base.offset(x,y,7)).is(net.minecraft.world.level.block.Blocks.STONE))before++;
        int stoneBefore=before;
        WorldSlash.travel(p,plane,true);
        h.runAfterDelay(12,()->{
            int after=0;for(int x=-5;x<=5;x++)for(int y=-3;y<=7;y++)if(level.getBlockState(base.offset(x,y,7)).is(net.minecraft.world.level.block.Blocks.STONE))after++;
            check(h,stoneBefore-after>=8,"The slash did not cut a line through the wall: "+(stoneBefore-after)+" blocks");
            check(h,stoneBefore-after<=40,"The slash removed far more than a thin cut: "+(stoneBefore-after)+" blocks");
            check(h,level.getBlockState(chestPos).is(net.minecraft.world.level.block.Blocks.CHEST),"A container was cut");
            check(h,level.getBlockState(bedrockPos).is(net.minecraft.world.level.block.Blocks.BEDROCK),"An unbreakable block was cut");
            h.succeed();
        });
    }
    private static net.minecraft.world.entity.monster.Zombie mob(GameTestHelper h,double x,double y,double z){
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setPos(x,y,z);zombie.setNoAi(true);h.getLevel().addFreshEntity(zombie);return zombie;
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void outsideEventsReachEachWearerOnce(GameTestHelper h) {
        var p=player(h);AtomicInteger shown=new AtomicInteger();
        OutsideServer.post(MetaMechanic.RENAME,stage->{shown.incrementAndGet();return List.of(Component.literal("renamed"));});
        for(int i=0;i<60;i++)tick(p);
        check(h,shown.get()==1,"An outside event was shown "+shown.get()+" times");
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.RENAME.key),"An outside event did not start adaptation");
        var bare=player(h);bare.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        for(int i=0;i<40;i++)tick(bare);
        check(h,shown.get()==1&&!AdaptationEvents.state(bare).pending().containsKey(MetaMechanic.RENAME.key),"An event reached a player without the wheel");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void changelogLineFollowsTheRelease(GameTestHelper h) {
        check(h,OutsideServer.release("4.29.1-trained-online").equals("4.29.1"),"A build variant hid the changelog line of its release");
        check(h,OutsideServer.release("4.29.1-untrained").equals("4.29.1"),"An untrained build lost its changelog line");
        check(h,OutsideServer.release("4.20.1-offline").equals("4.20.1")&&OutsideServer.release("4.29.1").equals("4.29.1"),"An older version string lost its changelog line");
        h.succeed();
    }
    private static void learn(ServerPlayer p,MetaMechanic m,int stage){
        AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);
        int guard=40000;while(AdaptationEvents.state(p).stage(m.key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+m.key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static void tick(ServerPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static ServerPlayer player(GameTestHelper h){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"OutsideTest"));
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p);
        for(int i=0;i<60;i++)p.tick(); // Expire vanilla login protection before exercising real damage.
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        return p;
    }
}