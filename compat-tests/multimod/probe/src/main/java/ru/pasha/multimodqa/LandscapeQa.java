package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import static ru.pasha.multimodqa.MultiModQa.*;

/** A real roof recorded by DH, then observed after vanilla client chunks disappear. */
final class LandscapeQa {
 static int step,ticks;static long began,last;static final List<Double> frames=new ArrayList<>();
 static final String API="com.seibel.distanthorizons.api.";
 static void run(ServerPlayer p)throws Exception{
  reset(p);p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(100000,0,false,false);
  p.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,p.server);
  for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=-59;y<=-48;y++){
   boolean wall=(Math.abs(x)==7||Math.abs(z)==7)&&y<=-51;
   p.serverLevel().setBlock(new BlockPos(x,y,z),(y==-50?Blocks.RED_WOOL:wall?Blocks.QUARTZ_BLOCK:Blocks.AIR).defaultBlockState(),3);
  }
  p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(0,-43,34);p.setYRot(180);p.setXRot(6);p.connection.teleport(0,-43,34,180,6);
  call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
 }
 static Object delayed(String field)throws Exception{return Class.forName(API+"DhApi$Delayed").getField(field).get(null);}
 static Object invoke(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}
 static java.util.concurrent.CompletableFuture<Boolean> query;
 static boolean roof()throws Exception{
  if(query==null){query=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return queryRoof();}catch(Exception e){throw new RuntimeException(e);}});return false;}
  if(!query.isDone())return false;boolean result=query.get();query=null;return result;
 }
 static boolean queryRoof()throws Exception{
  Object world=delayed("worldProxy"),level=invoke(world,"getSinglePlayerLevel");if(level==null)return false;
  Object repo=delayed("terrainRepo");
  Object cache=invoke(repo,"createSoftCache");Object result;
  try{result=repo.getClass().getMethod("getColumnDataAtBlockPos",Class.forName(API+"interfaces.world.IDhApiLevelWrapper"),int.class,int.class,Class.forName(API+"interfaces.data.IDhApiTerrainDataCache")).invoke(repo,level,0,0,cache);}finally{((AutoCloseable)cache).close();}
  if(!result.getClass().getField("success").getBoolean(result))return false;
  Object[] points=(Object[])result.getClass().getField("payload").get(result);if(points==null)return false;
  for(Object point:points){Object state=point.getClass().getField("blockStateWrapper").get(point);if(state!=null&&invoke(state,"getSerialString").toString().contains("red_wool"))return true;}return false;
 }
 static void frame(){long n=System.nanoTime();if(last>0&&step>=3&&step<=4)frames.add((n-last)/1e6);last=n;}
 static void screenshot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"landscape-"+name+".png",mc.getMainRenderTarget(),m->{});}
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();ticks++;if(began==0)began=System.nanoTime();long elapsed=(System.nanoTime()-began)/1_000_000_000L;
  if(elapsed>240)throw new AssertionError("Landscape fixture timed out at step "+step);
  if(step==0){mc.options.renderDistance().set(2);mc.options.fov().set(50);mc.options.hideGui=true;mc.player.setYRot(180);mc.player.setXRot(6);step=1;}
  if(step==1&&ticks%40==0&&roof()){
   screenshot("near");LogUtils.getLogger().info("MULTIMOD_QA LANDSCAPE roof archived from real blocks");
   mc.getSingleplayerServer().submit(()->{try{ServerPlayer p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());learn(p,"environment:landscape_memory",4);call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);p.connection.teleport(0,-40,220,180,3);}catch(Exception e){throw new RuntimeException(e);}}).get();step=2;ticks=0;
  }
  if(step==2&&ticks>600&&ticks%20==0&&!mc.level.getChunkSource().hasChunk(0,0)&&roof()){
   check((boolean)call("ru.pasha.adaptationwheel.client.LandscapeMemory","rendering",new Class<?>[]{}),"Full landscape stage did not enable renderer");
   screenshot("far-on");step=3;ticks=0;frames.clear();
  }
  if(step==3&&ticks>=160){report("on");screenshot("far-on-warm");mc.getSingleplayerServer().submit(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());controlDisable(p,"environment:landscape_memory");call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);}catch(Exception e){throw new RuntimeException(e);}}).get();step=4;ticks=0;frames.clear();}
  if(step==4&&ticks>=160&&ticks%20==0&&roof()){report("off");screenshot("far-off");LogUtils.getLogger().info("MULTIMOD_QA LANDSCAPE PASS: real red roof in terrain archive, vanilla chunk absent at 220 blocks, renderer gated by full stage, disk data retained after disable; frame samples and on/off screenshots saved");step=5;return true;}
  return step==5;
 }
 static void report(String name){var sorted=frames.stream().filter(x->x>0&&x<1000).sorted().toList();if(!sorted.isEmpty())LogUtils.getLogger().info("MULTIMOD_QA LANDSCAPE FRAME {} n={} medianMs={} p95Ms={}",name,sorted.size(),sorted.get(sorted.size()/2),sorted.get((int)(sorted.size()*.95)));}
 static void reload(ServerPlayer p)throws Exception{
  reset(p);learn(p,"environment:landscape_memory",4);call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
  p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(0,-40,220,180,3);
 }
 static boolean reloadClient()throws Exception{
  var mc=Minecraft.getInstance();if(began==0){began=System.nanoTime();mc.options.renderDistance().set(2);mc.options.fov().set(50);mc.options.hideGui=true;}
  if(System.nanoTime()-began>120_000_000_000L)throw new AssertionError("Reloaded archive did not supply old roof");
  if(++ticks%20==0&&ticks>200&&!mc.level.getChunkSource().hasChunk(0,0)&&roof()){
   screenshot("restart-far");LogUtils.getLogger().info("MULTIMOD_QA LANDSCAPE_RESTART PASS: original real roof read from SQLite after full process restart without receiving its vanilla client chunk");return true;
  }return false;
 }
}
