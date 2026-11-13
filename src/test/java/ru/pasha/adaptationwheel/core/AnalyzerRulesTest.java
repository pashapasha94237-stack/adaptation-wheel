package ru.pasha.adaptationwheel.core;

import java.util.List;
import java.util.Set;

public final class AnalyzerRulesTest {
    public static void run(){
        abilityEntities();pace();experience();words();
        check(AdaptationAnalysis.facts(1L<<AdaptationAnalysis.Evidence.REGISTRY_FIRE.ordinal()).equals(List.of(AdaptationAnalysis.Evidence.REGISTRY_FIRE)),"A saved mask reads back as its facts");
        System.out.println("AnalyzerRulesTest: ability entities, damage pace, element experience and death-line words passed.");
    }
    private static void abilityEntities(){
        for(String id:List.of("duneons:wickedwraithattack","jujutsucraft:entity_slash","jujutsucraft:entity_tornado","jujutsucraft:blue","scguns:plasma_projectile","mod:fire_orb","mod:ice_arrow","mod:death_beam"))
            check(AbilityEntities.is(id),id+" is an attack, not a creature");
        for(String id:List.of("minecraft:wither","minecraft:ender_dragon","twilightforest:hydra","iceandfire:fire_dragon","mod:orbiter","mod:beamer"))
            check(!AbilityEntities.is(id),id+" is a creature");
    }
    private static void pace(){
        var pace=new DamagePace();
        check(pace.add(0,1)==1&&pace.add(20,1)==2&&pace.add(99,1)==3,"Hits inside the window add up");
        check(pace.add(100,1)==3,"The first hit has left the window");
        check(pace.add(50,1)==1,"A clock that moved back starts the window again");
        check(pace.add(60,Double.NaN)==1&&pace.add(61,-5)==1,"Nonsense damage counts for nothing");
        var calm=AdaptationAnalysis.observe(null,hit(0));var burning=AdaptationAnalysis.observe(null,hit(16));
        check(burning.memory().peakRisk()>calm.memory().peakRisk()&&burning.durations().first()>calm.durations().first(),"Small hits at a deadly pace are dangerous");
        check(AdaptationAnalysis.observe(null,hit(5000)).memory().peakRisk()<=1,"Pace cannot push risk past the top");
    }
    private static AdaptationAnalysis.Observation hit(double recent){
        return new AdaptationAnalysis.Observation("damage:mod:zzz",AdaptationProfile.FIRE,Set.of(),1,1,20,false,0,recent);
    }
    private static void experience(){
        check(ElementExperience.percent(AdaptationProfile.FIRE,0)==0,"No experience, no change");
        check(ElementExperience.percent(AdaptationProfile.FIRE,2)==10,"5% per mastered source");
        check(ElementExperience.percent(AdaptationProfile.FIRE,100)==35,"Never more than 35%");
        check(ElementExperience.percent(AdaptationProfile.ENVIRONMENT,5)==0,"Environment rows are no element");
        check(Math.abs(AdaptationState.speedFor(0,30,10)-1.1)<1e-9,"Experience is a starting speed");
        check(Math.abs(AdaptationState.speedFor(1,30,15)-1.45)<1e-9,"Hits and experience add up");
        check(Math.abs(AdaptationState.speedFor(0,30,900)-1.35)<1e-9,"A synced percent cannot pass 35%");
        var state=new AdaptationState();
        state.setStage("damage:mod:a",4,AdaptationProfile.FIRE);state.setStage("damage:mod:b",4,AdaptationProfile.FIRE);
        state.setStage("damage:mod:c",3,AdaptationProfile.FIRE);state.setStage("damage:mod:d",4,AdaptationProfile.COLD);
        check(state.mastered(AdaptationProfile.FIRE,"damage:mod:new")==2,"Only finished sources of this category count");
        check(state.mastered(AdaptationProfile.FIRE,"damage:mod:a")==1,"A source is no experience for itself");
        check(state.damageMultiplier("damage:mod:new")==1,"Experience never gives protection");
    }
    private static void words(){
        check(AdaptationProfile.fromText("%1$s was frozen solid by %2$s")==AdaptationProfile.COLD,"A death line names the element");
        check(AdaptationProfile.fromText("%1$s was squashed by %2$s using %3$s")==AdaptationProfile.PHYSICAL,"Placeholders are not words");
        check(AdaptationProfile.fromText("%1$s was scalded")==AdaptationProfile.FIRE,"Scalding is fire");
        check(AdaptationProfile.fromText("%1$s died")==null&&AdaptationProfile.fromText(null)==null,"A line without a hint says nothing");
        check(AdaptationProfile.fromName("electricuted",null)==AdaptationProfile.ELECTRIC,"Misspelt electric words share a stem");
        check(AdaptationProfile.fromName("archer_snipe",null)==AdaptationProfile.PROJECTILE,"A snipe is a shot");
        check(AdaptationProfile.fromName("mechanical_saw",null)==AdaptationProfile.PHYSICAL&&AdaptationProfile.fromName("run_over",null)==AdaptationProfile.PHYSICAL,"Machines hit the body");
        check(AdaptationProfile.fromName("blinded",null)==AdaptationProfile.DARKNESS,"Blinded is darkness");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
