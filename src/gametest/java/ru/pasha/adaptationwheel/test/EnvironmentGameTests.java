package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;
import java.util.*;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnvironmentGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void allNegativeEffectsBlockedAndInstantDamageImmune(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());
        int count=0;
        for(MobEffect effect:BuiltInRegistries.MOB_EFFECT) {
            if(!NegativeEffects.harmful(effect)) continue;
            String key=NegativeEffects.key(effect);
            AdaptationEvents.state(p).hit(key,false);
            complete(p,key,4);
            check(h,!p.addEffect(new MobEffectInstance(effect,1000,1)),"Fully adapted negative effect blocked: "+key);
            check(h,!p.hasEffect(effect),"No lingering negative effect: "+key);
            count++;
        }
        check(h,count>=14,"All standard harmful and negative neutral effects are included");
        p.setHealth(20);p.damage(p.damageSources().magic(),20);
        check(h,p.getHealth()==20,"Instant damage also protected on its direct damage path");
        check(h,p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,1000)),"Beneficial effects remain usable");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void partialEffectDurationAndDisabledImmunity(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());String key=NegativeEffects.key(MobEffects.MOVEMENT_SLOWDOWN);
        AdaptationEvents.state(p).hit(key,false);complete(p,key,1);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,1000));tick(p);
        check(h,p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration()==800,"First stage reduces remaining negative-effect duration by 20%");
        check(h,AdaptationEvents.state(p).snapshots().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow().hits()==1,
            "Internal duration replacement never adds a second exposure");
        complete(p,key,4);check(h,!p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Full adaptation removes an existing negative effect");
        AdaptationEvents.state(p).setCategoryEnabled(key,false);
        check(h,p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,1000)),"Menu can disable an individual effect immunity");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void hungerFromRottenFleshAndStarvationSharesFullSatiety(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());p.addEffect(new MobEffectInstance(MobEffects.HUNGER,600));tick(p);
        String key=AdaptationProfile.HUNGER_KEY;
        check(h,AdaptationEvents.state(p).snapshots().stream().anyMatch(s -> s.key().equals(key)),"Rotten-flesh hunger effect discovers hunger adaptation");
        complete(p,key,4);p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);p.getFoodData().setExhaustion(30);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,p));tick(p);
        check(h,p.getFoodData().getFoodLevel()==20 && p.getFoodData().getSaturationLevel()==20,"Full hunger adaptation restores vanilla full-food sprint/regen conditions without eating");
        check(h,p.getFoodData().getExhaustionLevel()==0 && !p.hasEffect(MobEffects.HUNGER),"Exhaustion and rotten-flesh effect cannot defeat full satiety");
        p.setHealth(20);p.damage(p.damageSources().starve(),10);check(h,p.getHealth()==20,"Starvation damage shares hunger immunity");
        AdaptationEvents.state(p).setCategoryEnabled(key,false);p.getFoodData().setFoodLevel(0);tick(p);
        check(h,p.getFoodData().getFoodLevel()==0,"Disabling hunger adaptation restores ordinary hunger mechanics");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void freezingAndColdImmunity(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());p.setTicksFrozen(20);tick(p);
        String key=AdaptationProfile.COLD_KEY;
        check(h,AdaptationEvents.state(p).snapshots().stream().anyMatch(s -> s.key().equals(key)),"Cold starts before freezing becomes fatal");
        complete(p,key,4);p.setTicksFrozen(140);p.setHealth(20);tick(p);p.damage(p.damageSources().freeze(),10);
        check(h,p.getTicksFrozen()==0 && p.getHealth()==20,"Full cold adaptation removes frost and blocks freezing damage");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void caveGlowTorchPauseVisionToggleAndCleanup(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());ServerLevel level=h.getLevel();BlockPos center=new BlockPos(120,80,120);
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-3,-1,-3),center.offset(3,4,3)))
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-1,0,-1),center.offset(1,2,1)))
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        p.setPos(center.getX()+.5,center.getY(),center.getZ()+.5);
        h.runAfterDelay(12,() -> {
            DarknessLight.invalidate(p);check(h,DarknessLight.isDark(p),"Unlit enclosed cave starts darkness adaptation");
            BlockPos outsideTorch=p.blockPosition().above().east(4);
            level.setBlock(outsideTorch,Blocks.TORCH.defaultBlockState(),3);DarknessLight.invalidate(p);
            check(h,DarknessLight.isDark(p),"A torch behind a solid wall cannot light a sealed room");
            level.setBlock(outsideTorch,Blocks.AIR.defaultBlockState(),3);DarknessLight.invalidate(p);tick(p);
            String key=AdaptationProfile.DARK_KEY;BlockPos head=p.blockPosition().above();
            for(int stage=1;stage<=3;stage++) {
                complete(p,key,stage);DarknessLight.invalidate(p);
                check(h,level.getBlockState(head).is(AdaptationWheel.GLOW.get()),"Glow is a real invisible light source");
                check(h,level.getBlockState(head).getValue(LightBlock.LEVEL)==DarknessLight.brightness(stage),"Stage glow strength");
                check(h,DarknessLight.isDark(p),"Own glow never stops cave adaptation");
            }
            level.setBlock(head.east(),Blocks.TORCH.defaultBlockState(),3);DarknessLight.invalidate(p);
            double work=AdaptationEvents.state(p).snapshots().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow().work();tick(p);
            check(h,!DarknessLight.isDark(p),"Real torch counts as ambient lighting");
            check(h,AdaptationEvents.state(p).snapshots().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow().work()==work,"Lit cave pauses darkness timer");
            level.setBlock(head.east(),Blocks.AIR.defaultBlockState(),3);DarknessLight.invalidate(p);complete(p,key,4);
            check(h,p.hasEffect(MobEffects.NIGHT_VISION),"Fourth darkness stage grants vision");
            AdaptationEvents.state(p).setNightVisionEnabled(false);tick(p);
            check(h,!p.hasEffect(MobEffects.NIGHT_VISION),"Separate preference disables only adaptation night vision");
            check(h,level.getBlockState(head).getValue(LightBlock.LEVEL)==15,"Disabling night vision retains strong glow");
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,1000));tick(p);
            check(h,p.hasEffect(MobEffects.NIGHT_VISION),"External night-vision potion survives preference toggle");
            TestPlayer other=new TestPlayer(level);other.setPos(p.getX(),p.getY(),p.getZ());
            AdaptationEvents.state(other).hit(key,false);complete(other,key,1);
            p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);tick(p);
            check(h,level.getBlockState(head).getValue(LightBlock.LEVEL)==5,"Another player's shared glow is retained");
            other.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);tick(other);
            check(h,level.getBlockState(head).isAir(),"Removing wheel cleans up its temporary light");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void difficultyCatalogueAndSavedLegacyHunger(GameTestHelper h) {
        check(h,AdaptationProfile.FIRE.duration(0)<AdaptationProfile.PHYSICAL.duration(0)
            && AdaptationProfile.PHYSICAL.duration(0)<AdaptationProfile.MAGIC.duration(0),"Fire faster than physical, magic slower than both");
        for(var profile:AdaptationProfile.values()) for(int stage=1;stage<5;stage++)
            check(h,profile.duration(stage)>profile.duration(stage-1),"Each later stage takes longer");
        TestPlayer p=new TestPlayer(h.getLevel());CompoundTag tag=new CompoundTag();tag.putInt("Version",1);
        ListTag complete=new ListTag();complete.add(StringTag.valueOf("damage:minecraft:starve"));tag.put("Completed",complete);
        p.getPersistentData().put(AdaptationWheel.MOD_ID,tag);
        check(h,AdaptationEvents.loadState(p).isImmune(AdaptationProfile.HUNGER_KEY),"Legacy starvation immunity migrated to full hunger adaptation");h.succeed();
    }
    private static void check(GameTestHelper h,boolean value,String message) { if(!value) h.fail(message); }
    private static void tick(TestPlayer p) { p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p)); }
    private static void complete(TestPlayer p,String key,int stage) {
        int guard=20000;while(AdaptationEvents.state(p).stage(key)<stage && guard-->0) tick(p);
        if(guard<=0) throw new AssertionError("Timer did not finish "+key);
    }
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level) { super(level,new GameProfile(UUID.randomUUID(),"EnvironmentTest"));setPos(0,100,0);setHealth(20);setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get())); }
        @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
        void damage(DamageSource source,float amount) { super.actuallyHurt(source,amount); }
    }
}
