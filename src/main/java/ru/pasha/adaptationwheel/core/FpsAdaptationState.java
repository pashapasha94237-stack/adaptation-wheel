package ru.pasha.adaptationwheel.core;

/** Client-side adaptation measured in eligible wall-clock seconds, independently of ticks and hits. */
public final class FpsAdaptationState {
    public static final int STAGES = 4;
    public static final double REQUIRED_LOW_FPS_SECONDS = 5.0;
    public static final double MAX_SAMPLE_SECONDS = 2.0;
    private static final double EPSILON = 1.0e-9;
    private int level;
    private boolean started;
    private boolean progressing;
    private double qualifyingSeconds;
    private double stageElapsedSeconds;

    /**
     * Supplies a fresh measurement window, normally one second long.
     * Eligibility must exclude menus, loading, an unfocused window and an unequipped wheel.
     * Returns a newly completed stage (1..4), or zero when no stage completed.
     * Windows longer than two seconds are rejected instead of crediting loading pauses.
     */
    public int sample(double fps, double elapsedSeconds, boolean eligibleGameplay) {
        if (completed()) {
            progressing = false;
            return 0;
        }
        if(!eligibleGameplay) { pause();return 0; }
        if (!isLowFps(fps) || !Double.isFinite(elapsedSeconds)
                || elapsedSeconds <= 0 || elapsedSeconds > MAX_SAMPLE_SECONDS) {
            interruptQualification();
            return 0;
        }
        progressing = true;
        double workSeconds = elapsedSeconds;
        if (!started) {
            double needed = REQUIRED_LOW_FPS_SECONDS - qualifyingSeconds;
            if (workSeconds + EPSILON < needed) {
                qualifyingSeconds += workSeconds;
                return 0;
            }
            workSeconds = Math.max(0, workSeconds - needed);
            qualifyingSeconds = REQUIRED_LOW_FPS_SECONDS;
            started = true;
        }
        stageElapsedSeconds += workSeconds;
        double duration = currentDurationSeconds();
        if (stageElapsedSeconds + EPSILON < duration) {
            return 0;
        }
        stageElapsedSeconds = Math.max(0, stageElapsedSeconds - duration);
        level++;
        if (completed()) {
            stageElapsedSeconds = 0;
            progressing = false;
        }
        return level;
    }

    public static boolean isLowFps(double fps) {
        return Double.isFinite(fps) && fps >= 1.0 && fps <= 59.0;
    }

    /** Menus/background suspend measurement; checking the wheel does not erase valid low-FPS samples. */
    public void pause() {
        progressing = false;
    }

    /** Real healthy/invalid FPS, a loading stall or leaving gameplay breaks initial qualification. */
    public void interruptQualification() {
        progressing=false;
        if (!started) {
            qualifyingSeconds = 0;
        }
    }

    public void reset() {
        level = 0;
        started = progressing = false;
        qualifyingSeconds = stageElapsedSeconds = 0;
    }

    /** Restores this computer's earned stages without crediting time spent outside gameplay. */
    public void restore(int completedStages, double workSeconds, boolean wasStarted) {
        reset();
        level = Math.max(0, Math.min(STAGES, completedStages));
        started = level > 0 || wasStarted;
        if (completed()) return;
        if (started && Double.isFinite(workSeconds))
            stageElapsedSeconds = Math.max(0, Math.min(currentDurationSeconds(), workSeconds));
    }

    /** Number of completed stages, from zero through four. */
    public int level() { return level; }
    public boolean started() { return started; }
    public boolean completed() { return level == STAGES; }
    /** Whether the latest supplied sample was eligible and low; includes the initial qualification. */
    public boolean progressing() { return progressing; }
    public double qualifyingSeconds() { return qualifyingSeconds; }
    public double currentElapsedSeconds() { return stageElapsedSeconds; }

    /** Duration of the current, unfinished stage; zero after full completion. */
    public double currentDurationSeconds() {
        return completed() ? 0 : 20.0 + 30.0 * level;
    }

    public double remainingSeconds() {
        return completed() ? 0 : Math.max(0, currentDurationSeconds() - stageElapsedSeconds);
    }

    public double currentProgress() {
        return completed() ? 1 : stageElapsedSeconds / currentDurationSeconds();
    }

    /** Adaptation work only; the initial five-second qualification is excluded. */
    public double elapsedSeconds() {
        return completedWorkSeconds() + stageElapsedSeconds;
    }

    public double totalProgress() {
        return Math.min(1, elapsedSeconds() / totalDurationSeconds());
    }

    public static double totalDurationSeconds() { return 260.0; }

    private double completedWorkSeconds() {
        return 20.0 * level + 15.0 * level * (level - 1);
    }
}
