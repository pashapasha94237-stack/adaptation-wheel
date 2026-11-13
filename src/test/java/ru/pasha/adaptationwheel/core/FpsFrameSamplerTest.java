package ru.pasha.adaptationwheel.core;

/** Regression tests feed timestamped render callbacks through the same sampler as the live client. */
public final class FpsFrameSamplerTest {
    public static void run() {
        realThirtyFpsStartsAndCompletes();
        openingMenusDoesNotKeepRestarting();
        healthyFpsAndLoadingInterruptQualification();
        boundariesAndDuplicateCallbacks();
        System.out.println("FpsFrameSamplerTest: real frame cadence, menus, loading, 1/30/59/60 boundaries passed.");
    }

    private static void realThirtyFpsStartsAndCompletes() {
        var harness=new Harness();
        harness.frames(30,4);
        check(!harness.state.started(),"Four seconds of real 30-FPS frame intervals remain qualification");
        close(4,harness.state.qualifyingSeconds(),"Rendered frames have counted four measured seconds");
        harness.frames(30,1);
        check(harness.state.started(),"30 actual render frames per second start after five measured seconds");
        harness.frames(30,20);
        check(harness.state.level()==1,"Twenty real low-FPS seconds complete the first stage");
        close(0,harness.state.currentElapsedSeconds(),"No free work or timestamp rounding at stage completion");
    }

    private static void openingMenusDoesNotKeepRestarting() {
        var harness=new Harness();
        harness.frames(30,2);
        harness.pause(8);
        close(2,harness.state.qualifyingSeconds(),"Checking J/inventory retains earlier low-FPS measurements");
        harness.frames(30,2);
        harness.pause(8);
        check(!harness.state.started(),"Time in menus cannot finish qualification");
        harness.frames(30,1);
        check(harness.state.started(),"Repeated brief checks cannot permanently prevent a 30-FPS start");
        harness.frames(30,7);
        harness.pause(60);
        close(7,harness.state.currentElapsedSeconds(),"Menu time never advances an active stage");
        harness.frames(30,1);
        close(8,harness.state.currentElapsedSeconds(),"Only resumed gameplay advances work");
    }

    private static void healthyFpsAndLoadingInterruptQualification() {
        var harness=new Harness();
        harness.frames(30,4);
        harness.frames(60,1);
        close(0,harness.state.qualifyingSeconds(),"A real 60-FPS window breaks the initial low-FPS run");
        harness.frames(30,4);
        harness.deliver(harness.now+=8_000_000_000L);
        close(0,harness.state.qualifyingSeconds(),"Loading gap does not masquerade as sustained low FPS");
        check(!harness.state.started(),"Neither menus nor loading produce a free qualification");
    }

    private static void boundariesAndDuplicateCallbacks() {
        for(int fps:new int[]{1,30,59,60}) {
            var harness=new Harness();
            harness.frames(fps,6);
            check(harness.state.started()==(fps<=59),"Real frame interval boundary at "+fps+" FPS");
        }
        var harness=new Harness();
        for(int frame=1;frame<=180;frame++) {
            harness.now=Math.round(frame*1_000_000_000.0/30);
            harness.deliver(harness.now);
            harness.deliver(harness.now);
        }
        check(harness.state.started(),"Duplicate timestamps neither double FPS nor erase qualification");
        close(1,harness.state.currentElapsedSeconds(),"Only actual frame intervals count as time");
    }

    private static final class Harness {
        final FpsFrameSampler sampler=new FpsFrameSampler();
        final FpsAdaptationState state=new FpsAdaptationState();
        long now;
        Harness() { sampler.frame(now); }
        void frames(int fps,int seconds) {
            long start=now;
            sampler.frame(now);
            for(int frame=1;frame<=fps*seconds;frame++) deliver(now=start+Math.round(frame*1_000_000_000.0/fps));
        }
        void pause(int seconds) { sampler.pause();state.pause();now+=seconds*1_000_000_000L; }
        void deliver(long timestamp) {
            var measurement=sampler.frame(timestamp);
            if(measurement==null) return;
            if(!measurement.valid()) { state.interruptQualification();return; }
            double remaining=measurement.seconds();
            while(remaining>0) {
                double chunk=Math.min(FpsAdaptationState.MAX_SAMPLE_SECONDS,remaining);
                state.sample(measurement.fps(),chunk,true);
                remaining-=chunk;
            }
        }
    }
    private static void close(double expected,double actual,String message) {
        check(Math.abs(expected-actual)<1e-8,message+": expected "+expected+", got "+actual);
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
