package ru.pasha.adaptationwheel.client;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.MetaConfig;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.MetaNetwork;

/** Client observations are bounded; the server alone awards utility stages. */
@Mod.EventBusSubscriber(modid=AdaptationWheel.MOD_ID,value=Dist.CLIENT)
public final class MetaClient {
    private record Heard(Vec3 pos,String key,long expires){}
    private static final Deque<Heard> HEARD=new ArrayDeque<>();
    private static final NotificationMemory NOTIFICATIONS=new NotificationMemory();
    private static final EnumMap<MetaMechanic,Integer> STAGES=new EnumMap<>(MetaMechanic.class);
    private static final Set<MetaMechanic> ENABLED=EnumSet.noneOf(MetaMechanic.class);
    private static final EnumMap<MetaMechanic,Long> SENT=new EnumMap<>(MetaMechanic.class);
    private static boolean equipped;
    private static int ticks,seenChunks;
    private static final SustainedPressure HEAP_WINDOW=new SustainedPressure();
    private static double heapRatio;
    private static String noise="",warning="",trailDimension="";
    private static long noiseUntil,warnUntil,trailUntil;
    private static List<Vec3> trail=List.of();
    private static Component forwardedOverlay;
    private MetaClient(){}
    public static boolean active(MetaMechanic m){return equipped&&ENABLED.contains(m)&&MetaConfig.enabled(m);}
    public static int stage(MetaMechanic m){return STAGES.getOrDefault(m,0);}
    public static boolean full(MetaMechanic m){return active(m)&&stage(m)>=4;}
    public static double heapRatio(){return heapRatio;}
    private static void expose(MetaMechanic m){
        if(!equipped)return;long now=System.nanoTime();if(now-SENT.getOrDefault(m,Long.MIN_VALUE/2)<5_000_000_000L)return;
        SENT.put(m,now);MetaNetwork.action(m.key);
    }
    public static void report(MetaMechanic m){expose(m);}
    public static void tick(){
        ticks++;Minecraft mc=Minecraft.getInstance();var state=mc.player==null?null:ClientWheelState.get(mc.player.getId());
        equipped=mc.player!=null&&mc.player.isAlive()&&state!=null&&state.enabled()&&state.equipped();ENABLED.clear();STAGES.clear();
        if(state!=null)for(var e:state.entries()){var m=MetaMechanic.fromKey(e.key());if(m!=null){STAGES.put(m,e.stage());if(e.enabled())ENABLED.add(m);}}
        for(var m:MetaMechanic.ALL)if(!STAGES.containsKey(m))ENABLED.add(m);
        LandscapeMemory.tick();if(mc.level==null||mc.player==null||mc.isPaused()){HEAP_WINDOW.clear();return;}
        if(ticks%20==0){var usage=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();long max=usage.getMax()>0?usage.getMax():Runtime.getRuntime().maxMemory();heapRatio=max>0?(double)usage.getUsed()/max:0;
            double threshold=MetaConfig.CLIENT.isLoaded()?MetaConfig.HEAP_PRESSURE.get():.85;
            if(HEAP_WINDOW.sample(System.nanoTime(),equipped&&heapRatio>=threshold,10_000_000_000L,3_000_000_000L))expose(MetaMechanic.MEMORY);
            OutsideClient.second(heapRatio,threshold);
            if(equipped&&seenChunks>=4)expose(MetaMechanic.LANDSCAPE);
            if(full(MetaMechanic.BORDER)&&mc.screen==null&&mc.player.input.forwardImpulse>0&&Math.abs(mc.level.getWorldBorder().getDistanceToBorder(mc.player))<2){
                var dir=mc.player.getLookAngle();var b=mc.level.getWorldBorder();double x=mc.player.getX(),z=mc.player.getZ();
                boolean out=x<b.getMinX()||x>b.getMaxX()||z<b.getMinZ()||z>b.getMaxZ();
                if(out||x-b.getMinX()<2&&dir.x<-.4||b.getMaxX()-x<2&&dir.x>.4||z-b.getMinZ()<2&&dir.z<-.4||b.getMaxZ()-z<2&&dir.z>.4)MetaNetwork.action("border");
            }
        }
    }
    @SubscribeEvent public static void chunk(net.minecraftforge.event.level.ChunkEvent.Load e){if(e.getLevel().isClientSide())seenChunks=Math.min(100,seenChunks+1);}
    public static boolean overlay(Component component){if(component==forwardedOverlay){forwardedOverlay=null;return false;}return message(component,false);}
    private static boolean message(Component component,boolean playerChat){
        if(!equipped||!active(MetaMechanic.NOISE)||playerChat)return false;
        long now=System.currentTimeMillis();var repeat=NOTIFICATIONS.receive(component.getString(),now,full(MetaMechanic.NOISE));
        if(repeat.count()>=5)expose(MetaMechanic.NOISE);
        if(repeat.count()>1&&stage(MetaMechanic.NOISE)>0){noise=component.getString()+" ×"+repeat.count();noiseUntil=now+4000;}
        return repeat.group();
    }
    @SubscribeEvent public static void chat(ClientChatReceivedEvent e){if(message(e.getMessage(),!e.isSystem()))e.setCanceled(true);else if(e instanceof ClientChatReceivedEvent.System system&&system.isOverlay())forwardedOverlay=e.getMessage();}
    @SubscribeEvent public static void sound(PlaySoundEvent e){
        var thread=Minecraft.getInstance();if(!thread.isSameThread()){var sound=e.getSound();thread.execute(()->observeSound(sound));return;}
        observeSound(e.getSound());
    }
    private static void observeSound(net.minecraft.client.resources.sounds.SoundInstance s){
        var mc=Minecraft.getInstance();if(!equipped||!active(MetaMechanic.SILENCE)||s==null||mc.player==null||s.isRelative())return;
        if(mc.options.getSoundSourceVolume(SoundSource.MASTER)>0.001)return;
        String path=s.getLocation().getPath();if(s.getSource()==SoundSource.MUSIC||s.getSource()==SoundSource.RECORDS||!path.matches(".*(step|shoot|arrow|bow|attack|hurt|explode|fuse|roar|growl|break|hit).*"))return;
        Vec3 pos=new Vec3(s.getX(),s.getY(),s.getZ());if(pos.distanceToSqr(mc.player.position())>64*64)return;
        // The wheel reports what the player cannot hear: their own steps, swings, shots,
        // injuries, mining and mount are already known and must neither show nor train.
        if(ownSound(mc,s.getSource(),path,pos))return;
        expose(MetaMechanic.SILENCE);if(HEARD.size()>=12)HEARD.removeFirst();
        String key=path.contains("step")?"meta.adaptationwheel.sound.steps":path.contains("shoot")||path.contains("arrow")||path.contains("bow")?"meta.adaptationwheel.sound.shot":"meta.adaptationwheel.sound.danger";
        HEARD.addLast(new Heard(pos,key,System.currentTimeMillis()+2500));
    }
    private static boolean ownSound(Minecraft mc,SoundSource source,String path,Vec3 pos){
        var self=mc.player;
        if(source==SoundSource.PLAYERS){
            // A player's own steps, swings and hurt sounds use the PLAYERS source at their feet. Another player in the
            // same spot is the only ambiguity, so attribute the sound to whoever is closest.
            double own=pos.distanceToSqr(self.position());
            if(own<=2.5*2.5){
                boolean otherCloser=false;
                for(var other:mc.level.players())if(other!=self&&other.position().distanceToSqr(pos)<own){otherCloser=true;break;}
                if(!otherCloser)return true;
            }
        }
        var vehicle=self.getVehicle();
        if(vehicle!=null&&pos.distanceToSqr(vehicle.position())<=3*3)return true;
        if(source==SoundSource.BLOCKS&&(path.contains("hit")||path.contains("break")||path.contains("step"))
                &&mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult block&&mc.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                &&net.minecraft.world.phys.Vec3.atCenterOf(block.getBlockPos()).distanceToSqr(pos)<=1.5*1.5)return true;
        return false;
    }
    public static void warning(String dimension,String danger,List<Vec3> points,boolean alert){
        trailDimension=dimension;trail=List.copyOf(points);trailUntil=System.currentTimeMillis()+15000;
        if(alert){warning=Component.translatable("meta.adaptationwheel.defeat.warning").getString();warnUntil=System.currentTimeMillis()+3500;}
    }
    public static void render(GuiGraphics g,int width,int height){
        if(!equipped)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();
        if(active(MetaMechanic.NOISE)&&now<noiseUntil)g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(noise,width-30),width/2,height-72,0xFFEBC568);
        if(full(MetaMechanic.DEFEAT)&&now<warnUntil){g.fill(width/2-135,30,width/2+135,52,0xC0201515);g.drawCenteredString(mc.font,warning,width/2,37,0xFFFFB078);}
        HEARD.removeIf(s->now>s.expires);
        if(active(MetaMechanic.SILENCE)&&stage(MetaMechanic.SILENCE)>0&&mc.player!=null){
            for(Heard h:HEARD){double dx=h.pos.x-mc.player.getX(),dz=h.pos.z-mc.player.getZ();double angle=Math.atan2(-dx,dz)-Math.toRadians(mc.player.getYRot());int x=width/2+(int)(Math.sin(angle)*90),y=height/2-(int)(Math.cos(angle)*55);
                int color=stage(MetaMechanic.SILENCE)==1?0xFFB9A879:0xFFEBC568;String text=stage(MetaMechanic.SILENCE)<2?"◆":Component.translatable(h.key).getString();
                if(stage(MetaMechanic.SILENCE)>=4){double dy=h.pos.y-mc.player.getY();if(Math.abs(dy)>3)text+=(dy>0?" ↑":" ↓");}
                g.drawCenteredString(mc.font,text,x,y,color);
            }
        }
    }
    @SubscribeEvent public static void ghost(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!active(MetaMechanic.DEFEAT)||stage(MetaMechanic.DEFEAT)<1||System.currentTimeMillis()>trailUntil||trail.isEmpty()||!MetaConfig.GHOSTS.get())return;
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().toString().equals(trailDimension))return;
        var pose=e.getPoseStack();var camera=e.getCamera().getPosition();var out=mc.renderBuffers().bufferSource();var lines=out.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(int i=0;i<trail.size();i+=3){Vec3 p=trail.get(i);if(p.distanceToSqr(camera)>128*128)continue;net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose,lines,p.x-.3,p.y,p.z-.3,p.x+.3,p.y+1.8,p.z+.3,.55F,.75F,1F,.24F);}
        pose.popPose();out.endBatch(net.minecraft.client.renderer.RenderType.lines());
    }
    public static void logout(){equipped=false;ticks=seenChunks=0;HEAP_WINDOW.clear();heapRatio=0;STAGES.clear();ENABLED.clear();SENT.clear();HEARD.clear();NOTIFICATIONS.clear();forwardedOverlay=null;noise=warning="";trail=List.of();LandscapeMemory.logout();OutsideClient.logout();}

    @SubscribeEvent public static void inventory(net.minecraftforge.client.event.ScreenEvent.Render.Post e){
        if(!active(MetaMechanic.POCKET)||stage(MetaMechanic.POCKET)<1||!(e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen))return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;var slots=screen.getMenu().slots;
        for(var slot:slots){var item=slot.getItem();if(slot.container!=mc.player.getInventory()||item.isEmpty()||!item.isStackable())continue;
            boolean duplicate=false;for(var other:slots)if(other!=slot&&other.container==slot.container&&net.minecraft.world.item.ItemStack.isSameItemSameTags(item,other.getItem())){duplicate=true;break;}
            if(duplicate)e.getGuiGraphics().renderOutline(screen.getGuiLeft()+slot.x-1,screen.getGuiTop()+slot.y-1,18,18,0xFFB99B50);
        }
    }
}
