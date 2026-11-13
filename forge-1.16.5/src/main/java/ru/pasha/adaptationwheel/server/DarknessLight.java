package ru.pasha.adaptationwheel.server;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.LightType;
import net.minecraft.block.*;
import net.minecraft.block.BlockState;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import java.util.*;

/** Short-lived, invisible vanilla-light-compatible blocks. Only air and our own light may be touched. */
public final class DarknessLight {
    private static final class Placement {private final ServerWorld level;private final BlockPos pos;private final int brightness;private final long renewed; Placement(ServerWorld level,BlockPos pos,int brightness,long renewed){this.level=level;this.pos=pos;this.brightness=brightness;this.renewed=renewed;}ServerWorld level(){return level;}BlockPos pos(){return pos;}int brightness(){return brightness;}long renewed(){return renewed;}}
    private static final Map<UUID,Placement> LIGHTS=new HashMap<>();
    private static final class Sample {private final BlockPos pos;private final long time;private final boolean dark; Sample(BlockPos pos,long time,boolean dark){this.pos=pos;this.time=time;this.dark=dark;}BlockPos pos(){return pos;}long time(){return time;}boolean dark(){return dark;}}
    private static final Map<ServerPlayerEntity,Sample> SAMPLES=new WeakHashMap<>();
    private DarknessLight() {}
    public static int brightness(int stage) { return stage>=3 ? 15 : stage==2 ? 10 : stage==1 ? 5 : 0; }
    public static boolean isDark(ServerPlayerEntity p) {
        ServerWorld level=p.getLevel(); BlockPos head=p.blockPosition().above();
        Sample sample=SAMPLES.get(p);
        if(sample!=null && sample.pos().equals(head) && level.getGameTime()-sample.time()<20) return sample.dark();
        // Cave exposure ignores adaptation lights, so the player's own glow cannot pause its timer.
        boolean dark=!level.canSeeSky(head) && level.getBrightness(LightType.SKY,head)<=4;
        if(dark && naturalLightReaches(level,head)) dark=false;
        SAMPLES.put(p,new Sample(head.immutable(),level.getGameTime(),dark));
        return dark;
    }
    private static final class LightPath {private final BlockPos pos;private final int loss; LightPath(BlockPos pos,int loss){this.pos=pos;this.loss=loss;}BlockPos pos(){return pos;}int loss(){return loss;}}
    private static boolean naturalLightReaches(ServerWorld level,BlockPos head) {
        PriorityQueue<LightPath> paths=new PriorityQueue<>(Comparator.comparingInt(LightPath::loss));
        Map<BlockPos,Integer> best=new HashMap<>();
        paths.add(new LightPath(head.immutable(),0));best.put(head.immutable(),0);
        while(!paths.isEmpty()) {
            LightPath path=paths.remove();
            if(path.loss()!=best.get(path.pos()) || !level.hasChunkAt(path.pos())) continue;
            BlockState block=level.getBlockState(path.pos());
            if(!block.is(AdaptationWheel.GLOW.get()) && block.getLightValue(level,path.pos())-path.loss()>4) return true;
            int nextLoss=path.loss()+Math.max(1,block.getLightBlock(level,path.pos()));
            if(nextLoss>10) continue;
            for(net.minecraft.util.Direction direction:net.minecraft.util.Direction.values()) {
                BlockPos next=path.pos().relative(direction);
                if(best.getOrDefault(next,11)>nextLoss) {
                    best.put(next,nextLoss);paths.add(new LightPath(next,nextLoss));
                }
            }
        }
        return false;
    }
    public static void invalidate(ServerPlayerEntity p) { SAMPLES.remove(p); }
    public static void update(ServerPlayerEntity p) {
        int intensity=AdaptationEvents.active(p) && p.isAlive() && AdaptationEvents.state(p).categoryEnabled(AdaptationProfile.DARK_KEY)
            ? brightness(AdaptationEvents.state(p).stage(AdaptationProfile.DARK_KEY)) : 0;
        Placement old=LIGHTS.get(p.getUUID());
        BlockPos target=p.blockPosition().above();
        ServerWorld level=p.getLevel();
        BlockState block=level.getBlockState(target);
        if(!block.isAir() && !block.is(AdaptationWheel.GLOW.get())) {
            target=p.blockPosition(); block=level.getBlockState(target);
        }
        if(intensity==0 || !block.isAir() && !block.is(AdaptationWheel.GLOW.get())) { clear(p); return; }
        if(old!=null && (old.level()!=level || !old.pos().equals(target))) {
            LIGHTS.remove(p.getUUID()); rebuild(old.level(),old.pos());
        }
        LIGHTS.put(p.getUUID(),new Placement(level,target.immutable(),intensity,level.getGameTime()));
        rebuild(level,target);
    }
    public static boolean fresh(ServerWorld level,BlockPos pos) {
        return LIGHTS.values().stream().anyMatch(p -> p.level()==level && p.pos().equals(pos) && level.getGameTime()-p.renewed()<25);
    }
    private static void rebuild(ServerWorld level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return;
        int value=LIGHTS.values().stream().filter(p -> p.level()==level && p.pos().equals(pos) && level.getGameTime()-p.renewed()<25)
            .mapToInt(Placement::brightness).max().orElse(0);
        BlockState current=level.getBlockState(pos);
        if(value==0) {
            if(current.is(AdaptationWheel.GLOW.get())) level.setBlock(pos,
                current.getFluidState().isEmpty()?Blocks.AIR.defaultBlockState():Blocks.WATER.defaultBlockState(),3);
        } else if(current.isAir() || current.is(AdaptationWheel.GLOW.get())) {
            BlockState desired=AdaptationWheel.GLOW.get().defaultBlockState().setValue(ru.pasha.adaptationwheel.block.AdaptationGlowBlock.LEVEL,value);
if(!current.equals(desired)) level.setBlock(pos,desired,3);
        }
    }
    public static void clear(ServerPlayerEntity p) {
        Placement old=LIGHTS.remove(p.getUUID());
        if(old!=null) rebuild(old.level(),old.pos());
        SAMPLES.remove(p);
    }
    public static void clearAll() {
        List<Placement> old=new ArrayList<>(LIGHTS.values()); LIGHTS.clear(); SAMPLES.clear();
        old.forEach(p -> rebuild(p.level(),p.pos()));
    }
}
