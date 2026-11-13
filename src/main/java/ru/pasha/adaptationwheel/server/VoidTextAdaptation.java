package ru.pasha.adaptationwheel.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.VoidText;
import ru.pasha.adaptationwheel.net.WheelNetwork;

/**
 * The adaptation to the Infinite Void's text: one 20 s stage that runs only while the look menu shows that look and the
 * wheel is worn and on. The client says when the look is open (the reward is a readable text, so it may); the time is
 * counted here and kept with the player, so a closed menu pauses it and a finished one stays. Not a catalogue entry:
 * it protects from nothing and has no J-menu row.
 */
public final class VoidTextAdaptation {
    private static final String TICKS = "VoidTextTicks", READ = "VoidTextRead";
    /** Players whose look menu shows the Infinite Void. Server thread only. */
    private static final Set<UUID> WATCHING = new HashSet<>();

    private VoidTextAdaptation() {
    }

    public static boolean read(ServerPlayer player) {
        return MetaServer.meta(player).getBoolean(READ);
    }

    public static int ticks(ServerPlayer player) {
        return MetaServer.meta(player).getInt(TICKS);
    }

    public static void watch(ServerPlayer player, boolean watching) {
        if (watching && !read(player)) WATCHING.add(player.getUUID());
        else WATCHING.remove(player.getUUID());
    }

    /** One server tick of a player: counts the stage if the look is open and the wheel works. */
    public static void step(ServerPlayer player) {
        if (!WATCHING.contains(player.getUUID()) || !player.isAlive() || !AdaptationEvents.active(player)) return;
        int ticks = ticks(player) + 1;
        if (ticks >= VoidText.STAGE_TICKS) {
            complete(player);
            return;
        }
        MetaServer.meta(player).putInt(TICKS, ticks);
        // 1 % of the bar
        if (ticks % 4 == 0) WheelNetwork.voidText(player, ticks, false);
    }

    /** A stage completion as the wheel knows it: the sound and one turn. The text is the whole reward, so no heal. */
    private static void complete(ServerPlayer player) {
        var meta = MetaServer.meta(player);
        meta.remove(TICKS);
        meta.putBoolean(READ, true);
        WATCHING.remove(player.getUUID());
        AdaptationEvents.state(player).turnOnce();
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), AdaptationWheel.ADAPTED_SOUND.get(), SoundSource.PLAYERS, 1, 1);
        AdaptationEvents.saveAndSync(player);
        WheelNetwork.voidText(player, VoidText.STAGE_TICKS, true);
    }

    /** {@code /adaptation reset}: this adaptation goes with the others. */
    public static void reset(ServerPlayer player) {
        var meta = MetaServer.meta(player);
        meta.remove(TICKS);
        meta.remove(READ);
        WATCHING.remove(player.getUUID());
        WheelNetwork.voidText(player, 0, false);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !WATCHING.isEmpty() && event.player instanceof ServerPlayer player) step(player);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WheelNetwork.voidText(player, read(player) ? VoidText.STAGE_TICKS : ticks(player), read(player));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        WATCHING.clear();
    }
}
