package ru.pasha.multimodqa;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import static ru.pasha.multimodqa.MultiModQa.*;

/**
 * The 4.31.0 /adaptation commands typed by the real client: the command tree the server sent, a suggestion request
 * that goes to the server and back, then learn, disable, list, info, enable, an unknown name and forget as chat
 * commands, each checked on the server, in the client's synced wheel and in the chat the client received.
 */
final class Cmd431Qa {
 static final String CLIENT="ru.pasha.adaptationwheel.client.",ZOMBIE="mob:minecraft:zombie";
 static final String[] KEYS={"unknown","ambiguous","forget.none","for","for.many","status.of","none","list.header","list.entry","list.off","list.running",
  "list.idle","list.next","info.header","info.stage","info.protection","info.effect","info.darkness","switched.on","switched.off","learn.one","learn.many","forget.one","forget.many"};
 static int step,t;static long began;static boolean listening;
 static final List<String> chat=new ArrayList<>(),shown=new ArrayList<>();
 static CompletableFuture<Suggestions> pending,everything;

 // ---------- server half ----------
 /** One adaptation of the player's own that sorts last, so a capped suggestion list must keep it on purpose. */
 static final String OWN="mob:zzz_gone:own";
 static int catalogue;
 static void run(ServerPlayer p)throws Exception{
  reset(p);state(p).getClass().getMethod("hit",String.class,boolean.class).invoke(state(p),OWN,false);Adapt429Qa.sync(p);
  catalogue=((List<?>)call(BASE+"AdaptationCatalogue","entries",new Class<?>[]{ServerPlayer.class},p)).size();
 }

 // ---------- client half ----------
 /** Chat lines (not the action bar) the client received: every translation key in them, and the text as shown. */
 static void listen(){
  if(listening)return;listening=true;
  MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,ClientChatReceivedEvent.System.class,e->{if(!e.isOverlay()){keys(e.getMessage());shown.add(e.getMessage().getString());}});
 }
 static void keys(Component c){
  if(c.getContents() instanceof TranslatableContents text){chat.add(text.getKey());for(Object a:text.getArgs())if(a instanceof Component inner)keys(inner);}
  for(var sibling:c.getSiblings())keys(sibling);
 }
 static void ok(String what){LogUtils.getLogger().info("CMD431 OK {}",what);}
 static void next(){step++;t=0;}
 static void waiting(int limit,String what){if(t>limit)throw new AssertionError("cmd431: "+what+" (chat: "+chat+")");}
 static void send(String command){chat.clear();shown.clear();Minecraft.getInstance().player.connection.sendCommand(command);next();}
 static void said(String key,String what){check(chat.contains(key),what+": "+chat);}
 /** The wheel as the client has it from the server's last sync. */
 static Object cstate()throws Exception{return call(CLIENT+"ClientWheelState","get",new Class<?>[]{int.class},Minecraft.getInstance().player.getId());}
 static Object centry(String key)throws Exception{
  Object s=cstate();if(s==null)return null;
  for(Object e:(List<?>)s.getClass().getMethod("entries").invoke(s))if(key.equals(e.getClass().getMethod("key").invoke(e)))return e;
  return null;
 }
 static int cstage(String key)throws Exception{Object e=centry(key);return e==null?-1:(int)e.getClass().getMethod("stage").invoke(e);}
 static boolean cenabled(String key)throws Exception{Object e=centry(key);return e!=null&&(boolean)e.getClass().getMethod("enabled").invoke(e);}
 static boolean senabled(ServerPlayer p,String key)throws Exception{return (boolean)state(p).getClass().getMethod("categoryEnabled",String.class).invoke(state(p),key);}
 static void untranslated(){for(var line:shown)check(!line.contains("command.adaptationwheel."),"The client showed a raw key: "+line);}
 /** Suggestions the client asks of the server, as the chat box does while typing. */
 static CompletableFuture<Suggestions> suggest(String input){
  var c=Minecraft.getInstance().player.connection;var d=c.getCommands();
  return d.getCompletionSuggestions(d.parse(input,c.getSuggestionsProvider()));
 }

 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();listen();
  if(began==0)began=System.nanoTime();
  if(System.nanoTime()-began>300_000_000_000L)throw new AssertionError("cmd431 timed out at step "+step);
  t++;
  switch(step){
   case 0->{ // the command tree an operator's client holds, the translations, and a suggestion asked of the server
    if(cstate()==null||t<20){waiting(400,"the wheel never synced to the client");return false;}
    var root=mc.player.connection.getCommands().getRoot().getChild("adaptation");
    check(root!=null&&root.getChild("info")!=null&&root.getChild("info").getChild("adaptation")!=null,"The client did not get /adaptation info <adaptation>");
    check(root.getChild("learn")!=null&&root.getChild("forget")!=null,"The world owner with cheats on did not get the operator commands");
    for(var key:KEYS)check(I18n.exists("command.adaptationwheel."+key),"The client has no text for command.adaptationwheel."+key);
    check(I18n.exists("meta.adaptationwheel.changelog.4.31.0"),"The client has no 4.31.0 changelog line");
    // One request at a time: the client keeps a single pending server request and cancels the older one.
    pending=suggest("adaptation info zom");
    next();
   }
   case 1->{
    if(!pending.isDone()){waiting(200,"the server never answered the suggestion request");return false;}
    var zombie=pending.get().getList().stream().filter(s->s.getText().equals(ZOMBIE)).findFirst().orElse(null);
    check(zombie!=null,"The server suggested no "+ZOMBIE+" for \"zom\": "+pending.get().getList().stream().map(s->s.getText()).toList());
    String tip=zombie.getTooltip()==null?"":zombie.getTooltip().getString();
    check(!tip.isBlank()&&!tip.contains("adaptationwheel."),"The suggestion's name did not arrive translated: \""+tip+"\"");
    ok("suggestions: the client asked the server for \"zom\" and got "+ZOMBIE+" named \""+tip+"\"");
    everything=suggest("adaptation info ");
    next();
   }
   case 2->{
    if(!everything.isDone()){waiting(200,"the server never answered the suggestion request for an empty word");return false;}
    var all=everything.get().getList();
    check(all.size()<=200&&all.stream().anyMatch(s->s.getText().equals(OWN)),"An empty word got "+all.size()+" suggestions without the player's own "+OWN);
    ok("suggestions for an empty word: "+all.size()+" of "+catalogue+" catalogue entries, the player's own adaptation kept");
    send("adaptation learn @s 3 zombie");
   }
   case 3->{
    if(cstage(ZOMBIE)!=3||!chat.contains("command.adaptationwheel.learn.one")){waiting(200,"learn never reached the client wheel or chat");return false;}
    int[] stage={0};Adapt429Qa.server(p->stage[0]=stage(p,ZOMBIE));check(stage[0]==3,"The server has stage "+stage[0]+" after learn 3");
    said("message.adaptationwheel.stage","learn did not announce the stage");
    float age=(float)cstate().getClass().getMethod("completionAge",float.class).invoke(cstate(),0F);
    check(age<400,"The client wheel did not turn for the given stage");
    untranslated();
    ok("learn: a typed /adaptation learn @s 3 zombie sets stage III on the server, the client wheel turns and shows it");
    send("adaptation disable zombie");
   }
   case 4->{
    if(cenabled(ZOMBIE)){waiting(200,"disable never reached the client wheel");return false;}
    boolean[] on={true};Adapt429Qa.server(p->on[0]=senabled(p,ZOMBIE));check(!on[0],"The server still has the adaptation on");
    said("command.adaptationwheel.switched.off","disable did not report");untranslated();
    ok("disable: switched off on the server and in the client's J menu data");
    send("adaptation list");
   }
   case 5->{
    if(!chat.contains("command.adaptationwheel.list.header")){waiting(100,"list never answered");return false;}
    said("command.adaptationwheel.list.entry","list has no line");said("command.adaptationwheel.list.off","list does not mark the switched-off adaptation");untranslated();
    ok("list: "+String.join(" | ",shown));
    send("adaptation info dry_riptide");
   }
   case 6->{
    if(!chat.contains("command.adaptationwheel.info.header")){waiting(100,"info never answered");return false;}
    said("meta.adaptationwheel.dry_riptide.hint","info did not give the adaptation's hint");untranslated();
    ok("info: "+String.join(" | ",shown));
    send("adaptation enable zombie");
   }
   case 7->{
    if(!cenabled(ZOMBIE)){waiting(200,"enable never reached the client wheel");return false;}
    said("command.adaptationwheel.switched.on","enable did not report");
    ok("enable: back on in the client's data");
    send("adaptation learn @s 2 no_such_adaptation_431");
   }
   case 8->{
    if(!chat.contains("command.adaptationwheel.unknown")){waiting(100,"an unknown name did not fail");return false;}
    int[] stage={0};Adapt429Qa.server(p->stage[0]=stage(p,ZOMBIE));check(stage[0]==3&&cstage(ZOMBIE)==3,"A failed command changed a stage");
    untranslated();
    ok("unknown name: "+String.join(" | ",shown));
    send("adaptation forget @s zombie");
   }
   case 9->{
    if(centry(ZOMBIE)!=null){waiting(200,"forget never reached the client wheel");return false;}
    int[] stage={-1};Adapt429Qa.server(p->stage[0]=stage(p,ZOMBIE));check(stage[0]==0,"The server still has the adaptation");
    said("command.adaptationwheel.forget.one","forget did not report");untranslated();
    ok("forget: gone on the server and from the client wheel");
    LogUtils.getLogger().info("MULTIMOD_QA CMD431 PASS: /adaptation typed in the real client, with server suggestions, sync and chat");
    return true;
   }
   default->throw new AssertionError("cmd431: unknown step "+step);
  }
  return false;
 }
}
