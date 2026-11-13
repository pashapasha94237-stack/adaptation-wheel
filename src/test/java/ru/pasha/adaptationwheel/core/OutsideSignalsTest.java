package ru.pasha.adaptationwheel.core;

import java.util.*;

public final class OutsideSignalsTest {
    public static void run(){
        var before=Map.of("minecraft","1.20.1","jei","15.1","quark","4.0");
        var after=Map.of("minecraft","1.20.1","jei","15.2","alexsmobs","1.22");
        var diff=OutsideSignals.diffMods(before,after);
        check(diff.added().equals(List.of("alexsmobs"))&&diff.removed().equals(List.of("quark"))&&diff.updated().equals(List.of("jei 15.1 → 15.2")),"Mod set difference wrong: "+diff);
        check(OutsideSignals.diffMods(before,before).empty(),"Unchanged pack reported changes");
        check(OutsideSignals.decodeMods(OutsideSignals.encodeMods(after)).equals(new TreeMap<>(after)),"Mod list round trip failed");
        check(OutsideSignals.decodeMods("").isEmpty()&&OutsideSignals.decodeMods("broken;;x@1").equals(Map.of("x","1")),"Malformed stored list not tolerated");

        var lines=OutsideSignals.diffConfig(Map.of("a.b","1","c","true"),Map.of("a.b","2","c","true","new","x"));
        check(lines.equals(List.of("a.b: 1 → 2")),"Config diff wrong: "+lines);

        var clock=new OutsideSignals.ClockWatch();
        check(clock.sample(1_000_000,0)==0,"First clock sample reported a jump");
        check(clock.sample(1_001_000,1_000_000_000L)==0,"Normal second reported as a jump");
        long jump=clock.sample(1_001_000+3_600_000+1000,2_000_000_000L);
        check(jump==3_600_000,"Forward clock change not measured: "+jump);
        check(clock.sample(1_001_000+3_600_000+2000-7_200_000,3_000_000_000L)==-7_200_000,"Backward clock change not measured");
        check(clock.sample(1_001_000+3_600_000+2000-7_200_000+20_000,23_000_000_000L)==0,"A slow tick is not a clock change");

        var limiter=new OutsideSignals.RepeatLimiter(3,500,4);
        for(int i=0;i<3;i++)check(limiter.allow("tooltip"),"First repeats suppressed");
        int allowed=0;for(int i=4;i<=1000;i++)if(limiter.allow("tooltip"))allowed++;
        check(allowed==2&&limiter.suppressed()==995,"Repeat limiter rate wrong: "+allowed+"/"+limiter.suppressed());
        check(limiter.allow("other"),"A different message was suppressed");
        for(int i=0;i<10;i++)limiter.allow("k"+i);
        check(limiter.count("tooltip")<=1000,"Limiter map unbounded");

        var rate=new OutsideSignals.RateWindow(60_000_000_000L,100);
        for(int i=0;i<30;i++)rate.add(i*1_000_000_000L);
        check(rate.count(30_000_000_000L)==30&&rate.count(95_000_000_000L)==0,"Error rate window wrong");
        for(int i=0;i<500;i++)rate.add(100_000_000_000L);check(rate.count(100_000_000_000L)==100,"Rate window unbounded");

        var idle=new OutsideSignals.IdleClock();
        check(idle.update(0,0,0,0,0,0)==0,"First idle sample not zero");
        check(idle.update(0,0,0,0,0,200)==200,"Standing still did not count");
        check(idle.update(0,0,0,10,0,220)==0,"Looking around did not reset idleness");
        check(idle.update(0.01,0,0,10,0,400)==180,"Sub-pixel drift reset idleness");
        check(OutsideSignals.duration(3_725_000).equals("+1h 2m")&&OutsideSignals.duration(-45_000).equals("-45s"),"Duration text wrong");
        System.out.println("OutsideSignalsTest: mod/config diffs, clock jumps, log repeat limits, error rate and idleness passed.");
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
