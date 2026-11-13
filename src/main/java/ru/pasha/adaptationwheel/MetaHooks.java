package ru.pasha.adaptationwheel;

import java.util.function.ToIntBiFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.server.AdaptationEvents;
import ru.pasha.adaptationwheel.server.MetaServer;

/**
 * Stage of a utility adaptation for code that runs on both sides (coremod hooks, events fired on client and server):
 * the server's state for a ServerPlayer, the synced state for the local player, 0 for anyone else or when the wheel
 * is off, the adaptation disabled, or the config switch off. Movement, mining speed and menu results are predicted by
 * the client, so both sides must reach the same verdict.
 */
public final class MetaHooks {
    /** Set by the client: the local player's synced stage. */
    public static volatile ToIntBiFunction<Player,MetaMechanic> client=(p,m)->0;
    private MetaHooks(){}
    public static int stage(Entity e,MetaMechanic m){
        if(e instanceof ServerPlayer p)return MetaServer.active(p,m)?Math.min(4,AdaptationEvents.state(p).stage(m.key)):0;
        return e instanceof Player p&&p.level().isClientSide?Math.min(4,client.applyAsInt(p,m)):0;
    }
    public static boolean full(Entity e,MetaMechanic m){return stage(e,m)>=4;}
}
