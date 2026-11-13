package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.StackLimits;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StackLimitGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void fullAdaptationLiftsTheStackLimit(GameTestHelper h) {
        var p=player(h);learn(p,4);var inv=p.getInventory();
        ItemStack stone=new ItemStack(Items.COBBLESTONE,5000);inv.add(stone);
        check(h,stone.isEmpty()&&inv.getItem(0).getCount()==5000&&inv.getItem(1).isEmpty(),"5000 cobblestone did not fit one slot: "+inv.getItem(0).getCount());
        for(int i=0;i<10;i++)inv.add(new ItemStack(Items.COBBLESTONE,64));
        check(h,inv.getItem(0).getCount()==5640&&inv.getItem(1).isEmpty(),"Picked-up cobblestone did not join the big stack");
        inv.add(new ItemStack(Items.ENDER_PEARL,100));
        check(h,inv.getItem(1).is(Items.ENDER_PEARL)&&inv.getItem(1).getCount()==100,"A 16-stack item was not lifted");
        inv.add(new ItemStack(Items.DIAMOND_SWORD));inv.add(new ItemStack(Items.DIAMOND_SWORD));
        check(h,inv.getItem(2).getCount()==1&&inv.getItem(3).getCount()==1,"Swords were stacked");
        var head=p.inventoryMenu.getSlot(5);
        check(h,head.getMaxStackSize(new ItemStack(Items.CARVED_PUMPKIN))==1,"The helmet slot was lifted");
        check(h,p.inventoryMenu.getSlot(45).getMaxStackSize(new ItemStack(Items.TORCH))==StackCount.LIMIT,"The offhand slot keeps 64");
        // Taking the wheel off restores vanilla limits but never touches stacks already made.
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);inv.add(new ItemStack(Items.COBBLESTONE,10));
        check(h,inv.getItem(0).getCount()==5640&&inv.getItem(4).getCount()==10,"Without the wheel the big stack changed or kept growing");
        var control=player(h);ItemStack many=new ItemStack(Items.COBBLESTONE,5000);control.getInventory().add(many);
        check(h,control.getInventory().getItem(0).getCount()==64&&many.getCount()==5000-36*64,"A player without the adaptation got bigger stacks");
        var half=player(h);learn(half,3);half.getInventory().add(new ItemStack(Items.COBBLESTONE,100));
        check(h,half.getInventory().getItem(0).getCount()==64,"Stage three already lifted the limit");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void shiftClickAndCompactionMergeBeyond64(GameTestHelper h) {
        var p=player(h);learn(p,4);var inv=p.getInventory();
        inv.setItem(0,new ItemStack(Items.COBBLESTONE,64));inv.setItem(9,new ItemStack(Items.COBBLESTONE,64));
        p.inventoryMenu.quickMoveStack(p,36);
        check(h,inv.getItem(0).isEmpty()&&inv.getItem(9).getCount()==128,"Shift-click from the hotbar did not merge past 64: "+inv.getItem(9).getCount());
        var chest=new SimpleContainer(27);chest.setItem(0,new ItemStack(Items.COBBLESTONE,64));
        var menu=ChestMenu.threeRows(1,inv,chest);menu.quickMoveStack(p,0);
        check(h,chest.getItem(0).isEmpty()&&inv.getItem(9).getCount()==192,"Shift-click from a chest did not join the big stack");
        // Back into a chest (menu slot 27 is inventory slot 9): vanilla chest slots, 64 each, nothing lost.
        inv.setItem(9,new ItemStack(Items.COBBLESTONE,500));menu.clicked(27,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,p);
        int inChest=0,biggest=0;for(int i=0;i<27;i++){inChest+=chest.getItem(i).getCount();biggest=Math.max(biggest,chest.getItem(i).getCount());}
        check(h,inChest==500&&biggest==64&&inv.getItem(9).isEmpty(),"A big stack overfilled or lost chest items: "+inChest+" in chest, biggest "+biggest);
        inv.clearContent();p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        for(int i=9;i<12;i++)inv.setItem(i,new ItemStack(Items.DIRT,64));
        int moved=InventoryPressure.compact(p);
        check(h,moved==128&&inv.getItem(9).getCount()==192&&inv.getItem(10).isEmpty()&&inv.getItem(11).isEmpty(),"Compaction stopped at 64 after the full adaptation");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void bigStacksSurviveSavesAndPackets(GameTestHelper h) {
        ItemStack big=new ItemStack(Items.COBBLESTONE,123_456);
        CompoundTag saved=big.save(new CompoundTag());
        check(h,saved.get("Count") instanceof IntTag&&ItemStack.of(saved).getCount()==123_456,"A big stack was cut when saved");
        CompoundTag small=new ItemStack(Items.COBBLESTONE,64).save(new CompoundTag());
        check(h,small.get("Count") instanceof ByteTag&&ItemStack.of(small).getCount()==64,"Ordinary stacks no longer save in vanilla's format");
        NonNullList<ItemStack> chest=NonNullList.withSize(3,ItemStack.EMPTY);chest.set(1,big.copy());
        CompoundTag container=ContainerHelper.saveAllItems(new CompoundTag(),chest);NonNullList<ItemStack> loaded=NonNullList.withSize(3,ItemStack.EMPTY);
        ContainerHelper.loadAllItems(container,loaded);
        check(h,loaded.get(1).getCount()==123_456,"Container save cut a big stack");
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());buf.writeItem(big);
        check(h,buf.readItem().getCount()==123_456,"A big stack was cut in an item packet");
        // A normal stack must still be the vanilla bytes: present flag, item id, one count byte.
        FriendlyByteBuf plain=new FriendlyByteBuf(Unpooled.buffer());plain.writeItem(new ItemStack(Items.COBBLESTONE,64));
        check(h,plain.readBoolean()&&plain.readById(BuiltInRegistries.ITEM)==Items.COBBLESTONE&&plain.readByte()==64,"The packet format of an ordinary stack changed");
        var p=player(h);check(h,StackLimits.creativeLimit(p)==64,"Creative packets lifted without the adaptation");
        learn(p,4);check(h,StackLimits.creativeLimit(p)==StackCount.LIMIT,"Creative packets still refuse big stacks after the adaptation");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void oldPocketItemsComeBack(GameTestHelper h) {
        var p=player(h);ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD);
        wheel.getOrCreateTag().put("AdaptationPocket",pocket(new ItemStack(Items.DIAMOND,13),new ItemStack(Items.COBBLESTONE,64)));
        int back=InventoryPressure.returnPocket(p);
        check(h,back==77&&count(p,Items.DIAMOND)==13&&count(p,Items.COBBLESTONE)==64,"Pocket items were not returned: "+back);
        check(h,(!wheel.hasTag()||!wheel.getTag().contains("AdaptationPocket"))&&InventoryPressure.returnPocket(p)==0,"The pocket could be returned twice");
        var full=player(h);var inv=full.getInventory();
        for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.IRON_SWORD));
        ItemStack carried=new ItemStack(AdaptationWheel.WHEEL.get());carried.getOrCreateTag().put("AdaptationPocket",pocket(new ItemStack(Items.EMERALD,5)));
        full.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);inv.setItem(40,carried);
        check(h,InventoryPressure.returnPocket(full)==5,"A wheel held in the offhand kept its pocket");
        int dropped=0;for(var e:h.getLevel().getEntitiesOfClass(ItemEntity.class,full.getBoundingBox().inflate(4)))if(e.getItem().is(Items.EMERALD))dropped+=e.getItem().getCount();
        check(h,dropped==5,"Items that did not fit were lost instead of dropped: "+dropped);
        h.succeed();
    }

    private static CompoundTag pocket(ItemStack... items){
        NonNullList<ItemStack> list=NonNullList.withSize(9,ItemStack.EMPTY);for(int i=0;i<items.length;i++)list.set(i,items[i]);
        return ContainerHelper.saveAllItems(new CompoundTag(),list);
    }
    private static int count(ServerPlayer p,net.minecraft.world.item.Item item){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;}
    private static void learn(ServerPlayer p,int stage){
        var key=MetaMechanic.POCKET.key;
        AdaptationEvents.state(p).hit(key,false,AdaptationProfile.ENVIRONMENT);
        int guard=40000;while(AdaptationEvents.state(p).stage(key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static ServerPlayer player(GameTestHelper h){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"StackTest"));
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p);
        p.setPos(0,100,0);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        return p;
    }
}
