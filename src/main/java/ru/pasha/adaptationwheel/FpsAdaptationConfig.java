package ru.pasha.adaptationwheel;

import net.minecraftforge.common.ForgeConfigSpec;

/** FPS belongs to the local computer, so its progress is never authoritative server data. */
public final class FpsAdaptationConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, STARTED;
    public static final ForgeConfigSpec.IntValue STAGE;
    public static final ForgeConfigSpec.DoubleValue WORK;
    static {
        var b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Adapt to sustained 1-59 FPS while wearing an enabled wheel. Stage four unlocks the optional Adapt Boost runtime enhancement.")
                .define("fpsAdaptationEnabled", true);
        b.comment("Earned client-only progress for this computer. Menus, loading and background time do not count.").push("progress");
        STAGE = b.defineInRange("completedStages", 0, 0, 4);
        WORK = b.defineInRange("stageWorkSeconds", 0.0, 0.0, 110.0);
        STARTED = b.define("started", false);
        b.pop();
        SPEC = b.build();
    }
    private FpsAdaptationConfig() {}
}
