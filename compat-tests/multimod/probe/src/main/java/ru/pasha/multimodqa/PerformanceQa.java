package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import static ru.pasha.multimodqa.MultiModQa.*;

final class PerformanceQa {
 static volatile long slowUntil;static long began;static int step;
 static void run(ServerPlayer p)throws Exception{
  reset(p);slowUntil=System.nanoTime()+15_000_000_000L;
  MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,(TickEvent.ServerTickEvent e)->{if(e.phase==TickEvent.Phase.START&&System.nanoTime()<slowUntil)java.util.concurrent.locks.LockSupport.parkNanos(110_000_000L);});
 }
 static int saves(ServerPlayer p,boolean batched)throws Exception{
  reset(p);if(batched)learn(p,"environment:low_tps",4);
  state(p).getClass().getMethod("hit",String.class,boolean.class).invoke(state(p),"damage:minecraft:fall",false);
  p.tickCount=2000;call(BASE+"AdaptationEvents","save",new Class<?>[]{ServerPlayer.class},p);
  Object previous=p.getPersistentData().get("adaptationwheel");int count=0;
  for(int i=0;i<40;i++){wheelTick(p);Object current=p.getPersistentData().get("adaptationwheel");if(current!=previous)count++;previous=current;}
  var pending=(java.util.Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p));check(!pending.isEmpty(),"Batching discarded active adaptation");return count;
 }
 static boolean clientTick()throws Exception{
  if(began==0)began=System.nanoTime();long elapsed=System.nanoTime()-began;if(elapsed>35_000_000_000L)throw new AssertionError("TPS fixture timeout");
  if(step==0&&elapsed>16_000_000_000L){
   var mc=Minecraft.getInstance();mc.getSingleplayerServer().submit(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    check(((java.util.Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey("environment:low_tps"),"Actual slow server ticks did not start TPS adaptation");
    int ordinary=saves(p,false),batched=saves(p,true);check(ordinary==40&&batched<=3,"No real NBT serialization reduction: "+ordinary+" -> "+batched);
    LogUtils.getLogger().info("MULTIMOD_QA PERFORMANCE PASS: sustained actual 110ms server ticks start learning, actual persistent NBT replacements {} -> {} over 40 progression ticks",ordinary,batched);
   }catch(Exception e){throw new RuntimeException(e);}}).get();step=1;return true;
  }return step==1;
 }
}
