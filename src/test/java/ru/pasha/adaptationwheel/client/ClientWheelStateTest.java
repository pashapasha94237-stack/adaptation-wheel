package ru.pasha.adaptationwheel.client;

import java.util.List;
import ru.pasha.adaptationwheel.net.WheelEntry;
import net.minecraft.util.Mth;

public final class ClientWheelStateTest {
    private static final int ID=9701;
    public static void run() {
        clockRateChangesWithoutJump();
        exactRatesBetweenUpdates();
        delayedSnapshotsKeepTheSameRate();
        slowServerCannotAccumulatePredictionDrift();
        ClientWheelState.clear();
        accept(true,true,entry(3,true,true,true),3);
        float idle=state().turn(0);ticks(100);
        close(state().turn(0),idle,"Ordinary stages never spin continuously");
        accept(true,true,entry(4,true,true,true),4);ticks(40);
        float a=state().turn(0);ticks(20);float b=state().turn(0);
        close(b-a,Mth.PI/2,"Fifth stage continues rotating after its completion step animation");
        accept(true,true,entry(4,true,true,true),4);
        close(state().turn(0),b,"State packet must not reset rotation");
        ticks(20);float before=state().turn(0);
        accept(true,true,entry(4,false,true,true),4);ticks(100);
        close(state().turn(0),before,"Disabled adaptation freezes continuous rotation");
        accept(true,true,entry(4,true,true,true),4);ticks(20);
        close(state().turn(0)-before,Mth.PI/2,"Resuming starts at the saved angle");
        accept(false,true,entry(4,true,true,true),4);float paused=state().turn(0);ticks(20);
        close(state().turn(0),paused,"Global switch freezes continuous rotation");
        accept(true,false,entry(4,true,true,true),4);ticks(20);
        close(state().turn(0),paused,"Unequipped wheel cannot spin");
        accept(true,true,entry(4,true,true,true),4);ticks(10);float finishStart=state().turn(0);
        accept(true,true,entry(5,true,false,false),5);ticks(18);
        close(state().turn(0)-finishStart,Mth.PI/4,"Completion adds one final discrete turn");
        float finished=state().turn(0);ticks(100);
        close(state().turn(0),finished,"Completed existence adaptation stops rotating");
        ClientWheelState.clear();
        System.out.println("ClientWheelStateTest: continuous existence rotation, sync, pause and completion passed.");
    }
    private static void exactRatesBetweenUpdates() {
        for(int hits:new int[]{1,4,6}) {
            ClientWheelState.clear();
            var e=new WheelEntry("damage:minecraft:fall","",true,false,1,0,hits,true,true);
            accept(true,true,e,1);
            for(int tick=1;tick<=40;tick++) {
                ticks(1);
                linearClose(state().remaining(e,0),1000-tick*e.speed(),
                    "Clock must keep x"+e.speed()+" past the old ten-tick prediction limit");
            }
        }
        ClientWheelState.clear();
    }
    private static void delayedSnapshotsKeepTheSameRate() {
        for(int hits:new int[]{1,4,6}) {
            ClientWheelState.clear();
            var e=new WheelEntry("damage:minecraft:fall","",true,false,1,0,hits,true,true);
            ClientWheelState.accept(ID,true,true,true,List.of(e),1,100);
            ticks(15);float predicted=state().remaining(e,0);
            var delayed=new WheelEntry(e.key(),"",true,false,1,10*e.speed(),hits,true,true);
            ClientWheelState.accept(ID,true,true,true,List.of(delayed),1,110);
            linearClose(state().remaining(delayed,0),predicted,"Five-tick network delay must not rewind x"+e.speed());
            ticks(7);
            var next=new WheelEntry(e.key(),"",true,false,1,20*e.speed(),hits,true,true);
            ClientWheelState.accept(ID,true,true,true,List.of(next),1,120);
            linearClose(state().remaining(next,0),1000-22*e.speed(),"Jitter must not change the rate");
            ClientWheelState.accept(ID,true,true,true,List.of(delayed),1,110);
            linearClose(state().remaining(next,0),1000-22*e.speed(),"Stale samples must not reset the clock");
            var boosted=new WheelEntry(e.key(),"",true,false,1,22*e.speed(),hits+1,true,true);
            float before=state().remaining(next,0);
            ClientWheelState.accept(ID,true,true,true,List.of(boosted),1,122);
            linearClose(state().remaining(boosted,0),before,"A timely hit changes rate without jumping the clock");
            ticks(5);
            linearClose(state().remaining(boosted,.5f),before-5.5*boosted.speed(),"New rate applies once to fractional frames");
            var paused=new WheelEntry(e.key(),"",true,false,1,22*e.speed()+5*boosted.speed(),hits+1,true,false);
            ClientWheelState.accept(ID,true,true,true,List.of(paused),1,127);float frozen=state().remaining(paused,0);
            ticks(40);linearClose(state().remaining(paused,.5f),frozen,"Paused entries remain frozen without incoming packets");
        }
        ClientWheelState.clear();
    }
    private static void slowServerCannotAccumulatePredictionDrift() {
        ClientWheelState.clear();
        var e=new WheelEntry("damage:minecraft:fall","",true,false,1,0,6,true,true);
        ClientWheelState.accept(ID,true,true,true,List.of(e),1,100);
        for(int sample=1;sample<=20;sample++) {
            ticks(20);double work=sample*5*e.speed();
            var update=new WheelEntry(e.key(),"",true,false,1,work,6,true,true);
            ClientWheelState.accept(ID,true,true,true,List.of(update),1,100+sample*5);
            double projectedWork=1000-state().remaining(update,0);
            if(projectedWork-work>5*e.speed()+1e-4)throw new AssertionError("Server lag must not accumulate unbounded prediction error");
        }
        ClientWheelState.clear();
    }
    private static void clockRateChangesWithoutJump() {
        ClientWheelState.clear();
        var normal=new WheelEntry("damage:minecraft:fall","",true,false,1,400,0,true,true);
        accept(true,true,normal,1);
        linearClose(state().remaining(normal,0),600,"Clock starts with 30 adaptation seconds");
        ticks(10);float before=state().remaining(normal,0);
        var faster=new WheelEntry(normal.key(),"",true,false,1,410,4,true,true);
        accept(true,true,faster,1);
        linearClose(state().remaining(faster,0),before,"A bonus packet changes speed without dividing or subtracting from the clock");
        ticks(5);
        linearClose(before-state().remaining(faster,0),11,"Five real ticks advance eleven adaptation ticks at x2.20");
        var sync=new WheelEntry(normal.key(),"",true,false,1,421,4,true,true);
        float atSync=state().remaining(faster,0);accept(true,true,sync,1);
        linearClose(state().remaining(sync,0),atSync,"Periodic server updates preserve the fast clock");
        ClientWheelState.clear();
    }
    private static WheelEntry entry(int stage,boolean enabled,boolean running,boolean progressing) {
        return new WheelEntry("mob:minecraft:warden","entity.minecraft.warden",enabled,true,stage,0,0,running,progressing);
    }
    private static void accept(boolean enabled,boolean equipped,WheelEntry e,long rev) {ClientWheelState.accept(ID,enabled,equipped,true,List.of(e),rev);}
    private static ClientWheelState.State state(){return ClientWheelState.get(ID);}
    private static void ticks(int count){for(int i=0;i<count;i++)ClientWheelState.tick();}
    private static void close(float actual,float expected,String message) {
        float delta=(actual-expected)%Mth.TWO_PI;
        if(Math.abs(delta)>1e-4 && Math.abs(Math.abs(delta)-Mth.TWO_PI)>1e-4)throw new AssertionError(message+": "+actual+" expected "+expected);
    }
    private static void linearClose(double actual,double expected,String message) {
        if(Math.abs(actual-expected)>1e-4)throw new AssertionError(message+": "+actual+" expected "+expected);
    }
}
