package ru.pasha.adaptationwheel.server;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.MetaHooks;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/**
 * World and movement adaptations: slowing blocks, portal wait, mining in the air, trampled farmland, waiting at
 * furnaces and water in the Nether. Movement and mining speed run on both sides and read {@link MetaHooks};
 * everything that changes the world is decided by the server. The static hooks are called from
 * {@code coremods/movement_adaptations.js}.
 */
public final class MovementAdaptations {
    private static final int[] PORTAL={80,60,40,20,1};
    /** Lifetime of water placed in an ultra-warm dimension at stages I-III (stage IV keeps it). */
    private static final int[] WATER_TICKS={0,60,200,600,-1};
    private static final int SMELT_RADIUS=8,SMELT_LIMIT=16,WATER_LIMIT=512;
    /** Pending timed water of the running server (saved in the overworld's data storage), re-read when the server changes. */
    private static TimedWater DATA;
    /** The same positions per dimension, for the O(1) lookup in the fluid-source event, which fires for every fluid tick. */
    private static final Map<ResourceKey<Level>,Set<BlockPos>> TIMED=new HashMap<>();
    private static final Map<ServerPlayer,List<BlockEntity>> NEARBY=new WeakHashMap<>();
    /** {server tick, extra ticks already given that tick}: a block near several wearers is sped up once, by the best of them. */
    private static final Map<BlockEntity,long[]> EXTRA=new WeakHashMap<>();
    private MovementAdaptations(){}

    // ---------- coremod hooks (both sides) ----------
    private static double lerp(double factor,int stage){return factor+(1-factor)*stage*.25;}
    /** {@code Entity.makeStuckInBlock}: cobweb, powder snow, sweet berry bush. Each slowing multiplier (below 1) moves toward 1. */
    public static Vec3 stuck(Entity e,Vec3 v){
        if(v==null||v.x>=1&&v.y>=1&&v.z>=1)return v;
        if(e instanceof ServerPlayer p)MetaServer.expose(p,MetaMechanic.STICKY);
        int s=MetaHooks.stage(e,MetaMechanic.STICKY);
        return s==0?v:new Vec3(v.x<1?lerp(v.x,s):v.x,v.y<1?lerp(v.y,s):v.y,v.z<1?lerp(v.z,s):v.z); // powder snow's y=1.5 stays
    }
    /** Return value of {@code Entity.getBlockSpeedFactor}: soul sand (without Soul Speed), honey. */
    public static float speed(float f,Entity e){return slowdown(f,e);}
    /** Return value of {@code Entity.getBlockJumpFactor}: honey. */
    public static float jump(float f,Entity e){return slowdown(f,e);}
    private static float slowdown(float f,Entity e){
        if(f>=1)return f;
        if(e instanceof ServerPlayer p)MetaServer.expose(p,MetaMechanic.STICKY);
        int s=MetaHooks.stage(e,MetaMechanic.STICKY);
        return s==0?f:(float)lerp(f,s);
    }
    /** Return value of {@code Player.getPortalWaitTime}: 80 ticks in survival, 1 in creative (left alone). */
    public static int portalWait(int ticks,Player p){
        int s=MetaHooks.stage(p,MetaMechanic.PORTAL);
        return s==0||ticks<=1?ticks:Math.min(ticks,PORTAL[s]);
    }
    /**
     * Result of the "water evaporates here" test in {@code BucketItem.emptyContents} (Forge's
     * {@code FluidType.isVaporizedOnPlacement} for a bucket with contents, the dimension flag for a bare one).
     * Returns the final verdict; the client asks the same question and predicts the same answer.
     */
    public static boolean fizzles(boolean vaporizes,Player p,Level level,BlockPos pos,BucketItem bucket){
        if(!vaporizes||p==null||bucket==null||!bucket.getFluid().is(FluidTags.WATER))return vaporizes;
        int s=MetaHooks.stage(p,MetaMechanic.NETHER_WATER);
        if(!(p instanceof ServerPlayer sp)||!(level instanceof ServerLevel sl))return s==0;
        if(s==0){MetaServer.attempt(sp,MetaMechanic.NETHER_WATER);return true;}
        // Flowing water gets a timer too (a bucket emptied into it makes a new source); only a water source already has one or is permanent.
        var fs=level.getFluidState(pos);
        if(s<4&&!(fs.isSource()&&fs.is(FluidTags.WATER))&&!schedule(sl,pos,sp,s))return true;
        return false;
    }
    /** False when the timer was refused (list full and the oldest entry cannot be expired now): the water must fizzle. */
    private static boolean schedule(ServerLevel level,BlockPos pos,ServerPlayer owner,int stage){
        var server=level.getServer();
        return track(server,level.dimension(),pos,owner.getUUID(),clock(server)+WATER_TICKS[stage]);
    }
    /** Overworld game time: it keeps counting across restarts, unlike {@code getTickCount()}. */
    public static long clock(MinecraftServer server){return server.overworld().getGameTime();}
    private static TimedWater data(MinecraftServer server){
        var d=server.overworld().getDataStorage().computeIfAbsent(TimedWater::load,TimedWater::new,TimedWater.NAME);
        if(d!=DATA){DATA=d;TIMED.clear();for(var t:d.list)TIMED.computeIfAbsent(t.dim(),k->new HashSet<>()).add(t.pos());}
        return d;
    }
    /** Starts a timer for the water at {@code pos}; false when it was refused. A position that already has one keeps it. */
    public static boolean track(MinecraftServer server,ResourceKey<Level> dim,BlockPos pos,UUID owner,long due){
        var d=data(server);
        if(pending(dim,pos))return true;
        if(d.list.size()>=WATER_LIMIT){
            var old=d.list.get(0);var oldLevel=server.getLevel(old.dim());
            // Never let the oldest source silently become permanent: expire it now, or refuse the new timer.
            if(oldLevel!=null&&!oldLevel.hasChunkAt(old.pos()))return false;
            d.list.remove(0);untrack(d,old);
            if(oldLevel!=null)expire(server,oldLevel,old.pos(),null);
        }
        var key=pos.immutable();
        d.list.add(new TimedWater.Timed(dim,key,due,owner));TIMED.computeIfAbsent(dim,k->new HashSet<>()).add(key);d.setDirty();
        return true;
    }
    /** Forgets a timer without touching the water (used by tests to clean up); false when there was none. */
    public static boolean cancel(MinecraftServer server,ResourceKey<Level> dim,BlockPos pos){
        var d=data(server);
        for(var it=d.list.iterator();it.hasNext();){
            var t=it.next();
            if(t.dim()==dim&&t.pos().equals(pos)){it.remove();untrack(d,t);return true;}
        }
        return false;
    }
    private static void untrack(TimedWater d,TimedWater.Timed t){
        var set=TIMED.get(t.dim());
        if(set!=null){set.remove(t.pos());if(set.isEmpty())TIMED.remove(t.dim());}
        d.setDirty();
    }
    /** Whether the water at {@code pos} is a timed source waiting to expire. */
    public static boolean pending(ResourceKey<Level> dim,BlockPos pos){
        var set=TIMED.get(dim);return set!=null&&set.contains(pos);
    }
    /** Timed water must not feed source conversion (two sources beside a flowing block make a new source that nothing removes). */
    @SubscribeEvent public static void source(BlockEvent.CreateFluidSourceEvent e){
        if(TIMED.isEmpty()||!e.getLevel().dimensionType().ultraWarm())return;
        if(pending(e.getLevel().dimension(),e.getPos()))e.setResult(Event.Result.DENY);
    }
    public static int waterTicks(int stage){return WATER_TICKS[Math.max(0,Math.min(4,stage))];}

    // ---------- loose footing ----------
    @SubscribeEvent public static void breakSpeed(PlayerEvent.BreakSpeed e){
        var p=e.getEntity();
        if(p.isCreative()||p.isSpectator())return;
        boolean wet=p.isEyeInFluid(FluidTags.WATER)&&!EnchantmentHelper.hasAquaAffinity(p),air=!p.onGround();
        if(!wet&&!air)return;
        if(p instanceof ServerPlayer sp&&e.getOriginalSpeed()>0
                &&e.getPosition().filter(pos->!directlyBelow(p,pos)).isPresent()){
            MetaServer.expose(sp,MetaMechanic.AIR_MINING);
        }
        int s=MetaHooks.stage(p,MetaMechanic.AIR_MINING);
        if(s==0)return;
        // Vanilla divides by 5 for each penalty; stage s makes that divisor 5/(1+s), so 1.0 at stage IV.
        float k=1+s;if(wet&&air)k*=k;
        e.setNewSpeed(e.getNewSpeed()*k);
    }

    private static boolean directlyBelow(Player player, BlockPos pos){
        var box=player.getBoundingBox();
        return pos.getY()+1<=box.minY+0.1
                &&pos.getX()<box.maxX&&pos.getX()+1>box.minX
                &&pos.getZ()<box.maxZ&&pos.getZ()+1>box.minZ;
    }

    // ---------- trampled farmland ----------
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void trample(BlockEvent.FarmlandTrampleEvent e){
        var who=e.getEntity() instanceof ServerPlayer p?p:e.getEntity().getControllingPassenger() instanceof ServerPlayer rider?rider:null;
        if(who==null)return;
        // A prevented trample is no attempt: only a landing that really tramples counts.
        int s=MetaHooks.stage(who,MetaMechanic.FARMLAND);
        if(s>0&&who.getRandom().nextInt(100)<25*s){e.setCanceled(true);return;}
        MetaServer.attempt(who,MetaMechanic.FARMLAND);
    }

    // ---------- per-player work: portal, furnaces ----------
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!p.isAlive())return;
        boolean second=p.tickCount%20==0;
        if(second&&MetaServer.active(p,MetaMechanic.PORTAL)){
            var feet=p.blockPosition();
            if(p.level().getBlockState(feet).is(Blocks.NETHER_PORTAL)||p.level().getBlockState(feet.above()).is(Blocks.NETHER_PORTAL))MetaServer.expose(p,MetaMechanic.PORTAL);
        }
        List<BlockEntity> near;
        if(second){
            if(MetaServer.active(p,MetaMechanic.SMELTING)&&working(p))MetaServer.expose(p,MetaMechanic.SMELTING);
            if(MetaHooks.stage(p,MetaMechanic.SMELTING)==0){NEARBY.remove(p);return;}
            near=find(p);NEARBY.put(p,near);
        }else near=NEARBY.get(p);
        if(near==null||near.isEmpty())return;
        int stage=MetaHooks.stage(p,MetaMechanic.SMELTING);
        if(stage==0)return;
        long now=p.serverLevel().getServer().getTickCount();int extra=extraTicks(stage,now);
        if(extra>0)for(var be:near)speedUp(be,extra,now);
    }
    /** Extra ticks per server tick: I +1 every 4th (x1.25), II every 2nd (x1.5), III every tick (x2), IV two per tick (x3). */
    public static int extraTicks(int stage,long tick){
        return switch(Math.min(stage,4)){case 1->tick%4==0?1:0;case 2->tick%2==0?1:0;case 3->1;case 4->2;default->0;};
    }
    /** An open vanilla furnace/smoker/blast furnace/brewing stand menu of something that is working. */
    private static boolean working(ServerPlayer p){
        var m=p.containerMenu;
        if(m==null)return false;
        var type=m.getClass();
        if(type==FurnaceMenu.class||type==SmokerMenu.class||type==BlastFurnaceMenu.class)return ((AbstractFurnaceMenu)m).isLit();
        return type==BrewingStandMenu.class&&((BrewingStandMenu)m).getBrewingTicks()>0;
    }
    private static boolean vanilla(BlockEntity be){
        var type=be.getClass();
        return type==FurnaceBlockEntity.class||type==SmokerBlockEntity.class||type==BlastFurnaceBlockEntity.class||type==BrewingStandBlockEntity.class;
    }
    /** Vanilla furnace-like block entities within 8 blocks, nearest first, from chunks that are already loaded. */
    private static List<BlockEntity> find(ServerPlayer p){
        var level=p.serverLevel();var center=p.position();var found=new ArrayList<BlockEntity>();
        int minX=(int)Math.floor(center.x-SMELT_RADIUS)>>4,maxX=(int)Math.floor(center.x+SMELT_RADIUS)>>4,minZ=(int)Math.floor(center.z-SMELT_RADIUS)>>4,maxZ=(int)Math.floor(center.z+SMELT_RADIUS)>>4;
        for(int cx=minX;cx<=maxX;cx++)for(int cz=minZ;cz<=maxZ;cz++){
            LevelChunk chunk=level.getChunkSource().getChunkNow(cx,cz);
            if(chunk==null)continue;
            for(var be:chunk.getBlockEntities().values())
                if(!be.isRemoved()&&vanilla(be)&&be.getBlockPos().distToCenterSqr(center.x,center.y,center.z)<=SMELT_RADIUS*SMELT_RADIUS)found.add(be);
        }
        found.sort(Comparator.comparingDouble(be->be.getBlockPos().distToCenterSqr(center.x,center.y,center.z)));
        return found.size()>SMELT_LIMIT?new ArrayList<>(found.subList(0,SMELT_LIMIT)):found;
    }
    private static void speedUp(BlockEntity be,int wanted,long now){
        var done=EXTRA.computeIfAbsent(be,x->new long[2]);int todo;
        if(done[0]==now){todo=Math.max(0,wanted-(int)done[1]);done[1]=Math.max(done[1],wanted);}
        else{done[0]=now;done[1]=wanted;todo=wanted;}
        var level=be.getLevel();
        if(todo<=0||be.isRemoved()||!(level instanceof ServerLevel))return;
        try{
            for(int i=0;i<todo;i++){
                var pos=be.getBlockPos();
                // The state is read again each time: a furnace that runs out of fuel flips LIT in the middle of a tick.
                if(be.isRemoved()||level.getBlockEntity(pos)!=be)return;
                var state=level.getBlockState(pos);
                if(be instanceof AbstractFurnaceBlockEntity f)AbstractFurnaceBlockEntity.serverTick(level,pos,state,f);
                else if(be instanceof BrewingStandBlockEntity b)BrewingStandBlockEntity.serverTick(level,pos,state,b);
            }
        }catch(RuntimeException ex){LogUtils.getLogger().warn("Extra smelting tick failed at {}",be.getBlockPos(),ex);}
    }

    // ---------- nether water ----------
    @SubscribeEvent public static void started(ServerStartedEvent e){data(e.getServer());}
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var server=e.getServer();var d=data(server);
        if(d.list.isEmpty())return;
        long now=clock(server);
        for(var it=d.list.iterator();it.hasNext();){
            var t=it.next();
            if(now<t.due())continue;
            var level=server.getLevel(t.dim());
            if(level==null){it.remove();untrack(d,t);continue;}
            // A chunk that is not loaded is waited for however long that takes; it must not be loaded just to remove water.
            if(!level.hasChunkAt(t.pos()))continue;
            it.remove();untrack(d,t);expire(server,level,t.pos(),t.owner());
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){
        // Saving a world with a timed source in a loaded chunk would make it permanent; the rest stays in the saved list.
        var server=e.getServer();var d=data(server);
        for(var it=d.list.iterator();it.hasNext();){
            var t=it.next();var level=server.getLevel(t.dim());
            if(level!=null&&level.hasChunkAt(t.pos())){it.remove();untrack(d,t);expire(server,level,t.pos(),null);}
        }
        DATA=null;TIMED.clear();NEARBY.clear();EXTRA.clear();
    }
    private static void expire(MinecraftServer server,ServerLevel level,BlockPos pos,UUID owner){
        var state=level.getBlockState(pos);var fluid=state.getFluidState();
        if(fluid.isSource()&&fluid.is(FluidTags.WATER)&&state.getBlock() instanceof BucketPickup pickup){
            pickup.pickupBlock(level,pos,state);
            level.playSound(null,pos,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.5f,2.6f+(level.random.nextFloat()-level.random.nextFloat())*.8f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,8,.3,.3,.3,0);
        }
        // The vanished water is the refusal that starts the next stage.
        var player=owner==null?null:server.getPlayerList().getPlayer(owner);
        if(player!=null)MetaServer.attempt(player,MetaMechanic.NETHER_WATER);
    }
}
