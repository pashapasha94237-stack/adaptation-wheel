package ru.pasha.adaptationwheel.core;

import java.util.*;
import static ru.pasha.adaptationwheel.core.CombatKnowledge.*;

public final class CombatKnowledgeTest {
    private static Fact fact(String key,String actor,String direct,String phase,Kind kind,long tick){return new Fact(key,actor,"mod:boss",direct,"mod:pulse","mod:slow","far",phase,kind,tick);}
    public static void run(){
        var k=new CombatKnowledge();String key="attack:mod:pulse";
        k.describe(fact(key,"a","mod:beam","health-band:4",Kind.IMPACT,0));
        k.describe(fact(key,"a","mod:missile","health-band:4",Kind.IMPACT,20));
        check(k.summary(key).variants()==2,"Different projectiles collapsed into one observation");
        for(long tick=40;tick<=140;tick+=20)k.describe(fact(key,"a","mod:beam","health-band:4",Kind.IMPACT,tick));
        check(k.summary(key).intervalTicks()==20,"Consistent boss interval not learned");
        k.protection(key,false,0);k.protection(key,true,3.25);check(k.summary(key).verified()==1&&k.summary(key).leaks()==1&&k.summary(key).maxLeak()==3.25,"Measured leak not distinguished from successful protection");
        k.revised(key,AdaptationProfile.FIRE,AdaptationProfile.ELECTRIC);check(k.summary(key).previousProfile().equals("FIRE")&&k.summary(key).revisedProfile().equals("ELECTRIC"),"Category correction lost explanation");
        for(int cycle=0;cycle<3;cycle++){
            long t=cycle*400;String actor="boss"+cycle;
            check(k.event(fact("mob:mod:boss",actor,"","native:phase1",Kind.WINDUP,t)).isEmpty(),"Prediction before evidence");
            k.event(fact("attack:mod:shot",actor,"mod:shot","native:phase1",Kind.PROJECTILE,t+20));
            k.event(fact(key,actor,"mod:shot","native:phase1",Kind.EXPLOSION,t+40));
            k.event(fact("effect:mod:slow",actor,"","native:phase1",Kind.DEBUFF,t+41));
        }
        k.event(fact("mob:mod:boss","repeat","","native:phase1",Kind.WINDUP,2000));
        String prediction=k.event(fact("attack:mod:shot","repeat","mod:shot","native:phase1",Kind.PROJECTILE,2020));
        check(prediction.equals("EXPLOSION:"+key),"Observed windup/shot did not predict trained explosion");
        k.event(fact("mob:mod:boss","other-phase","","native:phase2",Kind.WINDUP,2000));
        check(k.event(fact("attack:mod:shot","other-phase","mod:shot","native:phase2",Kind.PROJECTILE,2020)).isEmpty(),"Boss phases incorrectly share prediction");
        check(k.event(fact("attack:mod:shot","different-actor","mod:shot","native:phase1",Kind.PROJECTILE,2020)).isEmpty(),"Different actors shared live sequence");
        var restored=CombatKnowledge.restore(k.save(),k.transitions());check(restored.summary(key).leaks()==1&&restored.summary(key).previousProfile().equals("FIRE"),"Persistence lost actual protection diagnostics");
        restored.event(fact("mob:mod:boss","fresh","","native:phase1",Kind.WINDUP,4000));check(restored.event(fact("attack:mod:shot","fresh","mod:shot","native:phase1",Kind.PROJECTILE,4020)).equals(prediction),"Persistent sequence knowledge lost");
        for(int i=0;i<1000;i++)k.describe(fact("attack:mod:"+i,"x","mod:a","",Kind.IMPACT,i));
        check(k.save().size()>=1000&&k.summary("attack:mod:0").variants()==1,"Every observed attack stays: nothing is evicted to make room");
        for(int i=0;i<40;i++)k.describe(new Fact("attack:mod:many","x","mod:boss","mod:orb"+i,"mod:pulse","","far","",Kind.IMPACT,5000+i));
        check(k.summary("attack:mod:many").variants()==40,"Variants of one attack are counted past eight");
        for(String invalid:List.of("{\"profile\":\"MAGIC\",\"firstSeconds\":0,\"stepSeconds\":30,\"confidence\":80}","{\"profile\":\"MAGIC\",\"firstSeconds\":20.5,\"stepSeconds\":30,\"confidence\":80}","{\"profile\":\"GODMODE\",\"firstSeconds\":20,\"stepSeconds\":30,\"confidence\":80}","{\"profile\":\"MAGIC\",\"firstSeconds\":20,\"stepSeconds\":30,\"confidence\":80,\"command\":\"kill\"}")){
            boolean rejected=false;try{NeuralAdvice.parse(invalid);}catch(RuntimeException expected){rejected=true;}check(rejected,"Unsafe neural advice accepted");
        }
        var advice=NeuralAdvice.parse("{\"profile\":\"MAGIC\",\"firstSeconds\":25,\"stepSeconds\":35,\"confidence\":80}");check(advice.timings().fourth()==130*20,"Neural timings conversion incorrect");
        timingContexts();sequenceContradictions();liveHints();legacyPersistence();
        System.out.println("CombatKnowledgeTest: attack/actor/phase timing, cadence changes, contradiction/relearning, live hint expiry, ordered/legacy persistence, identities, measured leaks, revisions, bounds and strict neural data passed.");
    }
    private static void timingContexts(){
        var k=new CombatKnowledge();String key="attack:mod:pulse";
        String[] actors={"a","b","a","b","a","a","b","a","b"};long[] ticks={0,5,20,35,40,60,65,80,95};
        for(int i=0;i<ticks.length;i++){
            k.describe(fact(key,actors[i],"mod:beam","native:one",Kind.IMPACT,ticks[i]));
            if(ticks[i]==60||ticks[i]==80)check(k.summary(key).intervalTicks()==20,"Interleaved actors contaminated the 20-tick cadence");
        }
        check(k.summary(key).intervalTicks()==30,"Interleaved actors contaminated the 30-tick cadence");
        k.describe(fact(key,"a","mod:beam","native:two",Kind.IMPACT,100));check(k.summary(key).intervalTicks()==0,"A different phase inherited the old interval");
        k.describe(fact(key,"a","mod:missile","native:one",Kind.IMPACT,110));check(k.summary(key).intervalTicks()==0,"Different projectile inherited the old interval");
        k.describe(fact(key,"a","mod:beam","native:one",Kind.PROJECTILE,120));check(k.summary(key).intervalTicks()==0,"Projectile spawn and impact share timing evidence");
        var cadence=new CombatKnowledge();for(long tick=0;tick<=60;tick+=20){cadence.describe(fact(key,"a","mod:beam","",Kind.IMPACT,tick));cadence.describe(fact(key,"a","mod:beam","",Kind.IMPACT,tick+1));}
        check(cadence.summary(key).intervalTicks()==20,"Repeated callbacks moved the interval origin");
        cadence.describe(fact(key,"a","mod:beam","",Kind.IMPACT,140));check(cadence.summary(key).intervalTicks()==0,"Changed attack cadence kept a stale confident interval");
        for(long tick=220;tick<=620;tick+=80)cadence.describe(fact(key,"a","mod:beam","",Kind.IMPACT,tick));
        check(cadence.summary(key).intervalTicks()==80,"Changed cadence could not replace old timing evidence");
        cadence.describe(fact(key,"a","mod:beam","",Kind.IMPACT,10));check(cadence.summary(key).intervalTicks()==0,"Backwards world time retained old cadence");
    }
    private static String begin(CombatKnowledge k,String actor,String phase,long tick){
        k.event(fact("mob:mod:boss",actor,"",phase,Kind.WINDUP,tick));
        return k.event(fact("attack:mod:shot",actor,"mod:shot",phase,Kind.PROJECTILE,tick+20));
    }
    private static void complete(CombatKnowledge k,String actor,String phase,String target,long tick){k.event(fact(target,actor,"mod:shot",phase,Kind.EXPLOSION,tick+40));}
    private static void train(CombatKnowledge k,String actor,String phase,String target,long tick){begin(k,actor,phase,tick);complete(k,actor,phase,target,tick);}
    private static void sequenceContradictions(){
        var k=new CombatKnowledge();String old="attack:mod:pulse",changed="attack:mod:charged_pulse",phase="native:one";
        for(int i=0;i<100;i++)train(k,"old"+i,phase,old,i*100L);
        check(begin(k,"query",phase,11000).equals("EXPLOSION:"+old),"Stable repeated continuation not recognized");
        complete(k,"query",phase,changed,11000);
        check(k.summary("attack:mod:shot").predicted().isEmpty(),"Consumed prediction remained visible after the next event");
        check(begin(k,"after-contradiction",phase,11100).isEmpty(),"Lifetime majority overruled a contradictory continuation");
        complete(k,"after-contradiction",phase,changed,11100);
        for(int i=2;i<7;i++)train(k,"new"+i,phase,changed,11200+i*100L);
        check(begin(k,"relearned",phase,13000).equals("EXPLOSION:"+changed),"Recent repeated behavior did not replace the old lifetime majority");
        var restored=CombatKnowledge.restore(k.save(),k.transitions());
        check(restored.summary("attack:mod:shot").predicted().isEmpty(),"Reload resurrected an active warning without a live sequence");
        check(begin(restored,"persisted",phase,14000).equals("EXPLOSION:"+changed),"Ordered recent observations changed after persistence");
        complete(restored,"persisted",phase,old,14000);
        var contradicted=CombatKnowledge.restore(restored.save(),restored.transitions());
        check(begin(contradicted,"persisted-contradiction",phase,15000).isEmpty(),"Persistence discarded the most recent contradiction");
    }
    private static void liveHints(){
        var k=new CombatKnowledge();String phase="native:one",target="attack:mod:pulse";
        for(int i=0;i<3;i++)train(k,"training"+i,phase,target,i*100L);
        check(!begin(k,"boss",phase,1000).isEmpty(),"Missing trained live prediction");
        k.event(fact("mob:mod:boss","boss","","native:two",Kind.WINDUP,1021));
        check(k.summary("attack:mod:shot").predicted().isEmpty()&&k.summary("attack:mod:shot").flow().isEmpty(),"Phase change left an earlier ability's live hints visible");
        check(begin(k,"actor-a",phase,2000).equals("EXPLOSION:"+target),"Actor A failed to recognize a known sequence");
        check(begin(k,"actor-b",phase,2010).equals("EXPLOSION:"+target),"Actor B failed to recognize a known sequence");
        complete(k,"actor-a",phase,target,2000);
        check(k.summary("attack:mod:shot").predicted().equals("EXPLOSION:"+target),"Actor A consumed actor B's active warning");
        k.event(fact("mob:mod:boss","actor-b","",phase,Kind.WINDUP,3000));
        check(k.summary("attack:mod:shot").predicted().isEmpty(),"A long combat gap retained an active warning");
        check(!begin(k,"silent-boss",phase,4000).isEmpty(),"Missing live prediction before expiry");
        k.expire(4221);
        check(k.summary("attack:mod:shot").predicted().isEmpty()&&k.summary("attack:mod:shot").flow().isEmpty(),"No-event timeout left a live warning visible");
        check(k.event(fact("attack:mod:shot","silent-boss","mod:shot",phase,Kind.PROJECTILE,4230)).isEmpty(),"Expired actor retained its live sequence");
        String timed="attack:mod:timed";
        for(long tick=5000;tick<=5060;tick+=20){Fact f=fact(timed,"timed-boss","mod:beam",phase,Kind.IMPACT,tick);k.describe(f);k.event(f);}
        check(k.summary(timed).intervalTicks()==20,"Missing cadence before expiry");
        k.expire(5261);check(k.summary(timed).intervalTicks()==0&&k.summary(timed).flow().isEmpty(),"Expired cadence remained visible");
        Fact first=fact(timed,"timed-boss","mod:beam",phase,Kind.IMPACT,5280);k.describe(first);k.event(first);
        check(k.summary(timed).intervalTicks()==0,"First post-expiry observation reused old interval samples");
        for(long tick=5300;tick<=5340;tick+=20){Fact f=fact(timed,"timed-boss","mod:beam",phase,Kind.IMPACT,tick);k.describe(f);k.event(f);}
        check(k.summary(timed).intervalTicks()==20&&!k.summary(timed).flow().isEmpty(),"Expired actor could not relearn live timing and flow");
    }
    private static void legacyPersistence(){
        var trained=new CombatKnowledge();String target="attack:mod:pulse",phase="native:one";
        for(int i=0;i<3;i++)train(trained,"old"+i,phase,target,i*100L);
        String prefix=trained.transitions().stream().filter(t->t.next().equals("EXPLOSION:"+target)).findFirst().orElseThrow().prefix();
        var deterministic=CombatKnowledge.restore(List.of(),List.of(new Transition(prefix,"EXPLOSION:"+target,1_000_000)));
        check(begin(deterministic,"new",phase,1000).equals("EXPLOSION:"+target),"Old deterministic aggregate saves no longer load");
        var ambiguous=CombatKnowledge.restore(List.of(),List.of(new Transition(prefix,"EXPLOSION:"+target,1_000_000),new Transition(prefix,"EXPLOSION:attack:mod:other",1)));
        check(begin(ambiguous,"new",phase,1000).isEmpty(),"Legacy unordered aggregate counts invented a certain recent continuation");
        for(int i=0;i<8;i++)train(ambiguous,"fresh"+i,phase,target,2000+i*100L);
        check(begin(ambiguous,"learned",phase,3000).equals("EXPLOSION:"+target),"Legacy uncertainty prevented fresh evidence from relearning");
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
