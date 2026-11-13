package ru.pasha.adaptationwheel.core;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Observations and empirical transitions. Never stores entities or invents immunity. */
public final class CombatKnowledge {
    private static final Set<String> ADVISORS=Set.of("local","online","fallback","pending");
    public enum Kind { WINDUP, PROJECTILE, IMPACT, EXPLOSION, DEBUFF }
    public record Fact(String key,String actor,String owner,String direct,String damage,String effects,
                       String range,String phase,Kind kind,long tick) {
        public Fact {key=clean(key,256);actor=clean(actor,96);owner=clean(owner,128);direct=clean(direct,128);damage=clean(damage,128);effects=clean(effects,256);range=clean(range,24);phase=clean(phase,64);Objects.requireNonNull(kind);}
        public String fingerprint(){return UUID.nameUUIDFromBytes((owner+"|"+direct+"|"+damage+"|"+effects+"|"+range+"|"+kind).getBytes(StandardCharsets.UTF_8)).toString();}
    }
    public record Summary(int variants,int verified,int leaks,double maxLeak,String owner,String direct,String damage,
                          String effects,String range,String flow,String phase,int intervalTicks,String predicted,
                          String previousProfile,String revisedProfile,String advisor) {
        public static final Summary EMPTY=new Summary(0,0,0,0,"","","","","","","",0,"","","","local");
        public Summary {variants=Math.max(0,variants);verified=Math.max(0,verified);leaks=Math.max(0,leaks);maxLeak=Double.isFinite(maxLeak)?Math.max(0,Math.min(1_000_000,maxLeak)):0;owner=clean(owner,128);direct=clean(direct,128);damage=clean(damage,128);effects=clean(effects,256);range=clean(range,24);flow=clean(flow,512);phase=clean(phase,64);intervalTicks=bound(intervalTicks,12000);predicted=clean(predicted,256);previousProfile=profile(previousProfile);revisedProfile=profile(revisedProfile);if(previousProfile.isEmpty()||revisedProfile.isEmpty()){previousProfile="";revisedProfile="";}advisor=advisor!=null&&ADVISORS.contains(advisor)?advisor:"local";}
    }
    public record Saved(String key,List<String> fingerprints,Summary summary){}
    public record Transition(String prefix,String next,int count){}
    private static final int INTERVAL_WINDOW=8,TRANSITION_WINDOW=8;
    private static final class Timing {
        long tick=-1;int count,cursor,estimate;
        final int[] intervals=new int[INTERVAL_WINDOW],sorted=new int[INTERVAL_WINDOW];
        int sample(long now){
            if(tick>=0&&now<tick){count=cursor=estimate=0;tick=now;return 0;}
            if(tick<0){tick=now;return 0;}
            long gap=now-tick;
            // Duplicate callbacks for one impact must not move the next interval's origin.
            if(gap<5)return estimate;
            tick=now;
            if(gap>12000){count=cursor=estimate=0;return 0;}
            intervals[cursor]=(int)gap;cursor=(cursor+1)%INTERVAL_WINDOW;count=Math.min(INTERVAL_WINDOW,count+1);
            if(count<3)return estimate=0;
            System.arraycopy(intervals,0,sorted,0,count);Arrays.sort(sorted,0,count);int median=sorted[count/2],consistent=0;
            for(int i=0;i<count;i++)if(intervals[i]>=median*.7&&intervals[i]<=median*1.3)consistent++;
            // A changed cadence immediately removes the old estimate until it stabilizes.
            return estimate=gap>=median*.7&&gap<=median*1.3&&consistent>=Math.ceil(count*.8)?median:0;
        }
    }
    private static final class Entry {
        final LinkedHashSet<String> variants=new LinkedHashSet<>();Summary s=Summary.EMPTY;
        Kind lastKind;
        String actor="";final Map<String,Timing> timings=new HashMap<>();
    }
    private static final class Track {String owner="",phase="",hintKey="";long tick=-1;final Deque<String> tokens=new ArrayDeque<>();}
    private static final class Continuations {
        final ArrayDeque<String> recent=new ArrayDeque<>();
        void add(String token){if(recent.size()==TRANSITION_WINDOW)recent.removeFirst();recent.addLast(token);}
        String prediction(){
            if(recent.size()<3)return "";
            String last=recent.getLast();int streak=0,total=0;
            for(var it=recent.descendingIterator();it.hasNext();)if(it.next().equals(last))streak++;else break;
            for(String token:recent)if(token.equals(last))total++;
            // A contradictory continuation invalidates the old warning immediately.
            return streak>=3&&total>=Math.ceil(recent.size()*.8)?last:"";
        }
    }
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final Map<String,Track> tracks=new LinkedHashMap<>();
    private final Map<String,Continuations> transitions=new LinkedHashMap<>();
    private static int bound(int n,int max){return Math.max(0,Math.min(max,n));}
    private static int more(int n,boolean add){return add&&n<Integer.MAX_VALUE?n+1:n;}
    private static String clean(String s,int max){
        if(s==null||s.isEmpty())return "";int length=Math.min(max,s.length());
        for(int i=0;i<length;i++)if(s.charAt(i)<32||s.charAt(i)==127){
            StringBuilder safe=new StringBuilder(length);for(int j=0;j<length;j++){char c=s.charAt(j);safe.append(c<32||c==127?' ':c);}return safe.toString();
        }
        return length==s.length()?s:s.substring(0,length);
    }
    private static String profile(String s){if(s==null||s.isEmpty())return "";try{return AdaptationProfile.valueOf(s).name();}catch(IllegalArgumentException ignored){return "";}}
    private Entry entry(String key){if(key==null||key.isBlank()||key.length()>256)return null;return entries.computeIfAbsent(key,k->new Entry());}
    public Summary summary(String key){Entry e=entries.get(key);return e==null?Summary.EMPTY:e.s;}
    /** Rich identity is evidence; range/effect changes do not reset a learned protection key. */
    public void describe(Fact f){
        Entry e=entry(f.key());if(e==null)return;Summary s=e.s;
        boolean sameVariant=e.lastKind==f.kind()&&s.owner().equals(f.owner())&&s.direct().equals(f.direct())
                &&s.damage().equals(f.damage())&&s.effects().equals(f.effects())&&s.range().equals(f.range());
        if(!sameVariant)e.variants.add(f.fingerprint());e.lastKind=f.kind();
        boolean sameContext=e.actor.equals(f.actor())&&s.owner().equals(f.owner())&&s.phase().equals(f.phase());
        int interval=0;
        if(!f.actor().isEmpty()){
            String context=f.actor()+"|"+f.owner()+"|"+f.phase()+"|"+f.kind()+"|"+f.direct()+"|"+f.damage();
            interval=e.timings.computeIfAbsent(context,x->new Timing()).sample(f.tick());
        }
        e.actor=f.actor();
        e.s=new Summary(e.variants.size(),s.verified(),s.leaks(),s.maxLeak(),f.owner(),f.direct(),f.damage(),f.effects(),f.range(),sameContext?s.flow():"",f.phase(),interval,sameContext?s.predicted():"",s.previousProfile(),s.revisedProfile(),s.advisor());
    }
    /** Per-actor, per-phase two-event context. Requires repeated observations to predict. */
    public String event(Fact f){
        if(f.actor().isEmpty())return "";Entry e=entry(f.key());if(e==null)return "";
        Track t=tracks.computeIfAbsent(f.actor(),x->new Track());String token=f.kind().name()+":"+f.key();
        boolean reset=t.tick>=0&&(f.tick()<t.tick||f.tick()-t.tick>200)||!t.phase.equals(f.phase())||!t.owner.equals(f.owner());
        if(reset){t.tokens.clear();invalidateActor(f.actor());}
        if(t.tick==f.tick()&&!t.tokens.isEmpty()&&t.tokens.getLast().equals(token))return "";
        clearPrediction(t.hintKey,f.actor());
        String before=prefix(f.owner(),f.phase(),t.tokens);
        if(t.tokens.size()>=2)continuations(before).add(token);
        if(t.tokens.size()==4)t.tokens.removeFirst();t.tokens.addLast(token);t.tick=f.tick();t.phase=f.phase();t.owner=f.owner();t.hintKey=f.key();
        String predicted="";var known=transitions.get(prefix(f.owner(),f.phase(),t.tokens));
        if(known!=null)predicted=known.prediction();
        if(!e.actor.equals(f.actor())){e.s=withoutLiveHints(e.s);e.actor=f.actor();}
        Summary s=e.s;e.s=new Summary(s.variants(),s.verified(),s.leaks(),s.maxLeak(),s.owner(),s.direct(),s.damage(),s.effects(),s.range(),String.join(" → ",t.tokens),f.phase(),s.intervalTicks(),predicted,s.previousProfile(),s.revisedProfile(),s.advisor());return predicted;
    }
    private Continuations continuations(String context){return transitions.computeIfAbsent(context,x->new Continuations());}
    private static Summary withoutLiveHints(Summary s){return new Summary(s.variants(),s.verified(),s.leaks(),s.maxLeak(),s.owner(),s.direct(),s.damage(),s.effects(),s.range(),"",s.phase(),0,"",s.previousProfile(),s.revisedProfile(),s.advisor());}
    private void invalidateActor(String actor){for(Entry e:entries.values())if(e.actor.equals(actor))e.s=withoutLiveHints(e.s);}
    /** Forget live combat context during silence; learned patterns and measured protection remain. */
    public void expire(long now){
        for(var it=tracks.entrySet().iterator();it.hasNext();){var row=it.next();Track t=row.getValue();if(t.tick>=0&&(now<t.tick||now-t.tick>200)){
            String actor=row.getKey();it.remove();invalidateActor(actor);
            for(Entry e:entries.values())e.timings.keySet().removeIf(context->context.startsWith(actor+"|"));
        }}
    }
    private void clearPrediction(String key,String actor){Entry e=entries.get(key);if(e==null||!e.actor.equals(actor)||e.s.predicted().isEmpty())return;Summary s=e.s;e.s=new Summary(s.variants(),s.verified(),s.leaks(),s.maxLeak(),s.owner(),s.direct(),s.damage(),s.effects(),s.range(),s.flow(),s.phase(),s.intervalTicks(),"",s.previousProfile(),s.revisedProfile(),s.advisor());}
    private static String prefix(String owner,String phase,Deque<String> tokens){var last=tokens.stream().skip(Math.max(0,tokens.size()-2)).toList();return clean(owner+"|"+phase+"|"+String.join("|",last),768);}
    public void protection(String key,boolean leaked,double loss){Entry e=entry(key);if(e==null)return;Summary s=e.s;e.s=new Summary(s.variants(),more(s.verified(),!leaked),more(s.leaks(),leaked),Math.max(s.maxLeak(),leaked?loss:0),s.owner(),s.direct(),s.damage(),s.effects(),s.range(),s.flow(),s.phase(),s.intervalTicks(),s.predicted(),s.previousProfile(),s.revisedProfile(),s.advisor());}
    public void revised(String key,AdaptationProfile previous,AdaptationProfile revised){if(previous==null||previous==revised)return;Entry e=entry(key);if(e==null)return;Summary s=e.s;e.s=new Summary(s.variants(),s.verified(),s.leaks(),s.maxLeak(),s.owner(),s.direct(),s.damage(),s.effects(),s.range(),s.flow(),s.phase(),s.intervalTicks(),s.predicted(),previous.name(),revised.name(),s.advisor());}
    public void advisor(String key,String mode){Entry e=entry(key);if(e==null)return;Summary s=e.s;e.s=new Summary(s.variants(),s.verified(),s.leaks(),s.maxLeak(),s.owner(),s.direct(),s.damage(),s.effects(),s.range(),s.flow(),s.phase(),s.intervalTicks(),s.predicted(),s.previousProfile(),s.revisedProfile(),mode);}
    public List<Saved> save(){return entries.entrySet().stream().map(e->new Saved(e.getKey(),List.copyOf(e.getValue().variants),e.getValue().s)).toList();}
    /** Ordered unit observations retain recency using the original three-field save format. */
    public List<Transition> transitions(){var out=new ArrayList<Transition>();transitions.forEach((prefix,next)->next.recent.forEach(token->out.add(new Transition(prefix,token,1))));return List.copyOf(out);}
    public static CombatKnowledge restore(List<Saved> saved,List<Transition> history){
        var k=new CombatKnowledge();for(Saved row:saved.stream().filter(Objects::nonNull).toList()){Entry e=k.entry(row.key());if(e==null||row.summary()==null)continue;e.s=withoutLiveHints(row.summary());if(row.fingerprints()!=null)row.fingerprints().stream().filter(s->s!=null&&s.length()<=36).forEach(e.variants::add);}
        var grouped=new LinkedHashMap<String,List<Transition>>();
        for(Transition t:history.stream().filter(Objects::nonNull).toList()){
            if(t.prefix()==null||t.next()==null||t.prefix().length()>768||t.next().length()>280||t.count()<=0)continue;
            grouped.computeIfAbsent(t.prefix(),x->new ArrayList<>()).add(t);
        }
        grouped.forEach((prefix,rows)->{
            Continuations next=k.continuations(prefix);
            boolean aggregate=rows.stream().anyMatch(t->t.count()>1);
            var outcomes=new LinkedHashSet<String>();rows.forEach(t->outcomes.add(t.next()));
            if(aggregate&&outcomes.size()>1){
                // Legacy aggregate counts contain no order: do not invent a confident last streak.
                var uncertain=outcomes.stream().limit(8).toList();for(int i=0;i<TRANSITION_WINDOW;i++)next.add(uncertain.get(i%uncertain.size()));
            }else for(Transition t:rows)for(int i=0;i<Math.min(TRANSITION_WINDOW,t.count());i++)next.add(t.next());
        });return k;
    }
}
