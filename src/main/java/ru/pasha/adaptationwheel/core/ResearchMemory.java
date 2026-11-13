package ru.pasha.adaptationwheel.core;

import java.util.*;

/** Local factual research and empirical recall. Contains no game objects, stages or protection. */
public final class ResearchMemory {
    public static final int COUNT_LIMIT=Integer.MAX_VALUE;

    public record Fact(String key,AdaptationProfile profile,int confidence,String source) {
        public Fact {
            key=identity(key,256);Objects.requireNonNull(profile);
            confidence=bound(confidence,100);source=text(source,128);
        }
    }
    public record Context(String key,String owner,String direct,String effects,String phase,String range) {
        public Context {
            boolean valid=validIdentity(owner,128)&&validIdentity(direct,128)&&validIdentity(effects,256)
                    &&validIdentity(phase,64)&&validIdentity(range,24);
            key=identity(key,256);owner=identity(owner,128);direct=identity(direct,128);
            effects=ResearchMemory.effects(effects);phase=text(phase,64);range=text(range,24);
            if(!valid)key=""; // Never collapse oversized or malformed identities into a known empty context.
        }
    }
    public record Proposal(AdaptationProfile profile,int confidence,int support,String reason) {}
    /** trustedObservations is the consecutive factual support for the current profile, not lifetime support. */
    public record Sample(Context context,AdaptationProfile profile,int observations,int trustedObservations,
                         int contradictions,double meanRaw,double meanActual,double meanFraction) {
        public Sample {
            Objects.requireNonNull(context);Objects.requireNonNull(profile);
            observations=bound(observations,COUNT_LIMIT);
            trustedObservations=bound(trustedObservations,observations);
            contradictions=bound(contradictions,observations);
            meanRaw=finite(meanRaw,1_000_000);meanActual=finite(meanActual,1_000_000);
            meanFraction=finite(meanFraction,1);
        }
    }
    private record Signature(String owner,String direct,String effects,String phase,String range) {
        static Signature of(Context c){return new Signature(c.owner(),c.direct(),c.effects(),c.phase(),c.range());}
    }
    private static final class Bucket {
        final EnumMap<AdaptationProfile,Fact> rows=new EnumMap<>(AdaptationProfile.class);
        int strength;
    }
    private record Votes(EnumMap<AdaptationProfile,Integer> profiles,Set<String> blocked) {}
    private final LinkedHashMap<String,Bucket> imported=new LinkedHashMap<>(128,.75F,true);
    private final LinkedHashMap<Context,Sample> observed=new LinkedHashMap<>(128,.75F,true);
    private final Map<Signature,Set<Context>> groups=new HashMap<>();
    private final Map<String,Set<Context>> sources=new HashMap<>();
    private final Map<Signature,Votes> voteCache=new HashMap<>();
    private int factRows;

    /** live observations > current world/registry metadata > bundled reference facts. */
    private static int strength(String source){
        String s=source.toLowerCase(Locale.ROOT);
        if(prefix(s,"live"))return 3;
        if(prefix(s,"world")||prefix(s,"registry"))return 2;
        return prefix(s,"seed")?1:0;
    }
    private static boolean prefix(String source,String prefix){return source.equals(prefix)||source.startsWith(prefix+":")||source.startsWith(prefix+".");}
    public void learnFact(Fact fact){
        if(fact==null||fact.key().isBlank()||fact.confidence()==0)return;
        int rank=strength(fact.source());if(rank==0)return;
        Bucket bucket=imported.get(fact.key());
        if(bucket==null){bucket=new Bucket();bucket.strength=rank;imported.put(fact.key(),bucket);}
        else if(rank<bucket.strength)return;
        else if(rank>bucket.strength){factRows-=bucket.rows.size();bucket.rows.clear();bucket.strength=rank;}
        Fact old=bucket.rows.get(fact.profile());
        if(old==null){bucket.rows.put(fact.profile(),fact);factRows++;}
        else if(fact.confidence()>=old.confidence())bucket.rows.put(fact.profile(),fact);
    }
    /** Equal-authority disagreement survives export/import. Three factual corrections may resolve it. */
    public Fact fact(String key){
        Bucket bucket=imported.get(key);if(bucket==null)return null;
        if(bucket.rows.size()==1)return bucket.rows.values().iterator().next();
        var contexts=sources.get(key);if(contexts==null)return null;
        AdaptationProfile correction=null;boolean confirmed=false;
        for(Context context:contexts){
            Sample sample=observed.get(context);
            if(sample==null||sample.trustedObservations()==0)continue;
            if(sample.contradictions()>0&&sample.trustedObservations()<3)return null;
            if(correction!=null&&correction!=sample.profile())return null;
            correction=sample.profile();confirmed|=sample.trustedObservations()>=3;
        }
        return correction==null||!confirmed?null:bucket.rows.get(correction);
    }
    /** Called once per real attributed event; weak classifications record outcomes but never train categories. */
    public void observe(Context context,AdaptationProfile profile,boolean trusted,double raw,double actual,double maxHealth){
        if(context==null||context.key().isBlank()||profile==null)return;
        raw=finite(raw,1_000_000);actual=finite(actual,1_000_000);maxHealth=Double.isFinite(maxHealth)?Math.max(1,Math.min(1_000_000,maxHealth)):1;
        Sample old=observed.get(context);int count=old==null?1:more(old.observations());
        int support=old==null?0:old.trustedObservations(),contradictions=old==null?0:old.contradictions();
        AdaptationProfile current=old==null?profile:old.profile();
        if(trusted){
            if(old!=null&&current!=profile){if(support>0)contradictions=more(contradictions);support=0;}
            current=profile;support=more(support);
        }else if(old!=null&&old.observations()>=3&&old.meanRaw()>0&&raw>0
                &&(raw>old.meanRaw()*4+4||raw*4+4<old.meanRaw())){
            // A newly stronger variant invalidates an analogy until factual evidence re-establishes it.
            // Actual mitigated HP alone cannot invalidate it: earned protection or armour changes HP loss.
            contradictions=more(contradictions);support=0;
        }
        double weight=1.0/Math.min(32,count);
        double meanRaw=old==null?raw:old.meanRaw(),meanActual=old==null?actual:old.meanActual();
        double fraction=Math.min(1,Math.max(raw,actual)/maxHealth),meanFraction=old==null?fraction:old.meanFraction();
        if(old!=null&&(raw>0||actual>0)){
            meanRaw+=(raw-meanRaw)*weight;meanActual+=(actual-meanActual)*weight;meanFraction+=(fraction-meanFraction)*weight;
        }
        put(new Sample(context,current,count,support,contradictions,meanRaw,meanActual,meanFraction));
    }
    private void put(Sample sample){
        Context c=sample.context();if(c.key().isBlank()||sample.observations()==0)return;
        observed.put(c,sample);Signature signature=Signature.of(c);
        groups.computeIfAbsent(signature,ignored->new LinkedHashSet<>()).add(c);
        sources.computeIfAbsent(c.key(),ignored->new LinkedHashSet<>()).add(c);
        voteCache.remove(signature);
    }
    public Proposal infer(Context context){
        if(context==null||context.key().isBlank())return null;
        Sample self=observed.get(context);Fact exact=fact(context.key());
        if(exact!=null){
            if(strength(exact.source())==1&&self!=null){
                if(self.contradictions()>0&&self.trustedObservations()<3)return null;
                if(self.trustedObservations()>0&&self.profile()!=exact.profile())
                    return self.trustedObservations()>=3?empirical(self):null;
            }
            return new Proposal(exact.profile(),Math.min(82,exact.confidence()),1,strength(exact.source())==1?"learned.pretrained":"learned.research");
        }
        // Do not bypass explicit ambiguity by falling back to a single experiential guess.
        if(imported.containsKey(context.key()))return null;
        if(self!=null&&self.contradictions()>0&&self.trustedObservations()<3)return null;
        if(self!=null&&self.trustedObservations()>=3)
            return empirical(self);
        if(!transferable(context))return null;
        Signature signature=Signature.of(context);
        Votes votes=voteCache.computeIfAbsent(signature,this::votes);AdaptationProfile selected=null;int support=0;
        for(String blocked:votes.blocked())if(!blocked.equals(context.key()))return null;
        for(var entry:votes.profiles().entrySet()){
            int count=entry.getValue()-(self!=null&&usable(self)&&self.profile()==entry.getKey()?1:0);
            if(count<=0)continue;
            if(selected!=null)return null;selected=entry.getKey();support=count;
        }
        return selected!=null&&support>=3?new Proposal(selected,Math.min(80,68+support*2),support,"learned.transfer"):null;
    }
    private static Proposal empirical(Sample sample){return new Proposal(sample.profile(),Math.min(82,70+sample.trustedObservations()*2),sample.trustedObservations(),"learned.observation");}
    private Votes votes(Signature signature){
        var counts=new EnumMap<AdaptationProfile,Integer>(AdaptationProfile.class);var blocked=new HashSet<String>();
        for(Context c:groups.getOrDefault(signature,Set.of())){
            Sample sample=observed.get(c);if(sample==null)continue;
            if(sample.contradictions()>0&&sample.trustedObservations()<3)blocked.add(c.key());
            else if(usable(sample))counts.merge(sample.profile(),1,Integer::sum);
        }
        return new Votes(counts,Set.copyOf(blocked));
    }
    private static boolean usable(Sample s){return s.trustedObservations()>0&&(s.contradictions()==0||s.trustedObservations()>=3);}
    private static boolean transferable(Context c){
        return !c.owner().isBlank()&&!c.direct().isBlank()&&!c.direct().equals(c.owner())
                &&(!c.direct().startsWith("minecraft:")||!c.effects().isBlank());
    }
    public List<Fact> facts(){
        var rows=new ArrayList<Fact>(factRows);for(Bucket bucket:imported.values())rows.addAll(bucket.rows.values());return List.copyOf(rows);
    }
    public List<Sample> samples(){return List.copyOf(observed.values());}
    public static ResearchMemory restore(List<Fact> facts,List<Sample> samples){
        var model=new ResearchMemory();
        if(facts!=null)facts.stream().filter(Objects::nonNull).forEach(model::learnFact);
        if(samples!=null)samples.stream().filter(Objects::nonNull).forEach(model::put);
        return model;
    }
    /**
     * Reconciles a world snapshot with shared research without counting their overlapping
     * events twice. Neither input is mutated; only facts and measured outcomes are merged.
     */
    public static ResearchMemory merge(ResearchMemory left,ResearchMemory right){
        var model=new ResearchMemory();
        mergeInto(model,left);
        mergeInto(model,right);
        return model;
    }
    private static void mergeInto(ResearchMemory target,ResearchMemory source){
        if(source==null)return;
        for(Fact fact:source.facts())target.learnFact(fact);
        for(Sample incoming:source.samples()){
            Sample previous=target.observed.get(incoming.context());
            target.put(previous==null?incoming:mergeSample(previous,incoming));
        }
    }
    private static Sample mergeSample(Sample left,Sample right){
        if(left.equals(right))return left;
        int observations=Math.max(left.observations(),right.observations());
        int contradictions=Math.max(left.contradictions(),right.contradictions());
        boolean leftPending=unresolved(left),rightPending=unresolved(right);
        AdaptationProfile profile;
        int support;
        if(left.profile()==right.profile()){
            profile=left.profile();
            // An old confident copy cannot clear a subsequent unresolved correction.
            // At most the fresh support already present in an unresolved copy survives.
            support=leftPending||rightPending
                    ?Math.max(leftPending?left.trustedObservations():0,rightPending?right.trustedObservations():0)
                    :Math.max(left.trustedObservations(),right.trustedObservations());
        }else if(left.trustedObservations()>0&&right.trustedObservations()>0){
            // No event ordering/unique IDs exist in this fixed persistence format.
            // Keep a deterministic placeholder but abstain until three NEW factual events.
            profile=firstProfile(left.profile(),right.profile());support=0;
            contradictions=Math.max(1,contradictions);
        }else if(leftPending||rightPending){
            // Preserve the conflict barrier even when the other side is the original
            // confident snapshot which produced it. Re-merging is not new evidence.
            if(left.trustedObservations()>0||right.trustedObservations()>0||leftPending&&rightPending)
                profile=firstProfile(left.profile(),right.profile());
            else profile=leftPending?left.profile():right.profile();
            support=0;
        }else if(left.trustedObservations()>0||right.trustedObservations()>0){
            Sample factual=left.trustedObservations()>0?left:right;
            profile=factual.profile();support=factual.trustedObservations();
        }else{
            profile=firstProfile(left.profile(),right.profile());support=0;
        }
        // Without overlap IDs a pooled mean would invent independent observations.
        // Retain the conservative outcome envelope instead, with max (never summed) counts.
        return new Sample(left.context(),profile,observations,support,contradictions,
                Math.max(left.meanRaw(),right.meanRaw()),Math.max(left.meanActual(),right.meanActual()),
                Math.max(left.meanFraction(),right.meanFraction()));
    }
    private static boolean unresolved(Sample sample){return sample.contradictions()>0&&sample.trustedObservations()<3;}
    private static AdaptationProfile firstProfile(AdaptationProfile left,AdaptationProfile right){
        return left.ordinal()<right.ordinal()?left:right;
    }
    public int factCount(){return factRows;}
    public int observationCount(){long count=0;for(Sample s:observed.values())count+=s.observations();return (int)Math.min(COUNT_LIMIT,count);}
    public int contradictionCount(){
        long count=0;for(Bucket b:imported.values())count+=Math.max(0,b.rows.size()-1);
        for(Sample s:observed.values())count+=s.contradictions();return (int)Math.min(COUNT_LIMIT,count);
    }
    private static int bound(int n,int max){return Math.max(0,Math.min(max,n));}
    private static int more(int n){return n<COUNT_LIMIT?n+1:n;}
    private static double finite(double n,double max){return Double.isFinite(n)?Math.max(0,Math.min(max,n)):0;}
    private static String identity(String s,int max){
        return validIdentity(s,max)&&s!=null?s.trim():"";
    }
    private static boolean validIdentity(String s,int max){
        if(s==null)return true;if(s.length()>max)return false;
        for(int i=0;i<s.length();i++)if(s.charAt(i)<32||s.charAt(i)==127)return false;
        return true;
    }
    private static String text(String s,int max){
        if(s==null)return "";s=s.substring(0,Math.min(max,s.length()));
        StringBuilder clean=new StringBuilder(s.length());for(int i=0;i<s.length();i++){char c=s.charAt(i);clean.append(c<32||c==127?' ':c);}return clean.toString().trim();
    }
    private static String effects(String s){
        s=identity(s,256);if(s.isEmpty())return "";
        var names=new TreeSet<String>();for(String name:s.split(",")){name=name.trim();if(!name.isEmpty())names.add(name);}
        return String.join(",",names);
    }
}
