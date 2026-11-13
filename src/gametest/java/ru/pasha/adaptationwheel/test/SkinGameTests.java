package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.WheelSkin;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import ru.pasha.adaptationwheel.server.AdaptationEvents;

/**
 * The wheel's look is a client's own choice; the server keeps it per player (by UUID) and tells every player who starts
 * seeing that player. The packets are read back from the wire format (channel index 7: entity id, look id).
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkinGameTests {
    private static final ResourceLocation CHANNEL = new ResourceLocation(AdaptationWheel.MOD_ID, "state");

    @GameTest(template = "empty")
    public static void lookTravelsToEveryViewer(GameTestHelper h) {
        var seen = new ArrayList<Packet<?>>();
        ServerPlayer subject = player(h, "SkinSubject", null), viewer = player(h, "SkinViewer", seen);
        check(h, WheelNetwork.skin(subject) == WheelSkin.CLASSIC, "A player who chose nothing wears the classic wheel");
        WheelNetwork.chooseSkin(subject, WheelSkin.CLOCKWORK);
        check(h, WheelNetwork.skin(subject) == WheelSkin.CLOCKWORK, "The chosen look is not kept");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, subject));
        check(h, looks(seen).equals(List.of(subject.getId() + "=clockwork")), "A new viewer is told the look: " + looks(seen) + " among " + seen.stream().map(x -> x.getClass().getSimpleName()).toList());
        seen.clear();
        WheelNetwork.chooseSkin(subject, WheelSkin.CLASSIC);
        check(h, WheelNetwork.skin(subject) == WheelSkin.CLASSIC, "Choosing the classic wheel again is not kept");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, subject));
        // the classic wheel is sent too: the viewer may still remember an older look under a reused entity id
        check(h, looks(seen).equals(List.of(subject.getId() + "=classic")), "The classic wheel is not sent: " + looks(seen));
        seen.clear();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, viewer));
        check(h, looks(seen).isEmpty(), "A player's own client is told its own look: " + looks(seen));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lookSurvivesRespawnAndEndsAtLogout(GameTestHelper h) {
        ServerPlayer p = player(h, "SkinRespawn", null);
        WheelNetwork.chooseSkin(p, WheelSkin.ICE_CROWN);
        // a respawn makes a new ServerPlayer with the same profile; the client sends its look only on login
        var replacement = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), p.getGameProfile());
        check(h, WheelNetwork.skin(replacement) == WheelSkin.ICE_CROWN, "The look is lost on respawn");
        AdaptationEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p));
        check(h, WheelNetwork.skin(replacement) == WheelSkin.CLASSIC, "The look outlives the player's logout");
        h.succeed();
    }

    /** The look packets among the captured ones, as "entityId=look". */
    private static List<String> looks(List<Packet<?>> sent) {
        var out = new ArrayList<String>();
        for (var packet : sent) {
            if (!(packet instanceof ClientboundCustomPayloadPacket payload) || !payload.getIdentifier().equals(CHANNEL)) continue;
            FriendlyByteBuf data = new FriendlyByteBuf(payload.getData().copy());
            if (data.readUnsignedByte() != 7) continue;
            out.add(data.readVarInt() + "=" + data.readUtf(64));
        }
        return out;
    }

    private static void check(GameTestHelper h, boolean value, String message) {
        if (!value) h.fail(message);
    }

    private static ServerPlayer player(GameTestHelper h, String name, List<Packet<?>> sent) {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        // Forge's PacketDistributor.PLAYER writes to the Connection itself, past the packet listener
        var connection = sent == null ? new Connection(PacketFlow.SERVERBOUND) : new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { sent.add(packet); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { sent.add(packet); }
        };
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p);
        return p;
    }
}
