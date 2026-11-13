package ru.pasha.adaptationwheel.core;

import java.util.Objects;

/**
 * An exact, audited custom-state mutation. A name such as "energy" or "cold" is
 * never sufficient evidence. The caller must separately verify the causal native
 * harm and the affected player before using the plan at its exact write site.
 */
public record StatePlan(String id, String conditionKey, String nativeDescriptor,
                        Direction direction, double healthy, double minimum, double maximum) {
    public enum Direction { HIGHER_IS_HARMFUL, LOWER_IS_HARMFUL }

    public StatePlan {
        Objects.requireNonNull(id); Objects.requireNonNull(conditionKey);
        Objects.requireNonNull(nativeDescriptor); Objects.requireNonNull(direction);
        if (id.isBlank() || id.length() > 512 || !conditionKey.startsWith("condition:") || conditionKey.length() > 256)
            throw new IllegalArgumentException("Invalid state-plan identity");
        if (!nativeDescriptor.matches("[ZIFDJ]")) throw new IllegalArgumentException("State plan is not scalar");
        if (!Double.isFinite(healthy) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum > maximum || healthy < minimum || healthy > maximum)
            throw new IllegalArgumentException("Invalid audited state bounds");
        if (nativeDescriptor.equals("Z") && (minimum != 0 || maximum != 1 || (healthy != 0 && healthy != 1)))
            throw new IllegalArgumentException("Invalid boolean-state schema");
        if ((nativeDescriptor.equals("I") || nativeDescriptor.equals("J"))
                && (healthy != Math.rint(healthy) || minimum != Math.rint(minimum) || maximum != Math.rint(maximum)))
            throw new IllegalArgumentException("Integral state has fractional bounds");
        if (nativeDescriptor.equals("I") && (minimum < Integer.MIN_VALUE || maximum > Integer.MAX_VALUE))
            throw new IllegalArgumentException("Int state exceeds native range");
        if (nativeDescriptor.equals("F") && (!Float.isFinite((float)healthy)
                || !Float.isFinite((float)minimum) || !Float.isFinite((float)maximum)))
            throw new IllegalArgumentException("Float state exceeds native range");
        // Long values outside the exact IEEE-754 integer range require a different
        // native adapter; silently rounding persistent capability values is unsafe.
        if (nativeDescriptor.equals("J") && (minimum < -9_007_199_254_740_991d || maximum > 9_007_199_254_740_991d))
            throw new IllegalArgumentException("Long state exceeds exact scalar range");
    }

    public boolean accepts(double value) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
    private double badness(double value) {
        return Math.max(0, direction == Direction.HIGHER_IS_HARMFUL ? value - healthy : healthy - value);
    }
    public boolean harmful(double value) { return accepts(value) && badness(value) > 0; }
    public boolean worsens(double before, double requested) {
        return accepts(before) && accepts(requested) && badness(requested) > badness(before);
    }

    /** Fail open on unknown schema/range; beneficial values and recovery are preserved. */
    public double protectNumber(double before, double requested, double remaining) {
        if (nativeDescriptor.equals("Z") || !accepts(before) || !accepts(requested)
                || !Double.isFinite(remaining) || remaining < 0 || remaining > 1) return requested;
        if (remaining == 1) return requested;
        // A confirmed harmful state must actually disappear after full adaptation,
        // not merely stop becoming worse while its old symptoms keep running.
        if (remaining == 0 && harmful(requested)) return healthy;
        if (!worsens(before, requested)) return requested;
        double delta = requested - before;
        if (!Double.isFinite(delta)) return requested;
        double result = before + delta * remaining;
        return accepts(result) ? result : requested;
    }

    /** Coarse integer states round toward the native request, never toward premature immunity. */
    public int protectInt(int before, int requested, double remaining) {
        if (!nativeDescriptor.equals("I")) return requested;
        double result = protectNumber(before, requested, remaining);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) return requested;
        return (int)(requested >= before ? Math.ceil(result) : Math.floor(result));
    }
    public long protectLong(long before, long requested, double remaining) {
        if (!nativeDescriptor.equals("J") || Math.abs((double)before) > 9_007_199_254_740_991d
                || Math.abs((double)requested) > 9_007_199_254_740_991d) return requested;
        double result = protectNumber(before, requested, remaining);
        return (long)(requested >= before ? Math.ceil(result) : Math.floor(result));
    }
    /** Boolean states have no meaningful fractional intensity. Only full protection clears them. */
    public boolean protectBoolean(boolean before, boolean requested, double remaining) {
        if (!nativeDescriptor.equals("Z") || remaining != 0) return requested;
        return harmful(requested ? 1 : 0) ? healthy != 0 : requested;
    }
}
