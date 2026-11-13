package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

/**
 * World and movement adaptations. Coremods are active in the GameTest server, so the hooks run through real
 * {@code Entity.move}, {@code Player.getDigSpeed}, {@code BucketItem.emptyContents}, {@code FarmBlock.fallOn}.
 * Client prediction (the same {@code MetaHooks} verdict on the local player) cannot be tested here.
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementGameTests {
    // ---------- sticky blocks ----------
    @GameTest(template="empty",timeoutTicks=60)
    public static void cobwebSlowdownShrinksPerStage(GameTestHelper h) {
        var p=player(h);var at=h.absolutePos(new BlockPos(1,8,1));
        for(int s=0;s<=4;s++){
            if(s>0)learn(p,MetaMechanic.STICKY,s);
            double moved=webStep(p,at),want=.25+.75*.25*s;
            check(h,Math.abs(moved-want)<.01,"Stage "+s+": cobweb let the wearer move "+moved+" instead of "+want);
        }
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.STICKY.key)||AdaptationEvents.state(p).stage(MetaMechanic.STICKY.key)>=4,"Standing in a cobweb did not start adaptation");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.STICKY.key,false);
        check(h,Math.abs(webStep(p,at)-.25)<.01,"A disabled adaptation still eased the cobweb");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.STICKY.key,true);
        check(h,Math.abs(webStep(p,at)-1)<.01,"Re-enabling did not bring the effect back");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,Math.abs(webStep(p,at)-.25)<.01,"Without the wheel the cobweb was eased");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void sweetBerryBushMultipliersMoveTowardOne(GameTestHelper h) {
        var p=player(h);var at=h.absolutePos(new BlockPos(1,8,1));
        learn(p,MetaMechanic.STICKY,2);
        p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);
        p.makeStuckInBlock(Blocks.SWEET_BERRY_BUSH.defaultBlockState(),new Vec3(.8,.75,.8));
        double x=p.getX(),y=p.getY();p.setNoGravity(true);p.move(MoverType.SELF,new Vec3(1,-1,0));
        check(h,Math.abs(p.getX()-x-.9)<.01&&Math.abs(y-p.getY()-.875)<.01,"Stage II berry bush: moved "+(p.getX()-x)+", fell "+(y-p.getY())+", expected .9 and .875");
        h.succeed();
    }
    /** Powder snow multiplies the movement by (.9, 1.5, .9): only the slowing components move toward 1, the 1.5 stays. */
    @GameTest(template="empty",timeoutTicks=60)
    public static void powderSnowKeepsItsVerticalBoostAndEasesTheSlowdown(GameTestHelper h) {
        var p=player(h);var at=h.absolutePos(new BlockPos(1,8,1));p.setNoGravity(true);
        for(int s=1;s<=4;s++){
            learn(p,MetaMechanic.STICKY,s);
            p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);
            p.makeStuckInBlock(Blocks.POWDER_SNOW.defaultBlockState(),new Vec3(.9,1.5,.9));
            var from=p.position();p.move(MoverType.SELF,new Vec3(1,1,1));
            double dx=p.getX()-from.x,dy=p.getY()-from.y,dz=p.getZ()-from.z,wantXZ=.9+.1*.25*s;
            check(h,Math.abs(dx-wantXZ)<.01&&Math.abs(dz-wantXZ)<.01,"Stage "+s+": powder snow moved the wearer "+dx+"/"+dz+" sideways instead of "+wantXZ);
            check(h,Math.abs(dy-1.5)<.01,"Stage "+s+": powder snow's vertical multiplier became "+dy+" instead of staying 1.5");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void honeySlowdownAndJumpShrinkPerStage(GameTestHelper h) {
        var p=player(h);var floor=h.absolutePos(new BlockPos(1,8,1));var level=h.getLevel();
        level.setBlockAndUpdate(floor,Blocks.HONEY_BLOCK.defaultBlockState());
        for(int s=0;s<=4;s++){
            if(s>0)learn(p,MetaMechanic.STICKY,s);
            double run=run(p,floor),wantRun=.4+.6*.25*s,jump=jump(p,floor),wantJump=.42*(.5+.5*.25*s);
            check(h,Math.abs(run-wantRun)<.01,"Stage "+s+": honey kept "+run+" of the speed instead of "+wantRun);
            check(h,Math.abs(jump-wantJump)<.01,"Stage "+s+": jump from honey was "+jump+" instead of "+wantJump);
        }
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.STICKY.key,false);
        check(h,Math.abs(run(p,floor)-.4)<.01&&Math.abs(jump(p,floor)-.21)<.01,"A disabled adaptation still eased honey");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.STICKY.key,true);
        level.setBlockAndUpdate(floor,Blocks.SOUL_SAND.defaultBlockState());
        check(h,Math.abs(run(p,floor)-1)<.01,"Full adaptation did not lift the soul sand slowdown");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,Math.abs(run(p,floor)-.4)<.01,"Without the wheel soul sand was eased");
        level.setBlockAndUpdate(floor,Blocks.AIR.defaultBlockState());
        h.succeed();
    }
    private static double webStep(ServerPlayer p,BlockPos at){
        p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);
        p.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(),new Vec3(.25,.05,.25));
        double x=p.getX();p.move(MoverType.SELF,new Vec3(1,0,0));return p.getX()-x;
    }
    /** Entity.move multiplies the velocity by the block speed factor after moving. */
    private static double run(ServerPlayer p,BlockPos floor){
        p.setPos(floor.getX()+.5,floor.getY()+1,floor.getZ()+.5);p.setDeltaMovement(1,0,1);p.move(MoverType.SELF,Vec3.ZERO);return p.getDeltaMovement().x;
    }
    private static double jump(ServerPlayer p,BlockPos floor){
        p.setPos(floor.getX()+.5,floor.getY()+1,floor.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);p.jumpFromGround();return p.getDeltaMovement().y;
    }

    // ---------- portal wait ----------
    @GameTest(template="empty",timeoutTicks=60)
    public static void portalWaitShrinksPerStage(GameTestHelper h) {
        var p=player(h);int[] want={80,60,40,20,1};
        for(int s=0;s<=4;s++){
            if(s>0)learn(p,MetaMechanic.PORTAL,s);
            check(h,p.getPortalWaitTime()==want[s],"Stage "+s+": portal wait was "+p.getPortalWaitTime()+" instead of "+want[s]);
        }
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.PORTAL.key,false);
        check(h,p.getPortalWaitTime()==80,"A disabled adaptation still shortened the portal wait");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.PORTAL.key,true);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,p.getPortalWaitTime()==80,"Without the wheel the portal wait was shortened");
        p.getAbilities().invulnerable=true;
        check(h,p.getPortalWaitTime()==1,"Creative portal wait is not 1");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void standingInPortalStartsAdaptation(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,8,1));
        var inside=player(h);inside.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        level.setBlock(pos,Blocks.NETHER_PORTAL.defaultBlockState(),18);
        var outside=player(h);outside.setPos(pos.getX()+5.5,pos.getY(),pos.getZ()+.5);
        for(int i=0;i<21;i++){tick(inside);tick(outside);}
        check(h,AdaptationEvents.state(inside).pending().containsKey(MetaMechanic.PORTAL.key),"Standing in a nether portal did not start adaptation");
        check(h,!AdaptationEvents.state(outside).pending().containsKey(MetaMechanic.PORTAL.key),"Standing beside a portal started adaptation");
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);h.succeed();
    }

    // ---------- loose footing ----------
    @GameTest(template="empty",timeoutTicks=60)
    public static void airMiningPenaltyShrinksPerStage(GameTestHelper h) {
        var p=player(h);var pos=h.absolutePos(new BlockPos(1,8,1));var stone=Blocks.STONE.defaultBlockState();
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        p.setOnGround(true);float ground=p.getDigSpeed(stone,pos);
        check(h,ground>0&&!AdaptationEvents.state(p).pending().containsKey(MetaMechanic.AIR_MINING.key),"Digging on the ground started adaptation");
        p.setOnGround(false);float air=p.getDigSpeed(stone,pos);
        check(h,Math.abs(air-ground/5)<.001,"Vanilla air penalty is not one fifth: "+air+" vs "+ground);
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.AIR_MINING.key),"Digging in the air did not start adaptation");
        for(int s=1;s<=4;s++){
            learn(p,MetaMechanic.AIR_MINING,s);p.setOnGround(false);
            float v=p.getDigSpeed(stone,pos);
            check(h,Math.abs(v-ground/5*(1+s))<.001,"Stage "+s+": dig speed in the air was "+v+" instead of "+ground/5*(1+s));
        }
        p.setOnGround(true);
        check(h,Math.abs(p.getDigSpeed(stone,pos)-ground)<.001,"Digging on the ground was changed");
        p.setOnGround(false);
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.AIR_MINING.key,false);
        check(h,Math.abs(p.getDigSpeed(stone,pos)-air)<.001,"A disabled adaptation still sped up digging");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.AIR_MINING.key,true);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,Math.abs(p.getDigSpeed(stone,pos)-air)<.001,"Without the wheel digging was sped up");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void underwaterPenaltyAndAirPenaltyBothShrink(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(1,8,1));var stone=Blocks.STONE.defaultBlockState();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            level.setBlockAndUpdate(base.offset(x,-1,z),stone);
            for(int y=0;y<=4;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.WATER.defaultBlockState());
        }
        var p=player(h);p.setPos(base.getX()+.5,base.getY()+1,base.getZ()+.5);p.doTick(); // the full player tick fills in fluidOnEyes
        p.setPos(base.getX()+.5,base.getY()+1,base.getZ()+.5);
        check(h,p.isEyeInFluid(FluidTags.WATER),"The test wearer's eyes are not in water");
        p.setOnGround(true);float wet=p.getDigSpeed(stone,base);
        p.setOnGround(false);float both=p.getDigSpeed(stone,base);
        check(h,wet>0&&Math.abs(both-wet/5)<.001,"Vanilla penalties do not stack as expected: "+wet+" "+both);
        for(int s=1;s<=4;s++){
            learn(p,MetaMechanic.AIR_MINING,s);
            p.setPos(base.getX()+.5,base.getY()+1,base.getZ()+.5);
            p.setOnGround(true);float w=p.getDigSpeed(stone,base);
            p.setOnGround(false);float b=p.getDigSpeed(stone,base);
            check(h,Math.abs(w-wet*(1+s))<.001,"Stage "+s+": underwater only: "+w+" instead of "+wet*(1+s));
            check(h,Math.abs(b-both*(1+s)*(1+s))<.001,"Stage "+s+": underwater and airborne: "+b+" instead of "+both*(1+s)*(1+s));
        }
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=4;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    // ---------- trampled farmland ----------
    @GameTest(template="empty",timeoutTicks=200)
    public static void farmlandSurvivesWithTheStagesChance(GameTestHelper h) {
        var p=player(h);p.setInvulnerable(true);var pos=h.absolutePos(new BlockPos(1,2,1));
        check(h,trampled(h,p,pos,20)==20,"Stage 0 farmland was saved");
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.FARMLAND.key),"Trampling did not start adaptation");
        double[][] band={{.15,.35},{.40,.60},{.65,.85}};
        for(int s=1;s<=3;s++){
            learn(p,MetaMechanic.FARMLAND,s);
            int n=400,saved=n-trampled(h,p,pos,n);double share=saved/(double)n;
            check(h,share>=band[s-1][0]&&share<=band[s-1][1],"Stage "+s+" saved "+share+" of the farmland, expected about "+25*s+" %");
        }
        learn(p,MetaMechanic.FARMLAND,4);
        check(h,trampled(h,p,pos,50)==0,"Stage IV farmland was trampled");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.FARMLAND.key,false);
        check(h,trampled(h,p,pos,20)==20,"A disabled adaptation still saved farmland");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.FARMLAND.key,true);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,trampled(h,p,pos,20)==20,"Without the wheel farmland was saved");
        h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void farmlandTrampledByTheWearersMountIsSavedToo(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));
        var p=player(h);var horse=EntityType.HORSE.create(level);
        horse.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);horse.setTamed(true);horse.equipSaddle(null);horse.setInvulnerable(true);level.addFreshEntity(horse);
        p.setInvulnerable(true);
        check(h,p.startRiding(horse,true)&&horse.getControllingPassenger()==p,"The wearer could not ride the test horse");
        check(h,trampled(h,horse,pos,10)==10,"A mount of a wearer without the adaptation was saved");
        learn(p,MetaMechanic.FARMLAND,4);
        check(h,trampled(h,horse,pos,10)==0,"The wearer's mount trampled farmland at stage IV");
        var loose=EntityType.HORSE.create(level);loose.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);loose.setInvulnerable(true);level.addFreshEntity(loose);
        check(h,trampled(h,loose,pos,10)==10,"A horse without the wearer on it was saved");
        p.stopRiding();horse.discard();loose.discard();level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.succeed();
    }
    /** A landing the adaptation prevented is not a refusal, so it must not speed the running stage up; a real trample does. */
    @GameTest(template="empty",timeoutTicks=60)
    public static void preventedTrampleIsNotAnAttempt(GameTestHelper h) {
        var p=player(h);p.setInvulnerable(true);var pos=h.absolutePos(new BlockPos(1,2,1));
        learn(p,MetaMechanic.FARMLAND,3);
        int hits=AdaptationEvents.state(p).snapshot(MetaMechanic.FARMLAND.key).hits();
        seed(p,true,3);
        check(h,trampled(h,p,pos,1)==0,"The seeded saving landing still trampled the farmland");
        check(h,AdaptationEvents.state(p).snapshot(MetaMechanic.FARMLAND.key).hits()==hits,"A prevented trample counted as an attempt and sped the stage up");
        seed(p,false,3);
        check(h,trampled(h,p,pos,1)==1,"The seeded failing landing did not trample the farmland");
        check(h,AdaptationEvents.state(p).snapshot(MetaMechanic.FARMLAND.key).hits()==hits+1,"A real trample did not count as an attempt");
        h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.succeed();
    }
    /** Makes the wearer's next {@code nextInt(100)} fall below (or not below) the stage's 25 % steps. */
    private static void seed(ServerPlayer p,boolean saved,int stage){
        for(long seed=1;;seed++)if((RandomSource.create(seed).nextInt(100)<25*stage)==saved){p.getRandom().setSeed(seed);return;}
    }
    /** How many of {@code n} landings turned the farmland to dirt (FarmBlock.fallOn, which fires the Forge event). */
    private static int trampled(GameTestHelper h,Entity who,BlockPos pos,int n){
        var level=h.getLevel();int dirt=0;
        for(int i=0;i<n;i++){
            level.setBlockAndUpdate(pos,Blocks.FARMLAND.defaultBlockState());
            Blocks.FARMLAND.fallOn(level,level.getBlockState(pos),pos,who,10f);
            if(level.getBlockState(pos).is(Blocks.DIRT))dirt++;
        }
        return dirt;
    }

    // ---------- smelting ----------
    @GameTest(template="empty",timeoutTicks=140)
    public static void fullSmeltingWaitTriplesFurnaceSpeed(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=wearerAt(h,base,MetaMechanic.SMELTING,4);var furnace=cook(h,base.offset(3,0,0),Blocks.FURNACE);
        h.onEachTick(()->tick(p));
        h.runAfterDelay(110,()->{
            check(h,furnace.getItem(2).getCount()==1,"A furnace beside a stage IV wearer gave "+furnace.getItem(2).getCount()+" ingots in 110 ticks (vanilla 0, x3 gives 1)");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void stageTwoSmeltingWaitIsOneAndAHalfTimesFaster(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=wearerAt(h,base,MetaMechanic.SMELTING,2);var furnace=cook(h,base.offset(-3,0,0),Blocks.FURNACE);
        h.onEachTick(()->tick(p));
        h.runAfterDelay(160,()->{
            check(h,furnace.getItem(2).getCount()==1,"A furnace beside a stage II wearer gave "+furnace.getItem(2).getCount()+" ingots in 160 ticks (vanilla 0, x1.5 gives 1)");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=150)
    public static void disabledOrUnwornSmeltingWaitChangesNothing(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var off=wearerAt(h,base,MetaMechanic.SMELTING,4);AdaptationEvents.state(off).setCategoryEnabled(MetaMechanic.SMELTING.key,false);
        var bare=wearerAt(h,base.offset(0,0,1),MetaMechanic.SMELTING,4);bare.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        var furnace=cook(h,base.offset(2,0,0),Blocks.FURNACE);
        h.onEachTick(()->{tick(off);tick(bare);});
        h.runAfterDelay(120,()->{
            check(h,furnace.getItem(2).isEmpty(),"A disabled or unworn adaptation still sped up a furnace");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=170)
    public static void furnaceNearTwoWearersIsSpedUpOnceByTheBestStage(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var a=wearerAt(h,base,MetaMechanic.SMELTING,4);var b=wearerAt(h,base.offset(0,0,2),MetaMechanic.SMELTING,3);
        var furnace=cook(h,base.offset(3,0,0),Blocks.FURNACE);
        h.onEachTick(()->{tick(b);tick(a);});
        h.runAfterDelay(130,()->{
            check(h,furnace.getItem(2).getCount()==1,"Two wearers sped one furnace up to "+furnace.getItem(2).getCount()+" ingots in 130 ticks (once at x3 gives 1, twice at x5 gives 2)");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void furnacesBeyondEightBlocksAreLeftAlone(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        // Straight up: nine blocks to a side can reach the wearer of a neighbouring test in the GameTest grid.
        var p=wearerAt(h,base,MetaMechanic.SMELTING,4);var far=cook(h,base.offset(0,9,0),Blocks.FURNACE);
        h.onEachTick(()->tick(p));
        h.runAfterDelay(70,()->{
            check(h,far.getItem(2).isEmpty()&&far.getItem(0).getCount()==4,"A furnace nine blocks away was sped up");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void fullSmeltingWaitTriplesBrewingStand(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(1,2,1));
        var p=wearerAt(h,base,MetaMechanic.SMELTING,4);var pos=base.offset(2,0,0);
        level.setBlockAndUpdate(pos,Blocks.BREWING_STAND.defaultBlockState());
        var stand=(BrewingStandBlockEntity)level.getBlockEntity(pos);
        stand.setItem(0,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER));
        stand.setItem(3,new ItemStack(Items.NETHER_WART));stand.setItem(4,new ItemStack(Items.BLAZE_POWDER));
        h.onEachTick(()->tick(p));
        h.runAfterDelay(160,()->{
            check(h,PotionUtils.getPotion(stand.getItem(0))==Potions.AWKWARD,"A brewing stand beside a stage IV wearer did not finish in 160 ticks (vanilla needs 400)");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void workingFurnaceMenuStartsAdaptation(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        var p=player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var furnace=cook(h,base.offset(1,0,0),Blocks.FURNACE);
        var state=h.getLevel().getBlockState(furnace.getBlockPos());
        AbstractFurnaceBlockEntity.serverTick(h.getLevel(),furnace.getBlockPos(),state,furnace);
        for(int i=0;i<21;i++)tick(p);
        check(h,!AdaptationEvents.state(p).pending().containsKey(MetaMechanic.SMELTING.key),"A closed furnace started adaptation");
        p.openMenu((MenuProvider)furnace);
        for(int i=0;i<21;i++)tick(p);
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.SMELTING.key),"Watching a working furnace did not start adaptation");
        p.closeContainer();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void extraTicksFollowTheStageSpeeds(GameTestHelper h) {
        int[] total={0,5,10,20,40};
        for(int s=1;s<=4;s++){
            int sum=0;for(long t=0;t<20;t++)sum+=MovementAdaptations.extraTicks(s,t);
            check(h,sum==total[s],"Stage "+s+" gave "+sum+" extra ticks per second, expected "+total[s]);
        }
        check(h,MovementAdaptations.extraTicks(0,0)==0,"Stage 0 sped something up");
        h.succeed();
    }
    private static ServerPlayer wearerAt(GameTestHelper h,BlockPos at,MetaMechanic m,int stage){
        var p=player(h);p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);learn(p,m,stage);return p;
    }
    private static AbstractFurnaceBlockEntity cook(GameTestHelper h,BlockPos pos,Block block){
        var level=h.getLevel();level.setBlockAndUpdate(pos,block.defaultBlockState());
        var be=(AbstractFurnaceBlockEntity)level.getBlockEntity(pos);
        be.setItem(0,new ItemStack(Items.RAW_IRON,4));be.setItem(1,new ItemStack(Items.COAL,4));
        return be;
    }

    // ---------- water in the Nether ----------
    @GameTest(template="empty",timeoutTicks=150)
    public static void netherWaterStaysForTheStagesTime(GameTestHelper h) {
        var nether=h.getLevel().getServer().getLevel(Level.NETHER);
        if(nether==null){h.fail("The test server has no Nether");return;}
        nether.setChunkForced(0,0,true);var pos=new BlockPos(0,200,0);var bucket=(BucketItem)Items.WATER_BUCKET;
        basin(nether,pos);
        var p=player(h);
        check(h,bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket))&&nether.getBlockState(pos).isAir(),"Water placed without the adaptation did not evaporate in the Nether");
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.NETHER_WATER.key),"Evaporating water did not start adaptation");
        learn(p,MetaMechanic.NETHER_WATER,1);
        check(h,bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket))&&nether.getFluidState(pos).isSource()&&nether.getFluidState(pos).is(FluidTags.WATER),"Stage I water did not stay in the Nether");
        check(h,MovementAdaptations.waterTicks(1)==60&&MovementAdaptations.waterTicks(2)==200&&MovementAdaptations.waterTicks(3)==600&&MovementAdaptations.waterTicks(4)<0,"Water lifetimes are not 3 s, 10 s, 30 s and permanent");
        h.runAfterDelay(40,()->check(h,nether.getFluidState(pos).isSource(),"Stage I water vanished before 3 seconds"));
        h.runAfterDelay(90,()->{
            check(h,nether.getBlockState(pos).isAir(),"Stage I water was still there after 4.5 seconds");
            nether.setChunkForced(0,0,false);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=140)
    public static void fullNetherWaterIsPermanentAndOffMeansVanilla(GameTestHelper h) {
        var nether=h.getLevel().getServer().getLevel(Level.NETHER);
        if(nether==null){h.fail("The test server has no Nether");return;}
        nether.setChunkForced(2,0,true);BlockPos pos=new BlockPos(40,200,0),dry=pos.offset(3,0,0);var bucket=(BucketItem)Items.WATER_BUCKET;
        basin(nether,pos);basin(nether,dry);
        var p=player(h);learn(p,MetaMechanic.NETHER_WATER,4);
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.NETHER_WATER.key,false);
        check(h,bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket))&&nether.getBlockState(pos).isAir(),"A disabled adaptation let water stay in the Nether");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.NETHER_WATER.key,true);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket))&&nether.getBlockState(pos).isAir(),"Water stayed in the Nether without the wheel");
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        check(h,bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket))&&nether.getFluidState(pos).isSource(),"Stage IV water did not stay in the Nether");
        var before=nether.getBlockState(dry);boolean emptied=bucket.emptyContents(null,nether,dry,null,new ItemStack(bucket));
        check(h,emptied&&nether.getBlockState(dry).isAir(),"A dispenser-style placement (no player) did not evaporate: "+before+" -> "+emptied+", "+nether.getBlockState(dry));
        h.runAfterDelay(100,()->{
            check(h,nether.getFluidState(pos).isSource(),"Stage IV water disappeared");
            nether.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());nether.setChunkForced(2,0,false);h.succeed();
        });
    }

    /** A bucket emptied into the flowing water of a timed source is a new source and needs its own timer. */
    @GameTest(template="empty",timeoutTicks=150)
    public static void bucketIntoFlowingNetherWaterGetsATimerToo(GameTestHelper h) {
        var nether=h.getLevel().getServer().getLevel(Level.NETHER);
        if(nether==null){h.fail("The test server has no Nether");return;}
        nether.setChunkForced(6,0,true);var pos=new BlockPos(100,200,0);var source=pos.offset(6,0,0);var bucket=(BucketItem)Items.WATER_BUCKET;
        basin(nether,pos);nether.setBlock(source,Blocks.AIR.defaultBlockState(),2);
        var p=player(h);learn(p,MetaMechanic.NETHER_WATER,1);
        nether.setBlock(pos,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,3),2);
        check(h,!nether.getFluidState(pos).isSource()&&nether.getFluidState(pos).is(FluidTags.WATER)&&!MovementAdaptations.pending(nether.dimension(),pos),"Test setup: no flowing water at the target");
        check(h,bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket))&&nether.getFluidState(pos).isSource(),"A bucket emptied into flowing water did not make a source in the Nether");
        check(h,MovementAdaptations.pending(nether.dimension(),pos),"The new source in flowing water got no timer, so it would stay forever");
        nether.setBlock(source,Blocks.WATER.defaultBlockState(),2);
        boolean timed=!MovementAdaptations.fizzles(true,p,nether,source,bucket)&&MovementAdaptations.pending(nether.dimension(),source);
        nether.setBlock(source,Blocks.AIR.defaultBlockState(),2); // right away: this one has no walls and would flow away
        check(h,!timed,"A target that is already a water source got a second timer");
        h.runAfterDelay(100,()->{
            check(h,nether.getBlockState(pos).isAir()&&!MovementAdaptations.pending(nether.dimension(),pos),"The source made in flowing water did not expire");
            clear(nether,pos);nether.setChunkForced(6,0,false);h.succeed();
        });
    }
    /** Two timed sources with one flowing block between them must not make a source that nothing removes. */
    @GameTest(template="empty",timeoutTicks=150)
    public static void twoTimedSourcesDoNotMakeAPermanentOne(GameTestHelper h) {
        var nether=h.getLevel().getServer().getLevel(Level.NETHER);
        if(nether==null){h.fail("The test server has no Nether");return;}
        if(!nether.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION)){h.fail("The waterSourceConversion rule is off in the test world, so the scenario cannot be built");return;}
        nether.setChunkForced(8,0,true);var a=new BlockPos(130,200,1);var mid=a.east();var c=a.east(2);var bucket=(BucketItem)Items.WATER_BUCKET;
        chamber(nether,a);
        var p=player(h);learn(p,MetaMechanic.NETHER_WATER,1);
        check(h,bucket.emptyContents(p,nether,a,null,new ItemStack(bucket))&&bucket.emptyContents(p,nether,c,null,new ItemStack(bucket)),"Stage I water was not placed");
        check(h,MovementAdaptations.pending(nether.dimension(),a)&&MovementAdaptations.pending(nether.dimension(),c),"The timed sources are not tracked");
        h.runAfterDelay(35,()->{
            check(h,nether.getFluidState(mid).is(FluidTags.WATER),"Test setup: the water did not flow between the sources");
            check(h,!nether.getFluidState(mid).isSource(),"Two timed sources made a source between them that no timer will remove");
        });
        h.runAfterDelay(100,()->{
            check(h,!nether.getFluidState(a).is(FluidTags.WATER)&&!nether.getFluidState(mid).is(FluidTags.WATER)&&!nether.getFluidState(c).is(FluidTags.WATER),"Water was left in the chamber after the timers ran out");
            unchamber(nether,a);nether.setChunkForced(8,0,false);h.succeed();
        });
    }
    /** An entry whose chunk is not loaded must stay (it used to be dropped after a minute and the water became permanent). */
    @GameTest(template="empty",timeoutTicks=40)
    public static void timedWaterIsKeptWhileItsChunkIsUnloaded(GameTestHelper h) {
        var server=h.getLevel().getServer();var nether=server.getLevel(Level.NETHER);
        if(nether==null){h.fail("The test server has no Nether");return;}
        var far=new BlockPos(2_000_000,200,0);var owner=UUID.randomUUID();
        check(h,!nether.hasChunkAt(far),"Test setup: the far chunk is loaded");
        check(h,MovementAdaptations.track(server,nether.dimension(),far,owner,MovementAdaptations.clock(server)-5000),"A timer was refused");
        h.runAfterDelay(10,()->{
            try{
                check(h,MovementAdaptations.pending(nether.dimension(),far),"An overdue entry with an unloaded chunk was dropped");
                var saved=server.overworld().getDataStorage().computeIfAbsent(TimedWater::load,TimedWater::new,TimedWater.NAME);
                check(h,saved.list.stream().anyMatch(t->t.pos().equals(far)&&owner.equals(t.owner())),"The pending entry is not in the saved data");
            }finally{MovementAdaptations.cancel(server,nether.dimension(),far);}
            check(h,!MovementAdaptations.pending(nether.dimension(),far),"The entry could not be cancelled");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void timedWaterListSurvivesSaveAndLoad(GameTestHelper h) {
        var data=new TimedWater();
        data.list.add(new TimedWater.Timed(Level.NETHER,new BlockPos(-5,200,7),123456789012L,UUID.randomUUID()));
        data.list.add(new TimedWater.Timed(Level.OVERWORLD,new BlockPos(30000000,-60,-30000000),42L,null));
        var loaded=TimedWater.load(data.save(new CompoundTag()));
        check(h,loaded.list.equals(data.list),"Timed water changed on the way through NBT: "+loaded.list+" instead of "+data.list);
        h.succeed();
    }

    /** A stone basin of one block: water placed there cannot flow away, so nothing is left in the saved test world. */
    private static void basin(net.minecraft.server.level.ServerLevel level,BlockPos pos){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        for(var side:new BlockPos[]{pos.below(),pos.north(),pos.south(),pos.east(),pos.west()})level.setBlock(side,Blocks.STONE.defaultBlockState(),2);
    }

    private static void clear(net.minecraft.server.level.ServerLevel level,BlockPos pos){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
    }
    private static void unchamber(net.minecraft.server.level.ServerLevel level,BlockPos a){
        for(int x=-1;x<=3;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++)level.setBlock(a.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
    }
    /** Three water cells in a row (a, a+1, a+2) with a stone floor and stone walls, so nothing flows out. */
    private static void chamber(net.minecraft.server.level.ServerLevel level,BlockPos a){
        unchamber(level,a);
        for(int x=-1;x<=3;x++){
            level.setBlock(a.offset(x,-1,0),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(a.offset(x,0,-1),Blocks.STONE.defaultBlockState(),2);level.setBlock(a.offset(x,0,1),Blocks.STONE.defaultBlockState(),2);
        }
        level.setBlock(a.west(),Blocks.STONE.defaultBlockState(),2);level.setBlock(a.east(3),Blocks.STONE.defaultBlockState(),2);
    }

    // ---------- helpers (as in OutsideGameTests) ----------
    private static void learn(ServerPlayer p,MetaMechanic m,int stage){
        AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);
        int guard=40000;while(AdaptationEvents.state(p).stage(m.key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+m.key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static void tick(ServerPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static ServerPlayer player(GameTestHelper h){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"MovementTest"));
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p);
        for(int i=0;i<60;i++)p.tick(); // Expire vanilla login protection before exercising real damage.
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        return p;
    }
}
