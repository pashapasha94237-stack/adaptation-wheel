package ru.pasha.adaptationwheel.core;

/** Bounded consequence estimate from an actual harmful effect, not its display name. */
public final class EffectThreat {
    public static double estimate(AdaptationProfile profile,int amplifier,int durationTicks,boolean infinite){
        double seconds=Math.max(0,Math.min(3600,durationTicks/20.0));
        double duration=infinite?1:Math.min(1,Math.log1p(seconds)/Math.log1p(180));
        double strength=Math.log1p(Math.max(0,Math.min(10,amplifier)))/Math.log(11);
        double base=profile==AdaptationProfile.POISON||profile==AdaptationProfile.MAGIC?.18:
                profile==AdaptationProfile.DARKNESS?.15:.10;
        return Math.min(.85,base+.25*duration+.30*strength);
    }
    private EffectThreat(){}
}
