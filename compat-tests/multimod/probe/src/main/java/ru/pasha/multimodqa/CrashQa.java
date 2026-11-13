package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The two crashes from Pasha's game on 2026-10-23. Death with Fabulous graphics: Boost had switched to Fast, a
 * dimension change dropped the Fabulous buffers, and the release on death set Fabulous back without them. Water
 * splashes under OptiFine with custom water colours: a splash rejected by Boost's particle budget came back null and
 * OptiFine recoloured it. Last, Fabulous set in code without a renderer rebuild, as a mod might, must not crash.
 */
final class CrashQa {
 static final int SPLASHES=3000;
 static int step,ticks;static ServerPlayer player;static BlockPos base;static boolean fabulous;
 static void run(ServerPlayer p){
  player=p;base=new BlockPos(p.getBlockX(),170,p.getBlockZ());
  platform(p.serverLevel(),base);
  p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);
  LogUtils.getLogger().info("MULTIMOD_QA CRASH STAGE: platform ready at {}",base);
 }
 static void platform(ServerLevel level,BlockPos at){
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){level.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<3;y++)level.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
 }
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();if(player==null||mc.player==null)return false;
  if(++ticks>3000)throw new AssertionError("Crash probe timed out at step "+step);
  var server=mc.getSingleplayerServer();
  switch(step){
   case 0->{PerfQa.boost(false);mc.options.particles().set(ParticleStatus.ALL);
    // As the video settings button does it: the option alone does not rebuild the renderer.
    mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);mc.levelRenderer.allChanged();ticks=0;step=1;}
   case 1->{if(ticks<40)return false;
    fabulous=mc.options.graphicsMode().get()==GraphicsStatus.FABULOUS&&mc.levelRenderer.getTranslucentTarget()!=null;
    LogUtils.getLogger().info("MULTIMOD_QA CRASH STAGE: Fabulous {}",fabulous?"on with buffers":"unavailable here, graphics checks skipped");
    PerfQa.boost(true);ticks=0;step=2;}
   case 2->{if(!PerfQa.boostActive()||ticks<40)return false;
    if(fabulous&&mc.options.graphicsMode().get()!=GraphicsStatus.FAST)throw new AssertionError("Boost did not switch to Fast: "+mc.options.graphicsMode().get());
    server.execute(()->{var nether=server.getLevel(Level.NETHER);var at=new BlockPos(base.getX()/8,100,base.getZ()/8);
     platform(nether,at);player.teleportTo(nether,at.getX()+.5,at.getY(),at.getZ()+.5,0,0);});
    ticks=0;step=3;}
   case 3->{if(mc.level.dimension()!=Level.NETHER||ticks<60)return false;
    server.execute(()->player.teleportTo(server.overworld(),base.getX()+.5,base.getY(),base.getZ()+.5,0,0));ticks=0;step=4;}
   case 4->{if(mc.level.dimension()!=Level.OVERWORLD||ticks<60)return false;
    LogUtils.getLogger().info("MULTIMOD_QA CRASH STAGE: back from the nether, graphics={} buffers={}",mc.options.graphicsMode().get(),mc.levelRenderer.getTranslucentTarget()!=null);
    splashes(mc);ticks=0;step=5;}
   case 5->{if(ticks<20)return false;server.execute(()->player.kill());ticks=0;step=6;}
   case 6->{if(mc.player.isAlive()||PerfQa.boostActive()||ticks<60)return false;
    // The old build crashed on the first frame after the release; three seconds of frames have passed.
    if(fabulous&&(mc.options.graphicsMode().get()!=GraphicsStatus.FABULOUS||mc.levelRenderer.getTranslucentTarget()==null))
     throw new AssertionError("Fabulous not restored with its buffers after death: "+mc.options.graphicsMode().get());
    LogUtils.getLogger().info("MULTIMOD_QA CRASH DEATH PASS: died wearing the wheel, Boost released, graphics {}{}, frames kept rendering",
     mc.options.graphicsMode().get(),mc.levelRenderer.getTranslucentTarget()!=null?" with buffers":"");
    PerfQa.boost(false);mc.player.respawn();mc.setScreen(null);ticks=0;step=7;}
   case 7->{if(!mc.player.isAlive()||ticks<40)return false;
    if(!fabulous){step=9;ticks=0;return false;}
    // A mod switching graphics in code: Fast with a rebuild, which drops the buffers, then Fabulous without one.
    mc.options.graphicsMode().set(GraphicsStatus.FAST);mc.levelRenderer.allChanged();
    mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);ticks=0;step=8;}
   case 8->{if(ticks<20)return false;
    if(mc.levelRenderer.getTranslucentTarget()==null)throw new AssertionError("Fabulous without buffers was not repaired");
    LogUtils.getLogger().info("MULTIMOD_QA CRASH GUARD PASS: Fabulous set in code without a rebuild got its buffers before a frame");
    ticks=0;step=9;}
   case 9->{if(ticks<20)return false;LogUtils.getLogger().info("MULTIMOD_QA CRASH ALL PASS");return true;}
  }
  return false;
 }
 /** A burst of splashes over Boost's per-tick budget, with OptiFine recolouring each one as custom water colours do. */
 static void splashes(Minecraft mc)throws Exception{
  var rejectedCount=Class.forName("ru.pasha.pashaboost.client.ParticleOptimizer").getMethod("rejectedCount");
  long before=(long)rejectedCount.invoke(null);
  Field color=null;int original=0;
  try{color=Class.forName("net.optifine.CustomColors").getDeclaredField("particleWaterColor");color.setAccessible(true);original=color.getInt(null);color.setInt(null,0x3F76E4);}
  catch(ClassNotFoundException absent){color=null;}
  try{var pos=mc.player.position();
   for(int i=0;i<SPLASHES;i++)mc.level.addParticle(ParticleTypes.SPLASH,pos.x-1+(i%10)*.2,pos.y+1,pos.z+2+(i/10%10)*.2,0,0,0);}
  finally{if(color!=null)color.setInt(null,original);}
  long rejected=(long)rejectedCount.invoke(null)-before;
  if(rejected<SPLASHES/3)throw new AssertionError("Particle budget rejected only "+rejected+" of "+SPLASHES+" splashes");
  LogUtils.getLogger().info("MULTIMOD_QA CRASH SPLASH PASS: {} splashes, {} rejected by the budget, OptiFine water colour {}",SPLASHES,rejected,color!=null?"on":"absent");
 }
}
