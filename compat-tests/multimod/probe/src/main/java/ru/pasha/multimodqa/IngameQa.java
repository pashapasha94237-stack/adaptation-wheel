package ru.pasha.multimodqa;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import static ru.pasha.multimodqa.MultiModQa.*;

/** 4.16 fixes in a real client: the normal world stays drawn after full landscape memory
 *  (with and without OptiFine) and the player's own footsteps never become silent-world markers. */
final class IngameQa {
 static final String CLIENT="ru.pasha.adaptationwheel.client.",META="ru.pasha.adaptationwheel.core.MetaMechanic";
 static int step,ticks,ownSteps,heardBefore;static boolean optifine;
 static void run(ServerPlayer p)throws Exception{
  reset(p);p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(100000,0,false,false);
  p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);
  learn(p,"environment:silent_world",4);learn(p,"environment:landscape_memory",4);
  call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
 }
 /** Counts the footsteps the local player really makes, independently of the wheel's filter. */
 static void sound(PlaySoundEvent e){
  var mc=Minecraft.getInstance();var s=e.getSound();if(step!=2||s==null||mc.player==null||s.isRelative())return;
  if(s.getSource()==SoundSource.PLAYERS&&s.getLocation().getPath().contains("step")
     &&mc.player.position().distanceToSqr(s.getX(),s.getY(),s.getZ())<=2.5*2.5)ownSteps++;
 }
 @SuppressWarnings({"rawtypes","unchecked"})
 static boolean full(String mechanic)throws Exception{return (boolean)call(CLIENT+"MetaClient","full",new Class<?>[]{Class.forName(META)},Enum.valueOf((Class)Class.forName(META),mechanic));}
 static int heard()throws Exception{var f=Class.forName(CLIENT+"MetaClient").getDeclaredField("HEARD");f.setAccessible(true);return ((Collection<?>)f.get(null)).size();}
 /** Share of the most common colour and number of distinct colours in the lower 60% of the frame. */
 static double[] picture(){
  var mc=Minecraft.getInstance();Map<Integer,Integer> colours=new HashMap<>();int samples=0;
  try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
   int w=image.getWidth(),h=image.getHeight();
   for(int y=(int)(h*.4);y<h;y+=Math.max(1,h/60))for(int x=0;x<w;x+=Math.max(1,w/80)){
    int c=image.getPixelRGBA(x,y);int q=(c>>4&0xF)|(c>>12&0xF)<<4|(c>>20&0xF)<<8;colours.merge(q,1,Integer::sum);samples++;
   }
  }
  int top=colours.values().stream().max(Integer::compare).orElse(samples);
  return new double[]{top/(double)samples,colours.size()};
 }
 static boolean visible(double[] m){return m[0]<.85&&m[1]>=8;}
 static void screenshot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"ingame-"+name+".png",mc.getMainRenderTarget(),m->{});}
 static void dhRendering(boolean value)throws Exception{
  Object configs=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs").get(null);
  Object graphics=configs.getClass().getMethod("graphics").invoke(configs);Object entry=graphics.getClass().getMethod("renderingEnabled").invoke(graphics);
  entry.getClass().getMethod("setValue",Object.class).invoke(entry,value);
 }
 static String variant(){return optifine?"optifine":"vanilla";}
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();ticks++;
  if(ticks>2400)throw new AssertionError("In-game checks timed out at step "+step);
  optifine=(boolean)call(CLIENT+"LandscapeMemory","optifine",new Class<?>[]{});
  switch(step){
   case 0->{
    if(!full("SILENCE")||!full("LANDSCAPE"))return false;
    mc.options.hideGui=true;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0D);
    mc.player.setYRot(0);mc.player.setXRot(35);step=1;ticks=0;
   }
   case 1->{
    // Landscape memory applies its state once per second; give chunks time to build and draw.
    if(ticks<240)return false;
    boolean rendering=(boolean)call(CLIENT+"LandscapeMemory","rendering",new Class<?>[]{});
    double[] m=picture();screenshot("landscape-full-"+variant());
    LogUtils.getLogger().info("MULTIMOD_QA INGAME LANDSCAPE {} rendering={} topColourShare={} colours={} renderedChunks={}",variant(),rendering,m[0],(int)m[1],mc.levelRenderer.countRenderedChunks());
    check(rendering==!optifine,"Distant terrain drawing state wrong for "+variant()+": "+rendering);
    check(visible(m),"Normal world is not visible after full landscape memory ("+variant()+")");
    heardBefore=heard();ownSteps=0;step=2;ticks=0;
   }
   case 2->{
    // Real walking and sprinting: the local player plays its own step sounds.
    mc.player.setXRot(10);mc.options.keyUp.setDown(ticks<=100);mc.options.keySprint.setDown(ticks>40&&ticks<=100);
    if(ticks%20==0&&ticks<=100)mc.options.keyJump.setDown(true);else mc.options.keyJump.setDown(false);
    check(heard()<=heardBefore,"Own footsteps produced a silent-world marker after "+ownSteps+" steps");
    if(ticks<120)return false;
    mc.options.keyUp.setDown(false);mc.options.keySprint.setDown(false);mc.options.keyJump.setDown(false);
    check(ownSteps>=4,"Walking produced too few real footstep sounds to judge: "+ownSteps);
    LogUtils.getLogger().info("MULTIMOD_QA INGAME OWN_STEPS {} steps={} markers={}",variant(),ownSteps,heard()-heardBefore);
    var pos=mc.player.position();
    mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.ZOMBIE_STEP,SoundSource.HOSTILE,1,1,RandomSource.create(),pos.x+3,pos.y,pos.z));
    check(heard()>heardBefore,"A zombie step no longer adds a silent-world marker");
    step=optifine?3:5;ticks=0;
   }
   case 3->{
    // Diagnostic only: force the distant renderer on under OptiFine to see the original 4.16.0 failure.
    dhRendering(true);if(ticks<200)return false;
    double[] m=picture();screenshot("optifine-dh-forced");
    LogUtils.getLogger().info("MULTIMOD_QA INGAME DIAG optifine forced-dh topColourShare={} colours={} visible={}",m[0],(int)m[1],visible(m));
    dhRendering(false);step=4;ticks=0;
   }
   case 4->{
    if(ticks<100)return false;
    double[] m=picture();screenshot("optifine-restored");
    check(visible(m)&&!(boolean)call(CLIENT+"LandscapeMemory","rendering",new Class<?>[]{}),"World did not return after the distant renderer was turned off again");
    step=5;
   }
   default->{}
  }
  if(step==5){LogUtils.getLogger().info("MULTIMOD_QA INGAME PASS: {} world visible after full landscape memory, own footsteps never marked, mob steps still marked",variant());return true;}
  return false;
 }
}
