package ru.pasha.adaptationwheel.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.*;
import ru.pasha.adaptationwheel.MetaHooks;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/**
 * Rest: sleeping by day, next to monsters, without phantoms, in a bed of a foreign dimension. Each adaptation lifts the
 * limit vanilla puts in front of the wearer; vanilla's own rules (enough players asleep, damage wakes you) stay.
 */
public final class RestAdaptations {
    private static final String BED="ForeignBed";
    private static final int CONFIRM_TICKS=100;
    /** Vanilla shows its own refusal right after our event handler, so our message waits for the next player tick. */
    private static final Map<ServerPlayer,Component> LATER=new WeakHashMap<>();
    /** DAY: whether the last attempt was made by day. A night sleep that runs past dawn stays a vanilla night sleep. */
    private static final Map<ServerPlayer,Boolean> MORNING=new WeakHashMap<>(),DAY=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> WARNED=new WeakHashMap<>(),NIGHT=new WeakHashMap<>();
    /**
     * PlayerList.respawn re-applies the old vanilla spawn to the new player between Clone and the respawn event: not a new
     * spawn. Keyed by UUID and server tick: Entity equality is the entity id, which the new player takes over only after Clone.
     */
    private static final Map<UUID,Integer> RESPAWNING=new HashMap<>();
    private RestAdaptations(){}

    // ---------- day sleep ----------
    @SubscribeEvent public static void sleepAttempt(PlayerSleepInBedEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||e.getResultStatus()!=null||e.getPos()==null||p.isSleeping()||!p.isAlive())return;
        var level=p.serverLevel();
        MORNING.put(p,p.isShiftKeyDown());DAY.put(p,level.dimensionType().natural()&&level.isDay());
        // The same order vanilla checks in before it asks about the time: natural dimension, reach, free space, then daylight.
        if(!level.dimensionType().natural()||!usable(p,e.getPos())||!level.isDay())return;
        int stage=MetaHooks.stage(p,MetaMechanic.DAY_SLEEP);
        MetaServer.attempt(p,MetaMechanic.DAY_SLEEP);
        if(stage==1)LATER.put(p,level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                ?Component.translatable("meta.adaptationwheel.day_sleep.wait",clock(eveningDelay(level,false)))
                :Component.translatable("meta.adaptationwheel.day_sleep.frozen"));
    }
    /** Both the attempt and the per-tick check in Player.tick that would wake the sleeper ask this event. */
    @SubscribeEvent public static void sleepingTime(SleepingTimeCheckEvent e){
        // The attempt is asked before the player lies down; asleep, only a sleep begun by day is kept past dawn.
        if(e.getResult()==Event.Result.DEFAULT&&e.getEntity() instanceof ServerPlayer p&&p.level().isDay()&&(!p.isSleeping()||DAY.getOrDefault(p,false))
                &&MetaHooks.stage(p,MetaMechanic.DAY_SLEEP)>=2)e.setResult(Event.Result.ALLOW);
    }
    @SubscribeEvent public static void sleepFinished(SleepFinishedTimeEvent e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.isDay())return; // sleeping through a night is plain vanilla
        int best=0;boolean morning=false;
        for(var p:level.players()){
            if(!p.isSleeping()||p.isSpectator()||!DAY.getOrDefault(p,false))continue;
            int stage=MetaHooks.stage(p,MetaMechanic.DAY_SLEEP);
            if(stage<2)continue;
            if(stage>best){best=stage;morning=false;}
            if(stage==best&&MORNING.getOrDefault(p,false))morning=true;
        }
        if(best<2)return;
        long now=level.getDayTime();
        if(best==2)e.setTimeAddition(now);
        else if(best<4||!morning){
            // The finished sleep clears rain, so the evening is looked for under a clear sky in that case.
            long delay=eveningDelay(level,level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)&&level.isRaining());
            if(delay>0)e.setTimeAddition(now+delay);
        }
    }
    /** Vanilla's Level.updateSkyBrightness for any time of day: sleeping is allowed from darkness 4 up. */
    static int skyDarken(ServerLevel level,long time,float rain,float thunder){
        double wet=1-(double)(rain*5f)/16d,storm=1-(double)(thunder*5f)/16d;
        double light=.5+2*Mth.clamp((double)Mth.cos(level.dimensionType().timeOfDay(time)*((float)Math.PI*2f)),-.25,.25);
        return (int)((1-light*wet*storm)*11);
    }
    /** Ticks until vanilla would allow sleeping (the first tick whose sky darkness is at least 4). */
    static long eveningDelay(ServerLevel level,boolean clear){
        float rain=clear?0:level.getRainLevel(1f),thunder=clear?0:level.getThunderLevel(1f);long now=level.getDayTime();
        for(int i=0;i<=24000;i++)if(skyDarken(level,now+i,rain,thunder)>=4)return i;
        return -1;
    }
    private static String clock(long ticks){long s=Math.max(0,(ticks+19)/20);return s/60+":"+(s%60<10?"0":"")+s%60;}
    private static boolean usable(ServerPlayer p,BlockPos pos){
        var state=p.level().getBlockState(pos);if(!state.hasProperty(HorizontalDirectionalBlock.FACING))return false;
        Direction back=state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
        var level=p.level();
        return (near(p,pos)||near(p,pos.relative(back)))&&!level.getBlockState(pos.above()).isSuffocating(level,pos.above())
                &&!level.getBlockState(pos.above().relative(back)).isSuffocating(level,pos.above().relative(back));
    }
    private static boolean near(ServerPlayer p,BlockPos pos){
        Vec3 v=Vec3.atBottomCenterOf(pos);return Math.abs(p.getX()-v.x)<=3&&Math.abs(p.getY()-v.y)<=2&&Math.abs(p.getZ()-v.z)<=3;
    }

    // ---------- sleeping near monsters ----------
    /** Called from the head of Monster.isPreventingPlayerRest (coremod): false means this monster does not count. */
    public static boolean preventsRest(Monster monster,Player player){
        if(!(player instanceof ServerPlayer p)||!MetaServer.active(p,MetaMechanic.RESTLESS))return true;
        int stage=MetaHooks.stage(p,MetaMechanic.RESTLESS);
        if(stage>=4)return false;
        double distance=monster.distanceTo(p);
        if(stage>=3&&distance>3||stage>=2&&distance>5)return false;
        MetaServer.attempt(p,MetaMechanic.RESTLESS);
        if(stage>=1)monster.addEffect(new MobEffectInstance(MobEffects.GLOWING,100,0,false,false));
        return true;
    }

    // ---------- insomnia ----------
    @SubscribeEvent public static void phantoms(PlayerSpawnPhantomsEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        int rest=p.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        if(rest<72000)return; // below vanilla's threshold no phantom can spawn anyway
        MetaServer.expose(p,MetaMechanic.INSOMNIA);
        int stage=MetaHooks.stage(p,MetaMechanic.INSOMNIA);
        if(stage>=1){
            long night=p.serverLevel().getDayTime()/24000;Long last=NIGHT.get(p);
            if(last==null||last!=night){NIGHT.put(p,night);p.displayClientMessage(Component.translatable("meta.adaptationwheel.insomnia.nights",rest/24000),true);}
        }
        if(stage>=4||stage>=3&&rest<120000||stage>=2&&rest<96000)e.setResult(Event.Result.DENY);
    }

    // ---------- a bed in the Nether or the End ----------
    @SubscribeEvent public static void bedClick(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isSpectator()||e.getLevel().isClientSide||e.getUseBlock()==Event.Result.DENY)return;
        var level=p.serverLevel();var state=level.getBlockState(e.getPos());
        if(!(state.getBlock() instanceof BedBlock)||BedBlock.canSetSpawn(level)||!usesBlock(p,e))return;
        BlockPos head=e.getPos();var bed=state.getBlock();
        if(state.getValue(BedBlock.PART)!=BedPart.HEAD){head=head.relative(state.getValue(HorizontalDirectionalBlock.FACING));state=level.getBlockState(head);if(!state.is(bed))return;}
        int stage=MetaHooks.stage(p,MetaMechanic.FOREIGN_BED);
        MetaServer.attempt(p,MetaMechanic.FOREIGN_BED);
        if(stage==0)return;
        if(stage==1){
            Long last=WARNED.get(p);long now=level.getGameTime();
            if(last!=null&&now-last<=CONFIRM_TICKS){WARNED.remove(p);return;} // the second click: vanilla explodes the bed
            WARNED.put(p,now);p.displayClientMessage(Component.translatable("meta.adaptationwheel.foreign_bed.warn"),true);
        }else if(stage==2)defuse(level,head,state);
        else if(stage==3)p.displayClientMessage(Component.translatable("meta.adaptationwheel.foreign_bed.impossible"),true);
        else remember(p,level,head);
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
    }
    /** ServerPlayerGameMode.useItemOn: sneaking with something in hand uses the item instead of the block. */
    private static boolean usesBlock(ServerPlayer p,PlayerInteractEvent.RightClickBlock e){
        boolean held=!p.getMainHandItem().isEmpty()||!p.getOffhandItem().isEmpty();
        boolean item=p.isSecondaryUseActive()&&held&&(!p.getMainHandItem().doesSneakBypassUse(p.level(),e.getPos(),p)||!p.getOffhandItem().doesSneakBypassUse(p.level(),e.getPos(),p));
        return e.getUseBlock()==Event.Result.ALLOW||!item;
    }
    /** BedBlock.use's explosion without the block damage and fire: the bed goes, the people near it still take the damage. */
    private static void defuse(ServerLevel level,BlockPos head,BlockState state){
        level.removeBlock(head,false);
        BlockPos foot=head.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
        if(level.getBlockState(foot).is(state.getBlock()))level.removeBlock(foot,false);
        Vec3 center=head.getCenter();
        level.explode(null,level.damageSources().badRespawnPointExplosion(center),null,center,5f,false,Level.ExplosionInteraction.NONE);
    }
    private static void remember(ServerPlayer p,ServerLevel level,BlockPos head){
        var tag=new CompoundTag();tag.putString("Dim",level.dimension().location().toString());tag.putInt("X",head.getX());tag.putInt("Y",head.getY());tag.putInt("Z",head.getZ());
        MetaServer.meta(p).put(BED,tag);p.displayClientMessage(Component.translatable("meta.adaptationwheel.foreign_bed.anchor_set"),true);
    }
    @SubscribeEvent public static void cloned(PlayerEvent.Clone e){if(e.getEntity() instanceof ServerPlayer p)RESPAWNING.put(p.getUUID(),p.server.getTickCount());}
    private static boolean respawning(ServerPlayer p){Integer tick=RESPAWNING.get(p.getUUID());return tick!=null&&tick==p.server.getTickCount();}
    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent e){
        if(e.getEntity() instanceof ServerPlayer sp)RESPAWNING.remove(sp.getUUID());
        if(e.isEndConquered()||!(e.getEntity() instanceof ServerPlayer p)||p.getServer()==null)return;
        var meta=MetaServer.meta(p);
        if(!meta.contains(BED,10)||!MetaServer.full(p,MetaMechanic.FOREIGN_BED))return;
        var tag=meta.getCompound(BED);var pos=new BlockPos(tag.getInt("X"),tag.getInt("Y"),tag.getInt("Z"));
        ResourceLocation id=ResourceLocation.tryParse(tag.getString("Dim"));
        ServerLevel level=id==null?null:p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
        if(level==null||!(level.getBlockState(pos).getBlock() instanceof BedBlock)){
            meta.remove(BED);p.displayClientMessage(Component.translatable("meta.adaptationwheel.foreign_bed.anchor_lost"),true);return;
        }
        Vec3 spot=standUp(p,level,pos);
        if(spot==null){p.displayClientMessage(Component.translatable("meta.adaptationwheel.foreign_bed.anchor_blocked"),true);return;}
        // The connection still holds the old player during this event (it takes the new one when respawn returns), so a
        // same-level connection.teleport alone would move the old one; another level goes through ServerPlayer.moveTo itself.
        if(level==p.level())p.moveTo(spot.x,spot.y,spot.z,p.getYRot(),0f);
        p.teleportTo(level,spot.x,spot.y,spot.z,Set.of(),p.getYRot(),0f);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void spawnSet(PlayerSetSpawnEvent e){
        if(e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p)||e.getNewSpawn()==null||respawning(p))return;
        var meta=MetaServer.meta(p);if(!meta.contains(BED,10))return;
        var tag=meta.getCompound(BED);
        boolean same=e.getSpawnLevel().location().toString().equals(tag.getString("Dim"))&&e.getNewSpawn().equals(new BlockPos(tag.getInt("X"),tag.getInt("Y"),tag.getInt("Z")));
        if(!same)meta.remove(BED); // an ordinary spawn point set later replaces the wheel's
    }
    private static Vec3 standUp(ServerPlayer p,ServerLevel level,BlockPos bed){
        var state=level.getBlockState(bed);Direction facing=state.hasProperty(HorizontalDirectionalBlock.FACING)?state.getValue(HorizontalDirectionalBlock.FACING):Direction.NORTH;
        var found=BedBlock.findStandUpPosition(EntityType.PLAYER,level,bed,facing,p.getYRot());
        if(found.isPresent())return found.get();
        for(int r=1;r<=2;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)for(int dy=0;dy<=1;dy++){
            var q=bed.offset(dx,dy,dz);var floor=q.below();
            if(level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP)&&level.getFluidState(q).isEmpty()
                    &&level.noCollision(EntityType.PLAYER.getDimensions().makeBoundingBox(q.getX()+.5,q.getY(),q.getZ()+.5)))return Vec3.atBottomCenterOf(q);
        }
        return null;
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||LATER.isEmpty()||!(e.player instanceof ServerPlayer p))return;
        var message=LATER.remove(p);if(message!=null)p.displayClientMessage(message,true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){
        if(e.getEntity() instanceof ServerPlayer p){LATER.remove(p);MORNING.remove(p);DAY.remove(p);WARNED.remove(p);NIGHT.remove(p);RESPAWNING.remove(p.getUUID());}
    }
}
