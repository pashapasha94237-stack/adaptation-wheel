package ru.pasha.multimodqa;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

import net.minecraft.world.item.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.TickEvent;
import com.mojang.logging.LogUtils;
import static ru.pasha.multimodqa.MultiModQa.*;

/** Real containers/border, client packets, sound engine and action-bar tests. No production shortcuts. */
final class UtilityQa {
 static final String META="ru.pasha.adaptationwheel.core.MetaMechanic";
 static int clientTicks;
 static boolean clientVerified;
 static Object mechanic(String name)throws Exception{return Enum.valueOf((Class)Class.forName(META),name);}
 static Object meta(String method,ServerPlayer p,String name)throws Exception{return call(BASE+"MetaServer",method,new Class<?>[]{ServerPlayer.class,Class.forName(META)},p,mechanic(name));}
 static String key(String id){return "environment:"+id;}
 static void run(ServerPlayer p)throws Exception{
  switch(group.get("utility").getAsString()){
   case "autonomous-health"->AutonomousQa.run(p);
   case "corpus-health"->NativeHealthModsQa.run(p);
   case "native-extra-effects"->NativeExtraEffectsQa.run(p);
   case "corpus-states"->NativeExtraStateQa.run(p);
   case "autonomous-states"->NativeStateQa.run(p);
   case "learning"->LearningQa.run(p);
   case "learning-reload"->LearningReloadQa.run(p);
   case "aim"->AimQa.run(p);
   case "pocket-border","border"->border(p);
   case "stacks"->StacksQa.run(p);
   case "mem"->MemQa.run(p);
   case "xp"->XpQa.run(p);
   case "crash"->CrashQa.run(p);
   case "analyzer"->AnalyzerQa.run(p);
   case "landscape"->LandscapeQa.run(p);
   case "landscape-reload"->LandscapeQa.reload(p);
   case "ingame"->IngameQa.run(p);
   case "outside-a"->OutsideQa.runA(p);
   case "slash"->SlashQa.run(p);
   case "perf"->PerfQa.run(p);
   case "forearm"->ForearmQa.run(p);
   case "screens"->ScreensQa.run(p);
   case "defeat"->DefeatQa.run(p);
   case "performance"->PerformanceQa.run(p);
   case "adapt429"->Adapt429Qa.run(p);
   case "adapt430"->Adapt430Qa.run(p);
   case "cmd431"->Cmd431Qa.run(p);
   case "client"->{
    reset(p);learn(p,key("notification_noise"),4);learn(p,key("silent_world"),4);
    call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
   }
   default->throw new AssertionError("Unknown utility fixture");
  }
 }
 static void border(ServerPlayer p)throws Exception{
  reset(p);var border=p.serverLevel().getWorldBorder();double size=border.getSize(),cx=border.getCenterX(),cz=border.getCenterZ();var original=p.position();
  try{
   border.setCenter(0,0);border.setSize(32);p.teleportTo(14,-59,0);p.tickCount=200;
   for(int x=10;x<=24;x++)for(int z=-2;z<=2;z++){p.serverLevel().setBlock(new BlockPos(x,-60,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);for(int y=-59;y<=-56;y++)p.serverLevel().setBlock(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}
   call(BASE+"MetaServer","playerTick",new Class<?>[]{TickEvent.PlayerTickEvent.class},new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
   check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key("world_border")),"Actual border contact did not start learning");
   call(BASE+"MetaServer","cross",new Class<?>[]{ServerPlayer.class},p);check(p.getX()==14,"Unlearned personal passage succeeded");
   learn(p,key("world_border"),4);call(BASE+"MetaServer","cross",new Class<?>[]{ServerPlayer.class},p);
   check(p.getX()>16&&border.getSize()==32&&border.getCenterX()==0,"Passage failed or changed shared border");
   float before=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().outOfBorder(),4);check(p.getHealth()==before,"Personal full passage still takes outside-border damage");
   controlDisable(p,key("world_border"));p.tickCount=240;call(BASE+"MetaServer","playerTick",new Class<?>[]{TickEvent.PlayerTickEvent.class},new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
   check(p.getX()<16&&p.getX()>-16&&p.fallDistance==0,"Disabled passage did not safely return inside");
   p.teleportTo(20,-59,0);p.invulnerableTime=0;p.hurt(p.damageSources().outOfBorder(),4);check(p.getHealth()<before,"Disabled border immunity remained active");
   state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key("world_border"),true);
   p.teleportTo(14,-59,0);p.tickCount=280;call(BASE+"MetaServer","cross",new Class<?>[]{ServerPlayer.class},p);check(p.getX()>16,"Second personal crossing failed");
   ItemStack worn=p.getItemBySlot(EquipmentSlot.HEAD);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.tickCount=300;call(BASE+"MetaServer","playerTick",new Class<?>[]{TickEvent.PlayerTickEvent.class},new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
   check(p.getX()==14&&p.getZ()==0&&p.fallDistance==0,"Actual unequip did not return to saved safe entry anchor");p.setItemSlot(EquipmentSlot.HEAD,worn);
   LogUtils.getLogger().info("MULTIMOD_QA UTILITY_BORDER PASS: native contact starts learning, locked passage denied, actual player crosses unchanged shared border, full outside HP immunity, disable returns safely and restores real damage");
  }finally{border.setCenter(cx,cz);border.setSize(size);p.teleportTo(original.x,original.y,original.z);p.getPersistentData().getCompound("adaptationwheel_meta").putBoolean("Outside",false);reset(p);}
 }
 static boolean clientTick()throws Exception{
  if(group.get("utility").getAsString().equals("autonomous-health"))return AutonomousQa.clientTick();
  if(group.get("utility").getAsString().equals("learning"))return LearningQa.clientTick();
  if(group.get("utility").getAsString().equals("learning-reload"))return LearningReloadQa.clientTick();
  if(group.get("utility").getAsString().equals("aim"))return AimQa.clientTick();
  if(group.get("utility").getAsString().equals("performance"))return PerformanceQa.clientTick();
  if(group.get("utility").getAsString().equals("defeat"))return DefeatQa.clientTick();
  if(group.get("utility").getAsString().equals("landscape"))return LandscapeQa.clientTick();
  if(group.get("utility").getAsString().equals("landscape-reload"))return LandscapeQa.reloadClient();
  if(group.get("utility").getAsString().equals("ingame"))return IngameQa.clientTick();
  if(group.get("utility").getAsString().equals("outside-a"))return OutsideQa.clientA();
  if(group.get("utility").getAsString().equals("slash"))return SlashQa.clientTick();
  if(group.get("utility").getAsString().equals("perf"))return PerfQa.clientTick();
  if(group.get("utility").getAsString().equals("forearm"))return ForearmQa.clientTick();
  if(group.get("utility").getAsString().equals("screens"))return ScreensQa.clientTick();
  if(group.get("utility").getAsString().equals("stacks"))return StacksQa.clientTick();
  if(group.get("utility").getAsString().equals("mem"))return MemQa.clientTick();
  if(group.get("utility").getAsString().equals("xp"))return XpQa.clientTick();
  if(group.get("utility").getAsString().equals("crash"))return CrashQa.clientTick();
  if(group.get("utility").getAsString().equals("outside-b"))return OutsideQa.clientB();
  if(group.get("utility").getAsString().equals("adapt429"))return Adapt429Qa.clientTick();
  if(group.get("utility").getAsString().equals("adapt430"))return Adapt430Qa.clientTick();
  if(group.get("utility").getAsString().equals("cmd431"))return Cmd431Qa.clientTick();
  if(!group.get("utility").getAsString().equals("client"))return true;
  var mc=Minecraft.getInstance();if(++clientTicks>240)throw new AssertionError("Client utilities timed out");
  if(clientVerified){if(clientTicks<15)return false;Screenshot.grab(mc.gameDirectory,"utility-graphics.png",mc.getMainRenderTarget(),m->{});LogUtils.getLogger().info("MULTIMOD_QA GRAPHICS PASS: native VideoSettingsScreen intercepted by wheel's own menu with bundled landscape engine and OptiFine loaded");return true;}
  if(!(boolean)call("ru.pasha.adaptationwheel.client.MetaClient","full",new Class<?>[]{Class.forName(META)},mechanic("SILENCE")))return false;
  if(clientTicks<5)return false;
  boolean ready=(boolean)call("ru.pasha.adaptationwheel.client.LandscapeMemory","ready",new Class<?>[]{});
  check(ready,"Bundled terrain engine did not initialize");check(!(boolean)call("ru.pasha.adaptationwheel.client.LandscapeMemory","rendering",new Class<?>[]{}),"Landscape rendering active before own full adaptation");
  mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0D);
  Object heard=field("ru.pasha.adaptationwheel.client.MetaClient","HEARD");int before=((Collection<?>)heard).size();
  var pos=mc.player.position();mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(net.minecraft.sounds.SoundEvents.ZOMBIE_STEP,net.minecraft.sounds.SoundSource.HOSTILE,1,1,net.minecraft.util.RandomSource.create(),pos.x+3,pos.y,pos.z));
  check(((Collection<?>)heard).size()>before,"Actual muted sound-engine event failed to add a direction marker");
  mc.gui.setOverlayMessage(Component.literal("Подобрано: руда +1"),false);mc.gui.setOverlayMessage(Component.literal("Подобрано: руда +1"),false);
  Object noise=field("ru.pasha.adaptationwheel.client.MetaClient","noise");check(noise.toString().contains("×2"),"Actual action-bar hook did not group repeated message");
  mc.gui.setOverlayMessage(Component.literal("Опасность: взрыв рядом!"),false);
  check(!(boolean)call("ru.pasha.adaptationwheel.client.MetaClient","overlay",new Class<?>[]{Component.class},Component.literal("Опасность: взрыв рядом!")),"Important warning suppressed");
  Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->{});
  if(group.has("graphics")){mc.setScreen(new net.minecraft.client.gui.screens.OptionsScreen(new net.minecraft.client.gui.screens.TitleScreen(),mc.options));mc.setScreen(new net.minecraft.client.gui.screens.VideoSettingsScreen(mc.screen,mc.options));check(mc.screen.getClass().getName().equals("ru.pasha.pashaboost.client.OwnVideoScreen"),"Own graphics menu not installed");clientVerified=true;return false;}
  LogUtils.getLogger().info("MULTIMOD_QA UTILITY_CLIENT PASS: actual muted SoundEngine produces directional event, native action-bar repeat grouped x2, danger retained, engine ready and rendering gated before landscape full");return true;
 }
 static Object field(String owner,String name)throws Exception{var f=Class.forName(owner).getDeclaredField(name);f.setAccessible(true);return f.get(null);}
}
