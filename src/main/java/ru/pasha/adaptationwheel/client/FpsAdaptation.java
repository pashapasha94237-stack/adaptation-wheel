package ru.pasha.adaptationwheel.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.FpsAdaptationConfig;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.FpsAdaptationState;
import ru.pasha.adaptationwheel.core.FpsFrameSampler;
import ru.pasha.adaptationwheel.core.StageDurations;
import ru.pasha.adaptationwheel.core.StageProtections;
import ru.pasha.adaptationwheel.net.CatalogueEntry;
import ru.pasha.adaptationwheel.net.WheelEntry;

import java.lang.reflect.Method;

/** Local frame-time adaptation; never changes server health, damage or tick rate. */
public final class FpsAdaptation {
    public static final String KEY = "client:low_fps";
    private static final StageDurations DURATIONS = StageDurations.seconds(20,50,80,110,140);
    private static final CatalogueEntry CATALOGUE = new CatalogueEntry(KEY,"adaptationwheel.fps.name","Low FPS",AdaptationProfile.ENVIRONMENT,DURATIONS);
    private static final StageProtections NO_DAMAGE_BONUS = new StageProtections(0,0,0,0);
    private static final FpsAdaptationState STATE = new FpsAdaptationState();
    private static final FpsFrameSampler FRAMES = new FpsFrameSampler();
    private static boolean loaded, checkedApi, bridgeFailed, boostApplied;
    private static Method boostSetter;
    private static long lastSaved, turnStarted;
    private static int measuredFps;
    private static float turnFrom, turnTarget;
    private static WheelEntry cachedEntry;
    private static long smokeNaturalStarted, smokeNaturalFrames, smokeNaturalEligibleFrames;

    private static void load() {
        if(loaded) return;
        loaded=true;
        STATE.restore(FpsAdaptationConfig.STAGE.get(),FpsAdaptationConfig.WORK.get(),FpsAdaptationConfig.STARTED.get());
        turnFrom=turnTarget=STATE.level()*Mth.PI/4;
        refreshEntry();
    }

    public static boolean eligible() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.screen!=null || mc.getOverlay()!=null
                || mc.isPaused() || !mc.isWindowActive() || !mc.player.isAlive() || mc.player.isSpectator()) return false;
        // The battery adaptation caps frames on purpose; that cap is not low FPS to adapt to.
        if(SenseClient.limitingFrames()) return false;
        var state=ClientWheelState.get(mc.player.getId());
        return state!=null && state.equipped() && state.enabled() && enabled();
    }

    public static void frame() {
        load();
        if(smokeNaturalStarted!=0) {
            smokeNaturalFrames++;
            if(eligible()) smokeNaturalEligibleFrames++;
        }
        if(!eligible()) { pauseWindow(); return; }
        long now=System.nanoTime();
        var measurement=FRAMES.frame(now);
        if(measurement==null) return;
        if(!measurement.valid()) { STATE.interruptQualification();refreshEntry();return; }
        acceptMeasurement(measurement.fps(),measurement.seconds(),now);
    }

    private static void acceptMeasurement(double fps,double seconds,long now) {
        measuredFps=(int)Math.round(fps);
        if(smokeNaturalStarted!=0) LogUtils.getLogger().info("FPS_NATURAL_SAMPLE fps={}, seconds={}, stage={}, qualifying={}, work={}",
                fps,seconds,STATE.level(),STATE.qualifyingSeconds(),STATE.currentElapsedSeconds());
        boolean wasStarted=STATE.started();
        int completed=0;
        // A valid low-FPS window can straddle the one-second measurement boundary.
        // Individual frame gaps were already checked; split the window without rounding FPS.
        while(seconds>0) {
            double chunk=Math.min(FpsAdaptationState.MAX_SAMPLE_SECONDS,seconds);
            int stage=STATE.sample(fps,chunk,true);
            if(stage>0) completed=stage;
            seconds-=chunk;
        }
        refreshEntry();
        if(!wasStarted && STATE.started()) Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("adaptationwheel.fps.started"),false);
        if(completed>0) completeStage(completed,now);
    }

    /** Checks equipment even on low frame rates; learned work survives temporary pauses. */
    public static void tick() {
        load();
        Minecraft mc=Minecraft.getInstance();
        var state=mc.player==null?null:ClientWheelState.get(mc.player.getId());
        boolean equipped=state!=null && state.equipped() && state.enabled() && enabled()
                && mc.player.isAlive() && !mc.player.isSpectator();
        if (!equipped && STATE.qualifyingSeconds()>0 && !STATE.started()) {
            STATE.interruptQualification();
            refreshEntry();
        }
        applyBoost(equipped && STATE.completed());
        if(!eligible()) pauseWindow();
        long now=System.nanoTime();
        if(STATE.started() && now-lastSaved>30_000_000_000L) save();
    }

    private static void completeStage(int stage,long now) {
        var mc=Minecraft.getInstance();
        turnFrom=angle(0); turnTarget+=Mth.PI/4; turnStarted=now;
        mc.player.playSound(AdaptationWheel.ADAPTED_SOUND.get(),0.8F,1.0F);
        mc.player.displayClientMessage(Component.translatable("adaptationwheel.fps.stage",stage),false);
        save();
        if(stage==4) {
            applyBoost(true);
            mc.gui.setTimes(10,70,20);
            mc.gui.setTitle(Component.translatable("adaptationwheel.fps.title"));
            mc.gui.setSubtitle(Component.translatable(optimizerAvailable()?"adaptationwheel.fps.subtitle":"adaptationwheel.fps.missing"));
        }
    }

    private static void pauseWindow() {
        FRAMES.pause();
        if(STATE.progressing()) { STATE.pause(); refreshEntry(); }
    }

    private static void refreshEntry() {
        cachedEntry=(STATE.started() || STATE.qualifyingSeconds()>0)?new WheelEntry(KEY,"adaptationwheel.fps.name",enabled(),false,STATE.level(),
                STATE.currentElapsedSeconds()*20,0,true,STATE.progressing(),AdaptationProfile.ENVIRONMENT,DURATIONS,0,
                NO_DAMAGE_BONUS,false,false):null;
    }

    public static WheelEntry entry() {
        load();
        if(cachedEntry!=null && cachedEntry.enabled()!=enabled()) refreshEntry();
        return cachedEntry;
    }
    public static CatalogueEntry catalogue() { return CATALOGUE; }
    public static boolean enabled() { return FpsAdaptationConfig.ENABLED.get(); }
    public static int measuredFps() { return measuredFps; }
    public static boolean completed() { load(); return STATE.completed(); }
    public static boolean qualifying() { load(); return !STATE.started() && STATE.qualifyingSeconds()>0; }
    public static double qualificationRemainingSeconds() { load(); return Math.max(0,FpsAdaptationState.REQUIRED_LOW_FPS_SECONDS-STATE.qualifyingSeconds()); }
    public static void setEnabled(boolean value) {
        load(); ru.pasha.adaptationwheel.ConfigWatch.ownSave(FpsAdaptationConfig.SPEC,()->FpsAdaptationConfig.ENABLED.set(value)); pauseWindow(); refreshEntry();
        if(!value) { applyBoost(false);STATE.interruptQualification();refreshEntry(); }
        save();
    }
    public static float angle(float partialTick) {
        if(turnStarted==0) return turnTarget;
        float t=Mth.clamp((System.nanoTime()-turnStarted)/900_000_000.0F,0,1);
        return Mth.lerp(1-(1-t)*(1-t)*(1-t),turnFrom,turnTarget);
    }
    public static float completionAge(float partialTick) {
        return turnStarted==0?Float.MAX_VALUE:(System.nanoTime()-turnStarted)/50_000_000.0F;
    }
    private static void save() {
        if(!loaded) return;
        // Each set() already saves the file (Forge autosave), so all of them count as the wheel's own write.
        ru.pasha.adaptationwheel.ConfigWatch.ownSave(FpsAdaptationConfig.SPEC,()->{
            FpsAdaptationConfig.STAGE.set(STATE.level());
            FpsAdaptationConfig.WORK.set(STATE.currentElapsedSeconds());
            FpsAdaptationConfig.STARTED.set(STATE.started());
            FpsAdaptationConfig.SPEC.save();
        });
        lastSaved=System.nanoTime();
    }
    public static void logout() {
        if(loaded) save();
        applyBoost(false); pauseWindow(); measuredFps=0;
        STATE.interruptQualification();refreshEntry();
        if(loaded) turnFrom=turnTarget=STATE.level()*Mth.PI/4;
        turnStarted=0;
    }
    public static boolean optimizerAvailable() {
        if(!checkedApi) {
            checkedApi=true;
            try {
                boostSetter=Class.forName("ru.pasha.pashaboost.api.FpsAdaptationBoost").getMethod("setActive",boolean.class);
            } catch(ReflectiveOperationException | LinkageError absent) { boostSetter=null; }
        }
        return boostSetter!=null && !bridgeFailed;
    }
    private static void applyBoost(boolean wanted) {
        if(wanted==boostApplied || !optimizerAvailable()) return;
        try {
            boostSetter.invoke(null,wanted);
            boostApplied=wanted;
        } catch(ReflectiveOperationException | LinkageError error) {
            bridgeFailed=true;
            LogUtils.getLogger().warn("FPS adaptation could not activate the optional Adapt Boost integration",error);
        }
    }

    /** Controlled production-client assertions; inaccessible unless the explicit test flag is set. */
    public static void smokePrepareNatural() {
        if(!Boolean.getBoolean("adaptationwheel.fps.smoke")) throw new IllegalStateException("Smoke mode required");
        load();
        var mc=Minecraft.getInstance();
        STATE.reset();applyBoost(false);pauseWindow();
        FpsAdaptationConfig.ENABLED.set(true);refreshEntry();
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(30);
        mc.setScreen(null);
        smokeNaturalStarted=System.nanoTime();smokeNaturalFrames=smokeNaturalEligibleFrames=0;
        var uuid=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(AdaptationWheel.WHEEL.get()));
            ru.pasha.adaptationwheel.server.AdaptationEvents.state(player).setEnabled(true);
            ru.pasha.adaptationwheel.server.AdaptationEvents.saveAndSync(player);
        });
        LogUtils.getLogger().info("FPS_NATURAL_BEGIN: fresh stage 0, real render frames, cap 30, ordinary eligibility");
    }

    /** Called after at least ten seconds of focused, unobstructed gameplay; never injects FPS or work. */
    public static void smokeCheckNatural() {
        if(!Boolean.getBoolean("adaptationwheel.fps.smoke") || smokeNaturalStarted==0) throw new IllegalStateException("Natural smoke was not started");
        var mc=Minecraft.getInstance();
        var wheel=ClientWheelState.get(mc.player.getId());
        String diagnostics="elapsed="+((System.nanoTime()-smokeNaturalStarted)/1_000_000_000.0)
                +", callbacks="+smokeNaturalFrames+", eligibleFrames="+smokeNaturalEligibleFrames
                +", measuredFps="+measuredFps+", vanillaFps="+mc.getFps()+", focused="+mc.isWindowActive()
                +", screen="+(mc.screen==null?"none":mc.screen.getClass().getSimpleName())+", paused="+mc.isPaused()
                +", overlay="+(mc.getOverlay()!=null)+", physicalWheel="+mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(AdaptationWheel.WHEEL.get())
                +", synced="+(wheel!=null)+", equipped="+(wheel!=null && wheel.equipped())+", enabled="+(wheel!=null && wheel.enabled())
                +", stage="+STATE.level()+", started="+STATE.started()+", qualifying="+STATE.qualifyingSeconds()+", work="+STATE.currentElapsedSeconds();
        if(!eligible() || !STATE.started() || STATE.level()!=0 || STATE.currentElapsedSeconds()<1
                || smokeNaturalEligibleFrames<100 || measuredFps<1 || measuredFps>59 || boostApplied) {
            throw new IllegalStateException("Natural 30 FPS adaptation failed: "+diagnostics);
        }
        LogUtils.getLogger().info("FPS_ADAPTATION_NATURAL PASS: {}",diagnostics);
        smokeNaturalStarted=0;
    }

    public static void smokePrepare() {
        if(!Boolean.getBoolean("adaptationwheel.fps.smoke")) throw new IllegalStateException("Smoke mode required");
        var mc=Minecraft.getInstance();
        var uuid=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(AdaptationWheel.WHEEL.get()));
        });
    }

    public static void smokeCheck() {
        if(!Boolean.getBoolean("adaptationwheel.fps.smoke")) throw new IllegalStateException("Smoke mode required");
        load();
        var mc=Minecraft.getInstance();
        var wheel=ClientWheelState.get(mc.player.getId());
        if(wheel==null || !wheel.equipped()) throw new IllegalStateException("Worn wheel did not synchronize");
        if(!optimizerAvailable()) throw new IllegalStateException("Adapt Boost runtime API missing");
        STATE.reset(); applyBoost(false); refreshEntry();
        for(int i=0;i<20;i++) acceptMeasurement(60,1,System.nanoTime());
        for(int i=0;i<20;i++) acceptMeasurement(59.4,1,System.nanoTime());
        if(STATE.started()) throw new IllegalStateException("60 FPS incorrectly started adaptation");
        for(int i=0;i<5;i++) acceptMeasurement(59,1,System.nanoTime());
        if(!STATE.started()) throw new IllegalStateException("59 FPS did not qualify");
        for(int i=0;i<20;i++) acceptMeasurement(1,1,System.nanoTime());
        if(STATE.level()!=1) throw new IllegalStateException("1 FPS did not complete the first stage");
        if(boostApplied) throw new IllegalStateException("Optimization unlocked before the last stage");
        for(int i=0;i<240;i++) acceptMeasurement(59,1,System.nanoTime());
        tick();
        if(!STATE.completed() || !boostApplied) throw new IllegalStateException("Fourth stage did not activate optimization");
        setEnabled(false);
        if(boostApplied) throw new IllegalStateException("FPS entry toggle did not release optimization");
        setEnabled(true); tick();
        if(!boostApplied) throw new IllegalStateException("Earned FPS optimization did not resume");
        // Verify the actual catalogue/detail view also works with the new local entry.
        AdaptationScreen.openFpsDetails();
        LogUtils.getLogger().info("FPS_ADAPTATION_SMOKE PASS: 1/59 accepted, 60 excluded, four stages, sound, wheel turns, J toggle and Pasha Boost bridge");
    }

    public static void smokeUnequip() {
        if(!Boolean.getBoolean("adaptationwheel.fps.smoke")) throw new IllegalStateException("Smoke mode required");
        var mc=Minecraft.getInstance();
        mc.setScreen(null);
        var uuid=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)
                .setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.item.ItemStack.EMPTY));
    }
    private FpsAdaptation() {}
}
