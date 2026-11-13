package ru.pasha.adaptationwheel.server;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.pasha.adaptationwheel.StackLimits;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/**
 * Overfull inventory adaptation. Stage two merges compatible stacks; stage four lifts the stack limit of the wearer's
 * own inventory (see StackLimits), so merging then frees every duplicate slot.
 */
public final class InventoryPressure {
    /** Where 4.13–4.19 kept the nine-slot pocket: in the NBT of the physical wheel. */
    static final String POCKET = "AdaptationPocket";
    private InventoryPressure(){}

    /** Preserve hotbar order and NBT identity. Never discard an item. */
    public static int compact(ServerPlayer p){
        if(!MetaServer.active(p,MetaMechanic.POCKET)||AdaptationEvents.state(p).stage(MetaMechanic.POCKET.key)<2||p.containerMenu!=p.inventoryMenu)return 0;
        var inv=p.getInventory();int moved=0;
        for(int to=9;to<36;to++){
            ItemStack dst=inv.getItem(to);if(dst.isEmpty()||!dst.isStackable())continue;
            int limit=StackLimits.item(dst,inv);
            for(int from=to+1;from<36&&dst.getCount()<limit;from++){
                ItemStack src=inv.getItem(from);if(!ItemStack.isSameItemSameTags(dst,src))continue;
                int n=Math.min(src.getCount(),limit-dst.getCount());if(n<=0)continue;
                dst.grow(n);src.shrink(n);moved+=n;if(src.isEmpty())inv.setItem(from,ItemStack.EMPTY);
            }
        }
        if(moved>0){inv.setChanged();p.inventoryMenu.broadcastChanges();}return moved;
    }
    public static int occupied(Player p){int count=0;for(int i=0;i<36;i++)if(!p.getInventory().getItem(i).isEmpty())count++;return count;}

    /**
     * The pocket was replaced by unlimited stacks. A wheel that still carries pocket items gives them back the first
     * time it is in the player's inventory (worn or not); what does not fit is dropped at the player's feet.
     */
    public static int returnPocket(ServerPlayer p){
        var inv=p.getInventory();int returned=0;boolean dropped=false;
        for(int i=0;i<inv.getContainerSize();i++){
            ItemStack wheel=inv.getItem(i);
            if(wheel.isEmpty()||!wheel.hasTag()||!wheel.getTag().contains(POCKET,Tag.TAG_COMPOUND))continue;
            NonNullList<ItemStack> saved=NonNullList.withSize(9,ItemStack.EMPTY);
            ContainerHelper.loadAllItems(wheel.getTag().getCompound(POCKET),saved);
            wheel.removeTagKey(POCKET);
            for(ItemStack item:saved){
                if(item.isEmpty())continue;returned+=item.getCount();
                inv.add(item);
                if(!item.isEmpty()){p.drop(item,false);dropped=true;}
            }
        }
        if(returned>0)p.displayClientMessage(Component.translatable(dropped?"meta.adaptationwheel.pocket.returned_dropped":"meta.adaptationwheel.pocket.returned",returned),false);
        return returned;
    }
}
