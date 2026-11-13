package ru.pasha.adaptationwheel.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import ru.pasha.adaptationwheel.AdaptationWheel;

/**
 * The wheel's advancement tab. Root, sword and the wheel recipe are data only (inventory_changed); the nodes here have
 * one {@code minecraft:impossible} criterion that only this class awards, always on the server thread. An advancement
 * is looked up on every call because /reload rebuilds them; a done one costs two lookups. Nothing is ever revoked.
 */
public final class WheelAdvancements {
    public static final ResourceLocation WORN=id("worn"),FIRST=id("first_adaptation"),FOUR=id("stage_four"),
        EXISTENCE=id("existence"),MENU=id("menu"),WORLD_SLASH=id("world_slash");
    private static final String CODE="code";
    private WheelAdvancements() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(AdaptationWheel.MOD_ID,path); }
    /** False when missing (a datapack removed it), already done, or for a FakePlayer (Forge makes award a no-op). */
    public static boolean award(ServerPlayer p,ResourceLocation id) {
        var a=p.server.getAdvancements().getAdvancement(id);return a!=null&&p.getAdvancements().award(a,CODE);
    }
    /** Cumulative, because a command can jump from 0 to IV at once. Only a boss entry ever reaches V. */
    public static void stage(ServerPlayer p,int stage) {
        if(stage>=1)award(p,FIRST);if(stage>=4)award(p,FOUR);if(stage>=5)award(p,EXISTENCE);
    }
    /**
     * At login, before the first advancement packet (a reset one, so the client shows no toasts): what the saved wheel
     * already proves. Only the stored stage counts, whatever the switches and the existence option say now.
     */
    public static void catchUp(ServerPlayer p) {
        int top=0;for(var s:AdaptationEvents.state(p).snapshots())top=Math.max(top,s.stage());
        stage(p,top);if(AdaptationEvents.equipped(p))award(p,WORN);
    }
}
