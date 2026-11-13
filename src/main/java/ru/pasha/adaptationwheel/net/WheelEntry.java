package ru.pasha.adaptationwheel.net;
import ru.pasha.adaptationwheel.core.AdaptationState;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.ElementExperience;
import ru.pasha.adaptationwheel.core.StageDurations;
import ru.pasha.adaptationwheel.core.AdaptationTimings;
import ru.pasha.adaptationwheel.core.StageProtections;

public record WheelEntry(String key, String translationKey, boolean enabled, boolean boss,
                         int stage, double work, int hits, boolean running, boolean progressing, AdaptationProfile profile,StageDurations timings,int hitAccelerationPercent,StageProtections protections,boolean existenceEnabled,boolean oneHitKillEnabled,AnalysisInfo analysis,AdaptationProfile analyzerProfile,boolean fixable,int experiencePercent) {
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile,StageDurations timings,int hitAccelerationPercent,StageProtections protections,boolean existenceEnabled,boolean oneHitKillEnabled,AnalysisInfo analysis,AdaptationProfile analyzerProfile,boolean fixable) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,protections,existenceEnabled,oneHitKillEnabled,analysis,analyzerProfile,fixable,0);
    }
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile,StageDurations timings,int hitAccelerationPercent,StageProtections protections,boolean existenceEnabled,boolean oneHitKillEnabled,AnalysisInfo analysis) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,protections,existenceEnabled,oneHitKillEnabled,analysis,null,false);
    }
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile,StageDurations timings,int hitAccelerationPercent,StageProtections protections,boolean existenceEnabled,boolean oneHitKillEnabled) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,protections,existenceEnabled,oneHitKillEnabled,AnalysisInfo.NONE);
    }
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile,StageDurations timings,int hitAccelerationPercent,StageProtections protections) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,protections,AdaptationTimings.existenceEnabled(),AdaptationTimings.oneHitKillEnabled());
    }
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile,StageDurations timings,int hitAccelerationPercent) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,AdaptationTimings.protections());
    }
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile,StageDurations timings) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,AdaptationTimings.hitAccelerationPercent());
    }
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,AdaptationTimings.forSource(key,profile==null?AdaptationProfile.forKey(key,boss):profile));
    }
    public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing) {
        this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,AdaptationProfile.forKey(key,boss));
    }
    public WheelEntry { if(profile==null) profile=AdaptationProfile.forKey(key,boss);if(timings==null)timings=AdaptationTimings.forSource(key,profile);
        if(hitAccelerationPercent<0||hitAccelerationPercent>AdaptationTimings.MAX_HIT_ACCELERATION_PERCENT)throw new IllegalArgumentException("Invalid hit acceleration percentage");
        if(protections==null)throw new IllegalArgumentException("Missing stage protections");if(analysis==null)analysis=AnalysisInfo.NONE;
        experiencePercent=Math.max(0,Math.min(ElementExperience.MOST_PERCENT,experiencePercent)); }
    public WheelEntry withAnalysis(AnalysisInfo value) {
        return new WheelEntry(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,protections,existenceEnabled,oneHitKillEnabled,value,analyzerProfile,fixable,experiencePercent);
    }
    /** analyzer is what the analyzer thinks of a row the player corrected, null on any other row. */
    public WheelEntry withCategory(AdaptationProfile analyzer,boolean canFix) {
        return new WheelEntry(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,protections,existenceEnabled,oneHitKillEnabled,analysis,analyzer,canFix,experiencePercent);
    }
    /** Element experience: the starting speed of this row's stages, in per cent. */
    public WheelEntry withExperience(int percent) {
        return new WheelEntry(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile,timings,hitAccelerationPercent,protections,existenceEnabled,oneHitKillEnabled,analysis,analyzerProfile,fixable,percent);
    }
    /** The player chose this row's {@link #profile}. */
    public boolean manual() { return analyzerProfile!=null; }
    /** The analyzer's own verdict, whatever the row shows. */
    public AdaptationProfile verdict() { return manual()?analyzerProfile:profile; }
    public int duration() { return timings.ticks(stage); }
    /** The stage's pace: repeat hits plus element experience. */
    public double speed() { return AdaptationState.speedFor(hits,hitAccelerationPercent,experiencePercent); }
    public int reduction() { return protections.percent(existenceEnabled?stage:Math.min(stage,4)); }
    public int totalStages(){return boss && existenceEnabled?5:4;}
    public boolean finished() { return stage >= totalStages(); }
    public boolean pending() { return running && !finished(); }
}
