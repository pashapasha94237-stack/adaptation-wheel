package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.server.AdaptationEvents;
import ru.pasha.adaptationwheel.server.NegativeEffects;

import java.util.UUID;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NightVisionGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void infiniteVisionNeverEntersVanillaFlickerWindow(GameTestHelper h) {
        VisionPlayer p=adapted(h);
        NegativeEffects.maintain(p);
        MobEffectInstance owned=p.getEffect(MobEffects.NIGHT_VISION);
        check(h,owned!=null && owned.getDuration()==MobEffectInstance.INFINITE_DURATION,
                "Adaptation uses vanilla infinite duration, not a repeatedly renewed short effect");
        for(int tick=0;tick<700;tick++) {
            advance(p,1);
            check(h,p.getEffect(MobEffects.NIGHT_VISION)==owned && !owned.endsWithin(200),
                    "The same infinite instance stays outside the night-vision flicker window");
        }
        AdaptationEvents.state(p).setNightVisionEnabled(false);NegativeEffects.maintain(p);
        check(h,!p.hasEffect(MobEffects.NIGHT_VISION),"Vision preference removes the owned infinite effect");
        AdaptationEvents.state(p).setNightVisionEnabled(true);NegativeEffects.maintain(p);
        check(h,p.getEffect(MobEffects.NIGHT_VISION).isInfiniteDuration(),"Re-enabling vision grants infinity again");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);NegativeEffects.maintain(p);
        check(h,!p.hasEffect(MobEffects.NIGHT_VISION),"Removing the wheel removes its infinite effect");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void preexistingPotionResumesWithItsRemainingTime(GameTestHelper h) {
        VisionPlayer p=adapted(h);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,1000,0,false,true,true));
        NegativeEffects.maintain(p);advance(p,120);
        check(h,p.getEffect(MobEffects.NIGHT_VISION).isInfiniteDuration(),"Potion does not replace infinite adaptation vision");
        AdaptationEvents.state(p).setNightVisionEnabled(false);NegativeEffects.maintain(p);
        MobEffectInstance restored=p.getEffect(MobEffects.NIGHT_VISION);
        check(h,restored!=null && restored.getDuration()==880,"Potion's 120 hidden ticks are deducted exactly");
        check(h,!restored.isAmbient() && restored.isVisible() && restored.showIcon(),"Potion presentation flags survive restoration");
        NegativeEffects.clear(p);
        check(h,p.getEffect(MobEffects.NIGHT_VISION)==restored,"Cleanup leaves an external potion untouched");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void potionsAppliedDuringInfinityKeepVanillaStrongerAndFallbackTimers(GameTestHelper h) {
        VisionPlayer p=adapted(h);NegativeEffects.maintain(p);
        MobEffectInstance owned=p.getEffect(MobEffects.NIGHT_VISION);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,1000,0));advance(p,120);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,40,1));
        check(h,p.getEffect(MobEffects.NIGHT_VISION)==owned && owned.isInfiniteDuration(),
                "A stronger potion cannot shorten the visible infinite effect even before maintenance");
        advance(p,10);
        AdaptationEvents.state(p).setNightVisionEnabled(false);NegativeEffects.maintain(p);
        MobEffectInstance restored=p.getEffect(MobEffects.NIGHT_VISION);
        check(h,restored!=null && restored.getAmplifier()==1 && restored.getDuration()==30,
                "The stronger potion is restored with its own remaining duration");
        advance(p,30);restored=p.getEffect(MobEffects.NIGHT_VISION);
        check(h,restored!=null && restored.getAmplifier()==0 && restored.getDuration()==840,
                "Vanilla weaker fallback potion also ages while the stronger potion is active");
        NegativeEffects.clear(p);h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void expiredAndClearedPotionsNeverReappear(GameTestHelper h) {
        VisionPlayer p=adapted(h);NegativeEffects.maintain(p);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20));advance(p,30);
        AdaptationEvents.state(p).setNightVisionEnabled(false);NegativeEffects.maintain(p);
        check(h,!p.hasEffect(MobEffects.NIGHT_VISION),"An expired hidden potion is not resurrected");
        AdaptationEvents.state(p).setNightVisionEnabled(true);NegativeEffects.maintain(p);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,1000));p.removeAllEffects();
        NegativeEffects.maintain(p);
        check(h,p.getEffect(MobEffects.NIGHT_VISION).isInfiniteDuration(),"Adaptation recovers its own vision after milk or effect clear");
        AdaptationEvents.state(p).setNightVisionEnabled(false);NegativeEffects.maintain(p);
        check(h,!p.hasEffect(MobEffects.NIGHT_VISION),"Milk or effect clear also removed the backed-up potion");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void externalInfiniteVisionSurvivesWheelRemoval(GameTestHelper h) {
        VisionPlayer p=adapted(h);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,MobEffectInstance.INFINITE_DURATION,2,false,true,true));
        NegativeEffects.maintain(p);advance(p,300);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);NegativeEffects.maintain(p);
        MobEffectInstance restored=p.getEffect(MobEffects.NIGHT_VISION);
        check(h,restored!=null && restored.isInfiniteDuration() && restored.getAmplifier()==2 && restored.showIcon(),
                "An externally supplied infinite effect survives wheel removal with its own metadata");
        NegativeEffects.clear(p);h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void autosavedInfiniteOwnershipAndPotionBackupRecover(GameTestHelper h) {
        VisionPlayer original=adapted(h);
        original.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,600));
        NegativeEffects.maintain(original);advance(original,40);AdaptationEvents.save(original);
        CompoundTag savedData=original.getPersistentData().copy();
        CompoundTag savedEffect=original.getEffect(MobEffects.NIGHT_VISION).save(new CompoundTag());
        NegativeEffects.clear(original);
        VisionPlayer reloaded=new VisionPlayer(h.getLevel());
        reloaded.getPersistentData().merge(savedData);
        // Vanilla reloads active effects directly from NBT, losing Java subclass identity.
        reloaded.getActiveEffectsMap().put(MobEffects.NIGHT_VISION,MobEffectInstance.load(savedEffect));
        NegativeEffects.maintain(reloaded);advance(reloaded,20);
        AdaptationEvents.state(reloaded).setNightVisionEnabled(false);NegativeEffects.maintain(reloaded);
        MobEffectInstance restored=reloaded.getEffect(MobEffects.NIGHT_VISION);
        check(h,restored!=null && !restored.isInfiniteDuration() && restored.getDuration()==540,
                "Reload recovers owned infinity and the potion's remaining duration without counting offline time");
        NegativeEffects.clear(reloaded);h.succeed();
    }

    private static VisionPlayer adapted(GameTestHelper h) {
        VisionPlayer p=new VisionPlayer(h.getLevel());
        var state=AdaptationEvents.state(p);state.hit(AdaptationProfile.DARK_KEY,false);
        int guard=20000;
        while(state.stage(AdaptationProfile.DARK_KEY)<4 && guard-->0) state.tick(true);
        check(h,guard>0,"Darkness adaptation setup reaches full stage");
        return p;
    }
    private static void advance(VisionPlayer p,int ticks) {
        for(int tick=0;tick<ticks;tick++) {
            p.tickCount++;p.tickVision();NegativeEffects.maintain(p);
        }
    }
    private static void check(GameTestHelper h,boolean condition,String message) { if(!condition) h.fail(message); }
    private static final class VisionPlayer extends FakePlayer {
        VisionPlayer(ServerLevel level) {
            super(level,new GameProfile(UUID.randomUUID(),"NightVisionTest"));
            setPos(0,100,0);setHealth(20);setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        }
        void tickVision() { super.tickEffects(); }
    }
}
