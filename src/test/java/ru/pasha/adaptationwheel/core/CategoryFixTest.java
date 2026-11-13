package ru.pasha.adaptationwheel.core;

import java.util.List;
import java.util.Map;

/** 4.44 manual category: the row's name and stage times follow the player, everything else stays the analyzer's and the wheel's. */
public final class CategoryFixTest {
    private static final String KEY = "damage:luminousworld:yeti_damage", OTHER = "damage:luminousworld:yeti_bite";

    public static void run() {
        AdaptationTimings.defaults();
        onlyNameAndTime();
        runningStageKeepsItsShare();
        analyzerKeepsLearning();
        otherRowsStayPut();
        savedAndRestored();
        refused();
        configuredTimesStillWin();
        AdaptationTimings.defaults();
        System.out.println("CategoryFixTest: name and time move, stages, protection, other rows and the analyzer stay.");
    }

    private static AdaptationAnalysis.Decision decision(AdaptationProfile profile, int confidence) {
        return new AdaptationAnalysis.Decision(profile, StageDurations.defaults(profile), confidence, "name.hint", 3, AdaptationAnalysis.Memory.EMPTY, true);
    }

    /** A running row of the analyzer's category with 100 ticks of work and one repeated hit. */
    private static AdaptationState running(AdaptationProfile profile) {
        var state = new AdaptationState();
        state.applyAnalysis(KEY, decision(profile, 40));
        state.hit(KEY, false, profile);
        for (int i = 0; i < 100; i++) state.tick();
        state.hit(KEY, false, profile);
        return state;
    }

    private static void onlyNameAndTime() {
        var state = running(AdaptationProfile.PHYSICAL);
        var before = state.snapshot(KEY);
        long revision = state.revision();
        double multiplier = state.damageMultiplier(KEY);
        check(state.setManualCategory(KEY, AdaptationProfile.COLD), "A correction is taken");
        var after = state.snapshot(KEY);
        check(after.category() == AdaptationProfile.COLD && after.manualProfile() == AdaptationProfile.COLD, "The row shows the player's category");
        check(after.profile() == AdaptationProfile.PHYSICAL && after.analysisConfidence() == 40 && after.analysisReason().equals("name.hint"), "The analyzer's verdict stays underneath");
        check(after.stage() == before.stage() && after.hits() == before.hits() && after.running() && after.enabled(), "Stage, hits and state stay");
        check(state.damageMultiplier(KEY) == multiplier && state.revision() == revision, "Protection stays and the wheel does not turn");
        check(after.duration() == AdaptationProfile.COLD.duration(0), "The running stage takes the new category's time: " + after.duration());
        for (int stage = 1; stage < 5; stage++) check(after.timings().ticks(stage) == AdaptationProfile.COLD.duration(stage), "Later stages too: " + stage);
        check(state.setManualCategory(KEY, null) && state.snapshot(KEY).category() == AdaptationProfile.PHYSICAL && state.snapshot(KEY).manualProfile() == null, "The analyzer comes back");
        check(state.snapshot(KEY).duration() == before.duration(), "With its own time");
        check(!state.setManualCategory(KEY, null), "Nothing to give back: no change");
        check(!state.setManualCategory(KEY, AdaptationProfile.PHYSICAL) && state.snapshot(KEY).manualProfile() == null, "The analyzer's own category is no correction");

        // A stage started while corrected is timed by the correction and finishes on it.
        state.setManualCategory(KEY, AdaptationProfile.COLD);
        int ticks = 0;
        while (state.stage(KEY) == 0) { state.tick(); ticks++; }
        check(state.snapshot(KEY).duration() == AdaptationProfile.COLD.duration(1), "The next stage follows the correction");
        check(ticks < AdaptationProfile.COLD.duration(0), "The running stage really ends on the shorter time: " + ticks);
    }

    private static void runningStageKeepsItsShare() {
        var state = running(AdaptationProfile.PHYSICAL);
        var before = state.snapshot(KEY);
        double share = before.work() / before.duration();
        state.setManualCategory(KEY, AdaptationProfile.FIRE);
        var fire = state.snapshot(KEY);
        check(near(fire.work() / fire.duration(), share), "The stage keeps its percentage on a shorter time");
        state.setManualCategory(KEY, null);
        state.setManualCategory(KEY, AdaptationProfile.MAGIC);
        var magic = state.snapshot(KEY);
        check(near(magic.work() / magic.duration(), share), "And on a longer one");
        for (int i = 0; i < 200; i++) {
            state.setManualCategory(KEY, i % 2 == 0 ? AdaptationProfile.FIRE : AdaptationProfile.MAGIC);
            check(state.stage(KEY) == 0, "Switching never finishes a stage");
        }
        var kept = state.snapshot(KEY);
        check(near(kept.work() / kept.duration(), share), "Going back and forth neither gains nor loses work");

        // Almost done on a long time: the short time must not end it at once.
        var late = new AdaptationState();
        late.applyAnalysis(KEY, decision(AdaptationProfile.MAGIC, 40));
        late.hit(KEY, false, AdaptationProfile.MAGIC);
        for (int i = 0; i < AdaptationProfile.MAGIC.duration(0) - 2; i++) late.tick();
        late.setManualCategory(KEY, AdaptationProfile.FIRE);
        check(late.stage(KEY) == 0 && late.snapshot(KEY).work() < late.snapshot(KEY).duration(), "A fix never completes a stage by itself");
        check(late.tick().isEmpty() || late.stage(KEY) == 1, "The stage goes on from where it was");
    }

    private static void analyzerKeepsLearning() {
        var state = running(AdaptationProfile.PHYSICAL);
        state.setManualCategory(KEY, AdaptationProfile.COLD);
        int active = state.snapshot(KEY).duration();
        check(state.applyAnalysis(KEY, decision(AdaptationProfile.ELECTRIC, 75)), "The analyzer still takes new facts on a corrected row");
        var after = state.snapshot(KEY);
        check(after.profile() == AdaptationProfile.ELECTRIC && after.analysisConfidence() == 75, "Its verdict moves on");
        check(after.category() == AdaptationProfile.COLD, "The player's word stays");
        check(after.duration() == active, "New facts do not change the running stage");
        check(after.timings().ticks(2) == AdaptationProfile.COLD.duration(2), "Future stages stay in the corrected category");
        state.setManualCategory(KEY, null);
        check(state.snapshot(KEY).category() == AdaptationProfile.ELECTRIC, "Giving it back shows what the analyzer learned meanwhile");
        check(near(state.snapshot(KEY).duration(), AdaptationProfile.ELECTRIC.duration(0) * (double) active / AdaptationProfile.COLD.duration(0)), "And its time");

        // What it measured about the source counts in the corrected time as well.
        var slow = new StageDurations(1000, 2000, 3000, 4000, 5000);
        var moved = AdaptationTimings.corrected(slow, AdaptationProfile.PHYSICAL, AdaptationProfile.COLD);
        for (int stage = 0; stage < 5; stage++)
            check(near(moved.ticks(stage), slow.ticks(stage) * (double) AdaptationProfile.COLD.duration(stage) / AdaptationProfile.PHYSICAL.duration(stage)), "Same share of the other category's time");
        check(AdaptationTimings.corrected(slow, AdaptationProfile.COLD, AdaptationProfile.COLD) == slow && AdaptationTimings.corrected(null, AdaptationProfile.PHYSICAL, AdaptationProfile.COLD) == null, "No move, no change");
        check(AdaptationTimings.scaled(20, AdaptationProfile.MAGIC, AdaptationProfile.FIRE, 0) == 20, "Never under a second");
        check(AdaptationTimings.scaled(StageDurations.MAX_SECONDS * 20, AdaptationProfile.FIRE, AdaptationProfile.MAGIC, 0) == StageDurations.MAX_SECONDS * 20, "Never over the limit");
    }

    private static void otherRowsStayPut() {
        var state = new AdaptationState();
        state.applyAnalysis(KEY, decision(AdaptationProfile.PHYSICAL, 40));
        state.applyAnalysis(OTHER, decision(AdaptationProfile.COLD, 40));
        state.setStage(KEY, 4, AdaptationProfile.PHYSICAL);
        state.hit(OTHER, false, AdaptationProfile.COLD);
        var other = state.snapshot(OTHER);
        check(state.mastered(AdaptationProfile.PHYSICAL, OTHER) == 1 && state.mastered(AdaptationProfile.COLD, OTHER) == 0, "Start");
        state.setManualCategory(KEY, AdaptationProfile.COLD);
        check(state.snapshot(OTHER).equals(other), "Another row does not move");
        check(state.mastered(AdaptationProfile.PHYSICAL, OTHER) == 1 && state.mastered(AdaptationProfile.COLD, OTHER) == 0, "Element experience keeps counting by the analyzer");
    }

    private static void savedAndRestored() {
        var state = running(AdaptationProfile.PHYSICAL);
        state.setManualCategory(KEY, AdaptationProfile.COLD);
        var copy = AdaptationState.restore(true, state.snapshots(), state.revision());
        check(copy.snapshots().equals(state.snapshots()), "A correction survives saving, with its running stage");
        copy.setManualCategory(KEY, null);
        check(copy.snapshot(KEY).category() == AdaptationProfile.PHYSICAL, "And can be taken back afterwards");
        var old = new AdaptationState.Snapshot(KEY, true, false, 1, 0, 0, false, AdaptationProfile.FIRE);
        check(old.manualProfile() == null && old.category() == AdaptationProfile.FIRE, "Saves from before 4.44 have none");
        state.clearPending();
        check(state.snapshot(KEY).manualProfile() == AdaptationProfile.COLD, "Death keeps the player's word");
        state.forget(KEY);
        check(!state.setManualCategory(KEY, AdaptationProfile.COLD), "A forgotten row has nothing to correct");
    }

    private static void refused() {
        var state = new AdaptationState();
        check(!state.setManualCategory(KEY, AdaptationProfile.COLD), "No row, no correction");
        String mechanic = MetaMechanic.RIPTIDE.key;
        state.hit(mechanic, false, AdaptationProfile.ENVIRONMENT);
        check(!state.setManualCategory(mechanic, AdaptationProfile.COLD) && state.snapshot(mechanic).manualProfile() == null, "A mechanic has no category to correct");
    }

    private static void configuredTimesStillWin() {
        var state = running(AdaptationProfile.PHYSICAL);
        AdaptationTimings.replace(Map.of(), Map.of(KEY, List.of(90, 0, 0, 0, 0)));
        int work = (int) state.snapshot(KEY).work();
        state.setManualCategory(KEY, AdaptationProfile.COLD);
        check(state.snapshot(KEY).duration() == 1800 && (int) state.snapshot(KEY).work() == work, "A source's Configured time outranks the category, work untouched");
        check(state.snapshot(KEY).timings().ticks(1) == AdaptationProfile.COLD.duration(1), "Stages it leaves alone follow the correction");
        AdaptationTimings.defaults();
        check(state.snapshot(KEY).duration() == AdaptationProfile.COLD.duration(0), "Removing it brings the correction's time");
        state.setManualCategory(KEY, null);
        AdaptationTimings.setAnalysisEnabled(false);
        state.setManualCategory(KEY, AdaptationProfile.COLD);
        check(state.snapshot(KEY).duration() == AdaptationProfile.COLD.duration(0), "With the analyzer off the category's own time counts");
        AdaptationTimings.defaults();

        // A Configured time lowered under the work already done: the fix does not leave the row past its end.
        var over = running(AdaptationProfile.PHYSICAL);
        AdaptationTimings.replace(Map.of(), Map.of(KEY, List.of(1, 0, 0, 0, 0)));
        over.setManualCategory(KEY, AdaptationProfile.COLD);
        check(over.stage(KEY) == 0 && over.snapshot(KEY).work() < over.snapshot(KEY).duration(), "Work stays under the time, the next tick ends the stage as usual");
        AdaptationTimings.defaults();
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) <= Math.max(1, Math.abs(b)) * 0.002; }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
