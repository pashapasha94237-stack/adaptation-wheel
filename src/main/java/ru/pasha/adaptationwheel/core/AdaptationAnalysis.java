package ru.pasha.adaptationwheel.core;

import java.util.*;

/** Small deterministic, offline classifier. It observes game facts; it does not execute mod code. */
public final class AdaptationAnalysis {
    public static final int MAX_OBSERVATIONS=Integer.MAX_VALUE;
    private static final long EVIDENCE_MASK=(1L<<Evidence.values().length)-1;
    /** REGISTRY facts are authoritative tags/identities; other facts describe observed native behaviour. */
    public enum Evidence {
        REGISTRY_FIRE, REGISTRY_COLD, REGISTRY_ELECTRIC, REGISTRY_EXPLOSION, REGISTRY_MAGIC,
        REGISTRY_PROJECTILE, REGISTRY_PHYSICAL, REGISTRY_HUNGER, REGISTRY_SUFFOCATION,
        REGISTRY_POISON, REGISTRY_DARKNESS, REGISTRY_LIGHT,
        FIRE, COLD, ELECTRIC, EXPLOSION, MAGIC, PROJECTILE, PHYSICAL, HUNGER,
        SUFFOCATION, POISON, DARKNESS, LIGHT, CONTROL, HARMFUL_EFFECT, BENEFICIAL_EFFECT,
        // Append facts to retain the ordinal-based mask used by existing saved analyses.
        REGISTRY_EFFECT, REGISTRY_ENVIRONMENT,
        // A real DamageSource was checked against the current registry/tag snapshot.
        // Metadata-only observations omit this, preserving the source's authoritative facts.
        SOURCE_CHECKED,
        // A harmful effect was attributed to this ability; its exact elemental category
        // remains unknown. Unlike a weak name hint, the harmful consequence is observed.
        ATTRIBUTED_EFFECT,
        // The observation's profile comes from the wheel's own compatibility table for this exact key.
        COMPAT_PROFILE,
        // Categories named by the damage type's own mod tags (forge:magic, forge:curse...). Weaker than
        // vanilla tags and observed delivery, stronger than a name; re-read on every checked source.
        TAG_FIRE, TAG_COLD, TAG_ELECTRIC, TAG_EXPLOSION, TAG_MAGIC, TAG_PROJECTILE, TAG_PHYSICAL,
        TAG_HUNGER, TAG_SUFFOCATION, TAG_POISON, TAG_DARKNESS, TAG_LIGHT,
        // Observed reduction at a real native HP sink, independent of a guessed ability name.
        DIRECT_HEALTH
    }
    public record Observation(String sourceId,AdaptationProfile fallback,Set<Evidence> evidence,
                              double rawDamage,double actualLoss,double maxHealth,boolean boss,double consequenceRisk,double recentDamage) {
        public Observation(String sourceId,AdaptationProfile fallback,Set<Evidence> evidence,double rawDamage,double actualLoss,double maxHealth,boolean boss,double consequenceRisk){
            this(sourceId,fallback,evidence,rawDamage,actualLoss,maxHealth,boss,consequenceRisk,0);
        }
        public Observation(String sourceId,AdaptationProfile fallback,Set<Evidence> evidence,double rawDamage,double actualLoss,double maxHealth,boolean boss){
            this(sourceId,fallback,evidence,rawDamage,actualLoss,maxHealth,boss,0);
        }
        public Observation {
            sourceId=sourceId==null?"":sourceId.substring(0,Math.min(256,sourceId.length()));
            fallback=fallback==null?AdaptationProfile.ENVIRONMENT:fallback;
            evidence=evidence==null?Set.of():Set.copyOf(evidence);
            rawDamage=finite(rawDamage,0,1_000_000);actualLoss=finite(actualLoss,0,1_000_000);
            maxHealth=finite(maxHealth,1,1_000_000);
            consequenceRisk=finite(consequenceRisk,0,1);recentDamage=finite(recentDamage,0,1_000_000);
        }
    }
    /** Sufficient statistics. Never retains entities, levels, effects, or arbitrary mod objects. Votes: a creature's hits by category. */
    public record Memory(int observations,double meanRisk,double peakRisk,int harmfulObservations,long evidenceMask,Map<AdaptationProfile,Integer> votes) {
        public static final Memory EMPTY=new Memory(0,0,0,0,0);
        public Memory(int observations,double meanRisk,double peakRisk,int harmfulObservations,long evidenceMask){
            this(observations,meanRisk,peakRisk,harmfulObservations,evidenceMask,Map.of());
        }
        public Memory {
            observations=Math.max(0,Math.min(MAX_OBSERVATIONS,observations));
            meanRisk=finite(meanRisk,0,1);peakRisk=finite(peakRisk,0,1);
            harmfulObservations=Math.max(0,Math.min(observations,harmfulObservations));
            evidenceMask&=EVIDENCE_MASK;
            // Beneficial effects never become negative simply because old data was present.
            evidenceMask&=~bit(Evidence.BENEFICIAL_EFFECT);
            var counted=new EnumMap<AdaptationProfile,Integer>(AdaptationProfile.class);
            if(votes!=null)votes.forEach((profile,count)->{if(profile!=null&&count!=null&&count>0)counted.put(profile,count);});
            votes=Collections.unmodifiableMap(counted);
        }
    }
    public record Decision(AdaptationProfile profile,StageDurations durations,int confidence,String reason,
                           int observations,Memory memory,boolean eligible) {}
    private record Classification(AdaptationProfile profile,int confidence,String reason) {}
    private record SemanticRule(AdaptationProfile profile,long fact,String reason){}
    private enum Tier { REGISTRY, NATIVE, TAG }
    private static final List<AdaptationProfile> SEMANTIC_ORDER=List.of(AdaptationProfile.ELECTRIC,
            AdaptationProfile.EXPLOSION,AdaptationProfile.HUNGER,AdaptationProfile.COLD,
            AdaptationProfile.POISON,AdaptationProfile.SUFFOCATION,AdaptationProfile.FIRE,
            AdaptationProfile.LIGHT,AdaptationProfile.DARKNESS,AdaptationProfile.MAGIC,
            AdaptationProfile.PROJECTILE,AdaptationProfile.PHYSICAL);
    private static final List<AdaptationProfile> REGISTRY_ORDER=java.util.stream.Stream.concat(
            SEMANTIC_ORDER.stream(),java.util.stream.Stream.of(AdaptationProfile.EFFECT,AdaptationProfile.ENVIRONMENT)).toList();
    private static final SemanticRule[] REGISTRY_RULES=rules(REGISTRY_ORDER,"REGISTRY_",p->"registry."+p.name().toLowerCase(Locale.ROOT)),
            NATIVE_RULES=rules(SEMANTIC_ORDER,"",p->"native."+p.name().toLowerCase(Locale.ROOT)),
            TAG_RULES=rules(SEMANTIC_ORDER,"TAG_",p->"tag.category");
    private static final long REGISTRY_MASK=prefixMask("REGISTRY_"),TAG_MASK=prefixMask("TAG_");
    // An element describes what an attack does; a projectile/melee fact only describes its
    // delivery. Keep these tiers separate so seeing the projectile again does not erase its
    // previously observed cold/fire/etc. behaviour, and a corrected element does not leave
    // an obsolete element in the saved mask forever.
    private static final long NATIVE_ELEMENT_MASK=nativeElementMask();
    private static final long NATIVE_CARRIER_MASK=bit(Evidence.PROJECTILE)|bit(Evidence.PHYSICAL);
    private static final long NATIVE_CATEGORY_MASK=NATIVE_ELEMENT_MASK|NATIVE_CARRIER_MASK|bit(Evidence.MAGIC);
    private AdaptationAnalysis() {}
    public static Decision observe(Memory previous,Observation observation) {
        Objects.requireNonNull(observation);
        Memory old=previous==null?Memory.EMPTY:previous;
        if(observation.evidence().contains(Evidence.BENEFICIAL_EFFECT))
            return new Decision(observation.fallback(),StageDurations.defaults(observation.fallback()),100,
                    "excluded.beneficial",old.observations(),old,false);
        boolean harmful=observation.evidence().contains(Evidence.HARMFUL_EFFECT);
        long currentMask=0;for(Evidence value:observation.evidence())currentMask|=bit(value);
        // A data-pack reload may authoritatively reclassify the same registry identity. Replace
        // old registry facts as one unit, so a previous higher-priority tag cannot win forever.
        long mask=old.evidenceMask();
        if((currentMask&REGISTRY_MASK)!=0 || (currentMask&bit(Evidence.SOURCE_CHECKED))!=0)mask&=~REGISTRY_MASK;
        if((currentMask&TAG_MASK)!=0 || (currentMask&bit(Evidence.SOURCE_CHECKED))!=0)mask&=~TAG_MASK;
        if((currentMask&NATIVE_ELEMENT_MASK)!=0)mask&=~NATIVE_ELEMENT_MASK;
        if((currentMask&NATIVE_CARRIER_MASK)!=0)mask&=~NATIVE_CARRIER_MASK;
        if((currentMask&bit(Evidence.CONTROL))!=0 && (currentMask&NATIVE_CATEGORY_MASK)==0)mask&=~NATIVE_CATEGORY_MASK;
        // A creature has several attacks: an element seen on one of them says nothing about the next one.
        boolean species=observation.sourceId().startsWith("mob:");
        if(species&&(currentMask&bit(Evidence.SOURCE_CHECKED))!=0)mask&=~NATIVE_ELEMENT_MASK;
        mask|=currentMask;
        int count=old.observations()<MAX_OBSERVATIONS?old.observations()+1:MAX_OBSERVATIONS;
        double damage=Math.max(observation.rawDamage(),observation.actualLoss());
        // Raw unmitigated damage prevents earned protection from making the learned attack appear weaker.
        double fraction=Math.min(1,damage/observation.maxHealth());
        // One tick of a fire says little: what counts is how much health the source takes in a few seconds.
        double pace=Math.min(1,observation.recentDamage()/observation.maxHealth());
        double severity=Math.max(pace,Math.max(fraction,Math.min(1,damage/40.0)));
        double risk=Math.min(1,severity*.8+(observation.boss()?.2:0)+(harmful?.08:0)
                +(observation.evidence().contains(Evidence.CONTROL)?.12:0));
        if(harmful)risk=Math.max(risk,observation.consequenceRisk());
        double weight=1.0/Math.min(32,count);
        // A source-attributed debuff may supply metadata without another damage receipt.
        // Such an observation can raise risk, but must not make a known powerful attack
        // look weaker. Measured hits gradually retire an old outlier instead of allowing
        // one exceptional hit to increase every future stage for the rest of the save.
        double mean=old.observations()==0?risk:damage>0?old.meanRisk()+(risk-old.meanRisk())*weight:Math.max(old.meanRisk(),risk);
        double peak=old.observations()==0?risk:damage>0?Math.max(risk,old.peakRisk()+(risk-old.peakRisk())/16):Math.max(old.peakRisk(),risk);
        Classification classification=classify(observation,currentMask,mask);
        var votes=old.votes();
        if(species&&damage>0)votes=withVote(votes,classification.profile());
        if(species)classification=mainAttack(classification,votes);
        Memory memory=new Memory(count,mean,peak,
                old.harmfulObservations()+(harmful&&old.harmfulObservations()<count?1:0),mask,votes);
        // This is an evidence-strength score, not a probability. Repeating a name guess
        // provides no new semantic facts and therefore cannot manufacture certainty.
        int confidence=classification.confidence();
        AdaptationProfile profile=classification.profile();
        return new Decision(profile,schedule(profile,memory),confidence,classification.reason(),count,memory,true);
    }
    /** Reuse measured risk after a case match, without inventing another received hit. */
    public static Decision learned(Decision current,LearnedPatterns.Match match){
        if(match==null||!current.eligible())return current;
        // "Some harmful effect" says less than the effect's own name: a plague stays a poison.
        if(match.profile()==AdaptationProfile.EFFECT&&elemental(current.profile()))return current;
        boolean incompleteDelivery=current.reason().equals("native.projectile")&&elemental(match.profile());
        // "Some harmful effect" is all the game says about most mod effects; a known element says more.
        boolean plainEffect=current.reason().equals("native.harmful_effect")&&match.profile()!=AdaptationProfile.EFFECT;
        if(!(current.reason().equals("name.hint")||current.reason().equals("fallback.profile")||incompleteDelivery||plainEffect))return current;
        return new Decision(match.profile(),schedule(match.profile(),current.memory()),match.confidence(),"learned.pattern",current.observations(),current.memory(),true);
    }
    private static StageDurations schedule(AdaptationProfile profile,Memory memory){
        double risk=Math.min(1,memory.meanRisk()*.7+memory.peakRisk()*.3);
        int first=(int)Math.round(profile.baseSeconds*(.75+1.5*risk));
        int extra=(int)Math.round(profile.extraSeconds*(.85+.6*risk));
        return StageDurations.seconds(first,first+extra,first+extra*2,first+extra*3,first+extra*4);
    }
    private static Map<AdaptationProfile,Integer> withVote(Map<AdaptationProfile,Integer> votes,AdaptationProfile profile){
        var counted=new EnumMap<AdaptationProfile,Integer>(AdaptationProfile.class);counted.putAll(votes);
        counted.merge(profile,1,(a,b)->a<Integer.MAX_VALUE?a+b:a);
        return counted;
    }
    /** A creature's row shows what it mostly does, not whatever landed last. Ties stay put: enum order, never the latest hit. */
    private static Classification mainAttack(Classification hit,Map<AdaptationProfile,Integer> votes){
        long total=0;int best=0;AdaptationProfile lead=hit.profile();
        for(var row:votes.entrySet()){total+=row.getValue();if(row.getValue()>best){best=row.getValue();lead=row.getKey();}}
        if(lead==hit.profile())return hit;
        return new Classification(lead,(int)Math.max(50,Math.min(90,best*100L/total)),"species.main");
    }
    private static Classification classify(Observation observation,long currentMask,long mask) {
        Classification currentRegistry=semantic(currentMask,Tier.REGISTRY);
        if(currentRegistry!=null)return currentRegistry;
        Classification rememberedRegistry=semantic(mask,Tier.REGISTRY);
        if(rememberedRegistry!=null)return rememberedRegistry;
        Classification nativeFacts=semantic(mask,Tier.NATIVE);
        // A projectile tag only describes delivery. When the source classification already names
        // a specific element (a light beam tagged as a projectile), that element is what to adapt to.
        if(nativeFacts!=null && nativeFacts.profile()==AdaptationProfile.PROJECTILE && elemental(observation.fallback()))
            return new Classification(observation.fallback(),nativeFacts.confidence(),"native.projectile_element");
        if(nativeFacts!=null)return nativeFacts;
        if((mask&bit(Evidence.DIRECT_HEALTH))!=0)return new Classification(AdaptationProfile.ENVIRONMENT,90,"native.direct_health");
        if((currentMask&bit(Evidence.CONTROL))!=0)return new Classification(AdaptationProfile.PHYSICAL,86,"native.control");
        if((mask&bit(Evidence.CONTROL))!=0)return new Classification(AdaptationProfile.PHYSICAL,86,"native.control");
        // A table written for this exact ability (a Jujutsu technique, a T2S condition) is more specific
        // than a tag shared by every attack of its mod.
        if((mask&bit(Evidence.COMPAT_PROFILE))!=0)return new Classification(observation.fallback(),86,"compat.known");
        Classification tagged=semantic(mask,Tier.TAG);
        if(tagged!=null)return tagged;
        if((mask&bit(Evidence.ATTRIBUTED_EFFECT))!=0)return new Classification(AdaptationProfile.EFFECT,85,"native.harmful_effect");
        String source=observation.sourceId();
        AdaptationProfile guessed=AdaptationProfile.fromName(source.substring(source.lastIndexOf(':')+1),null);
        if(guessed!=null)return new Classification(guessed,40,"name.hint");
        if((mask&bit(Evidence.HARMFUL_EFFECT))!=0)return new Classification(AdaptationProfile.EFFECT,85,"native.harmful_effect");
        return new Classification(observation.fallback(),30,"fallback.profile");
    }
    private static Classification semantic(long mask,Tier tier) {
        for(SemanticRule rule:switch(tier){case REGISTRY->REGISTRY_RULES;case NATIVE->NATIVE_RULES;case TAG->TAG_RULES;}) {
            if((mask&rule.fact())!=0){
                long group=switch(tier){
                    case REGISTRY->REGISTRY_MASK;
                    case TAG->TAG_MASK;
                    case NATIVE->(rule.fact()&NATIVE_ELEMENT_MASK)!=0?NATIVE_ELEMENT_MASK:
                            (rule.fact()&NATIVE_CARRIER_MASK)!=0?NATIVE_CARRIER_MASK:rule.fact();
                };
                int alternatives=Math.max(0,Long.bitCount(mask&group)-1);
                int confidence=switch(tier){
                    case REGISTRY->Math.max(86,96-alternatives*5);
                    case NATIVE->Math.max(64,88-alternatives*12);
                    case TAG->Math.max(62,78-alternatives*8);
                };
                return new Classification(rule.profile(),confidence,rule.reason());
            }
        }
        return null;
    }
    /** The saved facts behind a decision, in declaration order. */
    public static List<Evidence> facts(long mask){
        var rows=new ArrayList<Evidence>();for(Evidence value:Evidence.values())if((mask&bit(value))!=0)rows.add(value);
        return rows;
    }
    /** The category the analyzer picks among several equally strong ones, e.g. two mod tags. */
    public static AdaptationProfile strongest(Set<AdaptationProfile> profiles) {
        return SEMANTIC_ORDER.stream().filter(profiles::contains).findFirst().orElse(null);
    }
    private static SemanticRule[] rules(List<AdaptationProfile> profiles,String factPrefix,java.util.function.Function<AdaptationProfile,String> reason){
        return profiles.stream().map(p->new SemanticRule(p,bit(Evidence.valueOf(factPrefix+p.name())),reason.apply(p))).toArray(SemanticRule[]::new);
    }
    private static long prefixMask(String prefix) {
        long mask=0;for(Evidence value:Evidence.values())if(value.name().startsWith(prefix))mask|=bit(value);
        return mask;
    }
    private static long nativeElementMask() {
        long mask=0;
        for(AdaptationProfile profile:SEMANTIC_ORDER)if(profile!=AdaptationProfile.MAGIC && profile!=AdaptationProfile.PROJECTILE && profile!=AdaptationProfile.PHYSICAL)
            mask|=bit(Evidence.valueOf(profile.name()));
        return mask;
    }
    private static boolean elemental(AdaptationProfile profile){
        return SEMANTIC_ORDER.contains(profile) && profile!=AdaptationProfile.MAGIC && profile!=AdaptationProfile.PROJECTILE && profile!=AdaptationProfile.PHYSICAL;
    }
    private static long bit(Evidence value){return 1L<<value.ordinal();}
    private static double finite(double value,double min,double max){return Double.isFinite(value)?Math.max(min,Math.min(max,value)):min;}
}
