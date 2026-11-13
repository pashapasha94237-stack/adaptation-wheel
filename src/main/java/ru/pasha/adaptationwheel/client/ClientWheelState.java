package ru.pasha.adaptationwheel.client;
import net.minecraft.util.Mth;
import ru.pasha.adaptationwheel.net.WheelEntry;
import ru.pasha.adaptationwheel.net.CatalogueEntry;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import java.util.*;

public final class ClientWheelState {
    private static final double MAX_TRANSIT_COMPENSATION_TICKS=5;
    private static final Map<Integer,State> PLAYERS=new HashMap<>();
    private static long clientTicks;
    private static List<CatalogueEntry> catalogue=List.of();
    private static long catalogueBatch=-1;
    private static final Map<Integer,List<CatalogueEntry>> catalogueChunks=new HashMap<>();
    private ClientWheelState() {}
    public static final class State {
        private final boolean enabled,equipped,nightVision;
        private final List<WheelEntry> entries;
        private final long revision,receivedAt;
        private final long serverTick;
        private final double serverClockOffset,workAt;
        private final long completedAt;
        private final float angleStart,angleTarget;
        private final float existenceAngle;
        private final int queuedSteps;
        private final List<Completion> completions;
        private final Map<String,Long> hitFlashes;
        private State(boolean enabled,boolean equipped,boolean nightVision,List<WheelEntry> entries,long revision,long receivedAt,
                      long completedAt,float angleStart,float angleTarget,int queuedSteps,float existenceAngle,
                      long serverTick,double serverClockOffset,List<Completion> completions,Map<String,Long> hitFlashes) {
            this.enabled=enabled;this.equipped=equipped;this.nightVision=nightVision;this.entries=List.copyOf(entries);this.revision=revision;
            this.receivedAt=receivedAt;this.completedAt=completedAt;this.angleStart=angleStart;this.angleTarget=angleTarget;this.queuedSteps=queuedSteps;
            this.existenceAngle=existenceAngle;this.completions=List.copyOf(completions);this.hitFlashes=Map.copyOf(hitFlashes);
            this.serverTick=serverTick;this.serverClockOffset=serverClockOffset;
            this.workAt=serverTick+serverClockOffset;
        }
        public boolean enabled() { return enabled; }
        public boolean equipped() { return equipped; }
        public boolean nightVision() { return nightVision; }
        public boolean nightVisionUnlocked() { return entries.stream().anyMatch(e -> e.key().equals(AdaptationProfile.DARK_KEY) && e.stage()>=4); }
        public List<WheelEntry> entries() { return entries; }
        public int completedCount() { return (int)entries.stream().filter(p -> p.stage()>=4).count(); }
        public float remainingWork(WheelEntry entry,float partialTick) {
            // Apply the multiplier once to elapsed time, never to the remaining duration.
            // Predict continuously between packets; the old ten-tick cap froze the clock
            // whenever a heartbeat was late. The server timestamp also prevents a delayed
            // heartbeat from rewinding it to the instant that packet was generated.
            double elapsed=enabled && equipped && entry.enabled() && entry.pending() && entry.progressing()
                ? Math.max(0,(double)clientTicks+partialTick-workAt) : 0;
            return (float)Math.max(0,entry.duration()-entry.work()-elapsed*entry.speed());
        }
        /** Ticks of adaptation left at ×1: what the menu and HUD show. A hit does not change it, it makes it run faster. */
        public float remaining(WheelEntry entry,float partialTick) { return remainingWork(entry,partialTick); }
        /** Real ticks left: the remaining work at the current speed, so 12.4 s of work at ×1.30 is 9.5 s; only orders rows. */
        public float realRemaining(WheelEntry entry,float partialTick) { return remainingWork(entry,partialTick)/(float)Math.max(0.01,entry.speed()); }
        /** Stages reached in the last few seconds, newest first; the corner panel lights their rows. */
        public List<Completion> completions() { return completions; }
        /** Ticks since a repeat hit sped this row up, or MAX_VALUE when none is lit. */
        public float hitFlashAge(String key,float partialTick) {
            Long at=hitFlashes.get(key);
            float age=at==null ? Float.MAX_VALUE : clientTicks+partialTick-at;
            return age<HIT_FLASH_TICKS ? age : Float.MAX_VALUE;
        }
        public float completionAge(float partialTick) { return completedAt<0 ? Float.MAX_VALUE : clientTicks+partialTick-completedAt; }
        public boolean existenceAdapting() {
            return enabled && equipped && entries.stream().anyMatch(e -> e.enabled() && e.boss()
                && e.stage()==4 && e.pending() && e.progressing());
        }
        private float existenceTurn(float partialTick) {
            // Keep the angle across sync packets, pauses and completion. One revolution takes four seconds.
            return existenceAngle+(existenceAdapting() ? Math.max(0,clientTicks+partialTick-receivedAt)*(Mth.TWO_PI/80) : 0);
        }
        public float turn(float partialTick) {
            float spin=existenceTurn(partialTick);
            if (queuedSteps<=0) return angleTarget+spin;
            float elapsed=Math.max(0,completionAge(partialTick));
            int done=Math.min(queuedSteps,(int)(elapsed/18));
            if (done>=queuedSteps) return angleTarget+spin;
            float t=Mth.clamp((elapsed-done*18)/18,0,1);
            float eased=1-(1-t)*(1-t)*(1-t);
            return angleStart+(done+eased)*(Mth.PI/4)+spin;
        }
    }
    public static void accept(int id,boolean enabled,boolean equipped,boolean nightVision,List<WheelEntry> entries,long revision) {
        accept(id,enabled,equipped,nightVision,entries,revision,clientTicks);
    }
    public static void accept(int id,boolean enabled,boolean equipped,boolean nightVision,List<WheelEntry> entries,long revision,long serverTick) {
        State old=PLAYERS.get(id);
        if(old!=null && serverTick<old.serverTick) return;
        // Absorb short delivery jitter without rewinding. Bound compensation to
        // 250 ms so an overloaded server cannot leave the client clock far ahead.
        double offset=(double)clientTicks-serverTick;
        if(old!=null) offset=Math.max(offset-MAX_TRANSIT_COMPENSATION_TICKS,Math.min(offset,old.serverClockOffset));
        long completedAt=old==null ? -1 : old.completedAt;
        float start=old==null ? (revision%8)*(Mth.PI/4) : old.angleStart;
        float target=old==null ? start : old.angleTarget;
        int queued=old==null ? 0 : old.queuedSteps;
        if (old!=null && revision>old.revision) {
            // Preserve unfinished turns: simultaneous completed stages each produce one discrete 45-degree step.
            long delta=Math.min(1024,revision-old.revision);
            if (old.queuedSteps>0 && old.completionAge(0)<old.queuedSteps*18) {
                queued=old.queuedSteps+(int)delta;
                target=old.angleTarget+delta*(Mth.PI/4);
            } else {
                start=old.angleTarget;
                queued=(int)delta;
                target=start+delta*(Mth.PI/4);
                completedAt=clientTicks;
            }
        }
        float existenceAngle=old==null ? 0 : old.existenceTurn(0)%Mth.TWO_PI;
        PLAYERS.put(id,new State(enabled,equipped,nightVision,entries,revision,clientTicks,completedAt,start,target,queued,existenceAngle,serverTick,offset,completions(old,entries),hitFlashes(old,entries)));
    }
    /** A stage reached: the adaptation, the stage it reached and the client tick of the packet that said so. */
    public record Completion(String key,int stage,long at) {
        public float age(float partialTick) { return clientTicks+partialTick-at; }
    }
    /** How long a reached stage stays lit in the corner panel: three seconds. */
    public static final int COMPLETION_TICKS=60;
    /** Keeps the recent completions of the old state and adds every stage that went up in this packet. */
    private static List<Completion> completions(State old,List<WheelEntry> entries) {
        if(old==null) return List.of();
        List<Completion> result=new ArrayList<>();
        Map<String,Integer> before=new HashMap<>();
        for(var e:old.entries) before.put(e.key(),e.stage());
        for(var e:entries) {
            Integer was=before.get(e.key());
            if(e.stage()>(was==null?0:was)) result.add(new Completion(e.key(),e.stage(),clientTicks));
        }
        for(var c:old.completions)
            if(clientTicks-c.at()<COMPLETION_TICKS && result.stream().noneMatch(n->n.key().equals(c.key()))) result.add(c);
        return result;
    }
    /** How long a repeat hit stays lit as «+N% скорости»: a second and a half. */
    public static final int HIT_FLASH_TICKS=30;
    /** Rows whose hit count went up at the same stage in the last packets: the client tick of the newest such packet by key. */
    private static Map<String,Long> hitFlashes(State old,List<WheelEntry> entries) {
        if(old==null) return Map.of();
        Map<String,Long> result=new HashMap<>();
        Map<String,WheelEntry> before=new HashMap<>();
        for(var e:old.entries) before.put(e.key(),e);
        for(var e:entries) {
            WheelEntry was=before.get(e.key());
            if(was!=null && was.stage()==e.stage() && e.hits()>was.hits()) result.put(e.key(),clientTicks);
        }
        old.hitFlashes.forEach((k,at)->{ if(clientTicks-at<HIT_FLASH_TICKS) result.putIfAbsent(k,at); });
        return result;
    }
    public static State get(int id) { return PLAYERS.get(id); }
    public static List<CatalogueEntry> catalogue() { return catalogue; }
    public static void acceptCatalogue(long batch,int index,int count,List<CatalogueEntry> entries) {
        if(batch<catalogueBatch) return;
        if(batch!=catalogueBatch) { catalogueBatch=batch;catalogueChunks.clear(); }
        catalogueChunks.put(index,List.copyOf(entries));
        if(catalogueChunks.size()!=count) return;
        List<CatalogueEntry> all=new ArrayList<>();
        for(int i=0;i<count;i++) {
            var chunk=catalogueChunks.get(i);
            if(chunk==null) return;
            all.addAll(chunk);
        }
        catalogue=List.copyOf(all);
        catalogueChunks.clear();
    }
    public static float time(float partialTick) { return clientTicks+partialTick; }
    static void tick() { clientTicks++; }
    static void remove(int id) { PLAYERS.remove(id); }
    static void clear() { PLAYERS.clear();clientTicks=0;catalogue=List.of();catalogueChunks.clear();catalogueBatch=-1; }
}
