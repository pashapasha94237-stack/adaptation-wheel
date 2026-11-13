package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static ru.pasha.multimodqa.MultiModQa.*;

/**
 * Unlimited stacks after the full inventory pressure adaptation, in the production (SRG) game: the server inventory,
 * slots, shift-click, item NBT and packets, real client clicks through the network, the slot label and the tooltip,
 * and the one-time return of the old nine-slot pocket.
 */
final class StacksQa {
 static final int LIMIT=1_000_000_000;
 static int step,ticks;static ServerPlayer server;
 static void run(ServerPlayer p)throws Exception{
  server=p;reset(p);
  // Eternal Tales forces a normal world where the spawn is underground: the player bled and suffocated mid-test and
  // dropped everything. Stand on a platform in open air and take no damage; this test is about inventories only.
  var level=p.serverLevel();
  if(!(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource)){
   var base=new net.minecraft.core.BlockPos(p.getBlockX(),170,p.getBlockZ());
   for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){level.setBlock(base.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
    for(int y=0;y<4;y++)level.setBlock(base.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}
   p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);
  }
  p.getAbilities().invulnerable=true;p.onUpdateAbilities();
  ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD);p.getInventory().clearContent();p.setItemSlot(EquipmentSlot.HEAD,wheel);
  check(!wheel.isEmpty(),"Fixture wheel disappeared");
  var inv=p.getInventory();
  inv.add(new ItemStack(Items.COBBLESTONE,100));
  check(inv.getItem(0).getCount()==64,"Stacks were lifted before the adaptation");inv.clearContent();p.setItemSlot(EquipmentSlot.HEAD,wheel);
  learn(p,"environment:inventory_pressure",4);
  call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
  ItemStack stone=new ItemStack(Items.COBBLESTONE,12_345);inv.add(stone);inv.add(new ItemStack(Items.COBBLESTONE,64));
  check(stone.isEmpty()&&inv.getItem(0).getCount()==12_409&&inv.getItem(1).isEmpty(),"Inventory.add did not fill one slot past 64: "+inv.getItem(0).getCount());
  inv.add(new ItemStack(Items.ENDER_PEARL,300));check(inv.getItem(1).getCount()==300,"Ender pearls (16-stack) were not lifted");
  inv.setItem(40,new ItemStack(Items.TORCH,1500));
  check(p.inventoryMenu.getSlot(45).getMaxStackSize(new ItemStack(Items.TORCH))==LIMIT,"The offhand slot was not lifted");
  check(p.inventoryMenu.getSlot(5).getMaxStackSize(new ItemStack(Items.CARVED_PUMPKIN))==1,"The helmet slot was lifted");
  inv.setItem(8,new ItemStack(Items.DIRT,64));inv.setItem(20,new ItemStack(Items.DIRT,64));p.inventoryMenu.quickMoveStack(p,44);
  check(inv.getItem(8).isEmpty()&&inv.getItem(20).getCount()==128,"Shift-click did not merge past 64");
  CompoundTag saved=inv.getItem(0).save(new CompoundTag());
  check(saved.get("Count") instanceof IntTag&&ItemStack.of(saved).getCount()==12_409,"Item NBT cut a big stack");
  FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());buf.writeItem(inv.getItem(0));
  check(buf.readItem().getCount()==12_409,"An item packet cut a big stack");
  // A second wheel still carrying the old pocket, as left by 4.19: the server's own player tick must return it.
  ItemStack old=new ItemStack(wheel.getItem());NonNullList<ItemStack> pocket=NonNullList.withSize(9,ItemStack.EMPTY);pocket.set(0,new ItemStack(Items.DIAMOND,13));
  old.getOrCreateTag().put("AdaptationPocket",ContainerHelper.saveAllItems(new CompoundTag(),pocket));inv.setItem(30,old);
  LogUtils.getLogger().info("MULTIMOD_QA STACKS SERVER: 12409 cobblestone in one slot, 300 ender pearls, 1500 torches in the offhand, shift-click 128, NBT and packet round trip");
 }
 static int target;
 static int stoneSlot(Player p,int from,int to){for(int i=from;i<to;i++)if(p.getInventory().getItem(i).is(Items.COBBLESTONE)&&p.getInventory().getItem(i).getCount()==12_409)return i;return -1;}
 static String dump(Player p){
  StringBuilder s=new StringBuilder("[");for(int i=0;i<p.getInventory().getContainerSize();i++){var it=p.getInventory().getItem(i);if(!it.isEmpty())s.append(i).append(':').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it.getItem()).getPath()).append('x').append(it.getCount()).append(' ');}
  return s.append("carried ").append(p.containerMenu.getCarried().getCount()).append(" menu ").append(p.containerMenu.getClass().getSimpleName()).append(']').toString();
 }
 static int count(Player p,Item item){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;}
 static <T> T onServer(java.util.function.Supplier<T> task)throws Exception{return Minecraft.getInstance().getSingleplayerServer().submit(task).get();}
 static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"stacks-"+name+".png",mc.getMainRenderTarget(),m->{});}
 static void click(int slot,int button,ClickType type){var mc=Minecraft.getInstance();mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,slot,button,type,mc.player);}
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();ticks++;if(ticks>600)throw new AssertionError("Stacks QA timed out at step "+step);
  switch(step){
   case 0->{
    if(ticks<60)return false; // sync, and at least one 20-tick server pass for the pocket
    var inv=mc.player.getInventory();
    check(inv.getItem(0).getCount()==12_409,"The client received a cut stack: "+inv.getItem(0).getCount());
    check(inv.getItem(1).getCount()==300&&inv.getItem(40).getCount()==1500,"The client received cut pearls or torches");
    check((boolean)call("ru.pasha.adaptationwheel.StackLimits","unlimited",new Class<?>[]{Player.class},mc.player),"The client does not know its stacks are lifted");
    check(count(mc.player,Items.DIAMOND)==13&&onServer(()->count(server,Items.DIAMOND))==13,"The old pocket was not returned by the server tick");
    check(onServer(()->!server.getInventory().getItem(30).hasTag()||!server.getInventory().getItem(30).getTag().contains("AdaptationPocket")),"The old pocket stayed on the wheel");
    mc.setScreen(new InventoryScreen(mc.player));step=1;ticks=0;
   }
   case 1->{if(ticks<10)return false;shot("inventory");
    // Real clicks: the client predicts, sends the click packet with its big stacks, the server applies and answers.
    // Mods hand out guide books at login, so stacks are found wherever they land rather than at fixed slots.
    click(36,0,ClickType.QUICK_MOVE);step=2;ticks=0;}
   case 2->{
    int main=onServer(()->server.getInventory().getItem(0).isEmpty()?stoneSlot(server,9,36):-1);
    if(main<0||count(mc.player,Items.COBBLESTONE)!=12_409||!mc.player.getInventory().getItem(main).is(Items.COBBLESTONE)){
     if(ticks<100)return false;throw new AssertionError("Shift-click of 12409 did not move the whole stack. Server: "+onServer(()->dump(server))+" Client: "+dump(mc.player));}
    target=main;click(main,0,ClickType.PICKUP);step=3;ticks=0;}
   case 3->{
    if(mc.player.inventoryMenu.getCarried().getCount()!=12_409||onServer(()->server.containerMenu.getCarried().getCount())!=12_409){
     if(ticks<100)return false;throw new AssertionError("The cursor did not carry the whole stack. Server: "+onServer(()->dump(server))+" Client: "+dump(mc.player));}
    target=-1;for(int i=0;i<9&&target<0;i++)if(mc.player.getInventory().getItem(i).isEmpty())target=i;
    check(target>=0,"No empty hotbar slot to place into: "+dump(mc.player));
    click(36+target,0,ClickType.PICKUP);step=4;ticks=0;}
   case 4->{
    int slot=target;
    if(!onServer(()->server.getInventory().getItem(slot).getCount()==12_409&&server.containerMenu.getCarried().isEmpty())||mc.player.getInventory().getItem(slot).getCount()!=12_409){
     if(ticks<100)return false;throw new AssertionError("Placing the carried stack lost items. Server: "+onServer(()->dump(server))+" Client: "+dump(mc.player));}
    check(onServer(()->count(server,Items.COBBLESTONE))==12_409,"Cobblestone total changed on the server");
    var lines=Screen.getTooltipFromItem(mc,mc.player.getInventory().getItem(slot));boolean exact=false;
    for(var line:lines)if(line.getString().contains("12 409"))exact=true;
    check(exact,"The tooltip does not show the exact count: "+lines);
    shot("inventory-after");mc.setScreen(null);
    LogUtils.getLogger().info("MULTIMOD_QA STACKS PASS: server limits, NBT, packets, client sync, real shift-click/pick-up/place of 12409 items, tooltip, old pocket returned");
    return true;}
   default->{}
  }
  return false;
 }
}
