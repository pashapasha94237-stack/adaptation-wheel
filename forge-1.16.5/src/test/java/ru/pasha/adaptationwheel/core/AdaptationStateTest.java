package ru.pasha.adaptationwheel.core;
import java.util.*;
public final class AdaptationStateTest {
    private static final String Z="mob:minecraft:zombie",F="damage:minecraft:fall";
    public static void main(String[] args) {
        stagedDurations(); acceleration(); independentSources(); equipmentAndToggles();
        bossFifthStage(); persistenceAndDeath(); migration(); defensiveRestore();
        timerNeverJumpsOnContact(); clientClockUsesAdaptationTime(); classifiedProfileSurvivesRestore();
        System.out.println("AdaptationStateTest: all 11 scenarios passed.");
    }
    static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static void ticks(AdaptationState s,int count) { for(int i=0;i<count;i++) s.tick(); }
    static void stagedDurations() {
        AdaptationState s=new AdaptationState(); check(s.hit(Z,false),"first hit starts"); int[] duration={400,1000,1600,2200};
        int[] reductions={20,40,70,100};
        for(int i=0;i<4;i++) {
            ticks(s,duration[i]-1); check(s.stage(Z)==i,"no early stage "+i);
            check(s.tick().equals(Compat.list(Z)),"exact duration stage "+i);
            check(Math.abs(s.damageMultiplier(Z)-(1-reductions[i]/100.0))<1e-8,"reduction "+i);
            check(s.revision()==i+1,"one turn per completion");
        }
        check(s.isImmune(Z),"four turns full immunity"); check(!s.hit(Z,false),"completed source cannot accelerate");
        ticks(s,5000); check(s.revision()==4,"no idle turns");
    }
    static void acceleration() {
        AdaptationState s=new AdaptationState(); s.hit(Z,false); ticks(s,100); s.hit(Z,false);
        AdaptationState.Snapshot p=s.snapshots().get(0); check(p.work()==100,"repeat never resets work"); check(Math.abs(p.speed()-1.3)<1e-8,"30 percent speed");
        s.hit(Z,false); check(Math.abs(s.snapshots().get(0).speed()-1.6)<1e-8,"additive acceleration");
        int count=0; while(s.stage(Z)==0) { s.tick(); count++; }
        check(count==188,"remaining 300 work at 1.6 speed"); check(s.snapshots().get(0).hits()==0,"speed resets after stage");
        check(s.snapshots().get(0).duration()==1000,"second stage base 50 seconds");
    }
    static void timerNeverJumpsOnContact() {
        AdaptationState s=AdaptationState.restore(true,Compat.list(new AdaptationState.Snapshot(Z,true,false,1,400,0,true)),1);
        AdaptationState.Snapshot before=s.snapshots().get(0);
        check(before.remaining()==600 && before.speed()==1,"30 adaptation seconds at normal speed");
        s.hit(Z,false);s.hit(Z,false);
        AdaptationState.Snapshot boosted=s.snapshots().get(0);
        check(boosted.work()==400 && boosted.remaining()==before.remaining(),"hits must not change the clock value");
        check(Math.abs(boosted.speed()-1.6)<1e-8,"only passage of time accelerates");
        int realTicks=boosted.estimatedTicks();check(realTicks==375,"30 seconds at x1.6 last 18.75 real seconds");
        s.tick();check(Math.abs(s.snapshots().get(0).work()-401.6)<1e-8,"one real tick advances 1.6 adaptation ticks");
        ticks(s,realTicks-2);check(s.stage(Z)==1,"no early completion at scaled time");s.tick();
        check(s.stage(Z)==2,"complete at the exact scaled time");
    }
    static void clientClockUsesAdaptationTime() {
        ru.pasha.adaptationwheel.net.WheelEntry entry=new ru.pasha.adaptationwheel.net.WheelEntry(Z,"entity.minecraft.zombie",true,false,1,400,0,true,true);
        ru.pasha.adaptationwheel.client.ClientWheelState.accept(9601,true,true,true,Compat.list(entry),1);
        ru.pasha.adaptationwheel.client.ClientWheelState.State client=ru.pasha.adaptationwheel.client.ClientWheelState.get(9601);
        check(client.remaining(entry,0)==600,"client displays 30 unaccelerated adaptation seconds");
        ru.pasha.adaptationwheel.net.WheelEntry faster=new ru.pasha.adaptationwheel.net.WheelEntry(Z,"entity.minecraft.zombie",true,false,1,400,4,true,true);
        ru.pasha.adaptationwheel.client.ClientWheelState.accept(9601,true,true,true,Compat.list(faster),1);
        client=ru.pasha.adaptationwheel.client.ClientWheelState.get(9601);
        check(client.remaining(faster,0)==600,"client receiving higher speed must not divide its displayed timer");
        check(Math.abs(client.remaining(faster,.5f)-598.9f)<.001,"fractional frames interpolate the faster passage of time");
        check(!client.nightVisionUnlocked(),"night vision is unavailable before full darkness adaptation");
        ru.pasha.adaptationwheel.net.WheelEntry dark=new ru.pasha.adaptationwheel.net.WheelEntry(AdaptationProfile.DARK_KEY,"environment.adaptationwheel.darkness",false,false,4,0,0,false,false);
        ru.pasha.adaptationwheel.client.ClientWheelState.accept(9601,false,false,false,Compat.list(dark),4);
        check(ru.pasha.adaptationwheel.client.ClientWheelState.get(9601).nightVisionUnlocked(),"earned unlock survives turning adaptation off");
    }
    static void classifiedProfileSurvivesRestore() {
        AdaptationState s=new AdaptationState();s.hit("damage:example:unknown_ray",false,AdaptationProfile.MAGIC);
        check(s.snapshots().get(0).duration()==800,"analysis controls timing of otherwise unknown keys");
        AdaptationState copy=AdaptationState.restore(true,s.snapshots(),s.revision());
        check(copy.snapshots().get(0).profile()==AdaptationProfile.MAGIC,"classification is stored rather than guessed again");
    }
    static void independentSources() {
        AdaptationState s=new AdaptationState(); s.hit(Z,false); ticks(s,100); s.hit(F,false); ticks(s,300);
        check(s.stage(Z)==1 && s.stage(F)==0,"independent timers"); s.hit(F,false);
        check(s.snapshots().stream().filter(p -> p.key().equals(Z)).findFirst().orElseThrow(AssertionError::new).hits()==0,"no cross source acceleration");
        ticks(s,91); check(s.stage(F)==1,"fall accelerated separately");
    }
    static void equipmentAndToggles() {
        AdaptationState s=new AdaptationState(); s.hit(Z,false); ticks(s,100); for(int i=0;i<500;i++) s.tick(false);
        check(s.snapshots().get(0).work()==100,"unequipped pause"); s.setCategoryEnabled(Z,false); ticks(s,500);
        check(s.snapshots().get(0).work()==100,"per category pause"); check(!s.hit(Z,false),"disabled hit ignored");
        s.setCategoryEnabled(Z,true); ticks(s,300); check(s.stage(Z)==1,"resume exact saved progress");
        s.setEnabled(false); ticks(s,1000); check(s.damageMultiplier(Z)==1,"global off removes resistance");
        s.setEnabled(true); s.setCategoryEnabled(Z,false); check(s.damageMultiplier(Z)==1,"category off removes resistance");
        s.setCategoryEnabled(Z,true); check(Math.abs(s.damageMultiplier(Z)-.8)<1e-8,"resistance restored");
    }
    static void bossFifthStage() {
        AdaptationState s=new AdaptationState(); s.hit(Z,true); ticks(s,8000);
        check(s.isImmune(Z) && !s.existenceAdapted(Z),"100 percent not yet existence"); check(s.snapshots().get(0).duration()==4000,"complex boss fifth 200 seconds");
        s.hit(Z,true); check(s.snapshots().get(0).hits()==1,"blocked boss hits accelerate fifth");
        while(s.stage(Z)<5) s.tick(); check(s.existenceAdapted(Z),"fifth enables one-hit"); check(s.revision()==5,"five discrete turns");
        s.setCategoryEnabled(Z,false); check(!s.existenceAdapted(Z),"disabled category cannot one-hit");
    }
    static void persistenceAndDeath() {
        AdaptationState s=new AdaptationState(); s.hit(Z,true); ticks(s,800); s.hit(Z,true); ticks(s,123); s.setCategoryEnabled(Z,false);
        AdaptationState copy=AdaptationState.restore(s.enabled(),s.snapshots(),s.revision());
        check(copy.snapshots().equals(s.snapshots()),"full persistence including speed and toggles");
        copy.clearPending(); check(copy.stage(Z)==1 && !copy.snapshots().get(0).running(),"death keeps earned degree and clears current timer");
        check(copy.snapshots().get(0).work()==0,"death resets current work"); long turns=copy.revision(); copy.reset();
        check(copy.revision()==turns && copy.snapshots().isEmpty(),"reset cannot turn wheel");
    }
    static void migration() {
        AdaptationState s=AdaptationState.restore(true,Compat.set(Z),Compat.map(F,100),3);
        check(s.isImmune(Z),"legacy full immunity retained"); check(s.stage(F)==0 && s.snapshots().stream().anyMatch(p -> p.key().equals(F)&&p.work()==100),"legacy timer migrated");
        s.hit(Z,true); check(s.snapshots().stream().anyMatch(p -> p.key().equals(Z)&&p.running()),"legacy boss unlocks new fifth stage after exposure");
    }
    static void defensiveRestore() {
        AdaptationState s=AdaptationState.restore(true,Compat.list(new AdaptationState.Snapshot("",true,false,99,Double.NaN,-2,true),
            new AdaptationState.Snapshot(F,true,false,2,Double.POSITIVE_INFINITY,999,true)), -4);
        check(s.snapshots().size()==1,"reject invalid key"); check(s.snapshots().get(0).work()==0,"reject nonfinite work");
        check(s.revision()==0 && Double.isFinite(s.snapshots().get(0).speed()),"safe persisted counters");
        boolean protectedList=false; try { s.snapshots().clear(); } catch(UnsupportedOperationException ex) { protectedList=true; }
        check(protectedList,"immutable snapshots");
    }
}
