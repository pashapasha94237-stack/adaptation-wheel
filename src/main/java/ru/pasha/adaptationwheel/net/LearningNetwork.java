package ru.pasha.adaptationwheel.net;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.pasha.adaptationwheel.LearningConfig;
import ru.pasha.adaptationwheel.core.LearningMode;
import ru.pasha.adaptationwheel.server.LearningController;

/** A player may request their own status and an allowed mode; no examples, stages or code arrive from clients. */
public final class LearningNetwork {
    private static final SimpleChannel C = NetworkRegistry.newSimpleChannel(new ResourceLocation("adaptationwheel", "learning"), () -> "1", "1"::equals, "1"::equals);
    private static final Map<ServerPlayer, Long> QUERIES = new WeakHashMap<>(), CHANGES = new WeakHashMap<>();
    private LearningNetwork() {}
    public static void register() {
        C.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(Request::write).decoder(Request::read).consumerMainThread(Request::handle).add();
        C.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT).encoder(State::write).decoder(State::read).consumerMainThread(State::handle).add();
    }
    public static void request() { C.sendToServer(new Request(-1)); }
    public static void choose(LearningMode mode) { C.sendToServer(new Request(mode.ordinal())); }
    public static void sendStatus(ServerPlayer p) {
        var s = LearningController.status(p);
        C.send(PacketDistributor.PLAYER.with(() -> p), new State(p.getUUID(), p.level().dimension().location().toString(), s.mode(), s.pretrained(),
                s.seedFacts(), s.learnedFacts(), s.observations(), s.contradictions(), s.inspected(), s.total(), s.scanning(), LearningConfig.playersMayChoose()));
    }
    private static boolean allowed(Map<ServerPlayer, Long> times, ServerPlayer p, int interval) {
        long now = p.serverLevel().getGameTime(); Long previous = times.get(p);
        if (previous != null && now >= previous && now - previous < interval) return false;
        times.put(p, now); return true;
    }
    private record Request(int mode) {
        void write(FriendlyByteBuf b) { b.writeVarInt(mode); }
        static Request read(FriendlyByteBuf b) {
            int n = b.readVarInt(); if (n < -1 || n >= LearningMode.values().length) throw new IllegalArgumentException("Invalid learning mode");
            return new Request(n);
        }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); ServerPlayer p = context.getSender();
            if (p != null && p.isAlive() && !p.isSpectator() && allowed(mode < 0 ? QUERIES : CHANGES, p, mode < 0 ? 5 : 2)) {
                if (mode >= 0 && LearningConfig.playersMayChoose()) LearningController.setMode(p, LearningMode.values()[mode]);
                else sendStatus(p);
            }
            context.setPacketHandled(true);
        }
    }
    public record State(UUID player, String dimension, LearningMode mode, boolean pretrained, int seedFacts, int learnedFacts,
                        int observations, int contradictions, int inspected, int total, boolean scanning, boolean editable) {
        public State {
            seedFacts = bounded(seedFacts); learnedFacts = bounded(learnedFacts); observations = bounded(observations);
            contradictions = bounded(contradictions); inspected = bounded(inspected); total = bounded(total);
        }
        private static int bounded(int n) { return Math.max(0, n); }
        void write(FriendlyByteBuf b) {
            b.writeUUID(player); b.writeUtf(dimension, 128); b.writeEnum(mode); b.writeBoolean(pretrained);
            b.writeVarInt(seedFacts); b.writeVarInt(learnedFacts); b.writeVarInt(observations); b.writeVarInt(contradictions);
            b.writeVarInt(inspected); b.writeVarInt(total); b.writeBoolean(scanning); b.writeBoolean(editable);
        }
        private static int count(FriendlyByteBuf b) {
            int n = b.readVarInt(); if (n < 0) throw new IllegalArgumentException("Invalid learning statistic"); return n;
        }
        static State read(FriendlyByteBuf b) {
            UUID player = b.readUUID(); String dimension = b.readUtf(128);
            if (ResourceLocation.tryParse(dimension) == null) throw new IllegalArgumentException("Invalid learning dimension");
            LearningMode mode = b.readEnum(LearningMode.class); boolean trained = b.readBoolean();
            int seeds = count(b), facts = count(b), observations = count(b), contradictions = count(b), inspected = count(b), total = count(b);
            return new State(player, dimension, mode, trained, seeds, facts, observations, contradictions, inspected, total, b.readBoolean(), b.readBoolean());
        }
        void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.pasha.adaptationwheel.client.LearningClient.accept(this));
            context.get().setPacketHandled(true);
        }
    }
}
