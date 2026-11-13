package ru.pasha.adaptationwheel.core;

import java.util.*;

public final class ResearchMemoryTest {
    public static void main(String[] args){run();}
    private static ResearchMemory.Context context(String key){return new ResearchMemory.Context(key,"mod:mage","mod:orb","mod:chill","health:3","far");}
    private static void observed(ResearchMemory memory,String key,AdaptationProfile profile,int count){for(int i=0;i<count;i++)memory.observe(context(key),profile,true,8,8,20);}
    public static void run(){
        var model=new ResearchMemory();String key="attack:mod:opaque";
        model.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.FIRE,70,"seed:curated"));
        check(model.infer(context(key)).reason().equals("learned.pretrained"),"Bundled reference knowledge must remain distinguishable");
        check(model.infer(context(key)).confidence()==70,"A curated confidence must not be inflated");
        model.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.COLD,96,"world:damage-tags"));
        check(model.fact(key).profile()==AdaptationProfile.COLD&&model.infer(context(key)).confidence()==82,"Current world evidence wins over seeds without claiming certainty");
        model.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.POISON,95,"registry:effect-tags"));
        check(model.fact(key)==null&&model.infer(context(key))==null,"Equally strong contradictory facts must abstain");
        var restored=ResearchMemory.restore(model.facts(),model.samples());
        check(restored.fact(key)==null&&restored.contradictionCount()>0,"Fact ambiguity must survive persistence");
        restored.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.ELECTRIC,88,"live:registry.electric"));
        check(restored.fact(key).profile()==AdaptationProfile.ELECTRIC,"Real source evidence must correct stale world metadata");
        restored.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.FIRE,100,"seed:later"));
        check(restored.fact(key).profile()==AdaptationProfile.ELECTRIC,"A confident seed cannot overwrite actual observed evidence");
        restored.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.COLD,88,"LIVE:registry.cold"));
        check(restored.fact(key)==null,"A changed equally authoritative live fact must not silently erase the old contradiction");
        observed(restored,key,AdaptationProfile.COLD,2);
        check(restored.fact(key)==null,"Two subsequent receipts cannot resolve a stored factual conflict");
        observed(restored,key,AdaptationProfile.COLD,1);
        check(restored.fact(key).profile()==AdaptationProfile.COLD,"Three actual consistent corrections may resolve the old conflict");
        var phase=new ResearchMemory.Context(key,"mod:mage","mod:orb","mod:chill","health:1","far");
        restored.observe(phase,AdaptationProfile.ELECTRIC,true,8,8,20);
        check(restored.fact(key)==null,"A different phase with actual contrary evidence prevents a global classification");
        restored.learnFact(new ResearchMemory.Fact("damage:mod:unknown",AdaptationProfile.COLD,100,"registryFake"));
        check(restored.fact("damage:mod:unknown")==null,"A source name merely starting like a trusted prefix is not evidence");
        var seeded=new ResearchMemory();seeded.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.FIRE,80,"seed:curated"));
        for(int i=0;i<3;i++)seeded.observe(context(key),AdaptationProfile.FIRE,false,8,0,20);
        check(seeded.infer(context(key))!=null,"Mitigated actual HP alone must not invalidate a valid seed category");
        seeded.observe(context(key),AdaptationProfile.FIRE,false,100,100,20);
        check(seeded.infer(context(key))==null,"A weak seed must not bypass suspicion about an unexpectedly changed attack variant");
        observed(seeded,key,AdaptationProfile.COLD,3);
        check(seeded.infer(context(key)).profile()==AdaptationProfile.COLD&&seeded.infer(context(key)).reason().equals("learned.observation"),"Three contrary actual facts replace a wrong seed for this exact context");
        seeded.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.ELECTRIC,96,"world:current-tags"));
        seeded.observe(context(key),AdaptationProfile.COLD,false,1000,1000,20);
        check(seeded.infer(context(key)).profile()==AdaptationProfile.ELECTRIC,"Severity variation cannot erase a current authoritative registry category");
        model=new ResearchMemory();
        observed(model,"attack:mod:a",AdaptationProfile.COLD,20);observed(model,"attack:mod:b",AdaptationProfile.COLD,1);
        check(model.infer(context(key))==null,"Repeated hits from one ability are not distinct examples");
        observed(model,"attack:mod:c",AdaptationProfile.COLD,1);
        var learned=model.infer(context(key));
        check(learned!=null&&learned.profile()==AdaptationProfile.COLD&&learned.support()==3&&learned.confidence()<85,"Three factual different abilities may cautiously transfer");
        var other=new ResearchMemory.Context(key,"mod:other","mod:orb","mod:chill","health:3","far");
        check(model.infer(other)==null,"Another actor type cannot borrow an elemental analogy");
        other=new ResearchMemory.Context(key,"mod:mage","mod:orb","mod:chill","health:1","far");
        check(model.infer(other)==null,"A different boss phase must be studied independently");
        other=new ResearchMemory.Context(key,"mod:mage","mod:orb","mod:poison","health:3","far");
        check(model.infer(other)==null,"Different actual effects invalidate an identity match");
        other=new ResearchMemory.Context(key,"mod:mage","mod:orb","mod:chill","health:3","near");
        check(model.infer(other)==null,"Melee-range and ranged variants must not be conflated");
        observed(model,"attack:mod:d",AdaptationProfile.FIRE,1);
        check(model.infer(context(key))==null,"One contrary factual ability immediately blocks transfer");
        observed(model,"attack:mod:d",AdaptationProfile.COLD,2);
        check(model.infer(context(key))==null,"A contradictory sample requires repeated factual correction");
        observed(model,"attack:mod:d",AdaptationProfile.COLD,1);
        check(model.infer(context(key)).profile()==AdaptationProfile.COLD,"Repeated consistent correction recovers a mistaken sample");
        var exact=model.infer(context("attack:mod:d"));
        check(exact.reason().equals("learned.observation")&&exact.confidence()<=82,"Own factual experience has explicitly empirical, tentative provenance");
        model.observe(context("attack:mod:d"),AdaptationProfile.COLD,false,100,100,20);
        check(model.infer(context("attack:mod:d"))==null&&model.infer(context(key))==null,"Unexpected unmitigated severity must suspend uncertain analogy");
        observed(model,"attack:mod:d",AdaptationProfile.COLD,3);
        check(model.infer(context(key))!=null,"Fresh factual evidence can re-establish a changed variant");
        restored=ResearchMemory.restore(model.facts(),model.samples());
        check(restored.infer(context(key))!=null&&restored.observationCount()==model.observationCount()&&restored.contradictionCount()==model.contradictionCount(),"Observed facts, corrections and counts survive reload");
        var weak=new ResearchMemory();
        for(int i=0;i<1000;i++)weak.observe(context("attack:mod:guess"),AdaptationProfile.FIRE,false,4,2,20);
        check(weak.infer(context("attack:mod:guess"))==null,"Repeated weak guesses must never teach themselves");
        weak.observe(context("attack:mod:invalid"),AdaptationProfile.COLD,true,Double.NaN,Double.POSITIVE_INFINITY,Double.NaN);
        var sample=weak.samples().get(weak.samples().size()-1);
        check(Double.isFinite(sample.meanRaw())&&Double.isFinite(sample.meanActual())&&Double.isFinite(sample.meanFraction()),"Invalid mod numbers must remain finite");
        check(new ResearchMemory.Context("x".repeat(1000),"a","b","","","").key().isEmpty(),"Oversized identities must not collide through truncation");
        check(new ResearchMemory.Context(key,"a","b","","x".repeat(1000),"").key().isEmpty(),"An oversized boss phase cannot collapse into a familiar phase");
        check(new ResearchMemory.Context(key,"a\u0000","b","","","").key().isEmpty(),"Invalid actor identities must not become an ownerless context");
        check(new ResearchMemory.Context(key,"a","b","z:b,z:a,z:b","","").effects().equals("z:a,z:b"),"Effect-set order and duplicates must not split equivalent experiences");
        model=new ResearchMemory();
        for(int i=0;i<10_000;i++)model.learnFact(new ResearchMemory.Fact("effect:mod:"+i,AdaptationProfile.EFFECT,70,"seed:test"));
        for(int i=0;i<2_000;i++)observed(model,"attack:mod:"+i,AdaptationProfile.PHYSICAL,1);
        check(model.factCount()==10_000&&model.samples().size()==2_000&&model.fact("effect:mod:0")!=null,"Nothing learned is dropped to make room for newer rows");
        check(ResearchMemory.restore(null,null).factCount()==0,"Null persistence collections are valid empty memory");
        var badFacts=new ArrayList<ResearchMemory.Fact>(Collections.nCopies(3,null));
        badFacts.add(new ResearchMemory.Fact(key,AdaptationProfile.COLD,90,"seed:too-late"));
        check(ResearchMemory.restore(badFacts,Collections.singletonList(null)).factCount()==1,"Restore skips null rows and keeps the valid ones after them");
        mergesOlderWorldWithNewerArchive();
        repeatedMergesDoNotInventEvidence();
        conflictingSnapshotsRequireFreshCorrections();
        weakCopiesCannotReplaceFacts();
        mergeBoundsAndMalformedInputs();
        System.out.println("ResearchMemoryTest: provenance, ambiguity, empirical recall, causal identity, correction, outcome anomalies, no self-training, persistence, bounded merge, idempotence and fresh conflict correction passed.");
    }
    private static void mergesOlderWorldWithNewerArchive(){
        var world=new ResearchMemory();var archive=new ResearchMemory();
        world.learnFact(new ResearchMemory.Fact("damage:mod:old",AdaptationProfile.FIRE,70,"seed:curated"));
        world.learnFact(new ResearchMemory.Fact("damage:mod:shared",AdaptationProfile.FIRE,95,"world:old"));
        archive.learnFact(new ResearchMemory.Fact("damage:mod:new",AdaptationProfile.COLD,90,"live:measured"));
        archive.learnFact(new ResearchMemory.Fact("damage:mod:shared",AdaptationProfile.ELECTRIC,80,"live:measured"));
        observed(world,"attack:mod:old",AdaptationProfile.COLD,3);
        observed(world,"attack:mod:shared",AdaptationProfile.COLD,2);
        observed(archive,"attack:mod:new",AdaptationProfile.MAGIC,3);
        observed(archive,"attack:mod:shared",AdaptationProfile.COLD,7);
        var worldFacts=world.facts();var worldSamples=world.samples();var archiveFacts=archive.facts();var archiveSamples=archive.samples();
        var merged=ResearchMemory.merge(world,archive);
        check(merged.factCount()==3&&merged.fact("damage:mod:old")!=null&&merged.fact("damage:mod:new")!=null,"Old-world and newer-archive knowledge must both survive");
        check(merged.fact("damage:mod:shared").profile()==AdaptationProfile.ELECTRIC,"Live archive facts outrank stale world metadata through the ordinary authority policy");
        check(merged.samples().size()==3&&sample(merged,"attack:mod:shared").observations()==7,"Shared observations use the larger count rather than pooling snapshots");
        check(world.facts().equals(worldFacts)&&world.samples().equals(worldSamples)&&archive.facts().equals(archiveFacts)&&archive.samples().equals(archiveSamples),"Merge must leave both original snapshots unchanged");
        check(merged.infer(context("attack:mod:old"))!=null&&merged.infer(context("attack:mod:new"))!=null,"Exact factual recall from either world remains usable");
        var reverse=ResearchMemory.merge(archive,world);
        check(reverse.fact("damage:mod:shared").profile()==AdaptationProfile.ELECTRIC,"Operand order cannot lower factual authority");
    }
    private static void repeatedMergesDoNotInventEvidence(){
        var original=new ResearchMemory();
        original.learnFact(new ResearchMemory.Fact("damage:mod:copy",AdaptationProfile.COLD,80,"live:test"));
        observed(original,"attack:mod:copy",AdaptationProfile.COLD,2);
        var copy=ResearchMemory.restore(original.facts(),original.samples());
        var merged=ResearchMemory.merge(original,copy);
        for(int i=0;i<20;i++)merged=ResearchMemory.merge(merged,copy);
        check(merged.samples().equals(original.samples())&&merged.facts().equals(original.facts()),"Re-merging copied snapshots must be exactly idempotent");
        check(merged.observationCount()==2&&merged.contradictionCount()==0&&merged.infer(context("attack:mod:copy"))==null,"Two receipts cannot become three merely by importing copies");
        observed(merged,"attack:mod:copy",AdaptationProfile.COLD,1);
        check(original.observationCount()==2&&merged.observationCount()==3,"A merged memory owns an independent observation history");
    }
    private static void conflictingSnapshotsRequireFreshCorrections(){
        var left=new ResearchMemory();var right=new ResearchMemory();String key="attack:mod:conflict";
        observed(left,key,AdaptationProfile.FIRE,8);observed(right,key,AdaptationProfile.COLD,12);
        var merged=ResearchMemory.merge(left,right);
        check(merged.infer(context(key))==null&&sample(merged,key).trustedObservations()==0,"Different factual classifications of the same context must abstain immediately");
        check(sample(merged,key).observations()==12&&sample(merged,key).contradictions()==1,"Conflict does not fabricate received hits or sum copied counts");
        var reversed=ResearchMemory.merge(right,left);
        check(reversed.samples().equals(merged.samples()),"Conflicting source order cannot choose a learned category");
        var copied=ResearchMemory.restore(merged.facts(),merged.samples());
        for(int i=0;i<5;i++)copied=ResearchMemory.merge(copied,merged);
        check(copied.samples().equals(merged.samples()),"An unresolved copied conflict stays exactly idempotent");
        var repeated=ResearchMemory.merge(merged,right);
        check(repeated.samples().equals(merged.samples())&&repeated.infer(context(key))==null,"The confident snapshot which caused a conflict cannot change it by re-importing");
        observed(merged,key,AdaptationProfile.COLD,2);
        check(merged.infer(context(key))==null,"Two fresh factual corrections still abstain");
        merged=ResearchMemory.restore(merged.facts(),merged.samples());
        observed(merged,key,AdaptationProfile.COLD,1);
        check(merged.infer(context(key))!=null&&merged.infer(context(key)).profile()==AdaptationProfile.COLD,"Only three fresh consistent factual observations settle a merged conflict");
        var factsA=new ResearchMemory();var factsB=new ResearchMemory();
        factsA.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.FIRE,85,"live:a"));
        factsB.learnFact(new ResearchMemory.Fact(key,AdaptationProfile.COLD,85,"live:b"));
        var factConflict=ResearchMemory.merge(factsA,factsB);
        check(factConflict.fact(key)==null&&factConflict.infer(context(key))==null,"Equal-authority imported fact disagreements remain explicit");
        observed(factConflict,key,AdaptationProfile.COLD,3);
        check(factConflict.fact(key).profile()==AdaptationProfile.COLD,"Fresh factual correction can resolve imported fact ambiguity");
    }
    private static void weakCopiesCannotReplaceFacts(){
        var factual=new ResearchMemory();var weak=new ResearchMemory();String key="attack:mod:factual";
        observed(factual,key,AdaptationProfile.COLD,3);
        for(int i=0;i<50;i++)weak.observe(context(key),AdaptationProfile.FIRE,false,8,8,20);
        for(var merged:List.of(ResearchMemory.merge(factual,weak),ResearchMemory.merge(weak,factual))){
            check(merged.infer(context(key)).profile()==AdaptationProfile.COLD,"More numerous weak guesses cannot replace a factual category");
            check(sample(merged,key).observations()==50&&sample(merged,key).trustedObservations()==3,"Weak history does not multiply trusted support");
        }
    }
    private static void mergeBoundsAndMalformedInputs(){
        check(ResearchMemory.merge(null,null).samples().isEmpty(),"Missing world and archive are empty memory");
        var malformed=new ResearchMemory.Sample(new ResearchMemory.Context("bad\u0000key","","","","",""),AdaptationProfile.COLD,
                Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY);
        var finite=new ResearchMemory.Sample(context("attack:mod:finite"),AdaptationProfile.COLD,
                Integer.MAX_VALUE,Integer.MAX_VALUE,-5,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY);
        var memory=ResearchMemory.restore(Arrays.asList(null,new ResearchMemory.Fact("damage:mod:x",AdaptationProfile.FIRE,80,"not-evidence")),Arrays.asList(null,malformed,finite));
        var merged=ResearchMemory.merge(memory,null);
        check(merged.samples().size()==1&&merged.factCount()==0,"Malformed identities and unsupported fact provenance stay excluded");
        var sanitized=sample(merged,"attack:mod:finite");
        check(sanitized.observations()==ResearchMemory.COUNT_LIMIT&&Double.isFinite(sanitized.meanRaw())&&Double.isFinite(sanitized.meanActual())&&Double.isFinite(sanitized.meanFraction()),"Merged persisted counts and outcomes stay bounded and finite");
        var left=new ResearchMemory();var right=new ResearchMemory();
        for(int i=0;i<2_000;i++){
            left.observe(new ResearchMemory.Context("attack:old:"+i,"mod:old","mod:orb","","",""),AdaptationProfile.FIRE,true,8,8,20);
            right.observe(new ResearchMemory.Context("attack:new:"+i,"mod:new","mod:orb","","",""),AdaptationProfile.COLD,true,8,8,20);
        }
        for(int i=0;i<10_000;i++){
            left.learnFact(new ResearchMemory.Fact("damage:old:"+i,AdaptationProfile.FIRE,80,"live:old"));
            right.learnFact(new ResearchMemory.Fact("damage:new:"+i,AdaptationProfile.COLD,80,"live:new"));
        }
        merged=ResearchMemory.merge(left,right);
        check(merged.samples().size()==4_000&&merged.factCount()==20_000,"The union keeps every row of both snapshots");
        check(merged.infer(new ResearchMemory.Context("attack:mod:unknown","mod:old","mod:orb","","",""))!=null,"The older group keeps supporting inference");
        check(merged.infer(new ResearchMemory.Context("attack:mod:unknown","mod:new","mod:orb","","",""))!=null,"The newer group supports inference too");
    }
    private static ResearchMemory.Sample sample(ResearchMemory memory,String key){return memory.samples().stream().filter(row->row.context().key().equals(key)).findFirst().orElseThrow();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
