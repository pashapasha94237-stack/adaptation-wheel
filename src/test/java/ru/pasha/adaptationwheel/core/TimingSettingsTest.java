package ru.pasha.adaptationwheel.core;
import java.util.*;
import ru.pasha.adaptationwheel.net.*;

public final class TimingSettingsTest {
    public static void run() {
        String z="mob:minecraft:zombie";
        try {
            AdaptationTimings.replace(Map.of(AdaptationProfile.PHYSICAL,StageDurations.seconds(40,50,80,110,140)),Map.of(z,List.of(5,0,0,0,0)));
            AdaptationState state=new AdaptationState();state.hit(z,false);
            check(state.snapshots().get(0).duration()==100,"Five-second override used by progression");
            for(int i=0;i<99;i++)state.tick();check(state.stage(z)==0,"No early completion");state.tick();check(state.stage(z)==1,"Exactly five seconds complete a stage");
            check(state.snapshots().get(0).duration()==1000,"Zero inherits the next category stage");
            check(AdaptationTimings.duration("mob:minecraft:skeleton",AdaptationProfile.PHYSICAL,0)==800,"Other sources inherit changed category");
            var wireTimes=StageDurations.seconds(3,4,5,6,7);
            var client=new WheelEntry(z,"",true,false,0,0,0,true,true,AdaptationProfile.PHYSICAL,wireTimes);
            AdaptationTimings.defaults();check(client.duration()==60,"Remote duration is independent of local config");
            state=new AdaptationState();state.hit(z,false);for(int i=0;i<50;i++)state.tick();
            AdaptationTimings.replace(Map.of(),Map.of(z,List.of(5,6,7,8,9)));
            check(state.snapshots().get(0).work()==50,"Config reload does not erase elapsed progress");
            for(int i=0;i<50;i++)state.tick();check(state.stage(z)==1,"Running adaptation immediately uses changed duration");
            var saved=AdaptationState.restore(true,state.snapshots(),state.revision());check(saved.stage(z)==1,"Earned degrees survive reload");
            boolean rejected=false;try{StageDurations.seconds(0,1,1,1,1);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Zero cannot create an instant category stage");
        }finally{AdaptationTimings.defaults();}
        try {
            AdaptationTimings.replace(Map.of(),Map.of(),50);
            var state=new AdaptationState();state.hit(z,false);state.hit(z,false);
            check(state.snapshots().get(0).speed()==1.5,"Configured 50 percent gives x1.50");state.hit(z,false);
            check(state.snapshots().get(0).speed()==2,"Bonuses add rather than multiply");state.tick();
            check(state.snapshots().get(0).work()==2,"Configured bonus drives actual progress");
            var remote=new WheelEntry(z,"",true,false,0,0,2,true,true,AdaptationProfile.PHYSICAL,StageDurations.defaults(AdaptationProfile.PHYSICAL),50);
            AdaptationTimings.defaults();check(remote.speed()==2,"Remote speed uses server percentage even if the client's setting is 30");
            boolean rejected=false;try{AdaptationTimings.replace(Map.of(),Map.of(),-1);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Negative acceleration is rejected");
        }finally{AdaptationTimings.defaults();}
        try {
            var protections=new StageProtections(30,55,75,80);
            AdaptationTimings.replace(Map.of(),Map.of(),30,protections);
            for(int stage=0;stage<=5;stage++) {
                var s=AdaptationState.restore(true,List.of(new AdaptationState.Snapshot(z,true,true,stage,0,0,stage<5)),stage);
                check(Math.abs(s.damageMultiplier(z)-(1-protections.percent(stage)/100.0))<1e-8,"Actual configured reduction at degree "+stage);
            }
            var remote=new WheelEntry(z,"",true,false,1,0,0,true,true,AdaptationProfile.PHYSICAL,StageDurations.defaults(AdaptationProfile.PHYSICAL),30,protections);
            AdaptationTimings.defaults();check(remote.reduction()==30,"Remote protection is independent of the local config");
            boolean rejected=false;try{new StageProtections(101,40,70,100);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Protection over 100 percent is rejected");
        }finally{AdaptationTimings.defaults();}
        try {
            check(AdaptationTimings.healAfterStage(),"Completion healing defaults to enabled");
            AdaptationTimings.replace(Map.of(),Map.of(),30,StageProtections.DEFAULTS,false);
            check(!AdaptationTimings.healAfterStage(),"Immutable policy retains the disabled healing setting");
            AdaptationTimings.defaults();check(AdaptationTimings.healAfterStage(),"Unloading config restores default healing");
        }finally{AdaptationTimings.defaults();}
        try {
            var protections=new StageProtections(20,40,70,80);
            var state=AdaptationState.restore(true,List.of(new AdaptationState.Snapshot(z,true,true,4,100,2,true)),4);
            AdaptationTimings.replace(Map.of(),Map.of(),30,protections,true,false,true);
            for(int i=0;i<20;i++)state.tick();state.hit(z,true);
            check(state.snapshots().get(0).work()==100 && state.snapshots().get(0).hits()==2,"Disabled existence pauses both time and acceleration");
            check(state.pending().isEmpty(),"Disabled fifth stage is not exposed as an active timer");
            state=AdaptationState.restore(true,state.snapshots(),state.revision());
            AdaptationTimings.replace(Map.of(),Map.of(),30,protections,true,true,false);state.tick();
            check(Math.abs(state.snapshots().get(0).work()-101.6)<1e-8,"Re-enabling resumes preserved time and speed");
            var complete=AdaptationState.restore(true,List.of(new AdaptationState.Snapshot(z,true,true,5,0,0,false)),5);
            check(complete.existenceAdapted(z) && complete.isImmune(z),"Disabling only execution preserves earned immunity");
            AdaptationTimings.replace(Map.of(),Map.of(),30,protections,true,false,true);
            check(!complete.existenceAdapted(z) && Math.abs(complete.damageMultiplier(z)-.2)<1e-8 && complete.stage(z)==5,"Disabled earned existence falls back to degree four without erasing it");
            var remote=new WheelEntry(z,"",true,true,5,0,0,false,false,AdaptationProfile.MAGIC,StageDurations.defaults(AdaptationProfile.MAGIC),30,protections,false,false);
            AdaptationTimings.defaults();check(remote.reduction()==80 && remote.totalStages()==4 && !remote.pending(),"Client uses server feature switches independently of local settings");
        }finally{AdaptationTimings.defaults();}
        System.out.println("TimingSettingsTest: timing, acceleration, protection, healing, boss switches and reload settings passed.");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
