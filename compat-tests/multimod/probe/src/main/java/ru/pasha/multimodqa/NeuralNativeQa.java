package ru.pasha.multimodqa;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Local HTTP fixture proves both actual JAR variants and server-side async fallback. */
final class NeuralNativeQa {
 static HttpServer fixture;static volatile int requests;static boolean online;static int ticks,step,initialDuration;static long began;
 static final String KEY=CLIENT_KEY;
 static void set(String name,Object value)throws Exception{Object option=Class.forName("ru.pasha.adaptationwheel.NeuralConfig").getField(name).get(null);option.getClass().getMethod("set",Object.class).invoke(option,value);}
 static DamageSource source()throws Exception{return new DamageSource(custom("multimodqa:unknown_light_beam","multimodqa:unknown_light_beam"));}
 static void run(ServerPlayer p)throws Exception{
  reset(p);damageTypes=p.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
  online=Class.forName("ru.pasha.adaptationwheel.NeuralConfig").getField("ONLINE").getBoolean(null);check(online==group.get("expectedOnline").getAsBoolean(),"Actual JAR capability marker wrong");
  fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);fixture.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r->{var t=new Thread(r);t.setDaemon(true);return t;}));
  fixture.createContext("/valid",exchange->{requests++;String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);check(!body.contains(p.getUUID().toString())&&!body.contains(p.getGameProfile().getName()),"Request includes player identity");
   JsonObject answer=new JsonObject();JsonArray choices=new JsonArray();JsonObject choice=new JsonObject(),message=new JsonObject();choice.addProperty("finish_reason","stop");message.addProperty("content","{\"profile\":\"ELECTRIC\",\"firstSeconds\":9,\"stepSeconds\":10,\"confidence\":80}");choice.add("message",message);choices.add(choice);answer.add("choices",choices);
   byte[] bytes=answer.toString().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });
  fixture.createContext("/rate",exchange->{requests++;try{Thread.sleep(500);}catch(InterruptedException ignored){}exchange.sendResponseHeaders(429,-1);exchange.close();});fixture.start();
  if(online){set("ENABLED",true);set("ENDPOINT","http://127.0.0.1:"+fixture.getAddress().getPort()+"/valid");set("MODEL","fixture-model");set("KEY_ENV","ADAPTATION_QA_UNUSED_VARIABLE");}
  hit(p,source(),4);wheelTick(p);initialDuration=duration(required(p,KEY));hit(p,source(),4);wheelTick(p);
 }
 static void server(java.util.function.Consumer<ServerPlayer> action)throws Exception{var mc=Minecraft.getInstance();mc.getSingleplayerServer().submit(()->action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()))).get();}
 static boolean clientTick()throws Exception{
  if(began==0)began=System.nanoTime();if(System.nanoTime()-began>30_000_000_000L)throw new AssertionError("Native neural fixture timeout at "+step);ticks++;
  if(!online&&ticks>40){check(requests==0,"Offline JAR made HTTP request despite disabled build capability");check(call(BASE+"NeuralAdvisor","status",new Class<?>[]{}).equals("offline"),"Offline status misleading");fixture.stop(0);LogUtils.getLogger().info("MULTIMOD_QA NEURAL_OFFLINE PASS: actual offline JAR never registers remote configuration or calls the available local fixture; local learning continues");return true;}
  if(!online)return false;
  if(step==0&&ticks>20&&call(BASE+"NeuralAdvisor","status",new Class<?>[]{}).equals("ready")){
   server(p->{try{var row=required(p,KEY);check(getter(row,"profile").toString().equals("ELECTRIC")&&getter(row,"analysisReason").equals("online.advisory"),"Actual HTTP advice did not reach server classifier");check(duration(row)==initialDuration&&number(row,"work")>0,"Remote reply reset/shortened active clock");check(requests==1,"Unexpected duplicate remote calls");
    call(BASE+"NeuralAdvisor","reset",new Class<?>[]{});set("ENDPOINT","http://127.0.0.1:"+fixture.getAddress().getPort()+"/rate");hit(p,source(),4);wheelTick(p);
   }catch(Exception e){throw new RuntimeException(e);}});step=1;ticks=0;
  }
  if(step==1&&ticks>30&&call(BASE+"NeuralAdvisor","status",new Class<?>[]{}).equals("fallback")){
   server(p->{try{var row=required(p,KEY);check(duration(row)==initialDuration&&number(row,"work")>0&&p.isAlive(),"HTTP failure broke actual progression");check(getter(getter(info(p,KEY),"insight"),"advisor").equals("fallback"),"Fallback not visible in server explanation");check(requests==2,"Failure fixture not contacted");}catch(Exception e){throw new RuntimeException(e);}});
   fixture.stop(0);LogUtils.getLogger().info("MULTIMOD_QA NEURAL_ONLINE PASS: actual online JAR sends redacted facts to loopback API; bounded advice changes future classifier without active timer/work reset; HTTP429 returns to local analysis while native ticks continue");return true;
  }return false;
 }
}
