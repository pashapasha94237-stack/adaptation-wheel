package ru.pasha.adaptationwheel.core;

/** Native-free regressions for mutation direction, actual clearing, and fail-open boundaries. */
public final class StatePlanTest {
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    private static void equal(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-9, message + ": " + actual); }
    public static void main(String[] args) { run(); }
    public static void run() {
        var radiation = new StatePlan("audit:radiation", "condition:qa:radiation", "D", StatePlan.Direction.HIGHER_IS_HARMFUL, 0, 0, 100);
        equal(radiation.protectNumber(10, 20, .8), 18, "Partial radiation must scale the harmful delta");
        equal(radiation.protectNumber(10, 20, 0), 0, "Full protection must clear existing radiation, not leave old symptoms");
        equal(radiation.protectNumber(10, 5, .8), 5, "Natural decay must not be weakened");
        equal(radiation.protectNumber(10, 20, 1), 20, "Unadapted mutations must remain native");
        equal(radiation.protectNumber(10, 101, 0), 101, "Unknown native range must fail open");
        var unboundedDose = new StatePlan("audit:mekanism", "condition:qa:radiation", "D", StatePlan.Direction.HIGHER_IS_HARMFUL, 1e-7, 1e-7, Double.MAX_VALUE);
        equal(unboundedDose.protectNumber(2e12, 2e12, 0), 1e-7, "Native uncapped finite doses must clear above a trillion");
        equal(radiation.protectNumber(10, 20, Double.NaN), 20, "Invalid adaptation factors must fail open");
        var remainingCapacity = new StatePlan("audit:capacity", "condition:qa:capacity", "F", StatePlan.Direction.LOWER_IS_HARMFUL, 1, 0, 1);
        equal(remainingCapacity.protectNumber(.8, .4, .5), .6, "Lower-is-harmful state uses the inverse direction");
        equal(remainingCapacity.protectNumber(.4, .8, .5), .8, "Positive capacity changes must pass through");
        equal(remainingCapacity.protectNumber(.4, .4, 0), 1, "A persistent unchanged harmful state must actually clear");
        var sanityDim = new StatePlan("audit:sanitydim", "condition:qa:insanity", "F", StatePlan.Direction.HIGHER_IS_HARMFUL, 0, 0, 1);
        equal(sanityDim.protectNumber(.2, .6, .5), .4, "Real SanityDim counts higher values as MORE insane");
        equal(sanityDim.protectNumber(.6, .2, .5), .2, "Native SanityDim recovery is a negative delta");
        equal(sanityDim.protectNumber(.6, .6, 0), 0, "Full insanity protection must clear the actual meter");
        var hydration = new StatePlan("audit:hydration", "condition:qa:thirst", "F", StatePlan.Direction.LOWER_IS_HARMFUL, 2, 0, 20);
        equal(hydration.protectNumber(10, 8, 0), 8, "Values still above the safe baseline are not harmful");
        var counter = new StatePlan("audit:counter", "condition:qa:counter", "I", StatePlan.Direction.HIGHER_IS_HARMFUL, 0, 0, 100);
        check(counter.protectInt(0, 1, .8) == 1, "Integer rounding cannot grant premature full immunity");
        check(counter.protectInt(10, 20, .5) == 15, "Meaningful integer deltas must scale");
        check(counter.protectInt(10, 20, 0) == 0, "Full integer protection clears prior harmful state");
        var flag = new StatePlan("audit:frozen", "condition:qa:frozen", "Z", StatePlan.Direction.HIGHER_IS_HARMFUL, 0, 0, 1);
        check(flag.protectBoolean(false, true, .3), "Partial protection cannot represent fractional booleans");
        check(!flag.protectBoolean(true, true, 0), "Full boolean protection must clear an already active symptom");
        check(!flag.protectBoolean(true, false, .3), "A native boolean recovery must not be undone");
        boolean rejected = false;
        try { new StatePlan("bad", "condition:qa:wide", "J", StatePlan.Direction.HIGHER_IS_HARMFUL, 0, 0, Long.MAX_VALUE); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unsafe long precision must be rejected before registration");
        rejected = false;
        try { new StatePlan("bad", "condition:qa:fraction", "I", StatePlan.Direction.HIGHER_IS_HARMFUL, .5, 0, 100); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Fractional native integer baselines must be rejected");
        rejected = false;
        try { new StatePlan("bad", "condition:qa:float", "F", StatePlan.Direction.LOWER_IS_HARMFUL, 1e100, 0, 1e100); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Native float baselines must not overflow to infinity");
        System.out.println("StatePlanTest PASS: harmful deltas, recovery, actual full clearing, booleans, range and precision guards");
    }
}
