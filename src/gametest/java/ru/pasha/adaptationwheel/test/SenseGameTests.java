package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

/** Server-side triggers of the sense adaptations. Fog, outlines and the battery HUD are client-only and not covered here. */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SenseGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void eyesInWaterStartMurkyWater(GameTestHelper h) {
        var site=h.absolutePos(BlockPos.ZERO);
        withFluid(h,site,Blocks.WATER,()->{
            var wet=submerged(h,site);for(int i=0;i<25;i++)tick(wet);
            check(h,wet.isEyeInFluid(net.minecraft.tags.FluidTags.WATER),"Test setup: eyes are not in water");
            check(h,AdaptationEvents.state(wet).pending().containsKey(MetaMechanic.MURKY.key),"Eyes in water did not start murky water");
            var bare=submerged(h,site);bare.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);for(int i=0;i<25;i++)tick(bare);
            check(h,!AdaptationEvents.state(bare).pending().containsKey(MetaMechanic.MURKY.key),"Murky water started without the wheel");
            var off=submerged(h,site);learn(off,MetaMechanic.MURKY.key,AdaptationProfile.ENVIRONMENT,1);AdaptationEvents.state(off).clearPending(); // a finished stage keeps running into the next one
            AdaptationEvents.state(off).setCategoryEnabled(MetaMechanic.MURKY.key,false);for(int i=0;i<25;i++)tick(off);
            check(h,!AdaptationEvents.state(off).pending().containsKey(MetaMechanic.MURKY.key),"A disabled murky water entry kept learning");
            var on=submerged(h,site);learn(on,MetaMechanic.MURKY.key,AdaptationProfile.ENVIRONMENT,1);AdaptationEvents.state(on).clearPending();for(int i=0;i<25;i++)tick(on);
            check(h,AdaptationEvents.state(on).pending().containsKey(MetaMechanic.MURKY.key),"Murky water did not continue towards the next stage");
        });
        var dry=player(h);dry.setPos(site.getX()+.5,site.getY()+30,site.getZ()+.5);dry.tick();for(int i=0;i<25;i++)tick(dry);
        check(h,!AdaptationEvents.state(dry).pending().containsKey(MetaMechanic.MURKY.key),"Dry land started murky water");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lavaSightNeedsHarmlessLava(GameTestHelper h) {
        var site=h.absolutePos(BlockPos.ZERO);
        withFluid(h,site,Blocks.LAVA,()->{
            var burning=submerged(h,site);for(int i=0;i<25;i++)tick(burning);
            check(h,burning.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA),"Test setup: eyes are not in lava");
            check(h,!AdaptationEvents.state(burning).pending().containsKey(MetaMechanic.LAVA_SIGHT.key),"Dangerous lava started lava sight");
            var resistant=submerged(h,site);resistant.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,2000));for(int i=0;i<25;i++)tick(resistant);
            check(h,AdaptationEvents.state(resistant).pending().containsKey(MetaMechanic.LAVA_SIGHT.key),"Fire Resistance in lava did not start lava sight");
            var half=submerged(h,site);learn(half,SenseAdaptations.LAVA_KEY,AdaptationProfile.FIRE,4);for(int i=0;i<25;i++)tick(half);
            check(h,!AdaptationEvents.state(half).pending().containsKey(MetaMechanic.LAVA_SIGHT.key),"Lava immunity alone (burning still hurts) started lava sight");
            var adapted=submerged(h,site);learn(adapted,SenseAdaptations.LAVA_KEY,AdaptationProfile.FIRE,4);learn(adapted,SenseAdaptations.FIRE_KEY,AdaptationProfile.FIRE,4);
            for(int i=0;i<25;i++)tick(adapted);
            check(h,AdaptationEvents.state(adapted).pending().containsKey(MetaMechanic.LAVA_SIGHT.key),"A wearer fully adapted to lava and burning did not start lava sight");
            var disabled=submerged(h,site);disabled.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,2000));learn(disabled,MetaMechanic.LAVA_SIGHT.key,AdaptationProfile.ENVIRONMENT,1);AdaptationEvents.state(disabled).clearPending();
            AdaptationEvents.state(disabled).setCategoryEnabled(MetaMechanic.LAVA_SIGHT.key,false);for(int i=0;i<25;i++)tick(disabled);
            check(h,!AdaptationEvents.state(disabled).pending().containsKey(MetaMechanic.LAVA_SIGHT.key),"A disabled lava sight entry kept learning");
        });
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void invisibleAttackerStartsUnseenFoes(GameTestHelper h) {
        var hidden=zombie(h);hidden.setInvisible(true);
        var p=player(h);p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(hidden),2);
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.UNSEEN.key),"An invisible mob hitting the wearer did not start unseen foes");
        var seen=zombie(h);var q=player(h);q.invulnerableTime=0;q.hurt(q.damageSources().mobAttack(seen),2);
        check(h,!AdaptationEvents.state(q).pending().containsKey(MetaMechanic.UNSEEN.key),"A visible mob started unseen foes");
        var bare=player(h);bare.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);bare.invulnerableTime=0;bare.hurt(bare.damageSources().mobAttack(hidden),2);
        check(h,!AdaptationEvents.state(bare).pending().containsKey(MetaMechanic.UNSEEN.key),"Unseen foes started without the wheel");
        var off=player(h);learn(off,MetaMechanic.UNSEEN.key,AdaptationProfile.ENVIRONMENT,1);AdaptationEvents.state(off).clearPending();AdaptationEvents.state(off).setCategoryEnabled(MetaMechanic.UNSEEN.key,false);
        off.invulnerableTime=0;off.hurt(off.damageSources().mobAttack(hidden),2);
        check(h,!AdaptationEvents.state(off).pending().containsKey(MetaMechanic.UNSEEN.key),"A disabled unseen foes entry kept learning");
        var skeleton=EntityType.SKELETON.create(h.getLevel());skeleton.setNoAi(true);skeleton.setInvisible(true);h.getLevel().addFreshEntity(skeleton);
        var shot=player(h);shot.invulnerableTime=0;shot.hurt(shot.damageSources().mobAttack(skeleton),2);
        check(h,AdaptationEvents.state(shot).pending().containsKey(MetaMechanic.UNSEEN.key),"Any invisible mob (not only zombies) should start unseen foes");
        hidden.discard();seen.discard();skeleton.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void batteryReportStartsAdaptationAndTestPropertyParses(GameTestHelper h) {
        // MetaNetwork routes the client's "laptop_battery" action to MetaServer.expose.
        var p=player(h);MetaServer.expose(p,MetaMechanic.BATTERY);
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.BATTERY.key),"A battery report did not start the adaptation");
        var bare=player(h);bare.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);MetaServer.expose(bare,MetaMechanic.BATTERY);
        check(h,!AdaptationEvents.state(bare).pending().containsKey(MetaMechanic.BATTERY.key),"A battery report counted without the wheel");
        var off=player(h);learn(off,MetaMechanic.BATTERY.key,AdaptationProfile.ENVIRONMENT,1);AdaptationEvents.state(off).clearPending();AdaptationEvents.state(off).setCategoryEnabled(MetaMechanic.BATTERY.key,false);
        MetaServer.expose(off,MetaMechanic.BATTERY);
        check(h,!AdaptationEvents.state(off).pending().containsKey(MetaMechanic.BATTERY.key),"A disabled battery entry kept learning");
        check(h,SenseAdaptations.fakeCharge("37")==37&&SenseAdaptations.fakeCharge(" 12.4 ")==12,"The fake battery property is not read as a percentage");
        check(h,SenseAdaptations.fakeCharge("250")==100&&SenseAdaptations.fakeCharge("-3")==0,"The fake battery charge is not clamped to 0..100");
        check(h,SenseAdaptations.fakeCharge(null)==-1&&SenseAdaptations.fakeCharge("")==-1&&SenseAdaptations.fakeCharge("abc")==-1&&SenseAdaptations.fakeCharge("NaN")==-1,"An absent or invalid fake battery value counted");
        check(h,SenseAdaptations.runsOnBattery(false,true)&&!SenseAdaptations.runsOnBattery(true,true)&&!SenseAdaptations.runsOnBattery(false,false)&&!SenseAdaptations.runsOnBattery(true,false),
            "Only a discharge off the mains is running on battery");
        h.succeed();
    }

    /** Fills a 3x7x3 column (the water/lava the test player stands in) and always clears it again. */
    private static void withFluid(GameTestHelper h,BlockPos site,Block fluid,Runnable body){
        var level=h.getLevel();var placed=new ArrayList<BlockPos>();
        try{
            for(int x=-1;x<=1;x++)for(int y=1;y<=7;y++)for(int z=-1;z<=1;z++){var pos=site.offset(x,y,z);placed.add(pos);level.setBlock(pos,fluid.defaultBlockState(),2);}
            body.run();
        }finally{for(var pos:placed)level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
    }
    private static ServerPlayer submerged(GameTestHelper h,BlockPos site){
        var p=player(h);p.setInvulnerable(true);
        p.setPos(site.getX()+.5,site.getY()+2,site.getZ()+.5);p.setDeltaMovement(0,0,0);
        p.doTick(); // vanilla's full player tick (ServerPlayer.tick skips it) fills in fluidOnEyes, which the trigger reads
        return p;
    }
    private static net.minecraft.world.entity.monster.Zombie zombie(GameTestHelper h){
        var z=EntityType.ZOMBIE.create(h.getLevel());z.setPos(0,100,2);z.setNoAi(true);h.getLevel().addFreshEntity(z);return z;
    }
    private static void learn(ServerPlayer p,String key,AdaptationProfile profile,int stage){
        AdaptationEvents.state(p).hit(key,false,profile);
        int guard=40000;while(AdaptationEvents.state(p).stage(key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static void tick(ServerPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static ServerPlayer player(GameTestHelper h){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"SenseTest"));
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p);
        for(int i=0;i<60;i++)p.tick(); // Expire vanilla login protection before exercising real damage.
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        return p;
    }
}
