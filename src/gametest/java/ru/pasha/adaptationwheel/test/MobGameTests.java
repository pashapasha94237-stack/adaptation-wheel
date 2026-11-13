package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

/**
 * Mob adaptations. The gaze check goes through Forge's own ForgeHooks.shouldSuppressEnderManAnger (what
 * EnderMan.isLookingAtMe calls), piglin neutrality through PiglinAi.isWearingGold, targeting through
 * Mob.setTarget / ForgeHooks.onLivingChangeTarget, trades through Merchant.openTradingScreen and the player's real
 * closeContainer, and melee through Player.attack.
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobGameTests {
    // ---------- enderman ----------
    private static boolean masked(ServerPlayer p,EnderMan e){return ForgeHooks.shouldSuppressEnderManAnger(e,p,p.getInventory().armor.get(3));}
    private static void place(Entity e,Player p,double distance){e.setPos(p.getX()+distance,p.getY(),p.getZ());}
    @GameTest(template="empty",timeoutTicks=100)
    public static void endermanGazeIsIgnoredBeyondTheStageRange(GameTestHelper h) {
        var em=EntityType.ENDERMAN.create(h.getLevel());
        var none=player(h);place(em,none,100);check(h,!masked(none,em),"A wearer with no adaptation was hidden from the gaze");
        for(int[] c:new int[][]{{1,60,40},{2,30,20},{3,10,6}}){
            var p=player(h);learn(p,MetaMechanic.ENDERMAN,c[0]);
            place(em,p,c[1]);check(h,masked(p,em),"Stage "+c[0]+" did not hide the gaze at "+c[1]+" blocks");
            place(em,p,c[2]);check(h,!masked(p,em),"Stage "+c[0]+" hid the gaze at only "+c[2]+" blocks");
        }
        var full=player(h);learn(full,MetaMechanic.ENDERMAN,4);place(em,full,1);
        check(h,masked(full,em),"Stage four did not hide the gaze up close");
        em.setLastHurtByMob(full);check(h,!masked(full,em),"An enderman the wearer hit was still hidden from the gaze");
        em.setLastHurtByMob(null);
        AdaptationEvents.state(full).setCategoryEnabled(MetaMechanic.ENDERMAN.key,false);
        check(h,!masked(full,em),"A disabled adaptation still hid the gaze");
        AdaptationEvents.state(full).setCategoryEnabled(MetaMechanic.ENDERMAN.key,true);
        check(h,masked(full,em),"Re-enabling did not restore the effect");
        full.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);check(h,!masked(full,em),"Without the wheel the gaze was still hidden");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void endermanTargetingByGazeStartsAdaptation(GameTestHelper h) {
        var p=player(h);var em=EntityType.ENDERMAN.create(h.getLevel());place(em,p,10);
        em.setTarget(p);check(h,pending(p,MetaMechanic.ENDERMAN),"An enderman targeting the wearer did not start the adaptation");
        var q=player(h);var angry=EntityType.ENDERMAN.create(h.getLevel());place(angry,q,10);
        angry.setLastHurtByMob(q);angry.setTarget(q);
        check(h,!pending(q,MetaMechanic.ENDERMAN),"An enderman the wearer attacked counted as a gaze");
        h.succeed();
    }

    // ---------- creeper ----------
    private static ServerPlayer wearerAt(GameTestHelper h,BlockPos at,double dz,int stage) {
        var p=player(h);p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5+dz);if(stage>0)learn(p,MetaMechanic.CREEPER,stage);
        h.getLevel().addNewPlayer(p);return p;
    }
    private static void leave(GameTestHelper h,ServerPlayer p){h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    /** Posts the real Detonate event for an explosion whose block list holds {@code n} real blocks; returns what is left. */
    private static int blast(ServerLevel level,Entity exploder,BlockPos at,int n) {return blast(level,exploder,at,n,0);}
    /** Same, with {@code air} extra air positions in the list (vanilla lists every ray position, air included); the blocks are removed again. */
    private static int blast(ServerLevel level,Entity exploder,BlockPos at,int n,int air) {
        var list=new ArrayList<BlockPos>();var placed=new ArrayList<BlockPos>();
        for(int i=0;i<n;i++){var pos=at.offset(i%10,10,i/10);level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());placed.add(pos);list.add(pos);}
        for(int i=0;i<air;i++)list.add(at.offset(i%10,13,i/10));
        try{
            var ex=new Explosion(level,exploder,at.getX()+.5,at.getY()+.5,at.getZ()+.5,3f,list);
            MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level,ex,new ArrayList<>()));
            return ex.getToBlow().size();
        }finally{for(var pos:placed)level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void creeperBlastSavesBlocksByStage(GameTestHelper h) {
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(1,2,1));var creeper=EntityType.CREEPER.create(level);
        check(h,blast(level,creeper,at,100)==100,"Blocks were saved with nobody wearing the wheel");
        int[] left={100,75,50,25,0};
        for(int stage=1;stage<=4;stage++){
            var p=wearerAt(h,at,4,stage);
            try{
                int got=blast(level,creeper,at,100);check(h,got==left[stage],"Stage "+stage+" left "+got+" of 100 blocks, expected "+left[stage]);
                check(h,blast(level,EntityType.ZOMBIE.create(level),at,100)==100,"A blast that was not a creeper's was reduced");
                AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.CREEPER.key,false);
                check(h,blast(level,creeper,at,100)==100,"A disabled adaptation still saved blocks");
            }finally{leave(h,p);}
        }
        // The highest stage of the wearers near the blast counts; a wearer 17+ blocks away does not.
        var low=wearerAt(h,at,3,1);var high=wearerAt(h,at,5,3);var far=wearerAt(h,at,30,4);
        try{check(h,blast(level,creeper,at,100)==25,"The highest stage among nearby wearers was not used");}
        finally{leave(h,low);leave(h,high);}
        try{check(h,blast(level,creeper,at,100)==100,"A wearer farther than 16 blocks saved blocks");}
        finally{leave(h,far);}
        var fresh=wearerAt(h,at,4,0);
        try{blast(level,creeper,at,100);check(h,pending(fresh,MetaMechanic.CREEPER),"A creeper blast near the wearer did not start the adaptation");}
        finally{leave(h,fresh);}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void creeperBlastCountsOnlyRealBlocks(GameTestHelper h) {
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(1,2,1));var creeper=EntityType.CREEPER.create(level);
        // Nothing breaks (only air in the list): no attempt, nothing left, even for a full wearer.
        var fresh=wearerAt(h,at,4,0);var full=wearerAt(h,at,5,4);
        try{
            int got=blast(level,creeper,at,0,100);
            check(h,got==0,"An open-air blast left "+got+" positions");
            check(h,!pending(fresh,MetaMechanic.CREEPER),"A blast that broke nothing started the adaptation");
        }finally{leave(h,fresh);leave(h,full);}
        // 20 real blocks among 80 air positions: the kept share is exact and air is dropped.
        int[] left={20,15,10,5,0};
        for(int stage=1;stage<=4;stage++){
            var p=wearerAt(h,at,4,stage);
            try{
                int got=blast(level,creeper,at,20,80);check(h,got==left[stage],"Stage "+stage+" left "+got+" of 20 real blocks among air, expected "+left[stage]);
            }finally{leave(h,p);}
        }
        var q=wearerAt(h,at,4,0);
        try{blast(level,creeper,at,20,80);check(h,pending(q,MetaMechanic.CREEPER),"A blast that broke blocks did not start the adaptation");}
        finally{leave(h,q);}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void creeperBlastSparesTheBuildingButHurtsCreatures(GameTestHelper h) {
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(1,2,1));var creeper=EntityType.CREEPER.create(level);
        var centre=at.offset(0,1,0);var zombie=mob(h,at.getX()+1.5,centre.getY(),at.getZ()+.5);
        level.setBlockAndUpdate(centre,Blocks.DIRT.defaultBlockState());
        var p=wearerAt(h,at,6,4);
        try{
            level.explode(creeper,centre.getX()+.5,centre.getY()+.5,centre.getZ()+.5,3f,Level.ExplosionInteraction.TNT);
            check(h,level.getBlockState(centre).is(Blocks.DIRT),"A full adaptation did not keep the block at the heart of a creeper blast");
            check(h,zombie.getHealth()<20,"Creeper damage to creatures was reduced");
        }finally{leave(h,p);}
        level.setBlockAndUpdate(centre,Blocks.DIRT.defaultBlockState());
        level.explode(creeper,centre.getX()+.5,centre.getY()+.5,centre.getZ()+.5,3f,Level.ExplosionInteraction.TNT);
        check(h,!level.getBlockState(centre).is(Blocks.DIRT),"Without a wearer the blast did not break the block at its heart (control for the test setup)");
        zombie.discard();h.succeed();
    }

    // ---------- piglin ----------
    private static void piglinLooksAt(GameTestHelper h,net.minecraft.world.entity.LivingEntity piglin,ServerPlayer p) {
        ForgeHooks.onLivingChangeTarget(piglin,p,LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void piglinWithoutGoldStartsAdaptation(GameTestHelper h) {
        var level=h.getLevel();var piglin=EntityType.PIGLIN.create(level);var brute=EntityType.PIGLIN_BRUTE.create(level);
        var p=player(h);check(h,!PiglinAi.isWearingGold(p),"The wheel alone counted as gold");
        piglinLooksAt(h,piglin,p);check(h,pending(p,MetaMechanic.PIGLIN),"A piglin targeting a wearer without gold did not start the adaptation");
        var gold=player(h);gold.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.GOLDEN_BOOTS));
        check(h,PiglinAi.isWearingGold(gold),"Golden boots did not count as gold");
        piglinLooksAt(h,piglin,gold);check(h,!pending(gold,MetaMechanic.PIGLIN),"A piglin targeting a wearer of gold started the adaptation");
        var b=player(h);piglinLooksAt(h,brute,b);check(h,!pending(b,MetaMechanic.PIGLIN),"A piglin brute started the adaptation");
        var angry=player(h);piglin.getBrain().setMemory(MemoryModuleType.ANGRY_AT,angry.getUUID());
        piglinLooksAt(h,piglin,angry);check(h,!pending(angry,MetaMechanic.PIGLIN),"A piglin that was already angry at the wearer started the adaptation");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fullPiglinAdaptationCountsAsGold(GameTestHelper h) {
        for(int stage=1;stage<=3;stage++){var p=player(h);learn(p,MetaMechanic.PIGLIN,stage);check(h,!PiglinAi.isWearingGold(p),"Stage "+stage+" already made piglins neutral");}
        var p=player(h);learn(p,MetaMechanic.PIGLIN,4);
        check(h,PiglinAi.isWearingGold(p),"Stage four did not make piglins neutral");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.PIGLIN.key,false);
        check(h,!PiglinAi.isWearingGold(p),"A disabled adaptation still made piglins neutral");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.PIGLIN.key,true);
        check(h,PiglinAi.isWearingGold(p),"Re-enabling did not restore neutrality");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);check(h,!PiglinAi.isWearingGold(p),"Without the wheel piglins were still neutral");
        h.succeed();
    }

    // ---------- trade ----------
    private static MerchantOffer demandOffer(){return new MerchantOffer(new ItemStack(Items.EMERALD,10),ItemStack.EMPTY,new ItemStack(Items.BREAD,3),0,4,1,.5f,4);}
    private static MerchantOffer soldOutOffer(){var o=new MerchantOffer(new ItemStack(Items.EMERALD,2),ItemStack.EMPTY,new ItemStack(Items.APPLE),0,4,1,.05f,0);o.setToOutOfStock();return o;}
    private static Villager villager(GameTestHelper h,ServerPlayer near) {
        var v=EntityType.VILLAGER.create(h.getLevel());v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
        v.setNoAi(true);v.setPos(near.getX()+1,near.getY(),near.getZ());h.getLevel().addFreshEntity(v);return v;
    }
    private static void stock(AbstractVillager v,MerchantOffer... offers){v.getOffers().clear();v.getOffers().addAll(Arrays.asList(offers));v.getPersistentData().remove(MobAdaptations.RESTOCK_KEY);}
    private static void open(ServerPlayer p,AbstractVillager v){v.setTradingPlayer(p);v.openTradingScreen(p,v.getDisplayName(),1);}
    private static int price(AbstractVillager v){return v.getOffers().get(0).getCostA().getCount();}
    @GameTest(template="empty",timeoutTicks=200)
    public static void tradeDemandSurchargeShrinksByStageForTheWearerOnly(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));int[] expected={30,25,20,10,10};
        for(int stage=0;stage<=4;stage++){
            var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);if(stage>0)learn(p,MetaMechanic.TRADE,stage);
            var v=villager(h,p);stock(v,demandOffer(),soldOutOffer());
            open(p,v);
            check(h,price(v)==expected[stage],"Stage "+stage+" priced the offer at "+price(v)+", expected "+expected[stage]);
            check(h,v.getOffers().get(1).isOutOfStock()==(stage<4),"Stage "+stage+(stage<4?" restocked a sold-out offer":" left an offer sold out"));
            if(stage==0)check(h,pending(p,MetaMechanic.TRADE),"A raised price and a sold-out offer did not start the adaptation");
            p.closeContainer();
            check(h,price(v)==30,"Vanilla did not reset the special price after the trade ended: "+price(v));
            check(h,!v.isTrading(),"The villager kept trading after the menu closed");
            v.discard();
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void tradeCutFollowsAVanillaRestockWhileTheMenuIsOpen(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);learn(p,MetaMechanic.TRADE,2);
        var v=villager(h,p);
        // A: base 10, demand 4 (surcharge 20), unused. B: base 10, demand 6 (surcharge 30), one use of four.
        var a=demandOffer();var b=new MerchantOffer(new ItemStack(Items.EMERALD,10),ItemStack.EMPTY,new ItemStack(Items.BREAD,3),1,4,1,.5f,6);
        stock(v,a,b);open(p,v);
        check(h,cost(a)==20&&cost(b)==25,"Stage II did not cut the surcharges by half: "+cost(a)+" and "+cost(b));
        // Restock: demand A 4+0-4=0 (surcharge 0), demand B 6+1-3=4 (surcharge 20); vanilla resends the old special prices.
        v.restock();
        check(h,a.getDemand()==0&&b.getDemand()==4,"The restock did not change the demand as expected: "+a.getDemand()+" and "+b.getDemand());
        for(int i=0;i<10;i++)tick(p);
        check(h,cost(a)>=10,"The price of an unused offer fell below its base price: "+cost(a));
        check(h,cost(a)==10,"The cut did not follow the new demand of offer A: "+cost(a));
        check(h,cost(b)==20,"The cut did not follow the new demand of offer B: "+cost(b));
        for(int i=0;i<30;i++)tick(p);
        check(h,cost(a)==10&&cost(b)==20,"A later check changed the corrected prices: "+cost(a)+" and "+cost(b));
        p.closeContainer();
        check(h,cost(a)==10&&cost(b)==30,"Closing the menu did not reset the special prices: "+cost(a)+" and "+cost(b));
        for(int i=0;i<10;i++)tick(p);
        check(h,cost(a)==10&&cost(b)==30,"A check after closing changed the prices: "+cost(a)+" and "+cost(b));
        v.discard();h.succeed();
    }
    private static int cost(MerchantOffer o){return o.getCostA().getCount();}
    @GameTest(template="empty",timeoutTicks=200)
    public static void tradeRestockWaitsAMinutePerMerchant(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);learn(p,MetaMechanic.TRADE,4);
        var v=villager(h,p);stock(v,demandOffer(),soldOutOffer());
        open(p,v);check(h,!v.getOffers().get(1).isOutOfStock(),"The first visit did not restock");
        p.closeContainer();
        v.getOffers().get(1).setToOutOfStock();open(p,v);
        check(h,v.getOffers().get(1).isOutOfStock(),"The wheel restocked the same merchant twice within a minute");
        p.closeContainer();
        v.getPersistentData().putLong(MobAdaptations.RESTOCK_KEY,h.getLevel().getGameTime()-MobAdaptations.RESTOCK_GAP-5);
        open(p,v);check(h,!v.getOffers().get(1).isOutOfStock(),"A minute later the merchant was not restocked");
        p.closeContainer();
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.TRADE.key,false);
        stock(v,demandOffer(),soldOutOffer());open(p,v);
        check(h,price(v)==30&&v.getOffers().get(1).isOutOfStock(),"A disabled adaptation still changed the trade");
        p.closeContainer();v.discard();
        var trader=EntityType.WANDERING_TRADER.create(h.getLevel());trader.setNoAi(true);trader.setPos(p.getX()+1,p.getY(),p.getZ());h.getLevel().addFreshEntity(trader);
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.TRADE.key,true);
        stock(trader,soldOutOffer());open(p,trader);
        check(h,!trader.getOffers().get(0).isOutOfStock(),"A wandering trader was not restocked");
        p.closeContainer();trader.discard();
        var calm=player(h);calm.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);var v2=villager(h,calm);stock(v2,new MerchantOffer(new ItemStack(Items.EMERALD,2),ItemStack.EMPTY,new ItemStack(Items.APPLE),0,4,1,.05f,0));
        open(calm,v2);check(h,!pending(calm,MetaMechanic.TRADE),"A plain offer started the adaptation");
        calm.closeContainer();v2.discard();
        h.succeed();
    }

    // ---------- surrounded ----------
    private static Zombie mob(GameTestHelper h,double x,double y,double z) {
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setPos(x,y,z);zombie.setNoAi(true);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);h.getLevel().addFreshEntity(zombie);return zombie;
    }
    private static float lost(Zombie z){return 20-z.getHealth();}
    @GameTest(template="empty",timeoutTicks=200)
    public static void struckMobSharesTheBlowWithEnemiesTargetingTheWearer(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        // Neighbours 1.2, 3.0 and 4.5 blocks from the struck zombie: within range at every stage, from stage III, never.
        double[][] share={{0,0,0},{.25,0,0},{.5,0,0},{.75,.75,0},{1,1,0}};
        for(int stage=0;stage<=4;stage++){
            var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.fallDistance=0;if(stage>0)learn(p,MetaMechanic.SURROUNDED,stage);
            double x=base.getX()+.5,y=base.getY(),z=base.getZ()+6.5;
            var victim=mob(h,x,y,z);var near=mob(h,x+1.2,y,z);var mid=mob(h,x-3.0,y,z);var far=mob(h,x,y,z+4.5);var calm=mob(h,x,y,z-1.2);
            for(var m:List.of(victim,near,mid,far))m.setTarget(p);
            // ServerPlayer.tick does not recharge the attack; the full player tick does (no gravity: no fall, no critical hit).
            p.setNoGravity(true);for(int i=0;i<20;i++)p.doTick();
            p.attack(victim);
            float hit=lost(victim);check(h,hit>0.5f,"The struck zombie lost "+hit);
            check(h,Math.abs(lost(near)-hit*share[stage][0])<.02,"Stage "+stage+": the near zombie lost "+lost(near)+" of "+hit);
            check(h,Math.abs(lost(mid)-hit*share[stage][1])<.02,"Stage "+stage+": the zombie 3 blocks away lost "+lost(mid)+" of "+hit);
            check(h,lost(far)==0,"Stage "+stage+": a zombie out of range was hurt");
            check(h,lost(calm)==0,"Stage "+stage+": a zombie that was not targeting the wearer was hurt");
            for(var m:List.of(victim,near,mid,far,calm))m.discard();
        }
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.fallDistance=0;learn(p,MetaMechanic.SURROUNDED,4);
        double x=base.getX()+.5,y=base.getY(),z=base.getZ()+6.5;
        var victim=mob(h,x,y,z);var near=mob(h,x+1.2,y,z);near.setTarget(p);victim.setTarget(p);
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.SURROUNDED.key,false);p.attack(victim);
        check(h,lost(near)==0,"A disabled adaptation still shared the blow");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.SURROUNDED.key,true);
        var q=player(h);q.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);q.fallDistance=0;learn(q,MetaMechanic.SURROUNDED,4);q.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        var v2=mob(h,x,y,z+20);var n2=mob(h,x+1.2,y,z+20);n2.setTarget(q);v2.setTarget(q);q.attack(v2);
        check(h,lost(n2)==0,"Without the wheel the blow was shared");
        for(var m:List.of(victim,near,v2,n2))m.discard();
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fourEnemiesTargetingTheWearerStartAdaptation(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var mobs=new ArrayList<Zombie>();
        for(int i=0;i<3;i++){var m=mob(h,p.getX()+3,p.getY(),p.getZ()+i*.5);m.setTarget(p);mobs.add(m);}
        for(int i=0;i<25;i++)tick(p);
        check(h,!pending(p,MetaMechanic.SURROUNDED),"Three hostile mobs counted as surrounded");
        var fourth=mob(h,p.getX()-3,p.getY(),p.getZ());fourth.setTarget(p);mobs.add(fourth);
        for(int i=0;i<25;i++)tick(p);
        check(h,pending(p,MetaMechanic.SURROUNDED),"Four hostile mobs targeting the wearer did not start the adaptation");
        var q=player(h);q.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        for(int i=0;i<4;i++){var m=mob(h,q.getX()+3,q.getY(),q.getZ()+i*.5);mobs.add(m);} // close, but not targeting
        for(int i=0;i<25;i++)tick(q);
        check(h,!pending(q,MetaMechanic.SURROUNDED),"Mobs that were not targeting the wearer counted");
        mobs.forEach(Entity::discard);h.succeed();
    }

    /** A brain mob (hoglin, zoglin): its target lives only in MemoryModuleType.ATTACK_TARGET, Mob.getTarget stays null. */
    private static Mob brainMob(GameTestHelper h,EntityType<? extends Mob> type,double x,double y,double z,ServerPlayer target) {
        var m=type.create(h.getLevel());m.setPos(x,y,z);m.setNoAi(true);
        if(target!=null)m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);
        h.getLevel().addFreshEntity(m);return m;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void brainMobsTargetingTheWearerCountAsSurrounding(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var mobs=new ArrayList<Mob>();
        for(var type:List.<EntityType<? extends Mob>>of(EntityType.HOGLIN,EntityType.HOGLIN,EntityType.ZOGLIN)){mobs.add(brainMob(h,type,p.getX()+3,p.getY(),p.getZ()+mobs.size()*.5,p));}
        check(h,mobs.get(0).getTarget()==null,"A hoglin set through its brain also had Mob.getTarget set (test setup)");
        for(int i=0;i<25;i++)tick(p);
        check(h,!pending(p,MetaMechanic.SURROUNDED),"Three brain mobs counted as surrounded");
        mobs.add(brainMob(h,EntityType.ZOGLIN,p.getX()-3,p.getY(),p.getZ(),p));
        for(int i=0;i<25;i++)tick(p);
        check(h,pending(p,MetaMechanic.SURROUNDED),"Two hoglins and two zoglins targeting the wearer did not start the adaptation");
        var q=player(h);q.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        for(int i=0;i<4;i++)mobs.add(brainMob(h,EntityType.HOGLIN,q.getX()+3,q.getY(),q.getZ()+i*.5,null)); // close, but not targeting
        for(int i=0;i<25;i++)tick(q);
        check(h,!pending(q,MetaMechanic.SURROUNDED),"Hoglins that were not targeting the wearer counted");
        mobs.forEach(Entity::discard);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void struckMobSharesTheBlowWithBrainMobsTargetingTheWearer(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.fallDistance=0;learn(p,MetaMechanic.SURROUNDED,4);
        double x=base.getX()+.5,y=base.getY(),z=base.getZ()+6.5;
        var victim=mob(h,x,y,z);victim.setTarget(p);
        var hoglin=brainMob(h,EntityType.HOGLIN,x+1.2,y,z,p);var zoglin=brainMob(h,EntityType.ZOGLIN,x-1.2,y,z,p);var calm=brainMob(h,EntityType.HOGLIN,x,y,z-1.2,null);
        p.setNoGravity(true);for(int i=0;i<20;i++)p.doTick();
        p.attack(victim);
        float hit=lost(victim);check(h,hit>0.5f,"The struck zombie lost "+hit);
        float a=hoglin.getMaxHealth()-hoglin.getHealth(),b=zoglin.getMaxHealth()-zoglin.getHealth();
        check(h,Math.abs(a-hit)<.02,"A hoglin targeting the wearer lost "+a+" of "+hit);
        check(h,Math.abs(b-hit)<.02,"A zoglin targeting the wearer lost "+b+" of "+hit);
        check(h,calm.getHealth()==calm.getMaxHealth(),"A hoglin that was not targeting the wearer was hurt");
        for(var m:List.of(victim,hoglin,zoglin,calm))m.discard();
        h.succeed();
    }

    private static boolean pending(ServerPlayer p,MetaMechanic m){return AdaptationEvents.state(p).pending().containsKey(m.key);}
    private static void learn(ServerPlayer p,MetaMechanic m,int stage){
        AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);
        int guard=40000;while(AdaptationEvents.state(p).stage(m.key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+m.key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static void tick(ServerPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static ServerPlayer player(GameTestHelper h){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"MobTest"));
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p);
        for(int i=0;i<60;i++)p.tick(); // Expire vanilla login protection before exercising real damage.
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        return p;
    }
}
