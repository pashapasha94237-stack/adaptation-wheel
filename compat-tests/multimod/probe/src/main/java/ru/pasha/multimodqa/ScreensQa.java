package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static ru.pasha.multimodqa.MultiModQa.*;

/**
 * Screenshots of the wheel's J menu, its HUD and Adapt Boost's screens with a realistic set of adaptations, and the menu
 * driven by keyboard. The group's "window" and "options" pick the GUI size (the hidden QA window cannot be resized once
 * open) and "gui" names the size it must give: screens (480×320), screens-compact (Pasha's 925×530 launcher window,
 * 462×265) and screens-wide (his maximised 3440×1417 window at GUI scale 3, 1146×472, list and details side by side).
 */
final class ScreensQa {
 interface Action{void run(Minecraft mc)throws Exception;}
 static final List<Map.Entry<Integer,Action>> PLAN=new ArrayList<>();
 static int step,ticks,total,shots;
 static String tag="";
 static void run(ServerPlayer p)throws Exception{
  reset(p);
  learn(p,"damage:minecraft:on_fire",2);learn(p,"mob:minecraft:zombie",4);learn(p,"effect:minecraft:poison",3);
  learn(p,"environment:darkness",3);learn(p,"environment:notification_noise",1);
  var s=state(p);s.getClass().getMethod("hit",String.class,boolean.class).invoke(s,"mob:minecraft:wither",true);
  for(int n=0;n<200000&&stage(p,"mob:minecraft:wither")<5;n++)s.getClass().getMethod("tick").invoke(s);
  // A real sting, so the bee's details have the analyzer's verdict.
  var bee=EntityType.BEE.create(p.serverLevel());bee.moveTo(p.getX()+2,p.getY(),p.getZ());
  AnalyzerQa.hurt(p,p.serverLevel().damageSources().sting(bee));
  // One adaptation still in progress, so lists and the HUD show a running timer.
  s.getClass().getMethod("hit",String.class,boolean.class).invoke(s,"damage:minecraft:fall",false);
  for(int n=0;n<200;n++)s.getClass().getMethod("tick").invoke(s);
  call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
 }
 static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"ui-"+name+".png",mc.getMainRenderTarget(),m->{});shots++;LogUtils.getLogger().info("MULTIMOD_QA SCREENS SHOT {}",name);}
 static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void press(String name)throws Exception{((Button)field(Minecraft.getInstance().screen,name)).onPress();}
 static Object invoke(Object owner,String name,Class<?>[] types,Object...args)throws Exception{var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
 static void reopen(){var mc=Minecraft.getInstance();Screen s=mc.screen;s.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}
 static boolean menu(Minecraft mc){return mc.screen!=null&&mc.screen.getClass().getName().equals("ru.pasha.adaptationwheel.client.AdaptationScreen");}
 static void then(int wait,Action action){PLAN.add(Map.entry(wait,action));}
 static void build(){
  then(40,mc->{PerfQa.boost(true);setBoostHud(true);
   tag=mc.getWindow().getGuiScaledWidth()+"x"+mc.getWindow().getGuiScaledHeight();
   if(group.has("gui"))check(tag.equals(group.get("gui").getAsString()),"GUI is "+tag+", expected "+group.get("gui").getAsString());
   Class.forName("ru.pasha.adaptationwheel.client.AdaptationScreen").getMethod("open").invoke(null);});
  then(25,mc->{
   shot("j-mine-"+tag);
   Object layout=field(mc.screen,"layout");boolean split=(boolean)field(layout,"split"),compact=(boolean)field(layout,"compact");
   int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
   check(split==(w>=720),"Split layout at "+tag+": "+split);check(compact==(h<300),"Compact layout at "+tag+": "+compact);
   keyboard(mc.screen);press("all");});
  then(5,mc->{shot("j-catalogue-"+tag);press("categoryButton");});
  then(5,mc->{shot("j-categories-"+tag);press("categoryButton");});
  then(3,mc->press("mine"));
  // A running damage stage, a boss with the existence stage, a mob analysed from a real hit, a meta mechanic.
  for(String key:new String[]{"damage:minecraft:fall","mob:minecraft:wither","mob:minecraft:bee","environment:notification_noise"}){
   then(3,mc->{int row=(int)invoke(mc.screen,"rowOf",new Class<?>[]{String.class},key);check(row>=0,"Row missing: "+key);
    invoke(mc.screen,"openRow",new Class<?>[]{int.class},row);
    boolean split=mc.getWindow().getGuiScaledWidth()>=720;
    check(split==menu(mc),(split?"Details left the split menu":"Narrow menu did not open the details screen")+" for "+key+" at "+tag);
    if(split)check(key.equals(field(mc.screen,"selectedKey")),"Split menu did not select "+key);});
   then(8,mc->{shot("j-detail-"+key.substring(key.lastIndexOf(':')+1)+"-"+tag);if(!menu(mc))mc.screen.onClose();});
  }
  then(3,mc->mc.setScreen(null));
  then(30,mc->shot("hud-"+tag));
  // Adapt Boost's screens once, at the plain QA size.
  if(!group.has("window"))for(int tab=0;tab<3;tab++){int t=tab;
   then(10,mc->{var screen=(Screen)Class.forName("ru.pasha.pashaboost.client.OwnVideoScreen").getConstructor(Screen.class,net.minecraft.client.Options.class).newInstance(null,mc.options);mc.setScreen(screen);
    var f=screen.getClass().getDeclaredField("tab");f.setAccessible(true);f.setInt(screen,t);reopen();});
   then(6,mc->shot("boost-video-"+t));
  }
  if(!group.has("window"))for(int tab=0;tab<2;tab++){int t=tab;
   then(1,mc->{var type=Class.forName("ru.pasha.pashaboost.client.BoostScreen");var f=type.getDeclaredField("tab");f.setAccessible(true);f.setInt(null,t);
    mc.setScreen((Screen)type.getConstructor(Screen.class).newInstance((Object)null));});
   then(6,mc->shot(t==0?"boost-overview":"boost-settings"));
  }
  then(1,mc->{mc.setScreen(null);setBoostHud(false);
   LogUtils.getLogger().info("MULTIMOD_QA SCREENS PASS: {} screenshots at GUI {} (J menu layout, keyboard, details with analysis, HUD{})",shots,tag,group.has("window")?"":", Adapt Boost screens");});
 }
 /** Arrow keys select rows, typing goes to the search, Esc clears it and then leaves it. */
 static void keyboard(Screen screen)throws Exception{
  screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0);
  check(row(screen)==0,"Home did not select the first row");
  screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);
  check(row(screen)==1,"Down arrow did not select the second row");
  screen.keyPressed(GLFW.GLFW_KEY_UP,0,0);
  check(row(screen)==0,"Up arrow did not go back");
  EditBox search=(EditBox)field(screen,"search");
  screen.charTyped('з',0);
  check(search.isFocused()&&"з".equals(field(screen,"query")),"Typing did not start a search");
  // Up leaves the search for the list; vanilla arrow navigation would focus the global switch above it instead.
  screen.keyPressed(GLFW.GLFW_KEY_UP,0,0);
  check(!search.isFocused()&&screen.getFocused()==null&&row(screen)>=0,"Up from the search did not return to the list (focus "+screen.getFocused()+")");
  screen.keyPressed(GLFW.GLFW_KEY_RIGHT,0,0);
  check(screen.getFocused()==null,"Right arrow moved the focus to "+screen.getFocused());
  search.setValue("");
  screen.charTyped('з',0);
  screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
  check("".equals(field(screen,"query"))&&search.isFocused(),"Esc did not clear the search first");
  screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
  check(!search.isFocused()&&Minecraft.getInstance().screen==screen,"Second Esc should leave the search, not the menu");
 }
 static int row(Screen screen)throws Exception{return (int)invoke(screen,"rowOf",new Class<?>[]{String.class},field(screen,"selectedKey"));}
 static boolean clientTick()throws Exception{
  if(PLAN.isEmpty())build();
  if(++total>4000)throw new AssertionError("Screens QA timed out at step "+step);
  if(++ticks<PLAN.get(step).getKey())return false;
  PLAN.get(step).getValue().run(Minecraft.getInstance());
  step++;ticks=0;
  return step>=PLAN.size();
 }
 static void setBoostHud(boolean on)throws Exception{
  Object hud=Class.forName("ru.pasha.pashaboost.BoostConfig").getField("HUD").get(null);hud.getClass().getMethod("set",Object.class).invoke(hud,on);
 }
}
