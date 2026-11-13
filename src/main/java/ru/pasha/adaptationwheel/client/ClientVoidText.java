package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;
import ru.pasha.adaptationwheel.net.WheelNetwork;

/** What the server says about this player's adaptation to the Infinite Void's text, and whether it hears the look is open. */
public final class ClientVoidText {
    private static int ticks;
    private static boolean read, started, watching;

    private ClientVoidText() {
    }

    public static void accept(int newTicks, boolean newRead) {
        ticks = newTicks;
        read = newRead;
        // only a reset sends this after the stage has begun
        if (ticks == 0 && !read) started = false;
    }

    static int ticks() {
        return ticks;
    }

    static boolean read() {
        return read;
    }

    /** The stage has begun: the button was pressed here, or the server keeps its progress from an earlier visit. */
    static boolean adapting() {
        return !read && (started || ticks > 0);
    }

    static void start() {
        started = true;
    }

    /** Tells the server once whether the look menu shows this look; nothing happens without a world. */
    static void watch(boolean value) {
        var listener = Minecraft.getInstance().getConnection();
        if (value != watching && listener != null && WheelNetwork.watchVoidText(listener.getConnection(), value)) watching = value;
    }

    static void clear() {
        ticks = 0;
        read = started = watching = false;
    }
}
