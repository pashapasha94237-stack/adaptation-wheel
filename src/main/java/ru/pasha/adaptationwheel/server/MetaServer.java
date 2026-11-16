package ru.pasha.adaptationwheel.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import ru.pasha.adaptationwheel.MetaConfig;
import ru.pasha.adaptationwheel.core.*;

/** Server-authoritative progression and per-player border access. Never changes the shared border. */
public final class MetaServer {
    private static final Map<ServerPlayer,EnumMap<MetaMechanic,Long>> HEARTBEATS=new WeakHashMap<>(),ATTEMPTS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Integer> BORDER_COOLDOWN=new WeakHashMap<>();
    private static long tickStart,slowNanos;
    private static double measuredTps=20;
    private MetaServer(){}
    public static boolean active(ServerPlayer p,MetaMechanic m){return AdaptationEvents.active(p)&&MetaConfig.enabled(m)&&AdaptationEvents.state(p).categoryEnabled(m.key);}
    public static boolean full(ServerPlayer p,MetaMechanic m){return active(p,m)&&AdaptationEvents.state(p).stage(m.key)>=4;}
    public static void expose(ServerPlayer p,MetaMechanic m){
        if(!active(p,m))return;
        long now=p.serverLevel().getGameTime();var times=HEARTBEATS.computeIfAbsent(p,x->new EnumMap<>(MetaMechanic.class));
        if(now-times.getOrDefault(m,Long.MIN_VALUE/2)<20)return;times.put(m,now);
        // A continuous condition is not a new hit every tick and cannot farm acceleration.
        if(!AdaptationEvents.state(p).pending().containsKey(m.key)&&AdaptationEvents.state(p).stage(m.key)<4){
            AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);AdaptationEvents.saveAndSync(p);
        }
    }
    /**
     * A discrete refusal (a bed that will not let you sleep, a tool that broke): the first one starts the stage and
     * each later one speeds it up like a repeated hit. A burst within two seconds (spam-clicking a bed) counts once.
     */
    public static void attempt(ServerPlayer p,MetaMechanic m){
        if(!active(p,m))return;
        long now=p.serverLevel().getGameTime();var times=ATTEMPTS.computeIfAbsent(p,x->new EnumMap<>(MetaMechanic.class));
        if(now-times.getOrDefault(m,Long.MIN_VALUE/2)<40)return;times.put(m,now);
        if(AdaptationEvents.state(p).stage(m.key)<4){AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);AdaptationEvents.saveAndSync(p);}
    }
    public static boolean tpsProfile(ServerPlayer p){return full(p,MetaMechanic.TPS);}
    public static double measuredTps(){return measuredTps;}
    public static double distanceToBorder(ServerPlayer p){return p.serverLevel().getWorldBorder().getDistanceToBorder(p);}
    public static boolean outside(ServerPlayer p){return distanceToBorder(p)<0;}
    public static void cross(ServerPlayer p){
        if(!full(p,MetaMechanic.BORDER)||p.tickCount-BORDER_COOLDOWN.getOrDefault(p,-100)<20)return;
        var b=p.serverLevel().getWorldBorder();double minX=b.getMinX(),maxX=b.getMaxX(),minZ=b.getMinZ(),maxZ=b.getMaxZ();
        if(Math.abs(distanceToBorder(p))>6||b.getSize()<16)return;
        double x=p.getX(),z=p.getZ();double[] gaps={Math.abs(x-minX),Math.abs(x-maxX),Math.abs(z-minZ),Math.abs(z-maxZ)};
        int side=0;for(int i=1;i<4;i++)if(gaps[i]<gaps[side])side=i;
        boolean out=outside(p);
        if(side==0)x=minX+(out?4:-4);if(side==1)x=maxX+(out?-4:4);if(side==2)z=minZ+(out?4:-4);if(side==3)z=maxZ+(out?-4:4);
        double entryX=p.getX(),entryY=p.getY(),entryZ=p.getZ();
        if(safeTeleport(p,x,z)){
            BORDER_COOLDOWN.put(p,p.tickCount);tag(p).putBoolean("Outside",!out);
            if(!out){var anchor=new CompoundTag();anchor.putDouble("X",entryX);anchor.putDouble("Y",entryY);anchor.putDouble("Z",entryZ);anchor.putString("Dimension",p.level().dimension().location().toString());tag(p).put("BorderAnchor",anchor);}
        }
    }
    public static CompoundTag meta(ServerPlayer p){return tag(p);}
    private static CompoundTag tag(ServerPlayer p){var data=p.getPersistentData();if(!data.contains("adaptationwheel_meta"))data.put("adaptationwheel_meta",new CompoundTag());return data.getCompound("adaptationwheel_meta");}
    private static boolean safeTeleport(ServerPlayer p,double x,double z){
        return safeTeleport(p,x,z,p.getY());
    }
    private static boolean safeTeleport(ServerPlayer p,double x,double z,double preferredY){
        if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>29_999_980||Math.abs(z)>29_999_980)return false;
        var level=p.serverLevel();int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        // The destination is one nearby chunk. This never expands the shared world border.
        level.getChunk(bx>>4,bz>>4);int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,bx,bz);
        int original=(int)Math.floor(preferredY);
        for(int y:new int[]{original,surface,surface+1}){
            if(y<=level.getMinBuildHeight()||y>=level.getMaxBuildHeight()-2)continue;
            BlockPos pos=new BlockPos(bx,y,bz);
            if(!level.getBlockState(pos).isAir()||!level.getBlockState(pos.above()).isAir()||level.getFluidState(pos.below()).isSource()||level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty())continue;
            p.teleportTo(x,y,z);p.fallDistance=0;p.setDeltaMovement(0,0,0);return true;
        }
        p.displayClientMessage(Component.translatable("meta.adaptationwheel.border.no_safe_exit"),true);return false;
    }
    public static void returnInside(ServerPlayer p){
        var b=p.serverLevel().getWorldBorder();double margin=Math.min(4,b.getSize()/4);
        if(!outside(p)){tag(p).putBoolean("Outside",false);return;}
        var anchor=tag(p).getCompound("BorderAnchor");
        if(p.level().dimension().location().toString().equals(anchor.getString("Dimension"))&&b.isWithinBounds(new BlockPos((int)Math.floor(anchor.getDouble("X")),0,(int)Math.floor(anchor.getDouble("Z"))))){
            if(safeTeleport(p,anchor.getDouble("X"),anchor.getDouble("Z"),anchor.getDouble("Y"))){tag(p).putBoolean("Outside",false);return;}
        }
        double x=Math.max(b.getMinX()+margin,Math.min(b.getMaxX()-margin,p.getX()));
        double z=Math.max(b.getMinZ()+margin,Math.min(b.getMaxZ()-margin,p.getZ()));
        if(safeTeleport(p,x,z)){tag(p).putBoolean("Outside",false);return;}
        // A destroyed return point must not strand the player outside: search nearby solid ground.
        for(int radius=2;radius<=16;radius+=2)for(int side=0;side<8;side++){
            double xx=x+Math.cos(side*Math.PI/4)*radius,zz=z+Math.sin(side*Math.PI/4)*radius;
            if(b.isWithinBounds(new BlockPos((int)Math.floor(xx),0,(int)Math.floor(zz)))&&safeTeleport(p,xx,zz)){tag(p).putBoolean("Outside",false);return;}
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void borderDamage(LivingAttackEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&event.getSource().is(DamageTypes.OUTSIDE_BORDER)&&full(p,MetaMechanic.BORDER)&&distanceToBorder(p)>=-MetaConfig.BORDER_REACH.get())event.setCanceled(true);
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.START){tickStart=System.nanoTime();return;}
        if(tickStart==0)return;long duration=Math.max(0,System.nanoTime()-tickStart);
        // Only actual tick work contributes; sleeping to maintain 20 TPS is not lag.
        measuredTps=measuredTps*.9+Math.min(20,1_000_000_000.0/Math.max(50_000_000,duration))*.1;
        if(measuredTps<(MetaConfig.SERVER.isLoaded()?MetaConfig.LOW_TPS.get():18))slowNanos+=duration;else slowNanos=0;
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||!p.isAlive())return;
        if(p.tickCount%20!=0)return;
        if(tag(p).getBoolean("Outside")&&!outside(p))tag(p).putBoolean("Outside",false);
        if(tag(p).getBoolean("Outside") && (!full(p,MetaMechanic.BORDER)||distanceToBorder(p)<-MetaConfig.BORDER_REACH.get()))returnInside(p);
        OutsideServer.playerTick(p);
        InventoryPressure.returnPocket(p);
        if(!AdaptationEvents.active(p))return;
        if(Math.abs(distanceToBorder(p))<3)expose(p,MetaMechanic.BORDER);
        if(InventoryPressure.occupied(p)>=33){expose(p,MetaMechanic.POCKET);InventoryPressure.compact(p);}
        if(slowNanos>=10_000_000_000L)expose(p,MetaMechanic.TPS);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer replacement){replacement.getPersistentData().put("adaptationwheel_meta",old.getPersistentData().getCompound("adaptationwheel_meta").copy());if(e.isWasDeath())tag(replacement).putBoolean("Outside",false);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){HEARTBEATS.remove(p);ATTEMPTS.remove(p);BORDER_COOLDOWN.remove(p);}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){HEARTBEATS.clear();ATTEMPTS.clear();BORDER_COOLDOWN.clear();tickStart=slowNanos=0;measuredTps=20;}
}
