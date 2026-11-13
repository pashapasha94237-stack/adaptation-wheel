package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.pasha.adaptationwheel.net.LearningNetwork;

/** Ephemeral display data. Player and dimension checks prevent showing another session's status. */
@Mod.EventBusSubscriber(modid = "adaptationwheel", value = Dist.CLIENT)
public final class LearningClient {
    private static LearningNetwork.State state;
    private LearningClient() {}
    public static void accept(LearningNetwork.State value) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && value.player().equals(mc.player.getUUID()) && value.dimension().equals(mc.level.dimension().location().toString())) state = value;
    }
    public static LearningNetwork.State state() {
        var mc = Minecraft.getInstance();
        if (state == null || mc.player == null || mc.level == null || !state.player().equals(mc.player.getUUID()) || !state.dimension().equals(mc.level.dimension().location().toString())) return null;
        return state;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { state = null; }
}
