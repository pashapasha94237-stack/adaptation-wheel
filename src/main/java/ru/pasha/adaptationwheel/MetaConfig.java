package ru.pasha.adaptationwheel;

import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/** Configured discovers both specs; progress is server-owned, rendering is client-owned. */
public final class MetaConfig {
    public static final ForgeConfigSpec SERVER,CLIENT;
    public static final Map<MetaMechanic,ForgeConfigSpec.BooleanValue> ENABLED=new EnumMap<>(MetaMechanic.class);
    public static final ForgeConfigSpec.IntValue BORDER_REACH,LANDSCAPE_DISTANCE,RIPTIDE_DRY_POWER;
    public static final ForgeConfigSpec.DoubleValue LOW_TPS,HEAP_PRESSURE;
    public static final ForgeConfigSpec.DoubleValue NEAR_MISS_RADIUS,HOMING_RANGE;
    public static final ForgeConfigSpec.ConfigValue<String> ARCHIVE_DIRECTORY;
    public static final ForgeConfigSpec.BooleanValue GHOSTS;
    static{
        var b=new ForgeConfigSpec.Builder();
        for(var m:MetaMechanic.values())ENABLED.put(m,b.translation(m.nameKey()).comment("Enable this utility adaptation. Saved progress is retained when disabled.").define(m.id,true));
        BORDER_REACH=b.translation("meta.adaptationwheel.config.border_reach").comment("Maximum personal distance outside the unchanged world border. Removing the wheel safely returns the player.").defineInRange("personalBorderReach",64,8,512);
        LOW_TPS=b.translation("meta.adaptationwheel.config.low_tps").comment("Begin adaptation after ten seconds of sustained low server TPS. Only this mod's own persistence/network work is batched after completion.").defineInRange("lowTpsThreshold",18.0,1.0,19.9);
        RIPTIDE_DRY_POWER=b.translation("meta.adaptationwheel.config.riptide_dry_power").comment("Riptide dash power on dry land at stage four, in percent of the dash in water or rain (stage three: 50).").defineInRange("riptideDryPower",75,50,100);
        b.push("aim");
        NEAR_MISS_RADIUS=b.translation("meta.adaptationwheel.config.near_miss_radius").comment("Distance from the actual target bounding box which counts as a close miss. Actual hits do not count.").defineInRange("nearMissRadius",2.0,.25,4.0);
        HOMING_RANGE=b.translation("meta.adaptationwheel.config.homing_range").comment("Search loaded visible targets in this radius after stage four. Does not load chunks, change damage, or phase through walls.").defineInRange("homingRange",64.0,8.0,128.0);
        b.pop();
        SERVER=b.build();
        b=new ForgeConfigSpec.Builder();
        LANDSCAPE_DISTANCE=b.translation("meta.adaptationwheel.config.landscape_distance").comment("LOD view radius in chunks; this does not limit the area stored on disk. Archive files are never removed to enforce an area quota.").defineInRange("landscapeViewChunks",128,32,1024);
        ARCHIVE_DIRECTORY=b.translation("meta.adaptationwheel.config.archive_directory").comment("Optional absolute directory on your SSD. Empty keeps the engine's per-world/per-server SQLite storage. Do not change while a world is open.").define("landscapeArchiveDirectory","");
        HEAP_PRESSURE=b.translation("meta.adaptationwheel.config.heap_pressure").comment("Java heap utilization starting memory adaptation after ten sustained seconds. Does not call System.gc or erase third-party caches.").defineInRange("heapPressureThreshold",0.85,0.5,0.98);
        GHOSTS=b.translation("meta.adaptationwheel.defeat_ghosts").define("showDefeatTrail",true);
        CLIENT=b.build();
    }
    private MetaConfig(){}
    public static boolean enabled(MetaMechanic m){return !SERVER.isLoaded()||ENABLED.get(m).get();}
}
