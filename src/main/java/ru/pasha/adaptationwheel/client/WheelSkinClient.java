package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import ru.pasha.adaptationwheel.ConfigWatch;
import ru.pasha.adaptationwheel.WheelSkinConfig;
import ru.pasha.adaptationwheel.core.WheelSkin;
import ru.pasha.adaptationwheel.net.WheelNetwork;

import java.util.HashMap;
import java.util.Map;

/**
 * Which look each wheel has on this client. The own look comes straight from the client config (it is this
 * computer's choice); the server tells which look the other players chose and is told this player's choice on login
 * and whenever it changes.
 */
public final class WheelSkinClient {
    private static final Map<Integer, WheelSkin> OTHERS = new HashMap<>();
    /** The look last sent to the server of this connection; null until one is sent. */
    private static WheelSkin sent;
    /** While the look menu draws its preview: the player shown there wears this look, turn and pulse instead. */
    static Preview preview;

    record Preview(LivingEntity player, WheelSkin skin, float turn, float pulse, float seconds) {
    }

    private WheelSkinClient() {
    }

    /** This computer's look; the classic wheel while the client config is not loaded yet. */
    public static WheelSkin chosen() {
        try {
            return WheelSkinConfig.SKIN.get();
        } catch (IllegalStateException notLoaded) {
            return WheelSkin.CLASSIC;
        }
    }

    static WheelSkin of(Player player) {
        if (player == Minecraft.getInstance().player) return chosen();
        return OTHERS.getOrDefault(player.getId(), WheelSkin.CLASSIC);
    }

    /** «Надеть»: keeps the look for every world and server and shows it to the other players at once. */
    public static void wear(WheelSkin skin) {
        if (skin != chosen()) {
            // the player choosing a look is not someone editing the file from outside (the config-edit adaptation)
            ConfigWatch.ownSave(WheelSkinConfig.SPEC, () -> {
                WheelSkinConfig.SKIN.set(skin);
                WheelSkinConfig.SPEC.save();
            });
        }
        send();
    }

    /** Sends the own look if this connection has not heard it yet; nothing happens without a world. */
    static void send() {
        WheelSkin skin = chosen();
        var listener = Minecraft.getInstance().getConnection();
        if (skin != sent && listener != null && WheelNetwork.chooseSkin(listener.getConnection(), skin.id)) sent = skin;
    }

    static void login() {
        sent = null;
        send();
    }

    /** The client config file changed (Configured or an edit by hand). */
    static void configChanged() {
        send();
    }

    public static void accept(int entityId, String id) {
        WheelSkin skin = WheelSkin.byId(id);
        if (skin == WheelSkin.CLASSIC) OTHERS.remove(entityId);
        else OTHERS.put(entityId, skin);
    }

    static void remove(int entityId) {
        OTHERS.remove(entityId);
    }

    static void clear() {
        OTHERS.clear();
        sent = null;
    }
}
