package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Retained heap once the world is loaded: settle, then several full collections, then the used heap. Comparing
 * groups that differ by one memory mod shows what that mod saves on this mod set. Then a digest of what every block
 * state answers (values, neighbours, shapes, sturdy faces, model quads): equal digests in runs with and without a
 * memory optimisation mean it changed nothing observable.
 */
final class MemQa {
 static int ticks;static long best=Long.MAX_VALUE;
 static void run(ServerPlayer p){LogUtils.getLogger().info("MULTIMOD_QA MEM STAGE: world ready, uptime {} ms",ManagementFactory.getRuntimeMXBean().getUptime());}
 static boolean clientTick(){
  ticks++;
  if(ticks<400)return false; // 20 s for chunk loading, model and texture work to settle
  if(ticks%40==0){
   for(int i=0;i<3;i++)System.gc();
   var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();best=Math.min(best,heap.getUsed());
   LogUtils.getLogger().info("MULTIMOD_QA MEM SAMPLE used={} MB committed={} MB",heap.getUsed()>>20,heap.getCommitted()>>20);
  }
  if(ticks<640)return false;
  LogUtils.getLogger().info("MULTIMOD_QA MEM RESULT retained={} MB blockstates={} uptime={} ms boostStateMemory={} compacted={} shapes={} sharedModels={} sharedQuadVertices={}",best>>20,
   Block.BLOCK_STATE_REGISTRY.size(),ManagementFactory.getRuntimeMXBean().getUptime(),
   boost("ru.pasha.pashaboost.memory.MemoryGate","applied"),boost("ru.pasha.pashaboost.memory.StateMemory","STATES_COMPACTED"),
   boost("ru.pasha.pashaboost.memory.ShapeDedup","CACHES_DEDUPLICATED"),boost("ru.pasha.pashaboost.memory.ModelDedup","MODELS_SHARED"),
   boost("ru.pasha.pashaboost.memory.ModelSides","QUAD_VERTICES_SHARED"));
  LogUtils.getLogger().info("MULTIMOD_QA MEM MODELS simpleModelsCompacted={} quadsSeen={} quadsSubclassed={} quadSharingOff='{}'",
   boost("ru.pasha.pashaboost.memory.ModelSides","MODELS_COMPACTED"),boost("ru.pasha.pashaboost.memory.ModelSides","QUADS_SEEN"),
   boost("ru.pasha.pashaboost.memory.ModelSides","QUADS_SUBCLASSED"),boost("ru.pasha.pashaboost.memory.ModelSides","quadDedupOffReason"));
  digest();
  return true;
 }
 @SuppressWarnings({"unchecked","rawtypes"})
 static void digest(){
  long start=System.nanoTime();long values=1,neighbours=1,shapes=1,sturdy=1,models=1;int failures=0,simpleModels=0,readOnlyLists=0;
  var models3=Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
  for(BlockState state:Block.BLOCK_STATE_REGISTRY){
   try{
    values=mix(values,state.getValues().toString().hashCode());
    for(Property p:state.getProperties())for(Object v:p.getPossibleValues())neighbours=mix(neighbours,Block.getId(state.setValue(p,(Comparable)v)));
    for(AABB box:state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).toAabbs())shapes=mix(shapes,box.hashCode());
    for(Direction d:Direction.values()){
     for(AABB box:state.getFaceOcclusionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,d).toAabbs())shapes=mix(shapes,box.hashCode()^d.ordinal());
     sturdy=mix(sturdy,state.isFaceSturdy(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,d,SupportType.FULL)?1+d.ordinal():0);
    }
    var model=models3.getBlockModel(state);
    if(model instanceof SimpleBakedModel)simpleModels++;
    for(int side=0;side<=6;side++){
     Direction d=side==6?null:Direction.values()[side];
     var quads=model.getQuads(state,d,RandomSource.create(42),ModelData.EMPTY,null);
     // OptiFine edits these lists in place (SmartLeaves), so with it Boost must leave them mutable.
     if(model instanceof SimpleBakedModel&&quads.getClass().getName().startsWith("java.util.ImmutableCollections"))readOnlyLists++;
     for(BakedQuad q:quads){
      // Positions, face, tint and sprite name; UVs depend on atlas layout, not on the model.
      int[] v=q.getVertices();int stride=v.length/4;long pos=1;
      for(int i=0;i<4;i++)pos=mix(pos,Arrays.hashCode(Arrays.copyOfRange(v,i*stride,i*stride+3)));
      models=mix(mix(mix(models,pos),q.getDirection().ordinal()*31+q.getTintIndex()),q.getSprite().contents().name().hashCode());
     }
    }
   }catch(RuntimeException e){failures++;values=mix(values,e.getClass().getName().hashCode());}
  }
  LogUtils.getLogger().info("MULTIMOD_QA MEM DIGEST values={} neighbours={} shapes={} sturdy={} models={} failures={} took={} ms",
   Long.toHexString(values),Long.toHexString(neighbours),Long.toHexString(shapes),Long.toHexString(sturdy),Long.toHexString(models),failures,(System.nanoTime()-start)/1_000_000);
  LogUtils.getLogger().info("MULTIMOD_QA MEM LISTS simpleBlockModels={} readOnlyQuadLists={}",simpleModels,readOnlyLists);
 }
 static long mix(long h,long v){return (h^v)*0x9E3779B97F4A7C15L+(h>>>29);}
 /** A Boost static field, or "absent" with an older Boost. */
 static Object boost(String owner,String field){
  try{var f=Class.forName(owner).getField(field);return f.get(null);}catch(ReflectiveOperationException|LinkageError e){return "absent";}
 }
}
