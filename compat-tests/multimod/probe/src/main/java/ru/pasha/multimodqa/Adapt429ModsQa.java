package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import static ru.pasha.multimodqa.MultiModQa.*;

/**
 * 4.29 next to the player's own mods, in the real client: arrows in a Supplementaries quiver (inventory and Curios
 * belt) reach the wheel's arrow count, loose arrows that go before a quiver get no count, counting arrows leaves a
 * never-opened Sophisticated Backpacks backpack without storage, and the anvil upgrade tab of Sophisticated Backpacks
 * shows the wheel's price.
 * A part whose mod is not installed is skipped and says so.
 */
final class Adapt429ModsQa {
 static final String LEFT="meta.adaptationwheel.empty_quiver.left";
 static int step,t,cost;static String drawn="";static Object tab;static SpyFont spy;

 /** Records every text the backpack tab wraps for drawing; the wrapping itself is the real font's. */
 static final class SpyFont extends Font{
  final List<String> seen=new CopyOnWriteArrayList<>();
  SpyFont(Function<ResourceLocation,FontSet> fonts,boolean filter){super(fonts,filter);}
  @Override public List<FormattedCharSequence> split(FormattedText text,int width){seen.add(text.getString());return super.split(text,width);}
 }
 @SuppressWarnings("unchecked") static SpyFont spy(Font real)throws Exception{
  Function<ResourceLocation,FontSet> fonts=null;boolean filter=false;
  for(Field f:Font.class.getDeclaredFields()){
   if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);
   if(f.getType()==Function.class)fonts=(Function<ResourceLocation,FontSet>)f.get(real);else if(f.getType()==boolean.class)filter=f.getBoolean(real);
  }
  check(fonts!=null,"The font has no font set function");return new SpyFont(fonts,filter);
 }
 static Method method(Class<?> c,String name,Class<?>...types)throws NoSuchMethodException{
  for(var k=c;k!=null;k=k.getSuperclass())try{var m=k.getDeclaredMethod(name,types);m.setAccessible(true);return m;}catch(NoSuchMethodException next){}
  throw new NoSuchMethodException(c.getName()+"."+name);
 }
 static Field field(Class<?> c,String name)throws NoSuchFieldException{
  for(var k=c;k!=null;k=k.getSuperclass())try{var f=k.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException next){}
  throw new NoSuchFieldException(c.getName()+"."+name);
 }
 static Object invoke(Object owner,String name)throws Exception{return method(owner.getClass(),name).invoke(owner);}
 static ItemStack item(String mod,String name,int count){return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(mod,name)),count);}
 static void next(){step++;t=0;}
 static void waiting(int limit,String what){if(t>limit)throw new AssertionError("adapt429 mods: "+what);}
 static String overlay()throws Exception{var c=(Component)ObfuscationReflectionHelper.findField(Gui.class,"f_92990_").get(Minecraft.getInstance().gui);return c==null?"":c.getString();}

 // ---------- Supplementaries quiver ----------
 static ItemStack quiver(int arrows){
  var q=item("supplementaries","quiver",1);
  var rest=q.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow().insertItem(0,new ItemStack(Items.ARROW,arrows),false);
  check(rest.isEmpty(),"The quiver did not take "+arrows+" arrows");return q;
 }
 static int inQuiver(ItemStack q){
  var h=q.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);if(h==null)return -1;
  int n=0;for(int i=0;i<h.getSlots();i++)if(h.getStackInSlot(i).is(Items.ARROW))n+=h.getStackInSlot(i).getCount();return n;
 }
 static IItemHandlerModifiable belt(ServerPlayer p)throws Exception{
  var inventory=((LazyOptional<?>)Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory",LivingEntity.class).invoke(null,p)).resolve().orElseThrow();
  var handler=((Optional<?>)Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getStacksHandler",String.class).invoke(inventory,"belt")).orElseThrow();
  return (IItemHandlerModifiable)Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler").getMethod("getStacks").invoke(handler);
 }
 static void bowOnly(ServerPlayer p){
  p.getInventory().clearOrCountMatchingItems(s->s.is(Items.ARROW),-1,p.inventoryMenu.getCraftSlots());
  p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
 }
 /** A real draw and release of the bow in the main hand; true once released. */
 static boolean draw(Minecraft mc)throws Exception{
  if(t==5){check(mc.player.getMainHandItem().is(Items.BOW),"The bow did not reach the client hand");mc.gui.setOverlayMessage(Component.empty(),false);mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
  if(t>5&&t<25)mc.options.keyUse.setDown(true);
  if(t==12){check(mc.player.isUsingItem(),"The bow was not drawn with arrows in the quiver");drawn=overlay();}
  if(t==25){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);return true;}
  return false;
 }
 /** After the release: exactly one real arrow left the bow. */
 static void fired(ServerPlayer p,String where){
  var arrows=p.serverLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(80),a->a.getOwner()==p);
  check(arrows.size()==1,where+": "+arrows.size()+" arrows left the bow");
  check(arrows.get(0).pickup==AbstractArrow.Pickup.ALLOWED,where+": the arrow shot is not a real one");
  arrows.forEach(Entity::discard);
 }
 /** After the release: one real arrow left the quiver and the wheel counted what was inside before the draw. */
 static void shot(ServerPlayer p,ItemStack q,int before,String where){
  fired(p,where);
  check(inQuiver(q)==before-1,where+": the quiver holds "+inQuiver(q)+" arrows after the shot instead of "+(before-1));
 }

 // ---------- Sophisticated Backpacks anvil ----------
 static Object anvil(Object menu)throws Exception{
  for(var c:((Map<?,?>)invoke(menu,"getUpgradeContainers")).values())if(c.getClass().getSimpleName().equals("AnvilUpgradeContainer"))return c;
  throw new AssertionError("The backpack menu has no anvil upgrade container");
 }
 @SuppressWarnings("unchecked") static List<Slot> slots(Object container)throws Exception{return (List<Slot>)invoke(container,"getSlots");}

 /** One client tick; true when every installed mod has been checked. */
 static boolean tick()throws Exception{
  var mc=Minecraft.getInstance();t++;
  switch(step){
   case 0->{
    if(!ModList.get().isLoaded("supplementaries")){LogUtils.getLogger().info("ADAPT429 SKIP supplementaries is not installed");step=10;t=0;return false;}
    boolean curioOnly=(boolean)((Supplier<?>)Class.forName("net.mehvahdjukaar.supplementaries.configs.CommonConfigs$Tools").getField("QUIVER_CURIO_ONLY").get(null)).get();
    if(curioOnly){LogUtils.getLogger().info("ADAPT429 SKIP the quiver works only from Curios here");step=7;t=0;return false;}
    Adapt429Qa.server(p->{bowOnly(p);p.getInventory().setItem(9,quiver(5));});next();
   }
   case 1->{if(draw(mc))next();}
   case 2->{
    if(t<8)return false;
    Adapt429Qa.server(p->shot(p,p.getInventory().getItem(9),5,"Inventory quiver"));
    String want=Component.translatable(LEFT,5).getString();
    check(drawn.equals(want),"With 5 arrows in a quiver in the inventory the wheel said '"+drawn+"' instead of '"+want+"'");
    Adapt429Qa.ok("empty_quiver + Supplementaries: a real draw with 5 arrows in an inventory quiver says '"+drawn+"'; the quiver's real arrow is shot");
    // Loose arrows before the quiver (4 arrows now) go first, and the quiver takes over when they run out.
    Adapt429Qa.server(p->p.getInventory().setItem(1,new ItemStack(Items.ARROW,3)));next();
   }
   case 3->{if(draw(mc))next();}
   case 4->{
    if(t<8)return false;
    Adapt429Qa.server(p->{
     fired(p,"Loose arrows before a quiver");
     int loose=p.getInventory().getItem(1).getCount(),inside=inQuiver(p.getInventory().getItem(9));
     check(loose==2&&inside==4,"Loose arrows before a quiver: "+loose+" loose and "+inside+" in the quiver after the shot instead of 2 and 4");
    });
    check(drawn.isEmpty(),"With 3 loose arrows before a quiver of 4 the wheel said '"+drawn+"' instead of nothing");
    Adapt429Qa.ok("empty_quiver + Supplementaries: 3 loose arrows before a quiver of 4 - a loose arrow is shot and the wheel names no number");
    // A quiver before the loose arrows feeds first: the count is both.
    Adapt429Qa.server(p->{bowOnly(p);p.getInventory().setItem(9,ItemStack.EMPTY);p.getInventory().setItem(1,quiver(4));p.getInventory().setItem(20,new ItemStack(Items.ARROW,2));});next();
   }
   case 5->{if(draw(mc))next();}
   case 6->{
    if(t<8)return false;
    Adapt429Qa.server(p->{
     shot(p,p.getInventory().getItem(1),4,"A quiver before loose arrows");
     check(p.getInventory().getItem(20).getCount()==2,"A quiver before loose arrows: a loose arrow went first");
    });
    String want=Component.translatable(LEFT,6).getString();
    check(drawn.equals(want),"With a quiver of 4 before 2 loose arrows the wheel said '"+drawn+"' instead of '"+want+"'");
    Adapt429Qa.ok("empty_quiver + Supplementaries: a quiver of 4 before 2 loose arrows says '"+drawn+"'; the quiver's real arrow is shot");
    Adapt429Qa.server(p->{p.getInventory().setItem(1,ItemStack.EMPTY);p.getInventory().setItem(20,ItemStack.EMPTY);});next();
   }
   case 7->{
    if(!ModList.get().isLoaded("curios")){LogUtils.getLogger().info("ADAPT429 SKIP curios is not installed");step=10;t=0;return false;}
    Adapt429Qa.server(p->{bowOnly(p);belt(p).setStackInSlot(0,quiver(6));});next();
   }
   case 8->{if(draw(mc))next();}
   case 9->{
    if(t<8)return false;
    Adapt429Qa.server(p->{shot(p,belt(p).getStackInSlot(0),6,"Curios belt quiver");belt(p).setStackInSlot(0,ItemStack.EMPTY);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);});
    String want=Component.translatable(LEFT,6).getString();
    check(drawn.equals(want),"With 6 arrows in a quiver on the Curios belt the wheel said '"+drawn+"' instead of '"+want+"'");
    Adapt429Qa.ok("empty_quiver + Supplementaries + Curios: a real draw with 6 arrows in a belt quiver says '"+drawn+"'; the quiver's real arrow is shot");
    next();
   }
   case 10->{
    if(!ModList.get().isLoaded("sophisticatedbackpacks")){LogUtils.getLogger().info("ADAPT429 SKIP sophisticatedbackpacks is not installed");return true;}
    // A never-opened backpack next to 3 arrows: asking it for its inventory would give it storage in the world's data.
    Adapt429Qa.server(p->{bowOnly(p);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.getInventory().setItem(1,new ItemStack(Items.ARROW,3));p.getInventory().setItem(9,item("sophisticatedbackpacks","backpack",1));});
    next();
   }
   case 11->{if(draw(mc))next();}
   case 12->{
    if(t<8)return false;
    Adapt429Qa.server(p->{
     fired(p,"Arrows next to a new backpack");
     var backpack=p.getInventory().getItem(9);
     check(backpack.getTag()==null||!backpack.getTag().contains("contentsUuid"),"Drawing the bow gave a never-opened backpack a contents id: "+backpack.getTag());
     // The control: the same question the wheel no longer asks does give it one.
     backpack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
     check(backpack.getTag()!=null&&backpack.getTag().contains("contentsUuid"),"Asking a new backpack for its inventory no longer gives it a contents id: "+backpack.getTag());
     p.getInventory().setItem(1,ItemStack.EMPTY);p.getInventory().setItem(9,ItemStack.EMPTY);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    });
    String want=Component.translatable(LEFT,3).getString();
    check(drawn.equals(want),"With 3 arrows next to a new backpack the wheel said '"+drawn+"' instead of '"+want+"'");
    Adapt429Qa.ok("empty_quiver + Sophisticated Backpacks: 3 arrows next to a never-opened backpack say '"+drawn+"' and the backpack gets no storage");
    next();
   }
   case 13->{
    Adapt429Qa.server(p->{
     var backpack=item("sophisticatedbackpacks","backpack",1);
     @SuppressWarnings("unchecked") var capability=(Capability<Object>)Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper").getMethod("getCapabilityInstance").invoke(null);
     Object wrapper=backpack.getCapability(capability).resolve().orElseThrow();
     // A crafted backpack gets its storage and slot counts this way; one made in code starts with neither
     // (without a contents id its upgrade handler is an empty no-op).
     method(wrapper.getClass(),"setContentsUuid",java.util.UUID.class).invoke(wrapper,java.util.UUID.randomUUID());
     method(wrapper.getClass(),"setSlotNumbers",int.class,int.class).invoke(wrapper,invoke(backpack.getItem(),"getNumberOfSlots"),invoke(backpack.getItem(),"getNumberOfUpgradeSlots"));
     var upgrades=(IItemHandlerModifiable)Class.forName("net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper").getMethod("getUpgradeHandler").invoke(wrapper);
     upgrades.setStackInSlot(0,item("sophisticatedbackpacks","anvil_upgrade",1));
     p.setItemInHand(InteractionHand.MAIN_HAND,backpack);
    });
    next();
   }
   case 14->{ // a real right click with the backpack opens its screen
    if(t==5)mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
    if(mc.screen==null||!mc.screen.getClass().getSimpleName().equals("BackpackScreen")){waiting(200,"the backpack screen never opened");return false;}
    next();
   }
   case 15->{
    if(t<5)return false;
    var control=invoke(mc.screen,"getUpgradeSettingsControl");
    for(var child:(List<?>)field(control.getClass(),"children").get(control))if(child.getClass().getSimpleName().equals("AnvilUpgradeTab"))tab=child;
    check(tab!=null,"The backpack screen has no anvil upgrade tab");
    spy=spy(mc.font);field(tab.getClass(),"font").set(tab,spy);
    method(tab.getClass(),"onTabIconClicked",int.class).invoke(tab,0); // what a left click on the tab icon does
    next();
   }
   case 16->{
    boolean[] open={false};
    // The server keeps only which tab is open (the client's click sends it); the container's own isOpen is screen state.
    Adapt429Qa.server(p->{
     Object id=null,c=anvil(p.containerMenu);
     for(var e:((Map<?,?>)invoke(p.containerMenu,"getUpgradeContainers")).entrySet())if(e.getValue()==c)id=e.getKey();
     if(!((Optional<?>)invoke(invoke(p.containerMenu,"getStorageWrapper"),"getOpenTabId")).equals(Optional.ofNullable(id)))return;
     open[0]=true;slots(c).get(0).set(Adapt429Qa.sword());slots(c).get(1).set(Adapt429Qa.sword());
    });
    if(!open[0]){waiting(200,"the anvil tab never opened on the server");return false;}
    next();
   }
   case 17->{
    var c=anvil(mc.player.containerMenu);
    if(cost<40){cost=(int)invoke(c,"getCost");if(cost<40){waiting(200,"the backpack anvil never priced the repair on the client (price "+cost+")");return false;}spy.seen.clear();t=0;return false;}
    if(t<10)return false;
    boolean[] result={false};int[] serverCost={0};
    Adapt429Qa.server(p->{var s=anvil(p.containerMenu);result[0]=slots(s).get(2).hasItem();serverCost[0]=(int)invoke(s,"getCost");});
    check(serverCost[0]==cost,"The server prices the repair at "+serverCost[0]+", the client at "+cost);
    check(result[0],"Stage IV: the backpack anvil refused a "+cost+"-level repair on the server");
    String price=Component.translatable("container.repair.cost",cost).getString(),refusal=Component.translatable("container.repair.expensive").getString();
    check(spy.seen.contains(price)&&!spy.seen.contains(refusal),"Stage IV: the backpack anvil tab drew "+spy.seen+" instead of '"+price+"'");
    Adapt429Qa.shot("adapt429-backpack-anvil.png");
    Adapt429Qa.ok("too_expensive + Sophisticated Backpacks: the anvil upgrade keeps a "+cost+"-level repair at IV and its tab draws '"+price+"'");
    Adapt429Qa.server(p->Adapt429Qa.enable(p,"ANVIL",false));next();
   }
   case 18->{
    if(Adapt429Qa.cactive("ANVIL")){waiting(200,"ANVIL was not switched off on the client");return false;}
    if(t==1)spy.seen.clear();
    if(t<12)return false;
    String refusal=Component.translatable("container.repair.expensive").getString();
    check(spy.seen.contains(refusal),"Switched off, the backpack anvil tab drew "+spy.seen+" instead of '"+refusal+"'");
    Adapt429Qa.shot("adapt429-backpack-anvil-off.png");
    Adapt429Qa.server(p->{Adapt429Qa.enable(p,"ANVIL",true);p.closeContainer();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);});
    Adapt429Qa.ok("too_expensive + Sophisticated Backpacks: switched off, the tab draws vanilla's '"+refusal+"' for the same "+cost+"-level price");
    return true;
   }
   default->throw new AssertionError("adapt429 mods: unknown step "+step);
  }
  return false;
 }
}
