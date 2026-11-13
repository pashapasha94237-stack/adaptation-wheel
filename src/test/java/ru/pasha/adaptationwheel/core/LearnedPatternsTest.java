package ru.pasha.adaptationwheel.core;

import java.util.*;

public final class LearnedPatternsTest {
    private static LearnedPatterns.Example example(String key,String owner,String direct,AdaptationProfile profile){return new LearnedPatterns.Example(key,owner,direct,"",profile);}
    public static void run(){
        var model=new LearnedPatterns();var query=example("attack:mod:new","mod:caster","mod:opaque_orb",AdaptationProfile.ENVIRONMENT);
        model.remember(example("attack:mod:a","mod:caster","mod:opaque_orb",AdaptationProfile.COLD));
        model.remember(example("attack:mod:b","mod:caster","mod:opaque_orb",AdaptationProfile.COLD));
        var two=model.infer(query);check(two!=null&&two.profile()==AdaptationProfile.COLD&&two.support()==2&&two.confidence()==70,"Two abilities on one projectile give only a cautious guess");
        model.remember(example("attack:mod:c","mod:caster","mod:opaque_orb",AdaptationProfile.COLD));
        var match=model.infer(query);check(match!=null&&match.profile()==AdaptationProfile.COLD&&match.support()==3&&match.confidence()<85,"Three factual agreeing abilities should teach an uncertain local category");
        var foreign=model.infer(example("attack:mod:new","mod:other","mod:opaque_orb",AdaptationProfile.ENVIRONMENT));check(foreign!=null&&foreign.profile()==AdaptationProfile.COLD&&foreign.confidence()<match.confidence(),"The same projectile from another owner is a weaker analogy");
        var family=model.infer(example("attack:mod:new","mod:caster","mod:other_orb",AdaptationProfile.ENVIRONMENT));check(family!=null&&family.profile()==AdaptationProfile.COLD&&family.confidence()<foreign.confidence(),"Another projectile of the same owner is the weakest analogy");
        check(model.infer(example("attack:mod:new","mod:stranger","mod:other_orb",AdaptationProfile.ENVIRONMENT))==null,"Nothing in common, nothing carries over");
        check(model.infer(new LearnedPatterns.Example("attack:mod:new","mod:caster","mod:opaque_orb","","health-band:1",AdaptationProfile.ENVIRONMENT)).confidence()<match.confidence(),"A different boss phase is less sure of the old elemental evidence");
        model.remember(example("attack:mod:d","mod:caster","mod:opaque_orb",AdaptationProfile.FIRE));
        check(model.infer(query)==null,"A contradictory exact signature must cause abstention");
        check(model.infer(example("attack:mod:new","mod:other","mod:opaque_orb",AdaptationProfile.ENVIRONMENT))==null&&model.infer(example("attack:mod:new","mod:caster","mod:other_orb",AdaptationProfile.ENVIRONMENT))==null,"A contradiction stops the softer levels too");
        model.remember(example("attack:mod:d","mod:caster","mod:opaque_orb",AdaptationProfile.COLD));
        check(LearnedPatterns.restore(model.save()).infer(query)!=null,"Corrected factual evidence and save/load must recover learning");
        check(!LearnedPatterns.trusted("name.hint",40)&&!LearnedPatterns.trusted("learned.pattern",80)&&!LearnedPatterns.trusted("native.projectile_element",88),"Guesses must never train themselves");
        check(LearnedPatterns.trusted("registry.cold",96)&&LearnedPatterns.trusted("compat.known",86),"Factual registry/table observations may train the model");
        var plain=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation("effect:mod:zzz",AdaptationProfile.EFFECT,Set.of(AdaptationAnalysis.Evidence.HARMFUL_EFFECT),0,0,20,false));
        check(plain.reason().equals("native.harmful_effect")&&!LearnedPatterns.trusted(plain.reason(),plain.confidence()),"Some harmful effect names no category and teaches none");
        check(AdaptationAnalysis.learned(plain,new LearnedPatterns.Match(AdaptationProfile.ELECTRIC,78,1)).profile()==AdaptationProfile.ELECTRIC,"A known element refines a plain harmful effect");
        check(AdaptationAnalysis.learned(plain,new LearnedPatterns.Match(AdaptationProfile.EFFECT,78,1))==plain,"The word effect adds nothing to a plain harmful effect");
        var raw=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation(query.key(),AdaptationProfile.ENVIRONMENT,Set.of(),4,4,20,false));
        var state=new AdaptationState();state.applyAnalysis(query.key(),raw);state.hit(query.key(),false);state.tick();var before=state.snapshot(query.key());
        var learned=AdaptationAnalysis.learned(raw,match);state.applyAnalysis(query.key(),learned);var after=state.snapshot(query.key());
        check(learned.profile()==AdaptationProfile.COLD&&learned.observations()==raw.observations(),"Case matching refines the category without inventing a received hit");
        check(before.duration()==after.duration()&&before.work()==after.work()&&after.stage()==0&&state.damageMultiplier(query.key())==1,"Learning must preserve current work and never copy protection");
        var tagged=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation(query.key(),AdaptationProfile.FIRE,Set.of(AdaptationAnalysis.Evidence.REGISTRY_FIRE),4,4,20,false));
        check(AdaptationAnalysis.learned(tagged,match)==tagged,"A remembered analogy cannot override a current registry fact");
        var carrier=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation(query.key(),AdaptationProfile.PROJECTILE,Set.of(AdaptationAnalysis.Evidence.PROJECTILE),4,4,20,false));
        check(AdaptationAnalysis.learned(carrier,match).profile()==AdaptationProfile.COLD,"Observed projectile delivery can gain an explicitly tentative learned element");
        for(int i=0;i<1000;i++)model.remember(example("attack:mod:"+i,"mod:caster","mod:opaque_orb",AdaptationProfile.COLD));
        check(model.save().size()>=1000,"Every learned case stays: nothing is evicted to make room");
        var one=new LearnedPatterns();one.remember(example("attack:mod:a","mod:caster","mod:opaque_orb",AdaptationProfile.COLD));
        check(one.infer(query)==null,"One example is no analogy");
        var split=new LearnedPatterns();
        split.remember(example("attack:mod:s1","mod:y","mod:shared_orb",AdaptationProfile.COLD));split.remember(example("attack:mod:s2","mod:z","mod:shared_orb",AdaptationProfile.FIRE));
        for(int i=0;i<3;i++)split.remember(example("attack:mod:own"+i,"mod:x","mod:own_orb",AdaptationProfile.COLD));
        check(split.infer(example("attack:mod:new","mod:x","mod:shared_orb",AdaptationProfile.ENVIRONMENT))==null,"A projectile that disagrees with itself stops the owner's analogy too");
        var bites=new LearnedPatterns();for(int i=0;i<3;i++)bites.remember(example("damage:mod:bite"+i,"mod:croc","mod:croc",AdaptationProfile.PHYSICAL));
        var bite=bites.infer(example("damage:mod:new","mod:croc","mod:croc",AdaptationProfile.ENVIRONMENT));
        check(bite!=null&&bite.profile()==AdaptationProfile.PHYSICAL&&bite.confidence()==68,"A creature's own types teach its other unnamed ones, cautiously");
        check(bites.infer(example("damage:mod:bite0","mod:croc","mod:croc",AdaptationProfile.ENVIRONMENT))==null,"A row is no evidence for itself");
        check(bites.infer(example("damage:minecraft:generic","mod:croc","mod:croc",AdaptationProfile.ENVIRONMENT))==null,"A shared vanilla type says nothing about the creature");
        bites.remember(example("damage:minecraft:generic","mod:croc","mod:croc",AdaptationProfile.MAGIC));check(bites.save().size()==3,"A shared vanilla type teaches nothing either");
        bites.forget(Set.of("damage:mod:bite0"));check(bites.save().size()==2&&bites.infer(example("damage:mod:new","mod:croc","mod:croc",AdaptationProfile.ENVIRONMENT))==null,"What changed is forgotten and stops counting");
        System.out.println("LearnedPatternsTest: factual learning, ambiguity, identity isolation, persistence, clock preservation, no immunity transfer and bounds passed.");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
