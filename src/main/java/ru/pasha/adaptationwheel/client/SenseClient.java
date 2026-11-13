package ru.pasha.adaptationwheel.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.KnobRecord;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.core.OptionKnob;
import ru.pasha.adaptationwheel.server.SenseAdaptations;

/**
 * Client half of the sense adaptations: water and lava fog (Forge's RenderFog event, which also works under
 * OptiFine), outlines of invisible mobs (the coremod asks {@link #revealed}) and the laptop battery.
 * The battery is read by OSHI on a daemon thread, never on the render thread.
 */
@Mod.EventBusSubscriber(modid=AdaptationWheel.MOD_ID,value=Dist.CLIENT)
public final class SenseClient {
    private static final float[] WATER_SCALE={1,1.25F,1.5F,2,3},LAVA_END={0,5,8,12,20};
    private static final double[] REVEAL_RADIUS={0,4,8,16,32};
    private static volatile boolean wanted,onBattery,oshiBroken;
    private static volatile int charge=-1;
    private static Thread poller;
    private static int murky,lava,unseen,ticks,failures;
    private static boolean lavaSafe,warned,parked,recovered;
    private static Screen lastScreen;
    private static String hud="",lastRecord="";
    private static int hudColor;
    private static final OptionKnob FPS=knob(()->Minecraft.getInstance().options.framerateLimit()),DISTANCE=knob(()->Minecraft.getInstance().options.renderDistance());
    private SenseClient(){}

    /** True while the battery adaptation holds the frame limit down (the low-FPS adaptation must not mistake it for lag). */
    public static boolean limitingFrames(){return FPS.applied()>=0;}
    /** True while the battery adaptation holds the render distance down (memory pressure must not cut on top of it). */
    static boolean distanceCut(){return DISTANCE.applied()>=0;}

    /** Coremod hook of Minecraft.shouldEntityAppearGlowing: invisible mobs near the wearer are outlined. */
    public static boolean revealed(Entity e){
        int stage=unseen;if(stage<=0)return false;
        try{
            if(!(e instanceof Mob)||!e.isInvisible())return false;
            var player=Minecraft.getInstance().player;
            return player!=null&&e.distanceToSqr(player)<=REVEAL_RADIUS[stage]*REVEAL_RADIUS[stage];
        }catch(RuntimeException|LinkageError ignored){return false;}
    }

    @SubscribeEvent(priority=EventPriority.LOW,receiveCanceled=true) public static void fog(ViewportEvent.RenderFog e){
        try{
            var player=Minecraft.getInstance().player;
            if(player==null||e.getCamera().getEntity()!=player)return;
            if(e.getType()==FogType.WATER&&murky>0){
                // Blindness and Darkness replace the water fog in vanilla's own chain; leave theirs alone.
                if(player.hasEffect(MobEffects.BLINDNESS)||player.hasEffect(MobEffects.DARKNESS))return;
                float far=e.getFarPlaneDistance(),view=e.getRenderer().getRenderDistance(),want=far*WATER_SCALE[murky];
                float limit=Math.max(far,e.getMode()==FogRenderer.FogMode.FOG_TERRAIN?Math.max(32F,view):view);
                // Like vanilla: never past the loaded world, and a clamped fog is cylindrical.
                if(want>limit){want=limit;e.setFogShape(FogShape.CYLINDER);}
                if(want<=far)return;
                e.setFarPlaneDistance(want);e.setCanceled(true);
            }else if(e.getType()==FogType.LAVA&&lava>0&&lavaSafe&&!player.isSpectator()){
                float want=LAVA_END[lava];if(e.getFarPlaneDistance()>=want)return;
                e.setNearPlaneDistance(0);e.setFarPlaneDistance(want);e.setCanceled(true);
            }
        }catch(RuntimeException|LinkageError error){fail(error);}
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        try{var mc=Minecraft.getInstance();if(!recovered)recover(mc);update(mc);}catch(RuntimeException|LinkageError error){fail(error);}
    }
    private static void update(Minecraft mc){
        var player=mc.player;
        if(player==null||mc.level==null){off();return;}
        ticks++;
        murky=level(MetaMechanic.MURKY);lava=level(MetaMechanic.LAVA_SIGHT);unseen=level(MetaMechanic.UNSEEN);
        if(lava>0){if(ticks%10==0)lavaSafe=harmless(player);}else lavaSafe=false;
        boolean battery=MetaClient.active(MetaMechanic.BATTERY);
        wanted=battery;if(battery)ensurePoller();
        boolean on=battery&&onBattery;
        int stage=on?MetaClient.stage(MetaMechanic.BATTERY):0;
        if(on&&ticks%20==0)MetaClient.report(MetaMechanic.BATTERY);
        if(mc.screen!=lastScreen){lastScreen=mc.screen;parked=optionsScreen(mc.screen);}
        // Stage II: at most 60 FPS, III: 45 and render distance -2, IV: 30 and -4 (never below 6, as in memory pressure).
        int cap=stage>=4?30:stage>=3?45:stage>=2?60:0,cut=stage>=4?4:stage>=3?2:0;
        IntUnaryOperator fpsGoal=cap>0?(base->Math.min(base,cap)):null,distanceGoal=cut>0&&OutsideClient.reducedFrom()<0?(base->(base<=6?base:Math.max(6,base-cut))):null;
        // While memory pressure holds the distance down the knob stays off (apply(null) first gives its own cut back), so the two never overwrite each other's saved value.
        FPS.apply(fpsGoal,parked);DISTANCE.apply(distanceGoal,parked);sync(mc);
        if(stage>=1){if(ticks%10==0)hud=text();}else hud="";
    }
    private static int level(MetaMechanic m){return MetaClient.active(m)?MetaClient.stage(m):0;}
    /** Lava that cannot hurt: Fire Resistance, or the wheel fully adapted to lava and to burning (server rule, from synced entries). */
    private static boolean harmless(LocalPlayer p){
        if(p.hasEffect(MobEffects.FIRE_RESISTANCE))return true;
        var state=ClientWheelState.get(p.getId());if(state==null)return false;
        boolean lavaKnown=false,fireKnown=false;
        for(var entry:state.entries()){
            if(!entry.enabled()||entry.reduction()<100)continue;
            if(entry.key().equals(SenseAdaptations.LAVA_KEY))lavaKnown=true;else if(entry.key().equals(SenseAdaptations.FIRE_KEY))fireKnown=true;
        }
        return lavaKnown&&fireKnown;
    }
    /** Changes made while an options screen is open belong to the player, and saving options there must not store ours. */
    private static boolean optionsScreen(Screen s){
        if(s==null)return false;
        String name=s.getClass().getSimpleName();
        return s instanceof OptionsSubScreen||s instanceof OptionsScreen||name.contains("Option")||name.contains("Video")||name.contains("Setting");
    }
    private static String text(){
        int percent=charge;hudColor=percent>=0&&percent<=20?0xFFFF8F6B:0xFFE8CA85;
        var out=percent<0?Component.translatable("meta.adaptationwheel.laptop_battery.hud.unknown"):Component.translatable("meta.adaptationwheel.laptop_battery.hud",percent);
        if(FPS.applied()>=0)out.append(" · ").append(Component.translatable("meta.adaptationwheel.laptop_battery.hud.fps",FPS.applied()));
        if(DISTANCE.applied()>=0)out.append(" · ").append(Component.translatable("meta.adaptationwheel.laptop_battery.hud.distance",DISTANCE.applied()));
        return out.getString();
    }
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Post e){
        if(hud.isEmpty()||e.getOverlay()!=VanillaGuiOverlay.HOTBAR.type())return;
        var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.options.renderDebug||mc.player==null)return;
        // Top-left: the wheel list is top-right, notices bottom-centre, hotbar and chat at the bottom.
        e.getGuiGraphics().drawString(mc.font,hud,6,6,hudColor,true);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){off();}
    @SubscribeEvent public static void shutdown(GameShuttingDownEvent e){off();}
    /** Gives back exactly what this class changed and forgets the session. */
    private static void off(){
        try{FPS.end();DISTANCE.end();sync(Minecraft.getInstance());}catch(RuntimeException|LinkageError error){fail(error);}
        murky=lava=unseen=0;lavaSafe=false;wanted=false;onBattery=false;charge=-1;hud="";lastScreen=null;parked=false;ticks=0;
    }
    private static void fail(Throwable error){if(!warned){warned=true;LogUtils.getLogger().warn("Sense adaptations failed; they stay quiet from now on",error);}}

    private static OptionKnob knob(Supplier<OptionInstance<Integer>> option){return new OptionKnob(()->option.get().get(),v->option.get().set(v));}
    /**
     * Vanilla saves options.txt during play (F3+H, F3+P, tutorial, resource packs), so a capped value can reach the disk: whenever a value
     * really went back, save once; and while a cap is held a small record in the game folder lets the next launch undo it after a crash.
     */
    private static void sync(Minecraft mc){
        boolean back=FPS.takeRestored();back|=DISTANCE.takeRestored();
        if(back)try{mc.options.save();}catch(RuntimeException error){fail(error);return;}
        var held=new LinkedHashMap<String,KnobRecord.Entry>();
        if(FPS.applied()>=0)held.put("maxFps",new KnobRecord.Entry(FPS.original(),FPS.applied()));
        if(DISTANCE.applied()>=0)held.put("renderDistance",new KnobRecord.Entry(DISTANCE.original(),DISTANCE.applied()));
        String text=KnobRecord.format(held);if(text.equals(lastRecord))return;
        lastRecord=text;
        try{
            Path file=recordFile();
            if(held.isEmpty())Files.deleteIfExists(file);else{Files.createDirectories(file.getParent());Files.writeString(file,text,StandardCharsets.UTF_8);}
        }catch(IOException|RuntimeException error){fail(error);}
    }
    private static Path recordFile(){return FMLPaths.GAMEDIR.get().resolve("adaptationwheel").resolve("battery-options.txt");}
    /** First tick with options loaded: a record left by a killed game means the capped values may be the saved ones, so undo what is still ours. */
    private static void recover(Minecraft mc){
        recovered=true;
        try{
            Path file=recordFile();if(!Files.isRegularFile(file))return;
            boolean changed=false;
            for(var held:KnobRecord.parse(Files.readString(file,StandardCharsets.UTF_8)).entrySet()){
                var option=held.getKey().equals("maxFps")?mc.options.framerateLimit():held.getKey().equals("renderDistance")?mc.options.renderDistance():null;
                int back=option==null?-1:KnobRecord.recover(held.getValue(),option.get());
                if(back>=0){option.set(back);changed=true;}
            }
            if(changed)mc.options.save();
            Files.deleteIfExists(file);
        }catch(IOException|RuntimeException error){fail(error);}
    }

    // ---------- battery ----------
    private static synchronized void ensurePoller(){
        if(poller!=null)return;
        poller=new Thread(SenseClient::pollLoop,"adaptationwheel-battery");poller.setDaemon(true);poller.start();
    }
    private static void pollLoop(){
        long next=0;
        while(true){
            try{Thread.sleep(1000);}catch(InterruptedException stop){return;}
            if(!wanted){next=0;continue;}
            long now=System.nanoTime();if(next!=0&&now<next)continue;
            next=now+10_000_000_000L;poll();
        }
    }
    private static void poll(){
        try{
            int fake=SenseAdaptations.fakeCharge(System.getProperty("adaptationwheel.fakeBattery"));
            if(fake>=0){charge=fake;onBattery=true;return;}
            if(oshiBroken){onBattery=false;return;}
            int[] reading=Oshi.read();
            charge=reading[1];onBattery=reading[0]==1;failures=0;
        }catch(Throwable error){
            // A desktop, a missing native library, a locked-down system: stay "plugged in" and give up after three tries.
            onBattery=false;if(++failures>=3)oshiBroken=true;
        }
    }
    /** Kept in its own class so that nothing about OSHI is linked until the poller really asks. */
    private static final class Oshi{
        private static oshi.hardware.HardwareAbstractionLayer hardware;
        static int[] read(){
            if(hardware==null)hardware=new oshi.SystemInfo().getHardware();
            boolean battery=false;double sum=0;int counted=0;
            for(var source:hardware.getPowerSources()){
                if(!SenseAdaptations.runsOnBattery(source.isPowerOnLine(),source.isDischarging()))continue;
                battery=true;double part=source.getRemainingCapacityPercent();
                if(part>=0&&part<=1.0001){sum+=part;counted++;}
            }
            return new int[]{battery?1:0,counted==0?-1:(int)Math.round(100*sum/counted)};
        }
    }
}
