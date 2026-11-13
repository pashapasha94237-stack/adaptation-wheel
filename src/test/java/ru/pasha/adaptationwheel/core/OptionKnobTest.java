package ru.pasha.adaptationwheel.core;

import java.util.LinkedHashMap;
import java.util.function.IntUnaryOperator;

public final class OptionKnobTest {
    /** Stand-in for an OptionInstance: clamps to its range and rounds down to its step like the frame limit (tens). */
    static final class Fake {
        int value,step,min,max,sets;
        Fake(int value,int step,int min,int max){this.value=value;this.step=step;this.min=min;this.max=max;}
        int get(){return value;}
        void set(int v){value=Math.max(min,Math.min(max,v/step*step));sets++;}
        OptionKnob knob(){return new OptionKnob(this::get,this::set);}
    }
    static final IntUnaryOperator CUT4=base->base<=6?base:Math.max(6,base-4),CAP30=base->Math.min(base,30),CAP45=base->Math.min(base,45),CAP60=base->Math.min(base,60);
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}

    public static void run(){
        capAndRestoreExactly();playerChangeIsRespected();frameLimitRounding();restoreSignalsSave();parkHandsBackWithoutSave();crashRecord();
        System.out.println("OptionKnobTest: cap and exact restore, player change yields, rounding, save signal, park and crash record passed.");
    }
    static void capAndRestoreExactly(){
        var o=new Fake(12,1,2,32);var k=o.knob();
        k.apply(CUT4,false);check(o.get()==8&&k.applied()==8&&k.original()==12,"cap not applied: "+o.get());
        int sets=o.sets;k.apply(CUT4,false);check(o.sets==sets&&o.get()==8,"a steady cap must not touch the option again");
        check(!k.takeRestored(),"no restore signal while capped");
        k.end();check(o.get()==12&&k.applied()==-1&&k.original()==-1,"original not given back exactly: "+o.get());
        check(k.takeRestored()&&!k.takeRestored(),"restore must signal exactly once");
        var low=new Fake(6,1,2,32);var lk=low.knob();lk.apply(CUT4,false);check(low.get()==6&&lk.applied()==-1,"never below 6");
        var near=new Fake(8,1,2,32);var nk=near.knob();nk.apply(CUT4,false);check(near.get()==6&&nk.original()==8,"cut stops at 6");nk.end();check(near.get()==8,"8 not restored");
    }
    static void playerChangeIsRespected(){
        var o=new Fake(12,1,2,32);var k=o.knob();
        k.apply(CUT4,false);o.value=10;
        k.apply(CUT4,false);check(o.get()==10&&k.applied()==-1&&k.original()==-1,"the player's change was fought or remembered");
        k.apply(CUT4,false);check(o.get()==10,"must keep yielding while the cap is wanted");
        k.end();check(o.get()==10&&!k.takeRestored(),"the player's value must stay and no save is due");
        k.apply(CUT4,false);check(o.get()==6&&k.original()==10,"caps again from the player's new value after the cap was released");
        o.value=7;k.end();check(o.get()==7&&!k.takeRestored(),"a changed value is not overwritten by the restore");
    }
    static void frameLimitRounding(){
        var o=new Fake(260,10,10,260);var k=o.knob();
        k.apply(CAP45,false);check(o.get()==40&&k.applied()==40&&k.original()==260,"45 must round to 40 and remember 40: "+o.get());
        k.apply(CAP45,false);check(o.get()==40&&k.original()==260,"rounded cap must stay stable");
        k.apply(CAP30,false);check(o.get()==30&&k.applied()==30&&k.original()==260,"stage change keeps the player's value");
        k.end();check(o.get()==260,"260 not restored");
        var below=new Fake(40,10,10,260);var b=below.knob();b.apply(CAP45,false);check(below.get()==40&&b.applied()==-1,"a value under the cap is left alone");
        check(!b.takeRestored(),"nothing was lowered, nothing to save");
        var same=new Fake(48,1,1,260);var s=same.knob();s.apply(CAP45,false);check(same.get()==45&&s.original()==48,"unrounded option takes the cap as is");
    }
    static void restoreSignalsSave(){
        var o=new Fake(50,1,1,260);var k=o.knob();
        k.apply(CAP30,false);check(o.get()==30&&!k.takeRestored(),"capping must not signal a save");
        k.apply(CAP60,false);check(o.get()==50&&k.applied()==-1&&k.takeRestored(),"want>=base must give back and signal a save");
        k.apply(CAP60,false);check(o.get()==50&&!k.takeRestored(),"idle must not signal");
        k.apply(null,false);check(!k.takeRestored(),"ending an idle knob must not signal");
        k.apply(CAP30,false);k.apply(null,false);check(o.get()==50&&k.takeRestored(),"a null goal gives back and signals (the memory-pressure hand-over)");
    }
    static void parkHandsBackWithoutSave(){
        var o=new Fake(12,1,2,32);var k=o.knob();
        k.apply(CUT4,false);k.apply(CUT4,true);check(o.get()==12&&k.applied()==-1&&!k.takeRestored(),"an options screen gets the player's value; its own save does the writing");
        k.apply(CUT4,false);check(o.get()==8&&k.original()==12,"when the screen closes unchanged, the cap resumes from the player's value");
        var p=new Fake(12,1,2,32);var q=p.knob();
        q.apply(CUT4,false);q.apply(CUT4,true);p.value=14;q.apply(CUT4,false);check(p.get()==14&&q.applied()==-1,"a value changed on the screen belongs to the player");
        q.apply(CUT4,false);check(p.get()==14,"and stays so while the cap is wanted");
    }
    static void crashRecord(){
        var o=new Fake(12,1,2,32);var k=o.knob();k.apply(CUT4,false);
        var held=new LinkedHashMap<String,KnobRecord.Entry>();held.put("renderDistance",new KnobRecord.Entry(k.original(),k.applied()));held.put("maxFps",new KnobRecord.Entry(260,30));
        String text=KnobRecord.format(held);
        var back=KnobRecord.parse(text);check(back.equals(held)&&text.contains("renderDistance=12,8"),"record round trip: "+text);
        var entry=back.get("renderDistance");
        check(KnobRecord.recover(entry,8)==12,"file holds the capped value after a crash: the original comes back");
        check(KnobRecord.recover(entry,12)==-1&&KnobRecord.recover(entry,10)==-1,"anything else was set by the player or already restored: leave it");
        check(KnobRecord.recover(back.get("maxFps"),30)==260,"frame limit recovery");
        var junk=KnobRecord.parse("garbage\nrenderDistance=x,3\nmaxFps=30,60\n=1,0\nrenderDistance=12,8\r\nrenderDistance=5\n");
        check(junk.size()==1&&junk.get("renderDistance").equals(entry),"malformed lines must be skipped: "+junk);
        check(KnobRecord.parse(null).isEmpty()&&KnobRecord.parse("").isEmpty()&&KnobRecord.format(new LinkedHashMap<>()).isEmpty(),"empty record");
        // recovery of a fresh session, simulated: the capped option is on disk, the new knob knows nothing
        var disk=new Fake(entry.applied(),1,2,32);int want=KnobRecord.recover(entry,disk.get());if(want>=0)disk.set(want);
        check(disk.get()==12,"restart recovery did not give back the player's distance");
    }
}
