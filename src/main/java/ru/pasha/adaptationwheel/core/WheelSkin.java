package ru.pasha.adaptationwheel.core;

/**
 * The looks a worn wheel can have. A look changes nothing but the picture: stages, protection, sounds, the 45° turn per
 * stage and the endless spin of the existence stage are the same for all of them. The classic golden wheel is the
 * original model; the others are the six models Pasha accepted from «Десять новых моделей колеса» (AdaptationHub
 * m5, m6, m9, m10-r2, m13, m14; since 4.33.1 the Event Horizon is m10-r2, m10 without the beam; since 4.33.2 the
 * Armillary Sphere is m15, m9 with every ring turning; since 4.33.3 it is m16, the same with two-colour rings turning
 * faster), drawn from {@code /wheel_skins/<id>.json} by {@link SkinRig}. Since 4.38.0 the Malevolent Shrine is Hub m17-r2
 * (design malevolent-shrine-2026-11-01 rev 2): its slash rings flash ({@link SkinRig.Flash}) and the client draws cuts
 * on the blocks and mobs around it ({@code client/ShrineCuts}); since 4.41.0 it is m17-r3 (rev 3): the slashes are bent
 * blades that come in waves. Since 4.39.0 the Infinite Void is Hub m18 (design
 * infinite-void-2026-11-02 rev 1), a plain box model with no world effects; since 4.40.0 it is m18-r2, the same with a
 * red inner stream, and its text is noise until the wearer adapts to it (design void-text-adaptation-2026-11-03 rev 2,
 * {@link VoidText}).
 */
public enum WheelSkin {
    CLASSIC("classic"),
    CLOCKWORK("clockwork"),
    ICE_CROWN("ice_crown"),
    ARMILLARY("armillary"),
    EVENT_HORIZON("event_horizon"),
    OUROBOROS("ouroboros"),
    STORM_RING("storm_ring"),
    MALEVOLENT_SHRINE("malevolent_shrine"),
    INFINITE_VOID("infinite_void");

    /** Stable id: sent over the network and saved in the client config, never the ordinal. */
    public final String id;

    WheelSkin(String id) {
        this.id = id;
    }

    /** The look with this id; anything unknown (an older or newer mod, a typo) is the classic wheel. */
    public static WheelSkin byId(String id) {
        if (id != null) for (WheelSkin skin : values()) if (skin.id.equals(id)) return skin;
        return CLASSIC;
    }

    public String translationKey() {
        return "skin.adaptationwheel." + id;
    }

    /** The model file of a box-built look; the classic wheel has none (it keeps its original model and texture). */
    public String resource() {
        return this == CLASSIC ? null : "/wheel_skins/" + id + ".json";
    }
}
