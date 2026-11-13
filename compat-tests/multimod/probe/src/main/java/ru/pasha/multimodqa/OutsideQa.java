package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import static ru.pasha.multimodqa.MultiModQa.*;

/** 4.17 outside adaptations in a real client. Part A ends with a simulated crash; part B resumes the renamed world. */
final class OutsideQa {
 static final String CLIENT="ru.pasha.adaptationwheel.client.",META="ru.pasha.adaptationwheel.core.MetaMechanic";
 static final String[] ALL={"foreign_mods","log_errors","world_rename","config_edit","clock_shift","crash_memory","idle_player","open_inventory","connection_loss","wheel_update","console_commands","memory_pressure"};
 static int step,ticks;static float before;
 static void runA(ServerPlayer p)throws Exception{
  reset(p);for(String id:ALL)learn(p,"environment:"+id,4);
  call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
 }
 @SuppressWarnings({"rawtypes","unchecked"})
 static boolean full(String mechanic)throws Exception{return (boolean)call(CLIENT+"MetaClient","full",new Class<?>[]{Class.forName(META)},Enum.valueOf((Class)Class.forName(META),mechanic));}
 static ServerPlayer server(){var mc=Minecraft.getInstance();return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
 static float zombieHit()throws Exception{
  return Minecraft.getInstance().getSingleplayerServer().submit(()->{
   ServerPlayer p=server();var zombie=EntityType.ZOMBIE.create(p.serverLevel());zombie.setPos(p.getX()+1,p.getY(),p.getZ());
   float h=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(zombie),6);zombie.discard();return h-p.getHealth();
  }).get();
 }
 static boolean clientA()throws Exception{
  var mc=Minecraft.getInstance();ticks++;if(ticks>3000)throw new AssertionError("Outside part A timed out at step "+step);
  switch(step){
   case 0->{if(!full("INVENTORY")||!full("LOGS")||!full("CONFIG")||!full("COMMANDS"))return false;step=1;ticks=0;}
   case 1->{if(ticks==1)mc.setScreen(new InventoryScreen(mc.player));if(ticks<20)return false;
    float shielded=zombieHit();mc.setScreen(null);
    check(shielded==0,"Open inventory in combat did not protect: "+shielded);step=2;ticks=0;}
   case 2->{if(ticks<20)return false;float open=zombieHit();check(open>0,"Closed inventory still protected");
    LogUtils.getLogger().info("MULTIMOD_QA OUTSIDE INVENTORY PASS: real InventoryScreen blocks a zombie hit, closing restores damage {}",open);step=3;ticks=0;}
   case 3->{
    // This class loader's logger context is created on first use; the wheel attaches to new contexts once per second.
    var log=LogManager.getLogger("OutsideQaRepeat");
    if(ticks<60||!(boolean)call("ru.pasha.adaptationwheel.server.LogWatch","trimming",new Class<?>[]{}))return false;
    int suppressedBefore=(int)call("ru.pasha.adaptationwheel.server.LogWatch","suppressed",new Class<?>[]{});
    for(int i=0;i<1000;i++)log.error("OUTSIDE_QA repeated error {}","same");
    int hidden=(int)call("ru.pasha.adaptationwheel.server.LogWatch","suppressed",new Class<?>[]{})-suppressedBefore;
    var context=((org.apache.logging.log4j.core.Logger)log).getContext();
    LogUtils.getLogger().info("MULTIMOD_QA OUTSIDE LOG_DIAG factory={} context={} filter={} errorsTotal={} hidden={}",LogManager.getFactory().getClass().getName(),context.getName(),
     context.getConfiguration().getFilter(),call("ru.pasha.adaptationwheel.server.LogWatch","totalErrors",new Class<?>[]{}),hidden);
    check(hidden>=990,"Repeated log errors were not trimmed: "+hidden);
    LogManager.getLogger("OutsideQaFatal").fatal("OUTSIDE_QA fatal stays");
    LogUtils.getLogger().info("MULTIMOD_QA OUTSIDE LOGS PASS: 1000 identical errors, {} hidden",hidden);step=4;ticks=0;}
   case 4->{
    Path file=mc.gameDirectory.toPath().resolve("config/adaptationwheel-utility-client.toml");
    String text=Files.readString(file,StandardCharsets.UTF_8);check(text.contains("heapPressureThreshold = 0.85"),"Unexpected client config");
    Files.writeString(file,text.replace("heapPressureThreshold = 0.85","heapPressureThreshold = 0.9"),StandardCharsets.UTF_8);step=5;ticks=0;}
   case 5->{if(ticks<200)return false;
    Path file=mc.gameDirectory.toPath().resolve("config/adaptationwheel-utility-client.toml");
    Files.writeString(file,Files.readString(file,StandardCharsets.UTF_8).replace("heapPressureThreshold = 0.9","heapPressureThreshold = 0.85"),StandardCharsets.UTF_8);
    step=50;ticks=0;}
   case 50->{if(ticks<120)return false;
    // The wheel saving its own FPS progress must not be reported as someone editing the config.
    boolean enabled=(boolean)call(CLIENT+"FpsAdaptation","enabled",new Class<?>[]{});
    call(CLIENT+"FpsAdaptation","setEnabled",new Class<?>[]{boolean.class},enabled);
    LogUtils.getLogger().info("MULTIMOD_QA OUTSIDE OWN_SAVE marker: fps config saved by the wheel");step=51;ticks=0;}
   case 51->{if(ticks<200)return false;mc.player.connection.sendCommand("kill");step=6;ticks=0;}
   case 6->{if(ticks<60)return false;
    check(mc.player.isAlive()&&!(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen),"/kill went through the full console-command adaptation");
    LogUtils.getLogger().info("MULTIMOD_QA OUTSIDE KILL PASS: real /kill refused");
    mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().saveEverything(true,true,true)).get();
    LogUtils.getLogger().info("MULTIMOD_QA OUTSIDE PART_A PASS: simulating a crash now");
    LogUtils.getLogger().info("MULTIMOD_QA PASS: outside-a");
    LogManager.shutdown();Runtime.getRuntime().halt(0);}
   default->{}
  }
  return false;
 }
 static boolean clientB()throws Exception{
  var mc=Minecraft.getInstance();ticks++;if(ticks>1200)throw new AssertionError("Outside part B timed out");
  if(ticks<40)return false;
  String result=mc.getSingleplayerServer().submit(()->{
   ServerPlayer p=server();CompoundTag seen=p.getPersistentData().getCompound("adaptationwheel_meta").getCompound("Seen");
   StringBuilder missing=new StringBuilder();
   for(String id:new String[]{"crash_memory","world_rename","wheel_update","foreign_mods"})if(seen.getLong(id)==0)missing.append(id).append(' ');
   if(!p.hasEffect(MobEffects.DAMAGE_RESISTANCE))missing.append("crash-shield ");
   return missing.toString();
  }).get();
  if(!result.isEmpty()){if(ticks<600)return false;throw new AssertionError("Outside events not delivered: "+result);}
  LogUtils.getLogger().info("MULTIMOD_QA OUTSIDE PART_B PASS: crash, rename, wheel update and mod changes delivered after restart; post-crash shield active");
  return true;
 }
}
