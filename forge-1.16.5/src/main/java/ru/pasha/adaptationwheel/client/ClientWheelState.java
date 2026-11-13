package ru.pasha.adaptationwheel.client;
import net.minecraft.util.math.MathHelper;
import ru.pasha.adaptationwheel.net.WheelEntry;
import ru.pasha.adaptationwheel.net.CatalogueEntry;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import java.util.*;import ru.pasha.adaptationwheel.core.Compat;

public final class ClientWheelState {
    private static final Map<Integer,State> PLAYERS=new HashMap<>();
    private static long clientTicks;
    private static List<CatalogueEntry> catalogue=Compat.list();
    private static long catalogueBatch=-1;
    private static final Map<Integer,List<CatalogueEntry>> catalogueChunks=new HashMap<>();
    private ClientWheelState() {}
    public static final class State {
        private final boolean enabled,equipped,nightVision;
        private final List<WheelEntry> entries;
        private final long revision,receivedAt;
        private final long completedAt;
        private final float angleStart,angleTarget;
        private final int queuedSteps;
        private State(boolean enabled,boolean equipped,boolean nightVision,List<WheelEntry> entries,long revision,long receivedAt,
                      long completedAt,float angleStart,float angleTarget,int queuedSteps) {
            this.enabled=enabled;this.equipped=equipped;this.nightVision=nightVision;this.entries=Compat.copyList(entries);this.revision=revision;
            this.receivedAt=receivedAt;this.completedAt=completedAt;this.angleStart=angleStart;this.angleTarget=angleTarget;this.queuedSteps=queuedSteps;
        }
        public boolean enabled() { return enabled; }
        public boolean equipped() { return equipped; }
        public boolean nightVision() { return nightVision; }
        public boolean nightVisionUnlocked() { return entries.stream().anyMatch(e -> e.key().equals(AdaptationProfile.DARK_KEY) && e.stage()>=4); }
        public List<WheelEntry> entries() { return entries; }
        public int completedCount() { return (int)entries.stream().filter(p -> p.stage()>=4).count(); }
        public float remainingWork(WheelEntry entry,float partialTick) {
            float elapsed=enabled && equipped && entry.enabled() && entry.pending() && entry.progressing()
                ? MathHelper.clamp(clientTicks+partialTick-receivedAt,0,10) : 0;
            return (float)Math.max(0,entry.duration()-entry.work()-elapsed*entry.speed());
        }
        public float remaining(WheelEntry entry,float partialTick) { return remainingWork(entry,partialTick); }
        public float completionAge(float partialTick) { return completedAt<0 ? Float.MAX_VALUE : clientTicks+partialTick-completedAt; }
        public float turn(float partialTick) {
            if (queuedSteps<=0) return angleTarget;
            float elapsed=Math.max(0,completionAge(partialTick));
            int done=Math.min(queuedSteps,(int)(elapsed/18));
            if (done>=queuedSteps) return angleTarget;
            float t=MathHelper.clamp((elapsed-done*18)/18,0,1);
            float eased=1-(1-t)*(1-t)*(1-t);
            return angleStart+(done+eased)*(((float)Math.PI)/4);
        }
    }
    public static void accept(int id,boolean enabled,boolean equipped,boolean nightVision,List<WheelEntry> entries,long revision) {
        State old=PLAYERS.get(id);
        long completedAt=old==null ? -1 : old.completedAt;
        float start=old==null ? (revision%8)*(((float)Math.PI)/4) : old.angleStart;
        float target=old==null ? start : old.angleTarget;
        int queued=old==null ? 0 : old.queuedSteps;
        if (old!=null && revision>old.revision) {
            // Preserve unfinished turns: simultaneous completed stages each produce one discrete 45-degree step.
            long delta=Math.min(1024,revision-old.revision);
            if (old.queuedSteps>0 && old.completionAge(0)<old.queuedSteps*18) {
                queued=old.queuedSteps+(int)delta;
                target=old.angleTarget+delta*(((float)Math.PI)/4);
            } else {
                start=old.angleTarget;
                queued=(int)delta;
                target=start+delta*(((float)Math.PI)/4);
                completedAt=clientTicks;
            }
        }
        PLAYERS.put(id,new State(enabled,equipped,nightVision,entries,revision,clientTicks,completedAt,start,target,queued));
    }
    public static State get(int id) { return PLAYERS.get(id); }
    public static List<CatalogueEntry> catalogue() { return catalogue; }
    public static void acceptCatalogue(long batch,int index,int count,List<CatalogueEntry> entries) {
        if(batch<catalogueBatch) return;
        if(batch!=catalogueBatch) { catalogueBatch=batch;catalogueChunks.clear(); }
        catalogueChunks.put(index,Compat.copyList(entries));
        if(catalogueChunks.size()!=count) return;
        List<CatalogueEntry> all=new ArrayList<>();
        for(int i=0;i<count;i++) {
            List<CatalogueEntry> chunk=catalogueChunks.get(i);
            if(chunk==null) return;
            all.addAll(chunk);
        }
        catalogue=Compat.copyList(all);
        catalogueChunks.clear();
    }
    public static float time(float partialTick) { return clientTicks+partialTick; }
    static void tick() { clientTicks++; }
    static void remove(int id) { PLAYERS.remove(id); }
    static void clear() { PLAYERS.clear();clientTicks=0;catalogue=Compat.list();catalogueChunks.clear();catalogueBatch=-1; }
}
