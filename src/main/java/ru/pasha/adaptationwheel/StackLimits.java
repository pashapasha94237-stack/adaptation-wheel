package ru.pasha.adaptationwheel;

import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.core.StackCount;
import ru.pasha.adaptationwheel.server.MetaServer;

/**
 * Called from coremods/stack_limits.js. A wearer with the full inventory pressure adaptation keeps any number of a
 * stackable item in one slot of their own inventory (hotbar, main and offhand; armour slots and every other
 * container keep vanilla limits). Items that never stack (tools, armour, potions) stay single.
 */
public final class StackLimits {
    /** Set by the client: the server's verdict for the local player, mirrored so click prediction agrees. */
    public static volatile Predicate<Player> client = player -> false;
    private StackLimits() {}

    public static boolean unlimited(Player player) {
        if (player instanceof ServerPlayer server) return MetaServer.full(server, MetaMechanic.POCKET);
        return player != null && player.level().isClientSide && client.test(player);
    }
    private static boolean lifts(ItemStack stack, Player player) { return stack.getMaxStackSize() > 1 && unlimited(player); }

    /** Inventory: replaces ItemStack.getMaxStackSize() for stacks the inventory is merging into. */
    public static int item(ItemStack stack, Inventory inventory) {
        return lifts(stack, inventory.player) ? StackCount.LIMIT : stack.getMaxStackSize();
    }
    /** Inventory: replaces its own container limit of 64. */
    public static int container(Inventory inventory) {
        return unlimited(inventory.player) ? StackCount.LIMIT : inventory.getMaxStackSize();
    }
    /** Slot.getMaxStackSize(ItemStack): true for the wearer's hotbar, main and offhand slots. */
    public static boolean lifted(Slot slot, ItemStack stack) {
        if (!(slot.container instanceof Inventory inventory)) return false;
        int index = slot.getContainerSlot();
        return (index < Inventory.INVENTORY_SIZE || index == Inventory.SLOT_OFFHAND) && lifts(stack, inventory.player);
    }
    /**
     * moveItemStackTo (shift-click) compares with the slot's container limit and the item's own limit separately;
     * asking the slot about this item gives the same answer for vanilla slots and the lifted one for ours.
     */
    public static int slotLimit(Slot slot, ItemStack stack) { return slot.getMaxStackSize(stack); }
    public static int unbounded(ItemStack stack) { return stack.getMaxStackSize() > 1 ? StackCount.LIMIT : stack.getMaxStackSize(); }

    /** ItemStack.save: vanilla's byte for ordinary stacks, so saves stay readable without the mod; an int above 127. */
    public static void putCount(CompoundTag tag, String key, int count) {
        if (count == (byte) count) tag.putByte(key, (byte) count);
        else tag.putInt(key, count);
    }
    /** Creative inventory packets are refused above 64 by the server. */
    public static int creativeLimit(Player player) { return unlimited(player) ? StackCount.LIMIT : 64; }
    public static String countText(int count) { return StackCount.format(count); }
}
