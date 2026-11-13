package ru.pasha.adaptationwheel.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client-only parts of the item tooltips: the player's own adaptations, the bound menu key and the Shift state. */
public final class ItemTooltips {
    private ItemTooltips() {
    }

    /** The wheel: the player's adaptations in numbers and the real menu key (mouse button 4 on Pasha's setup). */
    public static void wheel(List<Component> lines) {
        var mc = Minecraft.getInstance();
        var state = mc.player == null ? null : ClientWheelState.get(mc.player.getId());
        if (state != null) {
            int[] counts = AdaptationScreen.counts(state);
            lines.add(Component.translatable("item.adaptationwheel.wheel.tooltip.stats", counts[0], counts[1], counts[2]).withStyle(ChatFormatting.GOLD));
        }
        lines.add((ClientEvents.MENU.isUnbound() ? Component.translatable("item.adaptationwheel.wheel.tooltip.keys_unbound")
                : Component.translatable("item.adaptationwheel.wheel.tooltip.keys", ClientEvents.MENU.getTranslatedKeyMessage()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean shiftDown() {
        return Screen.hasShiftDown();
    }
}
