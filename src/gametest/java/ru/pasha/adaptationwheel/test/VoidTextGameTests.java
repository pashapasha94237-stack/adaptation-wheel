package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.VoidText;
import ru.pasha.adaptationwheel.server.AdaptationEvents;
import ru.pasha.adaptationwheel.server.MetaServer;
import ru.pasha.adaptationwheel.server.VoidTextAdaptation;

/**
 * The adaptation to the Infinite Void's text is one 20 s stage counted by the server: only while the client says the
 * look is open and the wheel is worn and on. It is kept with the player and ends like a stage (one wheel turn).
 * The packets are read back from the wire format (channel index 8: ticks, read).
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VoidTextGameTests {
    private static final ResourceLocation CHANNEL = new ResourceLocation(AdaptationWheel.MOD_ID, "state");

    @GameTest(template = "empty")
    public static void stageTakesTwentySecondsAndTurnsTheWheelOnce(GameTestHelper h) {
        var seen = new ArrayList<Packet<?>>();
        ServerPlayer p = player(h, "VoidReader", seen);
        wearWheel(p, true);
        long revision = AdaptationEvents.state(p).revision();
        VoidTextAdaptation.watch(p, true);
        steps(p, VoidText.STAGE_TICKS - 1);
        check(h, !VoidTextAdaptation.read(p) && VoidTextAdaptation.ticks(p) == VoidText.STAGE_TICKS - 1, "The stage ends before 20 s: " + VoidTextAdaptation.ticks(p));
        check(h, AdaptationEvents.state(p).revision() == revision, "The wheel turns before the stage ends");
        check(h, sent(seen).contains("180/false"), "The client hears the progress: " + sent(seen));
        VoidTextAdaptation.step(p);
        check(h, VoidTextAdaptation.read(p), "The stage does not end at 20 s");
        check(h, AdaptationEvents.state(p).revision() == revision + 1, "A finished stage turns the wheel once");
        check(h, sent(seen).get(sent(seen).size() - 1).equals("400/true"), "The client is not told the text is read: " + sent(seen));
        steps(p, 50);
        VoidTextAdaptation.watch(p, true);
        steps(p, 50);
        check(h, AdaptationEvents.state(p).revision() == revision + 1, "A read text turns the wheel again");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stagePausesUnlessTheLookIsOpenAndTheWheelWorks(GameTestHelper h) {
        ServerPlayer p = player(h, "VoidPause", null);
        wearWheel(p, true);
        steps(p, 10);
        check(h, VoidTextAdaptation.ticks(p) == 0, "The stage runs before the button");
        VoidTextAdaptation.watch(p, true);
        steps(p, 100);
        wearWheel(p, false);
        steps(p, 100);
        check(h, VoidTextAdaptation.ticks(p) == 100, "The stage runs with the wheel off the head");
        wearWheel(p, true);
        AdaptationEvents.state(p).setEnabled(false);
        steps(p, 100);
        check(h, VoidTextAdaptation.ticks(p) == 100, "The stage runs with the wheel switched off");
        AdaptationEvents.state(p).setEnabled(true);
        VoidTextAdaptation.watch(p, false);
        steps(p, 100);
        check(h, VoidTextAdaptation.ticks(p) == 100, "The stage runs with the menu closed");
        VoidTextAdaptation.watch(p, true);
        steps(p, 20);
        check(h, VoidTextAdaptation.ticks(p) == 120, "The stage does not go on from where it stopped");
        VoidTextAdaptation.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
        steps(p, 20);
        check(h, VoidTextAdaptation.ticks(p) == 120, "The stage runs after logout");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void progressStaysWithThePlayerUntilAReset(GameTestHelper h) {
        var seen = new ArrayList<Packet<?>>();
        ServerPlayer p = player(h, "VoidKeep", seen);
        wearWheel(p, true);
        VoidTextAdaptation.watch(p, true);
        steps(p, 37);
        // a respawn makes a new ServerPlayer; what was earned moves over in the Clone event
        var replacement = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), p.getGameProfile());
        MetaServer.clone(new PlayerEvent.Clone(replacement, p, true));
        check(h, VoidTextAdaptation.ticks(replacement) == 37, "The progress is lost on respawn");
        VoidTextAdaptation.watch(p, true);
        steps(p, VoidText.STAGE_TICKS);
        check(h, VoidTextAdaptation.read(p), "The stage does not finish");
        replacement = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), p.getGameProfile());
        MetaServer.clone(new PlayerEvent.Clone(replacement, p, true));
        check(h, VoidTextAdaptation.read(replacement), "The read text is lost on respawn");
        // the player file: what a restart reads back, and the client hears it at login
        var saved = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), p.getGameProfile());
        saved.load(p.saveWithoutId(new CompoundTag()));
        check(h, VoidTextAdaptation.read(saved), "The read text is not saved with the player");
        seen.clear();
        VoidTextAdaptation.login(new PlayerEvent.PlayerLoggedInEvent(p));
        check(h, sent(seen).equals(List.of("400/true")), "The client is not told at login: " + sent(seen));
        VoidTextAdaptation.reset(p);
        check(h, !VoidTextAdaptation.read(p) && VoidTextAdaptation.ticks(p) == 0, "A reset keeps the text read");
        h.succeed();
    }

    private static void steps(ServerPlayer p, int count) {
        for (int i = 0; i < count; i++) VoidTextAdaptation.step(p);
    }

    private static void wearWheel(ServerPlayer p, boolean worn) {
        p.setItemSlot(EquipmentSlot.HEAD, worn ? new ItemStack(AdaptationWheel.WHEEL.get()) : ItemStack.EMPTY);
    }

    /** The text packets among the captured ones, as "ticks/read". */
    private static List<String> sent(List<Packet<?>> sent) {
        var out = new ArrayList<String>();
        for (var packet : sent) {
            if (!(packet instanceof ClientboundCustomPayloadPacket payload) || !payload.getIdentifier().equals(CHANNEL)) continue;
            FriendlyByteBuf data = new FriendlyByteBuf(payload.getData().copy());
            if (data.readUnsignedByte() != 8) continue;
            out.add(data.readVarInt() + "/" + data.readBoolean());
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
