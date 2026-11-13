package ru.pasha.adaptationwheel;

import net.minecraftforge.common.ForgeConfigSpec;
import ru.pasha.adaptationwheel.core.LearningMode;

/** Server-owned learning policy, exposed in Configured without a mandatory dependency. */
public final class LearningConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<LearningMode> DEFAULT_MODE;
    public static final ForgeConfigSpec.BooleanValue ALLOW_PLAYER_MODE, SHARE_ACROSS_WORLDS;
    public static final ForgeConfigSpec.IntValue STUDY_PER_SECOND;
    static {
        var b = new ForgeConfigSpec.Builder();
        DEFAULT_MODE = b.translation("learning.adaptationwheel.config.default_mode")
                .comment("Default analyzer learning policy. FROZEN uses existing knowledge; ADAPTIVE learns from gameplay; RESEARCH also inspects loaded registry and datapack definitions. Ordinary adaptation and earned stages remain active in every mode.")
                .defineEnum("defaultMode", LearningMode.RESEARCH);
        ALLOW_PLAYER_MODE = b.translation("learning.adaptationwheel.config.allow_player_mode")
                .comment("Allow each player to choose their own analyzer learning mode in the wheel menu. When false, defaultMode is enforced.")
                .define("allowPlayerMode", true);
        SHARE_ACROSS_WORLDS = b.translation("learning.adaptationwheel.config.share_across_worlds")
                .comment("Keep this player's analyzer examples across worlds on this installation. This does not transfer earned adaptation stages, inventories, coordinates or health.")
                .define("shareAcrossWorlds", true);
        STUDY_PER_SECOND = b.translation("learning.adaptationwheel.config.study_budget")
                .comment("Maximum registry definitions inspected per second by world research. Loaded game data only; no chunk generation or arbitrary code execution.")
                .defineInRange("definitionsPerSecond", 8, 1, 1_000_000);
        SPEC = b.build();
    }
    private LearningConfig() {}
    public static LearningMode defaultMode() { return SPEC.isLoaded() ? DEFAULT_MODE.get() : LearningMode.RESEARCH; }
    public static boolean playersMayChoose() { return !SPEC.isLoaded() || ALLOW_PLAYER_MODE.get(); }
    /** Forge syncs server configs to a connecting client, so the learning screen reads the real budget too. */
    public static int definitionsPerSecond() { return SPEC.isLoaded() ? STUDY_PER_SECOND.get() : 8; }
}
