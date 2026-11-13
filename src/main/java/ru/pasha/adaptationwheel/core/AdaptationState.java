package ru.pasha.adaptationwheel.core;
import java.util.*;

/** Pure progression. Work is measured in unaccelerated server ticks. */
public final class AdaptationState {
    public static final int DURATION_TICKS = 400;
    public static final int MAX_BONUS_HITS = 1_000_000;
    private boolean enabled = true;
    private boolean nightVisionEnabled = true;
    private long revision;
    private final Map<String,Progress> entries = new LinkedHashMap<>();
    private static final class Progress {
        boolean enabled = true, boss, running;
        int stage, hits;
        double work;
        /** The analyzer's verdict; manual is the player's word over it, or null. */
        AdaptationProfile profile,manual;
        StageDurations analyzedDurations;
        int activeDuration,analysisConfidence,analysisObservations;
        String analysisReason="";
    }
    public record Snapshot(String key, boolean enabled, boolean boss, int stage, double work, int hits, boolean running, AdaptationProfile profile,
                           StageDurations analyzedDurations,int activeDuration,int analysisConfidence,String analysisReason,int analysisObservations,AdaptationProfile manualProfile) {
        public Snapshot(String key, boolean enabled, boolean boss, int stage, double work, int hits, boolean running, AdaptationProfile profile,
                        StageDurations analyzedDurations,int activeDuration,int analysisConfidence,String analysisReason,int analysisObservations) {
            this(key,enabled,boss,stage,work,hits,running,profile,analyzedDurations,activeDuration,analysisConfidence,analysisReason,analysisObservations,null);
        }
        public Snapshot(String key, boolean enabled, boolean boss, int stage, double work, int hits, boolean running,AdaptationProfile profile) {
            this(key,enabled,boss,stage,work,hits,running,profile,null,0,0,"",0);
        }
        public Snapshot(String key, boolean enabled, boolean boss, int stage, double work, int hits, boolean running) {
            this(key,enabled,boss,stage,work,hits,running,AdaptationProfile.forKey(key,boss));
        }
        public Snapshot {
            if(profile==null) profile=AdaptationProfile.forKey(key,boss);
            if(activeDuration<20 || activeDuration>StageDurations.MAX_SECONDS*20)activeDuration=0;
            analysisConfidence=Math.max(0,Math.min(100,analysisConfidence));
            analysisObservations=Math.max(0,Math.min(AdaptationAnalysis.MAX_OBSERVATIONS,analysisObservations));
            analysisReason=analysisReason==null?"":analysisReason.substring(0,Math.min(64,analysisReason.length()));
        }
        /** The category the row shows and times by: the player's if there is one, else the analyzer's {@link #profile}. */
        public AdaptationProfile category() { return manualProfile==null?profile:manualProfile; }
        /** Effective server schedule, including a frozen current suggestion and live manual overrides. */
        public StageDurations timings() {
            StageDurations proposed=AdaptationTimings.corrected(analyzedDurations,profile,category());
            if(proposed!=null && running && activeDuration>0)proposed=withStage(proposed,stage,activeDuration);
            return AdaptationTimings.forAnalysis(key,category(),proposed);
        }
        public int duration() { return AdaptationTimings.analysisDuration(key,category(),AdaptationTimings.corrected(analyzedDurations,profile,category()),stage,running?activeDuration:0); }
        public double speed() { return speedFor(hits); }
        public boolean finished() { return stage >= (boss ? 5 : 4); }
        public int reduction() { return reductionPercent(stage); }
        /** Adaptation time: a hit changes its rate, never this clock's value. */
        public int remaining() { return finished() || !running || !canProgress(boss,stage) ? 0 : (int)Math.ceil(Math.max(0,duration()-work)); }
        /** Estimate in real server ticks, useful for scheduling rather than HUD display. */
        public int estimatedTicks() { return finished() || !running || !canProgress(boss,stage) ? 0 : (int)Math.ceil(Math.max(0,duration()-work)/speed()); }
    }
    public static int durationTicks(int stage) { return (20 + 30 * Math.max(0,Math.min(4,stage))) * 20; }
    public static int reductionPercent(int stage) { return AdaptationTimings.protections().percent(!AdaptationTimings.existenceEnabled()?Math.min(4,stage):stage); }
    public static boolean canProgress(boolean boss,int stage){return !boss || stage<4 || AdaptationTimings.existenceEnabled();}
    public static double speedFor(int hits) { return speedFor(hits,AdaptationTimings.hitAccelerationPercent()); }
    public static double speedFor(int hits,int percent) {
        return 1.0 + Math.max(0,Math.min(AdaptationTimings.MAX_HIT_ACCELERATION_PERCENT,percent))/100.0 * Math.max(0,Math.min(MAX_BONUS_HITS,hits));
    }
    public boolean enabled() { return enabled; }
    public boolean nightVisionEnabled() { return nightVisionEnabled; }
    public boolean nightVisionUnlocked() { return stage(AdaptationProfile.DARK_KEY)>=4; }
    public void setNightVisionEnabled(boolean value) { nightVisionEnabled=value; }
    public void setEnabled(boolean value) { enabled = value; }
    public long revision() { return revision; }
    public List<Snapshot> snapshots() {
        return entries.entrySet().stream().map(e -> {
            Progress p = e.getValue();
            return snapshot(e.getKey(),p);
        }).toList();
    }
    /** A combat receipt needs one entry, not a copy of the player's whole catalogue. */
    public Snapshot snapshot(String key) {
        Progress progress=entries.get(key);
        return progress==null?null:snapshot(key,progress);
    }
    private static Snapshot snapshot(String key,Progress p) {
        return new Snapshot(key,p.enabled,p.boss,p.stage,p.work,p.hits,p.running,p.profile,
                p.analyzedDurations,p.activeDuration,p.analysisConfidence,p.analysisReason,p.analysisObservations,p.manual);
    }
    private static AdaptationProfile category(Progress p) { return p.manual==null?p.profile:p.manual; }
    /** The analyzer's future stages as this row times them; null without analysis. */
    private static StageDurations planned(Progress p) { return AdaptationTimings.corrected(p.analyzedDurations,p.profile,category(p)); }
    private static StageDurations withStage(StageDurations base,int stage,int ticks) {
        int[] values={base.first(),base.second(),base.third(),base.fourth(),base.existence()};
        values[Math.max(0,Math.min(4,stage))]=ticks;
        return new StageDurations(values[0],values[1],values[2],values[3],values[4]);
    }
    /** New observations affect future stages; they never shrink or reset current adaptation work. */
    public boolean applyAnalysis(String key,AdaptationAnalysis.Decision decision) {
        if(!enabled || !valid(key) || decision==null || !decision.eligible() || !AdaptationTimings.analysisEnabled())return false;
        Progress p=entries.computeIfAbsent(key,ignored->new Progress());
        if(!p.enabled)return false;
        if(p.running && p.activeDuration==0) {
            p.activeDuration=p.analyzedDurations==null?AdaptationTimings.duration(key,category(p),p.stage):planned(p).ticks(p.stage);
        }
        p.profile=decision.profile();p.analyzedDurations=decision.durations();
        p.analysisConfidence=decision.confidence();p.analysisReason=decision.reason();p.analysisObservations=decision.observations();
        return true;
    }
    /**
     * The player's word for a row's category; null gives it back to the analyzer. The analyzer's verdict stays underneath
     * and keeps learning. Only the name and the stage times follow: a running stage keeps its share of the work, so
     * going back and forth never skips or repeats anything, and stages and protection are untouched.
     * @return whether the row changed
     */
    public boolean setManualCategory(String key,AdaptationProfile category) {
        Progress p=entries.get(key);
        if(p==null || MetaMechanic.fromKey(key)!=null)return false;
        if(category==p.profile)category=null;
        if(category==p.manual)return false;
        AdaptationProfile before=category(p);
        double was=snapshot(key,p).duration();
        p.manual=category;
        if(p.activeDuration>0)p.activeDuration=AdaptationTimings.scaled(p.activeDuration,before,category(p),p.stage);
        double now=snapshot(key,p).duration();
        p.work=Math.min(Math.nextDown(now),p.work*now/was);
        return true;
    }
    /** Finished sources the analyzer puts in this category, the given one aside: a manual fix leaves other rows' times alone. */
    public int mastered(AdaptationProfile profile,String except) {
        int count=0;for(var e:entries.entrySet())if(e.getValue().profile==profile&&e.getValue().stage>=4&&!e.getKey().equals(except))count++;
        return count;
    }
    private Map<AdaptationProfile,Integer> masteredByProfile() {
        Map<AdaptationProfile,Integer> counts=new EnumMap<>(AdaptationProfile.class);
        for(Progress p:entries.values())if(p.profile!=null&&p.stage>=4)counts.merge(p.profile,1,Integer::sum);
        return counts;
    }
    /**
     * Element experience as a starting speed in per cent (0..35): only for a row the analyzer has timed, by its own
     * verdict (a manual fix moves no experience), and only while the server allows it.
     */
    public int experiencePercent(String key) {
        Progress p=entries.get(key);
        return p==null?0:experiencePercent(p,mastered(p.profile,key));
    }
    /** The same for every row at once (a sync), counting the finished sources only once. */
    public Map<String,Integer> experiencePercents() {
        Map<AdaptationProfile,Integer> mastered=masteredByProfile();
        Map<String,Integer> result=new HashMap<>();
        entries.forEach((key,p) -> {
            int percent=experiencePercent(p,mastered.getOrDefault(p.profile,0)-(p.stage>=4?1:0));
            if(percent>0)result.put(key,percent);
        });
        return result;
    }
    private static int experiencePercent(Progress p,int mastered) {
        if(p.analyzedDurations==null||!AdaptationTimings.elementExperience()||!AdaptationTimings.analysisEnabled())return 0;
        return ElementExperience.percent(p.profile,mastered);
    }
    /** Hits plus element experience: what one server tick of this row adds to its work. */
    public static double speedFor(int hits,int percent,int experiencePercent) {
        return speedFor(hits,percent)+Math.max(0,Math.min(ElementExperience.MOST_PERCENT,experiencePercent))/100.0;
    }
    public boolean setCategoryEnabled(String key, boolean value) {
        Progress p = entries.get(key);
        if (p == null) return false;
        p.enabled = value;
        return true;
    }
    /** A command may also switch off an adaptation that has not started yet, so that it never starts. */
    public boolean setCategoryEnabled(String key, boolean value, AdaptationProfile profile) {
        if (!valid(key)) return false;
        if (!value) entries.computeIfAbsent(key, ignored -> new Progress()).profile = profile(key, profile);
        return setCategoryEnabled(key,value);
    }
    /**
     * A command sets the stage outright (at most IV): the work towards the next stage is dropped, and the next refusal
     * or hit starts it again. A higher stage counts as a completion, so the wheel turns.
     * @return the stage before
     */
    public int setStage(String key, int stage, AdaptationProfile profile) {
        if (!valid(key)) return -1;
        Progress p = entries.computeIfAbsent(key, ignored -> new Progress());
        p.profile = profile(key, profile);
        int before = p.stage;
        p.stage = Math.max(0, Math.min(4, stage));
        p.work = 0; p.hits = 0; p.running = false; p.activeDuration = 0;
        if (p.stage > before) revision++;
        return before;
    }
    /** A command forgets one adaptation; like reset, this is not a completion. */
    public boolean forget(String key) { return entries.remove(key) != null; }
    private AdaptationProfile profile(String key, AdaptationProfile given) {
        Progress p = entries.get(key);
        return p != null && p.profile != null ? p.profile : given != null ? given : AdaptationProfile.forKey(key,false);
    }
    public boolean categoryEnabled(String key) { Progress p = entries.get(key); return p == null || p.enabled; }
    public int stage(String key) { Progress p = entries.get(key); return p == null ? 0 : p.stage; }
    public double damageMultiplier(String key) {
        return enabled && categoryEnabled(key) ? 1.0 - reductionPercent(stage(key)) / 100.0 : 1.0;
    }
    public boolean isImmune(String key) { return key != null && damageMultiplier(key) == 0.0; }
    public boolean existenceAdapted(String key) { return enabled && AdaptationTimings.existenceEnabled() && categoryEnabled(key) && stage(key) >= 5; }

    /** One received hit, or a blocked boss hit during the existence stage. */
    public boolean hit(String key, boolean boss) {
        return hit(key,boss,AdaptationProfile.forKey(key,boss));
    }
    public boolean hit(String key, boolean boss, AdaptationProfile profile) {
        if (!enabled || !valid(key)) return false;
        Progress p = entries.computeIfAbsent(key, ignored -> new Progress());
        if(p.profile==null) p.profile=profile==null ? AdaptationProfile.forKey(key,boss) : profile;
        if(boss && !p.boss && p.analyzedDurations==null && key.startsWith("mob:")) p.profile=AdaptationProfile.MAGIC;
        p.boss |= boss;
        if (!p.enabled || p.stage >= (p.boss ? 5 : 4) || !canProgress(p.boss,p.stage)) return false;
        if (!p.running) { p.running = true;p.activeDuration=p.analyzedDurations==null?0:planned(p).ticks(p.stage); return true; }
        p.hits = Math.min(MAX_BONUS_HITS,p.hits + 1);
        return false;
    }
    public boolean begin(String key) { return hit(key,false); }
    public List<String> tick() { return tick(true); }
    public List<String> tick(boolean equipped) {
        return tick(equipped,Set.of());
    }
    public List<String> tick(boolean equipped,Set<String> newlyStarted) {
        if (!enabled || !equipped) return List.of();
        List<String> completed = new ArrayList<>();
        Map<AdaptationProfile,Integer> masteredBefore = masteredByProfile();
        entries.forEach((key,p) -> {
            if (!p.enabled || !p.running || newlyStarted.contains(key) || p.stage >= (p.boss ? 5 : 4) || !canProgress(p.boss,p.stage)) return;
            p.work += speedFor(p.hits) + experiencePercent(p,masteredBefore.getOrDefault(p.profile,0) - (p.stage >= 4 ? 1 : 0)) / 100.0;
            if (p.work + 1.0E-7 >= AdaptationTimings.analysisDuration(key,category(p),planned(p),p.stage,p.activeDuration)) {
                p.stage++; p.work = 0; p.hits = 0;
                p.running = p.stage < (p.boss ? 5 : 4);
                p.activeDuration=p.running && p.analyzedDurations!=null?planned(p).ticks(p.stage):0;
                revision++; completed.add(key);
            }
        });
        return List.copyOf(completed);
    }
    public void clearPending() { entries.values().forEach(p -> { p.work = 0; p.hits = 0; p.running = false;p.activeDuration=0; }); }
    public void reset() { entries.clear(); } // Resets are not completions: do not rotate.
    /** A completion that is not an entry here (the Infinite Void's text): the wheel turns all the same. */
    public void turnOnce() { revision++; }
    public Set<String> completed() {
        Set<String> result = new LinkedHashSet<>();
        entries.forEach((key,p) -> { if (p.stage >= 4) result.add(key); });
        return Collections.unmodifiableSet(result);
    }
    public Map<String,Integer> pending() {
        Map<String,Integer> result = new LinkedHashMap<>();
        entries.forEach((key,p) -> { if (p.running && canProgress(p.boss,p.stage)) result.put(key,(int)p.work); });
        return Collections.unmodifiableMap(result);
    }
    public static AdaptationState restore(boolean enabled, List<Snapshot> snapshots, long revision) {
        AdaptationState state = new AdaptationState();
        state.enabled = enabled; state.revision = Math.max(0,revision);
        for (Snapshot s : snapshots) {
            if (!valid(s.key())) continue;
            Progress p = new Progress();
            p.enabled = s.enabled(); p.boss = s.boss();
            p.profile = s.profile(); p.manual = s.manualProfile();
            p.analyzedDurations=s.analyzedDurations();p.activeDuration=s.activeDuration();
            p.analysisConfidence=s.analysisConfidence();p.analysisReason=s.analysisReason();p.analysisObservations=s.analysisObservations();
            p.stage = Math.max(0,Math.min(p.boss ? 5 : 4,s.stage()));
            p.hits = Math.max(0,Math.min(MAX_BONUS_HITS,s.hits()));
            p.running = s.running() && p.stage < (p.boss ? 5 : 4);
            p.work = Double.isFinite(s.work()) ? Math.max(0,Math.min(Math.nextDown((double)snapshot(s.key(),p).duration()),s.work())) : 0;
            state.entries.put(s.key(),p);
        }
        return state;
    }
    /** v1 migration retains already earned full immunity. */
    public static AdaptationState restore(boolean enabled, Set<String> completed, Map<String,Integer> pending, long revision) {
        List<Snapshot> snapshots = new ArrayList<>();
        completed.forEach(key -> snapshots.add(new Snapshot(key,true,false,4,0,0,false)));
        pending.forEach((key,work) -> { if (!completed.contains(key)) snapshots.add(new Snapshot(key,true,false,0,work,0,true)); });
        return restore(enabled,snapshots,revision);
    }
    public static AdaptationState restore(boolean enabled, Set<String> completed, Map<String,Integer> pending) {
        return restore(enabled,completed,pending,completed.size());
    }
    private static boolean valid(String key) { return key != null && !key.isBlank() && key.length() <= 256; }
}
