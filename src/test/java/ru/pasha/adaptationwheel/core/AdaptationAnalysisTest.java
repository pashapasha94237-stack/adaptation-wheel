package ru.pasha.adaptationwheel.core;

import java.util.*;
import static ru.pasha.adaptationwheel.core.AdaptationAnalysis.Evidence.*;

/** Behavioural regressions for evidence priority and authoritative progression, without Minecraft. */
public final class AdaptationAnalysisTest {
    private static final String KEY="attack:unknownmod:lightning_slash";
    public static void main(String[] args){run();}
    public static void run() {
        try {
            AdaptationTimings.defaults();
            compatTable445();
            var fire=analyze("attack:other:ice_arrow",Set.of(REGISTRY_FIRE,PROJECTILE),4,20,false);
            check(fire.profile()==AdaptationProfile.FIRE && fire.reason().equals("registry.fire"),"Registry fire outranks misleading ice name and generic projectile");
            var physical=analyze(KEY,Set.of(PHYSICAL),4,20,false);
            check(physical.profile()==AdaptationProfile.PHYSICAL && physical.confidence()>80,"Observed physical behaviour outranks a lightning name");
            var unknown=analyze("attack:unknownmod:ability_374",Set.of(MAGIC,PROJECTILE),8,20,true);
            check(unknown.profile()==AdaptationProfile.MAGIC && unknown.reason().equals("native.magic"),"Unknown mod abilities use native semantics without an integration table");
            var named=analyze("attack:unknownmod:electric_breath",Set.of(),2,20,false);
            check(named.profile()==AdaptationProfile.ELECTRIC && named.confidence()<66,"Names provide an explicitly uncertain hint only");
            var direct=analyze("attack:unknownmod:health_write/fire_arrow/apply/abcdef",Set.of(DIRECT_HEALTH),4,20,false);
            check(direct.profile()==AdaptationProfile.ENVIRONMENT&&direct.confidence()==90&&direct.reason().equals("native.direct_health"),"A measured HP write is a mechanism fact, not an element guessed from its class name");
            var taggedDirect=analyze("attack:unknownmod:health_write/fire_arrow/apply/abcdef",Set.of(DIRECT_HEALTH,REGISTRY_MAGIC),4,20,false);
            check(taggedDirect.profile()==AdaptationProfile.MAGIC,"Explicit registry semantics still outrank direct-health mechanism evidence");
            authoritativeReload();
            nativeCorrectionAndDelivery();
            evidenceStrength();
            representativeRisk();
            effectThreat();
            allExplicitProfiles();
            vocabularyAndVanillaTypes();
            modTagsAndCompatibilityTables();
            speciesMainAttack();
            catalogueProfilesAndGluedNames();
            countsNeverOverflow();
            var positive=analyze("effect:other:fire_resistance",Set.of(BENEFICIAL_EFFECT,FIRE),0,20,false);
            check(!positive.eligible() && positive.memory().observations()==0,"Beneficial effects never learn even with offensive-looking names or tags");
            var negative=analyze("effect:other:unfamiliar",Set.of(HARMFUL_EFFECT),0,20,false);
            check(negative.eligible() && negative.profile()==AdaptationProfile.EFFECT,"An unknown harmful mod effect is recognized without a name heuristic");
            var weak=analyze("damage:other:burn",Set.of(FIRE),1,20,false);
            var strong=analyze("damage:other:burn",Set.of(FIRE),18,20,true);
            check(strong.durations().first()>weak.durations().first(),"A dangerous hit takes longer than a weak hit of the same type");
            check(weak.durations().first()<physical.durations().first() && physical.durations().first()<unknown.durations().first(),"Simple fire, physical impact, and complex boss magic retain ordered difficulty");
            var repeated=AdaptationAnalysis.observe(strong.memory(),new AdaptationAnalysis.Observation("damage:other:burn",AdaptationProfile.FIRE,Set.of(FIRE),18,0,20,true));
            check(repeated.memory().meanRisk()>=strong.memory().meanRisk()-.00001,"Full earned protection cannot make unchanged raw attacks look harmless");
            var malformed=new AdaptationAnalysis.Memory(Integer.MAX_VALUE,Double.NaN,Double.POSITIVE_INFINITY,-8,-1);
            check(malformed.observations()==AdaptationAnalysis.MAX_OBSERVATIONS && Double.isFinite(malformed.meanRisk()),"Persisted statistics are bounded and nonfinite data sanitized");
            progressionFreeze(physical,strong);
            manualOverrides(physical,strong);
            restorationAndOptOut(physical);
            System.out.println("AdaptationAnalysisTest: evidence priority, native correction, delivery retention, evidence strength, recovering risk, positive effects, stable timers, manual settings, opt-out and persistence passed.");
        } finally {AdaptationTimings.defaults();}
    }
    /** A ghast's row must not flip between fire and explosion with every hit. */
    private static void speciesMainAttack() {
        String ghast="mob:minecraft:ghast";
        var ball=Set.of(SOURCE_CHECKED,REGISTRY_FIRE,PROJECTILE);var blast=Set.of(SOURCE_CHECKED,REGISTRY_EXPLOSION);
        var first=analyze(ghast,ball,6,20,false);
        check(first.profile()==AdaptationProfile.FIRE&&first.reason().equals("registry.fire"),"The first hit describes the creature");
        var tied=follow(first,ghast,blast,6,6);
        check(tied.profile()==AdaptationProfile.FIRE&&tied.reason().equals("species.main")&&tied.confidence()==50,"A tie does not hand the row to the latest hit");
        var again=follow(tied,ghast,ball,6,6);
        check(again.profile()==AdaptationProfile.FIRE&&again.reason().equals("registry.fire"),"A hit of the main kind keeps its exact fact");
        var side=follow(again,ghast,blast,6,6);
        check(side.profile()==AdaptationProfile.FIRE&&side.memory().votes().get(AdaptationProfile.EXPLOSION)==2,"Every other hit is still counted");
        var moved=side;for(int i=0;i<3;i++)moved=follow(moved,ghast,blast,6,6);
        check(moved.profile()==AdaptationProfile.EXPLOSION&&moved.reason().equals("registry.explosion"),"A creature that mostly explodes is shown as an explosion");
        var rare=follow(moved,ghast,ball,6,6);
        check(rare.profile()==AdaptationProfile.EXPLOSION&&rare.reason().equals("species.main")&&rare.confidence()>=50&&rare.confidence()<=90,"A rare attack stays a guess about the creature, never above the facts");
        var empty=follow(rare,ghast,ball,0,0);
        check(empty.memory().votes().equals(rare.memory().votes()),"Metadata without damage is not a hit");
        var attack=follow(analyze("attack:mod:orb",ball,6,20,false),"attack:mod:orb",blast,6,6);
        check(attack.profile()==AdaptationProfile.EXPLOSION&&attack.memory().votes().isEmpty(),"One ability has one nature: only creatures are voted on");
        var burn=analyze("mob:mod:ember",Set.of(SOURCE_CHECKED,FIRE),6,20,false);
        var bite=follow(burn,"mob:mod:ember",Set.of(SOURCE_CHECKED,PHYSICAL),6,6);
        check((bite.memory().evidenceMask()&(1L<<FIRE.ordinal()))==0,"An element seen on one attack does not stick to the creature's next one");
        var plague=analyze("effect:rats:plague",Set.of(HARMFUL_EFFECT),2,20,false);
        check(plague.profile()==AdaptationProfile.POISON&&AdaptationAnalysis.learned(plague,new LearnedPatterns.Match(AdaptationProfile.EFFECT,82,1))==plague,"\"Some effect\" says less than the plague's own name");
    }
    private static void catalogueProfilesAndGluedNames() {
        check(AdaptationProfile.forKey("mob:minecraft:ghast",false)==AdaptationProfile.FIRE&&AdaptationProfile.forKey("mob:minecraft:blaze",false)==AdaptationProfile.FIRE,"Fire throwers before the first hit");
        check(AdaptationProfile.forKey("mob:minecraft:creeper",false)==AdaptationProfile.EXPLOSION,"A creeper is an explosion");
        check(AdaptationProfile.forKey("mob:minecraft:skeleton",false)==AdaptationProfile.PROJECTILE&&AdaptationProfile.forKey("mob:minecraft:trader_llama",false)==AdaptationProfile.PROJECTILE,"Shooters are not melee");
        check(AdaptationProfile.forKey("mob:minecraft:guardian",false)==AdaptationProfile.MAGIC,"A guardian's beam is magic");
        check(AdaptationProfile.forKey("mob:minecraft:cow",false)==AdaptationProfile.PHYSICAL&&AdaptationProfile.forKey("mob:othermod:ghast",false)==AdaptationProfile.PHYSICAL,"Only the vanilla mobs are known by name");
        check(AdaptationProfile.fromName("projectile_shadowflameball",AdaptationProfile.ENVIRONMENT)==AdaptationProfile.FIRE,"Words glued together are read, the flame over the shadow");
        check(AdaptationProfile.fromName("shadowstrike",AdaptationProfile.ENVIRONMENT)==AdaptationProfile.DARKNESS,"Shadow");
        check(AdaptationProfile.fromName("frostbolt",AdaptationProfile.ENVIRONMENT)==AdaptationProfile.COLD&&AdaptationProfile.fromName("thunderclap",AdaptationProfile.ENVIRONMENT)==AdaptationProfile.ELECTRIC,"Cold and thunder");
        check(AdaptationProfile.fromName("lightningcraft:slam",AdaptationProfile.ENVIRONMENT)==AdaptationProfile.PHYSICAL,"The mod's id says nothing about the attack");
    }
    private static void countsNeverOverflow() {
        var full=new AdaptationAnalysis.Memory(Integer.MAX_VALUE,.5,.5,Integer.MAX_VALUE,0,Map.of(AdaptationProfile.FIRE,Integer.MAX_VALUE,AdaptationProfile.COLD,Integer.MAX_VALUE));
        var next=AdaptationAnalysis.observe(full,new AdaptationAnalysis.Observation("mob:mod:old",AdaptationProfile.ENVIRONMENT,Set.of(REGISTRY_COLD,HARMFUL_EFFECT),6,6,20,false));
        check(next.observations()==Integer.MAX_VALUE&&next.memory().harmfulObservations()==Integer.MAX_VALUE,"The largest count stays the largest");
        check(next.memory().votes().get(AdaptationProfile.COLD)==Integer.MAX_VALUE&&next.profile()==AdaptationProfile.FIRE&&next.confidence()==50,"Full votes neither overflow nor break the share");
        var counted=new AdaptationAnalysis.Memory(1_500_000,.5,.5,0,0);
        check(AdaptationAnalysis.observe(counted,new AdaptationAnalysis.Observation("damage:mod:x",AdaptationProfile.ENVIRONMENT,Set.of(FIRE),1,1,20,false)).observations()==1_500_001,"Hits are counted past the old million");
    }
    private static void nativeCorrectionAndDelivery() {
        String key="attack:other:unknown_beam";
        var electric=analyze(key,Set.of(ELECTRIC,PROJECTILE),6,20,false);
        var cold=follow(electric,key,Set.of(COLD,PROJECTILE),6,6);
        check(cold.profile()==AdaptationProfile.COLD,"Newly observed cold behaviour corrects an older electrical inference");
        check((cold.memory().evidenceMask()&(1L<<ELECTRIC.ordinal()))==0,"Corrected native elements are removed from saved evidence rather than resurfacing later");
        var carrier=follow(cold,key,Set.of(PROJECTILE),6,6);
        check(carrier.profile()==AdaptationProfile.COLD,"A generic projectile receipt retains its learned elemental behaviour");
        var magicCarrier=follow(carrier,key,Set.of(MAGIC,PROJECTILE),6,6);
        check(magicCarrier.profile()==AdaptationProfile.COLD,"Generic magic delivery does not erase an observed specific element");
        var quiet=follow(magicCarrier,key,Set.of(),6,6);
        check(quiet.profile()==AdaptationProfile.COLD,"A receipt without new semantic evidence retains the corrected category");
        var restored=new AdaptationAnalysis.Memory(quiet.memory().observations(),quiet.memory().meanRisk(),quiet.memory().peakRisk(),quiet.memory().harmfulObservations(),quiet.memory().evidenceMask());
        var reloaded=AdaptationAnalysis.observe(restored,new AdaptationAnalysis.Observation(key,AdaptationProfile.PHYSICAL,Set.of(PROJECTILE),6,6,20,false));
        check(reloaded.profile()==AdaptationProfile.COLD,"Native correction survives the existing saved-memory representation");
        var physical=analyze("attack:other:unknown",Set.of(PHYSICAL),4,20,false);
        var projectile=follow(physical,"attack:other:unknown",Set.of(PROJECTILE),4,4);
        check(projectile.profile()==AdaptationProfile.PROJECTILE && (projectile.memory().evidenceMask()&(1L<<PHYSICAL.ordinal()))==0,"A new delivery observation replaces obsolete physical/projectile carrier evidence");
        var held=follow(quiet,key,Set.of(CONTROL),0,0);
        var heldAgain=follow(held,key,Set.of(),0,0);
        check(held.profile()==AdaptationProfile.PHYSICAL && heldAgain.reason().equals("native.control"),"An observed restraint can correct stale native attack facts and is retained on later metadata-only observations");
    }
    private static void evidenceStrength() {
        String key="attack:other:electric_breath";
        var named=analyze(key,Set.of(),4,20,false);int initial=named.confidence();
        for(int n=0;n<100;n++)named=follow(named,key,Set.of(),4,4);
        check(named.confidence()==initial && named.reason().equals("name.hint"),"Repeating an unchanged name hint cannot create semantic certainty");
        var observed=follow(named,key,Set.of(ELECTRIC),4,4);
        check(observed.confidence()>named.confidence() && observed.reason().equals("native.electric"),"Observed behaviour materially increases evidence strength");
        var conflicting=analyze("attack:other:ambiguous",Set.of(COLD,ELECTRIC,PROJECTILE),4,20,false);
        check(conflicting.confidence()<observed.confidence(),"Competing elements lower confidence in the single chosen category");
        var resolved=follow(conflicting,"attack:other:ambiguous",Set.of(COLD),4,4);
        check(resolved.profile()==AdaptationProfile.COLD && resolved.confidence()>conflicting.confidence(),"A later unambiguous observation clears stale competing elements and restores confidence");
        var authoritative=follow(resolved,"attack:other:ambiguous",Set.of(REGISTRY_FIRE,COLD),4,4);
        check(authoritative.profile()==AdaptationProfile.FIRE && authoritative.confidence()>resolved.confidence(),"An explicit registry fact remains stronger than native observations");
        var namedBeam=analyze("attack:other:lightbeam",Set.of(),4,20,false);
        var attributed=follow(namedBeam,"attack:other:lightbeam",Set.of(HARMFUL_EFFECT,ATTRIBUTED_EFFECT),0,0);
        check(attributed.profile()==AdaptationProfile.EFFECT && attributed.confidence()==85 && attributed.reason().equals("native.harmful_effect"),"A concretely attributed harmful consequence outranks an unsupported lightbeam name hint");
        var electricBeam=follow(attributed,"attack:other:lightbeam",Set.of(ELECTRIC),4,4);
        check(electricBeam.profile()==AdaptationProfile.ELECTRIC,"A known native element remains stronger than an attributed effect of unknown category");
    }
    private static void representativeRisk() {
        String key="attack:other:measured_burn";
        var ordinary=analyze(key,Set.of(FIRE),1,20,false);
        var peak=analyze(key,Set.of(FIRE),40,20,false);
        check(peak.durations().first()>ordinary.durations().first(),"A newly measured dangerous hit immediately lengthens the learned recommendation");
        var metadata=follow(peak,key,Set.of(HARMFUL_EFFECT,FIRE),0,0);
        check(metadata.memory().meanRisk()==peak.memory().meanRisk() && metadata.memory().peakRisk()==peak.memory().peakRisk(),"A debuff metadata observation cannot dilute measured attack severity");
        var recovered=metadata;
        for(int n=0;n<160;n++)recovered=follow(recovered,key,Set.of(FIRE),1,1);
        check(recovered.memory().peakRisk()<peak.memory().peakRisk()*.2,"An isolated peak does not dominate the same source forever after many ordinary measured hits");
        check(recovered.durations().first()<=ordinary.durations().first()+20,"Future stage recommendations converge to representative repeated damage");
        var protectedHit=follow(recovered,key,Set.of(FIRE),1,0);
        check(Math.abs(protectedHit.memory().meanRisk()-recovered.memory().meanRisk())<.001,"Full earned mitigation does not make an unchanged raw hit appear less dangerous");
        var renewed=follow(protectedHit,key,Set.of(FIRE),40,40);
        check(renewed.memory().peakRisk()>=peak.memory().peakRisk() && renewed.durations().first()>protectedHit.durations().first(),"A real renewed spike is recognized immediately after old outliers have aged out");
    }
    private static void effectThreat() {
        double weak=EffectThreat.estimate(AdaptationProfile.POISON,0,60,false);
        double strong=EffectThreat.estimate(AdaptationProfile.POISON,4,1200,false);
        double endless=EffectThreat.estimate(AdaptationProfile.EFFECT,255,Integer.MAX_VALUE,true);
        check(weak<strong,"A stronger and longer effect is estimated as more dangerous");
        check(weak>0 && endless<=.85 && EffectThreat.estimate(AdaptationProfile.EFFECT,-5,-20,false)>=0,"Effect threat stays bounded for extreme or malformed values");
        check(EffectThreat.estimate(AdaptationProfile.POISON,1,200,false)>EffectThreat.estimate(AdaptationProfile.EFFECT,1,200,false),"Poison and magic effects start from a higher base risk");
        var mild=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation("effect:other:venom",AdaptationProfile.EFFECT,Set.of(HARMFUL_EFFECT),0,0,20,false,weak));
        var severe=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation("effect:other:venom",AdaptationProfile.EFFECT,Set.of(HARMFUL_EFFECT),0,0,20,false,strong));
        check(severe.durations().first()>mild.durations().first(),"A severe harmful effect takes longer to adapt to than a mild one");
        var ignored=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation("damage:other:burn",AdaptationProfile.FIRE,Set.of(FIRE),1,1,20,false,.85));
        var plain=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation("damage:other:burn",AdaptationProfile.FIRE,Set.of(FIRE),1,1,20,false));
        check(ignored.memory().meanRisk()==plain.memory().meanRisk(),"Effect threat applies only to harmful effects, not ordinary damage");
    }
    private static AdaptationAnalysis.Decision follow(AdaptationAnalysis.Decision previous,String key,Set<AdaptationAnalysis.Evidence> evidence,double raw,double actual) {
        return AdaptationAnalysis.observe(previous.memory(),new AdaptationAnalysis.Observation(key,AdaptationProfile.ENVIRONMENT,evidence,raw,actual,20,false));
    }
    private static void authoritativeReload() {
        String key="attack:other:lightning_breath";
        var electric=analyze(key,Set.of(REGISTRY_ELECTRIC,PROJECTILE),6,20,false);
        var cold=AdaptationAnalysis.observe(electric.memory(),new AdaptationAnalysis.Observation(key,AdaptationProfile.MAGIC,
                Set.of(REGISTRY_COLD,PROJECTILE),6,6,20,false));
        check(cold.profile()==AdaptationProfile.COLD && cold.reason().equals("registry.cold"),"A current authoritative cold tag replaces a previously learned electric tag after reload");
        var weak=AdaptationAnalysis.observe(cold.memory(),new AdaptationAnalysis.Observation(key,AdaptationProfile.MAGIC,
                Set.of(PROJECTILE),6,6,20,false));
        check(weak.profile()==AdaptationProfile.COLD && weak.reason().equals("registry.cold"),"Later generic projectile evidence retains the updated cold registry identity");
        check((weak.memory().evidenceMask()&(1L<<REGISTRY_ELECTRIC.ordinal()))==0,"Superseded authoritative tag is removed from saved memory");
        var tagged=analyze("attack:other:unknown_round",Set.of(REGISTRY_FIRE,PROJECTILE,SOURCE_CHECKED),4,20,false);
        var metadata=follow(tagged,"attack:other:unknown_round",Set.of(HARMFUL_EFFECT),0,0);
        check(metadata.profile()==AdaptationProfile.FIRE,"Source-less debuff metadata does not clear authoritative source facts");
        var removed=follow(metadata,"attack:other:unknown_round",Set.of(SOURCE_CHECKED,PROJECTILE),4,4);
        check(removed.profile()==AdaptationProfile.PROJECTILE && (removed.memory().evidenceMask()&(1L<<REGISTRY_FIRE.ordinal()))==0,"A checked source with no longer-present fire tags removes stale registry classification after reload");
        var retained=follow(removed,"attack:other:unknown_round",Set.of(HARMFUL_EFFECT),0,0);
        check(retained.profile()==AdaptationProfile.PROJECTILE,"A removed registry fact does not reappear during later metadata observations");
        var held=analyze("condition:other:electric_time_stop",Set.of(CONTROL),0,20,false);
        check(held.profile()==AdaptationProfile.PHYSICAL && held.reason().equals("native.control"),"An observed temporal restraint outranks an electrical-looking name");
        var ordinary=analyze("attack:other:ember_bite",Set.of(PHYSICAL),4,20,false);
        check(ordinary.profile()==AdaptationProfile.PHYSICAL,"A physical bite does not inherit elemental meaning from its name");
        var beam=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation("damage:other:light_beam",AdaptationProfile.LIGHT,Set.of(SOURCE_CHECKED,PROJECTILE),4,4,20,false));
        check(beam.profile()==AdaptationProfile.LIGHT && beam.reason().equals("native.projectile_element"),"A generic projectile tag keeps the element the source classification names");
        var bolt=AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation("damage:other:bolt",AdaptationProfile.MAGIC,Set.of(SOURCE_CHECKED,PROJECTILE),4,4,20,false));
        check(bolt.profile()==AdaptationProfile.PROJECTILE,"A generic magic fallback does not override projectile delivery");
    }
    private static void allExplicitProfiles() {
        var previous=analyze("attack:other:lightning_breath",Set.of(REGISTRY_ELECTRIC),6,20,false);
        for(AdaptationProfile profile:AdaptationProfile.values()) {
            var fact=AdaptationAnalysis.Evidence.valueOf("REGISTRY_"+profile.name());
            var decision=AdaptationAnalysis.observe(previous.memory(),new AdaptationAnalysis.Observation(
                    "attack:other:electric_fire_arrow",AdaptationProfile.MAGIC,Set.of(fact,PHYSICAL,PROJECTILE),6,6,20,false));
            check(decision.profile()==profile && decision.reason().equals("registry."+profile.name().toLowerCase(Locale.ROOT)),
                    "Explicit datapack category "+profile+" overrides previous registry identity, native attack behaviour, and name hints");
            var remembered=AdaptationAnalysis.observe(decision.memory(),new AdaptationAnalysis.Observation(
                    "attack:other:electric_fire_arrow",AdaptationProfile.MAGIC,Set.of(PROJECTILE),6,6,20,false));
            check(remembered.profile()==profile,"Explicit category "+profile+" survives later weak observations");
            previous=decision;
        }
        check(FIRE.ordinal()==12 && BENEFICIAL_EFFECT.ordinal()==26,"Existing stored evidence mask ordinals remain compatible");
    }
    private static void vocabularyAndVanillaTypes() {
        check(AdaptationProfile.vanillaDamage("sting")==AdaptationProfile.PHYSICAL && AdaptationProfile.fromName("sting",null)==AdaptationProfile.PHYSICAL,
                "A bee's sting is melee, not a projectile");
        check(AdaptationProfile.vanillaDamage("in_wall")==AdaptationProfile.SUFFOCATION && AdaptationProfile.vanillaDamage("cramming")==AdaptationProfile.SUFFOCATION
                && AdaptationProfile.vanillaDamage("thorns")==AdaptationProfile.PHYSICAL && AdaptationProfile.vanillaDamage("outside_border")==AdaptationProfile.ENVIRONMENT,
                "Untagged vanilla damage types have their game meaning");
        check(AdaptationProfile.vanillaDamage("mob_attack")==null && AdaptationProfile.vanillaDamage("lava")==null && AdaptationProfile.vanillaDamage("generic")==null,
                "Melee, tagged and opaque vanilla types stay with their existing evidence");
        // Names seen in the tested mods' damage types and effects.
        Map<String,AdaptationProfile> names=Map.ofEntries(Map.entry("gorgon",AdaptationProfile.MAGIC),Map.entry("hypothermia",AdaptationProfile.COLD),
                Map.entry("cold_fire_damage",AdaptationProfile.COLD),Map.entry("plague_damage",AdaptationProfile.POISON),Map.entry("sculk_infected",AdaptationProfile.POISON),
                Map.entry("thrown_axe",AdaptationProfile.PROJECTILE),Map.entry("ran_over",AdaptationProfile.PHYSICAL),Map.entry("chilling_breath",AdaptationProfile.COLD),
                Map.entry("burned",AdaptationProfile.FIRE),Map.entry("lich_bomb",AdaptationProfile.EXPLOSION),Map.entry("eclipsed",AdaptationProfile.DARKNESS),
                Map.entry("damage_combat",AdaptationProfile.PHYSICAL),Map.entry("damage_curse",AdaptationProfile.MAGIC));
        names.forEach((name,profile)->check(AdaptationProfile.fromName(name,null)==profile,name+" is "+profile));
        check(AdaptationProfile.fromName("Piercing Blood",AdaptationProfile.MAGIC)==AdaptationProfile.MAGIC
                && AdaptationProfile.fromName("Overhead Attack",AdaptationProfile.MAGIC)==AdaptationProfile.MAGIC,"Cursed techniques without a clear word keep their magic table profile");
        check(AdaptationProfile.fromTag("curse")==AdaptationProfile.MAGIC && AdaptationProfile.fromTag("is_magic")==AdaptationProfile.MAGIC
                && AdaptationProfile.fromTag("entity_physical")==AdaptationProfile.PHYSICAL && AdaptationProfile.fromTag("combat")==AdaptationProfile.PHYSICAL,
                "Mod tags that name a category are read");
        for(String relation:List.of("witch_resistant_to","fire_immune","bypasses_shield","no_spirit_regen","breaks_lich_shields","ring_of_odin_immune","environmental","reduced_flinch"))
            check(AdaptationProfile.fromTag(relation)==null,"Tag "+relation+" says who resists or what is bypassed, not what the attack is");
    }
    private static void modTagsAndCompatibilityTables() {
        var tagged=analyze("damage:other:opaque",Set.of(SOURCE_CHECKED,TAG_MAGIC),4,20,false);
        check(tagged.profile()==AdaptationProfile.MAGIC && tagged.reason().equals("tag.category") && tagged.confidence()==78,"A mod tag classifies an otherwise opaque type");
        var twoTags=analyze("damage:other:opaque",Set.of(SOURCE_CHECKED,TAG_MAGIC,TAG_FIRE),4,20,false);
        check(twoTags.profile()==AdaptationProfile.FIRE && twoTags.confidence()<tagged.confidence(),"Competing tags pick the specific element with lower confidence");
        check(analyze("damage:other:opaque",Set.of(SOURCE_CHECKED,REGISTRY_FIRE,TAG_MAGIC),4,20,false).reason().equals("registry.fire"),"Vanilla tags outrank mod tags");
        check(analyze("damage:minecraft:mob_projectile",Set.of(SOURCE_CHECKED,PROJECTILE,TAG_PHYSICAL),4,20,false).profile()==AdaptationProfile.PROJECTILE,
                "Observed delivery outranks a broad mod tag such as vampirism:entity_physical");
        check(analyze("damage:other:frost_bite",Set.of(SOURCE_CHECKED,TAG_MAGIC),4,20,false).profile()==AdaptationProfile.MAGIC,"A mod tag outranks a name hint");
        var debuff=follow(tagged,"damage:other:opaque",Set.of(HARMFUL_EFFECT,ATTRIBUTED_EFFECT),0,0);
        check(debuff.profile()==AdaptationProfile.MAGIC,"An attributed debuff does not replace the tag's category");
        var untagged=follow(debuff,"damage:other:opaque",Set.of(SOURCE_CHECKED),4,4);
        check((untagged.memory().evidenceMask()&(1L<<TAG_MAGIC.ordinal()))==0 && !untagged.reason().equals("tag.category"),"A tag removed by a datapack is forgotten on the next checked hit");
        var known=analyzeWith("condition:other:frozen",AdaptationProfile.COLD,Set.of(COMPAT_PROFILE));
        check(known.profile()==AdaptationProfile.COLD && known.reason().equals("compat.known") && known.confidence()>=85,
                "A compatibility table profile is known, not a low-confidence fallback, and a later debuff does not revise it");
        check(analyzeWith("condition:other:technique/7",AdaptationProfile.FIRE,Set.of(SOURCE_CHECKED,COMPAT_PROFILE,TAG_MAGIC)).profile()==AdaptationProfile.FIRE,
                "A table written for one technique outranks its mod's shared tag");
        check(analyzeWith("condition:other:capture/x",AdaptationProfile.PHYSICAL,Set.of(COMPAT_PROFILE,CONTROL)).reason().equals("native.control"),"Observed control keeps its specific reason");
        check(analyzeWith("condition:other:technique/8",AdaptationProfile.MAGIC,Set.of(SOURCE_CHECKED,COMPAT_PROFILE,PROJECTILE)).profile()==AdaptationProfile.PROJECTILE,
                "Observed delivery still outranks a generic magic table profile");
        check(SOURCE_CHECKED.ordinal()==29 && ATTRIBUTED_EFFECT.ordinal()==30 && COMPAT_PROFILE.ordinal()==31 && TAG_LIGHT.ordinal()==43
                && AdaptationAnalysis.Evidence.values().length<64,"Saved evidence masks keep their ordinals and fit in one long");
    }
    private static AdaptationAnalysis.Decision analyzeWith(String key,AdaptationProfile fallback,Set<AdaptationAnalysis.Evidence> evidence) {
        return AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation(key,fallback,evidence,4,4,20,false));
    }
    private static void progressionFreeze(AdaptationAnalysis.Decision initial,AdaptationAnalysis.Decision changed) {
        var state=new AdaptationState();state.applyAnalysis(KEY,initial);state.hit(KEY,false,initial.profile());
        for(int i=0;i<30;i++)state.tick();
        var before=state.snapshots().get(0);
        state.applyAnalysis(KEY,changed);var after=state.snapshots().get(0);
        check(after.work()==before.work() && after.duration()==before.duration() && after.remaining()==before.remaining(),"New evidence changes neither elapsed work nor current timer length");
        check(after.profile()==changed.profile() && after.analyzedDurations().equals(changed.durations()),"New evidence does update category and future recommendations");
        state.hit(KEY,false,changed.profile());var faster=state.snapshots().get(0);
        check(faster.remaining()==before.remaining() && Math.abs(faster.speed()-1.3)<1e-9,"A repeat hit changes real progression speed rather than displayed remaining work");
        double work=faster.work();state.tick();check(Math.abs(state.snapshots().get(0).work()-work-1.3)<1e-8,"The server actually consumes 1.30 adaptation ticks per real tick");
        int target=0;while(state.stage(KEY)==0 && target++<10000)state.tick();
        check(state.stage(KEY)==1 && state.snapshots().get(0).duration()==changed.durations().second(),"The next stage uses the newest recommendation");
        check(Math.abs(state.damageMultiplier(KEY)-.8)<1e-9,"Analyzed sources retain real earned damage reduction");
        var boss=new AdaptationState();boss.applyAnalysis(KEY,initial);boss.hit(KEY,true,initial.profile());
        check(boss.snapshots().get(0).profile()==initial.profile(),"A boss flag must not replace specific analyzed attack semantics with magic");
    }
    private static void manualOverrides(AdaptationAnalysis.Decision initial,AdaptationAnalysis.Decision changed) {
        var state=new AdaptationState();state.applyAnalysis(KEY,initial);state.hit(KEY,false);state.tick();
        AdaptationTimings.replace(Map.of(),Map.of(KEY,List.of(5,0,0,0,0)));
        check(state.snapshots().get(0).duration()==100 && state.snapshots().get(0).work()==1,"Live per-source Configured override changes actual running duration and preserves work");
        state.applyAnalysis(KEY,changed);check(state.snapshots().get(0).duration()==100,"Further analysis cannot defeat a source override");
        AdaptationTimings.replace(Map.of(AdaptationProfile.FIRE,StageDurations.seconds(9,12,13,14,15)),Map.of());
        check(state.snapshots().get(0).duration()==180,"Manual category values outrank frozen learned durations");
        AdaptationTimings.defaults();
        check(state.snapshots().get(0).duration()==initial.durations().first(),"Removing manual overrides restores the original frozen recommendation");
    }
    private static void restorationAndOptOut(AdaptationAnalysis.Decision initial) {
        var state=new AdaptationState();state.applyAnalysis(KEY,initial);state.hit(KEY,false);state.hit(KEY,false);state.tick();
        var copy=AdaptationState.restore(true,state.snapshots(),state.revision());
        check(copy.snapshots().equals(state.snapshots()),"Save/restore retains analyzed future schedule, frozen duration, metadata, acceleration and work");
        var near=state.snapshots().get(0);
        var fractional=new AdaptationState.Snapshot(KEY,true,false,0,near.duration()-.1,1,true,near.profile(),
                near.analyzedDurations(),near.activeDuration(),near.analysisConfidence(),near.analysisReason(),near.analysisObservations());
        check(AdaptationState.restore(true,List.of(fractional),0).snapshots().get(0).work()==fractional.work(),"Save/restore preserves fractional accelerated work immediately before completion");
        int old=copy.snapshots().get(0).duration();AdaptationTimings.setAnalysisEnabled(false);
        check(copy.snapshots().get(0).duration()==AdaptationProfile.PHYSICAL.duration(0),"Analyzer opt-out uses the original configured schedule");
        check(!copy.applyAnalysis(KEY,initial),"Opt-out prevents background analysis changes");
        AdaptationTimings.setAnalysisEnabled(true);check(copy.snapshots().get(0).duration()==old,"Re-enabling restores saved knowledge and frozen recommendation");
        copy.clearPending();check(copy.snapshots().get(0).activeDuration()==0 && copy.snapshots().get(0).analyzedDurations()!=null,"Death clears only pending work, retaining learned analysis");
        var empty=new AdaptationState();check(!empty.applyAnalysis("effect:other:buff",analyze("effect:other:buff",Set.of(BENEFICIAL_EFFECT),0,20,false)) && empty.snapshots().isEmpty(),"Positive effects never create progression entries");
    }
    private static AdaptationAnalysis.Decision analyze(String key,Set<AdaptationAnalysis.Evidence> evidence,double raw,double health,boolean boss) {
        return AdaptationAnalysis.observe(null,new AdaptationAnalysis.Observation(key,AdaptationProfile.ENVIRONMENT,evidence,raw,raw,health,boss));
    }
    /** 4.45.0: the corpus compatibility table, the mob seeds and the new analyzer words (design mod-compat-616-2026-11-07). */
    private static void compatTable445() {
        check(AdaptationProfile.fromName("minigun_bullet",null)==AdaptationProfile.PROJECTILE,"Minigun is a projectile");
        check(AdaptationProfile.fromName("railgun",null)==AdaptationProfile.PROJECTILE,"Railgun is a projectile");
        check(AdaptationProfile.fromName("taser",null)==AdaptationProfile.ELECTRIC,"Taser is electric");
        check(AdaptationProfile.fromName("flak",null)==AdaptationProfile.EXPLOSION,"Flak explodes");
        check(AdaptationProfile.fromName("rot",null)==AdaptationProfile.POISON,"Rot is poison");
        check(AdaptationProfile.fromName("drenched",null)==AdaptationProfile.SUFFOCATION,"Drench suffocates");
        check(AdaptationProfile.fromName("swarm",null)==AdaptationProfile.PHYSICAL,"A swarm bites");
        check(AdaptationProfile.fromName("punctured",null)==AdaptationProfile.PHYSICAL,"Punctured is physical");
        try {
            java.nio.file.Path dir=java.nio.file.Path.of("src/main/resources/data/adaptationwheel/tags/damage_type/adaptation");
            java.util.Set<String> seen=new java.util.HashSet<>();
            int entries=0;
            try(var files=java.nio.file.Files.list(dir)){
                for(java.nio.file.Path file:(Iterable<java.nio.file.Path>)files::iterator){
                    String name=file.getFileName().toString().replace(".json","").toUpperCase(java.util.Locale.ROOT);
                    AdaptationProfile.valueOf(name);
                    var tag=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
                    for(var value:tag.getAsJsonArray("values")){
                        if(!value.isJsonObject())continue;
                        var entry=value.getAsJsonObject();
                        check(entry.has("required")&&!entry.get("required").getAsBoolean(),"A table entry is optional: "+entry);
                        String id=entry.get("id").getAsString();
                        check(!id.startsWith("minecraft:"),"The table leaves vanilla alone: "+id);
                        check(seen.add(id),"A damage type sits in one category only: "+id);
                        entries++;
                    }
                }
            }
            check(entries>=400,"About 400 corpus damage types are in the table, got "+entries);
            var seeds=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/knowledge/pretrained-v1.json"))).getAsJsonObject();
            String text=seeds.toString();
            int mobs=text.split("seed:compat/",-1).length-1;
            check(mobs>=130,"Mob seeds of the corpus are present, got "+mobs);
        } catch(java.io.IOException failure){throw new AssertionError(failure);}
    }

    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
