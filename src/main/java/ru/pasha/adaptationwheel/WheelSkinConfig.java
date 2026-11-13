package ru.pasha.adaptationwheel;

import net.minecraftforge.common.ForgeConfigSpec;
import ru.pasha.adaptationwheel.core.WheelSkin;

/** The wheel's look belongs to this computer: one choice for every world and server, sent to the server on login. */
public final class WheelSkinConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<WheelSkin> SKIN;
    public static final ForgeConfigSpec.BooleanValue SHRINE_CUTS, VOID_STREAM, VOID_STARS, VOID_RINGS;
    static {
        var b = new ForgeConfigSpec.Builder();
        SKIN = b.comment("How the worn wheel looks (picture only: stages, protection and sounds stay the same). "
                        + "Other players with the mod see this look too. Also chosen in the J menu: the Look button or Ctrl+K.")
                .translation("config.adaptationwheel.wheel_skin")
                .defineEnum("wheelSkin", WheelSkin.CLASSIC);
        SHRINE_CUTS = b.comment("Cuts of the Malevolent Shrine look flash on the blocks and mobs around every player wearing it "
                        + "(picture only: blocks stay whole, mobs take no damage). Off: the shrine and its flashing slashes stay, "
                        + "the cuts around are not drawn on this computer.")
                .translation("config.adaptationwheel.shrine_cuts")
                .define("shrineCuts", true);
        VOID_STREAM = b.comment("Infinite Void look: lines of flickering noise run over mobs within 10 blocks of the wheel; after 8 s "
                        + "a mob trembles and glyphs run down it (picture only: the mob moves and fights as usual).")
                .translation("config.adaptationwheel.void_stream")
                .define("voidStream", true);
        VOID_STARS = b.comment("Infinite Void look: about 24 sparks circle the wheel and are drawn into its black core.")
                .translation("config.adaptationwheel.void_stars")
                .define("voidStars", true);
        VOID_RINGS = b.comment("Infinite Void look: a blue ring closes in on the feet while a red one spreads out, every 4 s "
                        + "(picture only: nothing is pulled or pushed).")
                .translation("config.adaptationwheel.void_rings")
                .define("voidRings", true);
        SPEC = b.build();
    }
    private WheelSkinConfig() {}
}
