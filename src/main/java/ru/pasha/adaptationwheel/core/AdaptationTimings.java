package ru.pasha.adaptationwheel.core;

import java.util.*;

/** Atomic timing policy. Forge loads it on the server; clients receive explicit durations. */
public final class AdaptationTimings {
    public static final int DEFAULT_HIT_ACCELERATION_PERCENT=30,MAX_HIT_ACCELERATION_PERCENT=1000;
    private record Policy(Map<AdaptationProfile,StageDurations> categories,Map<String,List<Integer>> overrides,long revision,int hitAccelerationPercent,StageProtections protections,boolean healAfterStage,boolean existenceEnabled,boolean oneHitKillEnabled) {}
    private static volatile Policy policy=new Policy(Map.of(),Map.of(),0,DEFAULT_HIT_ACCELERATION_PERCENT,StageProtections.DEFAULTS,true,true,true);
    private static volatile boolean analysisEnabled=true,elementExperience=true;
    private AdaptationTimings() {}
    public static StageDurations forSource(String key,AdaptationProfile profile) {
        Policy current=policy;
        StageDurations base=current.categories.getOrDefault(profile,StageDurations.defaults(profile));
        if(MetaMechanic.fromKey(key)!=null && base.equals(StageDurations.defaults(profile)))base=MetaMechanic.TIMES;
        List<Integer> override=current.overrides.get(key);
        if(override==null)return base;
        int[] values=new int[5];
        for(int i=0;i<5;i++)values[i]=override.get(i)==0?base.ticks(i):override.get(i)*20;
        return new StageDurations(values[0],values[1],values[2],values[3],values[4]);
    }
    public static int duration(String key,AdaptationProfile profile,int stage){return forSource(key,profile).ticks(stage);}
    /** Manual non-default category stages and source overrides always outrank learned suggestions. */
    public static StageDurations forAnalysis(String key,AdaptationProfile profile,StageDurations suggested) {
        if(!analysisEnabled || suggested==null)return forSource(key,profile);
        Policy current=policy;
        StageDurations category=current.categories.get(profile),defaults=StageDurations.defaults(profile);
        List<Integer> source=current.overrides.get(key);
        int[] values=new int[5];
        for(int i=0;i<5;i++) {
            values[i]=category!=null && category.ticks(i)!=defaults.ticks(i)?category.ticks(i):suggested.ticks(i);
            if(source!=null && source.get(i)>0)values[i]=source.get(i)*20;
        }
        return new StageDurations(values[0],values[1],values[2],values[3],values[4]);
    }
    /** Allocation-free progression path. frozenSuggestion is raw learned work, before manual settings. */
    public static int analysisDuration(String key,AdaptationProfile profile,StageDurations suggested,int stage,int frozenSuggestion) {
        if(!analysisEnabled || suggested==null)return duration(key,profile,stage);
        Policy current=policy;int selected=Math.max(0,Math.min(4,stage));
        List<Integer> source=current.overrides.get(key);
        if(source!=null && source.get(selected)>0)return source.get(selected)*20;
        StageDurations category=current.categories.get(profile);
        if(category!=null && category.ticks(selected)!=profile.duration(selected))return category.ticks(selected);
        return frozenSuggestion>0?frozenSuggestion:suggested.ticks(selected);
    }
    /** One stage of a schedule moved from one category to another: the same share of that category's own time. */
    public static int scaled(int ticks,AdaptationProfile from,AdaptationProfile to,int stage) {
        if(from==to)return ticks;
        long moved=Math.round((double)ticks*to.duration(stage)/from.duration(stage));
        return (int)Math.max(20,Math.min(StageDurations.MAX_SECONDS*20L,moved));
    }
    /** The analyzer's schedule for a row the player put in another category; what it measured about the source still counts. */
    public static StageDurations corrected(StageDurations suggested,AdaptationProfile from,AdaptationProfile to) {
        if(suggested==null || from==to)return suggested;
        return new StageDurations(scaled(suggested.first(),from,to,0),scaled(suggested.second(),from,to,1),scaled(suggested.third(),from,to,2),
                scaled(suggested.fourth(),from,to,3),scaled(suggested.existence(),from,to,4));
    }
    public static boolean analysisEnabled(){return analysisEnabled;}
    public static void setAnalysisEnabled(boolean enabled){analysisEnabled=enabled;}
    public static boolean elementExperience(){return elementExperience;}
    public static void setElementExperience(boolean enabled){elementExperience=enabled;}
    public static long revision(){return policy.revision;}
    public static int hitAccelerationPercent(){return policy.hitAccelerationPercent;}
    public static StageProtections protections(){return policy.protections;}
    public static boolean healAfterStage(){return policy.healAfterStage;}
    public static boolean existenceEnabled(){return policy.existenceEnabled;}
    public static boolean oneHitKillEnabled(){return policy.oneHitKillEnabled;}
    public static void replace(Map<AdaptationProfile,StageDurations> categories,Map<String,List<Integer>> overrides) {
        replace(categories,overrides,DEFAULT_HIT_ACCELERATION_PERCENT);
    }
    public static void replace(Map<AdaptationProfile,StageDurations> categories,Map<String,List<Integer>> overrides,int hitAccelerationPercent) {
        replace(categories,overrides,hitAccelerationPercent,StageProtections.DEFAULTS);
    }
    public static void replace(Map<AdaptationProfile,StageDurations> categories,Map<String,List<Integer>> overrides,int hitAccelerationPercent,StageProtections protections) {
        replace(categories,overrides,hitAccelerationPercent,protections,true);
    }
    public static void replace(Map<AdaptationProfile,StageDurations> categories,Map<String,List<Integer>> overrides,int hitAccelerationPercent,StageProtections protections,boolean healAfterStage) {
        replace(categories,overrides,hitAccelerationPercent,protections,healAfterStage,true,true);
    }
    public static synchronized void replace(Map<AdaptationProfile,StageDurations> categories,Map<String,List<Integer>> overrides,int hitAccelerationPercent,StageProtections protections,boolean healAfterStage,boolean existenceEnabled,boolean oneHitKillEnabled) {
        Objects.requireNonNull(protections);
        if(hitAccelerationPercent<0 || hitAccelerationPercent>MAX_HIT_ACCELERATION_PERCENT)throw new IllegalArgumentException("Hit acceleration percentage out of range");
        Map<String,List<Integer>> checked=new HashMap<>();
        overrides.forEach((key,values)->{
            if(key==null || key.isBlank() || values.size()!=5 || values.stream().anyMatch(v->v==null||v<0||v>StageDurations.MAX_SECONDS))
                throw new IllegalArgumentException("Invalid source timing override");
            checked.put(key,List.copyOf(values));
        });
        policy=new Policy(Map.copyOf(categories),Map.copyOf(checked),policy.revision+1,hitAccelerationPercent,protections,healAfterStage,existenceEnabled,oneHitKillEnabled);
    }
    public static void defaults(){analysisEnabled=true;elementExperience=true;replace(Map.of(),Map.of());}
}
