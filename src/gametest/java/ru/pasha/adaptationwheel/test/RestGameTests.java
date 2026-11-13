package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

/**
 * Rest adaptations through the real vanilla paths: startSleepInBed (Forge events, the monster check and the coremod hook),
 * ServerPlayerGameMode.useItemOn on a bed in the Nether, the Forge events SleepFinishedTimeEvent / PlayerSpawnPhantomsEvent /
 * PlayerRespawnEvent. Tests that need a particular time of day set it for the length of one synchronous test and restore it.
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestGameTests {
    private static final String BED_KEY="ForeignBed";

    // ---------- day sleep ----------
    @GameTest(template="empty",timeoutTicks=60)
    public static void daySleepRefusedUntilStageTwoThenAllowedAndSpawnSet(GameTestHelper h) {
        var level=h.getLevel();long time=level.getDayTime();
        try{
            at(level,6000);
            var head=bed(level,h.absolutePos(new BlockPos(2,2,2)),false);
            var p=player(h);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_POSSIBLE_NOW,"Vanilla did not refuse sleeping at noon");
            check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.DAY_SLEEP.key),"A refused daytime sleep did not start adaptation");
            learn(p,MetaMechanic.DAY_SLEEP,1);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_POSSIBLE_NOW,"Stage one must still refuse (it only explains)");
            tick(p); // the explanation waits for the next player tick, after vanilla's own message
            learn(p,MetaMechanic.DAY_SLEEP,2);
            p.getStats().setValue(p,Stats.CUSTOM.get(Stats.TIME_SINCE_REST),5000);
            check(h,sleep(p,head).right().isPresent()&&p.isSleeping(),"Stage two did not let the wearer lie down at noon");
            check(h,head.equals(p.getRespawnPosition()),"Sleeping by day did not set the spawn point");
            check(h,p.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST))==0,"Sleeping by day did not reset the rest timer");
            // doTick runs Player.tick, where the time check wakes sleepers (ServerPlayer.tick does not).
            for(int i=0;i<5;i++)p.doTick();
            check(h,p.isSleeping(),"The per-tick time check woke a stage-two sleeper by day");
            check(h,level.getDayTime()==6000,"Lying down changed the time of day");
            AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.DAY_SLEEP.key,false);
            p.doTick();check(h,!p.isSleeping(),"Disabling the adaptation did not wake the sleeper by day");
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_POSSIBLE_NOW,"A disabled adaptation still allows daytime sleep");
            AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.DAY_SLEEP.key,true);
            p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_POSSIBLE_NOW,"Without the wheel daytime sleep was still allowed");
            h.succeed();
        }finally{at(level,time);}
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void daySleepTargetTimeFollowsStage(GameTestHelper h) {
        var level=h.getLevel();long time=level.getDayTime();
        try{
            at(level,6000);
            var head=bed(level,h.absolutePos(new BlockPos(2,2,2)),false);
            var other=bed(level,h.absolutePos(new BlockPos(2,2,9)),false);
            var p=player(h);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);learn(p,MetaMechanic.DAY_SLEEP,2);
            check(h,sleep(p,head).right().isPresent(),"Stage two could not sleep by day");
            check(h,finish(level,p)==6000,"Stage two changed the time by day: "+finish(level,p));
            var q=player(h);q.setPos(other.getX()+.5,other.getY(),other.getZ()+.5);learn(q,MetaMechanic.DAY_SLEEP,3);
            check(h,sleep(q,other).right().isPresent(),"Stage three could not sleep by day");
            long evening=finish(level,p,q);
            check(h,evening>6000&&evening<30000,"The highest stage among sleepers did not win: "+evening);
            at(level,evening);check(h,!level.isDay(),"Stage three target is still day");
            at(level,evening-1);check(h,level.isDay(),"Stage three target is not the first tick vanilla allows sleeping");
            at(level,6000);
            // Stage four: no Shift goes to the evening, Shift to the vanilla morning.
            learn(q,MetaMechanic.DAY_SLEEP,4);p.stopSleepInBed(true,false);
            check(h,finish(level,q)==evening,"Stage four without Shift did not choose the evening");
            q.stopSleepInBed(true,false);q.setShiftKeyDown(true);
            check(h,sleep(q,other).right().isPresent(),"Stage four could not sleep by day");
            check(h,finish(level,q)==24000,"Stage four with Shift did not choose the next morning: "+finish(level,q));
            q.setShiftKeyDown(false);q.stopSleepInBed(true,false);
            // Sleeping through a night stays vanilla.
            at(level,14000);q.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            check(h,sleep(q,other).right().isPresent(),"Vanilla night sleep broke");
            check(h,finish(level,q)==24000,"The adaptation changed a night's sleep");
            h.succeed();
        }finally{at(level,time);}
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void nightSleepPastDawnStaysVanilla(GameTestHelper h) {
        var level=h.getLevel();long time=level.getDayTime();
        try{
            var head=bed(level,h.absolutePos(new BlockPos(2,2,2)),false);
            var p=player(h);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);learn(p,MetaMechanic.DAY_SLEEP,4);
            // Lying down in the last seconds of the night: the sleep finishes after dawn.
            at(level,23400);
            check(h,!level.isDay()&&sleep(p,head).right().isPresent(),"Could not lie down at the end of the night");
            at(level,23600);check(h,level.isDay(),"23600 is not day");
            check(h,finish(level,p)==24000,"A night sleep that ended after dawn skipped to "+finish(level,p)+" instead of the morning");
            p.doTick();check(h,!p.isSleeping(),"A night sleeper was not woken at dawn as in vanilla");
            // A sleep begun by day is still kept.
            at(level,6000);
            check(h,sleep(p,head).right().isPresent(),"Stage four could not sleep by day");
            p.doTick();check(h,p.isSleeping(),"A day sleeper was woken");
            check(h,finish(level,p)>6000&&finish(level,p)<24000,"A day sleep did not go to the evening");
            h.succeed();
        }finally{at(level,time);}
    }

    // ---------- sleeping near monsters ----------
    @GameTest(template="empty",timeoutTicks=60)
    public static void monstersNearBedCountByStage(GameTestHelper h) {
        var level=h.getLevel();long time=level.getDayTime();Zombie zombie=null;
        try{
            at(level,14000);
            var head=bed(level,h.absolutePos(new BlockPos(2,2,2)),false);
            var p=player(h);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);
            zombie=EntityType.ZOMBIE.create(level);zombie.setNoAi(true);zombie.setPersistenceRequired();place(zombie,p,4);level.addFreshEntity(zombie);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_SAFE,"A zombie 4 blocks away did not stop vanilla sleep");
            check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.RESTLESS.key),"A refusal because of monsters did not start adaptation");
            check(h,!zombie.hasEffect(MobEffects.GLOWING),"The zombie glowed before stage one");
            learn(p,MetaMechanic.RESTLESS,1);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_SAFE,"Stage one stopped refusing");
            check(h,zombie.hasEffect(MobEffects.GLOWING),"Stage one did not make the blocking monster glow");
            learn(p,MetaMechanic.RESTLESS,2);zombie.removeEffect(MobEffects.GLOWING);
            place(zombie,p,7);
            check(h,sleep(p,head).right().isPresent(),"Stage two still counted a monster 7 blocks away");wake(p);
            place(zombie,p,4);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_SAFE,"Stage two ignored a monster 4 blocks away");
            learn(p,MetaMechanic.RESTLESS,3);
            check(h,sleep(p,head).right().isPresent(),"Stage three still counted a monster 4 blocks away");wake(p);
            place(zombie,p,2);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_SAFE,"Stage three ignored a monster 2 blocks away");
            learn(p,MetaMechanic.RESTLESS,4);
            check(h,sleep(p,head).right().isPresent(),"Stage four was stopped by a monster");wake(p);
            AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.RESTLESS.key,false);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_SAFE,"A disabled adaptation still ignored the monster");
            AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.RESTLESS.key,true);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            check(h,sleep(p,head).left().orElse(null)==Player.BedSleepingProblem.NOT_SAFE,"Without the wheel the monster was ignored");
            h.succeed();
        }finally{if(zombie!=null)zombie.discard();at(level,time);}
    }

    // ---------- insomnia ----------
    @GameTest(template="empty",timeoutTicks=60)
    public static void phantomsWaitForLongerInsomnia(GameTestHelper h) {
        var p=player(h);
        rest(p,50000);check(h,phantoms(p)==Event.Result.DEFAULT,"Phantoms event was touched below the vanilla threshold");
        check(h,!AdaptationEvents.state(p).pending().containsKey(MetaMechanic.INSOMNIA.key),"Short insomnia started adaptation");
        rest(p,80000);check(h,phantoms(p)==Event.Result.DEFAULT,"Stage zero changed the phantom decision");
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.INSOMNIA.key),"Three sleepless days did not start adaptation");
        learn(p,MetaMechanic.INSOMNIA,1);check(h,phantoms(p)==Event.Result.DEFAULT,"Stage one changed the phantom decision");
        learn(p,MetaMechanic.INSOMNIA,2);
        rest(p,80000);check(h,phantoms(p)==Event.Result.DENY,"Stage two allowed phantoms before 4 days");
        rest(p,100000);check(h,phantoms(p)==Event.Result.DEFAULT,"Stage two denied phantoms after 4 days");
        learn(p,MetaMechanic.INSOMNIA,3);
        rest(p,100000);check(h,phantoms(p)==Event.Result.DENY,"Stage three allowed phantoms before 5 days");
        rest(p,130000);check(h,phantoms(p)==Event.Result.DEFAULT,"Stage three denied phantoms after 5 days");
        learn(p,MetaMechanic.INSOMNIA,4);
        rest(p,500000);check(h,phantoms(p)==Event.Result.DENY,"Stage four allowed phantoms");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.INSOMNIA.key,false);
        check(h,phantoms(p)==Event.Result.DEFAULT,"A disabled adaptation still denied phantoms");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.INSOMNIA.key,true);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,phantoms(p)==Event.Result.DEFAULT,"Without the wheel phantoms were still denied");
        h.succeed();
    }

    // ---------- a bed in the Nether ----------
    @GameTest(template="empty",timeoutTicks=60)
    public static void netherBedExplodesAsVanillaUntilAdapted(GameTestHelper h) {
        var nether=nether(h);var head=bed(nether,new BlockPos(1000,100,1000),true);
        var p=player(h,nether);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);
        click(p,nether,head);
        check(h,!(nether.getBlockState(head).getBlock() instanceof BedBlock),"A Nether bed did not explode for an unadapted player");
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.FOREIGN_BED.key),"The explosion did not start adaptation");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void netherBedStageOneWarnsFirst(GameTestHelper h) {
        var nether=nether(h);var head=bed(nether,new BlockPos(1032,100,1000),true);
        var p=player(h,nether);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);learn(p,MetaMechanic.FOREIGN_BED,1);
        click(p,nether,head);
        check(h,nether.getBlockState(head).getBlock() instanceof BedBlock,"The first click exploded the bed at stage one");
        click(p,nether,head);
        check(h,!(nether.getBlockState(head).getBlock() instanceof BedBlock),"The second click within 5 seconds did not explode the bed");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void netherBedStageTwoRemovesBedWithoutBlockDamage(GameTestHelper h) {
        var nether=nether(h);var head=bed(nether,new BlockPos(1064,100,1000),true);
        var p=player(h,nether);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);learn(p,MetaMechanic.FOREIGN_BED,2);
        click(p,nether,head);
        check(h,!(nether.getBlockState(head).getBlock() instanceof BedBlock)&&!(nether.getBlockState(head.south()).getBlock() instanceof BedBlock),"Stage two left a half of the bed");
        check(h,nether.getBlockState(head.below()).is(Blocks.NETHERRACK)&&nether.getBlockState(head.east()).isAir(),"The stage two blast damaged blocks");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void netherBedStageThreeJustRefuses(GameTestHelper h) {
        var nether=nether(h);var head=bed(nether,new BlockPos(1096,100,1000),true);
        var p=player(h,nether);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);learn(p,MetaMechanic.FOREIGN_BED,3);
        click(p,nether,head);
        check(h,nether.getBlockState(head).getBlock() instanceof BedBlock&&nether.getBlockState(head.below()).is(Blocks.NETHERRACK),"Stage three exploded the bed");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.FOREIGN_BED.key,false);
        click(p,nether,head);
        check(h,!(nether.getBlockState(head).getBlock() instanceof BedBlock),"A disabled adaptation still protected the bed");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void netherBedStageFourIsPersonalRespawnPoint(GameTestHelper h) {
        var nether=nether(h);var head=bed(nether,new BlockPos(1128,100,1000),true);
        var p=player(h,nether);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);learn(p,MetaMechanic.FOREIGN_BED,4);
        click(p,nether,head);
        check(h,nether.getBlockState(head).getBlock() instanceof BedBlock&&MetaServer.meta(p).contains(BED_KEY),"Stage four did not remember the bed");
        var far=new Vec3(head.getX()+60.5,head.getY(),head.getZ()+.5);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.setPos(far);respawn(p);
        check(h,p.distanceToSqr(far)<1,"A player without the wheel was moved to the bed");
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));respawn(p);
        check(h,p.level()==nether&&p.distanceToSqr(Vec3.atCenterOf(head))<16,"The respawn did not bring the wearer to the bed: "+p.position());
        p.setRespawnPosition(Level.OVERWORLD,new BlockPos(0,70,0),0f,true,false);
        check(h,!MetaServer.meta(p).contains(BED_KEY),"An ordinary spawn point did not replace the wheel's");
        click(p,nether,head);check(h,MetaServer.meta(p).contains(BED_KEY),"The bed was not remembered again");
        nether.removeBlock(head,false);p.setPos(far);respawn(p);
        check(h,!MetaServer.meta(p).contains(BED_KEY)&&p.distanceToSqr(far)<1,"A destroyed bed was not forgotten");
        h.succeed();
    }
    /**
     * PlayerList.respawn step by step: Clone fires from restoreFrom while the new player still has a fresh entity id (and
     * Entity equality is the id), setId follows, then the old overworld spawn is set again before the respawn event.
     */
    @GameTest(template="empty",timeoutTicks=60)
    public static void netherBedSurvivesTheRealRespawnSequence(GameTestHelper h) {
        var nether=nether(h);var head=bed(nether,new BlockPos(1160,100,1000),true);
        var p=player(h,nether);p.setPos(head.getX()+.5,head.getY(),head.getZ()+.5);learn(p,MetaMechanic.FOREIGN_BED,4);
        p.setRespawnPosition(Level.OVERWORLD,h.absolutePos(new BlockPos(1,2,1)),0f,false,false); // a night in the overworld first
        click(p,nether,head);check(h,MetaServer.meta(p).contains(BED_KEY),"Stage four did not remember the bed");
        var replacement=new ServerPlayer(p.server,nether,p.getGameProfile());replacement.connection=p.connection;
        check(h,replacement.getId()!=p.getId(),"The new player already had the old entity id");
        replacement.restoreFrom(p,false);
        replacement.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get())); // keepInventory
        replacement.setId(p.getId());
        replacement.setRespawnPosition(p.getRespawnDimension(),p.getRespawnPosition(),p.getRespawnAngle(),p.isRespawnForced(),false);
        check(h,MetaServer.meta(replacement).contains(BED_KEY),"Re-applying the old spawn during the respawn forgot the wheel's bed");
        replacement.setPos(head.getX()+60.5,head.getY(),head.getZ()+.5);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(replacement,false));
        check(h,replacement.distanceToSqr(Vec3.atCenterOf(head))<16,"The respawn did not bring the wearer to the bed: "+replacement.position());
        replacement.setRespawnPosition(Level.OVERWORLD,h.absolutePos(new BlockPos(3,2,3)),0f,false,false);
        check(h,!MetaServer.meta(replacement).contains(BED_KEY),"A spawn set after the respawn did not replace the wheel's bed");
        h.succeed();
    }

    // ---------- helpers ----------
    /** Sets the time of day and the sky darkness derived from it, as ServerLevel.tick does. */
    private static void at(ServerLevel level,long time){level.setDayTime(time);level.updateSkyBrightness();}
    private static BlockPos bed(ServerLevel level,BlockPos head,boolean nether){
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            level.setBlock(head.offset(dx,-1,dz),(nether?Blocks.NETHERRACK:Blocks.STONE).defaultBlockState(),2);
            for(int dy=0;dy<=3;dy++)level.setBlock(head.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
        }
        var state=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
        level.setBlock(head,state.setValue(BedBlock.PART,BedPart.HEAD),2);level.setBlock(head.south(),state.setValue(BedBlock.PART,BedPart.FOOT),2);
        return head;
    }
    private static com.mojang.datafixers.util.Either<Player.BedSleepingProblem,net.minecraft.util.Unit> sleep(ServerPlayer p,BlockPos head){return p.startSleepInBed(head);}
    private static void wake(ServerPlayer p){p.stopSleepInBed(true,false);}
    /** The level's own end-of-sleep time with the sleepers in its player list for that one call. */
    private static long finish(ServerLevel level,ServerPlayer... sleepers){
        var players=level.players();players.addAll(Arrays.asList(sleepers));
        try{long now=level.getDayTime(),next=now+24000;return ForgeEventFactory.onSleepFinished(level,next-next%24000,now);}
        finally{for(var p:sleepers)players.remove(p);}
    }
    private static void place(Zombie zombie,ServerPlayer p,double distance){zombie.setPos(p.getX()+distance,p.getY(),p.getZ());}
    private static void rest(ServerPlayer p,int ticks){p.getStats().setValue(p,Stats.CUSTOM.get(Stats.TIME_SINCE_REST),ticks);}
    private static Event.Result phantoms(ServerPlayer p){var event=new PlayerSpawnPhantomsEvent(p,1);MinecraftForge.EVENT_BUS.post(event);return event.getResult();}
    private static void click(ServerPlayer p,ServerLevel level,BlockPos pos){
        p.gameMode.useItemOn(p,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
    private static void respawn(ServerPlayer p){MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(p,false));}
    private static ServerLevel nether(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(Level.NETHER);
        if(level==null)h.fail("The Nether is not loaded in this GameTest server");
        return level;
    }
    private static void learn(ServerPlayer p,MetaMechanic m,int stage){
        AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);
        int guard=40000;while(AdaptationEvents.state(p).stage(m.key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+m.key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static void tick(ServerPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static ServerPlayer player(GameTestHelper h){return player(h,h.getLevel());}
    private static ServerPlayer player(GameTestHelper h,ServerLevel level){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),level,new GameProfile(UUID.randomUUID(),"RestTest"));
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p);
        for(int i=0;i<60;i++)p.tick(); // Expire vanilla login protection before exercising real damage.
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        return p;
    }
}
