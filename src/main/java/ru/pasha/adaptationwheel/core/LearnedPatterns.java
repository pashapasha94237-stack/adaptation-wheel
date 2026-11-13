package ru.pasha.adaptationwheel.core;

import java.util.*;

/** Local case memory. It can transfer a category, never stages or immunity. */
public final class LearnedPatterns {
    public record Example(String key,String owner,String direct,String effects,String phase,AdaptationProfile profile){
        public Example(String key,String owner,String direct,String effects,AdaptationProfile profile){this(key,owner,direct,effects,"",profile);}
        public Example{key=bounded(key,256);owner=bounded(owner,128);direct=bounded(direct,128);effects=bounded(effects,256);phase=bounded(phase,64);Objects.requireNonNull(profile);}
    }
    public record Match(AdaptationProfile profile,int confidence,int support){}
    private record Votes(AdaptationProfile profile,int count,boolean conflict){}
    private final Map<String,Example> examples=new LinkedHashMap<>(256,.75F,true);
    private final Map<String,EnumMap<AdaptationProfile,Integer>> index=new HashMap<>();
    private boolean dirty=true;
    /** Name-derived elements and other learned guesses must not teach themselves. "Some harmful effect" names no category either. */
    public static boolean trusted(String reason,int confidence){
        return reason!=null&&confidence>=85&&(reason.startsWith("registry.")||reason.startsWith("native.")&&!reason.equals("native.projectile_element")&&!reason.equals("native.projectile")&&!reason.equals("native.harmful_effect")||reason.equals("compat.known"));
    }
    public void remember(Example example){
        if(example.key().isBlank()||example.owner().isBlank()||!telling(example))return;
        if(example.equals(examples.get(example.key())))return;
        examples.put(example.key(),example);dirty=true;
    }
    /** A definition changed: what was learned from it is no longer known to be true. */
    public void forget(Set<String> keys){if(examples.keySet().removeAll(keys))dirty=true;}
    /** Closest evidence first, each level less sure. A disagreement at any level abstains: those abilities differ, so nothing carries over. */
    public Match infer(Example query){
        if(query.owner().isBlank()||!telling(query))return null;
        if(dirty){index.clear();for(var e:examples.values())for(String bucket:buckets(e))index.computeIfAbsent(bucket,ignored->new EnumMap<>(AdaptationProfile.class)).merge(e.profile(),1,Integer::sum);dirty=false;}
        Example self=examples.get(query.key());
        if(distinctDelivery(query)){
            Votes exact=votes(exact(query),self);if(exact.conflict())return null;
            if(exact.count()>=3)return new Match(exact.profile(),Math.min(80,68+exact.count()*2),exact.count());
            Votes same=votes(delivery(query),self);if(same.conflict())return null;
            if(same.count()>=2)return new Match(same.profile(),Math.min(78,66+same.count()*2),same.count());
        }
        Votes family=votes(owner(query),self);if(family.conflict())return null;
        return family.count()>=3?new Match(family.profile(),Math.min(74,62+family.count()*2),family.count()):null;
    }
    private Votes votes(String bucket,Example self){
        var rows=index.get(bucket);if(rows==null)return new Votes(null,0,false);
        boolean own=self!=null&&buckets(self).contains(bucket);AdaptationProfile chosen=null;int total=0;
        for(var row:rows.entrySet()){
            int count=row.getValue()-(own&&self.profile()==row.getKey()?1:0);
            if(count<=0)continue;
            if(chosen!=null)return new Votes(null,0,true);
            chosen=row.getKey();total=count;
        }
        return new Votes(chosen,total,false);
    }
    private static List<String> buckets(Example e){return distinctDelivery(e)?List.of(exact(e),delivery(e),owner(e)):List.of(owner(e));}
    private static String exact(Example e){return "exact|"+e.owner()+"|"+e.direct()+"|"+e.effects()+"|"+e.phase();}
    private static String delivery(Example e){return "delivery|"+e.direct();}
    private static String owner(Example e){return "owner|"+e.owner();}
    /** A vanilla type with a vanilla delivery says nothing of its own: every mod borrows those. */
    private static boolean telling(Example e){return distinctDelivery(e)||!e.key().startsWith("damage:minecraft:");}
    private static boolean distinctDelivery(Example e){
        return !e.direct().isBlank()&&!e.direct().equals(e.owner())&&(!e.direct().startsWith("minecraft:")||!e.effects().isBlank());
    }
    public List<Example> save(){return List.copyOf(examples.values());}
    public static LearnedPatterns restore(List<Example> rows){var model=new LearnedPatterns();rows.stream().filter(Objects::nonNull).forEach(model::remember);return model;}
    private static String bounded(String s,int max){return s==null?"":s.substring(0,Math.min(max,s.length()));}
}
