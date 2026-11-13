package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import static ru.pasha.multimodqa.MultiModQa.*;

/**
 * Reproducible frame-time bench: ~400 mobs, ~400 block entities and smoking campfires in front of
 * the player. Phases alternate Pasha Boost OFF/ON after a warm-up so JIT and chunk loading do not
 * favour either side. Frame times come from real render ticks.
 */
final class PerfQa {
 // A group may set "perfOrder", e.g. "off,on,on16,on16,on": "on16" is Boost on with the 1.7 techniques switched off, so
 // alternating it with "on" in one run measures what 1.7 adds while machine load drifts equally on both sides. "on17" switches
 // off only the 1.8 techniques; "on16" switches off 1.7's and 1.8's.
 static String[] ORDER={"off","on","off","on"};
 static final String[] FEATURES_17={"PUPFISH_SEARCH","CLOUD_RAIN_SHORTCUT","BRANDONS_BOUNDS_FILTER","BRANDONS_SECTION_SKIP","MAHOU_ENTITY_SCAN","SIGN_TEXT_CULLING",
  "GLYPH_LOOKUP_CACHE","ENTITY_VERDICT_CACHE","SAMPLER_NAME_CACHE","VERTEX_ELEMENT_ARRAY","ENTITY_DATA_LOCK_ELISION"};
 static final String[] FEATURES_18={"MODEL_DIRECT","MODEL_DIRECT_SPRITE","PARTICLE_LIGHT_SHARE","QUAD_SORT","ENTITY_SECTION_MEMO","MODEL_QUATERNION_REUSE"};
 static final long WARMUP=20_000_000_000L,PHASE=12_000_000_000L,SETTLE=6_000_000_000L;
 static int phase=-2;static long phaseStart,lastFrame;static final List<Long> FRAMES=new ArrayList<>();
 // Render-thread CPU time and allocation per frame: other processes on the machine stretch frame times, but barely these.
 static final com.sun.management.ThreadMXBean THREADS=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
 static long cpuStart=-1,allocStart;
 static final Map<String,List<double[]>> RESULTS=new LinkedHashMap<>();
 static final List<Double> MSPT=new ArrayList<>();
 static void run(ServerPlayer p)throws Exception{
  reset(p);ServerLevel level=p.serverLevel();
  level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);
  level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,p.server);level.setWeatherParameters(100000,0,false,false);
  BlockPos base=p.blockPosition();
  if(!(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource)){
   // Eternal Tales forces a normal world, where the flat-world spot is underground and the player fell
   // out of the world: build the same grass floor high above the terrain with clear air over it.
   base=new BlockPos(base.getX(),170,base.getZ());
   for(int x=-46;x<=46;x++)for(int z=-8;z<=124;z++){
    level.setBlock(base.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=0;y<10;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
   }
   p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);
   LogUtils.getLogger().info("MULTIMOD_QA PERF STAGE: platform at y={} (not a flat world)",base.getY());
  }
  int floor=base.getY();
  if(group.has("scene")&&group.get("scene").getAsString().equals("empty")){LogUtils.getLogger().info("MULTIMOD_QA PERF SCENE: empty flat world");return;}
  EntityType<?>[] types={EntityType.COW,EntityType.SHEEP,EntityType.PIG,EntityType.VILLAGER,EntityType.ZOMBIE,EntityType.CHICKEN,EntityType.IRON_GOLEM,EntityType.SKELETON_HORSE};
  int mobs=0;
  for(int x=-40;x<=40;x+=4)for(int z=10;z<=118;z+=6){
   Entity e=types[(mobs)%types.length].create(level);if(e==null)continue;
   e.moveTo(base.getX()+x+.5,floor,base.getZ()+z+.5,(x*37+z*11)%360,0);
   if(e instanceof Mob mob){mob.setNoAi(true);mob.setPersistenceRequired();mob.setSilent(true);}
   if(e instanceof Zombie zombie)zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));
   level.addFreshEntity(e);mobs++;
  }
  int blockEntities=0;
  for(int x=-30;x<=30;x+=3)for(int z=8;z<=62;z+=3){
   BlockPos pos=base.offset(x,0,z);
   switch(blockEntities%4){
    case 0->level.setBlock(pos,Blocks.CHEST.defaultBlockState(),2);
    case 1->{level.setBlock(pos,Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,8),2);
      if(level.getBlockEntity(pos) instanceof SignBlockEntity sign){var text=sign.getFrontText();for(int l=0;l<4;l++)text=text.setMessage(l,Component.literal("Pasha Boost "+l));sign.setText(text,true);}}
    case 2->level.setBlock(pos,Blocks.WHITE_BANNER.defaultBlockState(),2);
    default->level.setBlock(pos,Blocks.ENDER_CHEST.defaultBlockState(),2);
   }
   blockEntities++;
  }
  for(int i=0;i<30;i++){BlockPos pos=base.offset(-28+(i%10)*6,0,70+(i/10)*8);level.setBlock(pos.below(),Blocks.HAY_BLOCK.defaultBlockState(),2);level.setBlock(pos,Blocks.CAMPFIRE.defaultBlockState(),2);}
  p.teleportTo(base.getX()+.5,floor,base.getZ()+.5);p.connection.teleport(base.getX()+.5,floor,base.getZ()+.5,0,12);
  LogUtils.getLogger().info("MULTIMOD_QA PERF SCENE: {} mobs, {} block entities, 30 signal campfires",mobs,blockEntities);
 }
 /** Unlocks or locks the wheel's FPS adaptation, which is what gates Pasha Boost. */
 static void boost(boolean on)throws Exception{
  Class<?> cfg=Class.forName("ru.pasha.adaptationwheel.FpsAdaptationConfig");
  // The bench changes the unlock directly; mark it as the wheel's own write so it is not reported as a config edit.
  Runnable writes=()->{try{set(cfg.getField("STAGE").get(null),on?4:0);set(cfg.getField("WORK").get(null),0.0);set(cfg.getField("STARTED").get(null),on);}catch(Exception e){throw new RuntimeException(e);}};
  Class.forName("ru.pasha.adaptationwheel.ConfigWatch").getMethod("ownSave",Object.class,Runnable.class).invoke(null,cfg.getField("SPEC").get(null),writes);
  var loaded=Class.forName("ru.pasha.adaptationwheel.client.FpsAdaptation").getDeclaredField("loaded");loaded.setAccessible(true);loaded.setBoolean(null,false);
 }
 /** Every 1.7 and 1.8 technique reads its RuntimeTuning flag on each call, so this takes effect on the next frame and tick. */
 static void features(String[] names,boolean on){
  for(String name:names)try{Class.forName("ru.pasha.pashaboost.RuntimeTuning").getField(name).setBoolean(null,on);}catch(ReflectiveOperationException missing){}
 }
 static void set(Object value,Object v)throws Exception{value.getClass().getMethod("set",Object.class).invoke(value,v);}
 static boolean boostActive()throws Exception{return (boolean)Class.forName("ru.pasha.pashaboost.api.FpsAdaptationBoost").getMethod("isActive").invoke(null);}
 static boolean boostActiveQuiet(){try{return boostActive();}catch(Exception e){return false;}}
 static void frame(TickEvent.RenderTickEvent e){
  if(e.phase!=TickEvent.Phase.END||phase<0)return;long now=System.nanoTime();
  if(lastFrame!=0&&now-phaseStart>SETTLE){
   if(cpuStart<0){cpuStart=THREADS.getCurrentThreadCpuTime();allocStart=THREADS.getCurrentThreadAllocatedBytes();}
   FRAMES.add(now-lastFrame);
  }
  lastFrame=now;
 }
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();long now=System.nanoTime();
  if(phase>=ORDER.length)return true; // done; client ticks go on while a big pack saves its dimensions
  if(phase==-2){
   mc.options.renderDistance().set(8);mc.options.framerateLimit().set(260);mc.options.enableVsync().set(false);
   mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY);mc.options.entityShadows().set(true);
   mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
   if(group.has("perfOrder"))ORDER=group.get("perfOrder").getAsString().split(",");
   mc.player.setYRot(0);mc.player.setXRot(12);boost(false);phase=-1;phaseStart=now;return false;
  }
  mc.player.setYRot(0);mc.player.setXRot(12);
  if(phase==-1){if(now-phaseStart<WARMUP)return false;phase=0;begin(mc,now);return false;}
  if(now-phaseStart<SETTLE+PHASE){if(mc.getSingleplayerServer()!=null&&mc.level.getGameTime()%20==0)MSPT.add((double)mc.getSingleplayerServer().getAverageTickTime());return false;}
  record(mc);
  if(++phase>=ORDER.length){report();return true;}
  begin(mc,now);return false;
 }
 static final String[] COUNTERS={"shaderLookupsSaved","biomeLookupsSaved","particleLightReused","blockEntitiesCulled","distantLightmapUploadsSkipped","entityTicksSkipped","particleVectorsReused","particleRotationsReused","idleFramesCapped",
  "brandonsBoundsSkipped","brandonsSectionWalksSkipped","mahouEntitySetsAvoided","signTextSidesCulled","signTextEmptySidesSkipped","glyphLookupsFast","fontSetLookupsSaved",
  "cloudRainWalksSkippedClient","entityVerdictsReused","samplerNamesReused","cloudRainWalksSkipped","pupfishTriesDeferred","pupfishColumnsReused","pupfishResultsMarkedDirty",
  "modelCubesDirect","modelSpriteCubesDirect","modelCubeFallbacks","particleLightShared","particleLightComputed","quadSortsFast","quadSortsFallback",
  "entitySectionMemoHits","modelQuaternionsReused"};static long[] counterStart=new long[COUNTERS.length];
 // 1.8's off-thread counters are AtomicLongs, which are Numbers too.
 static long counter(String name){return field(name) instanceof Number n?n.longValue():-1;}
 static Object field(String name){try{return Class.forName("ru.pasha.pashaboost.OptimizationCounters").getField(name).get(null);}catch(Exception missing){return "absent";}}
 static Object staticField(String owner,String name){try{var f=Class.forName(owner).getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception missing){return "absent";}}
 static void begin(Minecraft mc,long now)throws Exception{
  for(int i=0;i<COUNTERS.length;i++)counterStart[i]=counter(COUNTERS[i]);
  boost(!ORDER[phase].equals("off"));features(FEATURES_17,!ORDER[phase].equals("on16"));
  features(FEATURES_18,!ORDER[phase].equals("on16")&&!ORDER[phase].equals("on17"));FRAMES.clear();MSPT.clear();lastFrame=0;cpuStart=-1;phaseStart=now;
  LogUtils.getLogger().info("MULTIMOD_QA PERF PHASE {} {} at {}",phase,ORDER[phase],System.currentTimeMillis());
 }
 static void record(Minecraft mc)throws Exception{
  long[] t=FRAMES.stream().mapToLong(Long::longValue).sorted().toArray();if(t.length<10)throw new AssertionError("Too few frames in phase "+phase);
  // clientTick runs on the render thread, so these are the same thread's totals since the first counted frame.
  double cpuMs=(THREADS.getCurrentThreadCpuTime()-cpuStart)/1e6/t.length,allocKb=(THREADS.getCurrentThreadAllocatedBytes()-allocStart)/1024.0/t.length;
  double total=0;for(long v:t)total+=v;double avg=t.length/(total/1e9);
  int worst=Math.max(1,t.length/100);double worstSum=0;for(int i=t.length-worst;i<t.length;i++)worstSum+=t[i];
  double low1=worst/(worstSum/1e9),p95=t[(int)(t.length*.95)]/1e6;
  double mspt=MSPT.stream().mapToDouble(Double::doubleValue).average().orElse(0);
  RESULTS.computeIfAbsent(ORDER[phase],k->new ArrayList<>()).add(new double[]{avg,low1,p95,mspt});
  StringBuilder c=new StringBuilder();for(int i=0;i<COUNTERS.length;i++)c.append(COUNTERS[i]).append('=').append(counter(COUNTERS[i])-counterStart[i]).append(' ');
  LogUtils.getLogger().info("MULTIMOD_QA PERF COUNTERS {} {}",ORDER[phase],c);
  LogUtils.getLogger().info("MULTIMOD_QA PERF FLAGS {} vertexElementArraysBuilt={} entityDataUnlockedSeen={} quadSortReady={} particleLightState={} particleLightFastPath={} particleLightHooks={}",
   ORDER[phase],field("VERTEX_ELEMENT_ARRAYS_BUILT"),field("entityDataUnlockedSeen"),staticField("ru.pasha.pashaboost.client.QuadSortGate","READY"),
   staticField("ru.pasha.pashaboost.client.SharedParticleLight","state"),staticField("ru.pasha.pashaboost.client.SharedParticleLight","fastPath"),
   staticField("ru.pasha.pashaboost.core.ParticleLightShare","hooks"));
  net.minecraft.client.Screenshot.grab(mc.gameDirectory,"perf-"+phase+"-"+ORDER[phase]+".png",mc.getMainRenderTarget(),m->{});
  LogUtils.getLogger().info("MULTIMOD_QA PERF RESULT {} boostActive={} frames={} avgFps={} low1Fps={} p95ms={} mspt={} renderDistance={} graphics={} focused={} cpuMsPerFrame={} allocKbPerFrame={}",
   ORDER[phase],boostActive(),t.length,String.format(Locale.ROOT,"%.1f",avg),String.format(Locale.ROOT,"%.1f",low1),String.format(Locale.ROOT,"%.2f",p95),String.format(Locale.ROOT,"%.2f",mspt),
   mc.options.renderDistance().get(),mc.options.graphicsMode().get(),mc.isWindowActive(),String.format(Locale.ROOT,"%.3f",cpuMs),String.format(Locale.ROOT,"%.1f",allocKb));
 }
 static void report(){
  for(var e:RESULTS.entrySet()){double[] s=new double[4];for(double[] r:e.getValue())for(int i=0;i<4;i++)s[i]+=r[i]/e.getValue().size();
   LogUtils.getLogger().info("MULTIMOD_QA PERF SUMMARY {} avgFps={} low1Fps={} p95ms={} mspt={}",e.getKey(),String.format(Locale.ROOT,"%.1f",s[0]),String.format(Locale.ROOT,"%.1f",s[1]),String.format(Locale.ROOT,"%.2f",s[2]),String.format(Locale.ROOT,"%.2f",s[3]));}
 }
}
