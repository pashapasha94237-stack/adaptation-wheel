package ru.pasha.adaptationwheel.core;

import java.util.List;

/** Persistent, individually switchable non-combat adaptations. */
public enum MetaMechanic {
    BORDER("world_border"), NOISE("notification_noise"), POCKET("inventory_pressure"),
    SILENCE("silent_world"), MEMORY("memory_pressure"),
    TPS("low_tps"), DEFEAT("defeat_memory"),
    // Outside the game world: the mod pack, files, clock, the computer and the player behind it.
    MODS("foreign_mods"), LOGS("log_errors"), RENAME("world_rename"), CONFIG("config_edit"),
    CLOCK("clock_shift"), CRASH("crash_memory"), AFK("idle_player"), INVENTORY("open_inventory"),
    NETWORK("connection_loss"), UPDATE("wheel_update"), COMMANDS("console_commands"), MISSES("near_misses"),
    // 4.29: the game refuses or hinders something again and again; the wheel lifts that limit for its wearer.
    DAY_SLEEP("day_sleep"), RESTLESS("restless_sleep"), INSOMNIA("insomnia"), FOREIGN_BED("foreign_bed"),
    STICKY("sticky_blocks"), PORTAL("portal_wait"), AIR_MINING("loose_footing"), FARMLAND("trampled_farmland"),
    SMELTING("smelting_wait"), NETHER_WATER("nether_water"),
    BROKEN_TOOL("broken_tool"), ANVIL("too_expensive"), QUIVER("empty_quiver"), COOLDOWN("item_cooldown"),
    ENDERMAN("enderman_gaze"), CREEPER("creeper_grief"), PIGLIN("piglin_gold"), TRADE("villager_trade"), SURROUNDED("surrounded"),
    MURKY("murky_water"), LAVA_SIGHT("lava_sight"), UNSEEN("unseen_foes"), BATTERY("laptop_battery"),
    // 4.30: travel.
    RIPTIDE("dry_riptide"), ENDER_EYE("shattered_eye");
    public static final StageDurations TIMES=StageDurations.seconds(20,50,80,110,140);
    public static final StageProtections NO_DAMAGE=new StageProtections(0,0,0,0);
    public final String id,key;
    MetaMechanic(String id){this.id=id;this.key="environment:"+id;}
    public String nameKey(){return "meta.adaptationwheel."+id;}
    public String hintKey(){return nameKey()+".hint";}
    /** values() copies the array on every call; hot paths iterate this shared one instead. */
    public static final List<MetaMechanic> ALL=List.of(values());
    public static MetaMechanic fromKey(String key){for(var m:ALL)if(m.key.equals(key))return m;return null;}
}
