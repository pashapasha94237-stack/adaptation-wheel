package ru.pasha.adaptationwheel.core;

public final class FpsAdaptationStateTest {
    public static void main(String[] args) {
        run();
        System.out.println("FpsAdaptationStateTest: all scenarios passed.");
    }

    public static void run() {
        inclusiveThresholds();
        sustainedQualification();
        exactStageDurations();
        independentOfFramerate();
        pausesPreserveEarnedProgress();
        noCreditForUnavailableGameplay();
        fractionalWindowsCarryRemainder();
        resetAndCompletedState();
        persistentRestore();
        FpsFrameSamplerTest.run();
    }

    private static void inclusiveThresholds() {
        check(FpsAdaptationState.isLowFps(1), "1 FPS is included");
        check(FpsAdaptationState.isLowFps(59), "59 FPS is included");
        for (double fps : new double[]{0, -1, 0.5, 59.001, 60, 120, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!FpsAdaptationState.isLowFps(fps), "Excluded FPS value: " + fps);
        }
        FpsAdaptationState state = new FpsAdaptationState();
        feed(state, 60, 20);
        check(!state.started(), "60 FPS never qualifies");
        feed(state, 59, 5);
        check(state.started(), "59 FPS starts after five seconds");
    }

    private static void sustainedQualification() {
        FpsAdaptationState state = new FpsAdaptationState();
        feed(state, 30, 4);
        check(!state.started(), "Four seconds do not start adaptation");
        state.sample(60, 1, true);
        close(0, state.qualifyingSeconds(), "Improvement interrupts initial qualification");
        feed(state, 30, 4);
        check(!state.started(), "Nonconsecutive low intervals do not accumulate");
        state.sample(30, 1, true);
        check(state.started(), "Exactly five consecutive seconds starts adaptation");
        close(0, state.currentElapsedSeconds(), "Qualification does not give stage progress");
        close(20, state.remainingSeconds(), "First stage starts with twenty seconds remaining");
    }

    private static void exactStageDurations() {
        FpsAdaptationState state = started(30);
        int[] durations = {20, 50, 80, 110};
        double completedSeconds = 0;
        for (int stage = 0; stage < durations.length; stage++) {
            close(durations[stage], state.currentDurationSeconds(), "Duration of stage " + (stage + 1));
            for (int second = 1; second < durations[stage]; second++) {
                check(state.sample(30, 1, true) == 0, "No completion before full duration");
                check(state.level() == stage, "No early level increase");
            }
            check(state.sample(30, 1, true) == stage + 1, "One completion event per stage");
            completedSeconds += durations[stage];
            close(completedSeconds, state.elapsedSeconds(), "Measured elapsed work");
        }
        check(state.completed() && state.level() == 4, "Four stages complete");
        close(260, state.elapsedSeconds(), "Twenty plus fifty plus eighty plus one hundred ten");
        close(1, state.totalProgress(), "Full total progress");
    }

    private static void independentOfFramerate() {
        FpsAdaptationState oneFps = started(1);
        FpsAdaptationState fiftyNineFps = started(59);
        for (int second = 1; second <= 260; second++) {
            int slowEvent = oneFps.sample(1, 1, true);
            int fasterEvent = fiftyNineFps.sample(59, 1, true);
            check(slowEvent == fasterEvent, "FPS does not multiply stage speed");
            close(oneFps.elapsedSeconds(), fiftyNineFps.elapsedSeconds(), "Equal wall time gives equal work");
        }
        check(oneFps.completed() && fiftyNineFps.completed(), "Both frame rates take 260 work seconds");
    }

    private static void pausesPreserveEarnedProgress() {
        FpsAdaptationState state = started(30);
        feed(state, 30, 27);
        check(state.level() == 1, "First stage earned");
        close(7, state.currentElapsedSeconds(), "Seven seconds of second stage earned");
        double total = state.totalProgress();
        feed(state, 120, 120);
        state.sample(0, 1, true);
        state.sample(Double.NaN, 1, true);
        state.pause();
        check(state.started() && !state.progressing(), "Improvement pauses an existing adaptation");
        close(7, state.currentElapsedSeconds(), "Paused work retained");
        close(total, state.totalProgress(), "No progress while FPS is good or invalid");
        state.sample(59, 1, true);
        close(8, state.currentElapsedSeconds(), "Started adaptation resumes immediately without requalification");
    }

    private static void noCreditForUnavailableGameplay() {
        FpsAdaptationState state = new FpsAdaptationState();
        feed(state, 1, 4);
        state.sample(1, 1, false);
        close(4, state.qualifyingSeconds(), "Unavailable gameplay pauses the check without erasing measured low FPS");
        state.sample(1, 60, true);
        check(!state.started(), "A loading gap cannot qualify adaptation");
        feed(state, 1, 5);
        state.sample(1, 1, true);
        for (double invalidSeconds : new double[]{0, -1, 2.001, 30, Double.NaN, Double.POSITIVE_INFINITY}) {
            state.sample(1, invalidSeconds, true);
        }
        for (int sample = 0; sample < 100; sample++) {
            state.sample(1, 1, false);
        }
        close(1, state.currentElapsedSeconds(), "No free progress from loading, menus or invalid windows");
    }

    private static void fractionalWindowsCarryRemainder() {
        FpsAdaptationState state = new FpsAdaptationState();
        state.sample(59, 2, true);
        state.sample(59, 2, true);
        state.sample(59, 2, true);
        check(state.started(), "Qualification can span bounded windows");
        close(1, state.currentElapsedSeconds(), "Only time after the fifth second becomes progress");
        for (int sample = 0; sample < 75; sample++) {
            state.sample(59, 0.25, true);
        }
        check(state.level() == 0, "Fractional windows do not finish early");
        check(state.sample(59, 0.5, true) == 1, "Fractional window emits completion");
        close(0.25, state.currentElapsedSeconds(), "Boundary overflow carries to next stage");
        close(20.25 / 260, state.totalProgress(), "Total progress includes completed and current work");
    }

    private static void resetAndCompletedState() {
        FpsAdaptationState state = started(30);
        feed(state, 30, 260);
        for (int sample = 0; sample < 200; sample++) {
            check(state.sample(1, 1, true) == 0, "No repeated completion events");
        }
        close(260, state.elapsedSeconds(), "Completed work remains bounded");
        close(0, state.remainingSeconds(), "Completed state has no remaining work");
        close(1, state.currentProgress(), "Completed state reports full current progress");
        state.reset();
        check(state.level() == 0 && !state.started() && !state.completed() && !state.progressing(), "Reset clears flags");
        close(0, state.elapsedSeconds(), "Reset clears work");
        close(0, state.qualifyingSeconds(), "Reset clears qualification");
        feed(state, 59, 5);
        check(state.started(), "A reset state can start again");
    }

    private static FpsAdaptationState started(double fps) {
        FpsAdaptationState state = new FpsAdaptationState();
        feed(state, fps, 5);
        return state;
    }

    private static void persistentRestore() {
        var state=new FpsAdaptationState();
        state.restore(2,7.5,true);
        check(state.level()==2 && state.started() && !state.progressing(),"Restore earned stages paused");
        state.sample(59,1,true);
        close(8.5,state.currentElapsedSeconds(),"Restored work resumes without a free time jump");
        state.restore(4,110,true);
        check(state.completed(),"Completed client adaptation survives reconnect");
        check(state.sample(1,1,true)==0,"No repeated completion after restore");
        state.restore(-1,Double.NaN,false);
        check(!state.started() && state.level()==0,"Invalid saved progress is bounded");
        state.restore(3,999,true);
        check(state.currentElapsedSeconds()<=110,"Restored work cannot exceed stage duration");
    }

    private static void feed(FpsAdaptationState state, double fps, int seconds) {
        for (int second = 0; second < seconds; second++) {
            state.sample(fps, 1, true);
        }
    }

    private static void close(double expected, double actual, String message) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= 1.0e-8,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
