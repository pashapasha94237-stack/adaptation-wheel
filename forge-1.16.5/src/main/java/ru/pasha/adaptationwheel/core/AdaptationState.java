package ru.pasha.adaptationwheel.core;
import java.util.*;

/** Pure progression. Work is measured in unaccelerated server ticks. */
public final class AdaptationState {
    public static final int DURATION_TICKS = 400;
    public static final int MAX_BONUS_HITS = 1_000_000;
    private static final int[] REDUCTIONS = {0,20,40,70,100,100};
    private boolean enabled = true;
    private boolean nightVisionEnabled = true;
    private long revision;
    private final Map<String,Progress> entries = new LinkedHashMap<>();
    private static final class Progress {
        boolean enabled = true, boss, running;
        int stage, hits;
        double work;
        AdaptationProfile profile;
    }
public static final class Snapshot {private final String key;
private final boolean enabled;
private final boolean boss;
private final int stage;
private final double work;
private final int hits;
private final boolean running;
private final AdaptationProfile profile;
public Snapshot(String key,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,AdaptationProfile profile){this.key=key;this.enabled=enabled;this.boss=boss;this.stage=stage;this.work=work;this.hits=hits;this.running=running;this.profile=profile==null?AdaptationProfile.forKey(key,boss):profile;}public String key(){return key;}
public boolean enabled(){return enabled;}
public boolean boss(){return boss;}
public int stage(){return stage;}
public double work(){return work;}
public int hits(){return hits;}
public boolean running(){return running;}
public AdaptationProfile profile(){return profile;}
public Snapshot(String key,boolean enabled,boolean boss,int stage,double work,int hits,boolean running){this(key,enabled,boss,stage,work,hits,running,AdaptationProfile.forKey(key,boss));}
public int duration(){return profile.duration(stage);} public double speed(){return speedFor(hits);}
public boolean finished(){return stage>=(boss?5:4);} public int reduction(){return reductionPercent(stage);}
public int remaining(){return finished()||!running?0:(int)Math.ceil(Math.max(0,duration()-work));}
public int estimatedTicks(){return finished()||!running?0:(int)Math.ceil(Math.max(0,duration()-work)/speed());}
@Override public boolean equals(Object value){if(!(value instanceof Snapshot))return false;Snapshot other=(Snapshot)value;return java.util.Objects.equals(key,other.key) && enabled==other.enabled && boss==other.boss && stage==other.stage && Double.compare(work,other.work)==0 && hits==other.hits && running==other.running && java.util.Objects.equals(profile,other.profile);} @Override public int hashCode(){return java.util.Objects.hash(key,enabled,boss,stage,work,hits,running,profile);}}
    public static int durationTicks(int stage) { return (20 + 30 * Math.max(0,Math.min(4,stage))) * 20; }
    public static int reductionPercent(int stage) { return REDUCTIONS[Math.max(0,Math.min(5,stage))]; }
    public static double speedFor(int hits) { return 1.0 + 0.30 * Math.max(0,Math.min(MAX_BONUS_HITS,hits)); }
    public boolean enabled() { return enabled; }
    public boolean nightVisionEnabled() { return nightVisionEnabled; }
    public boolean nightVisionUnlocked() { return stage(AdaptationProfile.DARK_KEY)>=4; }
    public void setNightVisionEnabled(boolean value) { nightVisionEnabled=value; }
    public void setEnabled(boolean value) { enabled = value; }
    public long revision() { return revision; }
    public List<Snapshot> snapshots() {
        return entries.entrySet().stream().map(e -> {
            Progress p = e.getValue();
            return new Snapshot(e.getKey(),p.enabled,p.boss,p.stage,p.work,p.hits,p.running,p.profile);
        }).collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),Compat::copyList));
    }
    public boolean setCategoryEnabled(String key, boolean value) {
        Progress p = entries.get(key);
        if (p == null) return false;
        p.enabled = value;
        return true;
    }
    public boolean categoryEnabled(String key) { Progress p = entries.get(key); return p == null || p.enabled; }
    public int stage(String key) { Progress p = entries.get(key); return p == null ? 0 : p.stage; }
    public double damageMultiplier(String key) {
        return enabled && categoryEnabled(key) ? 1.0 - reductionPercent(stage(key)) / 100.0 : 1.0;
    }
    public boolean isImmune(String key) { return key != null && damageMultiplier(key) == 0.0; }
    public boolean existenceAdapted(String key) { return enabled && categoryEnabled(key) && stage(key) >= 5; }

    /** One received hit, or a blocked boss hit during the existence stage. */
    public boolean hit(String key, boolean boss) {
        return hit(key,boss,AdaptationProfile.forKey(key,boss));
    }
    public boolean hit(String key, boolean boss, AdaptationProfile profile) {
        if (!enabled || !valid(key)) return false;
        Progress p = entries.computeIfAbsent(key, ignored -> new Progress());
        if(p.profile==null) p.profile=profile==null ? AdaptationProfile.forKey(key,boss) : profile;
        if(boss && !p.boss) p.profile=AdaptationProfile.MAGIC;
        p.boss |= boss;
        if (!p.enabled || p.stage >= (p.boss ? 5 : 4)) return false;
        if (!p.running) { p.running = true; return true; }
        p.hits = Math.min(MAX_BONUS_HITS,p.hits + 1);
        return false;
    }
    public boolean begin(String key) { return hit(key,false); }
    public List<String> tick() { return tick(true); }
    public List<String> tick(boolean equipped) {
        return tick(equipped,Compat.set());
    }
    public List<String> tick(boolean equipped,Set<String> newlyStarted) {
        if (!enabled || !equipped) return Compat.list();
        List<String> completed = new ArrayList<>();
        entries.forEach((key,p) -> {
            if (!p.enabled || !p.running || newlyStarted.contains(key) || p.stage >= (p.boss ? 5 : 4)) return;
            p.work += speedFor(p.hits);
            if (p.work + 1.0E-7 >= p.profile.duration(p.stage)) {
                p.stage++; p.work = 0; p.hits = 0;
                p.running = p.stage < (p.boss ? 5 : 4);
                revision++; completed.add(key);
            }
        });
        return Compat.copyList(completed);
    }
    public void clearPending() { entries.values().forEach(p -> { p.work = 0; p.hits = 0; p.running = false; }); }
    public void reset() { entries.clear(); } // Resets are not completions: do not rotate.
    public Set<String> completed() {
        Set<String> result = new LinkedHashSet<>();
        entries.forEach((key,p) -> { if (p.stage >= 4) result.add(key); });
        return Collections.unmodifiableSet(result);
    }
    public Map<String,Integer> pending() {
        Map<String,Integer> result = new LinkedHashMap<>();
        entries.forEach((key,p) -> { if (p.running) result.put(key,(int)p.work); });
        return Collections.unmodifiableMap(result);
    }
    public static AdaptationState restore(boolean enabled, List<Snapshot> snapshots, long revision) {
        AdaptationState state = new AdaptationState();
        state.enabled = enabled; state.revision = Math.max(0,revision);
        for (Snapshot s : snapshots) {
            if (!valid(s.key())) continue;
            Progress p = new Progress();
            p.enabled = s.enabled(); p.boss = s.boss();
            p.profile = s.profile();
            p.stage = Math.max(0,Math.min(p.boss ? 5 : 4,s.stage()));
            p.hits = Math.max(0,Math.min(MAX_BONUS_HITS,s.hits()));
            p.work = Double.isFinite(s.work()) ? Math.max(0,Math.min(p.profile.duration(p.stage)-1,s.work())) : 0;
            p.running = s.running() && p.stage < (p.boss ? 5 : 4);
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
    private static boolean valid(String key) { return key != null && !key.trim().isEmpty() && key.length() <= 256; }
}
